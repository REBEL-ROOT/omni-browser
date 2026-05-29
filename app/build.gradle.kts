plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.omni.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omni.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

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
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // === Core Android & Lifecycle ===
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // === Mozilla GeckoView Engine ===
    implementation("org.mozilla.geckoview:geckoview-stable:130.0.20240905140134")

    // === Jetpack Compose ===
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // === Haze Glassmorphism Backdrop Blur ===
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation("dev.chrisbanes.haze:haze-blur:0.7.3")

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
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.7")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // === Fonts ===
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
}
