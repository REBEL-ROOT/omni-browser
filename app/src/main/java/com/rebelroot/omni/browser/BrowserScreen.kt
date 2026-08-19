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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.zIndex
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
import kotlin.math.abs
 import androidx.compose.foundation.gestures.rememberTransformableState
 import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass


/**
 * Parses an ISO-format date/time string from GeckoView DateTimePrompt into a Calendar.
 * Handles: "yyyy-MM-dd", "yyyy-MM", "yyyy-Www", "HH:mm", "yyyy-MM-dd'T'HH:mm"
 */
private fun parseDateFromIso(value: String?): java.util.Calendar? {
    if (value.isNullOrBlank()) return null
    return try {
        val cal = java.util.Calendar.getInstance()
        when {
            // "yyyy-MM-dd'T'HH:mm" (datetime-local)
            value.contains("T") -> {
                val parts = value.split("T")
                val dateParts = parts[0].split("-").mapNotNull { it.toIntOrNull() }
                val timeParts = parts.getOrNull(1)?.split(":")?.mapNotNull { it.toIntOrNull() }
                if (dateParts.size >= 3) {
                    cal.set(dateParts[0], dateParts[1] - 1, dateParts[2])
                }
                if (timeParts != null && timeParts.size >= 2) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timeParts[0])
                    cal.set(java.util.Calendar.MINUTE, timeParts[1])
                }
                cal
            }
            // "yyyy-Www" (week — approximate to Monday of that week)
            value.matches(Regex("\\d{4}-W\\d{2}")) -> {
                val parts = value.split("-W")
                val year = parts[0].toInt()
                val week = parts[1].toInt()
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.minimalDaysInFirstWeek = 4
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.WEEK_OF_YEAR, week)
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                cal
            }
            // "yyyy-MM-dd" (date)
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                val parts = value.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size >= 3) cal.set(parts[0], parts[1] - 1, parts[2])
                cal
            }
            // "yyyy-MM" (month)
            value.matches(Regex("\\d{4}-\\d{2}")) -> {
                val parts = value.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size >= 2) cal.set(parts[0], parts[1] - 1, 1)
                cal
            }
            // "HH:mm" (time only — use today's date)
            value.matches(Regex("\\d{2}:\\d{2}")) -> {
                val parts = value.split(":").mapNotNull { it.toIntOrNull() }
                if (parts.size >= 2) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0])
                    cal.set(java.util.Calendar.MINUTE, parts[1])
                }
                cal
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenLocker: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPasswordManager: () -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenNewsCenter: () -> Unit = {},
    onOpenWallpapers: () -> Unit = {},
    onPlayOnlineStream: (String, String) -> Unit,
    onExitBrowser: () -> Unit,
    onOpenVisualBlockSettings: () -> Unit = {},
    onOpenUserAgentSettings: () -> Unit = {}
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
                context.getString(R.string.browser_unlock_incognito_tabs),
                context.getString(R.string.browser_auth_view_private)
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

    LaunchedEffect(viewModel.navigateToVisualBlockSettingsTrigger) {
        if (viewModel.navigateToVisualBlockSettingsTrigger) {
            viewModel.navigateToVisualBlockSettingsTrigger = false
            onOpenVisualBlockSettings()
        }
    }

    LaunchedEffect(viewModel.navigateToUserAgentSettingsTrigger) {
        if (viewModel.navigateToUserAgentSettingsTrigger) {
            viewModel.navigateToUserAgentSettingsTrigger = false
            onOpenUserAgentSettings()
        }
    }

    LaunchedEffect(viewModel.isIncognitoMode) {
        if (viewModel.isIncognitoMode && viewModel.lockIncognito && !viewModel.isIncognitoUnlocked) {
            tryUnlockIncognito()
        }
    }
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
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
    val config = getUiSizeConfig(viewModel.uiScale, configuration.screenWidthDp)
    var dragAmountAccumulated by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    val currentUrlLower = viewModel.currentUrl.lowercase()
    val isConfig = currentUrlLower == "omni:config" || currentUrlLower == "omni://config" || currentUrlLower == "about:config"
    val showHomeScreen = (viewModel.currentUrl == "about:blank" || viewModel.currentUrl.isEmpty()) && !isConfig
    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }

    
    var showMenu by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var showSiteInfoSheet by remember { mutableStateOf(false) }
    var showPrivacyReportSheet by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)) }
    LaunchedEffect(inputUrl.text) {
        viewModel.fetchSearchSuggestions(inputUrl.text)
        viewModel.fetchHistorySuggestions(inputUrl.text)
    }
    var isInputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // Video detection states
    val detectedMedia by viewModel.mediaInterceptor.detectedMedia.collectAsState()
    val playableMedia by viewModel.mediaInterceptor.playableMedia.collectAsState()
    val hasPlayableMedia by viewModel.mediaInterceptor.hasPlayableMedia.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var isAlohaBannerDismissed by remember { mutableStateOf(false) }
    val nonDrmMedia = remember(detectedMedia) { detectedMedia.filter { !it.isDrmProtected } }
    val showAlohaBanner = nonDrmMedia.isNotEmpty() && !isAlohaBannerDismissed && !showHomeScreen && !viewModel.isReaderModeActive && !viewModel.isFullscreen && viewModel.isMediaGrabberEnabled && !viewModel.isUrlBlockedByMediaSniffer(viewModel.currentUrl)
    var isScrollNavBarVisible by remember { mutableStateOf(true) }
    var isNavHideEnabled by remember { mutableStateOf(true) }
    var currentScrollPos by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var measuredTopBarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
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
        isScrollNavBarVisible = true
        isAlohaBannerDismissed = false
    }
    LaunchedEffect(viewModel.isVideoPlayingInPage) {
        if (viewModel.isVideoPlayingInPage) {
            isAlohaBannerDismissed = false
        }
    }
    LaunchedEffect(isNavHideEnabled) {
        if (!isNavHideEnabled) {
            isScrollNavBarVisible = true
        }
    }
    var showExtensionsSheet by remember { mutableStateOf(false) }
    var extensionToDelete by remember { mutableStateOf<org.mozilla.geckoview.WebExtension?>(null) }
    var builtInExtensionToDelete by remember { mutableStateOf<String?>(null) }

    // Fullscreen video buttons overlay
    var showFullscreenDownloadBtn by remember { mutableStateOf(true) }
    var fullscreenControlsLastActivityMs by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-fade controls after 3.5s of inactivity in fullscreen
    LaunchedEffect(showFullscreenDownloadBtn, fullscreenControlsLastActivityMs) {
        if (showFullscreenDownloadBtn) {
            delay(3500L)
            showFullscreenDownloadBtn = false
        }
    }

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
    // Issue #73: The site's own video player is NEVER overridden automatically.
    // The native player is launched only when the user taps the dedicated media
    // button and selects a stream. The previous auto-override on fullscreen is removed.

    // Offline Translation states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showSpoofIdentityDialog by remember { mutableStateOf(false) }
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
    var showGroupDialog by remember { mutableStateOf(false) }
    var showCreateGroupComposer by remember { mutableStateOf(false) }
    var groupDialogTargetTabId by remember { mutableStateOf<String?>(null) }

    var newGroupTitle by remember { mutableStateOf("") }
    var newGroupColorIndex by remember { mutableStateOf(0) }
    var showRenameGroupDialog by remember { mutableStateOf(false) }
    var renameGroupTarget by remember { mutableStateOf<TabGroup?>(null) }
    var renameGroupText by remember { mutableStateOf("") }
    
    // Developer Console state
    var showConsoleSheet by remember { mutableStateOf(false) }
    var showDevNotesSheet by remember { mutableStateOf(false) }
    var showSiteStyleCustomizerSheet by remember { mutableStateOf(false) }

    // Tools sheet state
    var showToolsSheet by remember { mutableStateOf(false) }
    var showQuickToolsSheet by remember { mutableStateOf(false) }
    var showImageGrabberSheet by remember { mutableStateOf(false) }
    var showPageInspectorSheet by remember { mutableStateOf(false) }
    var showAllInOneMenuSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showTorrentDownloaderDialog by remember { mutableStateOf(false) }
    var showSpeedDialSheet by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
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

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val config = viewModel.customVpnConfig
            if (!config.isNullOrBlank()) {
                viewModel.connectCustomVpn()
                Toast.makeText(context, "🛡️ Connecting to custom VPN...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "VPN permission denied", Toast.LENGTH_SHORT).show()
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

    // ── <select> Choice Prompt (Issue #74) ──────────────────────────────────────
    // Derive dialog visibility directly from ViewModel state — no LaunchedEffect
    // needed. This ensures Compose always recomposes when pendingChoicePrompt changes.
    // Placed at TOP LEVEL (outside Scaffold) to avoid being gated by conditional content.
    val showChoiceDialog = viewModel.pendingChoicePrompt != null

    // ── Date/Time Picker Prompt (Issue #74) ─────────────────────────────────────
    LaunchedEffect(viewModel.pendingDatePrompt) {
        val pending = viewModel.pendingDatePrompt
        if (pending == null) {
            return@LaunchedEffect
        }
        val prompt = pending.prompt

        // Resolve Activity context for native dialog windows.
        val activity = run {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            (ctx as? Activity) ?: com.rebelroot.omni.MainActivity.getActiveActivity()
        }

        if (activity == null) {
            viewModel.cancelDateTimePrompt()
            return@LaunchedEffect
        }

        // Parse the default value if present. GeckoView uses HTML5 value format strings.
        val defaultDate = parseDateFromIso(prompt.defaultValue)
        val minDate = parseDateFromIso(prompt.minValue)
        val maxDate = parseDateFromIso(prompt.maxValue)

        when (prompt.type) {
            org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE -> {
                val datePicker = android.app.DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val isoValue = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                        viewModel.deliverDateTimePromptResult(isoValue)
                    },
                    defaultDate?.get(java.util.Calendar.YEAR) ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                    defaultDate?.get(java.util.Calendar.MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                    defaultDate?.get(java.util.Calendar.DAY_OF_MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                )
                datePicker.setOnCancelListener { viewModel.cancelDateTimePrompt() }
                minDate?.let { datePicker.datePicker.minDate = it.timeInMillis }
                maxDate?.let { datePicker.datePicker.maxDate = it.timeInMillis }
                datePicker.show()
            }
            org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> {
                val cal = defaultDate ?: java.util.Calendar.getInstance()
                val timePicker = android.app.TimePickerDialog(
                    activity,
                    { _, hourOfDay, minute ->
                        val isoValue = String.format("%02d:%02d", hourOfDay, minute)
                        viewModel.deliverDateTimePromptResult(isoValue)
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true // 24-hour format
                )
                timePicker.setOnCancelListener { viewModel.cancelDateTimePrompt() }
                timePicker.show()
            }
            org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> {
                // Show DatePicker first, then chain to TimePicker
                val datePicker = android.app.DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val datePart = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                        // Now show TimePicker
                        val cal = defaultDate ?: java.util.Calendar.getInstance()
                        val timePicker = android.app.TimePickerDialog(
                            activity,
                            { _, hourOfDay, minute ->
                                val isoValue = "${datePart}T${String.format("%02d:%02d", hourOfDay, minute)}"
                                viewModel.deliverDateTimePromptResult(isoValue)
                            },
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE),
                            true
                        )
                        timePicker.setOnCancelListener {
                            // User cancelled time — deliver just the date (partial result)
                            viewModel.deliverDateTimePromptResult(datePart)
                        }
                        timePicker.show()
                    },
                    defaultDate?.get(java.util.Calendar.YEAR) ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                    defaultDate?.get(java.util.Calendar.MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                    defaultDate?.get(java.util.Calendar.DAY_OF_MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                )
                datePicker.setOnCancelListener { viewModel.cancelDateTimePrompt() }
                minDate?.let { datePicker.datePicker.minDate = it.timeInMillis }
                maxDate?.let { datePicker.datePicker.maxDate = it.timeInMillis }
                datePicker.show()
            }
            org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> {
                // Android has no built-in month picker. Use a DatePicker and extract year-month.
                val datePicker = android.app.DatePickerDialog(
                    activity,
                    { _, year, month, _ ->
                        val isoValue = String.format("%04d-%02d", year, month + 1)
                        viewModel.deliverDateTimePromptResult(isoValue)
                    },
                    defaultDate?.get(java.util.Calendar.YEAR) ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                    defaultDate?.get(java.util.Calendar.MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                    defaultDate?.get(java.util.Calendar.DAY_OF_MONTH) ?: 1
                )
                datePicker.setOnCancelListener { viewModel.cancelDateTimePrompt() }
                minDate?.let { datePicker.datePicker.minDate = it.timeInMillis }
                maxDate?.let { datePicker.datePicker.maxDate = it.timeInMillis }
                datePicker.show()
            }
            org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> {
                // Android has no built-in week picker. Use a DatePicker and convert to ISO week.
                val datePicker = android.app.DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val cal = java.util.Calendar.getInstance()
                        cal.set(year, month, dayOfMonth)
                        cal.firstDayOfWeek = java.util.Calendar.MONDAY
                        cal.minimalDaysInFirstWeek = 4
                        val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                        // Handle year edge: week 1 may belong to next calendar year
                        val weekYear = cal.get(java.util.Calendar.YEAR)
                        val isoValue = String.format("%04d-W%02d", weekYear, week)
                        viewModel.deliverDateTimePromptResult(isoValue)
                    },
                    defaultDate?.get(java.util.Calendar.YEAR) ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                    defaultDate?.get(java.util.Calendar.MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                    defaultDate?.get(java.util.Calendar.DAY_OF_MONTH) ?: java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                )
                datePicker.setOnCancelListener { viewModel.cancelDateTimePrompt() }
                minDate?.let { datePicker.datePicker.minDate = it.timeInMillis }
                maxDate?.let { datePicker.datePicker.maxDate = it.timeInMillis }
                datePicker.show()
            }
            else -> {
                Log.w("BrowserScreen", "onDateTimePrompt: unknown type=${prompt.type}")
                viewModel.cancelDateTimePrompt()
            }
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
        if (viewModel.isFullscreen) {
            showFullscreenDownloadBtn = true
            fullscreenControlsLastActivityMs = System.currentTimeMillis()
        }
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
                // Force-refresh scroll metrics via the GeckoView subclass lambda
                viewModel.refreshScrollMetrics?.invoke()
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

    // Exit bottom sheet — shown on first back press from home screen
    var showExitSheet by remember { mutableStateOf(false) }

    // Only intercept back when the browser screen is actually in focus.
    // The video player screen has its own BackHandler that takes priority when it is
    // composed on top, so this handler is only active when the browser is the top destination.
    androidx.activity.compose.BackHandler(enabled = true) {
        val activity = context as? android.app.Activity
        if (viewModel.isFullscreen) {
            activeTab?.session?.exitFullScreen()
            viewModel.isFullscreen = false
            return@BackHandler
        }
        if (!showHomeScreen) {
            if (viewModel.canGoBack) {
                // Navigate the active tab's GeckoSession back safely
                try { viewModel.goBack() } catch (e: Exception) {
                    android.util.Log.w("BackHandler", "goBack() error, handling back stack: ${e.message}")
                    if (viewModel.isExternalIntentLaunch && activity != null) {
                        if (activity.isTaskRoot) activity.finishAndRemoveTask() else activity.finish()
                    } else {
                        viewModel.navigateHomeDirectly()
                    }
                }
            } else if (viewModel.isExternalIntentLaunch && activity != null) {
                // Session launched via external ACTION_VIEW intent (e.g., RSS app) with no web history left -> return to caller's task stack
                android.util.Log.i("BackHandler", "🔙 External intent back target reached: returning to host app")
                if (activity.isTaskRoot) activity.finishAndRemoveTask() else activity.finish()
            } else {
                // No history left – go to home screen without touching session
                viewModel.navigateHomeDirectly()
            }
        } else {
            if (viewModel.isExternalIntentLaunch && activity != null) {
                if (activity.isTaskRoot) activity.finishAndRemoveTask() else activity.finish()
            } else {
                // On home screen — open exit options sheet immediately (single press)
                showExitSheet = true
            }
        }
    }

    if (showExitSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showExitSheet = false },
            containerColor = if (viewModel.isAmoledMode) Color(0xFF0D0D0D) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Header
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.exit_sheet_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.exit_sheet_subtitle),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 3.dp, bottom = 20.dp)
                )

                // --- Option 1: Just Quit ---
                ExitOptionRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = stringResource(R.string.exit_quit),
                    subtitle = stringResource(R.string.exit_quit_desc),
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    isDark = viewModel.isDarkThemeEnabled,
                    onClick = {
                        showExitSheet = false
                        onExitBrowser()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Option 2: Quit & Clear History ---
                ExitOptionRow(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.exit_quit_clear_history),
                    subtitle = stringResource(R.string.exit_quit_clear_history_desc),
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    titleColor = MaterialTheme.colorScheme.primary,
                    isDark = viewModel.isDarkThemeEnabled,
                    onClick = {
                        showExitSheet = false
                        coroutineScope.launch {
                            viewModel.clearAllHistory()
                            onExitBrowser()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Option 3: Quit & Burn All ---
                ExitOptionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = stringResource(R.string.exit_quit_burn_all),
                    subtitle = stringResource(R.string.exit_quit_burn_all_desc),
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    titleColor = MaterialTheme.colorScheme.error,
                    isDark = viewModel.isDarkThemeEnabled,
                    onClick = {
                        showExitSheet = false
                        coroutineScope.launch {
                            val runtime = viewModel.getGeckoRuntime(context)
                            FireButton(runtime, context).burn()
                            viewModel.burnAllData(context)
                            onExitBrowser()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Cancel ---
                OutlinedButton(
                    onClick = { showExitSheet = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.exit_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
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
        // ── <select> Choice Prompt Dialog (Issue #74) ──────────────────────────
        // Rendered at TOP LEVEL (outside Scaffold) so it's never gated by
        // conditional content branches like isHome/activeTab checks.
        if (showChoiceDialog) {
            viewModel.pendingChoicePrompt?.let { pending ->
                val choices = pending.prompt.choices ?: emptyArray()
                if (choices.isNotEmpty()) {
                    ChoicePromptDialog(
                        title = pending.prompt.message ?: "Select an option",
                        choices = choices.toList(),
                        initialSelectedIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0),
                        isMultiple = pending.prompt.type == org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE,
                        onConfirm = { selectedIndex ->
                            viewModel.deliverChoicePromptResult(selectedIndex)
                        },
                        onDismiss = {
                            viewModel.cancelChoicePrompt()
                        },
                        isDarkTheme = viewModel.isDarkThemeEnabled
                    )
                }
            }
        }

        // The Media Sniffer banner lives inside Scaffold's topBar/bottomBar slots and
        // toggles these dialog/sheet triggers. Reading them here, in BrowserScreen's own
        // composition scope, forces a recomposition so the content slot and root dialogs
        // re-execute and show them reliably.
        val _observeDialogTriggers = viewModel.showMediaSnifferSettingsDialog ||
            showDownloadSheet || showVideoOverviewDialog || (viewModel.pendingGenericDownload != null)
        if (_observeDialogTriggers) { /* observation only */ }

        Scaffold(
        topBar = {
        if (!viewModel.isFullscreen && !showHomeScreen &&
                ((viewModel.chromeNavBarEnabled && viewModel.addressBarPosition != "Bottom") ||
                (!viewModel.chromeNavBarEnabled && !(viewModel.addressBarPosition == "Bottom" && !isTablet)))) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val statusBarHeightDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
            val topBarMeasuredDp = if (measuredTopBarHeightPx > 0) with(density) { measuredTopBarHeightPx.toDp() } else if (isTablet) 113.dp else (config.searchBoxHeight + (config.paddingVertical * 2))
            val topBarTotalHeight = topBarMeasuredDp + statusBarHeightDp

            // Top bar: always rendered; graphicsLayer slides it out without removing from composition
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // 1. Address bar Surface (slides out on scroll)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarHeightDp)
                            .graphicsLayer { translationY = -topBarTotalHeight.toPx() * topBarFraction },
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        color = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                if (!showMenu && coords.size.height > 0 && (measuredTopBarHeightPx == 0 || kotlin.math.abs(coords.size.height - measuredTopBarHeightPx) > 4)) {
                                    measuredTopBarHeightPx = coords.size.height
                                }
                            }
                        ) {
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
                                                    text = if (tab.title.isNullOrBlank()) stringResource(R.string.new_tab_title) else tab.title,
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
                                                            contentDescription = stringResource(R.string.close_tab_desc),
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
                                            contentDescription = stringResource(R.string.menu_new_tab),
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
                                                    val layoutTextLength = textLayoutResult.layoutInput.text.length
                                                    if (cursorStart >= 0 && cursorStart <= layoutTextLength) {
                                                        try {
                                                            val cursorRect = textLayoutResult.getCursorRect(cursorStart)
                                                            coroutineScope.launch {
                                                                bringIntoViewRequester.bringIntoView(cursorRect)
                                                            }
                                                        } catch (e: Throwable) {
                                                            // Safely ignore transient layout bounds mismatch
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
                                                contentDescription = stringResource(R.string.ext_menu_cd),
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
                                        onClick = { showQuickToolsSheet = true },
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

                                    }
                                }
                            } else {
                                // Phone Top Bar — show address bar here when position is "Top",
                                // or All-in-One is enabled AND position is NOT explicitly "Bottom"
                                if (viewModel.addressBarPosition == "Top" || viewModel.addressBarPosition == "Split" ||
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
                                        onShowToolsSheet = { showQuickToolsSheet = true },
                                        showMenu = showMenu,
                                        onShowMenuChange = { showMenu = it },
                                        onOpenHistory = onOpenHistory,
                                        onOpenDownloads = onOpenDownloads,
                                        onOpenBookmarks = onOpenBookmarks,
                                        onOpenSettings = onOpenSettings,
                                        onOpenPasswordManager = onOpenPasswordManager,
                                        onShowThemeSheet = { showThemeSheet = true },
                                        onShowQuickTools = { showQuickToolsSheet = true },
                                        onShowFeedbackDialog = { showFeedbackDialog = true },
                                        onShowCustomizationSheet = { showCustomizationSheet = true },
                                        onShowPlayerSettings = { showPlayerSettingsDialog = true },
                                        onShowTabGroups = { showTabGroupsSheet = true },
                                        onShowSiteInfo = { showSiteInfoSheet = true },
                                        onShowAllInOneMenuSheet = { showAllInOneMenuSheet = true },
                            onOpenMediaSheet = { showDownloadSheet = true },
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = viewModel.isLoading && viewModel.addressBarPosition != "Bottom",
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
                        }
                    }

                    // 2. Media Sniffer Banner (slides up to top below status bar and stays pinned on scroll!)
                    AnimatedVisibility(
                        visible = showAlohaBanner && viewModel.addressBarPosition != "Bottom",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationY = -topBarMeasuredDp.toPx() * topBarFraction },
                            color = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            shadowElevation = 4.dp
                        ) {
                            MediaSnifferBanner(
                                viewModel = viewModel,
                                nonDrmMedia = nonDrmMedia,
                                onDismiss = { isAlohaBannerDismissed = true },
                                onPlay = { url -> onPlayOnlineStream(url, viewModel.currentUrl) },
                                onDownloadClick = { showDownloadSheet = true },
                                onOpenSettings = { viewModel.showMediaSnifferSettingsDialog = true }
                            )
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
                // --- Bottom Group Strip ---
                if (!showHomeScreen && activeTab != null) {
                    val activeGroup = viewModel.tabGroups.find { it.tabIds.contains(activeTab.id) }
                    if (activeGroup != null) {
                        val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                        val groupTabs = currentModeTabs.filter { it.id in activeGroup.tabIds }
                        if (groupTabs.size > 1) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(activeGroup.color)))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    androidx.compose.foundation.lazy.LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(groupTabs.size, key = { "strip_${groupTabs[it].id}" }) { index ->
                                            val tab = groupTabs[index]
                                            val isTabActive = tab.id == viewModel.activeTabId
                                            val groupColor = Color(activeGroup.color)
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isTabActive) groupColor.copy(alpha=0.2f) else Color.Transparent)
                                                    .clickable { viewModel.selectTab(tab.id) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                                    coil.compose.AsyncImage(
                                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                            .data("https://www.google.com/s2/favicons?domain=${tab.url}&sz=64")
                                                            .crossfade(true).build(),
                                                        contentDescription = null, modifier = Modifier.size(20.dp)
                                                    )
                                                } else {
                                                    Icon(Icons.Rounded.Language, null, tint = if (isTabActive) groupColor else (if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    // Add to group button
                                    val context = LocalContext.current
                                    IconButton(onClick = { viewModel.createNewTab(context, "about:blank", activeGroup.id) }) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Add to group", tint = Color(activeGroup.color))
                                    }
                                }
                            }
                        }
                    }
                }
                if ((!viewModel.chromeNavBarEnabled || viewModel.addressBarPosition == "Bottom") && viewModel.addressBarPosition != "Top" && viewModel.addressBarPosition != "Split" && !showHomeScreen && !viewModel.isFullscreen) {
                    val isBottomNavBarVisible = !viewModel.chromeNavBarEnabled && viewModel.showBottomNavBar && !viewModel.isFullscreen && !isInputFocused && !isHomeSearchFocused
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run {
                                if (isBottomNavBarVisible) this else navigationBarsPadding()
                            }
                            // When the URL field is focused, the soft keyboard opens.
                            // The window automatically resizes to accommodate the IME,
                            // which already lifts the Scaffold's bottom bar.
                            // Applying Modifier.imePadding() here would add the IME
                            // inset twice, pushing the edit bar to the top of the screen.
                            // Thus, we apply no additional IME padding.
                            ,
                        color = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        shadowElevation = 12.dp,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = viewModel.isLoading && !showHomeScreen && viewModel.addressBarPosition == "Bottom",
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                LinearProgressIndicator(
                                    progress = { viewModel.loadingProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Square
                                )
                            }
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
                            onShowToolsSheet = { showQuickToolsSheet = true },
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onOpenHistory = onOpenHistory,
                            onOpenDownloads = onOpenDownloads,
                            onOpenBookmarks = onOpenBookmarks,
                            onOpenSettings = onOpenSettings,
                            onOpenPasswordManager = onOpenPasswordManager,
                            onShowThemeSheet = { showThemeSheet = true },
                            onShowQuickTools = { showQuickToolsSheet = true },
                            onShowFeedbackDialog = { showFeedbackDialog = true },
                            onShowCustomizationSheet = { showCustomizationSheet = true },
                            onShowPlayerSettings = { showPlayerSettingsDialog = true },
                            onShowTabGroups = { showTabGroupsSheet = true },
                            onShowSiteInfo = { showSiteInfoSheet = true },
                            onShowAllInOneMenuSheet = { showAllInOneMenuSheet = true },
                            onOpenMediaSheet = { showDownloadSheet = true }
                        )
                    }
                    }
                }

                if ((!viewModel.chromeNavBarEnabled || showHomeScreen) && viewModel.showBottomNavBar && !(showHomeScreen && viewModel.hideHomeBottomNav) && !viewModel.isFullscreen && !isInputFocused && !isHomeSearchFocused) {
                    // Flat minimal bottom bar: transparent and seamlessly blended on Home Screen, contoured on Webpages
                val isDark = viewModel.isDarkThemeEnabled
                val navBg = if (showHomeScreen) Color.Transparent else if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                val navBorder = if (viewModel.isAmoledMode) Color(0xFF1A1A1A) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val navContent = if (isDark) Color.White else Color(0xFF1C1C1E)
                val navContentMuted = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF8E8E93)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .drawBehind {
                            if (!showHomeScreen) {
                                drawLine(
                                    color = navBorder,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        },
                    color = navBg
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = viewModel.isLoading && !showHomeScreen && viewModel.addressBarPosition == "Bottom",
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LinearProgressIndicator(
                                progress = { viewModel.loadingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Square
                            )
                        }
                        if (showHomeScreen) {
                        // 4-Button Home Navigation Bar: Palette, News Center, Quick Tools, Menu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((52 * viewModel.bottomNavScale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // 1. Palette Icon (Home Customization Sheet)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { showCustomizationSheet = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = stringResource(R.string.customize_home_cd),
                                        tint = navContent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // 2. News Center Icon (News Feed Screen)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { onOpenNewsCenter() },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Article,
                                        contentDescription = "News Center",
                                        tint = navContent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // 3. Quick Tools Icon (Quick Tools Sheet)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { showQuickToolsSheet = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = "Quick Tools",
                                        tint = navContent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // 4. Menu Icon (Options / Tools Sheet)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { showAllInOneMenuSheet = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = "Menu",
                                        tint = navContent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Webpage Navigation Bar (Back, Forward, Tools, Tabs, Menu)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((52 * viewModel.bottomNavScale).dp)
                                .pointerInput(viewModel.activeTabId, viewModel.isIncognitoMode) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (dragAmountAccumulated > 100f) {
                                                val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                                val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                                if (currentIndex > 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.selectTab(currentModeTabs[currentIndex - 1].id)
                                                }
                                            } else if (dragAmountAccumulated < -100f) {
                                                val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                                val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                                if (currentIndex != -1 && currentIndex < currentModeTabs.size - 1) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            // Back
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                            // Forward
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                            // Tools
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { showQuickToolsSheet = true },
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
                            // Tabs
                            Box(
                                modifier = Modifier.weight(1f).clickable { showTabGroupsSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(config.innerIconSize + 4.dp)
                                        .border(1.5.dp, navContent, RoundedCornerShape(5.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                                        color = navContent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // Menu
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { showAllInOneMenuSheet = true },
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
                .then(if (!viewModel.isFullscreen && !isLandscape) Modifier.navigationBarsPadding() else Modifier)
                .clip(androidx.compose.ui.graphics.RectangleShape)
                .background(if (viewModel.isFullscreen || isLandscape) Color.Black else MaterialTheme.colorScheme.background)
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
                        onDownloadClick = { showDownloadSheet = true },
                        onOpenSettings = { viewModel.showMediaSnifferSettingsDialog = true }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (needsStatusBarPadding && !viewModel.isFullscreen && !isLandscape) Modifier.statusBarsPadding() else Modifier)
                ) {
                    AnimatedContent(
                        targetState = Pair(viewModel.activeTabId, showHomeScreen),
                        transitionSpec = {
                            val (prevTabId, prevHome) = initialState
                            val (newTabId, newHome) = targetState

                            if (prevTabId != newTabId && newHome) {
                                // New tab creation — Safari zoom & spring enter
                                (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                                 scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f))) togetherWith
                                (fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
                                 scaleOut(targetScale = 0.95f))
                            } else {
                                // Tab switching — Safari immersive card transition
                                (fadeIn(animationSpec = tween(200)) +
                                 scaleIn(initialScale = 0.93f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f))) togetherWith
                                (fadeOut(animationSpec = tween(160)) +
                                 scaleOut(targetScale = 0.97f))
                            }
                        },
                        label = "SafariTabContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { (targetTabId, isHome) ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (activeTab != null && !isHome) {
                                val bottomNavBarHeight = remember(viewModel.addressBarPosition, viewModel.chromeNavBarEnabled, viewModel.showBottomNavBar, viewModel.bottomNavScale, viewModel.uiScale) {
                                    if (!isTablet && !isHome && !viewModel.isFullscreen && !isLandscape) {
                                        if (viewModel.addressBarPosition == "Bottom") {
                                            val searchHeight = config.searchBoxHeight + (config.paddingVertical * 2)
                                            if (viewModel.chromeNavBarEnabled) {
                                                searchHeight
                                            } else if (viewModel.showBottomNavBar) {
                                                searchHeight + (52 * viewModel.bottomNavScale).dp
                                            } else {
                                                searchHeight
                                            }
                                        } else if (viewModel.showBottomNavBar) {
                                            (52 * viewModel.bottomNavScale).dp
                                        } else {
                                            0.dp
                                        }
                                    } else {
                                        0.dp
                                    }
                                }

                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val statusBarHeightPx = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
                                val statusBarHeightDp = with(density) { statusBarHeightPx.toDp() }
                                
                                val hasTopBar = !(viewModel.addressBarPosition == "Bottom" && !isTablet)
                                val topBarMeasuredDp = if (measuredTopBarHeightPx > 0) with(density) { measuredTopBarHeightPx.toDp() } else if (isTablet) 113.dp else (config.searchBoxHeight + (config.paddingVertical * 2))
                                val topBarTotalHeight = topBarMeasuredDp + statusBarHeightDp

                                // Banner is inside Scaffold's topBar but measured separately; add its height
                                // so GeckoView offset is correct when the banner is visible.
                                val bannerHeight = if (showAlohaBanner && viewModel.addressBarPosition != "Bottom") 48.dp else 0.dp

                                val totalTopPad = if (hasTopBar && !viewModel.isFullscreen && !isLandscape && !(isKeyboardVisible && !isInputFocused && !isEditMode)) topBarTotalHeight + bannerHeight else 0.dp
                                val geckoTopOffset = if (hasTopBar && !viewModel.isFullscreen && !isLandscape && !(isKeyboardVisible && !isInputFocused && !isEditMode)) {
                                    (statusBarHeightDp + bannerHeight) + (topBarMeasuredDp * (1f - topBarFraction))
                                } else 0.dp
                                val geckoBottomPad = if (!viewModel.isFullscreen && !isLandscape) bottomNavBarHeight * (1f - bottomBarFraction) else 0.dp
                                
                                val geckoTopPad = 0.dp
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            object : androidx.compose.ui.layout.LayoutModifier {
                                                override fun androidx.compose.ui.layout.MeasureScope.measure(
                                                    measurable: androidx.compose.ui.layout.Measurable,
                                                    constraints: androidx.compose.ui.unit.Constraints
                                                ): androidx.compose.ui.layout.MeasureResult {
                                                    val extraHeight = totalTopPad.roundToPx()
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
                                        .offset(y = geckoTopOffset)
                                        .padding(top = geckoTopPad, bottom = geckoBottomPad)
                                ) {
                                    DisposableEffect(Unit) {
                                        onDispose {
                                            // When the GeckoView leaves composition, cancel any
                                            // pending prompts so their GeckoResult is not left hanging.
                                            viewModel.cancelAllPendingPrompts()
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
                                                private var isFastScrolling = false
                                                private val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

                                                override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                                                     val scrollY = currentScrollPos
                                                     val isPillEnabled = viewModel.showScrollButtons && !showHomeScreen && !viewModel.isFullscreen
                                                     val density = ctx.resources.displayMetrics.density
                                                     val stripPx = 48f * density
                                                     val minThumbPx = 36f * density
                                                     val maxThumbPx = 90f * density
                                                     val hitboxTolPx = 20f * density
                                                     val minHitboxHPx = 64f * density

                                                     val geometry = FastScrollMath.computeGeometry(
                                                         viewportWidth = width.toFloat(),
                                                         viewportHeight = height.toFloat(),
                                                         topTrackOffset = viewModel.fastScrollPillTrackTop,
                                                         bottomTrackOffset = viewModel.fastScrollPillTrackBottom,
                                                         pageScrollHeight = viewModel.pageScrollHeight,
                                                         pageViewportHeight = viewModel.pageViewportHeight,
                                                         scrollRange = viewModel.currentScrollRange,
                                                         scrollExtent = viewModel.currentScrollExtent,
                                                         currentScrollOffset = maxOf(scrollY, viewModel.currentScrollOffset).toFloat(),
                                                         isDragging = isFastScrolling,
                                                         dragFraction = viewModel.fastScrollPillFraction,
                                                         minThumbPx = minThumbPx,
                                                         maxThumbPx = maxThumbPx,
                                                         hitboxWidthPx = stripPx,
                                                         hitboxTolerancePx = hitboxTolPx,
                                                         minHitboxHeightPx = minHitboxHPx
                                                     )

                                                     when (ev.actionMasked) {
                                                         android.view.MotionEvent.ACTION_DOWN -> {
                                                             if (viewModel.isFullscreen) {
                                                                 showFullscreenDownloadBtn = true
                                                                 fullscreenControlsLastActivityMs = System.currentTimeMillis()
                                                             }
                                                             startY = ev.y
                                                             isPulling = false
                                                             if (isPillEnabled && geometry.isScrollable && FastScrollMath.isTouchInsideHitbox(ev.x, ev.y, geometry)) {
                                                                 isFastScrolling = true
                                                                 viewModel.isFastScrollingPill = true
                                                                 viewModel.scrollPillState = ScrollPillState.DRAGGING
                                                                 viewModel.fastScrollController.attachSession(activeTab.session)
                                                                 try {
                                                                     performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                                 } catch (_: Exception) {}

                                                                 val frac = FastScrollMath.computeDragFraction(
                                                                     fingerY = ev.y,
                                                                     topTrackOffset = viewModel.fastScrollPillTrackTop,
                                                                     thumbHeight = geometry.thumbHeight,
                                                                     maxThumbTravel = geometry.maxThumbTravel
                                                                 )
                                                                 viewModel.fastScrollPillFraction = frac
                                                                 viewModel.fastScrollController.dispatchDragFraction(frac, geometry.maxDocumentScroll)
                                                                 return true
                                                             }
                                                         }
                                                         android.view.MotionEvent.ACTION_MOVE -> {
                                                             if (isFastScrolling) {
                                                                 val frac = FastScrollMath.computeDragFraction(
                                                                     fingerY = ev.y,
                                                                     topTrackOffset = viewModel.fastScrollPillTrackTop,
                                                                     thumbHeight = geometry.thumbHeight,
                                                                     maxThumbTravel = geometry.maxThumbTravel
                                                                 )
                                                                 viewModel.fastScrollPillFraction = frac
                                                                 viewModel.fastScrollController.dispatchDragFraction(frac, geometry.maxDocumentScroll)
                                                                 return true
                                                             }
                                                             val deltaY = ev.y - startY
                                                             if (scrollY <= 0 && deltaY > touchSlop && !isPulling && !viewModel.isLoading && !viewModel.isFullscreen) {
                                                                 // Only allow pull-to-refresh if the swipe started near the top edge
                                                                 // of the screen. This prevents accidental refreshes when scrolling up
                                                                 // inside internal scrollable elements on websites like MangaPlus.
                                                                 val edgeThreshold = 120f * density
                                                                 if (startY < edgeThreshold) {
                                                                     isPulling = true
                                                                 }
                                                             }
                                                             if (isPulling) {
                                                                 val pullDistance = (deltaY - touchSlop).coerceAtLeast(0f)
                                                                 viewModel.pullToRefreshOffset = pullDistance
                                                                 return true
                                                             }
                                                         }
                                                         android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                                             if (isFastScrolling) {
                                                                 isFastScrolling = false
                                                                 viewModel.isFastScrollingPill = false
                                                                 viewModel.scrollPillState = ScrollPillState.VISIBLE_IDLE
                                                                 return true
                                                             }
                                                             if (isPulling) {
                                                                 isPulling = false
                                                                 viewModel.onPullRelease(thresholdPx)
                                                                 return true
                                                             }
                                                         }
                                                     }
                                                     return super.dispatchTouchEvent(ev)
                                                }

                                                override fun computeVerticalScrollRange(): Int {
                                                    val r = super.computeVerticalScrollRange()
                                                    viewModel.currentScrollRange = r
                                                    return r
                                                }

                                                override fun computeVerticalScrollExtent(): Int {
                                                    val e = super.computeVerticalScrollExtent()
                                                    viewModel.currentScrollExtent = e
                                                    return e
                                                }

                                                override fun computeVerticalScrollOffset(): Int {
                                                    val o = super.computeVerticalScrollOffset()
                                                    viewModel.currentScrollOffset = o
                                                    return o
                                                }

                                                // Expose protected compute methods via a lambda stored on the ViewModel.
                                                // This lambda captures `this` (the GeckoView subclass instance),
                                                // so it can call protected methods from within the View's scope.
                                                fun setupScrollMetricsRefresher() {
                                                    viewModel.refreshScrollMetrics = {
                                                        viewModel.currentScrollOffset = computeVerticalScrollOffset()
                                                        viewModel.currentScrollRange = computeVerticalScrollRange()
                                                        viewModel.currentScrollExtent = computeVerticalScrollExtent()
                                                    }
                                                }
                                            }.apply {
                                                setupScrollMetricsRefresher()
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                                
                                                setAutofillEnabled(true)
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
                                                }
                                                
                                                val runtime = viewModel.getGeckoRuntime(ctx)
                                                try {
                                                    if (!activeTab.session.isOpen) {
                                                        activeTab.session.open(runtime)
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.w("BrowserScreen", "Failed to open session in factory, resuming tab ${activeTab.id}", e)
                                                    viewModel.resumeTab(activeTab.id, ctx)
                                                }
                                                val currentSession = viewModel.tabs.find { it.id == activeTab.id }?.session ?: activeTab.session
                                                try {
                                                    setSession(currentSession)
                                                    currentSession.setActive(true)
                                                } catch (e: Exception) {
                                                    android.util.Log.w("BrowserScreen", "Failed to setSession in factory", e)
                                                }
                                                viewModel.setActiveGeckoView(this)
                                            }
                                        },
                                        update = { geckoView ->
                                            geckoView.setAutofillEnabled(true)
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                geckoView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
                                            }
                                            val runtime = viewModel.getGeckoRuntime(geckoView.context)
                                            try {
                                                if (!activeTab.session.isOpen) {
                                                    activeTab.session.open(runtime)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.w("BrowserScreen", "Failed to open session in update, resuming tab ${activeTab.id}", e)
                                                viewModel.resumeTab(activeTab.id, geckoView.context)
                                            }
                                            val currentSession = viewModel.tabs.find { it.id == activeTab.id }?.session ?: activeTab.session
                                            if (geckoView.session != currentSession) {
                                                try {
                                                    geckoView.setSession(currentSession)
                                                } catch (e: Exception) {
                                                    android.util.Log.w("BrowserScreen", "Failed to setSession in update", e)
                                                }
                                            }
                                            try {
                                                currentSession.setActive(true)
                                            } catch (_: Exception) {}
                                            viewModel.setActiveGeckoView(geckoView)
                                        },
                                        onRelease = { geckoView ->
                                            try {
                                                geckoView.releaseSession()
                                            } catch (_: Exception) {}
                                            viewModel.clearActiveGeckoView(geckoView)
                                        }
                                    )

                                    // Session recovery overlay — never show a blank screen.
                                    // While the active tab is being recovered (onKill / process death),
                                    // show "Restoring your page…" with the last-known title as a hint.
                                    if (viewModel.isRecoveringActiveTab) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 3.dp
                                                )
                                                Text(
                                                    text = "Restoring your page…",
                                                    color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (activeTab.title.isNotBlank() && activeTab.title != "New Tab") {
                                                    Text(
                                                        text = activeTab.title,
                                                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 32.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (viewModel.lastRecoveryFailed && !viewModel.isRecoveringActiveTab) {
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
                                                        contentDescription = "Recovery failed",
                                                        tint = Color(0xFFFF4444),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = "We couldn't restore this page",
                                                        color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = activeTab.url,
                                                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 14.sp,
                                                        maxLines = 2,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                viewModel.lastRecoveryFailed = false
                                                                viewModel.reload()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                            shape = RoundedCornerShape(20.dp)
                                                        ) {
                                                            Text(
                                                                text = "Reload",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        OutlinedButton(
                                                            onClick = { viewModel.lastRecoveryFailed = false },
                                                            shape = RoundedCornerShape(20.dp)
                                                        ) {
                                                            Text(
                                                                text = "Dismiss",
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

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
                                                    val mirrorFallback = remember(viewModel.currentUrl) {
                                                        viewModel.getTorrentMirrorFallback(viewModel.currentUrl)
                                                    }

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
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
                                                        if (mirrorFallback != null) {
                                                            OutlinedButton(
                                                                onClick = { viewModel.loadUrl(mirrorFallback) },
                                                                shape = RoundedCornerShape(20.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Try Working Mirror",
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                                    DisposableEffect(activeTab.id, lifecycleOwner) {
                                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                            val currentSession = viewModel.tabs.find { it.id == activeTab.id }?.session
                                            when (event) {
                                                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                                    // Re-bind GeckoView surface to session after Android surface
                                                    // recreation (e.g. returning from another app / app drawer).
                                                    // If we skip setSession here, the surface stays blank because
                                                    // Gecko's compositor lost its native window reference.
                                                    currentSession?.let { session ->
                                                        val geckoView = viewModel.activeGeckoViewRef?.get()
                                                        if (geckoView != null && geckoView.session != session) {
                                                            try {
                                                                geckoView.setSession(session)
                                                            } catch (_: Exception) {}
                                                        }
                                                        try {
                                                            session.setActive(true)
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                                    // Do NOT call setActive(false) here.
                                                    // Android OS automatically pauses the GeckoView SurfaceView
                                                    // when the Activity pauses. Manually calling setActive(false)
                                                    // detaches Gecko's compositor from the native window so that
                                                    // when the user returns, GeckoView has no surface to draw on
                                                    // and the screen is completely blank.
                                                }
                                                else -> {}
                                            }
                                        }
                                        lifecycleOwner.lifecycle.addObserver(observer)
                                        onDispose {
                                            lifecycleOwner.lifecycle.removeObserver(observer)
                                        }
                                    }
                                    
                                    val pullOffset = viewModel.pullToRefreshOffset
                                    val isRefreshing = viewModel.isLoading
                                    val showIndicator = isRefreshing && !viewModel.hideRefreshIndicator

                                    if (pullOffset > 0f || showIndicator) {
                                        val pullOffsetDp = with(density) { (pullOffset * 0.4f).toDp() }
                                        val thresholdDp = 80.dp
                                        val progress = (pullOffset * 0.4f) / with(density) { thresholdDp.toPx() }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopCenter)
                                                .padding(top = 16.dp)
                                                .offset(y = if (showIndicator) 40.dp else pullOffsetDp.coerceAtMost(120.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = CircleShape,
                                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color.White,
                                                shadowElevation = 6.dp
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (showIndicator) {
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
                                }
                                RainbowScanBorder(isScanning = viewModel.isQrScanning)
                            }

                            if (isConfig) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val hasTopBar = !(viewModel.addressBarPosition == "Bottom" && !isTablet)
                                val topBarHeightDp = (if (measuredTopBarHeightPx > 0) with(density) { measuredTopBarHeightPx.toDp() } else if (isTablet) 113.dp else (config.searchBoxHeight + (config.paddingVertical * 2))) + 8.dp
                                val configTopPad = if (hasTopBar && !viewModel.isFullscreen) topBarHeightDp else 0.dp
                                val configBottomPad = if (!hasTopBar && !viewModel.isFullscreen) (config.searchBoxHeight + (config.paddingVertical * 2) + config.bottomNavBarHeight + 16.dp) else 16.dp

                                OmniConfigContent(
                                    viewModel = viewModel,
                                    topPadding = configTopPad,
                                    bottomPadding = configBottomPad
                                )
                            } else if (isHome) {
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
                                        viewModel.saveExtensionsOverviewSeen(context, true)
                                        showExtensionsSheet = true
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
                                    onOpenPasswordManager = onOpenPasswordManager,
                                    onShowThemeSheet = { showThemeSheet = true },
                                    onShowQuickTools = { showQuickToolsSheet = true },
                                    onShowFeedbackDialog = { showFeedbackDialog = true },
                                    showCustomizationSheet = showCustomizationSheet,
                                    onShowCustomizationSheetChange = { showCustomizationSheet = it },
                                    onShowTabGroups = { showTabGroupsSheet = true },
                                    onOpenWallpapers = onOpenWallpapers,
                                    onShowPlayerSettings = { showPlayerSettingsDialog = true }
                                )
                            }
                        }
                    }
                }
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
                                    text = stringResource(R.string.autoscroll_speed, autoScrollSpeed),
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
                
                // Vertical Context Menu Popup for Text Selection (Appears at the end of selected text)
                val activeTextSelection = viewModel.activeTextSelection
                val selectionRect = viewModel.selectionScreenRect
                if (activeTextSelection != null && activeTab != null && !showHomeScreen) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val screenWidthPx = context.resources.displayMetrics.widthPixels
                    val screenHeightPx = context.resources.displayMetrics.heightPixels
                    val popupWidthPx = with(density) { 170.dp.toPx() }.toInt()
                    val popupHeightPx = with(density) { 190.dp.toPx() }.toInt()

                    val targetX = if (selectionRect != null) {
                        selectionRect.right.toInt().coerceIn(16, (screenWidthPx - popupWidthPx - 16).coerceAtLeast(16))
                    } else {
                        (screenWidthPx - popupWidthPx) / 2
                    }

                    val targetY = if (selectionRect != null) {
                        if (selectionRect.bottom + popupHeightPx > screenHeightPx - 120) {
                            (selectionRect.top.toInt() - popupHeightPx - 12).coerceAtLeast(40)
                        } else {
                            (selectionRect.bottom.toInt() + 12).coerceAtLeast(40)
                        }
                    } else {
                        (screenHeightPx - popupHeightPx) / 2
                    }

                    androidx.compose.ui.window.Popup(
                        offset = androidx.compose.ui.unit.IntOffset(targetX, targetY),
                        onDismissRequest = { viewModel.dismissTextSelection() }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF1E293B) else Color.White,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF334155) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                // Copy
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.copySelectedText(context) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1E293B)
                                    )
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0))

                                // Share
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val textToShare = activeTextSelection ?: ""
                                            if (textToShare.isNotBlank()) {
                                                try {
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                                    }
                                                    val chooser = android.content.Intent.createChooser(shareIntent, "Share Text").apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(chooser)
                                                } catch (e: Exception) {
                                                    android.util.Log.e("OmniBrowser", "Error sharing text", e)
                                                }
                                            }
                                            viewModel.dismissTextSelection()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Share",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1E293B)
                                    )
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0))

                                // Read Aloud
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val textToRead = activeTextSelection ?: ""
                                            if (textToRead.isNotBlank()) {
                                                viewModel.initTts(context)
                                                viewModel.speakText(textToRead)
                                            }
                                            viewModel.dismissTextSelection()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = "Read Aloud",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Read Aloud",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1E293B)
                                    )
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0))

                                // Search Google
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val query = activeTextSelection?.trim().orEmpty()
                                            if (query.isNotEmpty()) {
                                                val searchUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                                                viewModel.loadUrl(searchUrl)
                                            }
                                            viewModel.dismissTextSelection()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Search Google",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1E293B)
                                    )
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0))

                                // Select All
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectAllText() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SelectAll,
                                        contentDescription = "Select All",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Select All",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    }
                }
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



            // ─── Fullscreen Center-Top Action Buttons ──────────────────────────────
            val fullscreenMedia = if (playableMedia.isNotEmpty()) playableMedia else nonDrmMedia
            val isYouTubePage = viewModel.currentUrl.lowercase().contains("youtube.com") || viewModel.currentUrl.lowercase().contains("youtu.be")
            if (fullscreenMedia.isNotEmpty() && !showHomeScreen && !viewModel.isReaderModeActive && !isYouTubePage && viewModel.isNativePlayerEnabled && viewModel.isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFullscreenDownloadBtn,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val firstMedia = fullscreenMedia.firstOrNull()
                                if (firstMedia != null) {
                                    FilledIconButton(
                                        onClick = {
                                            viewModel.playMedia(firstMedia.toPlaybackRequest())
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(R.string.browser_play_premium),
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                FilledIconButton(
                                    onClick = {
                                        if (!viewModel.hasSeenVideoOverview) {
                                            pendingVideoAction = { showDownloadSheet = true }
                                            showVideoOverviewDialog = true
                                        } else {
                                            showDownloadSheet = true
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.22f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Safari-style Context Menu Bottom Sheet
            if (viewModel.activeContextMenu != null) {
                SafariContextMenuSheet(viewModel = viewModel, context = context)
            }

            // Generic file download destination dialog (Root level)
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
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = false, context) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.download_destination_local), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = true, context) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(32.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.download_destination_vault), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        if (viewModel.isExternalDownloadManagerEnabled) {
                            OutlinedButton(
                                onClick = {
                                    val current = pending
                                    viewModel.pendingGenericDownload = null
                                    viewModel.handOffToExternalDownloadManager(
                                        context = context,
                                        url = current.url,
                                        filename = current.filename,
                                        contentType = current.contentType
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(32.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.external_download_manager_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        TextButton(
                            onClick = { viewModel.pendingGenericDownload = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cancel_text), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
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
                            text = stringResource(R.string.media_detected_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (nonDrmMedia.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.media_detected_count, nonDrmMedia.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(nonDrmMedia, key = { it.url + "|" + it.type.name }) { item ->
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
                                                    text = stringResource(R.string.download_quality_label, when (item.quality) {
                                                        "Source HD" -> stringResource(R.string.download_quality_source_hd)
                                                        "Auto / Source" -> stringResource(R.string.download_quality_auto_source)
                                                        "Unknown Quality" -> stringResource(R.string.download_quality_unknown)
                                                        null -> stringResource(R.string.download_quality_auto_source_fallback)
                                                        else -> item.quality
                                                    }),
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
                                                    viewModel.playMedia(item.toPlaybackRequest())
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.play_text), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

                                                        if (viewModel.isExternalDownloadManagerEnabled && viewModel.canHandOffMedia(item.type, audioUrl)) {
                                                            viewModel.handOffToExternalDownloadManager(
                                                                context = context,
                                                                url = item.url,
                                                                filename = suggestedName,
                                                                contentType = when (item.type) {
                                                                    com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO -> "audio/*"
                                                                    com.rebelroot.omni.media.MediaInterceptor.MediaType.WEBM -> "video/webm"
                                                                    else -> "video/mp4"
                                                                }
                                                            )
                                                            Toast.makeText(context, context.getString(R.string.download_toast_external), Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            if (viewModel.isExternalDownloadManagerEnabled) {
                                                                Toast.makeText(context, context.getString(R.string.download_toast_no_handoff), Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, context.getString(R.string.download_toast_started), Toast.LENGTH_SHORT).show()
                                                            }
                                                            viewModel.streamDownloadEngine.startDownload(
                                                                url = item.url,
                                                                suggestedName = suggestedName,
                                                                type = item.type,
                                                                saveToLocker = false,
                                                                referrerUrl = viewModel.currentUrl,
                                                                cookies = viewModel.activeVideoCookies,
                                                                audioUrl = audioUrl
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.save_text), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                                        Toast.makeText(context, context.getString(R.string.download_toast_locker), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.locker_text), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        // For YouTube/googlevideo, find the audio-only stream
                                                        val isYouTubeUrl = item.url.contains("googlevideo.com")
                                                        val mp3Url = if (isYouTubeUrl) {
                                                            nonDrmMedia.find {
                                                                it.url.contains("googlevideo.com") &&
                                                                (it.url.contains("mime=audio") || it.url.contains("mime=audio%2F"))
                                                            }?.url ?: item.url
                                                        } else {
                                                            item.url
                                                        }

                                                        val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
                                                        val rawTitle = activeTab?.title ?: "Audio"
                                                        val cleanTitle = if (rawTitle.isNotEmpty() && rawTitle != "Loading..." && rawTitle != "New Tab" && !rawTitle.startsWith("http")) {
                                                            rawTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
                                                        } else "Audio"
                                                        val suggestedName = "$cleanTitle-${System.currentTimeMillis()}"

                                                        if (viewModel.isExternalDownloadManagerEnabled && !isYouTubeUrl) {
                                                            viewModel.handOffToExternalDownloadManager(
                                                                context = context,
                                                                url = mp3Url,
                                                                filename = suggestedName,
                                                                contentType = "audio/mpeg"
                                                            )
                                                            Toast.makeText(context, context.getString(R.string.download_toast_external), Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            if (viewModel.isExternalDownloadManagerEnabled) {
                                                                Toast.makeText(context, context.getString(R.string.download_toast_mp3_no_handoff), Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, context.getString(R.string.download_toast_mp3_started), Toast.LENGTH_SHORT).show()
                                                            }
                                                            viewModel.streamDownloadEngine.startDownload(
                                                                url = mp3Url,
                                                                suggestedName = suggestedName,
                                                                type = com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO,
                                                                saveToLocker = false,
                                                                referrerUrl = viewModel.currentUrl,
                                                                cookies = viewModel.activeVideoCookies
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.download_sheet_mp3), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (nonDrmMedia.isEmpty()) {
                            val isChecking = detectedMedia.any {
                                it.validationStatus == com.rebelroot.omni.media.MediaInterceptor.ValidationStatus.PENDING
                            }
                            Text(
                                text = stringResource(if (isChecking) R.string.media_checking else R.string.media_none_found),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Autofill picker sheet: appears when user taps a login input field
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.pm_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )
                                    val host = try { java.net.URI(viewModel.currentUrl).host?.removePrefix("www.") ?: "" } catch(e: Exception) { "" }
                                    if (host.isNotEmpty()) {
                                        Text(
                                            text = host,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            // X button to dismiss — "I'll type manually"
                            IconButton(
                                onClick = { viewModel.showAutofillBottomSheet = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.autofill_type_manually),
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Credential list
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(viewModel.autofillMatches) { credential ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    onClick = { viewModel.autofillCredential(credential) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Filled indicator if this was the last used credential
                                            val isLastUsed = viewModel.autofillLastUsed?.id == credential.id
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isLastUsed) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.VpnKey,
                                                    contentDescription = null,
                                                    tint = if (isLastUsed) Color.White else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = credential.username,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isLastUsed) stringResource(R.string.autofill_currently_filled) else stringResource(R.string.autofill_tap_to_fill),
                                                    fontSize = 12.sp,
                                                    color = if (isLastUsed) MaterialTheme.colorScheme.primary else Color.Gray
                                                )
                                            }
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

                        // "Type manually" footer
                        TextButton(
                            onClick = { viewModel.showAutofillBottomSheet = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = stringResource(R.string.autofill_type_manually_btn),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
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
                val defaultLang = languages.find { it.second == viewModel.selectedLanguageCode } ?: ("English" to "en")
                LaunchedEffect(showTranslationDialog) {
                    selectedPageTargetLang = defaultLang
                    selectedTargetLang = defaultLang
                }

                AlertDialog(
                    onDismissRequest = { showTranslationDialog = false; viewModel.translationManager.close() },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.translator_dialog_title),
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
                                        text = stringResource(R.string.translator_page_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )
                                    
                                    if (canTranslatePage) {
                                        Text(
                                            text = stringResource(R.string.translator_page_desc),
                                            fontSize = 11.sp,
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color(0xFF64748B)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.translator_target_lang),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                            )
                                            LanguageDropdownSelector(
                                                label = stringResource(R.string.translator_target),
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
                                                        val translateParams = "_x_tr_sl=auto&_x_tr_tl=$targetLang&_x_tr_hl=$targetLang&_x_tr_pto=wapp"
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
                                            Text(stringResource(R.string.translator_translate_page), color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        // Omni on-device page translation (offline-aware).
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    val tab = activeTab
                                                    if (tab != null) {
                                                        val src = selectedSourceLang.second
                                                        viewModel.translatePage(
                                                            tabId = tab.id,
                                                            session = tab.session,
                                                            sourceLanguage = if (src.isBlank() || src == "auto") null else src,
                                                            targetLanguage = selectedPageTargetLang.second,
                                                            isPrivate = tab.isIncognito
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.translator_translate_device),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { activeTab?.let { viewModel.stopPageTranslation(it.id) } },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(stringResource(R.string.translator_show_original), fontSize = 13.sp)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = stringResource(R.string.translator_open_page),
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
                                        text = stringResource(R.string.translator_text_title),
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
                                            label = stringResource(R.string.translator_source),
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
                                            label = stringResource(R.string.translator_target),
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
                                        placeholder = { Text(stringResource(R.string.translator_placeholder), color = Color.Gray) },
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
                                                text = stringResource(R.string.translator_downloading),
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
                                                        translationResultText = context.getString(R.string.translator_failed, e.message ?: "")
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
                                        Text(stringResource(R.string.translator_translate_text), color = Color.White, fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.close_text), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            if (showSpoofIdentityDialog) {
                SpoofIdentityChooserDialog(
                    viewModel = viewModel,
                    onDismiss = { showSpoofIdentityDialog = false },
                    onOpenUserAgentSettings = onOpenUserAgentSettings
                )
            }

            if (showTorrentDownloaderDialog) {
                TorrentDownloaderDialog(
                    viewModel = viewModel,
                    onDismiss = { showTorrentDownloaderDialog = false }
                )
            }

            if (showSpeedDialSheet) {
                SpeedDialLauncherSheet(
                    viewModel = viewModel,
                    onDismissRequest = { showSpeedDialSheet = false },
                    onOpenUrl = { url ->
                        showSpeedDialSheet = false
                        viewModel.loadUrl(url)
                    }
                )
            }

            // 2. Premium Grid Tab Windows Switcher Tray Bottom Sheet
            if (showTabGroupsSheet) {
                var showOnlyGroups by remember { mutableStateOf(false) }
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
                            .padding(bottom = 4.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Bar: Segmented Control & Title (Firefox Style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val normalCount = viewModel.tabs.count { !it.isIncognito }
                            val privateCount = viewModel.tabs.count { it.isIncognito }

                            // Segmented control pill
                            Row(
                                modifier = Modifier
                                    .width(290.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF1E2D3F) else MaterialTheme.colorScheme.surfaceVariant),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Normal tab option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (!viewModel.isIncognitoMode && !showOnlyGroups) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            showOnlyGroups = false
                                            if (viewModel.isIncognitoMode) {
                                                viewModel.toggleIncognitoMode(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.tab_mode_normal),
                                        color = if (!viewModel.isIncognitoMode && !showOnlyGroups) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Group option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (showOnlyGroups) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            showOnlyGroups = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.tab_mode_group),
                                        color = if (showOnlyGroups) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Incognito option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (viewModel.isIncognitoMode && !showOnlyGroups) Color(0xFFFF3B5C) else Color.Transparent)
                                        .clickable {
                                            showOnlyGroups = false
                                            if (!viewModel.isIncognitoMode) {
                                                viewModel.toggleIncognitoMode(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.tab_mode_incognito),
                                        color = if (viewModel.isIncognitoMode && !showOnlyGroups) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Tab group & context menu state (Moved to top level)

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
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        BorderStroke(
                                            if (isActive) 1.5.dp else 0.5.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else groupColor?.copy(alpha = 0.5f)
                                                ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
                                            text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") stringResource(R.string.new_tab_title) else tab.title,
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
                                            contentDescription = stringResource(R.string.close_tab_desc),
                                            tint = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        var activeGroupView by remember { mutableStateOf<com.rebelroot.omni.browser.TabGroup?>(null) }

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
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        BorderStroke(
                                            if (isActive) 1.5.dp else 0.5.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else groupColor?.copy(alpha = 0.6f)
                                                ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
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
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                                text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") stringResource(R.string.new_tab_title) else tab.title,
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
                                                contentDescription = stringResource(R.string.close_tab_desc),
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
                                                contentDescription = stringResource(R.string.tab_thumbnail_desc),
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

                        @Composable
                        fun TabGroupFolderCard(group: com.rebelroot.omni.browser.TabGroup, groupTabs: List<TabState>, modifier: Modifier = Modifier, onClick: () -> Unit) {
                            val isActive = groupTabs.any { it.id == viewModel.activeTabId }
                            val groupColor = Color(group.color)
                            
                            Box(
                                modifier = modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        BorderStroke(
                                            if (isActive) 1.5.dp else 0.5.dp,
                                            if (isActive) groupColor else groupColor.copy(alpha = 0.5f)
                                        ),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { onClick() }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Header row
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(groupColor))
                                            Text(
                                                text = group.title,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text("${groupTabs.size}", color = groupColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Preview box (2x2 grid)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(groupColor.copy(alpha = 0.1f))
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val displayTabs = groupTabs.take(4)
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            userScrollEnabled = false,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(displayTabs.size) { index ->
                                                val tab = displayTabs[index]
                                                Box(
                                                    modifier = Modifier
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (viewModel.isDarkThemeEnabled) Color(0xFF1E2D3F) else Color.White),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                                        coil.compose.AsyncImage(
                                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                                .data("https://www.google.com/s2/favicons?domain=${tab.url}&sz=64")
                                                                .crossfade(true).build(),
                                                            contentDescription = null, modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Icon(Icons.Rounded.Explore, null, tint = groupColor.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        @Composable
                        fun TabGroupFolderListRow(group: com.rebelroot.omni.browser.TabGroup, groupTabs: List<TabState>, modifier: Modifier = Modifier, onClick: () -> Unit) {
                            val isActive = groupTabs.any { it.id == viewModel.activeTabId }
                            val groupColor = Color(group.color)
                            Box(
                                modifier = modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        BorderStroke(if (isActive) 1.5.dp else 0.5.dp, if (isActive) groupColor else groupColor.copy(alpha = 0.5f)),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onClick() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(groupColor))
                                    // 2x2 mini favicons
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(groupColor.copy(alpha=0.15f)).padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                groupTabs.take(2).forEach { tab ->
                                                    Box(modifier = Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(if (viewModel.isDarkThemeEnabled) Color(0xFF1E2D3F) else Color.White), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Rounded.Explore, null, tint = groupColor.copy(alpha=0.5f), modifier = Modifier.size(10.dp))
                                                    }
                                                }
                                            }
                                            if (groupTabs.size > 2) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    groupTabs.drop(2).take(2).forEach { tab ->
                                                        Box(modifier = Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(if (viewModel.isDarkThemeEnabled) Color(0xFF1E2D3F) else Color.White), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Rounded.Explore, null, tint = groupColor.copy(alpha=0.5f), modifier = Modifier.size(10.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = group.title, color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val groupTabCountText = if (groupTabs.size == 1) stringResource(R.string.tab_group_count_singular) else stringResource(R.string.tab_group_count_plural, groupTabs.size)
                                        Text(text = groupTabCountText, color = groupColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        val isList = viewModel.tabLayoutMode == "List"

                        if (activeGroupView != null) {
                            // --- GROUP SUB-VIEW ---
                            val group = activeGroupView!!
                            val tabsInGroup = currentModeTabs.filter { it.id in group.tabIds }
                            
                            if (tabsInGroup.isEmpty()) {
                                activeGroupView = null // Close view if empty
                            } else {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    // Group Header Bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(group.color).copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(group.color).copy(alpha = 0.2f))
                                                .clickable { activeGroupView = null },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.ArrowBack, "Back", tint = Color(group.color), modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            text = group.title,
                                            color = Color(group.color),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Rename group
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(group.color).copy(alpha = 0.2f))
                                                .clickable {
                                                    renameGroupTarget = group
                                                    renameGroupText = group.title
                                                    showRenameGroupDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Edit, "Rename", tint = Color(group.color), modifier = Modifier.size(14.dp))
                                        }
                                        // Delete group
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF3B5C).copy(alpha = 0.15f))
                                                .clickable { 
                                                    viewModel.deleteTabGroup(group.id)
                                                    activeGroupView = null
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Close, "Delete", tint = Color(0xFFFF3B5C), modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    // Tabs in this group
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(if (isList) 8.dp else 12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isList) {
                                            items(tabsInGroup, key = { "group_list_${it.id}" }) { tab ->
                                                TabListRow(tab)
                                            }
                                        } else {
                                            val chunks = tabsInGroup.chunked(2)
                                            items(chunks, key = { "group_grid_${it.first().id}" }) { chunk ->
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    for (tab in chunk) {
                                                        TabGridCard(tab, modifier = Modifier.weight(1f))
                                                    }
                                                    if (chunk.size == 1) Box(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- MAIN TAB GRID (Mixed Groups and Tabs) ---
                            val gridItems = mutableListOf<Any>()
                            // 1. Add valid groups
                            viewModel.tabGroups.forEach { group ->
                                val groupTabs = currentModeTabs.filter { it.id in group.tabIds }
                                if (groupTabs.isNotEmpty()) {
                                    gridItems.add(Pair(group, groupTabs))
                                }
                            }
                            // 2. Add ungrouped tabs
                            if (!showOnlyGroups) {
                                gridItems.addAll(ungroupedTabs)
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(if (isList) 8.dp else 12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (isList) {
                                    items(gridItems.size, key = { "list_$it" }) { index ->
                                        val item = gridItems[index]
                                        if (item is Pair<*, *>) {
                                            val group = item.first as com.rebelroot.omni.browser.TabGroup
                                            val groupTabs = item.second as List<TabState>
                                            TabGroupFolderListRow(group, groupTabs, onClick = { activeGroupView = group })
                                        } else if (item is TabState) {
                                            TabListRow(item)
                                        }
                                    }
                                } else {
                                    val chunks = gridItems.chunked(2)
                                    items(chunks.size, key = { "grid_$it" }) { index ->
                                        val chunk = chunks[index]
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            for (item in chunk) {
                                                if (item is Pair<*, *>) {
                                                    val group = item.first as com.rebelroot.omni.browser.TabGroup
                                                    val groupTabs = item.second as List<TabState>
                                                    TabGroupFolderCard(group, groupTabs, modifier = Modifier.weight(1f), onClick = { activeGroupView = group })
                                                } else if (item is TabState) {
                                                    TabGridCard(item as TabState, modifier = Modifier.weight(1f))
                                                }
                                            }
                                            if (chunk.size == 1) Box(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action Bar - Compact & Professional
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp)
                                .height(44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showTabGroupsSheet = false
                                    viewModel.closeAllTabs(context, viewModel.isIncognitoMode)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.menu_close_all_tabs),
                                    tint = Color(0xFFFF4D4D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.menu_close_all_tabs),
                                    color = Color(0xFFFF4D4D),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        if (showOnlyGroups) {
                                            showCreateGroupComposer = true
                                        } else {
                                            showTabGroupsSheet = false
                                            val currentActiveGroup = activeGroupView
                                            if (currentActiveGroup != null) {
                                                viewModel.createNewTab(context, "about:blank", groupId = currentActiveGroup.id)
                                            } else {
                                                viewModel.createNewTab(context, "about:blank")
                                            }
                                        }
                                    },


                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.menu_new_tab),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                                        text = stringResource(R.string.console_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.console_subtitle, allLogs.size),
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
                                        Toast.makeText(context, context.getString(R.string.console_copied_all, allLogs.size), Toast.LENGTH_SHORT).show()
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
                                "ALL" to stringResource(R.string.console_filter_all, allLogs.size),
                                "ERRS" to stringResource(R.string.console_filter_errors, errorLogs.size),
                                "WARNS" to stringResource(R.string.console_filter_warnings, warnLogs.size),
                                "LOGS" to stringResource(R.string.console_filter_logs, infoLogs.size),
                                "SYSTEM" to stringResource(R.string.console_filter_system, sysLogs.size)
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
                            placeholder = { Text(stringResource(R.string.console_filter_placeholder), fontSize = 12.sp, color = Color(0xFF484F58)) },
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
                                        text = if (consoleSearchQuery.isNotEmpty()) stringResource(R.string.console_no_match) else stringResource(R.string.console_no_logs),
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
                                                    Toast.makeText(context, context.getString(R.string.console_log_copied), Toast.LENGTH_SHORT).show()
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
                                placeholder = { Text(stringResource(R.string.console_eval_placeholder), fontSize = 12.sp, color = Color(0xFF484F58)) },
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
                                    Text(stringResource(R.string.action_run), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.console_load_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                    stringResource(R.string.console_load_desc),
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
                                            Text(stringResource(R.string.console_load_pick), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(stringResource(R.string.console_load_pick_desc), fontSize = 11.sp, color = Color(0xFF8B949E))
                                        }
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF30363D))

                                // 2. CDN URL Injector
                                Text(stringResource(R.string.console_load_cdn), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                            Toast.makeText(context, context.getString(R.string.console_injecting), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = cdnUrlInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.console_inject_url), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = Color(0xFF30363D))

                                // 3. Preset Dev Tools Libraries
                                Text(stringResource(R.string.console_presets_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                val presets = listOf(
                                    Triple(
                                        R.string.console_preset_eruda_title,
                                        R.string.console_preset_eruda_desc,
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/eruda'; document.body.appendChild(script); script.onload = function () { eruda.init(); }; })();"
                                    ),
                                    Triple(
                                        R.string.console_preset_vconsole_title,
                                        R.string.console_preset_vconsole_desc,
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/vconsole'; document.body.appendChild(script); script.onload = function () { new VConsole(); }; })();"
                                    ),
                                    Triple(
                                        R.string.console_preset_jquery_title,
                                        R.string.console_preset_jquery_desc,
                                        "(function () { var script = document.createElement('script'); script.src='https://code.jquery.com/jquery-3.7.1.min.js'; document.body.appendChild(script); console.log('jQuery 3.7.1 loaded! Use $'); })();"
                                    ),
                                    Triple(
                                        R.string.console_preset_lodash_title,
                                        R.string.console_preset_lodash_desc,
                                        "(function () { var script = document.createElement('script'); script.src='https://cdn.jsdelivr.net/npm/lodash@4.17.21/lodash.min.js'; document.body.appendChild(script); console.log('Lodash loaded! Use _'); })();"
                                    )
                                )

                                presets.forEach { (titleRes, descRes, code) ->
                                    val titleStr = context.getString(titleRes)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.consoleLogs.add(BrowserViewModel.ConsoleLogEntry("EVAL", "> [Injected Preset: $titleStr]"))
                                                viewModel.pendingJsCommand = code
                                                showLoadScriptDialog = false
                                                Toast.makeText(context, context.getString(R.string.console_injected, titleStr), Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF161B22),
                                        border = BorderStroke(0.5.dp, Color(0xFF30363D))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(stringResource(titleRes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(stringResource(descRes), fontSize = 10.sp, color = Color(0xFF8B949E))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showLoadScriptDialog = false }) {
                                Text(stringResource(R.string.close_text), color = Color(0xFF8B949E))
                            }
                        },
                        containerColor = Color(0xFF161B22)
                    )
                }
            }

            // 3.5 Site Info (Cookies, Privacy, Security) Bottom Sheet
            if (showSiteInfoSheet) {
                val currentDomain = remember(viewModel.currentUrl) {
                    try { Uri.parse(viewModel.currentUrl).host ?: viewModel.currentUrl } catch (_: Exception) { viewModel.currentUrl }
                }
                val isHttps   = viewModel.currentUrl.startsWith("https://")
                val isHttp    = viewModel.currentUrl.startsWith("http://")
                val isLocal   = viewModel.currentUrl.startsWith("about:") || viewModel.currentUrl.startsWith("file://")
                val isDark    = viewModel.isDarkThemeEnabled
                val isAmoled  = viewModel.isAmoledMode

                val sheetBg    = if (isAmoled) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                val cardBg     = if (isAmoled) Color(0xFF111113) else if (isDark) Color(0xFF2C2C2E) else Color.White
                val textPrimary   = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
                val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
                val divColor      = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

                val secureGreen = Color(0xFF30D158)
                val warnOrange  = Color(0xFFFF9F0A)
                val dangerRed   = Color(0xFFFF453A)

                // Current per-site permission values
                val permLocation   = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "location") }
                val permCamera     = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "camera") }
                val permMic        = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "microphone") }
                val permNotif      = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "notifications") }
                val permJs         = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "javascript") }
                val permAutoplay   = remember(currentDomain) { viewModel.getSitePermissionValue(currentDomain, "autoplay") }

                ModalBottomSheet(
                    onDismissRequest = { showSiteInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = sheetBg,
                    tonalElevation = 0.dp,
                    dragHandle = {
                        BottomSheetDefaults.DragHandle(
                            color = if (isDark) Color(0xFF48484A) else Color(0xFFC7C7CC),
                            width = 32.dp, height = 3.dp
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ── Header: favicon + domain + connection badge ──────────────
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data("https://www.google.com/s2/favicons?sz=128&domain=$currentDomain")
                                    .size(64, 64).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_compass)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentDomain, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = when {
                                        isLocal  -> "Internal page"
                                        isHttps  -> stringResource(R.string.site_info_secure_connection)
                                        else     -> "Connection not secure"
                                    },
                                    fontSize = 12.sp,
                                    color = when {
                                        isLocal  -> textSecondary
                                        isHttps  -> secureGreen
                                        else     -> dangerRed
                                    }
                                )
                            }
                        }

                        // ── Card 1: SSL / Connection ─────────────────────────────────
                        Surface(shape = RoundedCornerShape(14.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                                // Connection row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isLocal  -> Icons.Rounded.Info
                                            isHttps  -> Icons.Rounded.Lock
                                            else     -> Icons.Rounded.LockOpen
                                        },
                                        contentDescription = null,
                                        tint = when { isLocal -> textSecondary; isHttps -> secureGreen; else -> dangerRed },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                isLocal  -> "Internal page"
                                                isHttps  -> stringResource(R.string.site_info_conn_is_secure)
                                                else     -> stringResource(R.string.site_info_conn_not_secure)
                                            },
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimary
                                        )
                                        Text(
                                            text = when {
                                                isLocal  -> "No data is sent to external servers."
                                                isHttps  -> stringResource(R.string.site_info_conn_secure_desc)
                                                else     -> stringResource(R.string.site_info_conn_insecure_desc)
                                            },
                                            fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp
                                        )
                                    }
                                }

                                if (!isLocal) {
                                    HorizontalDivider(color = divColor, thickness = 0.5.dp)

                                    // Certificate info row
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.VerifiedUser, null,
                                            tint = if (isHttps) secureGreen else warnOrange,
                                            modifier = Modifier.size(22.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isHttps) "Certificate valid (TLS)" else "No certificate",
                                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimary
                                            )
                                            Text(
                                                text = if (isHttps)
                                                    "Identity verified · Data encrypted in transit"
                                                else
                                                    "Your data could be visible to others on this network",
                                                fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = divColor, thickness = 0.5.dp)

                                    // HTTPS-only mode status
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Security, null,
                                            tint = if (viewModel.httpsOnlyMode) secureGreen else textSecondary,
                                            modifier = Modifier.size(22.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("HTTPS-Only Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimary)
                                            Text(
                                                text = if (viewModel.httpsOnlyMode) "Active — HTTP upgrades enforced" else "Off — some pages may load over HTTP",
                                                fontSize = 12.sp, color = textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Card 2: Cookies ──────────────────────────────────────────
                        Surface(shape = RoundedCornerShape(14.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.site_info_cookies_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimary)
                                    Text(stringResource(R.string.site_info_cookies_desc), fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp)
                                }
                                TextButton(onClick = { viewModel.clearSiteData(context); showSiteInfoSheet = false }) {
                                    Text(stringResource(R.string.site_info_clear), color = dangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // ── Card 3: Site History ─────────────────────────────────────
                        if (!isLocal) {
                            val siteHistory by remember(currentDomain) {
                                derivedStateOf { viewModel.getHistoryForDomain(currentDomain) }
                            }
                            Surface(shape = RoundedCornerShape(14.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                                    Text(
                                        text = stringResource(R.string.site_history_title),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )

                                    if (siteHistory.isEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                        ) {
                                            Icon(Icons.Rounded.History, null, tint = textSecondary, modifier = Modifier.size(22.dp))
                                            Text(stringResource(R.string.site_history_empty), fontSize = 13.sp, color = textSecondary)
                                        }
                                    } else {
                                        val now = System.currentTimeMillis()
                                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                        androidx.compose.foundation.lazy.LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 280.dp),
                                            verticalArrangement = Arrangement.spacedBy(0.dp),
                                            userScrollEnabled = true
                                        ) {
                                            items(siteHistory.size, key = { siteHistory[it].url + siteHistory[it].timestamp }) { index ->
                                                val entry = siteHistory[index]
                                                val entryHost = remember(entry.url) {
                                                    try { java.net.URL(entry.url).host } catch (_: Exception) { null }
                                                }
                                                val faviconUrl = remember(entryHost) {
                                                    entryHost?.let { "https://www.google.com/s2/favicons?domain=$it&sz=64" }
                                                }
                                                val path = remember(entry.url) {
                                                    try {
                                                        java.net.URL(entry.url).path.let { if (it.isBlank() || it == "/") "" else it }
                                                    } catch (_: Exception) { "" }
                                                }
                                                Column {
                                                    if (index > 0) HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 38.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        if (faviconUrl != null) {
                                                            coil.compose.AsyncImage(
                                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                                    .data(faviconUrl).size(32, 32).crossfade(true).build(),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                                                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_compass)
                                                            )
                                                        } else {
                                                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Rounded.Language, null, tint = textSecondary, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = entry.title.takeIf { it.isNotBlank() } ?: path.ifBlank { entry.url },
                                                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textPrimary,
                                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (path.isNotBlank()) {
                                                                Text(path, fontSize = 11.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }
                                                        Text(
                                                            text = formatRelativeTime(entry.timestamp, now),
                                                            fontSize = 11.sp, color = textSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = divColor, thickness = 0.5.dp)
                                        TextButton(
                                            onClick = { viewModel.clearHistoryForDomain(currentDomain); android.widget.Toast.makeText(context, R.string.site_history_cleared, android.widget.Toast.LENGTH_SHORT).show() },
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.site_history_clear), color = dangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // ── Card 4: Per-site permissions ─────────────────────────────
                        if (!isLocal) {
                            Surface(shape = RoundedCornerShape(14.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                                    Text(
                                        text = "Permissions",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )

                                    val permissions = listOf(
                                        Triple("Location",      Icons.Rounded.LocationOn,          permLocation),
                                        Triple("Camera",        Icons.Rounded.CameraAlt,           permCamera),
                                        Triple("Microphone",    Icons.Rounded.Mic,                 permMic),
                                        Triple("Notifications", Icons.Rounded.NotificationsActive, permNotif),
                                        Triple("JavaScript",    Icons.Rounded.Code,                permJs),
                                        Triple("Autoplay",      Icons.Rounded.PlayCircle,          permAutoplay)
                                    )
                                    val typeKeys = listOf("location","camera","microphone","notifications","javascript","autoplay")

                                    permissions.forEachIndexed { index, (label, icon, value) ->
                                        if (index > 0) HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 38.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(icon, null,
                                                tint = when (value) {
                                                    "allow" -> secureGreen
                                                    "block" -> dangerRed
                                                    else    -> textSecondary
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(label, fontSize = 14.sp, color = textPrimary, modifier = Modifier.weight(1f))

                                            // Compact 3-state selector: Ask / Allow / Block
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("ask" to "Ask", "allow" to "Allow", "block" to "Block").forEach { (key, lbl) ->
                                                    val isSelected = value == key || (key == "ask" && value != "allow" && value != "block")
                                                    Surface(
                                                        onClick = { viewModel.updateSitePermission(currentDomain, typeKeys[index], key) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = when {
                                                            isSelected && key == "allow" -> secureGreen.copy(alpha = 0.15f)
                                                            isSelected && key == "block" -> dangerRed.copy(alpha = 0.15f)
                                                            isSelected                  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                            else                        -> Color.Transparent
                                                        },
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.padding(horizontal = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = lbl,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = when {
                                                                    isSelected && key == "allow" -> secureGreen
                                                                    isSelected && key == "block" -> dangerRed
                                                                    isSelected                  -> MaterialTheme.colorScheme.primary
                                                                    else                        -> textSecondary
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }

                        // ── Card 4: Privacy Report ─────────
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth().clickable { showSiteInfoSheet = false; showPrivacyReportSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, null, tint = secureGreen, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.site_info_privacy_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimary)
                                    Text(stringResource(R.string.site_info_privacy_desc), fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp)
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (showPrivacyReportSheet) {
                PrivacyReportSheet(
                    onDismissRequest = { showPrivacyReportSheet = false },
                    viewModel = viewModel
                )
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
                    title = { Text(stringResource(R.string.ext_delete_title), color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black) },
                    text = { Text(stringResource(R.string.ext_delete_confirm_user, extDisplayName), color = if (viewModel.isDarkThemeEnabled) Color(0xFFC5D1DE) else Color.DarkGray) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.uninstallUserExtension(ext, context)
                                extensionToDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.ext_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { extensionToDelete = null }) {
                            Text(stringResource(R.string.ext_cancel), color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color.Gray)
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
                    title = { Text(stringResource(R.string.ext_delete_title), color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black) },
                    text = { Text(stringResource(R.string.ext_delete_confirm_builtin, name), color = if (viewModel.isDarkThemeEnabled) Color(0xFFC5D1DE) else Color.DarkGray) },
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
                            Text(stringResource(R.string.ext_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { builtInExtensionToDelete = null }) {
                            Text(stringResource(R.string.ext_cancel), color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color.Gray)
                        }
                    },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // ── Menu Bottom Sheet (Unified with All-In-One Menu Sheet) ──────────────────
            // Only redirect to the bottom sheet when the AllInOne nav is at the bottom.
            // When nav is at the top, showMenu drives the omnimenuDropdown popup directly.
            LaunchedEffect(showMenu) {
                if (showMenu && viewModel.addressBarPosition == "Bottom") {
                    showMenu = false
                    showAllInOneMenuSheet = true
                }
            }

            // Render top dropdown as an in-canvas overlay on web pages to keep GeckoView window focused
            if (showMenu && viewModel.addressBarPosition != "Bottom") {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val statusBarPx = WindowInsets.statusBars.getTop(density)
                val statusBarDp = with(density) { statusBarPx.toDp() }
                val topBarHeightDp = if (measuredTopBarHeightPx > 0) with(density) { measuredTopBarHeightPx.toDp() } else 56.dp
                val menuTopOffset = statusBarDp + topBarHeightDp + 4.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(100f)
                ) {
                    // Transparent clickable scrim to dismiss menu on outer tap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showMenu = false }
                            )
                    )
                    // Positioned dropdown menu card floating below top address bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = menuTopOffset, end = 8.dp)
                    ) {
                        omnimenuDropdownCard(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            viewModel = viewModel,
                            onNewTab = {
                                showMenu = false
                                viewModel.createNewTab(context, "about:blank")
                            },
                            onNewIncognitoTab = {
                                showMenu = false
                                if (!viewModel.isIncognitoMode) {
                                    viewModel.toggleIncognitoMode(context)
                                }
                                viewModel.createNewTab(context, "about:blank")
                            },
                            onOpenHistory = { showMenu = false; onOpenHistory() },
                            onBurnData = {
                                showMenu = false
                                coroutineScope.launch {
                                    val runtime = viewModel.getGeckoRuntime(context)
                                    FireButton(runtime, context).burn()
                                    viewModel.burnAllData(context)
                                    Toast.makeText(context, context.getString(R.string.toast_burn_all), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenDownloads = { showMenu = false; onOpenDownloads() },
                            onOpenBookmarks = { showMenu = false; onOpenBookmarks() },
                            onOpenSettings = { showMenu = false; onOpenSettings() },
                            onOpenPasswordManager = { showMenu = false; onOpenPasswordManager() },
                            onShowThemeSheet = { showMenu = false; showThemeSheet = true },
                            onShowFeedbackDialog = { showMenu = false; showFeedbackDialog = true },
                            onShowCustomizationSheet = { showMenu = false; showCustomizationSheet = true },
                            onShowExtensions = { showMenu = false; showExtensionsSheet = true },
                            onShowPlayerSettings = { showMenu = false; showPlayerSettingsDialog = true },
                            onShowSiteInfo = { showMenu = false; showSiteInfoSheet = true },
                            onFindInPage = { showMenu = false; viewModel.openFindInPage() }
                        )
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

                    // Take a stable snapshot to avoid ConcurrentModificationException.
                    // Extensions without an id are unmanageable leftovers — hide them
                    // so the UI (which keys on ext.id) can never crash on them.
                    val userExts = remember(viewModel.userExtensions.toList()) {
                        viewModel.userExtensions.filter { it.id != null }.toList()
                    }
                    val isDarkExt = viewModel.isDarkThemeEnabled
                    val totalInstalled = userExts.size
                    val totalEnabled = userExts.count {
                        try { it.metaData?.enabled == true } catch (_: Exception) { false }
                    }

                    var selectedExtensionTab by remember { mutableStateOf("Installed") }
                    var selectedCuratedCategory by remember { mutableStateOf("All") }

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
                                    text = stringResource(R.string.ext_sheet_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (totalInstalled > 0)
                                        stringResource(R.string.ext_subtitle_active, totalInstalled, totalEnabled)
                                    else stringResource(R.string.ext_subtitle_empty),
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
                                            contentDescription = stringResource(R.string.ext_list_view),
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
                                            contentDescription = stringResource(R.string.ext_grid_view),
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
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Store,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.ext_browse_store),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // ── Segmented Tab Selector: Installed vs Curated Store ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Installed" to stringResource(R.string.ext_installed_tab),
                                "Curated Store" to stringResource(R.string.ext_curated_store_tab)
                            ).forEach { (tabId, tabLabel) ->
                                val isSelected = selectedExtensionTab == tabId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable { selectedExtensionTab = tabId },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (tabId == "Installed") stringResource(R.string.ext_installed_tab_count, totalInstalled) else tabLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDarkExt) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))

                        if (selectedExtensionTab == "Curated Store") {
                            // ── Category Filter Chips ──
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                items(com.rebelroot.omni.browser.extensions.CuratedExtensionRepository.categories) { cat ->
                                    val isCatSelected = selectedCuratedCategory == cat
                                    val catLabel = when (cat) {
                                        "All" -> stringResource(R.string.ext_cat_all)
                                        "Privacy" -> stringResource(R.string.ext_cat_privacy)
                                        "Utilities" -> stringResource(R.string.ext_cat_utilities)
                                        "Media" -> stringResource(R.string.ext_cat_media)
                                        "Productivity" -> stringResource(R.string.ext_cat_productivity)
                                        else -> cat
                                    }
                                    FilterChip(
                                        selected = isCatSelected,
                                        onClick = { selectedCuratedCategory = cat },
                                        label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            // ── Curated Extensions List ──
                            val filteredCurated = remember(selectedCuratedCategory) {
                                val list = com.rebelroot.omni.browser.extensions.CuratedExtensionRepository.curatedList
                                if (selectedCuratedCategory == "All") list
                                else list.filter { it.category == selectedCuratedCategory }
                            }

                            val installedExtIds = remember(userExts) {
                                userExts.mapNotNull { try { it.id } catch (_: Exception) { null } }
                            }

                            filteredCurated.forEach { curated ->
                                val isCuratedInstalled = installedExtIds.any { id ->
                                    id.contains(curated.id.substringBefore("@"), ignoreCase = true) ||
                                    curated.id.contains(id.substringBefore("@"), ignoreCase = true)
                                }
                                CuratedExtensionCard(
                                    extension = curated,
                                    isInstalled = isCuratedInstalled,
                                    onInstallClick = {
                                        viewModel.installExtensionFromUrl(curated.downloadUrl, context)
                                    }
                                )
                            }
                        } else {
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
                                        text = stringResource(R.string.ext_installed_addons),
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
                                                            val activeAction = ext.id?.let { viewModel.getActionForExtension(it) }
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
                                                    val activeAction = ext.id?.let { viewModel.getActionForExtension(it) }
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
                                text = stringResource(R.string.ext_built_in_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        val extTeamAuthor = stringResource(R.string.ext_team_author)
                        val extUniversalCopy = stringResource(R.string.ext_builtin_universal_copy)
                        val extUniversalCopyDesc = stringResource(R.string.ext_builtin_universal_copy_desc)
                        val extMediaSniffer = stringResource(R.string.ext_builtin_media_sniffer)
                        val extMediaSnifferDesc = stringResource(R.string.ext_builtin_media_sniffer_desc)
                        val extAiBlocker = stringResource(R.string.ext_builtin_ai_blocker)
                        val extAiBlockerDesc = stringResource(R.string.ext_builtin_ai_blocker_desc)
                        val extForceDark = stringResource(R.string.appearance_force_dark_websites)
                        val extForceDarkDesc = stringResource(R.string.appearance_force_dark_websites_desc)
                        val extOmniTranslate = stringResource(R.string.ext_builtin_omni_translate)
                        val extOmniTranslateDesc = stringResource(R.string.ext_builtin_omni_translate_desc)
                        val builtInExts = remember(
                            viewModel.isUniversalCopyEnabled, viewModel.isUniversalCopyToggling,
                            viewModel.isMediaGrabberEnabled, viewModel.isMediaGrabberToggling,
                            viewModel.isAiBlockerEnabled, viewModel.isAiBlockerToggling,
                            viewModel.forceDarkWebsites
                        ) {
                            listOf(
                                BuiltInExt(Icons.Rounded.FileCopy, extUniversalCopy, extTeamAuthor,
                                    extUniversalCopyDesc,
                                    viewModel.isUniversalCopyEnabled, !viewModel.isUniversalCopyToggling,
                                    { viewModel.toggleUniversalCopy(context) }),
                                BuiltInExt(Icons.Rounded.Download, extMediaSniffer, extTeamAuthor,
                                    extMediaSnifferDesc,
                                    viewModel.isMediaGrabberEnabled, !viewModel.isMediaGrabberToggling,
                                    { viewModel.toggleMediaGrabber(context) },
                                    { viewModel.showMediaSnifferSettingsDialog = true }),
                                BuiltInExt(Icons.Rounded.Translate, extOmniTranslate, extTeamAuthor,
                                    extOmniTranslateDesc,
                                    true, false,
                                    { /* always-on bridge: not user-togglable */ },
                                    { showTranslationDialog = true }),
                                BuiltInExt(Icons.Rounded.Block, extAiBlocker, extTeamAuthor,
                                    extAiBlockerDesc,
                                    viewModel.isAiBlockerEnabled, !viewModel.isAiBlockerToggling,
                                    { viewModel.toggleAiBlocker(context) }),
                                BuiltInExt(Icons.Rounded.DarkMode, extForceDark, extTeamAuthor,
                                    extForceDarkDesc,
                                    viewModel.forceDarkWebsites, true,
                                    { viewModel.saveForceDarkWebsites(context, !viewModel.forceDarkWebsites) })
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
                                    text = stringResource(R.string.ext_get_more_addons),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }


            // 4c. External extension install permission dialog.
            // GeckoView has no native prompt UI; this surfaces the install-prompt
            // request and completes the GeckoResult with the user's choice.
            viewModel.pendingExtensionInstallPrompt?.let { pending ->
                AlertDialog(
                    onDismissRequest = { viewModel.respondToInstallPrompt(false) },
                    containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = "Install extension?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = pending.extensionName ?: pending.extensionId ?: "Unknown extension",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            if (pending.permissions.isNotEmpty()) {
                                Text(
                                    text = "This extension is requesting permission to:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                pending.permissions.forEach { perm ->
                                    Text("• $perm", fontSize = 13.sp)
                                }
                            }
                            if (pending.origins.isNotEmpty()) {
                                Text(
                                    text = "Sites:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                pending.origins.forEach { origin ->
                                    Text("• $origin", fontSize = 13.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.respondToInstallPrompt(true) }) {
                            Text("Install", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.respondToInstallPrompt(false) }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 4d. WebExtension Download confirmation dialog
            viewModel.pendingWebExtensionDownload?.let { downloadPrompt ->
                WebExtensionDownloadConfirmationDialog(
                    prompt = downloadPrompt,
                    onDismiss = { viewModel.pendingWebExtensionDownload = null }
                )
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
                                            text = stringResource(R.string.ext_popup_subtitle),
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
                                            contentDescription = stringResource(R.string.ext_zoom_out),
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
                                            contentDescription = stringResource(R.string.ext_zoom_in),
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
                                            contentDescription = stringResource(R.string.ext_close),
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
                                                text = stringResource(R.string.ext_loading_extension),
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

            // 4.5 Quick Tools Bottom Sheet (Reorderable)

        if (showAllInOneMenuSheet) {
            AllInOneMenuSheet(
                viewModel = viewModel,
                onDismissRequest = { showAllInOneMenuSheet = false },
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
                        Toast.makeText(context, context.getString(R.string.toast_burn_all), Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenDownloads = onOpenDownloads,
                onOpenBookmarks = onOpenBookmarks,
                onOpenSettings = onOpenSettings,
                onShowCustomizationSheet = {
                    showAllInOneMenuSheet = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        showCustomizationSheet = true
                    }
                },
                onShowExtensions = {
                    showAllInOneMenuSheet = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        showExtensionsSheet = true
                    }
                },
                onShowPlayerSettings = {
                    showAllInOneMenuSheet = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        showPlayerSettingsDialog = true
                    }
                },
                onShowSiteInfo = { showSiteInfoSheet = true },
                onFindInPage = { viewModel.openFindInPage() },
                onAddTabToNewGroup = {
                    showAllInOneMenuSheet = false
                    if (viewModel.activeTabId != null) {
                        groupDialogTargetTabId = viewModel.activeTabId
                        newGroupTitle = ""
                        newGroupColorIndex = 0
                        showGroupDialog = true
                    } else {
                        Toast.makeText(context, "No active tab to group", Toast.LENGTH_SHORT).show()
                    }
                },
                hasActiveUserExtensions = hasActiveUserExtensions,
                onShowThemeSheet = {
                    showAllInOneMenuSheet = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        showThemeSheet = true
                    }
                },
                onShowFeedbackDialog = {
                    showAllInOneMenuSheet = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        showFeedbackDialog = true
                    }
                }
            )
        }

        if (showThemeSheet) {
            ThemeSheet(
                viewModel = viewModel,
                onDismissRequest = { showThemeSheet = false }
            )
        }

        if (showPlayerSettingsDialog) {
            PlayerSettingsSheet(
                viewModel = viewModel,
                onDismissRequest = { showPlayerSettingsDialog = false },
                onShowSnifferSettings = {
                    showPlayerSettingsDialog = false
                    viewModel.showMediaSnifferSettingsDialog = true
                }
            )
        }

        if (showFeedbackDialog) {
            HelpFeedbackDialog(
                viewModel = viewModel,
                onDismissRequest = { showFeedbackDialog = false }
            )
        }

        if (showQuickToolsSheet) {
                val isDark = viewModel.isDarkThemeEnabled
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                var lastSwapMs by remember { mutableStateOf(0L) }

                ModalBottomSheet(
                    onDismissRequest = { showQuickToolsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = when {
                        viewModel.isAmoledMode  -> Color(0xFF000000)
                        isDark                  -> Color(0xFF111113)
                        else                    -> Color(0xFFF2F2F7)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.quick_tools_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(id = R.string.quick_tools_reorder_hint),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

                        val cardModifier = Modifier.width(72.dp)
                        val isEditing = activeTab?.isEditModeEnabled ?: false

                        // Stable ordered list that reacts to ViewModel state
                        val toolOrderState = remember(viewModel.quickToolsOrder) {
                            mutableStateListOf<String>().also { list ->
                                val vmOrder = viewModel.quickToolsOrder
                                 val allTools = listOf(
                                    "speed_dial", "image_grabber", "page_inspector", "block_area", "spoof_identity", "force_zoom", "vpn",
                                    "torrent_downloader", "omni_config",
                                    "qr_scanner", "safe_locker", "translator", "edit_page",
                                    "save_pdf", "pin_web_app", "auto_scroll", "qr_scan_page",
                                    "qr_generator", "dev_notes", "site_style"
                                )
                                list.addAll(vmOrder.filter { it in allTools } + allTools.filter { it !in vmOrder })
                            }
                        }
                        var draggedId by remember { mutableStateOf<String?>(null) }
                        val itemCenters = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
                        var gridTopLeft by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                        // Resolve tool display info
                        fun toolTitle(id: String): String = when (id) {
                            "speed_dial"          -> "Speed Dial"
                            "image_grabber"       -> context.getString(R.string.tool_image_grabber)
                            "page_inspector"      -> context.getString(R.string.tool_page_inspector)
                            "block_area"          -> context.getString(R.string.tool_block_area)
                            "spoof_identity"      -> "Spoof Identity"
                            "torrent_downloader"  -> "Torrent & Magnet"
                            "omni_config"         -> "omni:config"
                            "force_zoom"          -> if (viewModel.accessibilityForceZoom) context.getString(R.string.tool_force_zoom_on) else context.getString(R.string.tool_force_zoom)
                            "vpn"                 -> when (viewModel.proxyProvider) {
                                "tor", "tor_builtin" -> {
                                    val state = viewModel.activeTorState().value
                                    if (state is com.rebelroot.omni.privacy.TorState.Connected) context.getString(R.string.tool_vpn_tor_on) else context.getString(R.string.tool_vpn_tor)
                                }
                                "wireguard" -> {
                                    val state = viewModel.vpnManager.state.value
                                    if (state is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected) context.getString(R.string.tool_vpn_on) else context.getString(R.string.tool_vpn)
                                }
                                else -> context.getString(R.string.tool_network)
                            }
                            "qr_scanner"     -> context.getString(R.string.tool_qr_scanner)
                            "safe_locker"    -> context.getString(R.string.tool_safe_locker)
                            "translator"     -> context.getString(R.string.tool_translator)
                            "edit_page"      -> if (isEditing) context.getString(R.string.tool_stop_edit) else context.getString(R.string.tool_edit_page)
                            "save_pdf"       -> context.getString(R.string.tool_save_pdf)
                            "pin_web_app"    -> context.getString(R.string.tool_pin_web_app)
                            "auto_scroll"    -> context.getString(R.string.tool_auto_scroll)
                            "qr_scan_page"   -> context.getString(R.string.tool_qr_scan_page)
                            "qr_generator"   -> context.getString(R.string.tool_qr_generator)
                            "console_log"    -> context.getString(R.string.tool_console_log)
                            "dev_notes"      -> context.getString(R.string.tool_dev_notes)
                            "site_style"     -> context.getString(R.string.tool_site_style)
                            else -> id
                        }
                        fun toolIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
                            "speed_dial"          -> Icons.Rounded.Apps
                            "image_grabber"      -> Icons.Rounded.Collections
                            "page_inspector"     -> Icons.Rounded.Code
                            "block_area"          -> Icons.Rounded.LayersClear
                            "spoof_identity"      -> Icons.Rounded.Devices
                            "torrent_downloader"  -> Icons.Rounded.Download
                            "omni_config"         -> Icons.Rounded.Tune
                            "force_zoom"          -> Icons.Rounded.ZoomIn
                            "vpn"                 -> when (viewModel.proxyProvider) {
                                "tor" -> Icons.Rounded.Security
                                "wireguard" -> Icons.Rounded.VpnKey
                                else -> Icons.Rounded.Public
                            }
                            "qr_scanner"     -> Icons.Rounded.QrCodeScanner
                            "safe_locker"    -> Icons.Rounded.Lock
                            "translator"     -> Icons.Rounded.Translate
                            "edit_page"      -> Icons.Rounded.Edit
                            "save_pdf"       -> Icons.Rounded.Print
                            "pin_web_app"    -> Icons.AutoMirrored.Rounded.OpenInNew
                            "auto_scroll"    -> Icons.Rounded.ArrowDownward
                            "qr_scan_page"   -> Icons.Rounded.CenterFocusWeak
                            "qr_generator"   -> Icons.Rounded.QrCode2
                            "console_log"    -> Icons.Rounded.Terminal
                            "dev_notes"      -> Icons.Rounded.Description
                            "site_style"     -> Icons.Rounded.Palette
                            else -> Icons.Rounded.Build
                        }
                        fun toolAction(id: String): () -> Unit = when (id) {
                            "speed_dial" -> ({
                                showQuickToolsSheet = false
                                showSpeedDialSheet = true
                            })
                            "image_grabber" -> ({
                                showQuickToolsSheet = false
                                if (!showHomeScreen && activeTab != null) showImageGrabberSheet = true
                                else Toast.makeText(context, context.getString(R.string.toast_open_webpage_images), Toast.LENGTH_SHORT).show()
                            })
                            "page_inspector" -> ({
                                showQuickToolsSheet = false
                                if (!showHomeScreen && activeTab != null) showPageInspectorSheet = true
                                else Toast.makeText(context, context.getString(R.string.toast_open_webpage_inspect), Toast.LENGTH_SHORT).show()
                            })
                            "block_area" -> ({
                                showQuickToolsSheet = false
                                if (!showHomeScreen && activeTab != null) viewModel.toggleVisualBlockMode()
                                else Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                            })
                            "spoof_identity" -> ({
                                showQuickToolsSheet = false
                                showSpoofIdentityDialog = true
                            })
                            "torrent_downloader" -> ({
                                showQuickToolsSheet = false
                                showTorrentDownloaderDialog = true
                            })
                            "omni_config" -> ({
                                showQuickToolsSheet = false
                                viewModel.loadUrl("omni:config")
                            })
                            "force_zoom" -> ({
                                val nextState = !viewModel.accessibilityForceZoom
                                viewModel.saveAccessibilityForceZoom(context, nextState)
                                if (nextState) {
                                    viewModel.injectZoomEnabler()
                                    Toast.makeText(context, "🔍 " + context.getString(R.string.toast_force_zoom_on), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.toast_force_zoom_off), Toast.LENGTH_SHORT).show()
                                }
                                showQuickToolsSheet = false
                            })
                            "vpn" -> ({
                                showQuickToolsSheet = false
                                when (viewModel.proxyProvider) {
                                    "tor", "tor_builtin" -> {
                                        val state = viewModel.activeTorState().value
                                        if (state is com.rebelroot.omni.privacy.TorState.Connected) {
                                            viewModel.disconnectTor()
                                            Toast.makeText(context, "🧅 " + context.getString(R.string.toast_tor_disconnected), Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.connectTor()
                                            Toast.makeText(context, "🧅 " + context.getString(R.string.toast_tor_connecting), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "wireguard" -> {
                                        if (viewModel.vpnManager.state.value is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected) {
                                            viewModel.disconnectVpn()
                                            Toast.makeText(context, "🛡️ " + context.getString(R.string.toast_vpn_disconnected), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val config = viewModel.customVpnConfig
                                            if (!config.isNullOrBlank()) {
                                                val vpnIntent = android.net.VpnService.prepare(context)
                                                if (vpnIntent != null) {
                                                    vpnPermissionLauncher.launch(vpnIntent)
                                                } else {
                                                    viewModel.connectCustomVpn()
                                                    Toast.makeText(context, "🛡️ " + context.getString(R.string.toast_vpn_connecting), Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                onOpenSettings()
                                                Toast.makeText(context, context.getString(R.string.toast_vpn_setup_required), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    else -> {
                                        Toast.makeText(context, context.getString(R.string.toast_direct_connection), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })
                            "qr_scanner" -> ({
                                showQuickToolsSheet = false
                                if (!viewModel.hasSeenQrOverview) { pendingQrAction = onOpenQrTools; showQrOverviewDialog = true } else onOpenQrTools()
                            })
                            "safe_locker"  -> ({ showQuickToolsSheet = false; onOpenLocker() })
                            "translator"   -> ({ showQuickToolsSheet = false; translationSourceText = ""; translationResultText = ""; showTranslationDialog = true })
                            "edit_page" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; if (!viewModel.hasSeenEditPageOverview) { pendingEditPageAction = { viewModel.toggleEditMode() }; showEditPageOverviewDialog = true } else viewModel.toggleEditMode() }
                            })
                            "save_pdf" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; if (!viewModel.hasSeenPdfOverview) { pendingPdfAction = { viewModel.printCurrentPage(context) }; showPdfOverviewDialog = true } else viewModel.printCurrentPage(context) }
                            })
                            "pin_web_app" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; viewModel.installWebAppShortcut(context, activeTab.title, activeTab.url) }
                            })
                            "auto_scroll" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; isAutoScrollActive = !isAutoScrollActive }
                            })
                            "qr_scan_page" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; if (!viewModel.hasSeenQrOverview) { pendingQrAction = { viewModel.scanPageForQrCodes() }; showQrOverviewDialog = true } else viewModel.scanPageForQrCodes() }
                            })
                            "qr_generator" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; if (!viewModel.hasSeenQrOverview) { pendingQrAction = { qrGeneratorUrl = activeTab.url; showQrGeneratorDialog = true }; showQrOverviewDialog = true } else { qrGeneratorUrl = activeTab.url; showQrGeneratorDialog = true } }
                            })
                            "console_log" -> ({
                                showQuickToolsSheet = false
                                if (!viewModel.hasSeenConsoleOverview) { pendingConsoleAction = { showConsoleSheet = true }; showConsoleOverviewDialog = true } else showConsoleSheet = true
                            })
                            "dev_notes" -> ({
                                showQuickToolsSheet = false
                                if (!viewModel.hasSeenDevNotesOverview) { pendingDevNotesAction = { showDevNotesSheet = true }; showDevNotesOverviewDialog = true } else showDevNotesSheet = true
                            })
                            "site_style" -> ({
                                if (showHomeScreen || activeTab == null) Toast.makeText(context, context.getString(R.string.toast_open_webpage_tool), Toast.LENGTH_SHORT).show()
                                else { showQuickToolsSheet = false; showSiteStyleCustomizerSheet = true }
                            })
                            else -> ({})
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    gridTopLeft = coords.boundsInWindow().topLeft
                                }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val absPos = gridTopLeft + offset
                                            val closestEntry = itemCenters.entries.minByOrNull { entry ->
                                                val dx = entry.value.x - absPos.x
                                                val dy = entry.value.y - absPos.y
                                                dx * dx + dy * dy
                                            }
                                            if (closestEntry != null) {
                                                val dx = closestEntry.value.x - absPos.x
                                                val dy = closestEntry.value.y - absPos.y
                                                if (dx * dx + dy * dy < 2500f) { // threshold
                                                    draggedId = closestEntry.key
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                                            val currentCenter = itemCenters[id] ?: return@detectDragGesturesAfterLongPress
                                            val newCenter = currentCenter + dragAmount
                                            itemCenters[id] = newCenter
                                            
                                            val absPos = newCenter
                                            val myDistSq = 2500f
                                            val closestEntry = itemCenters.entries
                                                .filter { it.key != id }
                                                .minByOrNull { entry ->
                                                    val dx = entry.value.x - absPos.x
                                                    val dy = entry.value.y - absPos.y
                                                    dx * dx + dy * dy
                                                }
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
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
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
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                                                    val toolCardModifier = if (isDragged) {
                                                        cardModifier.graphicsLayer {
                                                            scaleX = 1.12f
                                                            scaleY = 1.12f
                                                            shadowElevation = 18f
                                                            alpha = 0.82f
                                                        }
                                                    } else {
                                                        cardModifier
                                                    }
                                                    ToolCard(
                                                        title = toolTitle(toolId),
                                                        icon = toolIcon(toolId),
                                                        isDarkTheme = isDark,
                                                        modifier = toolCardModifier,
                                                        isCompact = true,
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


            // 5. Grid Menu Bottom Sheet (Unified with All-In-One Menu Sheet)
            LaunchedEffect(showToolsSheet) {
                if (showToolsSheet) {
                    showToolsSheet = false
                    showAllInOneMenuSheet = true
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

            if (showImageGrabberSheet) {
                ImageGrabberSheetContent(
                    viewModel = viewModel,
                    onDismissRequest = { showImageGrabberSheet = false }
                )
            }

            if (showPageInspectorSheet) {
                PageInspectorSheetContent(
                    viewModel = viewModel,
                    onDismissRequest = { showPageInspectorSheet = false }
                )
            }

            if (showGroupDialog && groupDialogTargetTabId != null) {
                val currentGroup = remember(viewModel.tabGroups.toList()) { viewModel.getGroupForTab(groupDialogTargetTabId!!) }
                val groupColors = listOf(
                    0xFF4285F4L, 0xFF34A853L, 0xFFFBBC05L, 0xFFEA4335L,
                    0xFF8AB4F8L, 0xFF81C995L, 0xFFFDE293L, 0xFFF28B82L,
                    0xFF9AA0A6L, 0xFF607D8BL, 0xFFFF9800L, 0xFF9C27B0L,
                    0xFFE91E63L, 0xFF795548L, 0xFF009688L, 0xFF3F51B5L
                )
                TabGroupDialog(
                    viewModel = viewModel,
                    targetTabId = groupDialogTargetTabId!!,
                    currentGroup = currentGroup,
                    newGroupTitle = newGroupTitle,
                    onNewGroupTitleChange = { newGroupTitle = it },
                    newGroupColorIndex = newGroupColorIndex,
                    onNewGroupColorIndexChange = { newGroupColorIndex = it },
                    groupColors = groupColors,
                    onDismissRequest = { showGroupDialog = false }
                )
            }

            if (showRenameGroupDialog && renameGroupTarget != null) {
                val groupColors = listOf(
                    0xFF4285F4L, 0xFF34A853L, 0xFFFBBC05L, 0xFFEA4335L,
                    0xFF8AB4F8L, 0xFF81C995L, 0xFFFDE293L, 0xFFF28B82L,
                    0xFF9AA0A6L, 0xFF607D8BL, 0xFFFF9800L, 0xFF9C27B0L,
                    0xFFE91E63L, 0xFF795548L, 0xFF009688L, 0xFF3F51B5L
                )
                RenameTabGroupDialog(
                    viewModel = viewModel,
                    renameGroupTarget = renameGroupTarget!!,
                    renameGroupText = renameGroupText,
                    onRenameGroupTextChange = { renameGroupText = it },
                    groupColors = groupColors,
                    onDismissRequest = { showRenameGroupDialog = false },
                    onRenameGroupTargetChange = { renameGroupTarget = it }
                )
            }

            if (showCreateGroupComposer) {
                CreateNewGroupComposerDialog(
                    viewModel = viewModel,
                    onDismissRequest = { showCreateGroupComposer = false },
                    onGroupCreated = {
                        showTabGroupsSheet = false
                    }
                )
            }


            // --- Safari-style Password Manager Banner ---
            val saveCred = viewModel.pendingSaveCredential
            val autofillSuggestion = viewModel.autofillSuggestion
            val context = LocalContext.current

            if (saveCred != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .zIndex(1000f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.padding(
                            bottom = if (viewModel.addressBarPosition == "bottom" && !showHomeScreen) 84.dp else 24.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                    ) {
                if (saveCred != null) {
                    val isDark = viewModel.isDarkThemeEnabled
                    val bgColor = if (viewModel.isAmoledMode) Color(0xFF101012) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFAFAFC)
                    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
                    val subTextColor = if (isDark) Color(0x99FFFFFF) else Color(0x99000000)

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = bgColor,
                        shadowElevation = 16.dp,
                        tonalElevation = 8.dp,
                        border = BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Save Password?",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor
                                        )
                                        Text(
                                            text = saveCred.domain,
                                            fontSize = 12.sp,
                                            color = subTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.dismissSaveCredential() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = subTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Account Detail Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0x15FFFFFF) else Color(0x0A000000),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = subTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = saveCred.username,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Actions Row (Safari-Style)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { viewModel.neverSavePasswordForDomain(context, saveCred.domain) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Never for This Site", fontSize = 11.sp, color = subTextColor)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { viewModel.dismissSaveCredential() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("Not Now", fontSize = 13.sp, color = subTextColor)
                                }
                                Button(
                                    onClick = {
                                        viewModel.savePassword(saveCred.domain, saveCred.username, saveCred.password)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Save Password", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // iOS-style Capsule Scrollbar Overlay
            if (viewModel.showScrollButtons && !showHomeScreen && activeTab != null) {
                // Fetch page dimensions when tab changes
                LaunchedEffect(activeTab.id) {
                    viewModel.scrollPillState = ScrollPillState.VISIBLE_IDLE
                    activeTab.session.loadUri(
                        "javascript:(function(){try{var sh=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;if(sh&&vh){var ot=document.title;document.title='__omni__:'+sh+':'+vh;setTimeout(function(){if(document.title.indexOf('__omni__:')===0)document.title=ot;},10);}}catch(e){}})();"
                    )
                    kotlinx.coroutines.delay(1500)
                    if (!viewModel.isFastScrollingPill) {
                        viewModel.scrollPillState = ScrollPillState.FADED
                    }
                }

                val density = androidx.compose.ui.platform.LocalDensity.current
                val pillBottomNavHeight = if (viewModel.addressBarPosition == "Bottom" && !isTablet && !viewModel.isFullscreen && !isLandscape) {
                    val searchH = config.searchBoxHeight + (config.paddingVertical * 2)
                    if (viewModel.chromeNavBarEnabled) searchH else searchH + config.bottomNavBarHeight
                } else 0.dp
                val topTrackOffset = 6.dp
                val bottomTrackOffset = (pillBottomNavHeight * (1f - bottomBarFraction)) + 6.dp

                val topTrackPx = with(density) { topTrackOffset.toPx() }
                val bottomTrackPx = with(density) { bottomTrackOffset.toPx() }
                LaunchedEffect(topTrackPx, bottomTrackPx) {
                    viewModel.fastScrollPillTrackTop = topTrackPx
                    viewModel.fastScrollPillTrackBottom = bottomTrackPx
                }

                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

                val currentOffset = maxOf(currentScrollPos, viewModel.currentScrollOffset).toFloat()
                val geometry = remember(
                    screenWidthPx, screenHeightPx, topTrackPx, bottomTrackPx,
                    viewModel.pageScrollHeight, viewModel.pageViewportHeight,
                    viewModel.currentScrollRange, viewModel.currentScrollExtent,
                    currentOffset, viewModel.isFastScrollingPill, viewModel.fastScrollPillFraction
                ) {
                    FastScrollMath.computeGeometry(
                        viewportWidth = screenWidthPx,
                        viewportHeight = screenHeightPx,
                        topTrackOffset = topTrackPx,
                        bottomTrackOffset = bottomTrackPx,
                        pageScrollHeight = viewModel.pageScrollHeight,
                        pageViewportHeight = viewModel.pageViewportHeight,
                        scrollRange = viewModel.currentScrollRange,
                        scrollExtent = viewModel.currentScrollExtent,
                        currentScrollOffset = currentOffset,
                        isDragging = viewModel.isFastScrollingPill,
                        dragFraction = viewModel.fastScrollPillFraction,
                        minThumbPx = with(density) { 36.dp.toPx() },
                        maxThumbPx = with(density) { 90.dp.toPx() },
                        hitboxWidthPx = with(density) { 48.dp.toPx() },
                        hitboxTolerancePx = with(density) { 20.dp.toPx() },
                        minHitboxHeightPx = with(density) { 64.dp.toPx() }
                    )
                }

                LaunchedEffect(geometry) {
                    viewModel.fastScrollGeometry = geometry
                }

                LaunchedEffect(activeTab.session) {
                    viewModel.fastScrollController.attachSession(activeTab.session)
                }

                // Auto show/hide on normal scrolling: wake up pill for 1500ms then smoothly fade out
                LaunchedEffect(currentScrollPos, viewModel.currentScrollOffset, viewModel.isFastScrollingPill) {
                    if (viewModel.isFastScrollingPill) {
                        viewModel.scrollPillState = ScrollPillState.DRAGGING
                    } else if (geometry.isScrollable) {
                        viewModel.scrollPillState = ScrollPillState.VISIBLE_IDLE
                        kotlinx.coroutines.delay(1500)
                        if (!viewModel.isFastScrollingPill) {
                            viewModel.scrollPillState = ScrollPillState.FADED
                        }
                    } else {
                        viewModel.scrollPillState = ScrollPillState.NON_SCROLLABLE
                    }
                }

                val isPillVisible = !viewModel.isFullscreen && geometry.isScrollable &&
                    (viewModel.scrollPillState == ScrollPillState.VISIBLE_IDLE || viewModel.scrollPillState == ScrollPillState.DRAGGING)

                val alphaAnim by animateFloatAsState(
                    targetValue = if (isPillVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = if (viewModel.scrollPillState == ScrollPillState.DRAGGING) 100 else 350),
                    label = "scrollAlpha"
                )

                // iOS capsule width animation: thin when idle, thick when grabbed
                val capsuleWidth by animateDpAsState(
                    targetValue = if (viewModel.isFastScrollingPill) 12.dp else 5.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "capsuleWidth"
                )

                val pillLocalView = LocalView.current

                DisposableEffect(isPillVisible, geometry.hitboxTop, geometry.hitboxBottom) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        try {
                            if (isPillVisible && geometry.isScrollable) {
                                pillLocalView.systemGestureExclusionRects = listOf(
                                    android.graphics.Rect(
                                        geometry.hitboxLeft.toInt(),
                                        geometry.hitboxTop.toInt(),
                                        geometry.hitboxRight.toInt(),
                                        geometry.hitboxBottom.toInt()
                                    )
                                )
                            } else {
                                pillLocalView.systemGestureExclusionRects = emptyList()
                            }
                        } catch (_: Exception) {}
                    }
                    onDispose {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            try {
                                pillLocalView.systemGestureExclusionRects = emptyList()
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Render purely visual high-contrast Apple capsule scrollbar (no full-height touch interceptor Box)
                if (alphaAnim > 0.005f && geometry.isScrollable) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(100f)
                            .graphicsLayer { alpha = alphaAnim }
                    ) {
                        val w = capsuleWidth.toPx()
                        val x = size.width - w - 3.dp.toPx()
                        val thumbY = geometry.thumbY
                        val thumbH = geometry.thumbHeight

                        val isDark = viewModel.isDarkThemeEnabled || viewModel.isAmoledMode
                        val thumbColor = if (isDark) {
                            androidx.compose.ui.graphics.Color(0xFFE5E5EA).copy(
                                alpha = if (viewModel.isFastScrollingPill) 0.95f else 0.75f
                            )
                        } else {
                            androidx.compose.ui.graphics.Color(0xFF2C2C2E).copy(
                                alpha = if (viewModel.isFastScrollingPill) 0.90f else 0.70f
                            )
                        }
                        val borderColor = if (isDark) {
                            androidx.compose.ui.graphics.Color(0xFF000000).copy(
                                alpha = if (viewModel.isFastScrollingPill) 0.50f else 0.30f
                            )
                        } else {
                            androidx.compose.ui.graphics.Color(0xFFFFFFFF).copy(
                                alpha = if (viewModel.isFastScrollingPill) 0.60f else 0.40f
                            )
                        }

                        // Outer outline for maximum contrast against any website background
                        drawRoundRect(
                            color = borderColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x - 0.5f, thumbY - 0.5f),
                            size = androidx.compose.ui.geometry.Size(w + 1f, thumbH + 1f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius((w + 1f) / 2f, (w + 1f) / 2f)
                        )

                        // Capsule body
                        drawRoundRect(
                            color = thumbColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, thumbY),
                            size = androidx.compose.ui.geometry.Size(w, thumbH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
                        )
                    }
                }
            }

            // "Switch account" chip — shown after autofill when multiple passwords exist.
            // Lets the user switch to a different saved credential without reloading.
            val showSwitchChip = viewModel.autofillWasPerformed
                && viewModel.autofillMatches.size > 1
                && saveCred == null
            androidx.compose.animation.AnimatedVisibility(
                visible = showSwitchChip,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
            ) {
                val lastUsed = viewModel.autofillLastUsed
                if (lastUsed != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (viewModel.isAmoledMode) Color(0xFF111827)
                                else MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        onClick = {
                            viewModel.showAutofillBottomSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.autofill_filled_as, lastUsed.username),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.autofill_tap_to_switch),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Dismiss
                            IconButton(
                                onClick = {
                                    viewModel.autofillWasPerformed = false
                                    viewModel.autofillLastUsed = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // QR Overview Dialog
            if (showQrOverviewDialog) {
                FeatureOverviewDialog(
                    title = stringResource(R.string.overview_qr_title),
                    subtitle = stringResource(R.string.overview_qr_subtitle),
                    description = stringResource(R.string.overview_qr_desc),
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
                    title = stringResource(R.string.overview_pdf_title),
                    subtitle = stringResource(R.string.overview_pdf_subtitle),
                    description = stringResource(R.string.overview_pdf_desc),
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

            // Extensions Overview Dialog (shown only from other entry points)
            if (showExtensionsOverviewDialog) {
                FeatureOverviewDialog(
                    title = stringResource(R.string.ext_overview_title),
                    subtitle = stringResource(R.string.ext_overview_subtitle),
                    description = stringResource(R.string.ext_overview_desc),
                    icon = Icons.Rounded.Extension,
                    accentColor = Color(0xFFFF3B5C), // Crimson Red
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showExtensionsOverviewDialog = false
                        viewModel.saveExtensionsOverviewSeen(context, true)
                        showExtensionsSheet = true
                    }
                )
            }
            
            // Edit Page Overview Dialog
            if (showEditPageOverviewDialog) {
                FeatureOverviewDialog(
                    title = stringResource(R.string.overview_edit_title),
                    subtitle = stringResource(R.string.overview_edit_subtitle),
                    description = stringResource(R.string.overview_edit_desc),
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
                    title = stringResource(R.string.overview_console_title),
                    subtitle = stringResource(R.string.overview_console_subtitle),
                    description = stringResource(R.string.overview_console_desc),
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
                    title = stringResource(R.string.overview_devnotes_title),
                    subtitle = stringResource(R.string.overview_devnotes_subtitle),
                    description = stringResource(R.string.overview_devnotes_desc),
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

            // External app redirect dialog (auto-redirects blocked by default until user decides)
            viewModel.pendingExternalAppRequest?.let { request ->
                ExternalAppRedirectDialog(
                    request = request,
                    viewModel = viewModel,
                    context = context,
                    onDismiss = { viewModel.pendingExternalAppRequest = null }
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
                        text = stringResource(R.string.browser_incognito_locked),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.browser_incognito_auth_desc),
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
                        Text(stringResource(R.string.browser_unlock), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }


        // Media Sniffer Settings dialog (rendered at root of BrowserScreen)
        if (viewModel.showMediaSnifferSettingsDialog) {
            MediaSnifferSettingsDialog(
                viewModel = viewModel,
                onDismissRequest = { viewModel.showMediaSnifferSettingsDialog = false }
            )
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
fun GridMenuTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isBurnData: Boolean = false,
    isDark: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (isBurnData) Color(0xFF3D1416) else if (isDark) Color(0xFF24252A) else Color(0xFFF0F0F2)
    val iconTint = if (isBurnData) Color(0xFFEF5350) else if (isDark) Color(0xFFE5E5EA) else Color(0xFF202124)
    val textColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF202124)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(66.dp)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExitOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
    titleColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "exit_row_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
            )
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF666666),
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.25f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Choice Prompt Dialog (for <select> elements) — Issue #74 ────────────────────
/**
 * Renders a native AlertDialog with a radio-list or checkbox-list of choices,
 * matching the GeckoView ChoicePrompt specification.
 */
@Composable
fun ChoicePromptDialog(
    title: String,
    choices: List<org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
    initialSelectedIndex: Int = 0,
    isMultiple: Boolean = false,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean = false
) {
    val selectedIndex = remember { mutableStateOf(initialSelectedIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color.Black,
                maxLines = 3
            )
        },
        text = {
            LazyColumn {
                itemsIndexed(choices) { index, choice ->
                    if (choice.separator) {
                        Divider(
                            color = if (isDarkTheme) Color(0xFF333333) else Color(0xFFE0E0E0),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !choice.disabled) {
                                    selectedIndex.value = index
                                    if (!isMultiple) {
                                        onConfirm(index)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .alpha(if (choice.disabled) 0.4f else 1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Radio or checkbox indicator
                            if (!choice.disabled) {
                                if (isMultiple) {
                                    Checkbox(
                                        checked = selectedIndex.value == index,
                                        onCheckedChange = {
                                            selectedIndex.value = index
                                        }
                                    )
                                } else {
                                    RadioButton(
                                        selected = selectedIndex.value == index,
                                        onClick = null
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = choice.label,
                                color = if (isDarkTheme) Color.White else Color.Black,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // Icon if available
                            if (!choice.icon.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                coil.compose.AsyncImage(
                                    model = choice.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isMultiple) {
                Button(
                    onClick = { onConfirm(selectedIndex.value) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(32.dp)
    )
}

/**
 * Format a timestamp as a human-readable relative string (e.g. "2h ago", "Yesterday").
 */
private fun formatRelativeTime(timestamp: Long, now: Long): String {
    val diffMs = now - timestamp
    if (diffMs < 0) return "Just now"
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            fmt.format(java.util.Date(timestamp))
        }
    }
}

@Composable
private fun MediaSnifferBanner(
    viewModel: BrowserViewModel,
    nonDrmMedia: List<com.rebelroot.omni.media.MediaInterceptor.DetectedMedia>,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showBlockConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentHost = remember(viewModel.currentUrl) {
        try {
            android.net.Uri.parse(viewModel.currentUrl).host ?: ""
        } catch (_: Exception) { "" }
    }
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
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_dismiss),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (viewModel.isVideoPlayingInPage) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                ) {
                    EqualizerIcon(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_video_detected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val hasOnlyAudio = nonDrmMedia.all { it.type == com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO }
            val bannerText = when {
                viewModel.isVideoPlayingInPage && hasOnlyAudio -> androidx.compose.ui.res.stringResource(R.string.media_sniffer_banner_audio_playing)
                viewModel.isVideoPlayingInPage -> androidx.compose.ui.res.stringResource(R.string.media_sniffer_banner_video_playing)
                hasOnlyAudio -> androidx.compose.ui.res.stringResource(R.string.media_sniffer_banner_audio_detected)
                else -> androidx.compose.ui.res.stringResource(R.string.media_sniffer_banner_media_detected)
            }
            Text(
                text = bannerText,
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
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_play_premium),
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
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_download_options),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.media_sniffer_settings_title),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (currentHost.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showBlockConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.media_sniffer_block_site),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showBlockConfirm && currentHost.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.media_sniffer_block_confirm_title),
                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.media_sniffer_block_confirm_message, currentHost),
                    color = if (viewModel.isDarkThemeEnabled) Color(0xFFC5D1DE) else Color.DarkGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addDomainToMediaSnifferBlocklist(context, currentHost)
                        showBlockConfirm = false
                        onDismiss()
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.media_sniffer_blocked_toast, currentHost),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.media_sniffer_block_confirm_block),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.media_sniffer_block_confirm_cancel),
                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color.Gray
                    )
                }
            },
            containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
