# Downloader Pro Android v3.0

إعادة بناء لبرنامج Downloader Pro الأصلي كواجهة HTML/CSS/JavaScript داخل APK Android.

## الوظائف
- MP4 و MP3.
- yt-dlp كمحرك استخراج وتنزيل.
- FFmpegKit لمعالجة MP3 ودمج الفيديو والصوت.
- المعاينة والعنوان والصورة المصغرة.
- MP4 افتراضيًا إلى `DCIM/Downloader Pro` عبر MediaStore، فيظهر في تطبيق المعرض.
- MP3 افتراضيًا إلى `Music/Downloader Pro`.
- اختيار مجلد مخصص عبر Android Storage Access Framework، بما فيه SD Card، مع حفظ الاختيار.
- شريط تقدم وسرعة وETA وإلغاء.
- لا يحتاج المستخدم تحديد مكان افتراضي عند أول تشغيل.

## فتح المشروع
1. افتح المجلد في Android Studio.
2. دع Gradle/Chaquopy ينزل الاعتمادات.
3. Build > Build APK(s).

## ملاحظات مهمة
- هذه النسخة لا تنقل Cookies من Chrome/Brave/Edge/Firefox مثل نسخة Windows؛ Android لا يتيح قراءة Cookies الخاصة بالمتصفحات بهذه الطريقة. المواقع التي تتطلب تسجيل دخول قد تحتاج معالجة إضافية.
- دعم التحميل يعتمد على المواقع التي يدعمها yt-dlp وعلى القيود التي تفرضها كل منصة.
- النسخة الحالية تستخدم FFmpegKit maintained `ffmpeg-kit-full` غير GPL لمعالجة الصوت ودمج الفيديو والصوت. راجع تراخيص المكونات قبل التوزيع التجاري.
- التحميل يعمل داخل عملية التطبيق. إذا أردت تحميلات تستمر بشكل مضمون بعد إغلاق التطبيق، أضف Foreground Download Service في المرحلة التالية.
