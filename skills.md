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
- تم: إضافة -v (الخيار --print-traces غير مدعوم في هذا الإصدار) وعرض آخر 600 حرف من رسالة الخطأ في بطاقة التنزيل (DownloadService.kt)
- التالي: إن استمر الخطأ نقرأ الـ traceback الظاهر في البطاقة ونحدد السطر الفعلي
- ملاحظة: زر تحديث المحرك قد لا يتوافق مع بايثون المضمّن في المكتبة 0.18.1، لذا نجرّبه بعد قراءة الـ traceback

## مهمة: إعادة تصميم الواجهة بثيم FreeLLMChat
- المرجع: FreeLLMChat-v2_9-16-1.zip (ui/Theme.kt): لون أساسي 2F7BFF، خلفية سوداء نقية بالوضع الداكن وبيضاء بالفاتح، أسطح 101010 و161616 داكنًا وF4F4F4 فاتحًا
- الخطوط: FontFamily.Serif للعناوين وخط النظام للنص، الزوايا 12/16/24/28
- ui/Theme.kt جديد: SesosTheme (يتبع ثيم النظام) مع Motion وSpacing وCorners وbounceClick
- ui/SesosScreen.kt أعيد تصميمه: عنوان Serif، شريط رابط بشكل حبة مع زر تحليل دائري، بطاقات مدورة بحدود رفيعة، أزرار الجودة كحبات، حالة الحسابات بنقطة ملونة، شريط تقدم مدور
- res/values و values-night: window_bg وthemes، ونصوص الشاشة الرئيسية انتقلت إلى strings.xml بالعربية والإنجليزية بدون نصوص عربية داخل الأكواد
- LoginActivity وGeckoBrowserActivity تستخدمان SesosTheme الآن، ونصوصهما العربية ما زالت داخل الكود
- أضيفت مكتبة material-icons-core فقط (أيقونات Refresh وSearch وCheck)
- لم يُجرَّب البناء محليًا، والتحقق بمراجعة ساكنة

## مهمة: إعادة تصميم شاشة المتصفح ونافذة المشاركة المنبثقة
- GeckoBrowserActivity: شريط علوي جديد (زر إغلاق دائري، اسم الموقع بخط Serif مع نقطة الحالة، زر تم بشكل حبة) وشريط أدوات حبة (رجوع، تحديث، كمبيوتر/جوال، WebView) مع احترام هوامش النظام عبر enableEdgeToEdge وsafeDrawing
- زر تنزيل هذا الفيديو صار حبة زرقاء تظهر بحركة من الأسفل وتفتح النافذة المنبثقة بدل الانتقال إلى التطبيق الأساسي، والمتصفح يبقى مفتوحًا
- ShareDownloadActivity جديدة بثيم شفاف وtaskAffinity فارغ وexcludeFromRecents، وفلتر ACTION_SEND انتقل إليها من MainActivity
- ui/SharePopup.kt: ورقة سفلية فوق التطبيق المصدر تحلل الرابط وتعرض الجودات وMP3 والصورة ثم تتحول إلى شريط تقدم مع إلغاء وفتح، والتحميل يكمل بالخلفية في DownloadService عند إغلاقها
- MainViewModel.popupStartedAt لربط عنصر التحميل بالنافذة عبر بادئة المعرف الزمنية
- Theme.kt: أضيف CircleButton
- نصوص جديدة في strings.xml بالعربية والإنجليزية
- لم يُجرَّب البناء، فأي خطأ ترجمة يُرسل لوجه للإصلاح

## إصلاح: خطأ ترجمة في GeckoBrowserActivity
- AnimatedVisibility داخل Box ضمن Column يلتقط نسخة ColumnScope الضمنية ويفشل
- الحل: استدعاء androidx.compose.animation.AnimatedVisibility باسمه الكامل
- البناء وصل إلى compileReleaseKotlin وهذا الخطأ الوحيد الذي ظهر

