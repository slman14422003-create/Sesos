# skills

## المهمة
إصلاح فشل بناء assembleRelease على GitHub Actions

## الجولة الأولى
- الفشل في checkReleaseAarMetadata بسبب geckoview:+ المنحل إلى 159 وهو يتطلب compileSdk 37
- مكتبات androidx الأحدث (core 1.19.1 و lifecycle 2.11.0) تتطلب AGP 9.1.0 والمشروع على 8.7.3

## الجولة الثانية
- المنصة platforms;android-37 غير متوفرة في صورة الـ runner، فلا يمكن استخدام compileSdk 37
- الحل: العودة إلى خط geckoview 147 بنطاق [147.0,148.0) مع compileSdk 36

## الجولة الثالثة
- نجح فحص checkReleaseAarMetadata وفشل دمج المانيفست: geckoview 147 يتطلب minSdk 26 والمشروع على 24
- الحل: رفع minSdk إلى 26 (يستثني أندرويد 7.0 و7.1)
- بديل غير مفضل: tools:overrideLibrary وقد يسبب انهيارًا على الأجهزة القديمة

## الوضع الحالي
- minSdk = 26
- compileSdk = 36 مع android.suppressUnsupportedCompileSdk=36
- geckoview بنطاق [147.0,148.0) وبعد أول بناء ناجح يُثبَّت على الإصدار المحلول
- فرض androidx.lifecycle على 2.8.7 وandroidx.core (core وcore-ktx) على 1.15.0
- الـ workflow يثبّت platforms;android-36

## إن فشل البناء مجددًا
- خطأ لا يجد geckoview ضمن النطاق: نغيّر النطاق إلى خط آخر (مثلًا [145.0,146.0))
- خطأ يطلب compileSdk 37 من Gecko: نخفض خط Gecko
- خطأ كوتلن في GeckoBrowserActivity.kt: نراجع واجهات GeckoSession وGeckoRuntime

## مهمة: خطأ NoneType lower أثناء التحميل
- التحليل ينجح (1280p و640p) والتحميل يفشل بالخطأ: 'NoneType' object has no attribute 'lower'
- السبب غير مؤكد دون traceback، والأرجح مسار دمج الصيغ داخل yt-dlp
- تم: لانستغرام تفضيل صيغة مفردة b[height<=H] قبل مسار الدمج (Engine.kt)
- تم: إضافة --print-traces وعرض آخر 600 حرف من رسالة الخطأ في بطاقة التنزيل (DownloadService.kt)
- التالي: إن استمر الخطأ نقرأ الـ traceback الظاهر في البطاقة ونحدد السطر الفعلي
- ملاحظة: زر تحديث المحرك قد لا يتوافق مع بايثون المضمّن في المكتبة 0.18.1، لذا نجرّبه بعد قراءة الـ traceback
