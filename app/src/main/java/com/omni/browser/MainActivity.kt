package com.omni.browser

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
import com.omni.browser.browser.BrowserScreen
import com.omni.browser.browser.BrowserViewModel
import com.omni.browser.media.DownloadManagerScreen
import com.omni.browser.media.player.VideoPlayerScreen
import com.omni.browser.settings.SettingsScreen
import com.omni.browser.history.HistoryScreen
import com.omni.browser.tools.locker.PrivateLockerScreen
import com.omni.browser.tools.qrcode.QrToolsScreen
import com.omni.browser.tools.scanner.DocumentScannerScreen
import com.omni.browser.ui.theme.OmniTheme
import java.io.File
import java.net.URLEncoder

class MainActivity : FragmentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OmniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()

                    // Ensure GeckoRuntime and engines are loaded immediately on app start
                    browserViewModel.getGeckoRuntime(this)

                    NavHost(
                        navController = navController,
                        startDestination = "browser"
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
                                onPlayOnlineStream = { url ->
                                    val encodedPath = java.net.URLEncoder.encode(url, "UTF-8")
                                    navController.navigate("video_player/$encodedPath")
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
                                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                                    navController.navigate("video_player/$encodedPath")
                                }
                            )
                        }

                        // Swipe-gesture Media3 Video Player
                        composable(
                            route = "video_player/{filePath}",
                            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                            VideoPlayerScreen(
                                videoPath = filePath,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // Unified Settings Panel
                        composable("settings") {
                            SettingsScreen(
                                viewModel = browserViewModel,
                                ffmpegLoader = browserViewModel.ffmpegLoader,
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
}
