import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// DO NOT remove the ABI offsets in the onVariants block!
// baseVersionCode = 2027 on its own wouldn't clear the old 1002004 bug. 
// It only works because of the per-ABI +1M/2M/3M offsets.
val baseVersionCode = 2044
val baseVersionName = "1.2.9.4"

android {
    namespace = "com.rebelroot.omni"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rebelroot.omni"
        minSdk = 26
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = baseVersionName

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
            "en", "hi", "es", "fr", "de", "pt", "pl", "ru", "ja", "zh", "ar"
        )
    }

    flavorDimensions.add("abi")
    productFlavors {
        create("universal") {
            dimension = "abi"
            // Bundles all physical device architectures (ARM64 + ARM32), excluding emulator x86/x86_64
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
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
            // Enable R8 code minification & resource shrinking per Google Play Console recommendation.
            // Critical keep rules in proguard-rules.pro safeguard GeckoView, Compose, SnakeYAML & SQLCipher.
            isMinifyEnabled = true
            isShrinkResources = true
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
            useLegacyPackaging = true
            pickFirsts.addAll(listOf("**/libjsc.so", "**/libc++_shared.so"))
            excludes.addAll(listOf(
                "**/libminidump_analyzer.so",
                "**/libcrashhelper.so"
            ))
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

androidComponents {
    onVariants { variant ->
        val versionOffset = when (variant.flavorName) {
            "arm" -> 1000000
            "aarch64" -> 2000000
            "universal" -> 3000000
            else -> 0
        }
        variant.outputs.forEach { output ->
            output.versionCode.set(baseVersionCode + versionOffset)
        }
    }
}

// Exclude io.opencensus transitive dependencies pulled in by io.grpc.
// These libraries are telemetry/tracing stubs and are not used by the app
// at runtime. F-Droid scanner flags them as "Tracker" anti-features.
configurations.all {
    exclude(group = "io.opencensus", module = "opencensus-api")
    exclude(group = "io.opencensus", module = "opencensus-proto")
    exclude(group = "io.opencensus", module = "opencensus-contrib-grpc-metrics")
    exclude(group = "org.mozilla.telemetry", module = "glean")
    exclude(group = "org.mozilla.telemetry", module = "glean-native")
    exclude(group = "org.mozilla.components", module = "service-glean")
}

dependencies {
    // === Core Android & Lifecycle ===
    implementation("androidx.core:core-ktx:1.13.1")

    // === Unit Testing ===
    // JUnit 4 powers the JVM unit test suite (app/src/test). Required so the
    // project's existing SecurityPolicyTest and the new Offline AI tests compile
    // and run under `./gradlew test`.
    testImplementation("junit:junit:4.13.2")
    // org.json for JVM unit tests (mirrors Android's org.json API)
    testImplementation("org.json:json:20231013")
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

    // === Mozilla Firefox Sync (Application Services) ===
    val mozillaComponentsVersion = "152.0.5"
    implementation("org.mozilla.components:service-firefox-accounts:$mozillaComponentsVersion")
    implementation("org.mozilla.components:browser-storage-sync:$mozillaComponentsVersion")
    implementation("org.mozilla.components:concept-storage:$mozillaComponentsVersion")
    implementation("org.mozilla.components:concept-sync:$mozillaComponentsVersion")
    implementation("org.mozilla.components:lib-dataprotect:$mozillaComponentsVersion")
    implementation("org.mozilla.components:service-sync-logins:$mozillaComponentsVersion")
    // implementation("org.mozilla.components:support-rusthttp:$mozillaComponentsVersion")
    // implementation("org.mozilla.components:support-rustlog:$mozillaComponentsVersion")

    // === In-app BitTorrent engine (jlibtorrent) for magnet/torrent downloading ===
    implementation("com.frostwire:jlibtorrent:1.2.0.18")
    "universalImplementation"("com.frostwire:jlibtorrent-android-arm:1.2.0.18")
    "universalImplementation"("com.frostwire:jlibtorrent-android-arm64:1.2.0.18")
    "armImplementation"("com.frostwire:jlibtorrent-android-arm:1.2.0.18")
    "aarch64Implementation"("com.frostwire:jlibtorrent-android-arm64:1.2.0.18")

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
    implementation("com.wireguard.android:tunnel:1.0.20260102")

    // === Embedded Tor (kmp-tor) ===
    // Runs a real Tor daemon in-process using `resource-noexec-tor` (JNI shared lib).
    // This maintains 16 KB page alignment compatibility for Android 15+ & GeckoView.
    implementation("io.matthewnelson.kmp-tor:runtime:2.6.0")
    implementation("io.matthewnelson.kmp-tor:resource-noexec-tor:409.5.0")

    // === Room + SQLCipher Encrypted DB ===
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // === WorkManager — keeps model downloads alive after the user leaves Settings ===
    // Downloads are long-running and must not depend on a Compose screen's lifecycle.
    implementation("androidx.work:work-runtime:2.9.1")


    // === Image Loading ===
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // === AndroidX Media3 / ExoPlayer ===
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")

    // === Vosk on-device speech recognition (runtime lib, no model weights) ===
    // Loads downloaded/verified models from application-private storage.
    implementation("com.alphacephei:vosk-android:0.3.75")

    // === CameraX ===
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}

tasks.register("checkStringParity") {
    group = "verification"
    description = "Verifies that all values-*/strings.xml files contain all keys from values/strings.xml"
    doLast {
        val resDir = file("src/main/res")
        val mainStringsFile = file("src/main/res/values/strings.xml")
        check(mainStringsFile.exists()) { "Main strings.xml not found!" }

        fun extractKeys(f: File): Set<String> {
            val regex = """<string\s+name="([^"]+)"""".toRegex()
            return regex.findAll(f.readText()).map { it.groupValues[1] }.toSet()
        }

        val baseKeys = extractKeys(mainStringsFile)
        var missingFound = false

        resDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("values-") && !it.name.contains("night") && !it.name.contains("v31") }?.forEach { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) {
                val localeKeys = extractKeys(stringsFile)
                val missing = baseKeys - localeKeys
                if (missing.isNotEmpty()) {
                    println("❌ ${dir.name}/strings.xml is missing ${missing.size} keys: $missing")
                    missingFound = true
                }
            }
        }

        if (missingFound) {
            throw GradleException("String key parity check failed! Fill in missing locale keys.")
        } else {
            println("✅ All locale resource files have 100% key parity with values/strings.xml (${baseKeys.size} keys).")
        }
    }
}

