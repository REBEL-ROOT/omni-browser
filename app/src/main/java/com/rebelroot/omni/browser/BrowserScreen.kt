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

package com.rebelroot.omni.browser

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.ui.theme.getUiSizeConfig
import androidx.compose.ui.draw.blur
import androidx.compose.ui.viewinterop.AndroidView

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView
import com.rebelroot.omni.R
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.privacy.FireButton
import com.rebelroot.omni.tools.qrcode.BarcodeGenerator
import android.graphics.Bitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
 import androidx.compose.foundation.gestures.rememberTransformableState
 import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenLocker: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onPlayOnlineStream: (String, String) -> Unit,
    onExitBrowser: () -> Unit
) {
    val context = LocalContext.current
    val keyguardManager = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }
    val unlockLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.isIncognitoUnlocked = true
        } else {
            viewModel.isIncognitoUnlocked = false
        }
    }
    
    fun tryUnlockIncognito() {
        if (keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Unlock Incognito Tabs",
                "Authenticate to view your private tabs"
            )
            if (intent != null) {
                unlockLauncher.launch(intent)
            } else {
                viewModel.isIncognitoUnlocked = true
            }
        } else {
            viewModel.isIncognitoUnlocked = true
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                if (viewModel.isIncognitoMode && viewModel.lockIncognito) {
                    viewModel.isIncognitoUnlocked = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel.isIncognitoMode) {
        if (viewModel.isIncognitoMode && viewModel.lockIncognito && !viewModel.isIncognitoUnlocked) {
            tryUnlockIncognito()
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val coroutineScope = rememberCoroutineScope()
    val config = getUiSizeConfig(viewModel.uiScale)
    var dragAmountAccumulated by remember { mutableStateOf(0f) }

    val showHomeScreen = viewModel.currentUrl == "about:blank" || viewModel.currentUrl.isEmpty()
    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }

    
    var showMenu by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var showSiteInfoSheet by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)) }
    var isInputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // Video detection states
    val detectedMedia by viewModel.mediaInterceptor.detectedMedia.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var isAlohaBannerDismissed by remember { mutableStateOf(false) }
    val nonDrmMedia = remember(detectedMedia) { detectedMedia.filter { !it.isDrmProtected } }
    val showAlohaBanner = nonDrmMedia.isNotEmpty() && !isAlohaBannerDismissed && !showHomeScreen && !viewModel.isReaderModeActive
    var isScrollNavBarVisible by remember { mutableStateOf(true) }
    var isNavHideEnabled by remember { mutableStateOf(true) }
    var currentScrollPos by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val isKeyboardVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible

    val isEditMode = activeTab?.isEditModeEnabled == true
    val navHideTopActive = isNavHideEnabled && viewModel.navBarHideTop
    val navHideBottomActive = isNavHideEnabled && viewModel.navBarHideBottom
    val topBarFraction by animateFloatAsState(
        targetValue = if (isKeyboardVisible && !isInputFocused && !isEditMode) 1f else if (!viewModel.isFullscreen && !showHomeScreen && navHideTopActive && !isScrollNavBarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "topBarHide"
    )
    val bottomBarFraction by animateFloatAsState(
        targetValue = if (isKeyboardVisible && !isInputFocused && !isEditMode) 1f else if (!viewModel.isFullscreen && !showHomeScreen && navHideBottomActive && !isScrollNavBarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "bottomBarHide"
    )

    val hasActiveUserExtensions = remember(viewModel.userExtensions.toList()) {
        viewModel.userExtensions.any { it.metaData.enabled }
    }
    LaunchedEffect(viewModel.currentUrl) {
        isAlohaBannerDismissed = false
        isScrollNavBarVisible = true
    }
    // Re-show the banner whenever a brand-new video URL is detected (e.g. next video starts)
    // Observing the count via flow is the thread-safe Compose-idiomatic approach.
    LaunchedEffect(viewModel.mediaInterceptor.detectedMedia) {
        var lastKnownCount = 0
        viewModel.mediaInterceptor.detectedMedia.collect { mediaList ->
            if (mediaList.size > lastKnownCount) {
                // New media was added — re-show banner even if user dismissed it
                isAlohaBannerDismissed = false
            }
            lastKnownCount = mediaList.size
        }
    }
    LaunchedEffect(isNavHideEnabled) {
        if (!isNavHideEnabled) {
            isScrollNavBarVisible = true
        }
    }
    var selectedMediaItem by remember { mutableStateOf<MediaInterceptor.DetectedMedia?>(null) }
    var showExtensionsSheet by remember { mutableStateOf(false) }
    var extensionToDelete by remember { mutableStateOf<org.mozilla.geckoview.WebExtension?>(null) }
    var builtInExtensionToDelete by remember { mutableStateOf<String?>(null) }

    // Fullscreen download overlay — hoisted outside the if-block so state survives
    // fullscreen entry/exit transitions and doesn't reset on every recomposition.
    var showFullscreenDownloadBtn by remember { mutableStateOf(true) }
    
    // Auto-Scroll and Player Settings states
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var isAutoScrollPaused by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableStateOf(1) }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }
    var isReaderSettingsExpanded by remember { mutableStateOf(true) }
    var isAutoScrollHUDExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel.isReaderModeActive) {
        if (viewModel.isReaderModeActive) {
            isReaderSettingsExpanded = true
        }
    }

    // Auto-Scroll Loop
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed, isAutoScrollPaused) {
        if (isAutoScrollActive && activeTab != null && !showHomeScreen) {
            val pixels = when (autoScrollSpeed) {
                1 -> 1
                2 -> 2
                3 -> 3
                4 -> 4
                5 -> 6
                else -> 1
            }
            val delayMs = when (autoScrollSpeed) {
                1 -> 50L
                2 -> 50L
                3 -> 40L
                4 -> 30L
                5 -> 20L
                else -> 50L
            }
            while (isAutoScrollActive && !isAutoScrollPaused) {
                activeTab.session.loadUri("javascript:(function(){ window.scrollBy(0, $pixels); })();")
                delay(delayMs)
            }
        }
    }

    // Auto-fade after 3 s while playing; reappear immediately on pause
    LaunchedEffect(showFullscreenDownloadBtn, viewModel.isVideoPlayingInPage) {
        if (showFullscreenDownloadBtn && viewModel.isVideoPlayingInPage) {
            kotlinx.coroutines.delay(3000)
            showFullscreenDownloadBtn = false
        }
    }
    LaunchedEffect(viewModel.isVideoPlayingInPage) {
        if (!viewModel.isVideoPlayingInPage) {
            showFullscreenDownloadBtn = true
        }
    }
    // Reset visibility whenever fullscreen is entered fresh
    LaunchedEffect(viewModel.isFullscreen) {
        if (viewModel.isFullscreen) showFullscreenDownloadBtn = true
    }

    // Offline Translation states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var translationSourceText by remember { mutableStateOf("") }
    var translationResultText by remember { mutableStateOf("") }
    var translationProgress by remember { mutableStateOf(false) }

    var showSourceLangMenu by remember { mutableStateOf(false) }
    var showTargetLangMenu by remember { mutableStateOf(false) }
    var showPageTargetLangMenu by remember { mutableStateOf(false) }

    var selectedSourceLang by remember { mutableStateOf("Spanish" to "es") }
    var selectedTargetLang by remember { mutableStateOf("English" to "en") }
    var selectedPageTargetLang by remember { mutableStateOf("English" to "en") }

    // Tab Switcher states
    var showTabGroupsSheet by remember { mutableStateOf(false) }
    
    // Developer Console state
    var showConsoleSheet by remember { mutableStateOf(false) }
    var showDevNotesSheet by remember { mutableStateOf(false) }
    var showSiteStyleCustomizerSheet by remember { mutableStateOf(false) }

    // Tools sheet state
    var showToolsSheet by remember { mutableStateOf(false) }
    var isHomeSearchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
            isHomeSearchFocused = false
            isInputFocused = false
            isScrollNavBarVisible = true
        } else {
            if (!isInputFocused) {
                isScrollNavBarVisible = false
            }
        }
    }

    // QR Quick Tools states
    var showQrGeneratorDialog by remember { mutableStateOf(false) }
    var showQrScanResult by remember { mutableStateOf(false) }
    var qrGeneratorUrl by remember { mutableStateOf("") }

    // Feature Overview States
    var showQrOverviewDialog by remember { mutableStateOf(false) }
    var showPdfOverviewDialog by remember { mutableStateOf(false) }
    var showVideoOverviewDialog by remember { mutableStateOf(false) }
    var showExtensionsOverviewDialog by remember { mutableStateOf(false) }

    var pendingQrAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPdfAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingVideoAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExtensionsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var showEditPageOverviewDialog by remember { mutableStateOf(false) }
    var showConsoleOverviewDialog by remember { mutableStateOf(false) }

    var pendingEditPageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConsoleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDevNotesOverviewDialog by remember { mutableStateOf(false) }
    var pendingDevNotesAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        val request = viewModel.activeSystemPermissionRequest
        if (request != null) {
            if (allGranted) {
                request.onGranted()
            } else {
                request.onDenied()
            }
            viewModel.clearActiveSystemPermissionRequest()
        }
    }

    LaunchedEffect(viewModel.activeSystemPermissionRequest) {
        viewModel.activeSystemPermissionRequest?.let { request ->
            systemPermissionLauncher.launch(request.permissions ?: emptyArray())
        }
    }

    // ── File / Photo picker for web <input type="file"> ────────────────
    // Single-file picker (also handles camera/gallery via MIME type)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.deliverFilePickerResult(listOf(uri))
        } else {
            viewModel.cancelFilePrompt()
        }
    }

    // Multi-file picker
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.deliverFilePickerResult(uris)
        } else {
            viewModel.cancelFilePrompt()
        }
    }

    // Dedicated JS File picker for DevTools Console script loader
    val jsFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val jsCode = stream.bufferedReader().readText()
                    if (jsCode.isNotBlank()) {
                        viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> [Loaded JS File: ${uri.lastPathSegment ?: "script.js"}]"))
                        viewModel.pendingJsCommand = jsCode
                        Toast.makeText(context, "Injected JS script into page!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read JS file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Observe pendingFilePrompt and launch the right picker automatically
    LaunchedEffect(viewModel.pendingFilePrompt) {
        val pending = viewModel.pendingFilePrompt ?: return@LaunchedEffect
        // Build a MIME type string for the launcher. Fall back to "*/*" if none supplied.
        val mime = pending.mimeTypes
            ?.filter { it.isNotBlank() }
            ?.joinToString(",")
            ?.ifBlank { null }
            ?: "*/*"
        if (pending.allowMultiple) {
            multiFilePickerLauncher.launch(mime)
        } else {
            filePickerLauncher.launch(mime)
        }
    }

    LaunchedEffect(viewModel.currentUrl) {
        inputUrl = androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)
    }

    LaunchedEffect(viewModel.qrScanResults) {
        if (viewModel.qrScanResults.isNotEmpty()) {
            showQrScanResult = true
        }
    }

    LaunchedEffect(viewModel.qrScanError) {
        viewModel.qrScanError?.let { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            viewModel.clearQrScanResults()
        }
    }

    LaunchedEffect(viewModel.isFullscreen) {
        val activity = run {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            (ctx as? Activity) ?: com.rebelroot.omni.MainActivity.getActiveActivity()
        }
        activity?.let {
            FullscreenManager.setFullscreen(it, viewModel.isFullscreen)
        }
    }

    LaunchedEffect(viewModel, onPlayOnlineStream) {
        viewModel.onPlayVideoRequestReceived = { url, pageUrl ->
            onPlayOnlineStream(url, pageUrl)
        }
        viewModel.pendingVideoUrl?.let { url ->
            viewModel.pendingVideoUrl = null
            onPlayOnlineStream(url, url)
        }
    }


    // Reset home search focus state when we leave the home screen.
    // Also force the nav bar permanently visible when on the home screen.
    LaunchedEffect(showHomeScreen) {
        if (!showHomeScreen) {
            isHomeSearchFocused = false
        } else {
            isScrollNavBarVisible = true
        }
    }

    val currentShowHomeScreen by rememberUpdatedState(showHomeScreen)

    // Scroll-driven nav-bar hide (Chrome-style)
    // A CONFLATED channel acts as a debouncer: the scroll delegate (producer) emits
    // at up to 60 Hz, but the consumer coroutine only wakes once per emission batch,
    // so Compose state is updated at most once per frame — no jank.
    val scrollChannel = remember { Channel<Int>(Channel.CONFLATED) }

    LaunchedEffect(activeTab?.session) {
        currentScrollPos = 0
        val session = activeTab?.session ?: return@LaunchedEffect
        session.scrollDelegate = object : org.mozilla.geckoview.GeckoSession.ScrollDelegate {
            override fun onScrollChanged(sess: org.mozilla.geckoview.GeckoSession, scrollX: Int, scrollY: Int) {
                scrollChannel.trySend(scrollY)  // non-blocking; drops stale values automatically
            }
        }
    }

    // Consumer: runs on main thread, receives only the latest scroll position per frame
    LaunchedEffect(scrollChannel, isNavHideEnabled, viewModel.navBarHideTop, viewModel.navBarHideBottom) {
        var lastScrollY = 0
        var accumulated = 0
        for (scrollY in scrollChannel) {          // suspends until next value arrives
            currentScrollPos = scrollY
            // Always show on home screen or when nav-hide is disabled
            if (currentShowHomeScreen || !isNavHideEnabled) {
                if (!isScrollNavBarVisible) isScrollNavBarVisible = true
                lastScrollY = scrollY; accumulated = 0
                continue
            }

            // Always show when scrolled back to the very top
            if (scrollY <= 0) {
                isScrollNavBarVisible = true
                lastScrollY = 0; accumulated = 0
                continue
            }

            val delta = scrollY - lastScrollY
            lastScrollY = scrollY

            // Ignore micro-jitter (< 5 px) from sticky/fixed site navs
            if (delta in -4..4) continue

            accumulated += delta

            if (accumulated > 60) {          // deliberate downward scroll → hide
                if (isScrollNavBarVisible) isScrollNavBarVisible = false
                accumulated = 0
            } else if (accumulated < -40) {  // deliberate upward scroll → show
                if (!isScrollNavBarVisible) isScrollNavBarVisible = true
                accumulated = 0
            }
        }
    }

    // Add back gesture handler to handle system back clicks safely
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Only intercept back when the browser screen is actually in focus.
    // The video player screen has its own BackHandler that takes priority when it is
    // composed on top, so this handler is only active when the browser is the top destination.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (!showHomeScreen) {
            if (viewModel.canGoBack) {
                // Navigate the active tab's GeckoSession back safely
                try { viewModel.goBack() } catch (e: Exception) {
                    android.util.Log.w("BackHandler", "goBack() error, going home: ${e.message}")
                    viewModel.navigateHomeDirectly()
                }
            } else {
                // No history left – go to home screen without touching session
                viewModel.navigateHomeDirectly()
            }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                showExitConfirmDialog = true
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Tap again to exit Omni Browser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text("Exit Browser?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Text("Are you sure you want to exit Omni Browser?", fontSize = 14.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onExitBrowser()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Yes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("No", color = if (viewModel.isDarkThemeEnabled) Color.Gray else Color.DarkGray)
                }
            },
            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(32.dp)
        )
    }

    // Uncaught exception crash recovery notification dialog
    val crashPrefs = remember { context.getSharedPreferences("omni_crash_prefs", android.content.Context.MODE_PRIVATE) }
    var crashMsg by remember { mutableStateOf(crashPrefs.getString("last_crash_msg", null)) }
    if (crashMsg != null) {
        AlertDialog(
            onDismissRequest = {
                crashPrefs.edit().remove("last_crash_msg").apply()
                crashMsg = null
            },
            title = {
                Text("Auto Recovery", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("Omni Browser recovered from an unexpected error: \n\n$crashMsg\n\nYou can continue browsing safely.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        crashPrefs.edit().remove("last_crash_msg").apply()
                        crashMsg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(32.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
        if (!viewModel.isFullscreen && !showHomeScreen &&
                ((viewModel.chromeNavBarEnabled && viewModel.addressBarPosition != "Bottom") ||
                (!viewModel.chromeNavBarEnabled && !(viewModel.addressBarPosition == "Bottom" && !isTablet)))) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val statusBarHeightDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
            val topBarHeight = if (isTablet) 113.dp else (config.searchBoxHeight + (config.paddingVertical * 2))

            // Top bar: always rendered; graphicsLayer slides it out without removing from composition
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarHeightDp)
                            .graphicsLayer { translationY = -topBarHeight.toPx() * topBarFraction },
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp
                    ) {
                        Column {
                            if (isTablet) {
                                // Tablet Tab Strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(if (viewModel.isAmoledMode) Color(0xFF000000) else if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF1C1C1E) else Color(0xFFF1F3F4))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabletTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(tabletTabs, key = { it.id }) { tab ->
                                            val isActive = tab.id == viewModel.activeTabId
                                            val tabBg = if (isActive) {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF2C2C2E) else Color.White
                                            } else {
                                                Color.Transparent
                                            }
                                            val tabTextColor = if (isActive) {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White else Color(0xFF202124)
                                            } else {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                                            }
                                            
                                            Row(
                                                modifier = Modifier
                                                    .width(164.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                                    .background(tabBg)
                                                    .clickable { viewModel.selectTab(tab.id) }
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (tab.title.isNullOrBlank()) "New Tab" else tab.title,
                                                    color = tabTextColor,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                
                                                if (tabletTabs.size > 1 || viewModel.isIncognitoMode) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .clickable { viewModel.closeTab(tab.id, context) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Close Tab",
                                                            tint = tabTextColor.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    IconButton(
                                        onClick = { viewModel.createNewTab(context, "about:blank") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "New Tab",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0x1F000000))

                                // Tablet Toolbar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.goBack() },
                                        enabled = viewModel.canGoBack,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (viewModel.canGoBack) (if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)) else (if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.2f) else Color(0x1F000000)),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.goForward() },
                                        enabled = viewModel.canGoForward,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Forward",
                                            tint = if (viewModel.canGoForward) (if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)) else (if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.2f) else Color(0x1F000000)),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.loadUrl("about:blank") },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = "Home",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isInputFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (!isInputFocused) {
                                                Icon(
                                                    imageVector = if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Search,
                                                    contentDescription = "Search icon",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (viewModel.isIncognitoMode) Color(0xFFCBB2FF) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                )
                                            }

                                            val domainColor = MaterialTheme.colorScheme.onSurface
                                            val pathColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            val urlTransformation = remember(isInputFocused, domainColor, pathColor) {
                                                UrlVisualTransformation(isInputFocused, domainColor, pathColor)
                                            }

                                            val bringIntoViewRequester = remember { BringIntoViewRequester() }

                                            BasicTextField(
                                                value = if (inputUrl.text == "about:blank") androidx.compose.ui.text.input.TextFieldValue("") else inputUrl,
                                                onValueChange = { inputUrl = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged { 
                                                        if (it.isFocused && !isInputFocused) {
                                                            val text = inputUrl.text
                                                            inputUrl = inputUrl.copy(selection = androidx.compose.ui.text.TextRange(0, text.length))
                                                        }
                                                        isInputFocused = it.isFocused
                                                    }
                                                    .bringIntoViewRequester(bringIntoViewRequester),
                                                onTextLayout = { textLayoutResult ->
                                                    val cursorStart = inputUrl.selection.start
                                                    if (cursorStart >= 0 && cursorStart <= inputUrl.text.length) {
                                                        val cursorRect = textLayoutResult.getCursorRect(cursorStart)
                                                        coroutineScope.launch {
                                                            bringIntoViewRequester.bringIntoView(cursorRect)
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    imeAction = ImeAction.Go
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onGo = {
                                                        viewModel.loadUrl(inputUrl.text)
                                                        focusManager.clearFocus()
                                                        keyboardController?.hide()
                                                    }
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                visualTransformation = urlTransformation
                                            )

                                            if (inputUrl.text.isNotEmpty() && inputUrl.text != "about:blank") {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { inputUrl = androidx.compose.ui.text.input.TextFieldValue("") },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Clear",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }

                                            if (viewModel.currentUrl.isNotEmpty() && viewModel.currentUrl != "about:blank" && !isInputFocused) {
                                                val isBookmarked = viewModel.isBookmarked(viewModel.currentUrl)
                                                
                                                // Only show reader toggle button when reader mode is NOT active
                                                // (when active, the dedicated reader config bar at the bottom handles exit)
                                                if (false) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clickable { viewModel.toggleReaderMode() },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                                            contentDescription = "Reader Mode",
                                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable {
                                                            if (isBookmarked) {
                                                                viewModel.removeBookmark(viewModel.currentUrl)
                                                            } else {
                                                                val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Page"
                                                                viewModel.addToBookmarks(activeTabTitle, viewModel.currentUrl)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        contentDescription = "Bookmark",
                                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isInputFocused) {
                                        TextButton(
                                            onClick = {
                                                inputUrl = androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                        }
                                    }

                                    IconButton(
                                        onClick = { showExtensionsSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            Icon(
                                                imageVector = Icons.Rounded.Extension,
                                                contentDescription = "Extensions",
                                                tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (hasActiveUserExtensions) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .offset(x = 1.dp, y = (-1).dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = CircleShape
                                                        )
                                                        .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { showToolsSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = BlackholeIcon,
                                            contentDescription = "Tools",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Menu",
                                                tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        ChromeMenuDropdown(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            viewModel = viewModel,
                                            onNewTab = {
                                                viewModel.createNewTab(context, "about:blank")
                                            },
                                            onNewIncognitoTab = {
                                                if (!viewModel.isIncognitoMode) {
                                                    viewModel.toggleIncognitoMode(context)
                                                }
                                                viewModel.createNewTab(context, "about:blank")
                                            },
                                            onOpenHistory = onOpenHistory,
                                            onBurnData = {
                                                coroutineScope.launch {
                                                    val runtime = viewModel.getGeckoRuntime(context)
                                                    FireButton(runtime, context).burn()
                                                    viewModel.burnAllData(context)
                                                    Toast.makeText(context, "🔥 All history and tabs burned", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onOpenDownloads = onOpenDownloads,
                                            onOpenBookmarks = onOpenBookmarks,
                                            onOpenSettings = onOpenSettings,
                                            onShowCustomizationSheet = { showCustomizationSheet = true },
                                            onShowExtensions = { showExtensionsSheet = true },
                                            onShowPlayerSettings = { showPlayerSettingsDialog = true },
                                            onShowSiteInfo = { showSiteInfoSheet = true },
                                            onFindInPage = { viewModel.openFindInPage() }
                                        )
                                    }
                                }
                            } else {
                                // Phone Top Bar — show address bar here when position is "Top",
                                // or All-in-One is enabled AND position is NOT explicitly "Bottom"
                                if (viewModel.addressBarPosition == "Top" ||
                                    (viewModel.chromeNavBarEnabled && viewModel.addressBarPosition != "Bottom")) {
                                    PhoneAddressBar(
                                        viewModel = viewModel,
                                        inputUrl = inputUrl,
                                        onInputUrlChange = { inputUrl = it },
                                        isInputFocused = isInputFocused,
                                        onInputFocusedChange = { focused ->
                                 if (focused && !isInputFocused) {
                                     val text = inputUrl.text
                                     inputUrl = inputUrl.copy(selection = androidx.compose.ui.text.TextRange(0, text.length))
                                 }
                                 isInputFocused = focused
                             },
                                        focusRequester = focusRequester,
                                        hasActiveUserExtensions = hasActiveUserExtensions,
                                        onShowExtensionsSheet = { showExtensionsSheet = true },
                                        onShowToolsSheet = { showToolsSheet = true },
                                        showMenu = showMenu,
                                        onShowMenuChange = { showMenu = it },
                                        onOpenHistory = onOpenHistory,
                                        onOpenDownloads = onOpenDownloads,
                                        onOpenBookmarks = onOpenBookmarks,
                                        onOpenSettings = onOpenSettings,
                                        onShowCustomizationSheet = { showCustomizationSheet = true },
                                        onShowPlayerSettings = { showPlayerSettingsDialog = true },
                                        onShowTabGroups = { showTabGroupsSheet = true },
                                        onShowSiteInfo = { showSiteInfoSheet = true }
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = viewModel.isLoading,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                LinearProgressIndicator(
                                    progress = { viewModel.loadingProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Square
                                )
                            }

                            AnimatedVisibility(
                                visible = showAlohaBanner && viewModel.addressBarPosition != "Bottom",
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                MediaSnifferBanner(
                                    viewModel = viewModel,
                                    nonDrmMedia = nonDrmMedia,
                                    onDismiss = { isAlohaBannerDismissed = true },
                                    onPlay = { url -> onPlayOnlineStream(url, viewModel.currentUrl) },
                                    onDownloadClick = {
                                        if (!viewModel.hasSeenVideoOverview) {
                                            pendingVideoAction = { showDownloadSheet = true }
                                            showVideoOverviewDialog = true
                                        } else {
                                            showDownloadSheet = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Static status bar background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeightDp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                )
            }
        }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = size.height * bottomBarFraction }
            ) {
                if ((!viewModel.chromeNavBarEnabled || viewModel.addressBarPosition == "Bottom") && viewModel.addressBarPosition != "Top" && !isTablet && !showHomeScreen && !viewModel.isFullscreen) {
                    val isBottomNavBarVisible = !viewModel.chromeNavBarEnabled && viewModel.showBottomNavBar && !isTablet && !viewModel.isFullscreen && !isInputFocused && !isHomeSearchFocused
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run {
                                if (isBottomNavBarVisible) this else navigationBarsPadding()
                            },
                        color = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        shadowElevation = 12.dp,
                        tonalElevation = 2.dp
                    ) {
                        PhoneAddressBar(
                            viewModel = viewModel,
                            inputUrl = inputUrl,
                            onInputUrlChange = { inputUrl = it },
                            isInputFocused = isInputFocused,
                            onInputFocusedChange = { focused ->
                                if (focused && !isInputFocused) {
                                    val text = inputUrl.text
                                    inputUrl = inputUrl.copy(selection = androidx.compose.ui.text.TextRange(0, text.length))
                                }
                                isInputFocused = focused
                            },
                            focusRequester = focusRequester,
                            hasActiveUserExtensions = hasActiveUserExtensions,
                            onShowExtensionsSheet = { showExtensionsSheet = true },
                            onShowToolsSheet = { showToolsSheet = true },
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onOpenHistory = onOpenHistory,
                            onOpenDownloads = onOpenDownloads,
                            onOpenBookmarks = onOpenBookmarks,
                            onOpenSettings = onOpenSettings,
                            onShowCustomizationSheet = { showCustomizationSheet = true },
                            onShowPlayerSettings = { showPlayerSettingsDialog = true },
                            onShowTabGroups = { showTabGroupsSheet = true },
                            onShowSiteInfo = { showSiteInfoSheet = true }
                        )
                    }
                }

                if (!viewModel.chromeNavBarEnabled && viewModel.showBottomNavBar && !isTablet && !viewModel.isFullscreen && !isInputFocused && !isHomeSearchFocused) {
                // Flat minimal bottom bar persisting exactly as requested in screenshots
                val isDark = viewModel.isDarkThemeEnabled
                val navBg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                val navBorder = if (viewModel.isAmoledMode) Color(0xFF1A1A1A) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val navContent = if (isDark) Color.White else Color(0xFF1C1C1E)
                val navContentMuted = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF8E8E93)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .drawBehind {
                            drawLine(
                                color = navBorder,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    color = navBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(config.bottomNavBarHeight)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragAmountAccumulated > 100f) {
                                            val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                            // Swiped right -> select previous tab
                                            val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                            if (currentIndex > 0) {
                                                viewModel.selectTab(currentModeTabs[currentIndex - 1].id)
                                            }
                                        } else if (dragAmountAccumulated < -100f) {
                                            // Swiped left -> select next tab
                                            val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                            val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                            if (currentIndex != -1 && currentIndex < currentModeTabs.size - 1) {
                                                viewModel.selectTab(currentModeTabs[currentIndex + 1].id)
                                            }
                                        }
                                        dragAmountAccumulated = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAmountAccumulated += dragAmount
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Back button
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.goBack() },
                                enabled = viewModel.canGoBack,
                                modifier = Modifier.size(config.barIconSize + 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (viewModel.canGoBack) navContent else navContentMuted,
                                    modifier = Modifier.size(config.innerIconSize)
                                )
                            }
                        }

                        // Left-Center: Forward button
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.goForward() },
                                enabled = viewModel.canGoForward,
                                modifier = Modifier.size(config.barIconSize + 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (viewModel.canGoForward) navContent else navContentMuted,
                                    modifier = Modifier.size(config.innerIconSize)
                                )
                            }
                        }

                        // Center: Tools
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showToolsSheet = true },
                                modifier = Modifier.size(config.barIconSize + 4.dp)
                            ) {
                                Icon(
                                    imageVector = BlackholeIcon,
                                    contentDescription = "Tools",
                                    tint = navContent,
                                    modifier = Modifier.size(config.innerIconSize)
                                )
                            }
                        }

                        // Right-Center: Tabs counter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTabGroupsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(config.innerIconSize + 4.dp)
                                    .border(1.dp, navContent, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                  Text(
                                      text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                                      color = navContent,
                                      fontSize = (config.fontSize.value * 0.66f).sp,
                                      fontWeight = FontWeight.Bold
                                  )
                            }
                        }

                        // Right: Menu
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(config.barIconSize + 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Menu",
                                    tint = navContent,
                                    modifier = Modifier.size(config.innerIconSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    ) { paddingValues ->


        // ── Content area: mirrors Omni Browser 2.0 layout exactly ──────────────────
        // Outer Box uses system inset padding directly (not Scaffold paddingValues) so
        // the content area is never tied to Scaffold's measurement. GeckoView padding
        // snaps binary on isScrollNavBarVisible, so no reflow happens during the
        // graphicsLayer slide animation.
        val needsStatusBarPadding = viewModel.addressBarPosition != "Bottom" || isTablet || showHomeScreen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .clip(androidx.compose.ui.graphics.RectangleShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!needsStatusBarPadding && !viewModel.isFullscreen) {
                    val isDark = viewModel.isDarkThemeEnabled
                    val statusBarBg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color.White
                    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF161618) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(statusBarBg)
                            .statusBarsPadding()
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp
                    )
                }

                AnimatedVisibility(
                    visible = showAlohaBanner && viewModel.addressBarPosition == "Bottom",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    MediaSnifferBanner(
                        viewModel = viewModel,
                        nonDrmMedia = nonDrmMedia,
                        onDismiss = { isAlohaBannerDismissed = true },
                        onPlay = { url -> onPlayOnlineStream(url, viewModel.currentUrl) },
                        onDownloadClick = {
                            if (!viewModel.hasSeenVideoOverview) {
                                pendingVideoAction = { showDownloadSheet = true }
                                showVideoOverviewDialog = true
                            } else {
                                showDownloadSheet = true
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (needsStatusBarPadding && !viewModel.isFullscreen) Modifier.statusBarsPadding() else Modifier)
                ) {
                // GeckoView transitions from "static" to "overlap" based on scroll position.
                // At the very top (currentScrollPos <= 0), it has padding so the site's own
                // nav/header is fully visible and not covered by the browser's top bar.
                // When scrolled down (currentScrollPos > 0), padding snaps to 0.dp so the browser
                // bars overlap the site, allowing them to hide/show cleanly without white flashes.
                if (activeTab != null && !showHomeScreen) {
                    val bottomNavBarHeight = remember(viewModel.addressBarPosition, viewModel.chromeNavBarEnabled, viewModel.showBottomNavBar, viewModel.uiScale) {
                        if (viewModel.addressBarPosition == "Bottom" && !isTablet && !showHomeScreen && !viewModel.isFullscreen) {
                            val searchHeight = config.searchBoxHeight + (config.paddingVertical * 2)
                            if (viewModel.chromeNavBarEnabled) {
                                searchHeight
                            } else {
                                searchHeight + config.bottomNavBarHeight
                            }
                        } else {
                            0.dp
                        }
                    }

                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val statusBarHeightPx = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
                    val statusBarHeightDp = with(density) { statusBarHeightPx.toDp() }
                    
                    val hasTopBar = !(viewModel.addressBarPosition == "Bottom" && !isTablet)
                    val topBarHeight = if (isTablet) 113.dp else (config.searchBoxHeight + (config.paddingVertical * 2))
                    
                    val translationDistance = if (hasTopBar && !viewModel.isFullscreen && !(isKeyboardVisible && !isInputFocused && !isEditMode)) topBarHeight else 0.dp
                    val geckoBottomPad = bottomNavBarHeight * (1f - bottomBarFraction)
                    
                    val geckoTopPad = if (hasTopBar) (statusBarHeightDp - 24.dp).coerceAtLeast(0.dp) else 0.dp
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                object : androidx.compose.ui.layout.LayoutModifier {
                                    override fun androidx.compose.ui.layout.MeasureScope.measure(
                                        measurable: androidx.compose.ui.layout.Measurable,
                                        constraints: androidx.compose.ui.unit.Constraints
                                    ): androidx.compose.ui.layout.MeasureResult {
                                        val extraHeight = translationDistance.roundToPx()
                                        val newConstraints = constraints.copy(
                                            minHeight = constraints.minHeight + extraHeight,
                                            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extraHeight else constraints.maxHeight
                                        )
                                        val placeable = measurable.measure(newConstraints)
                                        return layout(placeable.width, placeable.height) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                }
                            )
                            .offset(y = translationDistance * (1f - topBarFraction))
                            .padding(top = geckoTopPad, bottom = geckoBottomPad)
                    ) {
                        DisposableEffect(Unit) {
                            onDispose {
                                viewModel.clearActiveGeckoView()
                            }
                        }
    
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val thresholdPx = with(density) { 80.dp.toPx() }
                            object : GeckoView(ctx) {
                                private var startY = 0f
                                private var isPulling = false
                                private val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

                                override fun onDetachedFromWindow() {
                                    releaseSession()
                                    super.onDetachedFromWindow()
                                }

                                override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                                    val scrollY = currentScrollPos
                                    when (ev.action) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            startY = ev.y
                                            isPulling = false
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            val deltaY = ev.y - startY
                                            if (scrollY <= 0 && deltaY > touchSlop && !isPulling && !viewModel.isLoading) {
                                                isPulling = true
                                            }
                                            if (isPulling) {
                                                val pullDistance = (deltaY - touchSlop).coerceAtLeast(0f)
                                                viewModel.pullToRefreshOffset = pullDistance
                                                return true
                                            }
                                        }
                                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                            if (isPulling) {
                                                isPulling = false
                                                viewModel.onPullRelease(thresholdPx)
                                                return true
                                            }
                                        }
                                    }
                                    return super.dispatchTouchEvent(ev)
                                }
                            }.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                val runtime = viewModel.getGeckoRuntime(ctx)
                                if (!activeTab.session.isOpen) {
                                    activeTab.session.open(runtime)
                                }
                                setSession(activeTab.session)
                                activeTab.session.setActive(true)
                                viewModel.setActiveGeckoView(this)
                                
                                // Scroll delegate is set via LaunchedEffect below; skip here to avoid duplicate assignment on recomposition
                            }
                        },
                        update = { geckoView ->
                            val runtime = viewModel.getGeckoRuntime(geckoView.context)
                            if (!activeTab.session.isOpen) {
                                activeTab.session.open(runtime)
                            }
                            if (geckoView.session != activeTab.session) {
                                geckoView.releaseSession()
                                geckoView.setSession(activeTab.session)
                            }
                            activeTab.session.setActive(true)
                            viewModel.setActiveGeckoView(geckoView)
                            
                            // Scroll delegate is managed via LaunchedEffect; no inline assignment needed
                        },
                        onRelease = { geckoView ->
                            geckoView.releaseSession()
                            viewModel.clearActiveGeckoView()
                        }
                    )

                    RainbowScanBorder(isScanning = viewModel.isQrScanning)
                    
                    activeTab.loadError?.let { errorMsg ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(32.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                colors = CardDefaults.cardColors(containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF4444),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Unable to Load Page",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorMsg,
                                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.reload() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Key on tab ID only — NOT the full TabState data class.
                    // TabState.copy() is called on every title/url/canGoBack change,
                    // which would cause the old key to dispose (setActive(false)) and
                    // immediately re-enter (setActive(true)), creating a rapid
                    // deactivate→reactivate cycle that freezes the compositor,
                    // drops network packets, and stalls cookie/JS processing.
                    DisposableEffect(activeTab.id) {
                        onDispose {
                            // Only deactivate when we truly leave this tab
                            viewModel.tabs.find { it.id == activeTab.id }?.session?.setActive(false)
                        }
                    }

                    // Lifecycle-aware session management:
                    // GeckoView requires setActive(false) on pause and setActive(true)
                    // on resume to properly suspend/resume its internal compositor,
                    // network stack, and JS engine. Without this, returning from
                    // background causes pages to appear frozen until a user interaction
                    // triggers a Compose recomposition.
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    DisposableEffect(activeTab.id, lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            val currentSession = viewModel.tabs.find { it.id == activeTab.id }?.session
                            when (event) {
                                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                    currentSession?.setActive(true)
                                }
                                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                    currentSession?.setActive(false)
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    // Pull-to-refresh overlay UI
                    val pullOffset = viewModel.pullToRefreshOffset
                    val isRefreshing = viewModel.isLoading
                    
                    if (pullOffset > 0f || isRefreshing) {
                        val pullOffsetDp = with(density) { (pullOffset * 0.4f).toDp() }
                        val thresholdDp = 80.dp
                        val progress = (pullOffset * 0.4f) / with(density) { thresholdDp.toPx() }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .offset(y = if (isRefreshing) 40.dp else pullOffsetDp.coerceAtMost(120.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color.White,
                                shadowElevation = 6.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            tint = if (progress >= 1f) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    rotationZ = progress * 360f
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    } // end GeckoView padding Box
                } // end if (activeTab != null && !showHomeScreen)

                if (showHomeScreen) {
                    HomeScreenContent(
                        viewModel = viewModel,
                        onOpenDownloads = onOpenDownloads,
                        onOpenHistory = onOpenHistory,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenLocker = onOpenLocker,
                        onOpenQrTools = {
                            if (!viewModel.hasSeenQrOverview) {
                                pendingQrAction = onOpenQrTools
                                showQrOverviewDialog = true
                            } else {
                                onOpenQrTools()
                            }
                        },
                        onOpenExtensions = {
                            if (!viewModel.hasSeenExtensionsOverview) {
                                pendingExtensionsAction = { showExtensionsSheet = true }
                                showExtensionsOverviewDialog = true
                            } else {
                                showExtensionsSheet = true
                            }
                        },
                        onOpenTranslator = {
                            translationSourceText = ""
                            translationResultText = ""
                            showTranslationDialog = true
                        },
                        onOpenConsole = {
                            if (!viewModel.hasSeenConsoleOverview) {
                                pendingConsoleAction = { showConsoleSheet = true }
                                showConsoleOverviewDialog = true
                            } else {
                                showConsoleSheet = true
                            }
                        },
                        onNavigateTo = { query ->
                            viewModel.loadUrl(query)
                        },
                        onFocusChanged = { isHomeSearchFocused = it },
                        showMenu = showMenu,
                        onShowMenuChange = { showMenu = it },
                        onOpenSettings = onOpenSettings,
                        showCustomizationSheet = showCustomizationSheet,
                        onShowCustomizationSheetChange = { showCustomizationSheet = it },
                        onShowTabGroups = { showTabGroupsSheet = true }
                    )
                }

                // Auto-Scroll HUD Pill
                if (isAutoScrollActive && activeTab != null && !showHomeScreen && !viewModel.isReaderModeActive && isAutoScrollHUDExpanded) {
                    var showSpeedSlider by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            
                            .combinedClickable(
                                onClick = {
                                    isAutoScrollPaused = !isAutoScrollPaused
                                },
                                onLongClick = {
                                    showSpeedSlider = !showSpeedSlider
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoScrollPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (isAutoScrollPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { isAutoScrollPaused = !isAutoScrollPaused }
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )
                                
                                Text(
                                    text = "Scroll ${autoScrollSpeed}x",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Collapse Auto Scroll",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            isAutoScrollHUDExpanded = false
                                        }
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )

                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close Auto Scroll",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            isAutoScrollActive = false
                                            isAutoScrollPaused = false
                                        }
                                )
                            }

                            if (showSpeedSlider) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.width(180.dp)
                                ) {
                                    Text("1x", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Slider(
                                        value = autoScrollSpeed.toFloat(),
                                        onValueChange = { autoScrollSpeed = it.toInt().coerceIn(1, 5) },
                                        valueRange = 1f..5f,
                                        steps = 3,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("5x", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                
                // Collapsed Auto-Scroll HUD Indicator
                if (isAutoScrollActive && activeTab != null && !showHomeScreen && !viewModel.isReaderModeActive && !isAutoScrollHUDExpanded) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp)
                            .size(36.dp)
                            .clickable { isAutoScrollHUDExpanded = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoScrollPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "Expand Auto Scroll",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Bottom edge touch detector to restore nav bars when hidden
                if (!isScrollNavBarVisible && !viewModel.isFullscreen && !showHomeScreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(20.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                isScrollNavBarVisible = true
                            }
                    )
                }
            }
            }

            // ─── Reader Mode Configuration Bar ─────────────────────────────────────
            if (viewModel.isReaderModeActive && activeTab != null && !showHomeScreen) {
                if (isReaderSettingsExpanded) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = if (isTablet) 16.dp else 72.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ── Header Row ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Reader View",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (viewModel.isTtsPlaying)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable {
                                            if (viewModel.isTtsPlaying) viewModel.stopTts()
                                            else viewModel.readAloudCurrentPage()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (viewModel.isTtsPlaying) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.RecordVoiceOver,
                                                contentDescription = "Read Aloud",
                                                tint = if (viewModel.isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (viewModel.isTtsPlaying) "Stop" else "Listen",
                                                color = if (viewModel.isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { isReaderSettingsExpanded = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Collapse Reader Controls",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { viewModel.toggleReaderMode() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Exit Reader Mode",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.decreaseReaderFontSize() }
                                    ) {
                                        Text(
                                            text = "A−",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    Text(
                                        text = "${viewModel.readerFontSize}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.increaseReaderFontSize() }
                                    ) {
                                        Text(
                                            text = "A+",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatLineSpacing,
                                        contentDescription = "Line spacing",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.decreaseReaderLineHeight() }
                                    ) {
                                        Text(
                                            text = "−",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    Text(
                                        text = String.format("%.1f", viewModel.readerLineHeight),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.increaseReaderLineHeight() }
                                    ) {
                                        Text(
                                            text = "+",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    listOf("Light", "Sepia", "Dark").forEach { theme ->
                                        val isSelected = viewModel.readerTheme == theme
                                        val (themeBg, themeFg) = when (theme) {
                                            "Sepia" -> Color(0xFFF4ECD8) to Color(0xFF5B4636)
                                            "Dark" -> Color(0xFF1E1E1E) to Color(0xFFE0E0E0)
                                            else -> Color.White to Color(0xFF1A1A1A)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(width = 46.dp, height = 24.dp)
                                                .clip(RoundedCornerShape(32.dp))
                                                .background(themeBg)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(32.dp)
                                                )
                                                .clickable { viewModel.setReaderThemeMode(theme) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = theme,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else themeFg,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Font",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(32.dp)
                                )
                                listOf("System", "Serif", "Sans", "Mono").forEach { family ->
                                    val isSelected = viewModel.readerFontFamily == (if (family == "Sans") "Sans-Serif" else if (family == "Mono") "Monospace" else family)
                                    val vmFamily = when (family) {
                                        "Sans" -> "Sans-Serif"
                                        "Mono" -> "Monospace"
                                        else -> family
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.updateReaderFontFamily(vmFamily) }
                                    ) {
                                        Text(
                                            text = family,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Width",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(32.dp)
                                )
                                listOf("Narrow", "Medium", "Wide").forEach { w ->
                                    val isSelected = viewModel.readerWidth == w
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.updateReaderWidth(w) }
                                    ) {
                                        Text(
                                            text = w,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Space",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(32.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (viewModel.readerLetterSpacing != "Normal") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val next = when (viewModel.readerLetterSpacing) {
                                                "Normal" -> "Wide"
                                                "Wide" -> "Very Wide"
                                                else -> "Normal"
                                            }
                                            viewModel.updateReaderLetterSpacing(next)
                                        }
                                ) {
                                    Text(
                                        text = "Letter: ${viewModel.readerLetterSpacing}",
                                        fontSize = 10.sp,
                                        fontWeight = if (viewModel.readerLetterSpacing != "Normal") FontWeight.Bold else FontWeight.Normal,
                                        color = if (viewModel.readerLetterSpacing != "Normal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (viewModel.readerWordSpacing != "Normal") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val next = when (viewModel.readerWordSpacing) {
                                                "Normal" -> "Wide"
                                                "Wide" -> "Very Wide"
                                                else -> "Normal"
                                            }
                                            viewModel.updateReaderWordSpacing(next)
                                        }
                                ) {
                                    Text(
                                        text = "Word: ${viewModel.readerWordSpacing}",
                                        fontSize = 10.sp,
                                        fontWeight = if (viewModel.readerWordSpacing != "Normal") FontWeight.Bold else FontWeight.Normal,
                                        color = if (viewModel.readerWordSpacing != "Normal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Align",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(32.dp)
                                )
                                listOf("Left", "Justify").forEach { align ->
                                    val isSelected = (align == "Justify" && viewModel.readerJustified) || (align == "Left" && !viewModel.readerJustified)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.toggleReaderJustified() }
                                    ) {
                                        Text(
                                            text = align,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = if (isTablet) 16.dp else 72.dp, end = 16.dp)
                            .size(36.dp)
                            .clickable { isReaderSettingsExpanded = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = "Expand Reader Controls",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                }
            }


            // ─── Find In Page bar ───────────────────────────────────────────────────
            if (viewModel.showFindInPage && !showHomeScreen && !viewModel.isFullscreen) {
                FindInPageBar(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                )
            }

            // ─── Unified smart download button ─────────────────────────────────────
            // • Fullscreen: fades while playing, stays / reappears while paused or on tap
            val nonDrmMedia = detectedMedia.filter { !it.isDrmProtected }
            val isYouTubePage = viewModel.currentUrl.lowercase().contains("youtube.com") || viewModel.currentUrl.lowercase().contains("youtu.be")
            if (nonDrmMedia.isNotEmpty() && !showHomeScreen && !viewModel.isReaderModeActive && !isYouTubePage && viewModel.isNativePlayerEnabled) {
                if (viewModel.isFullscreen) {
                    // Fullscreen mode — overlay with auto-fade controls
                    // Transparent tap-catcher; restores controls on any tap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showFullscreenDownloadBtn = true }
                    ) {
                        // Top-left controls: Back + Exit Fullscreen
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .safeDrawingPadding()
                                .padding(start = 12.dp, top = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back to browser button
                                IconButton(
                                    onClick = { viewModel.goBack() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // Exit fullscreen button — tells GeckoView to exit fullscreen
                                IconButton(
                                    onClick = {
                                        activeTab?.session?.exitFullScreen()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Bottom-right controls: Play FAB + Download FAB
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 32.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                val firstMedia = nonDrmMedia.firstOrNull()
                                if (firstMedia != null) {
                                    FloatingActionButton(
                                        onClick = {
                                            onPlayOnlineStream(firstMedia.url, viewModel.currentUrl)
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(32.dp),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play in Premium Player",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                FloatingActionButton(
                                    onClick = {
                                        if (!viewModel.hasSeenVideoOverview) {
                                            pendingVideoAction = { showDownloadSheet = true }
                                            showVideoOverviewDialog = true
                                        } else {
                                            showDownloadSheet = true
                                        }
                                    },
                                    containerColor = Color.Black.copy(alpha = 0.78f),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // ───────────────────────────────────────────────────────────────────────

            // Context Menu Bottom Sheet
            val activeContextMenu = viewModel.activeContextMenu
            if (activeContextMenu != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissContextMenu() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color.White,
                    contentColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0)) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        // Header
                        val titleText = when {
                            !activeContextMenu.srcUri.isNullOrEmpty() -> "Image Option"
                            !activeContextMenu.linkUri.isNullOrEmpty() -> "Link Option"
                            else -> "Page Option"
                        }
                        val subtitleText = activeContextMenu.srcUri ?: activeContextMenu.linkUri ?: ""
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = titleText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                            )
                            if (subtitleText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitleText,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFF0F0F0),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Options
                        if (!activeContextMenu.srcUri.isNullOrEmpty()) {
                            val imageUrl = activeContextMenu.srcUri
                            // Google Lens Search Option
                            ContextMenuItem(
                                icon = Icons.Rounded.CameraAlt,
                                title = "Search image with Google Lens",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val encodedUrl = android.net.Uri.encode(imageUrl)
                                    val lensUrl = "https://lens.google.com/uploadbyurl?url=$encodedUrl"
                                    viewModel.createNewTab(context, lensUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Google Lens App Intent
                            ContextMenuItem(
                                icon = Icons.Rounded.CameraAlt,
                                title = "Search with Google Lens App",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    try {
                                        val intent = android.content.Intent("com.google.lens.intent.action.LENS_INPUT").apply {
                                            setPackage("com.google.android.googlequicksearchbox")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Google Lens app not installed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Open in New Tab
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                title = "Open image in new tab",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    viewModel.createNewTab(context, imageUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Copy Image Link
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy image link",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Image Link", imageUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Image link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                        }

                        if (!activeContextMenu.linkUri.isNullOrEmpty()) {
                            val linkUrl = activeContextMenu.linkUri
                            // Open Link in New Tab
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                title = "Open link in new tab",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    viewModel.createNewTab(context, linkUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Copy Link Address
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy link address",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Link Address", linkUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link address copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            // Share Link
                            ContextMenuItem(
                                icon = Icons.Rounded.Share,
                                title = "Share link",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, linkUrl)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    val chooserIntent = Intent.createChooser(shareIntent, "Share link").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(chooserIntent)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                        }
                    }
                }
            }

            // Text Selection Bottom Sheet
            val activeTextSelection = viewModel.activeTextSelection
            if (activeTextSelection != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissTextSelection() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color.White,
                    contentColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0)) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        // Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Text Selection",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeTextSelection,
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                            )
                        }
                        
                        HorizontalDivider(
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0),
                            thickness = 1.dp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Options
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy Option
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy",
                                onClick = {
                                    viewModel.copySelectedText(context)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Share Option
                            ContextMenuItem(
                                icon = Icons.Rounded.Share,
                                title = "Share",
                                onClick = {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, activeTextSelection)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share text"))
                                    viewModel.dismissTextSelection()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            // Speak Aloud Option
                            ContextMenuItem(
                                icon = Icons.Rounded.RecordVoiceOver,
                                title = "Speak Aloud",
                                onClick = {
                                    viewModel.speakSelectedText(context)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            
                            // Select All Option
                            ContextMenuItem(
                                icon = Icons.Rounded.SelectAll,
                                title = "Select All",
                                onClick = {
                                    viewModel.selectAllText()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                        }
                    }
                }
            }

            // Bottom options sheet for video downloading
            if (showDownloadSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Download Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(nonDrmMedia) { item ->
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (item.type == com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Quality: ${item.quality ?: "Auto/Source"}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = item.type.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f), RoundedCornerShape(24.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    onPlayOnlineStream(item.url, viewModel.currentUrl)
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        val isYouTubeUrl = item.url.contains("googlevideo.com")
                                                        val audioUrl = if (isYouTubeUrl && item.type != com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO) {
                                                            nonDrmMedia.find { 
                                                                it.url.contains("googlevideo.com") && 
                                                                (it.url.contains("mime=audio") || it.url.contains("mime=audio%2F"))
                                                            }?.url
                                                        } else null

                                                        val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
                                                        val rawTitle = activeTab?.title ?: "Video"
                                                        val cleanTitle = if (rawTitle.isNotEmpty() && rawTitle != "Loading..." && rawTitle != "New Tab" && !rawTitle.startsWith("http")) {
                                                            rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
                                                        } else "Video"
                                                        val suggestedName = "$cleanTitle-${System.currentTimeMillis()}"

                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = suggestedName,
                                                            type = item.type,
                                                            saveToLocker = false,
                                                            referrerUrl = viewModel.currentUrl,
                                                            cookies = viewModel.activeVideoCookies,
                                                            audioUrl = audioUrl
                                                        )
                                                        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        val isYouTubeUrl = item.url.contains("googlevideo.com")
                                                        val audioUrl = if (isYouTubeUrl && item.type != com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO) {
                                                            nonDrmMedia.find { 
                                                                it.url.contains("googlevideo.com") && 
                                                                (it.url.contains("mime=audio") || it.url.contains("mime=audio%2F"))
                                                            }?.url
                                                        } else null

                                                        val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
                                                        val rawTitle = activeTab?.title ?: "Video"
                                                        val cleanTitle = if (rawTitle.isNotEmpty() && rawTitle != "Loading..." && rawTitle != "New Tab" && !rawTitle.startsWith("http")) {
                                                            rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
                                                        } else "Video"
                                                        val suggestedName = "$cleanTitle-${System.currentTimeMillis()}"

                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = suggestedName,
                                                            type = item.type,
                                                            saveToLocker = true,
                                                            referrerUrl = viewModel.currentUrl,
                                                            cookies = viewModel.activeVideoCookies,
                                                            audioUrl = audioUrl
                                                        )
                                                        Toast.makeText(context, "Downloading to Locker...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Locker", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Autofill suggestion bottom sheet: appears when user taps a login input
            if (viewModel.showAutofillBottomSheet && viewModel.autofillMatches.isNotEmpty()) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.showAutofillBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF0C1322) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Saved Passwords",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                            )
                        }

                        Text(
                            text = "Choose a credential to fill in for ${try { java.net.URI(viewModel.currentUrl).host?.removePrefix("www.") ?: "this site" } catch(e: Exception) { "this site" }}:",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(viewModel.autofillMatches) { credential ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    onClick = {
                                        viewModel.autofillCredential(credential)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = credential.username,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                                maxLines = 2,
                                                overflow = TextOverflow.Clip
                                            )
                                            Text(
                                                text = "••••••••",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 1. Offline translation dialog card overlay
            if (showTranslationDialog) {
                val languages = listOf(
                    "English" to "en",
                    "Spanish" to "es",
                    "French" to "fr",
                    "German" to "de",
                    "Chinese" to "zh",
                    "Hindi" to "hi",
                    "Arabic" to "ar",
                    "Russian" to "ru",
                    "Portuguese" to "pt",
                    "Japanese" to "ja",
                    "Italian" to "it",
                    "Turkish" to "tr",
                    "Korean" to "ko",
                    "Vietnamese" to "vi"
                )

                AlertDialog(
                    onDismissRequest = { showTranslationDialog = false; viewModel.translationManager.close() },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = "🌐 Site & Text Translator",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // --- Whole Page Translation Card ---
                            val currentUrl = viewModel.currentUrl
                            val canTranslatePage = !showHomeScreen && activeTab != null && 
                                    (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Translate Webpage",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )
                                    
                                    if (canTranslatePage) {
                                        Text(
                                            text = "Translate the entire active webpage using Google Web Translation proxy.",
                                            fontSize = 11.sp,
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color(0xFF64748B)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Target Language:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                            )
                                            LanguageDropdownSelector(
                                                label = "Target",
                                                selectedLanguageName = selectedPageTargetLang.first,
                                                expanded = showPageTargetLangMenu,
                                                onExpandedChange = { showPageTargetLangMenu = it },
                                                languages = languages,
                                                onLanguageSelected = { selectedPageTargetLang = it }
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                                                    try {
                                                        val parsedUri = android.net.Uri.parse(currentUrl)
                                                        val targetLang = selectedPageTargetLang.second
                                                        // Build the modern translate.goog URL.
                                                        // IMPORTANT: dots in the original host must become hyphens
                                                        // so the subdomain is a single DNS label covered by *.translate.goog.
                                                        // e.g. novelpedia.co → novelpedia-co.translate.goog
                                                        //      www.example.com → www-example-com.translate.goog
                                                        val host = parsedUri.host ?: ""
                                                        val sanitizedHost = host.replace(".", "-")
                                                        val path = parsedUri.path?.takeIf { it.isNotEmpty() } ?: "/"
                                                        val existingQuery = parsedUri.query
                                                        val scheme = if (parsedUri.scheme == "http") "http" else "https"
                                                        val translateParams = "_x_tr_sl=auto&_x_tr_tl=$targetLang&_x_tr_hl=en&_x_tr_pto=wapp"
                                                        val fullQuery = if (!existingQuery.isNullOrEmpty()) "$existingQuery&$translateParams" else translateParams
                                                        val translateUrl = "$scheme://$sanitizedHost.translate.goog$path?$fullQuery"
                                                        android.util.Log.d("Translator", "Translate URL: $translateUrl")
                                                        viewModel.loadUrl(translateUrl)
                                                        showTranslationDialog = false
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Translator", "Failed to build translate.goog URL", e)
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Translate Entire Page", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text(
                                            text = "Open any webpage to enable whole-site translation.",
                                            fontSize = 12.sp,
                                            color = Color.Red.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // --- Text Translation Card ---
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Translate Custom Text",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LanguageDropdownSelector(
                                            label = "Source",
                                            selectedLanguageName = selectedSourceLang.first,
                                            expanded = showSourceLangMenu,
                                            onExpandedChange = { showSourceLangMenu = it },
                                            languages = languages,
                                            onLanguageSelected = { selectedSourceLang = it }
                                        )
                                        Text(
                                            text = "➔",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                        LanguageDropdownSelector(
                                            label = "Target",
                                            selectedLanguageName = selectedTargetLang.first,
                                            expanded = showTargetLangMenu,
                                            onExpandedChange = { showTargetLangMenu = it },
                                            languages = languages,
                                            onLanguageSelected = { selectedTargetLang = it }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = translationSourceText,
                                        onValueChange = { translationSourceText = it },
                                        placeholder = { Text("Type text to translate...", color = Color.Gray) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black),
                                        modifier = Modifier.fillMaxWidth().height(90.dp),
                                        shape = RoundedCornerShape(20.dp)
                                    )

                                    if (translationProgress) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "Downloading offline model...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (translationResultText.isNotEmpty()) {
                                        Surface(
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = translationResultText,
                                                fontSize = 14.sp,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            translationProgress = true
                                            viewModel.translationManager.setupLanguage(
                                                selectedSourceLang.second,
                                                selectedTargetLang.second
                                            ) {
                                                coroutineScope.launch {
                                                    try {
                                                        translationResultText = viewModel.translationManager.translateText(translationSourceText)
                                                    } catch (e: Exception) {
                                                        translationResultText = "Translation failed: ${e.message}"
                                                    } finally {
                                                        translationProgress = false
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Translate Text", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showTranslationDialog = false
                                viewModel.translationManager.close()
                            }
                        ) {
                            Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // 2. Premium Grid Tab Windows Switcher Tray Bottom Sheet
            if (showTabGroupsSheet) {
                val currentModeTabs = remember(viewModel.tabs.toList(), viewModel.isIncognitoMode) {
                    viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                }
                ModalBottomSheet(
                    onDismissRequest = { showTabGroupsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isIncognitoMode) Color(0xFF070A0F) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.isIncognitoMode) "Incognito Tabs (${currentModeTabs.size})" else "Open Tabs (${currentModeTabs.size})",
                                color = if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            TextButton(
                                onClick = {
                                    showTabGroupsSheet = false
                                    viewModel.createNewTab(context, "about:blank")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Tab",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Premium Mode Toggle Capsule Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF1E2D3F) else MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val normalCount = viewModel.tabs.count { !it.isIncognito }
                            val privateCount = viewModel.tabs.count { it.isIncognito }

                            // Normal tab option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (!viewModel.isIncognitoMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        if (viewModel.isIncognitoMode) {
                                            viewModel.toggleIncognitoMode(context)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Normal ($normalCount)",
                                    color = if (!viewModel.isIncognitoMode) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Private tab option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (viewModel.isIncognitoMode) Color(0xFFFF3B5C) else Color.Transparent)
                                    .clickable {
                                        if (!viewModel.isIncognitoMode) {
                                            viewModel.toggleIncognitoMode(context)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Incognito ($privateCount)",
                                    color = if (viewModel.isIncognitoMode) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Tab group & context menu state
                        var showGroupDialog by remember { mutableStateOf(false) }
                        var groupDialogTargetTabId by remember { mutableStateOf<String?>(null) }
                        var newGroupTitle by remember { mutableStateOf("") }
                        var newGroupColorIndex by remember { mutableStateOf(0) }
                        var showRenameGroupDialog by remember { mutableStateOf(false) }
                        var renameGroupTarget by remember { mutableStateOf<TabGroup?>(null) }
                        var renameGroupText by remember { mutableStateOf("") }

                        val groupColors = listOf(
                            0xFF4285F4L, // Google Blue
                            0xFF34A853L, // Google Green
                            0xFFEA4335L, // Google Red
                            0xFFFBBC05L, // Google Yellow
                            0xFF9C27B0L, // Purple
                            0xFFFF6D00L, // Orange
                            0xFF00BCD4L, // Cyan
                            0xFFE91E63L  // Pink
                        )
                        val groupColorLabels = listOf("Blue","Green","Red","Yellow","Purple","Orange","Cyan","Pink")

                        // Organise tabs: grouped tabs → show under their group header; ungrouped → show at bottom
                        val groupedTabIds = remember(viewModel.tabGroups.toList()) {
                            viewModel.tabGroups.flatMap { it.tabIds }.toSet()
                        }
                        val ungroupedTabs = remember(currentModeTabs, groupedTabIds) {
                            currentModeTabs.filter { it.id !in groupedTabIds }
                        }

                        // Helper composable: full-width list row for List mode
                        @Composable
                        fun TabListRow(tab: TabState) {
                            val isActive = tab.id == viewModel.activeTabId
                            val swipeOffsetX = remember(tab.id) { androidx.compose.animation.core.Animatable(0f) }
                            val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
                            val scope = rememberCoroutineScope()
                            val tabGroup = remember(viewModel.tabGroups.toList()) { viewModel.getGroupForTab(tab.id) }
                            val groupColor = tabGroup?.let { Color(it.color) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationX = swipeOffsetX.value
                                        alpha = (1f - (kotlin.math.abs(swipeOffsetX.value) / (swipeThreshold * 1.5f))).coerceIn(0f, 1f)
                                    }
                                    .pointerInput(tab.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (kotlin.math.abs(swipeOffsetX.value) > swipeThreshold) {
                                                    scope.launch {
                                                        val target = if (swipeOffsetX.value > 0) swipeThreshold * 2f else -swipeThreshold * 2f
                                                        swipeOffsetX.animateTo(target, androidx.compose.animation.core.tween(150))
                                                        viewModel.closeTab(tab.id, context)
                                                    }
                                                } else {
                                                    scope.launch { swipeOffsetX.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                }
                                            },
                                            onDragCancel = { scope.launch { swipeOffsetX.animateTo(0f, androidx.compose.animation.core.spring()) } },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch { swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount) }
                                            }
                                        )
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isActive)
                                            (if (viewModel.isDarkThemeEnabled) Color(0xFF1A2C40) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        else
                                            (if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    .border(
                                        BorderStroke(
                                            if (isActive) 1.5.dp else 0.5.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else groupColor?.copy(alpha = 0.5f)
                                                ?: (if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.selectTab(tab.id)
                                            showTabGroupsSheet = false
                                        },
                                        onLongClick = {
                                            groupDialogTargetTabId = tab.id
                                            newGroupTitle = ""
                                            newGroupColorIndex = 0
                                            showGroupDialog = true
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Group color stripe (left edge accent if grouped)
                                    if (groupColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(groupColor)
                                        )
                                    }

                                    // Favicon box
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (viewModel.isDarkThemeEnabled) Color(0xFF1E2D3F) else Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                    .data("https://www.google.com/s2/favicons?domain=${tab.url}&sz=64")
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Explore,
                                                contentDescription = null,
                                                tint = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    // Title and URL
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") "New Tab" else tab.title,
                                            color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                            Text(
                                                text = tab.url,
                                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (groupColor != null && tabGroup != null) {
                                            Text(
                                                text = tabGroup.title,
                                                color = groupColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Active indicator
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    // Close button
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                            .clickable { viewModel.closeTab(tab.id, context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close Tab",
                                            tint = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Helper composable: square grid card for Grid mode
                        @Composable
                        fun TabGridCard(tab: TabState, modifier: Modifier = Modifier) {
                            val isActive = tab.id == viewModel.activeTabId
                            val swipeOffsetX = remember(tab.id) { androidx.compose.animation.core.Animatable(0f) }
                            val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 100.dp.toPx() }
                            val scope = rememberCoroutineScope()
                            val tabGroup = remember(viewModel.tabGroups.toList()) { viewModel.getGroupForTab(tab.id) }
                            val groupColor = tabGroup?.let { Color(it.color) }

                            Box(
                                modifier = modifier
                                    .graphicsLayer {
                                        translationX = swipeOffsetX.value
                                        alpha = (1f - (kotlin.math.abs(swipeOffsetX.value) / (swipeThreshold * 1.5f))).coerceIn(0f, 1f)
                                    }
                                    .pointerInput(tab.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (kotlin.math.abs(swipeOffsetX.value) > swipeThreshold) {
                                                    scope.launch {
                                                        val target = if (swipeOffsetX.value > 0) swipeThreshold * 2f else -swipeThreshold * 2f
                                                        swipeOffsetX.animateTo(target, androidx.compose.animation.core.tween(150))
                                                        viewModel.closeTab(tab.id, context)
                                                    }
                                                } else {
                                                    scope.launch { swipeOffsetX.animateTo(0f, androidx.compose.animation.core.spring()) }
                                                }
                                            },
                                            onDragCancel = { scope.launch { swipeOffsetX.animateTo(0f, androidx.compose.animation.core.spring()) } },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch { swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount) }
                                            }
                                        )
                                    }
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        BorderStroke(
                                            if (isActive) 1.5.dp else 0.5.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else groupColor?.copy(alpha = 0.6f)
                                                ?: (if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                        ),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.selectTab(tab.id)
                                            showTabGroupsSheet = false
                                        },
                                        onLongClick = {
                                            groupDialogTargetTabId = tab.id
                                            newGroupTitle = ""
                                            newGroupColorIndex = 0
                                            showGroupDialog = true
                                        }
                                    )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Header row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Group color dot
                                            if (groupColor != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(groupColor)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Language,
                                                    contentDescription = null,
                                                    tint = if (isActive) MaterialTheme.colorScheme.primary else (if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") "New Tab" else tab.title,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                                .clickable { viewModel.closeTab(tab.id, context) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Close Tab",
                                                tint = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    // Preview box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = if (viewModel.isDarkThemeEnabled) {
                                                        listOf(Color(0xFF1E2D3F), Color(0xFF0F1B26))
                                                    } else {
                                                        listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                                    }
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                    .data("https://www.google.com/s2/favicons?domain=${tab.url}&sz=128")
                                                    .size(96, 96)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Site Thumbnail",
                                                modifier = Modifier.size(40.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Explore,
                                                contentDescription = null,
                                                tint = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            contentAlignment = Alignment.BottomStart
                                        ) {
                                            Text(
                                                text = if (tab.url == "about:blank") "about:blank" else tab.url,
                                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isList = viewModel.tabLayoutMode == "List"

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(if (isList) 8.dp else 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // ── Tab Groups Section ──────────────────────────────────────────
                            viewModel.tabGroups.forEach { group ->
                                val groupTabsInMode = currentModeTabs.filter { it.id in group.tabIds }
                                if (groupTabsInMode.isEmpty()) return@forEach

                                item(key = "group_header_${group.id}") {
                                    // Group header row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(group.color).copy(alpha = if (viewModel.isDarkThemeEnabled) 0.15f else 0.10f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(group.color))
                                        )
                                        Text(
                                            text = group.title,
                                            color = Color(group.color),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${groupTabsInMode.size} tab${if (groupTabsInMode.size != 1) "s" else ""}",
                                            color = Color(group.color).copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                        // Rename group
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(group.color).copy(alpha = 0.2f))
                                                .clickable {
                                                    renameGroupTarget = group
                                                    renameGroupText = group.title
                                                    showRenameGroupDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Rename Group",
                                                tint = Color(group.color),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        // Delete group
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF3B5C).copy(alpha = 0.12f))
                                                .clickable { viewModel.deleteTabGroup(group.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Delete Group",
                                                tint = Color(0xFFFF3B5C),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // Tabs in this group
                                if (isList) {
                                    items(groupTabsInMode, key = { "list_${it.id}" }) { tab ->
                                        TabListRow(tab)
                                    }
                                } else {
                                    val chunks = groupTabsInMode.chunked(2)
                                    items(chunks, key = { "grid_group_${group.id}_${it.first().id}" }) { chunk ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            for (tab in chunk) {
                                                TabGridCard(tab, modifier = Modifier.weight(1f))
                                            }
                                            if (chunk.size == 1) Box(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                item(key = "group_divider_${group.id}") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }

                            // ── Ungrouped Tabs Section ──────────────────────────────────────
                            if (ungroupedTabs.isNotEmpty() && viewModel.tabGroups.any { g -> currentModeTabs.any { t -> t.id in g.tabIds } }) {
                                item(key = "ungrouped_header") {
                                    Text(
                                        text = "Other Tabs",
                                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (isList) {
                                items(ungroupedTabs, key = { "list_ungrouped_${it.id}" }) { tab ->
                                    TabListRow(tab)
                                }
                            } else {
                                val chunks = ungroupedTabs.chunked(2)
                                items(chunks, key = { "grid_${it.first().id}" }) { chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        for (tab in chunk) {
                                            TabGridCard(tab, modifier = Modifier.weight(1f))
                                        }
                                        if (chunk.size == 1) Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // ── Add to Group dialog (long-press on tab) ────────────────────────
                        if (showGroupDialog && groupDialogTargetTabId != null) {
                            val targetTabId = groupDialogTargetTabId!!
                            val currentGroup = remember(viewModel.tabGroups.toList()) { viewModel.getGroupForTab(targetTabId) }

                            AlertDialog(
                                onDismissRequest = { showGroupDialog = false },
                                containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1B26) else MaterialTheme.colorScheme.surface,
                                title = {
                                    Text(
                                        "Tab Groups",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // Remove from group option
                                        if (currentGroup != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFFF3B5C).copy(alpha = 0.12f))
                                                    .clickable {
                                                        viewModel.removeTabFromGroup(targetTabId, currentGroup.id)
                                                        showGroupDialog = false
                                                    }
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.Close, null, tint = Color(0xFFFF3B5C), modifier = Modifier.size(16.dp))
                                                Text("Remove from \"${currentGroup.title}\"", color = Color(0xFFFF3B5C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        // Existing groups
                                        val existingGroups = viewModel.tabGroups.filter { it.id != currentGroup?.id }
                                        if (existingGroups.isNotEmpty()) {
                                            Text(
                                                "Add to existing group:",
                                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                            )
                                            existingGroups.forEach { group ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(group.color).copy(alpha = 0.12f))
                                                        .clickable {
                                                            viewModel.addTabToGroup(targetTabId, group.id)
                                                            showGroupDialog = false
                                                        }
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(group.color)))
                                                    Text(group.title, color = Color(group.color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    Spacer(Modifier.weight(1f))
                                                    Text("${group.tabIds.size} tab${if (group.tabIds.size != 1) "s" else ""}",
                                                        color = Color(group.color).copy(alpha = 0.6f), fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))

                                        // Create new group
                                        Text(
                                            "Create new group:",
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        androidx.compose.material3.OutlinedTextField(
                                            value = newGroupTitle,
                                            onValueChange = { newGroupTitle = it },
                                            placeholder = { Text("Group name (e.g. Work, Social)", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(groupColors[newGroupColorIndex]),
                                                unfocusedBorderColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                focusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                            )
                                        )
                                        // Color picker row
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            groupColors.forEachIndexed { i, colorLong ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(colorLong))
                                                        .border(
                                                            if (i == newGroupColorIndex) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                                            CircleShape
                                                        )
                                                        .clickable { newGroupColorIndex = i }
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (newGroupTitle.isNotBlank()) {
                                                viewModel.createTabGroup(
                                                    title = newGroupTitle.trim(),
                                                    color = groupColors[newGroupColorIndex],
                                                    initialTabId = targetTabId
                                                )
                                            }
                                            showGroupDialog = false
                                        }
                                    ) {
                                        Text(if (newGroupTitle.isNotBlank()) "Create & Add" else "Close",
                                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showGroupDialog = false }) {
                                        Text("Cancel", color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }

                        // ── Rename Group dialog ────────────────────────────────────────────
                        if (showRenameGroupDialog && renameGroupTarget != null) {
                            AlertDialog(
                                onDismissRequest = { showRenameGroupDialog = false },
                                containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1B26) else MaterialTheme.colorScheme.surface,
                                title = {
                                    Text("Rename Group",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        androidx.compose.material3.OutlinedTextField(
                                            value = renameGroupText,
                                            onValueChange = { renameGroupText = it },
                                            placeholder = { Text("Group name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        // Color change row
                                        Text("Change color:", color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            groupColors.forEach { colorLong ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(colorLong))
                                                        .border(
                                                            if (colorLong == renameGroupTarget!!.color) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                                            CircleShape
                                                        )
                                                        .clickable {
                                                            renameGroupTarget = renameGroupTarget!!.copy(color = colorLong)
                                                            viewModel.changeTabGroupColor(renameGroupTarget!!.id, colorLong)
                                                        }
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (renameGroupText.isNotBlank()) {
                                            viewModel.renameTabGroup(renameGroupTarget!!.id, renameGroupText.trim())
                                        }
                                        showRenameGroupDialog = false
                                    }) {
                                        Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRenameGroupDialog = false }) {
                                        Text("Cancel", color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. DevTools Pro Developer Console Bottom Sheet
            if (showConsoleSheet) {
                var jsInputText by remember { mutableStateOf("") }
                var selectedLogFilter by remember { mutableStateOf("ALL") }
                var consoleSearchQuery by remember { mutableStateOf("") }
                var showLoadScriptDialog by remember { mutableStateOf(false) }
                var cdnUrlInput by remember { mutableStateOf("") }
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                val allLogs = viewModel.consoleLogs.toList()
                val errorLogs = remember(allLogs) { allLogs.filter { it.level == "ERROR" } }
                val warnLogs = remember(allLogs) { allLogs.filter { it.level == "WARN" } }
                val infoLogs = remember(allLogs) { allLogs.filter { it.level == "LOG" || it.level == "INFO" } }
                val sysLogs = remember(allLogs) { allLogs.filter { it.level == "EVAL" || it.level == "RESULT" } }

                val filteredLogs = remember(allLogs, selectedLogFilter, consoleSearchQuery) {
                    val base = when (selectedLogFilter) {
                        "ERRS" -> errorLogs
                        "WARNS" -> warnLogs
                        "LOGS" -> infoLogs
                        "SYSTEM" -> sysLogs
                        else -> allLogs
                    }
                    if (consoleSearchQuery.isBlank()) {
                        base
                    } else {
                        base.filter { it.message.contains(consoleSearchQuery, ignoreCase = true) || it.level.contains(consoleSearchQuery, ignoreCase = true) }
                    }
                }

                ModalBottomSheet(
                    onDismissRequest = { showConsoleSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else Color(0xFF161B22)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header Bar: Title + Badges + Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF21262D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "DevTools Console",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${allLogs.size} total log entries • Real-time Web Inspector",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { showLoadScriptDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = "Load & Inject Script",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val formatted = allLogs.joinToString("\n") { log ->
                                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                            "[$timeStr] [${log.level}] ${log.message}"
                                        }
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(formatted))
                                        Toast.makeText(context, "Copied ${allLogs.size} logs to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy All Logs",
                                        tint = Color(0xFF8B949E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.consoleLogs.clear() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Clear Console",
                                        tint = Color(0xFFF85149),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Filter Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(Modifier.horizontalScroll(rememberScrollState())),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filterOptions = listOf(
                                "ALL" to "All (${allLogs.size})",
                                "ERRS" to "Errors (${errorLogs.size})",
                                "WARNS" to "Warnings (${warnLogs.size})",
                                "LOGS" to "Logs (${infoLogs.size})",
                                "SYSTEM" to "Exec/System (${sysLogs.size})"
                            )

                            filterOptions.forEach { (key, label) ->
                                val isSelected = selectedLogFilter == key
                                val badgeBg = when (key) {
                                    "ERRS" -> if (isSelected) Color(0xFFDA3633) else Color(0x33DA3633)
                                    "WARNS" -> if (isSelected) Color(0xFFD29922) else Color(0x33D29922)
                                    "LOGS" -> if (isSelected) Color(0xFF238636) else Color(0x33238636)
                                    "SYSTEM" -> if (isSelected) Color(0xFF8957E5) else Color(0x338957E5)
                                    else -> if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF21262D)
                                }
                                val textColor = if (isSelected) Color.White else Color(0xFFC9D1D9)

                                Surface(
                                    modifier = Modifier.clickable { selectedLogFilter = key },
                                    shape = RoundedCornerShape(16.dp),
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = label,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Search Filter Field
                        OutlinedTextField(
                            value = consoleSearchQuery,
                            onValueChange = { consoleSearchQuery = it },
                            placeholder = { Text("Filter console output...", fontSize = 12.sp, color = Color(0xFF484F58)) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.White
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF484F58), modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (consoleSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { consoleSearchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117)
                            )
                        )

                        // Main Console Logs Viewport
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D1117))
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            if (filteredLogs.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFF30363D),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (consoleSearchQuery.isNotEmpty()) "No matching console logs" else "No console messages recorded",
                                        fontSize = 12.sp,
                                        color = Color(0xFF484F58),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredLogs, key = { "${it.timestamp}_${it.message.hashCode()}" }) { log ->
                                        val formatter = remember { java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()) }
                                        val timeStr = formatter.format(java.util.Date(log.timestamp))

                                        val levelBadgeBg = when (log.level) {
                                            "ERROR" -> Color(0xFFDA3633)
                                            "WARN" -> Color(0xFFD29922)
                                            "INFO" -> Color(0xFF3FB950)
                                            "EVAL" -> Color(0xFFA371F7)
                                            "RESULT" -> Color(0xFF58A6FF)
                                            else -> Color(0xFF30363D)
                                        }
                                        val levelBadgeText = when (log.level) {
                                            "WARN", "INFO", "RESULT" -> Color.Black
                                            else -> Color.White
                                        }
                                        val textColor = when (log.level) {
                                            "ERROR" -> Color(0xFFFF6E6E)
                                            "WARN" -> Color(0xFFFFC857)
                                            "INFO" -> Color(0xFF7EE787)
                                            "EVAL" -> Color(0xFFD2A8FF)
                                            "RESULT" -> Color(0xFF79C0FF)
                                            else -> Color(0xFFC9D1D9)
                                        }
                                        val rowBg = when (log.level) {
                                            "ERROR" -> Color(0x22DA3633)
                                            "WARN" -> Color(0x1AD29922)
                                            "EVAL" -> Color(0x15A371F7)
                                            "RESULT" -> Color(0x1558A6FF)
                                            else -> Color.Transparent
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(log.message))
                                                    Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                            color = rowBg
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Time Tag
                                                Text(
                                                    text = timeStr,
                                                    fontSize = 10.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = Color(0xFF6E7681)
                                                )

                                                // Level Pill
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = levelBadgeBg
                                                ) {
                                                    Text(
                                                        text = log.level.take(4),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = levelBadgeText,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                // Log Content
                                                Text(
                                                    text = log.message,
                                                    fontSize = 11.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = textColor,
                                                    lineHeight = 15.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Script Preset Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(Modifier.horizontalScroll(rememberScrollState())),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presetScripts = listOf(
                                "console.log(document.title)" to "title",
                                "console.log(location.href)" to "url",
                                "console.log(document.cookie)" to "cookies",
                                "console.log(navigator.userAgent)" to "userAgent",
                                "document.body.style.backgroundColor = 'black'" to "darkMode"
                            )
                            presetScripts.forEach { (script, label) ->
                                Surface(
                                    modifier = Modifier.clickable {
                                        jsInputText = script
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF21262D),
                                    border = BorderStroke(0.5.dp, Color(0xFF30363D))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("+", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text(label, fontSize = 10.sp, color = Color(0xFF8B949E), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        // Code Execution Terminal Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = jsInputText,
                                onValueChange = { jsInputText = it },
                                placeholder = { Text("eval JS (e.g. console.log('hello'))", fontSize = 12.sp, color = Color(0xFF484F58)) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFF7EE787)
                                ),
                                leadingIcon = {
                                    Text(">", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF30363D),
                                    focusedContainerColor = Color(0xFF0D1117),
                                    unfocusedContainerColor = Color(0xFF0D1117)
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSend = {
                                        if (jsInputText.isNotBlank()) {
                                            viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> $jsInputText"))
                                            viewModel.pendingJsCommand = jsInputText
                                            jsInputText = ""
                                        }
                                    }
                                )
                            )
                            Button(
                                onClick = {
                                    if (jsInputText.isNotBlank()) {
                                        viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> $jsInputText"))
                                        viewModel.pendingJsCommand = jsInputText
                                        jsInputText = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Load Script Dialog
                if (showLoadScriptDialog) {
                    AlertDialog(
                        onDismissRequest = { showLoadScriptDialog = false },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Load & Inject Script", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(Modifier.verticalScroll(rememberScrollState())),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    "Inject external JavaScript files, CDN libraries, or mobile developer tools into the active webpage.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8B949E)
                                )

                                // 1. Local File Upload Button
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showLoadScriptDialog = false
                                            jsFilePickerLauncher.launch("*/*")
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF21262D),
                                    border = BorderStroke(1.dp, Color(0xFF30363D))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Pick JS File from Device", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("Select a local .js or .txt file to run", fontSize = 11.sp, color = Color(0xFF8B949E))
                                        }
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF30363D))

                                // 2. CDN URL Injector
                                Text("Inject Script from CDN / Web URL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                OutlinedTextField(
                                    value = cdnUrlInput,
                                    onValueChange = { cdnUrlInput = it },
                                    placeholder = { Text("https://cdn.example.com/script.js", fontSize = 12.sp, color = Color(0xFF484F58)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFF30363D),
                                        focusedContainerColor = Color(0xFF0D1117),
                                        unfocusedContainerColor = Color(0xFF0D1117)
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (cdnUrlInput.isNotBlank()) {
                                            val url = cdnUrlInput.trim()
                                            val injectCode = "(function(){var s=document.createElement('script');s.src='$url';document.head.appendChild(s);console.log('Injected URL script: $url');})();"
                                            viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> [Injected URL Script: $url]"))
                                            viewModel.pendingJsCommand = injectCode
                                            showLoadScriptDialog = false
                                            Toast.makeText(context, "Injecting script from URL...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = cdnUrlInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Inject URL Script", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = Color(0xFF30363D))

                                // 3. Preset Dev Tools Libraries
                                Text("Preset Developer Suite & Libraries", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                val presets = listOf(
                                    Triple(
                                        "Eruda Mobile Inspector",
                                        "Adds full DOM element inspector, network, & console overlay",
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/eruda'; document.body.appendChild(script); script.onload = function () { eruda.init(); }; })();"
                                    ),
                                    Triple(
                                        "vConsole Inspector",
                                        "Tencent Mobile DevTools overlay for mobile web debugging",
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/vconsole'; document.body.appendChild(script); script.onload = function () { new VConsole(); }; })();"
                                    ),
                                    Triple(
                                        "jQuery 3.7.1",
                                        "Load jQuery library for DOM manipulation in console",
                                        "(function () { var script = document.createElement('script'); script.src='https://code.jquery.com/jquery-3.7.1.min.js'; document.body.appendChild(script); console.log('jQuery 3.7.1 loaded! Use $'); })();"
                                    ),
                                    Triple(
                                        "Lodash 4.17.21",
                                        "Load Lodash utility library for console data manipulation",
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/lodash@4.17.21/lodash.min.js'; document.body.appendChild(script); console.log('Lodash loaded! Use _'); })();"
                                    )
                                )

                                presets.forEach { (title, desc, code) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> [Injected Preset: $title]"))
                                                viewModel.pendingJsCommand = code
                                                showLoadScriptDialog = false
                                                Toast.makeText(context, "Injected $title!", Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF161B22),
                                        border = BorderStroke(0.5.dp, Color(0xFF30363D))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(desc, fontSize = 10.sp, color = Color(0xFF8B949E))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showLoadScriptDialog = false }) {
                                Text("Close", color = Color(0xFF8B949E))
                            }
                        },
                        containerColor = Color(0xFF161B22)
                    )
                }
            }

            // 3.5 Site Info (Cookies, Privacy, Security) Bottom Sheet
            if (showSiteInfoSheet) {
                val currentDomain = remember(viewModel.currentUrl) {
                    try { Uri.parse(viewModel.currentUrl).host ?: viewModel.currentUrl } catch (e: Exception) { viewModel.currentUrl }
                }
                val isHttps = viewModel.currentUrl.startsWith("https://")

                ModalBottomSheet(
                    onDismissRequest = { showSiteInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data("https://www.google.com/s2/favicons?sz=128&domain=$currentDomain")
                                    .size(64, 64)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Favicon",
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass)
                            )
                            Column {
                                Text(
                                    text = currentDomain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isHttps) "Secure connection" else "Insecure connection",
                                    fontSize = 11.sp,
                                    color = if (isHttps) Color(0xFF34C759) else Color(0xFFFF9500)
                                )
                            }
                        }

                        HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = "Connection status",
                                tint = if (isHttps) Color(0xFF34C759) else Color(0xFFFF9500),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHttps) "Connection is secure" else "Connection is not secure",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isHttps)
                                        "Your information (for example, passwords or credit card numbers) is private when it is sent to this site."
                                    else
                                        "Your connection to this site is not secure. You should not enter any sensitive information (like passwords or cards).",
                                    fontSize = 12.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = "Cookies and Site Data",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cookies & site data",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                )
                                Text(
                                    text = "Cookies are used to keep you signed in, remember your preferences, and provide localized content.",
                                    fontSize = 12.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                                    lineHeight = 16.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.clearSiteData(context)
                                    showSiteInfoSheet = false
                                }
                            ) {
                                Text("Clear", color = Color(0xFFFF4444), fontSize = 13.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Privacy settings",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Privacy & Site Settings",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                )
                                Text(
                                    text = "JavaScript is active. Built-in Tracking Blockers are actively filtering ads and analytics for optimal speed.",
                                    fontSize = 12.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (extensionToDelete != null) {
                val ext = extensionToDelete!!
                val extDisplayName = remember(ext.id) {
                    val name = try { ext.metaData?.name } catch (_: Exception) { null }
                    if (!name.isNullOrBlank()) name
                    else ext.id.substringBefore("@").replace("-", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
                AlertDialog(
                    onDismissRequest = { extensionToDelete = null },
                    title = { Text("Delete Extension", color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black) },
                    text = { Text("Do you want to delete '$extDisplayName'?", color = if (viewModel.isDarkThemeEnabled) Color(0xFFC5D1DE) else Color.DarkGray) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.uninstallUserExtension(ext, context)
                                extensionToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { extensionToDelete = null }) {
                            Text("Cancel", color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color.Gray)
                        }
                    },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (builtInExtensionToDelete != null) {
                val name = builtInExtensionToDelete!!
                AlertDialog(
                    onDismissRequest = { builtInExtensionToDelete = null },
                    title = { Text("Delete Extension", color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black) },
                    text = { Text("Do you want to delete '$name'?", color = if (viewModel.isDarkThemeEnabled) Color(0xFFC5D1DE) else Color.DarkGray) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                when (name) {

                                    "Universal Text Copy" -> viewModel.uninstallUniversalCopy(context)
                                    "AI Blocker" -> viewModel.uninstallAiBlocker(context)
                                }
                                builtInExtensionToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { builtInExtensionToDelete = null }) {
                            Text("Cancel", color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color.Gray)
                        }
                    },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // ── Menu Bottom Sheet (Edge-style, classic mode only) ──────────────────
            if (showMenu && !viewModel.chromeNavBarEnabled) {
                val isDark = viewModel.isDarkThemeEnabled
                val inactiveIconBg = if (viewModel.isAmoledMode) Color(0xFF121212) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFF1F3F4)
                val inactiveIconTint = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                val activeIconTint = MaterialTheme.colorScheme.primary
                val activeIconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

                ModalBottomSheet(
                    onDismissRequest = { showMenu = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 6.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (viewModel.isAmoledMode) Color(0xFF222222) else if (isDark) Color(0xFF3A3A3C) else Color(0xFFC7C7CC))
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // ── Row 1: Primary actions ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MenuGridCell(
                                icon = Icons.Rounded.Bookmark,
                                label = "Bookmarks",
                                iconTint = activeIconTint,
                                iconBg = activeIconBg,
                                onClick = { showMenu = false; onOpenBookmarks() }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.History,
                                label = "History",
                                iconTint = activeIconTint,
                                iconBg = activeIconBg,
                                onClick = { showMenu = false; onOpenHistory() }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Download,
                                label = "Downloads",
                                iconTint = activeIconTint,
                                iconBg = activeIconBg,
                                onClick = { showMenu = false; onOpenDownloads() }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Settings,
                                label = "Settings",
                                iconTint = activeIconTint,
                                iconBg = activeIconBg,
                                onClick = { showMenu = false; onOpenSettings() }
                            )
                        }

                        HorizontalDivider(
                            color = if (isDark) Color(0xFF23374A).copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // ── Row 2: Secondary actions ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MenuGridCell(
                                icon = if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                label = "Incognito",
                                iconTint = if (viewModel.isIncognitoMode) activeIconTint else inactiveIconTint,
                                iconBg = if (viewModel.isIncognitoMode) activeIconBg else inactiveIconBg,
                                onClick = { viewModel.toggleIncognitoMode(context) }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.PlayCircle,
                                label = "Player\nSettings",
                                iconTint = inactiveIconTint,
                                iconBg = inactiveIconBg,
                                onClick = { showMenu = false; showPlayerSettingsDialog = true }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Computer,
                                label = "Desktop\nSite",
                                iconTint = if (viewModel.isDesktopMode) activeIconTint else inactiveIconTint,
                                iconBg = if (viewModel.isDesktopMode) activeIconBg else inactiveIconBg,
                                onClick = { viewModel.toggleDesktopMode(context) }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.OpenInFull,
                                label = if (isNavHideEnabled) "Nav\nHide On" else "Nav\nHide Off",
                                iconTint = if (isNavHideEnabled) activeIconTint else inactiveIconTint,
                                iconBg = if (isNavHideEnabled) activeIconBg else inactiveIconBg,
                                onClick = {
                                    showMenu = false
                                    val nextEnabled = !isNavHideEnabled
                                    isNavHideEnabled = nextEnabled
                                    isScrollNavBarVisible = true
                                    Toast.makeText(context, if (nextEnabled) "Nav hide enabled" else "Nav hide disabled", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── Row 3: Power tools ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MenuGridCell(
                                icon = Icons.Rounded.Search,
                                label = "Find in\nPage",
                                iconTint = inactiveIconTint,
                                iconBg = inactiveIconBg,
                                onClick = {
                                    showMenu = false
                                    if (!showHomeScreen && activeTab != null) {
                                        viewModel.openFindInPage()
                                    } else {
                                        Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Book,
                                label = "Reader\nMode",
                                iconTint = inactiveIconTint,
                                iconBg = inactiveIconBg,
                                onClick = {
                                    showMenu = false
                                    try { viewModel.toggleReaderMode() } catch (e: Exception) {
                                        Toast.makeText(context, "Reader Mode not supported on this page", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.LocalFireDepartment,
                                label = "Burn\nData",
                                iconTint = Color(0xFFFF4444),
                                iconBg = Color(0xFFFF4444).copy(alpha = 0.12f),
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        val runtime = viewModel.getGeckoRuntime(context)
                                        FireButton(runtime, context).burn()
                                        viewModel.burnAllData(context)
                                        Toast.makeText(context, "🔥 All history and tabs burned", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Add,
                                label = "Add to\nShortcuts",
                                iconTint = inactiveIconTint,
                                iconBg = inactiveIconBg,
                                onClick = {
                                    showMenu = false
                                    if (showHomeScreen || activeTab == null) {
                                        Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val currentUrl = viewModel.currentUrl
                                        val currentTitle = activeTab.title ?: "Webpage"
                                        viewModel.addShortcut(currentTitle, currentUrl)
                                    }
                                }
                            )
                            MenuGridCell(
                                icon = Icons.Rounded.Extension,
                                label = "Extensions",
                                iconTint = inactiveIconTint,
                                iconBg = inactiveIconBg,
                                onClick = {
                                    showMenu = false
                                    if (!viewModel.hasSeenExtensionsOverview) {
                                        pendingExtensionsAction = { showExtensionsSheet = true }
                                        showExtensionsOverviewDialog = true
                                    } else {
                                        showExtensionsSheet = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 4. Web Extensions Manager Bottom Sheet
            if (showExtensionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showExtensionsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                ) {
                    // Sync user extensions safely when sheet opens
                    LaunchedEffect(Unit) {
                        try {
                            viewModel.syncUserExtensions()
                        } catch (_: Exception) { /* ignore sync errors */ }
                    }

                    // Take a stable snapshot to avoid ConcurrentModificationException
                    val userExts = remember(viewModel.userExtensions.toList()) {
                        viewModel.userExtensions.toList()
                    }
                    val isDarkExt = viewModel.isDarkThemeEnabled
                    val totalInstalled = userExts.size
                    val totalEnabled = userExts.count {
                        try { it.metaData?.enabled == true } catch (_: Exception) { false }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Extensions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (totalInstalled > 0)
                                        "$totalInstalled installed · $totalEnabled active"
                                    else "Firefox Add-ons compatible",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // View Mode Selector (List vs Grid)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.saveExtensionViewMode(context, "List") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.List,
                                            contentDescription = "List View",
                                            tint = if (viewModel.extensionViewMode == "List")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.saveExtensionViewMode(context, "Grid") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Apps,
                                            contentDescription = "Grid View",
                                            tint = if (viewModel.extensionViewMode == "Grid")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Firefox AMO quick-link
                                Surface(
                                    onClick = {
                                        showExtensionsSheet = false
                                        viewModel.createNewTab(context, "https://addons.mozilla.org/en-US/android/")
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Store,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Browse Store",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDarkExt) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))

                         // ── User-installed section (shown first for fast access) ────────────
                        if (userExts.isNotEmpty()) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Installed Add-ons",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Count chip
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$totalInstalled",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (viewModel.extensionViewMode == "Grid") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    userExts.chunked(3).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEach { ext ->
                                                val isEnabled = ext.metaData.enabled
                                                val optionsUrl = try { ext.metaData?.optionsPageUrl } catch (_: Exception) { null }
                                                val iconBitmap = viewModel.extensionIcons[ext.id]

                                                Box(modifier = Modifier.weight(1f)) {
                                                    UserExtensionGridCard(
                                                        extension = ext,
                                                        checked = isEnabled,
                                                        enabled = !viewModel.togglingUserExtensionIds.contains(ext.id),
                                                        onCheckedChange = { viewModel.toggleUserExtension(ext, context) },
                                                        onUninstall = { extensionToDelete = ext },
                                                        onOptionsClick = if (!optionsUrl.isNullOrBlank()) {
                                                            { showExtensionsSheet = false; viewModel.loadUrl(optionsUrl) }
                                                        } else null,
                                                        onPopupClick = run {
                                                            val activeAction = viewModel.getActionForExtension(ext.id)
                                                            if (activeAction != null || !ext.metaData?.optionsPageUrl.isNullOrBlank()) {
                                                                {
                                                                    showExtensionsSheet = false
                                                                    if (activeAction != null) {
                                                                        activeAction.click()
                                                                    } else {
                                                                        val optionsUrl = ext.metaData?.optionsPageUrl
                                                                        if (!optionsUrl.isNullOrBlank()) {
                                                                            viewModel.loadUrl(optionsUrl)
                                                                        }
                                                                    }
                                                                }
                                                            } else null
                                                        },
                                                        iconBitmap = iconBitmap
                                                    )
                                                }
                                            }
                                            for (i in 0 until (3 - row.size)) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Dynamic reordering layout in Column
                                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                                var draggedOffset by remember { mutableStateOf(0f) }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    userExts.forEachIndexed { idx, ext ->
                                        val isDragged = draggedIndex == idx
                                        val isEnabled = ext.metaData.enabled
                                        val optionsUrl = try { ext.metaData?.optionsPageUrl } catch (_: Exception) { null }
                                        val iconBitmap = viewModel.extensionIcons[ext.id]
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    translationY = if (isDragged) draggedOffset else 0f
                                                    scaleX = if (isDragged) 1.02f else 1f
                                                    scaleY = if (isDragged) 1.02f else 1f
                                                    shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                                }
                                                .pointerInput(idx, userExts.size) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggedIndex = idx
                                                            draggedOffset = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            draggedOffset += dragAmount.y
                                                            val threshold = 76.dp.toPx()
                                                            val targetIndex = (idx + (draggedOffset / threshold).toInt()).coerceIn(0, userExts.size - 1)
                                                            if (targetIndex != idx && targetIndex != draggedIndex) {
                                                                viewModel.reorderUserExtensions(idx, targetIndex)
                                                                draggedIndex = targetIndex
                                                                draggedOffset = 0f
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            draggedIndex = null
                                                            draggedOffset = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggedIndex = null
                                                            draggedOffset = 0f
                                                        }
                                                    )
                                                }
                                        ) {
                                            UserExtensionItemCard(
                                                extension = ext,
                                                checked = isEnabled,
                                                enabled = !viewModel.togglingUserExtensionIds.contains(ext.id),
                                                onCheckedChange = { viewModel.toggleUserExtension(ext, context) },
                                                onUninstall = { extensionToDelete = ext },
                                                onOptionsClick = if (!optionsUrl.isNullOrBlank()) {
                                                    { showExtensionsSheet = false; viewModel.loadUrl(optionsUrl) }
                                                } else null,
                                                onPopupClick = run {
                                                    val activeAction = viewModel.getActionForExtension(ext.id)
                                                    if (activeAction != null || !ext.metaData?.optionsPageUrl.isNullOrBlank()) {
                                                        {
                                                            showExtensionsSheet = false
                                                            if (activeAction != null) {
                                                                activeAction.click()
                                                            } else {
                                                                val optUrl = ext.metaData?.optionsPageUrl
                                                                if (!optUrl.isNullOrBlank()) {
                                                                    viewModel.loadUrl(optUrl)
                                                                }
                                                            }
                                                        }
                                                    } else null
                                                },
                                                iconBitmap = iconBitmap
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Built-in Extensions (below installed for quick access) ────────────
                        HorizontalDivider(color = if (isDarkExt) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Built-in Extensions",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        val builtInExts = remember(
                            viewModel.isPopupBlockerEnabled,
                            viewModel.isUniversalCopyEnabled, viewModel.isUniversalCopyToggling,
                            viewModel.isMediaGrabberEnabled, viewModel.isMediaGrabberToggling,
                            viewModel.isAiBlockerEnabled, viewModel.isAiBlockerToggling
                        ) {
                            listOf(
                                BuiltInExt(Icons.Rounded.Block, "Popup Blocker", "Omni Browser Team",
                                    "Blocks auto-jumping ad tabs, pop-unders, and script-injected popups.",
                                    viewModel.isPopupBlockerEnabled, true,
                                    { viewModel.togglePopupBlocker(context) }),
                                BuiltInExt(Icons.Rounded.FileCopy, "Universal Copy", "Omni Browser Team",
                                    "Bypass website restrictions to force-enable text selection and copying.",
                                    viewModel.isUniversalCopyEnabled, !viewModel.isUniversalCopyToggling,
                                    { viewModel.toggleUniversalCopy(context) }),
                                BuiltInExt(Icons.Rounded.Download, "Media Sniffer", "Omni Browser Team",
                                    "Sniff and capture offline dynamic HLS/DASH segments and streams.",
                                    viewModel.isMediaGrabberEnabled, !viewModel.isMediaGrabberToggling,
                                    { viewModel.toggleMediaGrabber(context) }),
                                BuiltInExt(Icons.Rounded.Block, "AI Blocker", "Omni Browser Team",
                                    "Block AI Overview summaries and assistant panels on search engines.",
                                    viewModel.isAiBlockerEnabled, !viewModel.isAiBlockerToggling,
                                    { viewModel.toggleAiBlocker(context) })
                            )
                        }

                        if (viewModel.extensionViewMode == "Grid") {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                builtInExts.chunked(3).forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { ext ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                ExtensionGridCard(ext.icon, ext.name, ext.checked, ext.enabled, ext.onCheckedChange, ext.onUninstallClick)
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else {
                            builtInExts.forEach { ext ->
                                ExtensionItemCard(ext.icon, ext.name, ext.author, ext.description, ext.checked, ext.enabled, ext.onCheckedChange, ext.onUninstallClick)
                            }
                        }

                        // ── Get more add-ons CTA ──────────────────────────────────────────────
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            onClick = {
                                showExtensionsSheet = false
                                viewModel.createNewTab(context, "https://addons.mozilla.org/en-US/android/")
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Get More Firefox Add-ons",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }


            // 4b. Extension Popup / Composer Sheet
            // Opens the extension's browser-action popup (moz-extension://…/popup.html)
            // so users can interact with it fully: zoom in/out, pinch gesture, etc.
            if (viewModel.activeExtensionPopupSession != null) {

                // Zoom & pan state — reset each time a new extension popup is opened
                key(viewModel.activeExtensionPopupName) {
                    var popupScale by remember { mutableStateOf(1f) }
                    var popupOffset by remember { mutableStateOf(Offset.Zero) }

                    // Pinch-to-zoom + two-finger pan (single touch passes through to GeckoView)
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        popupScale = (popupScale * zoomChange).coerceIn(0.4f, 4f)
                        // Reset offset when near 1x so content snaps back to center
                        popupOffset = if (popupScale > 1.02f) popupOffset + panChange else Offset.Zero
                    }

                    ModalBottomSheet(
                        onDismissRequest = { viewModel.dismissExtensionPopup() },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.9f)
                                .navigationBarsPadding()
                        ) {
                            // ── Header ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Extension icon + name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = viewModel.activeExtensionPopupName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Extension",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                }

                                // ── Zoom controls ───────────────────────────────────
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Zoom Out
                                    IconButton(
                                        onClick = {
                                            popupScale = (popupScale - 0.15f).coerceAtLeast(0.4f)
                                            if (popupScale <= 1.02f) popupOffset = Offset.Zero
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ZoomOut,
                                            contentDescription = "Zoom Out",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Zoom percentage chip — tap to reset
                                    Surface(
                                        onClick = { popupScale = 1f; popupOffset = Offset.Zero },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        modifier = Modifier.widthIn(min = 42.dp)
                                    ) {
                                        Text(
                                            text = "${(popupScale * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Zoom In
                                    IconButton(
                                        onClick = { popupScale = (popupScale + 0.15f).coerceAtMost(4f) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ZoomIn,
                                            contentDescription = "Zoom In",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Close
                                    IconButton(
                                        onClick = { viewModel.dismissExtensionPopup() },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )

                            // ── Extension WebView with pinch-to-zoom ────────────────────
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clipToBounds()
                                    .transformable(state = transformState)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        org.mozilla.geckoview.GeckoView(ctx).apply {
                                            setSession(viewModel.activeExtensionPopupSession!!)
                                            isClickable = true
                                            isFocusable = true
                                            isFocusableInTouchMode = true
                                        }
                                    },
                                    update = { geckoView ->
                                        val session = viewModel.activeExtensionPopupSession
                                        if (session != null) geckoView.setSession(session)
                                        geckoView.scaleX = popupScale
                                        geckoView.scaleY = popupScale
                                        geckoView.translationX = popupOffset.x
                                        geckoView.translationY = popupOffset.y
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Loading overlay
                                if (viewModel.activeExtensionPopupLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (viewModel.isDarkThemeEnabled)
                                                    Color(0xFF0D1620).copy(alpha = 0.9f)
                                                else
                                                    Color.White.copy(alpha = 0.9f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = "Loading extension…",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // key block
            }


            // 5. Quick Tools Bottom Sheet
            if (showToolsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showToolsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Quick Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))

                        val cardModifier = Modifier.width(80.dp)
                        val isDark = viewModel.isDarkThemeEnabled
                        val isEditing = activeTab?.isEditModeEnabled ?: false
                        val haptic = LocalHapticFeedback.current

                        // Stable ordered list that reacts to ViewModel state
                        val toolOrderState = remember(viewModel.quickToolsOrder) {
                            mutableStateListOf<String>().also { list ->
                                val vmOrder = viewModel.quickToolsOrder
                                val allTools = listOf(
                                    "qr_scanner", "safe_locker", "translator", "edit_page",
                                    "save_pdf", "pin_web_app", "auto_scroll", "qr_scan_page",
                                    "qr_generator", "console_log", "dev_notes", "site_style"
                                )
                                list.addAll(vmOrder.filter { it in allTools } + allTools.filter { it !in vmOrder })
                            }
                        }
                        var draggedId by remember { mutableStateOf<String?>(null) }
                        val itemCenters = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
                        var gridTopLeft by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                        // Resolve tool display info
                        fun toolTitle(id: String): String = when (id) {
                            "qr_scanner"   -> "QR Scanner"
                            "safe_locker"  -> "Safe Locker"
                            "translator"   -> "Translator"
                            "edit_page"    -> if (isEditing) "Stop Edit" else "Edit Page"
                            "save_pdf"     -> "Save PDF"
                            "pin_web_app"  -> "Pin Web App"
                            "auto_scroll"  -> "Auto-Scroll"
                            "qr_scan_page" -> "QR Scan Page"
                            "qr_generator" -> "QR Generator"
                            "console_log"  -> "Console Log"
                            "dev_notes"    -> "Dev Notes"
                            "site_style"   -> "Site Style"
                            else -> id
                        }
                        fun toolIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
                            "qr_scanner"   -> Icons.Rounded.QrCodeScanner
                            "safe_locker"  -> Icons.Rounded.Lock
                            "translator"   -> Icons.Rounded.Translate
                            "edit_page"    -> Icons.Rounded.Edit
                            "save_pdf"     -> Icons.Rounded.Print
                            "pin_web_app"  -> Icons.AutoMirrored.Rounded.OpenInNew
                            "auto_scroll"  -> Icons.Rounded.ArrowDownward
                            "qr_scan_page" -> Icons.Rounded.CenterFocusWeak
                            "qr_generator" -> Icons.Rounded.QrCode2
                            "console_log"  -> Icons.Rounded.Terminal
                            "dev_notes"    -> Icons.Rounded.Description
                            "site_style"   -> Icons.Rounded.Palette
                            else -> Icons.Rounded.Build
                        }
                        fun toolAction(id: String): () -> Unit = when (id) {
                            "qr_scanner" -> ({
                                showToolsSheet = false
                                if (!viewModel.hasSeenQrOverview) { pendingQrAction = onOpenQrTools; showQrOverviewDialog = true } else onOpenQrTools()
                            })
                            "safe_locker"  -> ({ showToolsSheet = false; onOpenLocker() })
                            "translator"   -> ({ showToolsSheet = false; translationSourceText = ""; translationResultText = ""; showTranslationDialog = true })
                            "edit_page" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; if (!viewModel.hasSeenEditPageOverview) { pendingEditPageAction = { viewModel.toggleEditMode() }; showEditPageOverviewDialog = true } else viewModel.toggleEditMode() }
                            })
                            "save_pdf" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; if (!viewModel.hasSeenPdfOverview) { pendingPdfAction = { viewModel.printCurrentPage(context) }; showPdfOverviewDialog = true } else viewModel.printCurrentPage(context) }
                            })
                            "pin_web_app" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; viewModel.installWebAppShortcut(context, activeTab.title, activeTab.url) }
                            })
                            "auto_scroll" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; isAutoScrollActive = !isAutoScrollActive }
                            })
                            "qr_scan_page" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; if (!viewModel.hasSeenQrOverview) { pendingQrAction = { viewModel.scanPageForQrCodes() }; showQrOverviewDialog = true } else viewModel.scanPageForQrCodes() }
                            })
                            "qr_generator" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; if (!viewModel.hasSeenQrOverview) { pendingQrAction = { qrGeneratorUrl = activeTab.url; showQrGeneratorDialog = true }; showQrOverviewDialog = true } else { qrGeneratorUrl = activeTab.url; showQrGeneratorDialog = true } }
                            })
                            "console_log" -> ({
                                showToolsSheet = false
                                if (!viewModel.hasSeenConsoleOverview) { pendingConsoleAction = { showConsoleSheet = true }; showConsoleOverviewDialog = true } else showConsoleSheet = true
                            })
                            "dev_notes" -> ({
                                showToolsSheet = false
                                if (!viewModel.hasSeenDevNotesOverview) { pendingDevNotesAction = { showDevNotesSheet = true }; showDevNotesOverviewDialog = true } else showDevNotesSheet = true
                            })
                            "site_style" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                else { showToolsSheet = false; showSiteStyleCustomizerSheet = true }
                            })
                            else -> ({})
                        }

                        // "Hold & drag to reorder" hint
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenWith,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hold & drag to reorder",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // Drag-to-reorder grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    gridTopLeft = coords.boundsInWindow().topLeft
                                }
                                .pointerInput(Unit) {
                                    var lastSwapMs = 0L
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val absPos = androidx.compose.ui.geometry.Offset(
                                                gridTopLeft.x + offset.x,
                                                gridTopLeft.y + offset.y
                                            )
                                            draggedId = itemCenters.entries.minByOrNull { (_, c) ->
                                                val dx = c.x - absPos.x; val dy = c.y - absPos.y; dx * dx + dy * dy
                                            }?.key
                                            if (draggedId != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, _ ->
                                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                                            val absPos = androidx.compose.ui.geometry.Offset(
                                                gridTopLeft.x + change.position.x,
                                                gridTopLeft.y + change.position.y
                                            )
                                            val myCenter = itemCenters[id] ?: return@detectDragGesturesAfterLongPress
                                            val myDistSq = run { val dx = myCenter.x - absPos.x; val dy = myCenter.y - absPos.y; dx * dx + dy * dy }
                                            val closestEntry = itemCenters.entries
                                                .filter { it.key != id }
                                                .minByOrNull { (_, c) -> val dx = c.x - absPos.x; val dy = c.y - absPos.y; dx * dx + dy * dy }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val closestDistSq = closestEntry.value.let { c -> val dx = c.x - absPos.x; val dy = c.y - absPos.y; dx * dx + dy * dy }
                                            val now = System.currentTimeMillis()
                                            if (closestDistSq < myDistSq && now - lastSwapMs > 120L) {
                                                val from = toolOrderState.indexOf(id)
                                                val to = toolOrderState.indexOf(closestEntry.key)
                                                if (from != -1 && to != -1 && from != to) {
                                                    toolOrderState.removeAt(from)
                                                    toolOrderState.add(to, id)
                                                    lastSwapMs = now
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            viewModel.saveQuickToolsOrder(context, toolOrderState.toList())
                                            draggedId = null
                                        },
                                        onDragCancel = { draggedId = null }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                toolOrderState.chunked(4).forEach { row ->
                                    val paddedRow = row + List(4 - row.size) { "" }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        paddedRow.forEach { toolId ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .onGloballyPositioned { coords ->
                                                        if (toolId.isNotEmpty()) {
                                                            itemCenters[toolId] = coords.boundsInWindow().center
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (toolId.isNotEmpty()) {
                                                    val isDragged = draggedId == toolId
                                                    ToolCard(
                                                        title = toolTitle(toolId),
                                                        icon = toolIcon(toolId),
                                                        isDarkTheme = isDark,
                                                        modifier = cardModifier.graphicsLayer {
                                                            scaleX = if (isDragged) 1.12f else 1f
                                                            scaleY = if (isDragged) 1.12f else 1f
                                                            shadowElevation = if (isDragged) 18f else 0f
                                                            alpha = if (isDragged) 0.82f else 1f
                                                        },
                                                        onClick = if (isDragged) ({}) else toolAction(toolId)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                    }
                }
            }
            
            // 6. Dev Notes & Vault Bottom Sheet
            if (showDevNotesSheet) {
                DevNotesSheetContent(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    onDismissRequest = { showDevNotesSheet = false }
                )
            }

            if (showSiteStyleCustomizerSheet) {
                SiteStyleCustomizerSheetContent(
                    viewModel = viewModel,
                    onDismissRequest = { showSiteStyleCustomizerSheet = false }
                )
            }

            // --- Password Manager Banners ---
            val saveCred = viewModel.pendingSaveCredential
            val autofillSuggestion = viewModel.autofillSuggestion

            // Save-password banner: slides up from bottom when GeckoView fires onLoginSave
            AnimatedVisibility(
                visible = saveCred != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
            ) {
                if (saveCred != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Save password for ${saveCred.domain}?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = saveCred.username,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { viewModel.dismissSaveCredential() }) {
                                Text("Ignore", color = Color.Gray, fontSize = 13.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.savePassword(saveCred.domain, saveCred.username, saveCred.password)
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Autofill suggestion bar: appears at bottom when visiting a site with saved credentials
            AnimatedVisibility(
                visible = autofillSuggestion != null && saveCred == null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
            ) {
                if (autofillSuggestion != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign in as ${autofillSuggestion.username}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = autofillSuggestion.domain,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            TextButton(onClick = { viewModel.dismissAutofill() }) {
                                Text("Dismiss", color = Color.Gray, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { autofillSuggestion?.let { viewModel.autofillCredential(it) } },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Fill In", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // QR Overview Dialog
            if (showQrOverviewDialog) {
                FeatureOverviewDialog(
                    title = "QR Scanner & Generator",
                    subtitle = "Fully Local & Secure",
                    description = "Omni Browser integrates local QR code utilities powered completely offline by Google ML Kit.\n\n• QR Scan Page: Automatically detects and decodes any QR codes visible on your current tab's screen.\n• QR Generator: Instantly converts your active webpage URL into a QR code for quick sharing. You can also customize the text/URL in real-time.",
                    icon = Icons.Rounded.QrCodeScanner,
                    accentColor = Color(0xFF00D2C4), // Cyan/Teal
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showQrOverviewDialog = false
                        viewModel.saveQrOverviewSeen(context, true)
                        pendingQrAction?.invoke()
                        pendingQrAction = null
                    }
                )
            }

            // PDF Overview Dialog
            if (showPdfOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Web Page to PDF",
                    subtitle = "Clean offline saving",
                    description = "Convert any webpage or web article into a clean, read-optimized PDF document locally on your device.\n\nGreat for saving news, documentation, or study materials for offline access without external trackers.",
                    icon = Icons.Rounded.Print,
                    accentColor = Color(0xFF30D158), // Emerald Green
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showPdfOverviewDialog = false
                        viewModel.savePdfOverviewSeen(context, true)
                        pendingPdfAction?.invoke()
                        pendingPdfAction = null
                    }
                )
            }

            // Video Player & Downloader Dialog
            if (showVideoOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Media Player & Downloader",
                    subtitle = "Disclaimer & Rules",
                    description = "Stream video feeds through our hardware-accelerated Media3 player with gesture controls and multi-threaded parallel downloads.\n\n⚠️ Piracy Disclaimer: Omni Browser does not host, index, or endorse the download of copyrighted content. Downloads are only permitted for personal, non-commercial use of public or freely available media.\n\n🚫 YouTube/Google Restriction: In compliance with terms of service, video detection and downloading are disabled on YouTube and other Google services by default. Enable them in Native Video Player settings if you accept the terms-of-service risk.",
                    icon = Icons.Rounded.Download,
                    accentColor = Color(0xFFFF6D00), // Sunset Orange
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showVideoOverviewDialog = false
                        viewModel.saveVideoOverviewSeen(context, true)
                        pendingVideoAction?.invoke()
                        pendingVideoAction = null
                    }
                )
            }

            // Extensions Overview Dialog
            if (showExtensionsOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Firefox Desktop Extensions",
                    subtitle = "Ad-blockers & Developer tools",
                    description = "Omni Browser supports desktop Firefox extensions. You can install tools like uBlock Origin directly from the extensions panel to block ads and trackers.\n\nAll extension processing runs entirely local to your device.",
                    icon = Icons.Rounded.Extension,
                    accentColor = Color(0xFFFF3B5C), // Crimson Red
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showExtensionsOverviewDialog = false
                        viewModel.saveExtensionsOverviewSeen(context, true)
                        pendingExtensionsAction?.invoke()
                        pendingExtensionsAction = null
                    }
                )
            }
            
            // Edit Page Overview Dialog
            if (showEditPageOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Web Page Editor",
                    subtitle = "Live DOM Manipulation",
                    description = "Omni Browser allows you to edit the text and elements of any webpage in real-time.\n\nSimply tap elements on the screen to modify text, delete components, or change layout. Tap the tool again to save your edits or exit edit mode.",
                    icon = Icons.Rounded.Edit,
                    accentColor = Color(0xFF5E5CE6), // Royal Purple
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showEditPageOverviewDialog = false
                        viewModel.saveEditPageOverviewSeen(context, true)
                        pendingEditPageAction?.invoke()
                        pendingEditPageAction = null
                    }
                )
            }

            // Console Log Overview Dialog
            if (showConsoleOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Developer Console",
                    subtitle = "JS Logs & Diagnostics",
                    description = "Inspect console messages, warnings, and errors printed by the active webpage.\n\nGreat for debugging scripts, tracking network requests, or analyzing webpage behavior in real-time.",
                    icon = Icons.Rounded.Terminal,
                    accentColor = Color(0xFFF1C40F), // Glow Yellow/Amber
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showConsoleOverviewDialog = false
                        viewModel.saveConsoleOverviewSeen(context, true)
                        pendingConsoleAction?.invoke()
                        pendingConsoleAction = null
                    }
                )
            }

            // Dev Notes Overview Dialog
            if (showDevNotesOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Dev Notes & Vault",
                    subtitle = "Secure Offline Credentials & Snippets",
                    description = "Store passwords, API keys, code snippets, and urls 100% offline securely inside the app sandbox.\n\nIncludes an helper keyboard row for quick symbols insertion (`{}`, `[]`, `=>`, `;`), paste/copy clipboard operators, and direct URL navigation with automated password copying.",
                    icon = Icons.Rounded.Description,
                    accentColor = Color(0xFF9B59B6), // Purple
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showDevNotesOverviewDialog = false
                        viewModel.saveDevNotesOverviewSeen(context, true)
                        pendingDevNotesAction?.invoke()
                        pendingDevNotesAction = null
                    }
                )
            }
            
            // Site permission prompt overlay
            viewModel.activePermissionPrompt?.let { prompt ->
                PermissionPromptDialog(prompt = prompt, isDarkThemeEnabled = viewModel.isDarkThemeEnabled)
            }

            // Site WebRTC media permission prompt overlay
            viewModel.activeMediaPermissionPrompt?.let { prompt ->
                MediaPermissionPromptDialog(prompt = prompt, isDarkThemeEnabled = viewModel.isDarkThemeEnabled)
            }
            
            // Native Player Settings dialog
            if (showPlayerSettingsDialog) {
                PlayerSettingsDialog(
                    viewModel = viewModel,
                    onDismissRequest = { showPlayerSettingsDialog = false }
                )
            }

            // QR Scan Result Composer Overlay
            if (showQrScanResult) {
                QrScanResultComposer(
                    results = viewModel.qrScanResults,
                    onOpenUrl = { url ->
                        viewModel.loadUrl(url)
                    },
                    onDismiss = {
                        showQrScanResult = false
                        viewModel.clearQrScanResults()
                    },
                    isDarkTheme = viewModel.isDarkThemeEnabled
                )
            }

            // QR Generator Dialog Bottom Sheet
            if (showQrGeneratorDialog) {
                QrGeneratorDialog(
                    initialUrl = qrGeneratorUrl,
                    onDismissRequest = { showQrGeneratorDialog = false },
                    isDarkTheme = viewModel.isDarkThemeEnabled
                )
            }

            // ── Generic file download destination dialog ───────────────────────────
            viewModel.pendingGenericDownload?.let { pending ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.pendingGenericDownload = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val ext = pending.filename.substringAfterLast('.').lowercase()
                        val (fileIcon, fileColor) = when {
                            ext == "pdf" -> Icons.Rounded.PictureAsPdf to Color(0xFFE53935)
                            ext == "apk" -> Icons.Rounded.Android to Color(0xFF43A047)
                            ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip to Color(0xFFFF8F00)
                            ext in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac") -> Icons.Rounded.MusicNote to Color(0xFF8E24AA)
                            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Rounded.Image to Color(0xFF039BE5)
                            ext in setOf("doc", "docx", "txt", "rtf") -> Icons.Rounded.Description to Color(0xFF1E88E5)
                            ext in setOf("xls", "xlsx", "csv") -> Icons.Rounded.TableChart to Color(0xFF43A047)
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(fileColor.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(fileIcon, contentDescription = null, tint = fileColor, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download File",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pending.filename,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Button(
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = false) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Locally", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = true) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(32.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Private Vault 🔒", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        TextButton(
                            onClick = { viewModel.pendingGenericDownload = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

        // Lock Screen Overlay
        if (viewModel.isIncognitoMode && viewModel.lockIncognito && !viewModel.isIncognitoUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A0F))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFFCBB2FF),
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Incognito locked",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Authenticate to access your private tabs.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { tryUnlockIncognito() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF), contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp).padding(horizontal = 16.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Google OAuth Native Account Picker ─────────────────────────────────
        val accountLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
                android.util.Log.i("BrowserScreen", "🔑 User selected Google account: $accountName")
                viewModel.resumeGoogleOAuth(accountName)
            } else {
                android.util.Log.i("BrowserScreen", "🔑 Google account picker dismissed/cancelled by user")
                viewModel.dismissGoogleOAuth()
            }
        }

        val pendingOAuth = viewModel.pendingGoogleOAuthRequest
        LaunchedEffect(pendingOAuth) {
            if (pendingOAuth != null) {
                try {
                    val intent = android.accounts.AccountManager.newChooseAccountIntent(
                        null, // selectedAccount
                        null, // allowableAccounts
                        arrayOf("com.google"), // allowableAccountTypes
                        true, // alwaysPromptForAccount
                        null, // descriptionOverrideText
                        null, // addAccountRequiredFeatures
                        null, // addAccountAuthTokenType
                        null  // options
                    )
                    accountLauncher.launch(intent)
                } catch (e: Exception) {
                    android.util.Log.e("BrowserScreen", "🔑 Error launching system account picker: ${e.message}")
                    // Fallback: resume without hint so the user can sign in manually
                    viewModel.resumeGoogleOAuth(null)
                }
            }
        }
    }
}

data class BuiltInExt(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val name: String,
    val author: String,
    val description: String,
    val checked: Boolean,
    val enabled: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val onUninstallClick: (() -> Unit)? = null
)

@Composable
private fun MediaSnifferBanner(
    viewModel: BrowserViewModel,
    nonDrmMedia: List<com.rebelroot.omni.media.MediaInterceptor.DetectedMedia>,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    onDownloadClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = Color(0xFF1B2234)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (viewModel.isVideoPlayingInPage) {
                EqualizerIcon(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "Video Detected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (viewModel.isVideoPlayingInPage) "Video is playing" else "Video playing detected",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    val firstMedia = nonDrmMedia.firstOrNull()
                    if (firstMedia != null) {
                        onPlay(firstMedia.url)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play in Premium Player",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download Options",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
