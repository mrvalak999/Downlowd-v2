package com.downloaderpro.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private PyObject py;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean processing = false;
    private String currentFormat = "MP4";
    private static final int PICK_VIDEO = 4101, PICK_AUDIO = 4102;
    private static final String PREFS = "downloader_pro";

    @Override protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        Python.start(new com.chaquo.python.android.AndroidPlatform(this));
        py = Python.getInstance().getModule("downloader_backend");
        web = new WebView(this);
        web.setBackgroundColor(android.graphics.Color.rgb(7,13,26));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
        handler.postDelayed(this::pollStatus, 500);
    }

    private String cacheDir() { return new File(getCacheDir(), "downloads").getAbsolutePath(); }

    private void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    private void pollStatus() {
        try {
            String raw = py.callAttr("get_status").toString();
            js("window.onNativeStatus(JSON.parse(" + JSONObject.quote(raw) + "));" );
            JSONObject o = new JSONObject(raw);
            String state = o.optString("state");
            if ("processing".equals(state) && !processing) {
                processing = true;
                finalizeDownload(o.optJSONArray("files"));
            }
        } catch (Exception ignored) {}
        handler.postDelayed(this::pollStatus, 500);
    }

    private void finalizeDownload(org.json.JSONArray files) {
        new Thread(() -> {
            try {
                File[] all = new File(cacheDir()).listFiles();
                if (all == null) throw new IOException("No downloaded file found");
                File inputAudio = null, inputVideo = null;
                for (File f : all) {
                    String n = f.getName().toLowerCase(Locale.US);
                    if (n.endsWith(".m4a") || n.endsWith(".webm") || n.endsWith(".aac") || n.endsWith(".opus") || n.endsWith(".mp3")) inputAudio = f;
                    if (n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv")) inputVideo = f;
                }
                String title = "media";
                try { title = py.callAttr("get_status").toString(); title = new JSONObject(title).optString("title", "media"); } catch(Exception ignored) {}
                title = safe(title);
                File out;
                if ("MP3".equals(currentFormat)) {
                    if (inputAudio == null) throw new IOException("Audio file not found");
                    out = new File(getCacheDir(), title + ".mp3");
                    String cmd = q(inputAudio.getAbsolutePath()) + " -vn -codec:a libmp3lame -b:a 192k " + q(out.getAbsolutePath());
                    FFmpegKit.executeAsync(cmd, session -> {
                        if (ReturnCode.isSuccess(session.getReturnCode())) publishAndFinish(out, false);
                        else fail("MP3 conversion failed");
                    });
                } else {
                    if (inputVideo == null) throw new IOException("Video file not found");
                    if (inputAudio != null && !sameExt(inputVideo, inputAudio)) {
                        out = new File(getCacheDir(), title + ".mp4");
                        String cmd = "-i " + q(inputVideo.getAbsolutePath()) + " -i " + q(inputAudio.getAbsolutePath()) + " -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -movflags +faststart " + q(out.getAbsolutePath());
                        FFmpegKit.executeAsync(cmd, session -> {
                            if (ReturnCode.isSuccess(session.getReturnCode())) publishAndFinish(out, true);
                            else fail("MP4 merge failed");
                        });
                    } else {
                        publishAndFinish(inputVideo, true);
                    }
                }
            } catch (Exception e) { fail(e.getMessage()); }
        }).start();
    }

    private boolean sameExt(File a, File b) { return a.getName().toLowerCase().endsWith(".mp4") && b.getName().toLowerCase().endsWith(".mp4"); }
    private String q(String p) { return "\"" + p.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private String safe(String s) { return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim().replaceAll("\\s+", " ").substring(0, Math.min(170, s.length())); }

    private void publishAndFinish(File file, boolean video) {
        new Thread(() -> {
            try {
                String custom = getSharedPreferences(PREFS,0).getString(video ? "video_uri" : "audio_uri", null);
                Uri dest;
                if (custom != null) dest = copyToTree(file, Uri.parse(custom), video);
                else dest = publishMediaStore(file, video);
                cleanupCache();
                js("window.onFinished(" + JSONObject.quote(dest != null ? dest.toString() : "") + ");");
            } catch (Exception e) { fail(e.getMessage()); }
            finally { try { py.callAttr("reset_state"); } catch(Exception ignored) {} processing = false; }
        }).start();
    }

    private Uri publishMediaStore(File f, boolean video) throws Exception {
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
        v.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "audio/mpeg");
        if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, video ? "DCIM/Downloader Pro" : "Music/Downloader Pro");
        Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri uri = cr.insert(base, v); if (uri == null) throw new IOException("MediaStore insert failed");
        try (InputStream in = new FileInputStream(f); OutputStream out = cr.openOutputStream(uri)) {
            byte[] buf = new byte[1024*1024]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        return uri;
    }

    private Uri copyToTree(File f, Uri tree, boolean video) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(this, tree); if (root == null || !root.canWrite()) throw new IOException("Selected folder is not writable");
        DocumentFile existing = root.findFile(f.getName()); if (existing != null) existing.delete();
        DocumentFile dst = root.createFile(video ? "video/mp4" : "audio/mpeg", f.getName()); if (dst == null) throw new IOException("Cannot create destination file");
        try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(dst.getUri())) {
            byte[] buf = new byte[1024*1024]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        return dst.getUri();
    }

    private void cleanupCache() { File d = new File(cacheDir()); File[] fs=d.listFiles(); if(fs!=null) for(File f:fs) f.delete(); }
    private void fail(String msg) { processing=false; js("window.onNativeError("+JSONObject.quote(msg == null ? "Unknown error" : msg)+");"); }

    public class Bridge {
        @JavascriptInterface public String getClipboard() { try { android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); return cm.hasPrimaryClip()?String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this)):""; } catch(Exception e){return "";} }
        @JavascriptInterface public void preview(String url) { new Thread(() -> { try { String r=py.callAttr("preview",url).toString(); js("window.onPreview("+JSONObject.quote(r)+");"); } catch(Exception e){ js("window.onPreview('"+JSONObject.quote("{\\\"ok\\\":false,\\\"error\\\":\\\""+e.getMessage()+"\\\"}")+"');"); } }).start(); }
        @JavascriptInterface public void start(String url,String fmt) { currentFormat=fmt; cleanupCache(); py.callAttr("start",url,fmt,cacheDir()); }
        @JavascriptInterface public void cancel() { py.callAttr("cancel"); }
        @JavascriptInterface public void chooseFolder(String kind) { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,"MP4".equals(kind)?PICK_VIDEO:PICK_AUDIO); }
        @JavascriptInterface public String getFolder(String kind) { return getSharedPreferences(PREFS,0).getString("MP4".equals(kind)?"video_uri":"audio_uri",""); }
        @JavascriptInterface public void clearFolder(String kind) { getSharedPreferences(PREFS,0).edit().remove("MP4".equals(kind)?"video_uri":"audio_uri").apply(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) { super.onActivityResult(requestCode,resultCode,data); if(resultCode==RESULT_OK && data!=null && data.getData()!=null){ Uri u=data.getData(); try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){} String key=requestCode==PICK_VIDEO?"video_uri":"audio_uri"; getSharedPreferences(PREFS,0).edit().putString(key,u.toString()).apply(); js("window.onFolderChanged("+JSONObject.quote(key)+","+JSONObject.quote(u.toString())+");"); }}
}
