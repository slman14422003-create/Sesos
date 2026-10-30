# youtubedl-android + its reflection-based JSON mapping
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.apache.commons.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn java.lang.invoke.StringConcatFactory

# GeckoView is called from native code by name (JNI), so its Java side stays intact
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.thirdparty.** { *; }
-keep class org.mozilla.gecko.annotation.** { *; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.WrapForJNI <methods>; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.WrapForJNI <fields>; }
-dontwarn org.mozilla.**

# Native-method holders must keep their names
-keepclasseswithmembernames class * { native <methods>; }
