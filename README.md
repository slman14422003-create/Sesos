# Sesos

تطبيق أندرويد (Kotlin + Jetpack Compose) على طريقة Snaptube: شارك أي رابط من أي تطبيق إلى **Sesos**
فيحلّله ويعرض لك الفيديو / الصوت / الصور المتاحة للتحميل، ويحفظها في المعرض والموسيقى تحت مجلد `Sesos`.

## كيف يعمل
- `AndroidManifest`: `ACTION_SEND` + `text/plain` ← يظهر Sesos في قائمة المشاركة بالنظام.
- المحرك: [yt-dlp](https://github.com/yt-dlp/yt-dlp) عبر مكتبة `youtubedl-android` (بايثون + yt-dlp + ffmpeg مدمجة).
- الصور: fallback يقرأ `og:image` من الصفحة أو الرابط المباشر للصورة.
- التحميل داخل Foreground Service، والحفظ عبر MediaStore (Movies / Music / Pictures ← `Sesos`).

## البناء
ارفع المشروع على GitHub ← تبويب **Actions** ← شغّل *Build Sesos APK* ← حمّل `Sesos-apk` من الـ Artifacts.
للتوقيع الثابت أضف Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## ملاحظة
حمّل فقط المحتوى المسموح لك بتحميله، والتزم بحقوق النشر وشروط كل منصة.