// ── 16 KB page-size alignment fix for prebuilt native libraries ────────────────
// Android 15+ (16 KB page size) requires every .so ELF program header p_align
// to be >= 16 KB (0x4000). Prebuilt libraries (GeckoView, jlibtorrent, SQLCipher,
// Tor, WireGuard, Vosk, etc.) were compiled with 4 KB alignment. This hook patches
// all merged and stripped .so files before the APK is packaged.
tasks.whenTaskAdded {
    if (name.startsWith("strip") && name.endsWith("DebugSymbols")) {
        doLast {
            val script = file("elf-align-16kb.py")
            if (script.exists()) {
                val strippedDir = file("build/intermediates/stripped_native_libs")
                if (strippedDir.exists()) {
                    ProcessBuilder("python3", script.absolutePath, strippedDir.absolutePath)
                        .inheritIO()
                        .start()
                        .waitFor()
                }
            }
        }
    } else if (name.startsWith("merge") && name.endsWith("NativeLibs")) {
        doLast {
            val script = file("elf-align-16kb.py")
            if (script.exists()) {
                val mergedDir = file("build/intermediates/merged_native_libs")
                if (mergedDir.exists()) {
                    ProcessBuilder("python3", script.absolutePath, mergedDir.absolutePath)
                        .inheritIO()
                        .start()
                        .waitFor()
                }
            }
        }
    } else if (name.startsWith("package") && !name.contains("Resource") && !name.contains("Manifest")) {
        doFirst {
            val script = file("elf-align-16kb.py")
            if (script.exists()) {
                val intermediates = file("build/intermediates")
                if (intermediates.exists()) {
                    val strippedDir = file("build/intermediates/stripped_native_libs")
                    if (strippedDir.exists()) {
                        ProcessBuilder("python3", script.absolutePath, strippedDir.absolutePath)
                            .inheritIO()
                            .start()
                            .waitFor()
                    }
                    val mergedDir = file("build/intermediates/merged_native_libs")
                    if (mergedDir.exists()) {
                        ProcessBuilder("python3", script.absolutePath, mergedDir.absolutePath)
                            .inheritIO()
                            .start()
                            .waitFor()
                    }
                }
            }
        }
    }
}