## مهمة: بحث يوتيوب، شريط تنقل عائم، أيقونة جديدة
- الأيقونة: adaptive icon من الصورة المرفقة. الخلفية بيضاء والمقدمة حرف S وزر التنزيل مستخرجان بقناة ألفا من الصورة (mipmap-*/ic_launcher_foreground.png) مع طبقة monochrome لأندرويد 13. تُحذف drawable/ic_launcher.xml القديمة والمانيفست يشير إلى mipmap
- بحث يوتيوب عبر yt-dlp نفسه: Yt.kt. الأغاني بـ ytsearch30 والقوائم بصفحة results مع sp=EgIQAw%253D%253D وكلاهما بـ --flat-playlist --dump-single-json. الصور المصغرة للأغاني تُبنى من i.ytimg.com. إن لم تظهر القوائم أو صورها فالسبب غالبًا تغيّر شكل نتائج يوتيوب وتُعالج بتحديث المحرك
- الشريط الواحد في الرئيسية يقبل رابطًا فيُحلَّل أو نصًا فيُبحث به. شاشة الرئيسية بثلاث حالات HomeMode: IDLE (تصفح بفئات وعمليات بحث أخيرة) وLINK (خيارات الجودة) وSEARCH (تبويب أغاني وقوائم)
- زر التنزيل الدائري بجانب كل أغنية ينزّل MP3 مباشرة ويتحول إلى حلقة تقدم ثم علامة صح. النقر على الصف يفتح خيارات الفيديو والجودات. فتح قائمة يعرض مقاطعها مع زر تحميل القائمة كلها MP3
- Engine.download للقوائم: إن فشل عنصر وبقيت ملفات ناجحة تُحفظ ولا يُعتبر الكل فاشلًا إلا عند الإلغاء. للصوت أُضيف --embed-metadata
- ui/AppShell.kt: شريط عائم بحبة (الرئيسية، المحلي، الإعدادات) مع نقطة على المحلي أثناء التنزيل، يختفي عند ظهور الكيبورد. MainActivity صار enableEdgeToEdge
- المحلي (LibraryScreen): التنزيلات الجارية ثم ملفات MediaStore تحت مجلد Sesos مع فلاتر وفتح ومشاركة وحذف بتأكيد. على أندرويد 9 وأقل طُلبت أذونات القراءة والكتابة عند التشغيل
- الإعدادات (SettingsScreen): الحسابات انتقلت إليها من الرئيسية، مع المحرك وتحديثه ومكان الحفظ وحول
- SesosScreen.kt حُذف وتوزعت محتوياته على Components.kt وHomeScreen.kt وSettingsScreen.kt
- إن فشل البناء: أرسل لوج compileReleaseKotlin، معظم الكود جديد ولم يُترجم فعليًا

## إصلاح: تعارض SesosScreen.kt القديم
- فشل البناء بـ Conflicting overloads لـ InfoCard وContext.open لأن نسخ الحزمة فوق المستودع لا يحذف SesosScreen.kt القديم
- الحل: الملف موجود الآن ويحتوي سطر package فقط حتى يستبدل القديم عند النسخ. يمكن حذفه يدويًا لاحقًا
- درس: عند حذف ملف من المشروع اترك بدله ملفًا فارغًا في الحزمة، أو احذفه يدويًا من المستودع

## مهمة: فشل التحميل + واجهة سلسة + splash متحركة
- سبب النص الفارغ بالرئيسية: Text خارج Surface يأخذ لون Black الافتراضي فيختفي على الخلفية السوداء. الحل: SesosTheme يوفّر LocalContentColor = onBackground
- فشل التحميل: الـ traceback ينتهي غالبًا بـ HTTP 403 من يوتيوب (عميلا android_vr وandroid_sdkless محجوبان). الحل في Engine.kt:
  - إزالة -v (كان يملأ رسالة الخطأ بالـ traceback)
  - تجربة عملاء يوتيوب بالتسلسل: default بدون android_vr، ثم tv/web_safari/mweb، ثم ios/web_safari
  - عند 403 أو sign in أو nsig: تحديث yt-dlp (قناة NIGHTLY) مرة واحدة ثم إعادة المحاولة
  - تحديث صامت بالخلفية عند التشغيل كل 12 ساعة (Engine.autoUpdate)
  - Engine.friendly يختصر الخطأ إلى سطر واحد، وبطاقة الخطأ فيها زر إعادة المحاولة
- الخيوط: DownloadService حتى 3 تنزيلات معًا (Semaphore) وإلغاء الـ scope عند الإغلاق، البحث بمجمّع خاص limitedParallelism(3) مع كاش 10 دقائق (Yt.kt)، تحديثات التقدم كل 1% فقط
- الأنيميشن: AnimatedContent للتبديل بين التبويبات، شريط التنقل ينزلق، animateItem بمفاتيح ثابتة بكل القوائم، staggerIn لبطاقات التصنيفات، شريط تقدم ناعم، كاش لصور المكتبة
- Splash: core-splashscreen + SplashOverlay.kt (نفس الأيقونة والخلفية، حرف S يتنفس، شارة التحميل ترتفع وترتد، موجة دائرية، ثم تكبير واختفاء). الطبقتان splash_s.png وsplash_badge.png مقتطعتان من أيقونة التطبيق
- لم يُجرَّب البناء. إن ظهر خطأ ترجمة نصلحه: أرجح موضع هو YoutubeDL.UpdateChannel.NIGHTLY (يمكن استبداله بـ STABLE)
