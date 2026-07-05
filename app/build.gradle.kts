plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rebelroot.omni"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rebelroot.omni"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.2.0"


        ndk {
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        // Fixes deprecation: replaces aaptOptions.ignoreAssetsPattern
        // Ensure WebExtension files starting with underscores (e.g. _locales)
        // are not excluded by the Android packaging process.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"

        // Fixes deprecation: replaces resourceConfigurations
        // Restrict bundled translations to only the languages the app actively
        // supports — removes ~2MB of unused locale data from AAPT.
        localeFilters += listOf(
            "en", "hi", "es", "fr", "de", "pt", "ru", "ja", "zh", "ar"
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "omnibrowserrelease"
            keyAlias = "omni-release"
            keyPassword = "omnibrowserrelease"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // R8 full mode: more aggressive dead-code removal and class merging
            // than classic ProGuard — safe for Compose + GeckoView when keep
            // rules are in place.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xno-param-assertions")
        }
    }

    buildFeatures {
        compose = true
        // Disable features we don't use — each one adds overhead
        buildConfig = false
        aidl       = false
        // Removed deprecated renderScript = false
    }

    packaging {
        jniLibs {
            // false = keep native libraries uncompressed and aligned to 16 KB page boundaries for Android 15+ compatibility.
            useLegacyPackaging = false
            pickFirsts.addAll(listOf("**/libjsc.so", "**/libc++_shared.so"))
        }
        resources {
            excludes += listOf(
                // License / legal metadata nobody reads at runtime
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE.md",
                // Kotlin compiler metadata (not needed at runtime)
                "/META-INF/*.kotlin_module",
                "/*.kotlin_metadata",
                "/kotlin/**",
                "/kotlinx/**",
                // Unused test runner classes that leak into release builds
                "**/junit/**",
                "**/androidx/test/**",
                // Duplicated version files from transitive deps
                "**/VERSION.txt",
                "**/version.txt",
                "**/*.proto",
                "**/*.bin",
            )
        }
    }

    // ndk.abiFilters already restricts to arm64-v8a — no splits needed.
}

dependencies {
    // === Core Android & Lifecycle ===
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.3")

    // === Mozilla GeckoView Engine (arm64-v8a only — matches ndk abiFilters) ===
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:145.0.20251124145406")

    // === Jetpack Compose ===
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    // ui-tooling-preview only needed in debug — never include in release APK
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // === Haze Glassmorphism Backdrop Blur ===
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // === QR / Barcode ===
    // Play Code Scanner: zero-permission, dynamically loaded via Play Services
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Bundled ML Kit for offline / screenshot QR scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // ZXing: QR code generator only (tiny, no scanner runtime)
    implementation("com.google.zxing:core:3.5.3")

    // === Google Sign-In ===
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // === Security & Storage ===
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // === Room + SQLCipher Encrypted DB ===
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")


    // === Image Loading ===
    implementation("io.coil-kt:coil-compose:2.7.0")

    // === AndroidX Media3 / ExoPlayer ===
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")

    // === CameraX ===
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}
