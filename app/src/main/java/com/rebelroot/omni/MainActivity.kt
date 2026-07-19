/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep

import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rebelroot.omni.browser.BrowserScreen
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.media.DownloadManagerScreen
import com.rebelroot.omni.media.player.VideoPlayerScreen
import com.rebelroot.omni.settings.SettingsScreen
import com.rebelroot.omni.settings.AppearanceScreen
import com.rebelroot.omni.settings.WallpaperScreen
import com.rebelroot.omni.history.HistoryScreen
import com.rebelroot.omni.bookmarks.BookmarksScreen
import com.rebelroot.omni.tools.locker.PrivateLockerScreen
import com.rebelroot.omni.tools.qrcode.QrToolsScreen
import com.rebelroot.omni.ui.theme.OmniTheme
import java.io.File
import com.rebelroot.omni.browser.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

class MainActivity : FragmentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    // Runtime notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — no action needed, engine checks at post time */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = try {
            val datastore = newBase.dataStore
            val key = androidx.datastore.preferences.core.stringPreferencesKey("selected_language")
            var savedLang = "en"
            runBlocking {
                val prefs = datastore.data.first()
                savedLang = prefs[key] ?: "en"
            }
            savedLang
        } catch (e: Exception) {
            "en"
        }
        
        val locale = java.util.Locale.forLanguageTag(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activeActivity = java.lang.ref.WeakReference(this)

        // --- Critical fix for PrintManager ("Can print only from an activity") ---
        // When attachBaseContext() swaps the base context with createConfigurationContext(),
        // the Android framework calls setOuterContext(activity) only on the ORIGINAL base
        // context, not on our replacement ContextImpl. This causes getSystemService(PRINT_SERVICE)
        // to create a PrintManager bound to a raw ContextImpl (not this Activity), making
        // PrintManager.print() throw "Can print only from an activity".
        // We fix it here by explicitly setting the outer context of our replacement ContextImpl
        // to this Activity, exactly mirroring what Activity.attach() does for the original context.
        try {
            val m = baseContext.javaClass.getDeclaredMethod(
                "setOuterContext", android.content.Context::class.java
            )
            m.isAccessible = true
            m.invoke(baseContext, this)
            android.util.Log.i("MainActivity", "✅ ContextImpl outer context fixed for PrintManager")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "⚠️ Could not fix ContextImpl outer context: $e")
        }
        // -------------------------------------------------------------------------

        // --- Global Robust Uncaught Exception Handler (with crash-loop protection) ---
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("OMNI_CRASH", "Uncaught Exception caught!", throwable)
            val crashPrefs = getSharedPreferences("omni_crash_prefs", android.content.Context.MODE_PRIVATE)
            val crashMsg = throwable.localizedMessage ?: throwable.toString()
            crashPrefs.edit().putString("last_crash_msg", crashMsg).apply()

            // Crash-loop detection: count consecutive crashes within a 60s window
            val now = System.currentTimeMillis()
            val lastCrashTime = crashPrefs.getLong("last_crash_time", 0)
            var crashCount = crashPrefs.getInt("crash_loop_count", 0)
            if (now - lastCrashTime < 60_000) {
                crashCount++
            } else {
                crashCount = 1
            }
            crashPrefs.edit()
                .putLong("last_crash_time", now)
                .putInt("crash_loop_count", crashCount)
                .apply()

            if (crashCount >= 3) {
                // Crash loop detected — stop auto-restarting, exit cleanly
                android.util.Log.e("OMNI_CRASH", "Crash loop detected ($crashCount crashes). Not restarting.")
                crashPrefs.edit().putInt("crash_loop_count", 0).apply()
                android.os.Process.killProcess(android.os.Process.myPid())
                java.lang.System.exit(1)
                return@setDefaultUncaughtExceptionHandler
            }

            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            java.lang.System.exit(10)
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        // App started successfully — reset the crash-loop counter so that
        // an isolated crash later doesn't count toward the rapid-restart threshold.
        getSharedPreferences("omni_crash_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putInt("crash_loop_count", 0).apply()


        val intentUrl = intent?.dataString
        val isDirectVideo = !intentUrl.isNullOrEmpty() && (intentUrl.contains("autoplay=native") || intentUrl.endsWith(".mp4"))
        if (!intentUrl.isNullOrEmpty() && !isDirectVideo) {
            android.util.Log.i("MainActivity", "🎬 onCreate intent URL detected: $intentUrl")
            browserViewModel.pendingIntentUrl = intentUrl
        }

        setContent {
            val context = LocalContext.current
            val currentLanguage = browserViewModel.selectedLanguageCode
            val localizedContext = remember(currentLanguage) {
                val locale = java.util.Locale.forLanguageTag(currentLanguage)
                java.util.Locale.setDefault(locale)
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.createConfigurationContext(config)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity,
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides this@MainActivity
            ) {
                OmniTheme(darkTheme = browserViewModel.isDarkThemeEnabled, accentTheme = browserViewModel.selectedAccentTheme, amoledMode = browserViewModel.isAmoledMode, dynamicColor = browserViewModel.isDynamicColorEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val navController = rememberNavController()

                    // Ensure GeckoRuntime and engines are loaded immediately on app start.
                    // Guarded with try-catch so a Gecko init failure does not crash the
                    // entire composition (which would otherwise show a black screen).
                    var geckoInitFailed by remember { androidx.compose.runtime.mutableStateOf(false) }
                    if (!geckoInitFailed) {
                        try {
                            // NOTE: The return value is intentionally captured and "touched"
                            // so R8/ProGuard cannot treat this call as dead code. Without it,
                            // R8 strips getGeckoRuntime() entirely (it sees a discarded result
                            // and an unobservable field write), leaving GeckoView uninitialized
                            // and the app showing only the dark window background (black screen).
                            val rt = browserViewModel.getGeckoRuntime(this)
                            if (rt == null) throw IllegalStateException("GeckoRuntime unavailable")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "❌ GeckoRuntime init failed", e)
                            geckoInitFailed = true
                        }
                    }

                    // Show a graceful error screen instead of a black screen when
                    // the GeckoView engine fails to initialize in release builds.
                    if (geckoInitFailed) {
                        GeckoErrorScreen(
                            onRetry = {
                                geckoInitFailed = false
                                try {
                                    val rt = browserViewModel.getGeckoRuntime(this)
                                    if (rt == null) throw IllegalStateException("GeckoRuntime unavailable")
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "❌ GeckoRuntime retry failed", e)
                                    geckoInitFailed = true
                                }
                            }
                        )
                        return@Surface
                    }

                    val isOnboardingCompleted = remember {
                        runBlocking {
                            val prefs = context.dataStore.data.first()
                            prefs[BrowserViewModel.ONBOARDING_COMPLETED_KEY] ?: false
                        }
                    }

                    val isLanguageSelectionDone = remember {
                        runBlocking {
                            val prefs = context.dataStore.data.first()
                            prefs[BrowserViewModel.LANGUAGE_SELECTION_DONE_KEY] ?: false
                        }
                    }

                    val startDestination = if (isDirectVideo) {
                        val encodedPath = android.util.Base64.encodeToString(intentUrl!!.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                        android.util.Log.i("MainActivity", "🎬 Startup direct video player route: video_player/$encodedPath")
                        "video_player/$encodedPath"
                    } else if (!isLanguageSelectionDone) {
                        "language_selection"
                    } else if (!isOnboardingCompleted) {
                        "onboarding"
                    } else {
                        "browser"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // Language Selection Screen (first launch)
                        composable("language_selection") {
                            com.rebelroot.omni.onboarding.LanguageSelectionScreen(
                                viewModel = browserViewModel,
                                context = context,
                                onFinish = {
                                    val nextRoute = if (!isOnboardingCompleted) "onboarding" else "browser"
                                    navController.navigate(nextRoute) {
                                        popUpTo("language_selection") { inclusive = true }
                                    }
                                    this@MainActivity.recreate()
                                }
                            )
                        }

                        // Starting Onboarding Presentation Screen
                        composable("onboarding") {
                            com.rebelroot.omni.onboarding.OnboardingScreen(
                                viewModel = browserViewModel,
                                context = context,
                                onFinish = {
                                    navController.navigate("browser") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Core Browser Screen
                        composable("browser") {
                            BrowserScreen(
                                viewModel = browserViewModel,
                                onOpenLocker = { navController.navigate("locker") },
                                onOpenQrTools = { navController.navigate("qr_tools") },
                                onOpenDownloads = { navController.navigate("downloads") },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenHistory = { navController.navigate("history") },
                                onOpenBookmarks = { navController.navigate("bookmarks") },
                                onPlayOnlineStream = { url, pageUrl ->
                                    android.util.Log.i("MainActivity", "🎬 onPlayOnlineStream triggered! url=$url, pageUrl=$pageUrl")
                                    val encodedPath = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    val encodedPageUrl = android.util.Base64.encodeToString(pageUrl.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    val route = "video_player/$encodedPath?pageUrl=$encodedPageUrl"
                                    android.util.Log.i("MainActivity", "🎬 Navigating to: $route")
                                    navController.navigate(route)
                                },
                                onExitBrowser = {
                                    this@MainActivity.finishAffinity()
                                }
                            )
                        }

                        // Sandboxed Encrypted Vault Room
                        composable("locker") {
                            PrivateLockerScreen(
                                activity = this@MainActivity,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ZXing Generator + Play Services Scanner
                        composable("qr_tools") {
                            QrToolsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenUrlInBrowser = { url ->
                                    browserViewModel.loadUrl(url)
                                    navController.popBackStack("browser", inclusive = false)
                                }
                            )
                        }

                        // Video Downloader Manager Screen
                        composable("downloads") {
                            DownloadManagerScreen(
                                engine = browserViewModel.streamDownloadEngine,
                                onNavigateBack = { navController.popBackStack() },
                                onPlayVideo = { file ->
                                    val encodedPath = android.util.Base64.encodeToString(file.absolutePath.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    navController.navigate("video_player/$encodedPath")
                                }
                            )
                        }

                        // Swipe-gesture Media3 Video Player
                        composable(
                            route = "video_player/{filePath}?pageUrl={pageUrl}",
                            arguments = listOf(
                                navArgument("filePath") { type = NavType.StringType },
                                navArgument("pageUrl") { 
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                            val pageUrlEncoded = backStackEntry.arguments?.getString("pageUrl") ?: ""
                            val pageUrl = if (pageUrlEncoded.isNotEmpty()) {
                                try {
                                    val decodedBytes = android.util.Base64.decode(pageUrlEncoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    String(decodedBytes, Charsets.UTF_8)
                                } catch (e: Exception) {
                                    ""
                                }
                            } else {
                                ""
                            }
                            VideoPlayerScreen(
                                videoPath = filePath,
                                referrerUrl = pageUrl,
                                downloadEngine = browserViewModel.streamDownloadEngine,
                                viewModel = browserViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Unified Settings Panel
                        composable("settings") {
                            SettingsScreen(
                                viewModel = browserViewModel,
                                onNavigateBack = {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate("browser") {
                                            popUpTo("browser") { inclusive = true }
                                        }
                                    }
                                },
                                onOpenUrl = { url ->
                                    browserViewModel.loadUrl(url)
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack("browser", inclusive = false)
                                    } else {
                                        navController.navigate("browser") {
                                            popUpTo("browser") { inclusive = true }
                                        }
                                    }
                                },
                                onLanguageChanged = {
                                    this@MainActivity.recreate()
                                },
                                onOpenAppearance = {
                                    navController.navigate("appearance")
                                },
                                onOpenWallpapers = {
                                    navController.navigate("wallpapers")
                                }
                            )
                        }

                        // Appearance Settings Screen
                        composable("appearance") {
                            AppearanceScreen(
                                viewModel = browserViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Wallpaper Settings Screen
                        composable("wallpapers") {
                            WallpaperScreen(
                                viewModel = browserViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Browser History Screen
                        composable("history") {
                            HistoryScreen(
                                viewModel = browserViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenUrl = { url ->
                                    browserViewModel.loadUrl(url)
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack("browser", inclusive = false)
                                    } else {
                                        navController.navigate("browser") {
                                            popUpTo("browser") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        // Browser Bookmarks Screen
                        composable("bookmarks") {
                            BookmarksScreen(
                                viewModel = browserViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenUrl = { url ->
                                    browserViewModel.loadUrl(url)
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack("browser", inclusive = false)
                                    } else {
                                        navController.navigate("browser") {
                                            popUpTo("browser") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Listen for external links opening while browser is default app,
                    // pop back to the browser screen so the user sees the loaded page
                    androidx.compose.runtime.LaunchedEffect(browserViewModel.openBrowserScreenEvent) {
                        if (browserViewModel.openBrowserScreenEvent) {
                            browserViewModel.consumeOpenBrowserScreenEvent()
                            navController.popBackStack("browser", inclusive = false)
                        }
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val intentUrl = intent.dataString
        if (!intentUrl.isNullOrEmpty()) {
            android.util.Log.i("MainActivity", "🎬 onNewIntent URL detected: $intentUrl")
            if (browserViewModel.isNativePlayerEnabled && browserViewModel.isDirectVideoUrl(intentUrl)) {
                android.util.Log.i("MainActivity", "🎬 Direct video detected in onNewIntent: $intentUrl. Launching native player...")
                val callback = browserViewModel.onPlayVideoRequestReceived
                if (callback != null) {
                    callback.invoke(intentUrl, intentUrl)
                } else {
                    browserViewModel.pendingVideoUrl = intentUrl
                }
            } else {
                browserViewModel.loadUrl(intentUrl)
                // Trigger navigation back to browser screen (e.g. when default browser opens a link from Settings)
                browserViewModel.triggerOpenBrowserScreen()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            // Trim image loading memory caches (Coil) to reclaim memory
            coil.Coil.imageLoader(this).memoryCache?.clear()
            
            // Clean up JVM garbage collection if memory is getting low
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                System.gc()
            }
            android.util.Log.d("MainActivity", "🧹 onTrimMemory level $level: Purged image loader memory cache and triggered GC")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to trim memory: $e")
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        browserViewModel.isInPictureInPictureMode = isInPictureInPictureMode
    }

    // Note: Auto-PiP on home press was removed because requestedOrientation changes
    // (from the fullscreen button) incorrectly triggered onUserLeaveHint on some devices,
    // causing PiP to be entered instead of going fullscreen. Use the PiP button in the player.

    override fun onDestroy() {
        if (activeActivity?.get() == this) {
            activeActivity = null
        }
        super.onDestroy()
    }

    companion object {
        private var activeActivity: java.lang.ref.WeakReference<MainActivity>? = null

        fun getActiveActivity(): MainActivity? {
            return activeActivity?.get()
        }
    }
}

/**
 * Graceful fallback screen shown when the GeckoView engine fails to initialize
 * (typically in release builds where R8/ProGuard may strip reflection-loaded classes).
 * Prevents the opaque black-screen crash-loop by surfacing a clear, actionable error.
 */
@Keep
@Composable
private fun GeckoErrorScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Browser engine failed to start",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "The rendering engine could not be initialized. This usually happens " +
                        "after a corrupted install or when system resources are low. " +
                        "Tap retry, or reinstall the app if the problem persists.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F8CFF)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Retry")
            }
        }
    }
}
