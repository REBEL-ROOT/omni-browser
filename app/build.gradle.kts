import java.util.Properties

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
        versionCode = 24
        versionName = "1.2.4.1.1-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        // Fixes deprecation: replaces aaptOptions.ignoreAssetsPattern
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"

        // Restrict bundled translations to only the languages the app actively supports
        localeFilters += listOf(
            "en", "hi", "es", "fr", "de", "pt", "ru", "ja", "zh", "ar"
        )
    }

    flavorDimensions.add("abi")
    productFlavors {
        create("universal") {
            dimension = "abi"
            // Includes all architectures
        }
        create("arm") {
            dimension = "abi"
            ndk {
                abiFilters.add("armeabi-v7a")
            }
        }
        create("aarch64") {
            dimension = "abi"
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val stream = localPropertiesFile.inputStream()
        try {
            localProperties.load(stream)
        } finally {
            stream.close()
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release-new.keystore")
            storePassword = localProperties.getProperty("keystore.password") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = localProperties.getProperty("keystore.alias") ?: System.getenv("KEYSTORE_ALIAS") ?: ""
            keyPassword = localProperties.getProperty("keystore.alias.password") ?: System.getenv("KEYSTORE_ALIAS_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // R8 (ProGuard) over-optimization caused two release-only regressions
            // that never reproduced on the uncompressed test APK (minify off):
            //   1. LAG: R8 inlined the browser-screen Compose tree into a single
            //      mega-method (obfuscated as `xu.a`). On first render ART had to
            //      JIT-compile ~6.5 MB of code on the main thread, producing
            //      16-22s frame gaps and "Skipped 67 frames" stalls.
            //   2. ExceptionInInitializerError after onboarding: R8 optimizing /
            //      shrinking static initializers (the prior crash-loop was exactly
            //      this — snakeyaml's <clinit> throwing and taking GeckoRuntime
            //      init down with it).
            // Disabling minify makes release behave like the smooth, stable
            // uncompressed test APK. The APK is larger (~500 MB) but matches the
            // reference build's smoothness and eliminates R8-induced crashes.
            // proguard-rules.pro is retained (it is ignored when minify is off,
            // but keeps @Keep honored if minify is ever re-enabled).
            isMinifyEnabled = false
            isShrinkResources = false
            // Emit native debug symbols so Play Console can symbolicate GeckoView /
            // SQLCipher NDK crash traces. SYMBOL_TABLE (not FULL) because the
            // prebuilt .so files ship stripped — this still recovers function
            // names. Generated at:
            //   app/build/outputs/native-debug-symbols/<flavor>Release/native-debug-symbols.zip
            // Upload that zip to Play Console per version; does not require
            // re-uploading the AAB itself.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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
        buildConfig = false
        aidl       = false
    }

    packaging {
        jniLibs {
            // false = keep native libraries uncompressed and aligned to 16 KB page boundaries for Android 15+ compatibility.
            useLegacyPackaging = false
            pickFirsts.addAll(listOf("**/libjsc.so", "**/libc++_shared.so"))
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE.md",
                "/META-INF/*.kotlin_module",
                "/*.kotlin_metadata",
                "/kotlin/**",
                "/kotlinx/**",
                "**/junit/**",
                "**/androidx/test/**",
                "**/VERSION.txt",
                "**/version.txt",
                "**/*.proto",
                "**/*.bin"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Exclude io.opencensus transitive dependencies pulled in by io.grpc.
// These libraries are telemetry/tracing stubs and are not used by the app
// at runtime. F-Droid scanner flags them as "Tracker" anti-features.
configurations.all {
    exclude(group = "io.opencensus", module = "opencensus-api")
    exclude(group = "io.opencensus", module = "opencensus-proto")
    exclude(group = "io.opencensus", module = "opencensus-contrib-grpc-metrics")
}

dependencies {
    // === Core Android & Lifecycle ===
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.3")

    // === Mozilla GeckoView Engine (Architecture-specific) ===
    val geckoviewVersion = "145.0.20251124145406"

    // Universal flavor: single artifact (avoids capability collision between ABIs)
    "universalImplementation"("org.mozilla.geckoview:geckoview:$geckoviewVersion")

    // Architecture-specific flavors
    "armImplementation"("org.mozilla.geckoview:geckoview-armeabi-v7a:$geckoviewVersion")
    "aarch64Implementation"("org.mozilla.geckoview:geckoview-arm64-v8a:$geckoviewVersion")

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
    // ZXing: QR code decoder and generator (pure open-source FOSS, no play services)
    implementation("com.google.zxing:core:3.5.3")

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
