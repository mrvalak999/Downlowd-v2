let fmt='MP4',busy=false,lastPreview='';
const $=id=>document.getElementById(id);
function setFormat(x){fmt=x;$('mp4').classList.toggle('selected',x==='MP4');$('mp3').classList.toggle('selected',x==='MP3')}
function toast(t){let e=$('toast');e.textContent=t;e.classList.add('show');setTimeout(()=>e.classList.remove('show'),2500)}
function platform(u){let s=u.toLowerCase(),t='🌐 Universal Media';if(s.includes('youtube'))t=s.includes('list=')?'🔴 YouTube Playlist':'🔴 YouTube';else if(s.includes('tiktok'))t='♪ TikTok';else if(s.includes('instagram'))t='◎ Instagram';else if(s.includes('.m3u8')||s.includes('hls'))t='📡 HLS Stream';else if(s)t='🌐 HTTP / Web Stream';$('platform').textContent=t;$('previewplatform').textContent=t}
function paste(){let x=Android.getClipboard();if(x){$('url').value=x;onUrl();}}
function onUrl(){let u=$('url').value.trim();platform(u);if(/^https?:\/\//i.test(u)){Android.preview(u)}}
$('url').addEventListener('input',()=>{clearTimeout(window.pt);window.pt=setTimeout(onUrl,450)});
function choose(k){Android.chooseFolder(k)}
function downloadMedia(){if(busy)return;let u=$('url').value.trim();if(!/^https?:\/\//i.test(u)){toast('أدخل رابطًا صحيحًا');return}busy=true;$('download').disabled=true;$('download').textContent='↓  جارٍ التحميل...';$('cancel').disabled=false;setState('● تجهيز...','#f5c542');Android.start(u,fmt)}
function cancel(){Android.cancel();setState('● إلغاء...','#f5c542')}
function setState(t,c){$('state').textContent=t;$('state').style.color=c}
window.onPreview=r=>{try{let x=JSON.parse(r);if(!x.ok)return;$('title').textContent=x.title||'Media';$('hint').textContent='تم اكتشاف معلومات الوسائط';if(x.thumbnail){$('thumb').src=x.thumbnail;$('thumb').style.display='block';$('thumbtext').style.display='none'}}catch(e){}};
window.onNativeStatus=x=>{if(x.progress!=null)$('bar').style.width=Math.round(x.progress*100)+'%';if(x.speed||x.eta)$('details').textContent=`السرعة: ${x.speed||'—'}  •  الوقت المتبقي: ${x.eta||'—'}`;if(x.message&&x.state!=='idle')setState('● '+x.message,'#3b82f6');if(x.state==='error'){busy=false;$('download').disabled=false;$('download').textContent='↓  تحميل';$('cancel').disabled=true;$('bar').style.width='0';setState('● فشل التحميل','#ef4444');toast(x.error||'حدث خطأ')}};
window.onFinished=u=>{busy=false;$('download').disabled=false;$('download').textContent='↓  تحميل';$('cancel').disabled=true;$('bar').style.width='100%';setState('● اكتمل التحميل','#16d98b');$('details').textContent='تم الحفظ وظهر الملف في المكان المحدد.';toast('تم التحميل بنجاح')};
window.onNativeError=e=>{busy=false;$('download').disabled=false;$('download').textContent='↓  تحميل';$('cancel').disabled=true;$('bar').style.width='0';setState('● فشل','#ef4444');toast(e||'حدث خطأ')};
window.onFolderChanged=(k,u)=>{if(k==='video_uri')$('vloc').textContent='مجلد مخصص / بطاقة SD';else $('aloc').textContent='مجلد مخصص / بطاقة SD';toast('تم حفظ مكان الحفظ')};
