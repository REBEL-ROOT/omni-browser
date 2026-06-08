package com.rebelroot.omni

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import com.rebelroot.omni.history.HistoryScreen
import com.rebelroot.omni.tools.locker.PrivateLockerScreen
import com.rebelroot.omni.tools.qrcode.QrToolsScreen
import com.rebelroot.omni.tools.scanner.DocumentScannerScreen
import com.rebelroot.omni.ui.theme.OmniTheme
import java.io.File
import java.net.URLEncoder

class MainActivity : FragmentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentUrl = intent?.dataString
        val isDirectVideo = !intentUrl.isNullOrEmpty() && (intentUrl.contains("autoplay=native") || intentUrl.endsWith(".mp4"))
        if (!intentUrl.isNullOrEmpty() && !isDirectVideo) {
            android.util.Log.i("MainActivity", "🎬 onCreate intent URL detected: $intentUrl")
            browserViewModel.pendingIntentUrl = intentUrl
        }

        setContent {
            OmniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()

                    // Ensure GeckoRuntime and engines are loaded immediately on app start
                    browserViewModel.getGeckoRuntime(this)

                    val startDestination = if (isDirectVideo) {
                        val encodedPath = android.util.Base64.encodeToString(intentUrl!!.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                        android.util.Log.i("MainActivity", "🎬 Startup direct video player route: video_player/$encodedPath")
                        "video_player/$encodedPath"
                    } else {
                        "browser"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // Core Browser Screen
                        composable("browser") {
                            BrowserScreen(
                                viewModel = browserViewModel,
                                onOpenLocker = { navController.navigate("locker") },
                                onOpenScanner = { navController.navigate("scanner") },
                                onOpenQrTools = { navController.navigate("qr_tools") },
                                onOpenDownloads = { navController.navigate("downloads") },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenHistory = { navController.navigate("history") },
                                onPlayOnlineStream = { url, pageUrl ->
                                    android.util.Log.i("MainActivity", "🎬 onPlayOnlineStream triggered! url=$url, pageUrl=$pageUrl")
                                    val encodedPath = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    val encodedPageUrl = android.util.Base64.encodeToString(pageUrl.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                                    val route = "video_player/$encodedPath?pageUrl=$encodedPageUrl"
                                    android.util.Log.i("MainActivity", "🎬 Navigating to: $route")
                                    navController.navigate(route)
                                }
                            )
                        }

                        // Sandboxed Encrypted Vault Room
                        composable("locker") {
                            PrivateLockerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ML Kit Auto-perspective Document Scanner
                        composable("scanner") {
                            DocumentScannerScreen(
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
                                }
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
            }
        }
    }
}
