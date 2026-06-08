plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.rebelroot.omni"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rebelroot.omni"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Configure aaptOptions to ensure WebExtension files starting with underscores (e.g. _locales)
        // are not ignored or excluded by the Android packaging process.
        aaptOptions {
            ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Highly stable compiler version matching Kotlin 1.9.24
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Only include arm64-v8a native libs in release (already filtered by ndk abiFilters,
            // but this ensures no stray .so files from transitive dependencies)
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
                "/META-INF/*.kotlin_module",
                "/*.kotlin_metadata",
                "/kotlin/**",
                "/kotlinx/**"
            )
        }
    }
}

dependencies {
    // === Core Android & Lifecycle ===
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0") // Required for Theme.Material3.DayNight.NoActionBar
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // === Mozilla GeckoView Engine ===
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:145.0.20251124145406")

    // === Jetpack Compose ===
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // === Haze Glassmorphism Backdrop Blur ===
    implementation("dev.chrisbanes.haze:haze:0.7.3")


    // === ML Kit Document Scanner (Zero Permission) ===
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // === Google Play Code Scanner (Zero Permission) ===
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // === ZXing QR Generator ===
    implementation("com.google.zxing:core:3.5.3")

    // === Biometrics & Modern Encryption ===
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // EncryptedFile
    implementation("androidx.datastore:datastore-preferences:1.1.1")   // Preferences storage

    // === Room Database with SQLCipher Encryption ===
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // === WireGuard VPN SDK ===
    implementation("com.wireguard.android:tunnel:1.0.20260102")

    // === ML Kit Translation ===
    implementation("com.google.mlkit:translate:17.0.3")

    // === Coil (Image Loader) ===
    implementation("io.coil-kt:coil-compose:2.7.0")

    // === AndroidX Media3 (ExoPlayer & Session) ===
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")

    // === Fonts ===
    // NOTE: Removed ui-text-google-fonts (saved ~8MB) — app only uses FontFamily.SansSerif/Monospace
    // If Google Fonts are needed later, add individual font files to res/font/ instead
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.24")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24")
    }
}

