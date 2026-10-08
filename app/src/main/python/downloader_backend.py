import json, os, re, threading, time
from pathlib import Path
import yt_dlp

STATE = {
    "state": "idle", "progress": 0.0, "speed": "", "eta": "",
    "title": "", "thumbnail": "", "message": "Ready", "files": [], "error": ""
}
LOCK = threading.Lock()
WORKER = None
CANCEL = False


def _set(**kw):
    with LOCK:
        STATE.update(kw)


def get_status():
    with LOCK:
        return json.dumps(dict(STATE), ensure_ascii=False)


def cancel():
    global CANCEL
    CANCEL = True


def _hook(d):
    global CANCEL
    if CANCEL:
        raise Exception("DOWNLOAD_CANCELLED")
    if d.get("status") == "downloading":
        total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
        done = d.get("downloaded_bytes", 0)
        p = (done / total) if total else 0
        _set(progress=max(0, min(0.98, p)), speed=d.get("_speed_str", ""), eta=d.get("_eta_str", ""), message="Downloading...")
    elif d.get("status") == "finished":
        _set(progress=0.99, message="Processing media...")


def _safe(s):
    s = re.sub(r'[\\/:*?"<>|]', '_', s or 'media')
    return s.strip().strip('.')[:180] or 'media'


def preview(url):
    try:
        opts = {
            "quiet": True, "no_warnings": True, "skip_download": True,
            "extract_flat": "in_playlist",
            "nocheckcertificate": True,
            "http_headers": {"User-Agent": "Mozilla/5.0"}
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if info.get("_type") == "playlist":
            entries = info.get("entries") or []
            first = next((x for x in entries if x), {})
            return json.dumps({"ok": True, "title": "Playlist: " + (info.get("title") or "Media Playlist"), "thumbnail": first.get("thumbnail") or info.get("thumbnail", "")}, ensure_ascii=False)
        return json.dumps({"ok": True, "title": info.get("title") or "Media", "thumbnail": info.get("thumbnail") or ""}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def start(url, fmt, workdir):
    global WORKER, CANCEL
    if WORKER and WORKER.is_alive():
        return False
    CANCEL = False
    Path(workdir).mkdir(parents=True, exist_ok=True)
    WORKER = threading.Thread(target=_download, args=(url, fmt, workdir), daemon=True)
    WORKER.start()
    return True


def _download(url, fmt, workdir):
    try:
        _set(state="preparing", progress=0, speed="", eta="", message="Reading media information...", files=[], error="")
        with yt_dlp.YoutubeDL({"quiet": True, "no_warnings": True, "skip_download": True, "nocheckcertificate": True, "http_headers": {"User-Agent": "Mozilla/5.0"}}) as ydl:
            info = ydl.extract_info(url, download=False)
        title = info.get("title") or "media"
        _set(title=title, thumbnail=info.get("thumbnail") or "", message="Starting download...")
        base = _safe(title)
        if fmt == "MP3":
            template = str(Path(workdir) / (base + ".%(ext)s"))
            opts = {
                "outtmpl": template, "quiet": True, "no_warnings": True,
                "nocheckcertificate": True, "progress_hooks": [_hook],
                "format": "bestaudio/best", "noplaylist": True,
                "http_headers": {"User-Agent": "Mozilla/5.0"}
            }
        else:
            # Download video/audio separately; Android FFmpegKit merges them natively.
            template = str(Path(workdir) / (base + ".%(ext)s"))
            opts = {
                "outtmpl": template, "quiet": True, "no_warnings": True,
                "nocheckcertificate": True, "progress_hooks": [_hook],
                "format": "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "noplaylist": True,
                "http_headers": {"User-Agent": "Mozilla/5.0"}
            }
        with yt_dlp.YoutubeDL(opts) as ydl:
            ydl.download([url])
        files = [str(p) for p in Path(workdir).iterdir() if p.is_file()]
        _set(state="processing", progress=0.99, message="Finalizing...", files=files)
    except Exception as e:
        if "DOWNLOAD_CANCELLED" in str(e):
            _set(state="cancelled", progress=0, message="Download cancelled", error="")
        else:
            _set(state="error", progress=0, message="Download failed", error=str(e))


def reset_state():
    _set(state="done", progress=1.0, message="Complete")
