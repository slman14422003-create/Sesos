plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ytdlAndroid = "0.18.1"
val ksFile: String? = System.getenv("KEYSTORE_FILE")

configurations.configureEach {
    resolutionStrategy.eachDependency {
        // Newer releases require AGP 9.1+; keep the versions AGP 8.7.x accepts.
        if (requested.group == "androidx.lifecycle") useVersion("2.8.7")
        if (requested.group == "androidx.core" && (requested.name == "core" || requested.name == "core-ktx")) useVersion("1.15.0")
    }
}

android {
    namespace = "com.sesos.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sesos.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (ksFile != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // R8 gives only a few MB here; the size is native libs. Turn on only after testing.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ksFile != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    // yt-dlp + python + ffmpeg are shipped as native libs and must be extracted to disk.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // GeckoView may be built with a newer Kotlin than 2.0.21
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.14.0")

    // Real Firefox engine (not WebView): Meta pages render and log in normally.
    // Pinned to the exact version Gradle resolved in the failing CI run.
    implementation("org.mozilla.geckoview:geckoview:159.0.20261022150525")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // yt-dlp engine (bundled Python + yt-dlp) and ffmpeg for merging / mp3 conversion
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlAndroid")
}
