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

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.browser.extensions.UniversalCopyManager
import com.rebelroot.omni.browser.extensions.AiBlockerManager
import com.rebelroot.omni.media.FFmpegBridge
import com.rebelroot.omni.media.FFmpegLoader
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.media.StreamDownloadEngine
import com.rebelroot.omni.privacy.VpnManager
import com.rebelroot.omni.tools.locker.PrivateLockerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import com.rebelroot.omni.tools.qrcode.QrCodeDecoder
import java.lang.ref.WeakReference
import java.io.File
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.speech.tts.TextToSpeech
import java.util.UUID
import kotlinx.coroutines.Dispatchers

val Context.dataStore by preferencesDataStore(name = "omni_settings")

data class TabState(
    val id: String,
    val session: GeckoSession,
    val title: String,
    val url: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadError: String? = null,
    val isEditModeEnabled: Boolean = false,
    val settingsVersion: Int = 0,
    val isUriLoaded: Boolean = true,
    val isIncognito: Boolean = false
)


data class BookmarkEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

data class HomeShortcut(
    val id: String,
    val title: String,
    val url: String,
    val isFeature: Boolean = false,
    val isPermanent: Boolean = false
)

data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val pubDate: String,
    val imageUrl: String
)

data class HistoryEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

data class ContentPermissionPrompt(
    val siteUri: String,
    val permissionType: Int,
    val onAllow: () -> Unit,
    val onDeny: () -> Unit
)

data class SystemPermissionRequest(
    val permissions: Array<String>?,
    val onGranted: () -> Unit,
    val onDenied: () -> Unit
)

data class MediaPermissionPrompt(
    val siteUri: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val onAllow: (videoSource: org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource?, audioSource: org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource?) -> Unit,
    val onDeny: () -> Unit
)

class BrowserViewModel : ViewModel() {

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val GRABBER_ID = "omni-media-grabber@omnibrowser.app"
        private const val AI_BLOCKER_ID = "omni-ai-blocker@omnibrowser.app"

        /** Known ad popup / pop-under network domains.
         *  Popups opening any of these URLs are silently blocked regardless of user gesture. */
        private val POPUP_AD_DOMAINS = setOf(
            "popads.net", "popcash.net", "popunder.net",
            "exoclick.com", "trafficjunky.net", "juicyads.com",
            "adsterra.com", "propellerads.com", "hilltopads.net",
            "clickadu.com", "evadav.com", "megapush.com",
            "push.house", "richpush.co", "pushground.com",
            "mgpusher.com", "pu.sh", "adf.ly", "j.gs",
            "link.tl", "linkvertise.com", "hrefli.com",
            "trafficfactory.biz", "tsyndicate.com", "doublelift.net",
            "adskeeper.com", "voluumtrk.com", "atominik.com",
            "mondoagency.net", "trafficshop.com", "adnxs.com",
            "openx.net", "rubiconproject.com", "doubleclick.net",
            "googlesyndication.com"
        )
        
        val UBLOCK_ENABLED_KEY = booleanPreferencesKey("ublock_adblocker_enabled")
        val POPUP_BLOCKER_ENABLED_KEY = booleanPreferencesKey("popup_blocker_enabled")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
        val AI_BLOCKER_ENABLED_KEY = booleanPreferencesKey("ai_blocker_enabled")
        val NATIVE_PLAYER_ENABLED_KEY = booleanPreferencesKey("native_player_enabled")
        val MEDIA_GRABBER_ENABLED_KEY = booleanPreferencesKey("media_grabber_enabled")
        val CUSTOM_VPN_CONFIG_KEY = stringPreferencesKey("custom_vpn_config")
        val SEARCH_ENGINE_KEY = stringPreferencesKey("default_search_engine")
        val CUSTOM_SEARCH_URL_KEY = stringPreferencesKey("custom_search_url")
        val DARK_THEME_ENABLED_KEY = booleanPreferencesKey("dark_theme_enabled")
        val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme")
        val PDF_EXPORT_THEME_KEY = stringPreferencesKey("pdf_export_theme")
        val SELECTED_LANGUAGE_KEY = stringPreferencesKey("selected_language")
        val LANGUAGE_SELECTION_DONE_KEY = booleanPreferencesKey("language_selection_done")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val QR_OVERVIEW_SEEN_KEY = booleanPreferencesKey("qr_overview_seen")
        val PDF_OVERVIEW_SEEN_KEY = booleanPreferencesKey("pdf_overview_seen")
        val VIDEO_OVERVIEW_SEEN_KEY = booleanPreferencesKey("video_overview_seen")
        val EXTENSIONS_OVERVIEW_SEEN_KEY = booleanPreferencesKey("extensions_overview_seen")
        val EDIT_PAGE_OVERVIEW_SEEN_KEY = booleanPreferencesKey("edit_page_overview_seen")
        val CONSOLE_OVERVIEW_SEEN_KEY = booleanPreferencesKey("console_overview_seen")
        val FORCE_DARK_WEBSITES_KEY = booleanPreferencesKey("force_dark_websites")
        val NAV_BAR_HIDE_TOP_KEY = booleanPreferencesKey("nav_bar_hide_top")
        val NAV_BAR_HIDE_BOTTOM_KEY = booleanPreferencesKey("nav_bar_hide_bottom")
        val ADDRESS_BAR_POSITION_KEY = stringPreferencesKey("address_bar_position")
        val APP_ICON_STATE_KEY = stringPreferencesKey("app_icon_state")
        val BROWSER_WALLPAPER_URI_KEY = stringPreferencesKey("browser_wallpaper_uri")
        val CHANGE_WALLPAPER_DAILY_KEY = booleanPreferencesKey("change_wallpaper_daily")
        val SHOW_DISCOVER_FEED_KEY = booleanPreferencesKey("show_discover_feed")
        val SHOW_BOTTOM_NAV_BAR_KEY = booleanPreferencesKey("show_bottom_nav_bar")



        // Native Player Settings Keys
        val PLAYER_DEFAULT_QUALITY_KEY = stringPreferencesKey("player_default_quality")
        val PLAYER_AUTOPLAY_KEY = booleanPreferencesKey("player_autoplay")
        val PLAYER_LOOP_KEY = booleanPreferencesKey("player_loop")
        val PLAYER_BRIGHTNESS_GESTURE_KEY = booleanPreferencesKey("player_brightness_gesture")
        val PLAYER_VOLUME_GESTURE_KEY = booleanPreferencesKey("player_volume_gesture")
        val PLAYER_RESUME_PLAYBACK_KEY = booleanPreferencesKey("player_resume_playback")
        val PLAYER_BACKGROUND_PLAYBACK_KEY = booleanPreferencesKey("player_background_playback")
        val DEV_NOTES_OVERVIEW_SEEN_KEY = booleanPreferencesKey("dev_notes_overview_seen")

        @Volatile
        private var geckoRuntime: GeckoRuntime? = null
    }

    // Engine Session & Runtime
    var geckoSession by mutableStateOf(GeckoSession())
        private set
    var isIncognitoMode by mutableStateOf(false)
        private set

    val runtime: GeckoRuntime? get() = geckoRuntime

    // Extension Action System (Compose-friendly maps & states)
    val extensionActions = mutableStateMapOf<String, WebExtension.Action>()
    val defaultExtensionActions = mutableStateMapOf<String, WebExtension.Action>()
    val sessionExtensionActions = mutableStateMapOf<String, MutableMap<String, WebExtension.Action>>()
    var activeExtensionPopupSession by mutableStateOf<GeckoSession?>(null)
    var activeExtensionPopupName by mutableStateOf("")

    fun registerExtensionAction(id: String, session: GeckoSession?, action: WebExtension.Action) {
        extensionActions[id] = action
        if (session != null) {
            val tab = tabs.find { it.session == session }
            if (tab != null) {
                val extMap = sessionExtensionActions.getOrPut(tab.id) { mutableMapOf() }
                extMap[id] = action
            }
        } else {
            defaultExtensionActions[id] = action
        }
    }

    fun getActionForExtension(extensionId: String): WebExtension.Action? {
        val activeId = activeTabId ?: return defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
        return sessionExtensionActions[activeId]?.get(extensionId) ?: defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
    }


    fun handleExtensionOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
        val result = GeckoResult<GeckoSession>()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val run = geckoRuntime
            if (run == null) {
                result.completeExceptionally(IllegalStateException("GeckoRuntime not ready"))
                return@post
            }
            activeExtensionPopupSession?.close() // close previous popup session to avoid leaks
            val session = GeckoSession()
            session.open(run)
            activeExtensionPopupSession = session
            activeExtensionPopupName = extension.metaData?.name ?: extension.id
            result.complete(session)
        }
        return result
    }

    fun dismissExtensionPopup() {
        activeExtensionPopupSession?.close()
        activeExtensionPopupSession = null
        activeExtensionPopupName = ""
    }
    var pendingIntentUrl: String? = null
    private var isViewModelInitialized = false

    // Real Tab System
    val tabs = mutableStateListOf<TabState>()
    var activeTabId by mutableStateOf<String?>(null)
        private set
    var activeNormalTabId by mutableStateOf<String?>(null)
        private set
    var activeIncognitoTabId by mutableStateOf<String?>(null)
        private set


    // Context Menu State
    var activeContextMenu by mutableStateOf<ContextMenuElement?>(null)
        private set

    // Text Selection State
    var activeTextSelection by mutableStateOf<String?>(null)
        private set
    var activeSelectionObject by mutableStateOf<org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.Selection?>(null)


    // Browser History System
    val historyList = mutableStateListOf<HistoryEntry>()

    // Feature Modules
    val mediaInterceptor = MediaInterceptor()
    lateinit var ffmpegLoader: FFmpegLoader
    lateinit var ffmpegBridge: FFmpegBridge
    lateinit var streamDownloadEngine: StreamDownloadEngine
    lateinit var vpnManager: VpnManager
    val translationManager = com.rebelroot.omni.tools.TranslationManager()
    private var copyManager: UniversalCopyManager? = null
    private var aiBlockerManager: AiBlockerManager? = null
    private var appContext: Context? = null

    // GeckoView Reference for capturePixels
    private var activeGeckoViewRef: WeakReference<GeckoView>? = null

    fun setActiveGeckoView(geckoView: GeckoView) {
        activeGeckoViewRef = WeakReference(geckoView)
    }

    fun clearActiveGeckoView() {
        activeGeckoViewRef = null
    }

    // QR Page Scan States
    var isQrScanning by mutableStateOf(false)
    var qrScanResults by mutableStateOf<List<String>>(emptyList())
    var qrScanError by mutableStateOf<String?>(null)

    // Feature Overview Seen States
    var hasSeenQrOverview by mutableStateOf(false)
    var hasSeenPdfOverview by mutableStateOf(false)
    var hasSeenVideoOverview by mutableStateOf(false)
    var hasSeenExtensionsOverview by mutableStateOf(false)
    var hasSeenEditPageOverview by mutableStateOf(false)
    var hasSeenConsoleOverview by mutableStateOf(false)
    var hasSeenDevNotesOverview by mutableStateOf(false)

    // UI States
    var currentUrl by mutableStateOf("about:blank")
    var isFullscreen by mutableStateOf(false)
    var isVideoPlayingInPage by mutableStateOf(false)
    var isAdblockerEnabled by mutableStateOf(true)
    /** When true (default), popup windows not triggered by a real user tap are blocked. */
    var isPopupBlockerEnabled by mutableStateOf(true)
    var isUniversalCopyEnabled by mutableStateOf(false)
    var isAiBlockerEnabled by mutableStateOf(false)
    var isMediaGrabberEnabled by mutableStateOf(true)
    var isNativePlayerEnabled by mutableStateOf(true)
    var pendingVideoUrl: String? = null
    var customVpnConfig by mutableStateOf<String?>(null)
    var selectedSearchEngine by mutableStateOf("Google")
    var customSearchUrl by mutableStateOf("")
    var isDarkThemeEnabled by mutableStateOf(true)
    var selectedLanguageCode by mutableStateOf("en")
    var isLanguageSelectionDone by mutableStateOf(false)
    var isOnboardingCompleted by mutableStateOf(false)
    var selectedAccentTheme by mutableStateOf("Ocean Blue")
    var forceDarkWebsites by mutableStateOf(false)
    var navBarHideTop by mutableStateOf(true)
    var navBarHideBottom by mutableStateOf(true)
    var addressBarPosition by mutableStateOf("Top")
    var appIconState by mutableStateOf("Default")
    var browserWallpaperUri by mutableStateOf<String?>(null)
    var changeWallpaperDaily by mutableStateOf(false)
    var showDiscoverFeed by mutableStateOf(true)
    var showBottomNavBar by mutableStateOf(true)



    // --- Custom Site Style Config ---
    var siteStyleFontSize by mutableStateOf(100)
    var siteStyleTheme by mutableStateOf("DEFAULT")
    var siteStyleLineSpacing by mutableStateOf(1.4f)
    var siteStyleLetterSpacing by mutableStateOf(0f)
    var siteStyleFontFamily by mutableStateOf("inherit")
    var siteStyleAppliedGlobally by mutableStateOf(false)
    var pdfExportTheme by mutableStateOf("default")
    var isReaderModeActive by mutableStateOf(false)
    var readerFontSize by mutableStateOf(18)
    var readerTheme by mutableStateOf("Light")
    var readerFontFamily by mutableStateOf("System")
    var readerLineHeight by mutableStateOf(1.6f)
    var readerWidth by mutableStateOf("Medium")
    var readerLetterSpacing by mutableStateOf("Normal")
    var readerWordSpacing by mutableStateOf("Normal")
    var readerJustified by mutableStateOf(false)

    // Native Player Settings
    var playerDefaultQuality by mutableStateOf("Auto")
    var isPlayerAutoPlayEnabled by mutableStateOf(true)
    var isPlayerLoopEnabled by mutableStateOf(false)
    var isPlayerBrightnessGestureEnabled by mutableStateOf(true)
    var isPlayerVolumeGestureEnabled by mutableStateOf(true)
    var isPlayerResumePlaybackEnabled by mutableStateOf(true)
    var isPlayerBackgroundPlaybackEnabled by mutableStateOf(false)

    var isAdblockerToggling by mutableStateOf(false)
    var isUniversalCopyToggling by mutableStateOf(false)
    var isAiBlockerToggling by mutableStateOf(false)
    var isMediaGrabberToggling by mutableStateOf(false)
    val togglingUserExtensionIds = mutableStateListOf<String>()
    var currentSettingsVersion by mutableStateOf(0)

    // Navigation event: set true when an external link intent should open the browser screen
    var openBrowserScreenEvent by mutableStateOf(false)
    fun triggerOpenBrowserScreen() { openBrowserScreenEvent = true }
    fun consumeOpenBrowserScreenEvent() { openBrowserScreenEvent = false }

    // QR code scanner support
    fun scanPageForQrCodes() {
        val geckoView = activeGeckoViewRef?.get()
        if (geckoView == null) {
            qrScanError = "No active page to scan"
            return
        }

        isQrScanning = true
        qrScanResults = emptyList()
        qrScanError = null

        geckoView.capturePixels().then { bitmap ->
            if (bitmap == null) {
                viewModelScope.launch(Dispatchers.Main) {
                    isQrScanning = false
                    qrScanError = "Could not capture page"
                }
                return@then GeckoResult.fromValue(false)
            }

            // Run FOSS ZXing barcode detection on the captured bitmap
            val result = QrCodeDecoder.decodeBitmap(bitmap)
            val results = if (result != null) listOf(result) else emptyList()
            viewModelScope.launch(Dispatchers.Main) {
                isQrScanning = false
                qrScanResults = results
                if (results.isEmpty()) {
                    qrScanError = "No QR codes found on this page"
                }
            }

            GeckoResult.fromValue(true)
        }.exceptionally { throwable ->
            viewModelScope.launch(Dispatchers.Main) {
                isQrScanning = false
                qrScanError = "Capture failed: ${throwable.localizedMessage}"
            }
            GeckoResult.fromValue(false)
        }
    }

    fun scanImageForQrCodes(context: Context, uri: Uri) {
        isQrScanning = true
        qrScanResults = emptyList()
        qrScanError = null

        try {
            val result = QrCodeDecoder.decodeUri(context, uri)
            val results = if (result != null) listOf(result) else emptyList()
            viewModelScope.launch(Dispatchers.Main) {
                isQrScanning = false
                qrScanResults = results
                if (results.isEmpty()) {
                    qrScanError = "No QR codes found in this image"
                }
            }
        } catch (e: Exception) {
            isQrScanning = false
            qrScanError = "Failed to load image: ${e.localizedMessage}"
        }
    }

    fun applySiteStyleToActiveTab() {
        val session = geckoSession
        val hasCustomStyles = siteStyleTheme != "DEFAULT" || siteStyleFontSize != 100 || siteStyleLineSpacing != 1.4f || siteStyleLetterSpacing != 0f || siteStyleFontFamily != "inherit"

        val bgValue = when (siteStyleTheme) {
            "DARK" -> "#0B131E"
            "SEPIA" -> "#F4ECD8"
            "OLED" -> "#000000"
            "FOREST" -> "#E6F0E6"
            else -> null
        }
        val textValue = when (siteStyleTheme) {
            "DARK" -> "#E2E8F0"
            "SEPIA" -> "#5C4033"
            "OLED" -> "#E5E7EB"
            "FOREST" -> "#1E3F20"
            else -> null
        }
        
        val fontCss = if (siteStyleFontFamily != "inherit") "font-family: ${siteStyleFontFamily} !important;" else ""
        val bgCss = if (bgValue != null) "background-color: ${bgValue} !important; background: ${bgValue} !important;" else ""
        val textCss = if (textValue != null) "color: ${textValue} !important;" else ""
        val sizeCss = "font-size: ${siteStyleFontSize}% !important;"
        val lineSpacingCss = "line-height: ${siteStyleLineSpacing} !important;"
        val letterSpacingCss = "letter-spacing: ${siteStyleLetterSpacing}px !important;"

        val cssRules = if (hasCustomStyles) {
            """
            html, body, p, span, div, h1, h2, h3, h4, h5, h6, li, a, section, article {
                $bgCss
                $textCss
                $fontCss
                $sizeCss
                $lineSpacingCss
                $letterSpacingCss
            }
            """.trimIndent().replace("\n", " ").replace("'", "\\'")
        } else {
            ""
        }

        val js = """
            javascript:(function() {
                const styleId = 'omni-custom-site-style';
                let styleEl = document.getElementById(styleId);
                if ('$cssRules' === '') {
                    if (styleEl) {
                        styleEl.remove();
                    }
                } else {
                    if (!styleEl) {
                        styleEl = document.createElement('style');
                        styleEl.id = styleId;
                        document.head.appendChild(styleEl);
                    }
                    styleEl.innerHTML = '$cssRules';
                }
            })();
        """.trimIndent()
        
        session.loadUri(js)
    }

    fun updateSiteStyle(
        fontSize: Int,
        theme: String,
        lineSpacing: Float,
        letterSpacing: Float,
        fontFamily: String,
        appliedGlobally: Boolean
    ) {
        siteStyleFontSize = fontSize
        siteStyleTheme = theme
        siteStyleLineSpacing = lineSpacing
        siteStyleLetterSpacing = letterSpacing
        siteStyleFontFamily = fontFamily
        siteStyleAppliedGlobally = appliedGlobally

        val context = appContext ?: return
        val sp = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
        sp.edit().apply {
            putInt("site_style_font_size", fontSize)
            putString("site_style_theme", theme)
            putFloat("site_style_line_spacing", lineSpacing)
            putFloat("site_style_letter_spacing", letterSpacing)
            putString("site_style_font_family", fontFamily)
            putBoolean("site_style_applied_globally", appliedGlobally)
        }.apply()

        applySiteStyleToActiveTab()
    }

    fun resetSiteStyle() {
        updateSiteStyle(
            fontSize = 100,
            theme = "DEFAULT",
            lineSpacing = 1.4f,
            letterSpacing = 0f,
            fontFamily = "inherit",
            appliedGlobally = false
        )
    }

    fun clearQrScanResults() {
        qrScanResults = emptyList()
        qrScanError = null
    }

    /**
     * Extracts the S.browser_fallback_url from an intent:// URI.
     * Payment gateways (Razorpay, PayU, etc.) embed this URL so browsers
     * can redirect users to a web fallback when the target app isn't installed.
     *
     * Format: intent://...;S.browser_fallback_url=https%3A%2F%2Fexample.com;end
     */
    private fun extractFallbackUrl(intentUri: String): String? {
        return try {
            // The fallback URL is stored as an "extra" in the intent URI
            val intent = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank() && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                fallback
            } else {
                // Manual extraction as a backup (some gateways use non-standard encoding)
                val regex = Regex("[;?&]S\\.browser_fallback_url=([^;&#]+)", RegexOption.IGNORE_CASE)
                val match = regex.find(intentUri)
                val url = match?.groupValues?.get(1)
                if (url != null) java.net.URLDecoder.decode(url, "UTF-8") else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fallback URL from intent URI", e)
            null
        }
    }

    fun isDirectVideoUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.contains("autoplay=native") ||
                clean.endsWith(".mp4") ||
                clean.endsWith(".m3u8") ||
                clean.endsWith(".mpd") ||
                clean.endsWith(".webm") ||
                clean.endsWith(".mkv") ||
                clean.endsWith(".ts") ||
                clean.contains(".mp4?") ||
                clean.contains(".m3u8?") ||
                clean.contains(".mpd?") ||
                clean.contains(".webm?") ||
                clean.contains(".mkv?") ||
                clean.contains(".ts?")
    }

    private fun parseFilenameFromContentDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        val regex = Regex("""filename\*?=(?:UTF-8''?)?\"?([^\";]+)\"?""", RegexOption.IGNORE_CASE)
        val match = regex.find(disposition)
        return match?.groupValues?.get(1)?.trim()?.trim('"')
    }

    private fun guessDownloadFilename(url: String, contentType: String?): String {
        val parsed = try {
            Uri.parse(url).lastPathSegment
        } catch (e: Exception) {
            null
        }
        if (!parsed.isNullOrBlank() && parsed.contains('.')) {
            return parsed
        }
        return when {
            contentType?.contains("pdf", true) == true -> "download.pdf"
            contentType?.contains("zip", true) == true -> "download.zip"
            contentType?.contains("msword", true) == true || contentType?.contains("wordprocessingml.document", true) == true -> "download.docx"
            contentType?.contains("excel", true) == true || contentType?.contains("spreadsheetml.sheet", true) == true -> "download.xlsx"
            contentType?.contains("presentation", true) == true || contentType?.contains("presentationml.presentation", true) == true -> "download.pptx"
            contentType?.contains("text/plain", true) == true -> "download.txt"
            else -> "downloaded-file"
        }
    }

    private fun isGenericDownloadUrl(url: String): Boolean {
        val path = url.substringBeforeLast("?").substringAfterLast("/")
        if (path.isBlank() || !path.contains('.')) return false
        val ext = path.substringAfterLast('.').lowercase()
        return ext in setOf(
            "apk", "pdf", "zip", "rar", "7z", "tar", "gz", "tgz", "bin", "exe", "epub",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "txt", "rtf", "xml", "json",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "mp3", "wav", "flac",
            "m4a", "aac", "ogg", "mp4", "mkv", "webm", "ts", "mov", "avi", "flv"
        )
    }

    // Download interceptor data struct
    data class PendingGenericDownload(
        val url: String,
        val filename: String,
        val contentType: String?
    )
    var pendingGenericDownload by mutableStateOf<PendingGenericDownload?>(null)

    fun startGenericDownload(download: PendingGenericDownload, saveToLocker: Boolean) {
        pendingGenericDownload = null
        streamDownloadEngine.startGenericFileDownload(
            url = download.url,
            filename = download.filename,
            contentType = download.contentType,
            saveToLocker = saveToLocker
        )
    }

    private fun handleExternalDownloadResponse(response: org.mozilla.geckoview.WebResponse, context: Context) {
        val headers = response.headers
        val disposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        val contentType = headers["Content-Type"] ?: headers["content-type"]
        if (response.requestExternalApp || disposition?.contains("attachment", true) == true) {
            Log.i(TAG, "Handling external download response: ${response.uri}")
            val filename = parseFilenameFromContentDisposition(disposition)
                ?: guessDownloadFilename(response.uri, contentType)
            viewModelScope.launch(Dispatchers.Main) {
                pendingGenericDownload = PendingGenericDownload(
                    url = response.uri,
                    filename = filename,
                    contentType = contentType
                )
            }
        }
    }

    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isDesktopMode by mutableStateOf(false)
        private set

    // Permissions System
    var activePermissionPrompt by mutableStateOf<ContentPermissionPrompt?>(null)
    var activeSystemPermissionRequest by mutableStateOf<SystemPermissionRequest?>(null)
    var activeMediaPermissionPrompt by mutableStateOf<MediaPermissionPrompt?>(null)
        private set

    fun clearActiveSystemPermissionRequest() {
        activeSystemPermissionRequest = null
    }

    fun clearActivePermissionPrompt() {
        activePermissionPrompt = null
    }

    fun clearActiveMediaPermissionPrompt() {
        activeMediaPermissionPrompt = null
    }

    fun dismissTextSelection() {
        activeTextSelection = null
        activeSelectionObject = null
    }

    fun speakSelectedText(context: Context) {
        val text = activeTextSelection ?: return
        initTts(context)
        speakText(text)
        dismissTextSelection()
    }


    fun selectAllText() {
        val selection = activeSelectionObject
        if (selection != null) {
            try {
                selection.execute(org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing SELECT_ALL action", e)
                // Fallback to JS Selection API
                try {
                    geckoSession.loadUri("javascript:window.getSelection()?.selectAllChildren(document.body);")
                } catch (jsEx: Exception) {
                    Log.e(TAG, "Error fallback selectAll JS", jsEx)
                }
            }
        } else {
            // Fallback to evaluating JS selectall command
            try {
                geckoSession.loadUri("javascript:window.getSelection()?.selectAllChildren(document.body);")
            } catch (e: Exception) {
                Log.e(TAG, "Error fallback selectAll JS", e)
            }
        }
    }



    fun copySelectedText(context: Context) {
        val text = activeTextSelection ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Selected Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
        dismissTextSelection()
    }

    // Web Video Play Takeover Lambda
    var onPlayVideoRequestReceived: ((String, String) -> Unit)? = null

    // File Upload: holds all info needed for the UI to launch a file picker and
    // then deliver the selected URIs back into the GeckoSession engine.
    data class PendingFilePrompt(
        val geckoResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        val prompt: GeckoSession.PromptDelegate.FilePrompt,
        val allowMultiple: Boolean,
        val mimeTypes: Array<String>?
    )
    var pendingFilePrompt by mutableStateOf<PendingFilePrompt?>(null)

    fun deliverFilePickerResult(uris: List<android.net.Uri>) {
        val pending = pendingFilePrompt ?: return
        pendingFilePrompt = null
        if (uris.isEmpty()) {
            pending.geckoResult.complete(pending.prompt.dismiss())
        } else {
            val ctx = appContext
            if (ctx == null) {
                // No context — can't resolve content URIs; dismiss gracefully
                pending.geckoResult.complete(pending.prompt.dismiss())
            } else {
                pending.geckoResult.complete(
                    pending.prompt.confirm(ctx, uris.toTypedArray())
                )
            }
        }
    }

    fun cancelFilePrompt() {
        val pending = pendingFilePrompt ?: return
        pendingFilePrompt = null
        pending.geckoResult.complete(pending.prompt.dismiss())
    }

    // Extensions References
    private var uBlockExtension: WebExtension? = null
    private var grabberExtension: WebExtension? = null
    
    val userExtensions = mutableStateListOf<WebExtension>()
    
    // Console Logs
    val consoleLogs = mutableStateListOf<ConsoleLogEntry>()
    var pendingJsCommand by mutableStateOf<String?>(null)
    
    data class ConsoleLogEntry(
        val level: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DevNote(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String,
        val content: String,
        val type: String, // "NOTE", "CODE", "KEY", "PASSWORD", "URL"
        val timestamp: Long = System.currentTimeMillis()
    )

    val devNotes = mutableStateListOf<DevNote>()

    // --- Tab Management ---
    fun saveTabs() {
        val context = appContext ?: return
        val tabsSnapshot = tabs.toList()
        val currentActiveId = activeTabId
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_tabs.json")
            try {
                val jsonArray = org.json.JSONArray()
                tabsSnapshot.forEach { tab ->
                    val obj = org.json.JSONObject().apply {
                        put("id", tab.id)
                        put("title", tab.title)
                        put("url", tab.url)
                        put("isActive", tab.id == currentActiveId)
                        put("isIncognito", tab.isIncognito)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving tabs", e)
            }
        }
    }


    fun loadDevNotes(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "dev_notes.json")
            if (file.exists()) {
                try {
                    val jsonStr = file.readText()
                    val jsonArray = org.json.JSONArray(jsonStr)
                    val loadedList = mutableListOf<DevNote>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        loadedList.add(
                            DevNote(
                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                title = obj.optString("title", ""),
                                content = obj.optString("content", ""),
                                type = obj.optString("type", "NOTE"),
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        devNotes.clear()
                        devNotes.addAll(loadedList)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading dev notes", e)
                }
            }
        }
    }

    fun saveDevNotes() {
        val context = appContext ?: return
        val listSnapshot = devNotes.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "dev_notes.json")
            try {
                val jsonArray = org.json.JSONArray()
                listSnapshot.forEach { note ->
                    val obj = org.json.JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("type", note.type)
                        put("timestamp", note.timestamp)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving dev notes", e)
            }
        }
    }

    fun addDevNote(title: String, content: String, type: String) {
        val note = DevNote(title = title, content = content, type = type)
        devNotes.add(0, note)
        saveDevNotes()
    }

    fun updateDevNote(id: String, title: String, content: String, type: String) {
        val idx = devNotes.indexOfFirst { it.id == id }
        if (idx != -1) {
            devNotes[idx] = devNotes[idx].copy(title = title, content = content, type = type, timestamp = System.currentTimeMillis())
            saveDevNotes()
        }
    }

    fun deleteDevNote(id: String) {
        devNotes.removeAll { it.id == id }
        saveDevNotes()
    }

    fun initTabs(context: Context) {
        if (tabs.isEmpty()) {
            val file = File(context.filesDir, "browser_tabs.json")
            var loaded = false
            if (file.exists()) {
                try {
                    val jsonStr = file.readText()
                    val jsonArray = org.json.JSONArray(jsonStr)
                    var activeId: String? = null
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val title = obj.getString("title")
                        val url = obj.getString("url")
                        val isActive = obj.optBoolean("isActive", false)
                        val isIncognito = obj.optBoolean("isIncognito", false)
                        
                        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                            .usePrivateMode(isIncognito)
                            .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                            .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                            .allowJavascript(true)
                            .build()
                        val session = GeckoSession(settings)
                        
                        val shouldLoadNow = isActive || (url == "about:blank" || url.isEmpty())
                        
                        val tab = TabState(
                            id = id,
                            session = session,
                            title = title,
                            url = url,
                            isIncognito = isIncognito,
                            isUriLoaded = shouldLoadNow
                        )
                        setupTabSessionListeners(tab, context)
                        tabs.add(tab)
                        session.open(getGeckoRuntime(context))
                        
                        if (shouldLoadNow && url != "about:blank" && url.isNotEmpty()) {
                            session.loadUri(url)
                        }
                        
                        if (isActive) {
                            activeId = id
                            if (isIncognito) {
                                activeIncognitoTabId = id
                            } else {
                                activeNormalTabId = id
                            }
                        }
                    }
                    if (tabs.isNotEmpty()) {
                        val activeNormalTab = tabs.find { !it.isIncognito }
                        val activeIncognitoTab = tabs.find { it.isIncognito }
                        activeNormalTabId = activeNormalTabId ?: activeNormalTab?.id
                        activeIncognitoTabId = activeIncognitoTabId ?: activeIncognitoTab?.id
                        
                        val targetId = activeId ?: tabs.first().id
                        selectTab(targetId)
                        loaded = true
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error loading saved tabs", e)
                }
            }
            
            if (loaded) {
                val urlToLoad = pendingIntentUrl
                if (urlToLoad != null) {
                    pendingIntentUrl = null
                    val activeTab = tabs.find { it.id == activeTabId }
                    if (activeTab != null && (activeTab.url == "about:blank" || activeTab.url.isEmpty())) {
                        loadUrlInTab(activeTab, urlToLoad)
                    } else {
                        createNewTab(context, urlToLoad)
                    }
                }
            } else {
                val urlToLoad = pendingIntentUrl ?: "about:blank"
                pendingIntentUrl = null
                createNewTab(context, urlToLoad)
            }
        }
    }

    fun createNewTab(context: Context, url: String) {
        val runtime = getGeckoRuntime(context)
        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognitoMode)
            .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .allowJavascript(true)
            .build()
        val session = GeckoSession(settings)
        val tabId = java.util.UUID.randomUUID().toString()
        val newTab = TabState(
            id = tabId,
            session = session,
            title = "New Tab",
            url = url,
            isIncognito = isIncognitoMode
        )
        
        setupTabSessionListeners(newTab, context)
        tabs.add(newTab)
        session.open(runtime)
        selectTab(newTab.id)
        loadUrlInTab(newTab, url)
        saveTabs()
    }

    fun dismissContextMenu() {
        activeContextMenu = null
    }

    fun selectTab(tabId: String) {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return
        val tab = tabs[tabIndex]
        val oldSession = geckoSession
        activeTabId = tabId
        geckoSession = tab.session
        currentUrl = tab.url
        isIncognitoMode = tab.isIncognito
        
        if (tab.isIncognito) {
            activeIncognitoTabId = tabId
        } else {
            activeNormalTabId = tabId
        }
        
        // Notify Gecko runtime's web extension controller of the active tab change
        val controller = geckoRuntime?.webExtensionController
        if (controller != null) {
            val oldActiveTab = tabs.find { it.session == oldSession }
            if (oldActiveTab != null && oldActiveTab.session != tab.session) {
                try {
                    controller.setTabActive(oldActiveTab.session, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deactivating old tab session", e)
                }
            }
            try {
                controller.setTabActive(tab.session, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error activating new tab session", e)
            }
        }
        
        if (siteStyleAppliedGlobally) {
            applySiteStyleToActiveTab()
        }
        
        // Restore the tab's own saved navigation state
        canGoBack = tab.canGoBack
        canGoForward = tab.canGoForward


        // Clear media list when switching tabs to ensure only active tab's media is tracked
        mediaInterceptor.clear()
        isVideoPlayingInPage = false
        
        // If the tab's URI was loaded lazily and hasn't actually been requested yet, load it now!
        if (!tab.isUriLoaded) {
            val updatedTab = tab.copy(isUriLoaded = true)
            tabs[tabIndex] = updatedTab
            if (updatedTab.url != "about:blank" && updatedTab.url.isNotEmpty()) {
                updatedTab.session.loadUri(updatedTab.url)
            }
        }
        
        saveTabs()

        // Lazily reload tab if settings changed while it was in background
        if (tab.settingsVersion != currentSettingsVersion) {
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
            }
            reload()
        }
    }

    fun closeTab(tabId: String, context: Context) {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return
        val tabToClose = tabs[tabIndex]
        
        val modeTabsCount = tabs.count { it.isIncognito == tabToClose.isIncognito }
        
        if (modeTabsCount <= 1) {
            if (!tabToClose.isIncognito) {
                // Last normal tab: keep it, but reset to Home
                val idx = tabs.indexOfFirst { it.id == tabToClose.id }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(
                        url = "about:blank",
                        title = "New Tab",
                        canGoBack = false,
                        canGoForward = false,
                        loadError = null
                    )
                }
                if (tabToClose.id == activeTabId) {
                    currentUrl = "about:blank"
                    canGoBack = false
                    canGoForward = false
                }
                try {
                    tabToClose.session.stop()
                    tabToClose.session.loadUri("about:blank")
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting last tab session", e)
                }
                saveTabs()
                return
            } else {
                // Last incognito tab: close it and exit incognito mode
                try {
                    tabToClose.session.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing tab session", e)
                }
                tabs.removeAt(tabIndex)
                
                isIncognitoMode = false
                
                val normalTabs = tabs.filter { !it.isIncognito }
                if (normalTabs.isEmpty()) {
                    createNewTab(context, "about:blank")
                } else {
                    val targetTab = normalTabs.find { it.id == activeNormalTabId } ?: normalTabs.first()
                    selectTab(targetTab.id)
                }
                saveTabs()
                return
            }
        }

        // Standard close for any tab when there are multiple tabs in that mode
        try {
            tabToClose.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tab session", e)
        }
        tabs.removeAt(tabIndex)
        
        if (activeTabId == tabId) {
            val remainingModeTabs = tabs.filter { it.isIncognito == tabToClose.isIncognito }
            if (remainingModeTabs.isNotEmpty()) {
                val nextSelect = remainingModeTabs.find { it.id == (if (tabToClose.isIncognito) activeIncognitoTabId else activeNormalTabId) } 
                    ?: remainingModeTabs.first()
                selectTab(nextSelect.id)
            }
        }
        saveTabs()
    }


    private fun loadUrlInTab(tab: TabState, url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        if (formattedUrl.startsWith("about:")) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(url = formattedUrl, title = if (formattedUrl == "about:blank") "New Tab" else formattedUrl, isUriLoaded = true)
            }
            if (tab.id == activeTabId) {
                currentUrl = formattedUrl
            }
            tab.session.loadUri(formattedUrl)
            return
        }

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                getSearchUrlForQuery(formattedUrl)
            }
        }
        val idx = tabs.indexOfFirst { it.id == tab.id }
        if (idx != -1) {
            tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...", isUriLoaded = true)
        }
        if (tab.id == activeTabId) {
            currentUrl = formattedUrl
        }
        tab.session.loadUri(formattedUrl)
    }

    private fun applyUserAgentForTab(tab: TabState) {
        val ua = if (isDesktopMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        } else {
            "Mozilla/5.0 (Android 13; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
        }
        tab.session.settings.setUserAgentOverride(ua)
    }

    private fun setupTabSessionListeners(tab: TabState, context: Context) {
        applyUserAgentForTab(tab)
        tab.session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                Log.d(TAG, "onAndroidPermissionsRequest: ${permissions?.joinToString()}")
                if (tab.id != activeTabId) {
                    callback.reject()
                    return
                }
                activeSystemPermissionRequest = SystemPermissionRequest(
                    permissions = permissions,
                    onGranted = { callback.grant() },
                    onDenied = { callback.reject() }
                )
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                Log.d(TAG, "onContentPermissionRequest: type=${permission.permission}, uri=${permission.uri}")
                
                if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }

                if (tab.id != activeTabId) {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }

                val result = GeckoResult<Int>()
                activePermissionPrompt = ContentPermissionPrompt(
                    siteUri = permission.uri,
                    permissionType = permission.permission,
                    onAllow = {
                        activePermissionPrompt = null
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    },
                    onDeny = {
                        activePermissionPrompt = null
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    }
                )
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                Log.d(TAG, "onMediaPermissionRequest: uri=$uri, video=${video?.size}, audio=${audio?.size}")
                
                if (tab.id != activeTabId) {
                    callback.reject()
                    return
                }

                val hasVideo = !video.isNullOrEmpty()
                val hasAudio = !audio.isNullOrEmpty()

                if (!hasVideo && !hasAudio) {
                    callback.reject()
                    return
                }

                activeMediaPermissionPrompt = MediaPermissionPrompt(
                    siteUri = uri,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    onAllow = { selectedVideo, selectedAudio ->
                        activeMediaPermissionPrompt = null
                        callback.grant(selectedVideo, selectedAudio)
                    },
                    onDeny = {
                        activeMediaPermissionPrompt = null
                        callback.reject()
                    }
                )
            }
        }

        tab.session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                if (tab.id != activeTabId) {
                    return GeckoResult.fromValue(prompt.dismiss())
                }
                val allowMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                val mimeTypes = prompt.mimeTypes
                Log.d(TAG, "onFilePrompt: multiple=$allowMultiple, mimes=${mimeTypes?.joinToString()}")
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                pendingFilePrompt = PendingFilePrompt(
                    geckoResult = result,
                    prompt = prompt,
                    allowMultiple = allowMultiple,
                    mimeTypes = mimeTypes
                )
                return result
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                Log.i(TAG, "onCloseRequest: closing session for tab ${tab.id}")
                closeTab(tab.id, context)
            }

            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                if (tab.id != activeTabId) return
                handleExternalDownloadResponse(response, context)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (tab.id == activeTabId) {
                    isFullscreen = fullScreen
                }
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    val idx = tabs.indexOfFirst { it.id == tab.id }
                    if (idx != -1) {
                        val currentUrl = tabs[idx].url
                        tabs[idx] = tabs[idx].copy(title = it)
                        if (!isIncognitoMode) {
                            addToHistory(it, currentUrl)
                        }
                        saveTabs()
                    }
                }
            }

            override fun onCrash(session: GeckoSession) {
                // When renderer process crashes (e.g. OOM), auto-reload to recover from blank screen
                android.util.Log.e(TAG, "GeckoSession crashed, auto-reloading...")
                session.reload()
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                if (tab.id == activeTabId) {
                    activeContextMenu = ContextMenuElement(
                        linkUri = element.linkUri,
                        srcUri = element.srcUri,
                        linkText = element.textContent
                    )
                }
            }
        }

        tab.session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection
            ) {
                // When text is selected in web content, show custom selection menu
                if (tab.id == activeTabId && selection.text.isNotEmpty()) {
                    activeTextSelection = selection.text
                    activeSelectionObject = selection
                }
            }

            override fun onHideAction(session: GeckoSession, reason: Int) {
                // Clear selection when hidden
                if (tab.id == activeTabId) {
                    activeTextSelection = null
                    activeSelectionObject = null
                }
            }
        }


        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url?.let {
                    val idx = tabs.indexOfFirst { it.id == tab.id }
                    if (idx != -1) {
                        val currentTabUrl = tabs[idx].url
                        // GeckoView often fires a stale "about:blank" location change when a session
                        // is re-bound to the view. If we already have a real URL, ignore it.
                        if (it == "about:blank" && currentTabUrl != "about:blank" && currentTabUrl.isNotEmpty()) {
                            return
                        }
                        tabs[idx] = tabs[idx].copy(url = it, settingsVersion = currentSettingsVersion)
                        saveTabs()
                    }
                    if (tab.id == activeTabId) {
                        currentUrl = it
                        // Clear detected media on new page load in the active tab!
                        mediaInterceptor.clear()
                        isVideoPlayingInPage = false
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBackValue: Boolean) {
                // Always save to the tab's own state
                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(canGoBack = canGoBackValue)
                }
                if (tab.id == activeTabId) {
                    canGoBack = canGoBackValue
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForwardValue: Boolean) {
                // Always save to the tab's own state
                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(canGoForward = canGoForwardValue)
                }
                if (tab.id == activeTabId) {
                    canGoForward = canGoForwardValue
                }
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val lowerUri = uri.lowercase().trim()

                if (tab.id == activeTabId) {
                    mediaInterceptor.onMediaRequestDetected(uri)
                }

                // Intercept popups before new session triggers
                if (request.target == org.mozilla.geckoview.GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    if (isPopupBlockerEnabled) {
                        // 1. Block if target is a known ad popup network
                        val isAdPopup = POPUP_AD_DOMAINS.any { domain ->
                            lowerUri.contains(domain)
                        }
                        if (isAdPopup) {
                            Log.w(TAG, "🚫 onLoadRequest: Blocked ad popup to $uri")
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }

                        // 2. Block suspicious blank popups
                        if (request.uri.isEmpty() || lowerUri == "about:blank") {
                            Log.w(TAG, "🚫 onLoadRequest: Blocked blank popup")
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }

                        // 3. Block popups that have no user gesture (auto-popups / redirects)
                        if (!request.hasUserGesture) {
                            Log.w(TAG, "🚫 onLoadRequest: Blocked auto-popup (no user gesture) to $uri")
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }

                // 1. Intercept and block calendar spam subscription redirects from adware

                if (lowerUri.startsWith("webcal://") || lowerUri.startsWith("webcal:") || 
                    lowerUri.startsWith("calendar:") || lowerUri.endsWith(".ics") || 
                    lowerUri.contains(".ics?") || lowerUri.contains("calendar.google.com") ||
                    (lowerUri.startsWith("intent:") && (lowerUri.contains("calendar") || lowerUri.contains(".ics") || lowerUri.contains("webcal")))
                ) {
                    Log.w(TAG, "🚫 Intercepted and blocked potential spam calendar request: $uri")
                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Blocked calendar spam attempt", Toast.LENGTH_SHORT).show()
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 2. Intercept clicks to direct video files and launch native player
                val isYouTube = lowerUri.contains("youtube.com") || lowerUri.contains("youtu.be")
                if (isNativePlayerEnabled && isDirectVideoUrl(uri) && !isYouTube) {
                    Log.i(TAG, "🎬 Intercepted direct video load request: $uri. Opening in native player...")
                    viewModelScope.launch(Dispatchers.Main) {
                        val callback = onPlayVideoRequestReceived
                        if (callback != null) {
                            callback.invoke(uri, tab.url)
                        } else {
                            pendingVideoUrl = uri
                        }
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 2a. Intercept generic file downloads — show a dialog so user picks local vs. vault
                if (tab.id == activeTabId && isGenericDownloadUrl(uri) && !isYouTube) {
                    Log.i(TAG, "📥 Intercepted file download URL: $uri")
                    viewModelScope.launch(Dispatchers.Main) {
                        val filename = guessDownloadFilename(uri, null)
                        pendingGenericDownload = PendingGenericDownload(
                            url = uri,
                            filename = filename,
                            contentType = null
                        )
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                
                // 3. Block addon install click
                if (uri.endsWith(".xpi") || uri.contains("/firefox/downloads/file/")) {
                    Log.d(TAG, "Intercepted addon install click: $uri")
                    installExtensionFromUrl(uri, context)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 4. Handle non-standard schemes (intent://, market://, mailto:, tel:, etc.)
                if (!lowerUri.startsWith("http://") && 
                    !lowerUri.startsWith("https://") && 
                    !lowerUri.startsWith("about:") && 
                    !lowerUri.startsWith("javascript:") && 
                    !lowerUri.startsWith("data:")
                ) {
                    // Handle intent:// and market:// URIs (used by payment gateways, app deep links)
                    if (lowerUri.startsWith("intent:") || lowerUri.startsWith("market:")) {
                        Log.i(TAG, "Intercepted intent/market URI: $uri")
                        
                        try {
                            val intent = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME)
                            val intentPackage = intent.getPackage()
                            
                            // Block calendar spam intents
                            val isCalendarSpam = (intentPackage != null && (intentPackage.contains("calendar") || intentPackage.contains("cal"))) ||
                                    (intent.dataString != null && (intent.dataString!!.contains("calendar") || intent.dataString!!.contains("webcal") || intent.dataString!!.contains(".ics")))
                            
                            if (isCalendarSpam) {
                                Log.w(TAG, "🚫 Blocked calendar/adware intent: package=$intentPackage, data=${intent.dataString}")
                                viewModelScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, "Blocked calendar spam intent", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.i(TAG, "Launching external app intent safely: package=$intentPackage")
                                viewModelScope.launch(Dispatchers.Main) {
                                    try {
                                        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                        intent.setComponent(null)
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            intent.setSelector(null)
                                        }
                                        // Use createChooser so Android always shows the app picker
                                        val chooser = android.content.Intent.createChooser(intent, "Open with")
                                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(chooser)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        Log.w(TAG, "No app found for intent, checking for fallback URL", e)
                                        // Extract S.browser_fallback_url from the intent:// URI
                                        val fallbackUrl = extractFallbackUrl(uri)
                                        if (fallbackUrl != null) {
                                            Log.i(TAG, "Navigating to fallback URL: $fallbackUrl")
                                            loadUrl(fallbackUrl)
                                        } else if (intentPackage != null) {
                                            // Open Play Store listing for the package
                                            try {
                                                val marketIntent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("market://details?id=$intentPackage")
                                                )
                                                marketIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(marketIntent)
                                            } catch (e2: Exception) {
                                                // Fall back to web Play Store
                                                loadUrl("https://play.google.com/store/apps/details?id=$intentPackage")
                                            }
                                        } else {
                                            Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch external intent", e)
                                        val fallbackUrl = extractFallbackUrl(uri)
                                        if (fallbackUrl != null) {
                                            loadUrl(fallbackUrl)
                                        } else {
                                            Toast.makeText(context, "Could not open this link", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing intent URI", e)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    // Handle other custom protocols like upi:, mailto:, tel:, sms:, geo:, whatsapp:, tg:
                    Log.i(TAG, "Handling custom protocol URI: $uri")
                    viewModelScope.launch(Dispatchers.Main) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                            // Use createChooser to force the OS app picker (critical for UPI)
                            val chooserTitle = when {
                                lowerUri.startsWith("upi:") -> "Pay with"
                                lowerUri.startsWith("mailto:") -> "Send email with"
                                lowerUri.startsWith("tel:") -> "Call with"
                                lowerUri.startsWith("sms:") || lowerUri.startsWith("smsto:") -> "Send SMS with"
                                else -> "Open with"
                            }
                            val chooser = android.content.Intent.createChooser(intent, chooserTitle)
                            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        } catch (e: android.content.ActivityNotFoundException) {
                            Log.e(TAG, "No app found for custom protocol: $uri", e)
                            val schemeName = uri.split(":").firstOrNull() ?: "link"
                            Toast.makeText(context, "No app found to handle $schemeName links", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to launch custom protocol intent for: $uri", e)
                            val schemeName = uri.split(":").firstOrNull() ?: "link"
                            Toast.makeText(context, "No app found to handle $schemeName", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError
            ): GeckoResult<String>? {
                Log.e(TAG, "GeckoView Load Error: code=${error.code}, category=${error.category}, uri=$uri")
                
                val errorMsg = when (error.code) {
                    org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN_HOST -> "Unknown Host: The server's name could not be resolved. Make sure the URL is spelled correctly and you have an active network connection."
                    org.mozilla.geckoview.WebRequestError.ERROR_CONNECTION_REFUSED -> "Connection Failed: Could not connect to the server."
                    org.mozilla.geckoview.WebRequestError.ERROR_NET_TIMEOUT -> "Connection Timeout: The site took too long to respond."
                    org.mozilla.geckoview.WebRequestError.ERROR_PROXY_CONNECTION_REFUSED -> "Proxy connection failed."
                    org.mozilla.geckoview.WebRequestError.ERROR_NET_RESET, org.mozilla.geckoview.WebRequestError.ERROR_NET_INTERRUPT -> "Network Connection Error: Connection was reset or interrupted."
                    org.mozilla.geckoview.WebRequestError.ERROR_REDIRECT_LOOP -> "Too many redirects."
                    org.mozilla.geckoview.WebRequestError.ERROR_OFFLINE -> "Network Offline: Please check your internet connection."
                    org.mozilla.geckoview.WebRequestError.ERROR_MALFORMED_URI -> "Malformed URL: The URL is invalid."
                    else -> "Failed to load page (Error code: ${error.code})"
                }
                
                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(loadError = errorMsg)
                }
                
                return null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                try {
                    // Block target if ad domain or blank window
                    //   (a) the target URL belongs to a known ad/popup network, OR
                    //   (b) the popup blocker is enabled AND the URI is empty / about:blank
                    //       (common ad trick: open blank popup then JS-redirect it).
                    // Legitimate popups (OAuth, login dialogs, user-opened links) have a
                    // real https:// URI and pass through fine.
                    val lowerUri = uri.lowercase().trim()

                    if (isPopupBlockerEnabled) {
                        // Block if the URL matches a known popup ad network domain
                        val isAdPopup = POPUP_AD_DOMAINS.any { domain ->
                            lowerUri.contains(domain)
                        }
                        if (isAdPopup) {
                            Log.w(TAG, "🚫 Popup blocked — ad domain: $uri")
                            return GeckoResult.fromValue(null)
                        }

                        // Block suspicious blank popups that are script-injected (no real URL).
                        // about:blank popups are only legitimate when they have a meaningful URI
                        // passed in, e.g. for OAuth flows the URI is always a real https:// URL.
                        if (uri.isEmpty() || lowerUri == "about:blank") {
                            Log.w(TAG, "🚫 Popup blocked — suspicious blank popup with no URI")
                            return GeckoResult.fromValue(null)
                        }
                    }


                    // Allow legitimate popups in background tab
                    Log.i(TAG, "onNewSession: opening new tab for popup URI $uri")
                    val runtime = getGeckoRuntime(context)
                    val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                        .usePrivateMode(isIncognitoMode)
                        .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                        .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                        .allowJavascript(true)
                        .build()
                    val newSession = GeckoSession(settings)
                    val tabId = java.util.UUID.randomUUID().toString()
                    val newTab = TabState(
                        id = tabId,
                        session = newSession,
                        title = "New Tab",
                        url = uri,
                        isIncognito = isIncognitoMode
                    )

                    setupTabSessionListeners(newTab, context)
                    tabs.add(newTab)
                    newSession.open(runtime)
                    selectTab(newTab.id)
                    saveTabs()

                    return GeckoResult.fromValue(newSession)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onNewSession popup", e)
                    return null
                }
            }
        }

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (tab.id == activeTabId) {
                    isLoading = true
                    isReaderModeActive = false
                    stopTts()
                }
                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(loadError = null)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (tab.id == activeTabId) {
                    isLoading = false
                }
                if (success) {
                    if (tab.id == activeTabId) {
                        injectZoomEnabler()
                        if (siteStyleAppliedGlobally) {
                            applySiteStyleToActiveTab()
                        }
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {}
        }
    }

    // --- Persistent Browser History ---
    private fun loadHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_history.json")
            if (!file.exists()) return@launch
            try {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                val temp = mutableListOf<HistoryEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    temp.add(
                        HistoryEntry(
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            timestamp = obj.getLong("timestamp")
                        )
                    )
                }
                temp.sortByDescending { it.timestamp }
                withContext(Dispatchers.Main) {
                    historyList.clear()
                    historyList.addAll(temp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
            }
        }
    }

    private fun saveHistory(context: Context) {
        val historySnapshot = historyList.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_history.json")
            try {
                val jsonArray = JSONArray()
                historySnapshot.forEach { entry ->
                    val obj = JSONObject().apply {
                        put("title", entry.title)
                        put("url", entry.url)
                        put("timestamp", entry.timestamp)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving history", e)
            }
        }
    }

    fun addToHistory(title: String, url: String) {
        val context = appContext ?: return
        if (url == "about:blank" || url.trim().isEmpty()) return
        
        // Prevent duplicate spam
        historyList.removeAll { it.url == url }
        historyList.add(0, HistoryEntry(title, url, System.currentTimeMillis()))
        
        // Cap history at 500 items for memory safety
        if (historyList.size > 500) {
            historyList.removeAt(historyList.lastIndex)
        }
        
        saveHistory(context)
    }

    fun deleteHistoryEntry(entry: HistoryEntry) {
        val context = appContext ?: return
        historyList.remove(entry)
        saveHistory(context)
    }

    fun clearAllHistory() {
        val context = appContext ?: return
        historyList.clear()
        saveHistory(context)
    }

    fun clearCacheAndSiteData(context: Context) {
        val runtime = geckoRuntime
        if (runtime != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // 1. Clear GeckoView storage
                    val flags = org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
                                org.mozilla.geckoview.StorageController.ClearFlags.NETWORK_CACHE or
                                org.mozilla.geckoview.StorageController.ClearFlags.IMAGE_CACHE or
                                org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES or
                                org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA or
                                org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
                    
                    withContext(Dispatchers.Main) {
                        runtime.storageController.clearData(flags).accept(
                            { Log.d(TAG, "Storage clear completed successfully.") },
                            { err -> Log.e(TAG, "Storage clear error", err) }
                        )
                    }

                    // 2. Clear standard HTTP WebView caches and temp cacheDir files
                    val cacheDir = context.cacheDir
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                        cacheDir.mkdirs()
                    }

                    // 3. Clear temporary downloads
                    val tempDownloadsDir = File(context.filesDir, "temp_downloads")
                    if (tempDownloadsDir.exists()) {
                        tempDownloadsDir.deleteRecursively()
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Storage optimized successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear cache", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Clear failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Browser engine not running", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearSiteData(context: Context) {
        val runtime = geckoRuntime
        if (runtime != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val flags = org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
                                org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES or
                                org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA
                    
                    withContext(Dispatchers.Main) {
                        runtime.storageController.clearData(flags).accept(
                            { Toast.makeText(context, "Cookies and site data cleared", Toast.LENGTH_SHORT).show() },
                            { err -> Toast.makeText(context, "Clear failed", Toast.LENGTH_SHORT).show() }
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Browser engine not running", Toast.LENGTH_SHORT).show()
        }
    }

    fun pruneTemporaryStorage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    val now = System.currentTimeMillis()
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("hls_") || 
                            file.name.startsWith("omni_") || 
                            file.name.endsWith(".zip") || 
                            file.name.endsWith(".pdf")) {
                            if (now - file.lastModified() > oneDayMs) {
                                file.deleteRecursively()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pruning temporary storage", e)
            }
        }
    }

    fun getGeckoRuntime(context: Context): GeckoRuntime {
        val appCtx = context.applicationContext
        appContext = appCtx

        // 1. Static/Global runtime initialization (once per process)
        if (geckoRuntime == null) {
            val isDebug = (appCtx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            
            // Retrieve the user's selected app language preference to configure GeckoView locale
            val lang = try {
                val sp = appCtx.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                sp.getString("selected_language", "en") ?: "en"
            } catch (e: Exception) {
                "en"
            }

            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(isDebug)
                .consoleOutput(isDebug)
                .debugLogging(isDebug)
                .remoteDebuggingEnabled(isDebug)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .locales(arrayOf(lang)) // Configures Accept-Language headers automatically
                .build()
            
            geckoRuntime = GeckoRuntime.create(appCtx, settings)
            pruneTemporaryStorage(appCtx)
            
            geckoRuntime!!.webExtensionController.setPromptDelegate(object : org.mozilla.geckoview.WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: org.mozilla.geckoview.WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.WebExtension.PermissionPromptResponse>? {
                    Log.d(TAG, "Auto-approving install prompt for extension: ${extension.id}")
                    return org.mozilla.geckoview.GeckoResult.fromValue(
                        org.mozilla.geckoview.WebExtension.PermissionPromptResponse(
                            true, // isPermissionsGranted
                            true, // isPrivateModeGranted
                            false // isTechnicalAndInteractionDataGranted
                        )
                    )
                }
            })
        }

        // 2. Instance-scoped initialization (once per BrowserViewModel instance)
        if (!isViewModelInitialized) {
            isViewModelInitialized = true

            // Load persistent history
            loadHistory(appCtx)
            loadBookmarks(appCtx)
            loadShortcuts(appCtx)
            initTts(appCtx)
            fetchNews()

            // Initialize dependency engines
            ffmpegLoader = FFmpegLoader(appCtx)
            viewModelScope.launch(Dispatchers.IO) {
                ffmpegLoader.downloadAndInstall()
            }
            ffmpegBridge = FFmpegBridge(ffmpegLoader)
            val locker = PrivateLockerManager(appCtx)
            streamDownloadEngine = StreamDownloadEngine(appCtx, ffmpegBridge, locker)
            vpnManager = VpnManager(appCtx)
            copyManager = UniversalCopyManager(geckoRuntime!!)
            aiBlockerManager = AiBlockerManager(geckoRuntime!!)
            
            // Sync user extensions on start
            syncUserExtensions()

            // Initialize multi-tabs
            initTabs(appCtx)
            loadDevNotes(appCtx)

            viewModelScope.launch {
                isPopupBlockerEnabled = getPopupBlockerPreference(appCtx).first()
            }

            viewModelScope.launch {
                isNativePlayerEnabled = getNativePlayerPreference(appCtx).first()
                syncNativePlayerStateInPage()
            }


            viewModelScope.launch {
                customVpnConfig = getCustomVpnConfig(appCtx).first()
            }

            viewModelScope.launch {
                selectedSearchEngine = getSearchEnginePreference(appCtx).first()
                customSearchUrl = getCustomSearchUrlPreference(appCtx).first()
            }

            viewModelScope.launch {
                val darkThemePref = getDarkThemePreference(appCtx).first()
                isDarkThemeEnabled = darkThemePref
                geckoRuntime?.settings?.preferredColorScheme = if (darkThemePref) {
                    GeckoRuntimeSettings.COLOR_SCHEME_DARK
                } else {
                    GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                }
            }

            viewModelScope.launch {
                selectedAccentTheme = getAccentThemePreference(appCtx).first()
            }

            viewModelScope.launch {
                selectedLanguageCode = getLanguagePreference(appCtx).first()
            }

            viewModelScope.launch {
                isLanguageSelectionDone = getLanguageSelectionDone(appCtx).first()
            }

            viewModelScope.launch {
                isOnboardingCompleted = getOnboardingCompletedPreference(appCtx).first()
            }

            viewModelScope.launch {
                val sp = appCtx.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                siteStyleFontSize = sp.getInt("site_style_font_size", 100)
                siteStyleTheme = sp.getString("site_style_theme", "DEFAULT") ?: "DEFAULT"
                siteStyleLineSpacing = sp.getFloat("site_style_line_spacing", 1.4f)
                siteStyleLetterSpacing = sp.getFloat("site_style_letter_spacing", 0f)
                siteStyleFontFamily = sp.getString("site_style_font_family", "inherit") ?: "inherit"
                siteStyleAppliedGlobally = sp.getBoolean("site_style_applied_globally", false)
            }

            viewModelScope.launch {
                hasSeenQrOverview = getQrOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                hasSeenPdfOverview = getPdfOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                hasSeenVideoOverview = getVideoOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                hasSeenExtensionsOverview = getExtensionsOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                hasSeenEditPageOverview = getEditPageOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                hasSeenConsoleOverview = getConsoleOverviewSeenPreference(appCtx).first()
                hasSeenDevNotesOverview = getDevNotesOverviewSeenPreference(appCtx).first()
            }

            viewModelScope.launch {
                appCtx.dataStore.data.first().let { prefs ->
                    forceDarkWebsites = prefs[FORCE_DARK_WEBSITES_KEY] ?: false
                    navBarHideTop = prefs[NAV_BAR_HIDE_TOP_KEY] ?: true
                    navBarHideBottom = prefs[NAV_BAR_HIDE_BOTTOM_KEY] ?: true
                    addressBarPosition = prefs[ADDRESS_BAR_POSITION_KEY] ?: "Top"
                    appIconState = prefs[APP_ICON_STATE_KEY] ?: "Default"
                    browserWallpaperUri = prefs[BROWSER_WALLPAPER_URI_KEY]
                    changeWallpaperDaily = prefs[CHANGE_WALLPAPER_DAILY_KEY] ?: false
                    showDiscoverFeed = prefs[SHOW_DISCOVER_FEED_KEY] ?: true
                    showBottomNavBar = prefs[SHOW_BOTTOM_NAV_BAR_KEY] ?: true
                }
            }



            viewModelScope.launch {
                loadPlayerSettings(appCtx)
            }

            // Refresh and load all built-in extensions (grabber, ublock, universal copy, ai blocker)
            refreshAndLoadBuiltInExtensions(appCtx)
        }
        return geckoRuntime!!
    }

    private fun refreshAndLoadBuiltInExtensions(context: Context) {
        val runtime = geckoRuntime ?: return
        Log.d(TAG, "Refreshing and loading built-in extensions...")
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runtime.webExtensionController.list().accept(
                { extensions ->
                    val coreIds = setOf(UBLOCK_ID, GRABBER_ID, "omni-universal-copy@omnibrowser.app", AI_BLOCKER_ID)
                    val toUninstall = extensions?.filter { it.id in coreIds } ?: emptyList()
                    
                    if (toUninstall.isNotEmpty()) {
                        Log.d(TAG, "Uninstalling old built-in extensions to reload assets: ${toUninstall.map { it.id }}")
                        var remaining = toUninstall.size
                        toUninstall.forEach { ext ->
                            runtime.webExtensionController.uninstall(ext).accept(
                                {
                                    remaining--
                                    if (remaining == 0) {
                                        loadExtensionsClean(context)
                                    }
                                },
                                { error ->
                                    Log.e(TAG, "Failed to uninstall extension ${ext.id}", error)
                                    remaining--
                                    if (remaining == 0) {
                                        loadExtensionsClean(context)
                                    }
                                }
                            )
                        }
                    } else {
                        loadExtensionsClean(context)
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to list extensions on startup", error)
                    loadExtensionsClean(context)
                }
            )
        }
    }

    private fun loadExtensionsClean(context: Context) {
        val runtime = geckoRuntime ?: return
        viewModelScope.launch {
            isMediaGrabberEnabled = getMediaGrabberPreference(context).first()
            installGrabberExtension(runtime)
            
            isAdblockerEnabled = getAdblockPreference(context).first()
            syncAdblockerState(shouldReload = false)
            
            isUniversalCopyEnabled = getUniversalCopyPreference(context).first()
            syncUniversalCopyState(shouldReload = false)
            
            isAiBlockerEnabled = getAiBlockerPreference(context).first()
            aiBlockerManager?.installAndSync(isAiBlockerEnabled, onComplete = null)
        }
    }


    private fun installGrabberExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/media_grabber/",
            GRABBER_ID
        ).accept(
            { ext ->
                grabberExtension = ext
                ext?.let {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                    if (isMediaGrabberEnabled) {
                        runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    } else {
                        runtime.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    }
                    setupNativeAppMessageDelegate(it)

                }
                Log.i(TAG, "Aggressive Media Grabber active.")
            },
            { error ->
                Log.e(TAG, "Failed to load Aggressive Media Grabber", error)
            }
        )
    }

    /**
     * Listen to messaging port communication coming from inject.js MSE capture scripts
     */
    private fun setupNativeAppMessageDelegate(extension: WebExtension) {
        // nativeApp parameter must match nativeApp ID registered in background.js chrome.runtime.sendNativeMessage
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                Log.d(TAG, "🎬 onMessage called! nativeApp = $nativeApp, messageType = ${message.javaClass.name}, message = $message")
                try {
                    val type = if (message is org.json.JSONObject) {
                        if (message.has("type")) message.getString("type") else null
                    } else {
                        (message as? Map<*, *>)?.get("type") as? String
                    }

                    if (type == "GET_NATIVE_PLAYER_STATE") {
                        val response = org.json.JSONObject()
                        response.put("enabled", isNativePlayerEnabled)
                        pendingJsCommand?.let {
                            response.put("pendingJs", it)
                            pendingJsCommand = null
                        }
                        return GeckoResult.fromValue(response)
                    } else if (type == "MEDIA_GRABBED") {
                        val url = if (message is org.json.JSONObject) {
                            if (message.has("url")) message.getString("url") else null
                        } else {
                            (message as? Map<*, *>)?.get("url") as? String
                        }
                        val mime = if (message is org.json.JSONObject) {
                            if (message.has("mimeType")) message.getString("mimeType") else null
                        } else {
                            (message as? Map<*, *>)?.get("mimeType") as? String
                        }
                        if (url != null) {
                            mediaInterceptor.onAggressiveMediaGrabbed(url, mime ?: "video/mp4")
                        }
                    } else if (type == "PLAY_IN_NATIVE") {
                        val videoUrl = if (message is org.json.JSONObject) {
                            if (message.has("url")) message.getString("url") else null
                        } else {
                            (message as? Map<*, *>)?.get("url") as? String
                        }
                        val pageUrl = (if (message is org.json.JSONObject) {
                            if (message.has("pageUrl")) message.getString("pageUrl") else null
                        } else {
                            (message as? Map<*, *>)?.get("pageUrl") as? String
                        }) ?: ""
                        Log.i(TAG, "🎬 received PLAY_IN_NATIVE message. url=$videoUrl, pageUrl=$pageUrl, isNativePlayerEnabled=$isNativePlayerEnabled")
                        val isYouTube = pageUrl.lowercase().contains("youtube.com") || pageUrl.lowercase().contains("youtu.be") ||
                                        (videoUrl != null && (videoUrl.lowercase().contains("youtube.com") || videoUrl.lowercase().contains("youtu.be")))
                        if (videoUrl != null && isNativePlayerEnabled && !isYouTube) {
                            viewModelScope.launch(Dispatchers.Main) {
                                Log.i(TAG, "🎬 Native player takeover starting for: $videoUrl")
                                if (onPlayVideoRequestReceived == null) {
                                    Log.e(TAG, "onPlayVideoRequestReceived is NULL! Cannot navigate to VideoPlayerScreen.")
                                } else {
                                    onPlayVideoRequestReceived?.invoke(videoUrl, pageUrl)
                                }
                            }
                        } else if (isYouTube) {
                            Log.i(TAG, "🎬 Native player takeover bypassed for YouTube URL")
                        }
                    } else if (type == "VIDEO_STATE_CHANGE") {
                        val playing = if (message is org.json.JSONObject) {
                            if (message.has("isPlaying")) message.getBoolean("isPlaying") else false
                        } else {
                            (message as? Map<*, *>)?.get("isPlaying") as? Boolean ?: false
                        }
                        viewModelScope.launch(Dispatchers.Main) {
                            isVideoPlayingInPage = playing
                        }
                    } else if (type == "CONSOLE_LOG") {
                        val level = (if (message is org.json.JSONObject) {
                            if (message.has("level")) message.getString("level") else null
                        } else {
                            (message as? Map<*, *>)?.get("level") as? String
                        }) ?: "LOG"
                        val msg = (if (message is org.json.JSONObject) {
                            if (message.has("message")) message.getString("message") else null
                        } else {
                            (message as? Map<*, *>)?.get("message") as? String
                        }) ?: ""
                        Log.d("WebConsole", "[$level] $msg")
                        // Run on main thread because we are updating a Compose MutableStateList
                        viewModelScope.launch(Dispatchers.Main) {
                            if (level == "READER_TTS_CONTENT") {
                                speakText(msg)
                            } else {
                                consoleLogs.add(ConsoleLogEntry(level, msg))
                                if (consoleLogs.size > 200) {
                                    consoleLogs.removeAt(0)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing grabbed media extension port message", e)
                }
                return null
            }
        }, "omniApp")
    }

    private fun syncAdblockerState(shouldReload: Boolean = false) {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/ublock/",
            UBLOCK_ID
        ).accept(
            { ext ->
                uBlockExtension = ext
                ext?.let {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                    val action = if (isAdblockerEnabled) {
                        runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    } else {
                        runtime.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    }

                    action.accept(
                        {
                            isAdblockerToggling = false
                            if (shouldReload) {
                                currentSettingsVersion++
                                val activeId = activeTabId
                                if (activeId != null) {
                                    val idx = tabs.indexOfFirst { it.id == activeId }
                                    if (idx != -1) {
                                        tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
                                    }
                                }
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    reload()
                                }
                            }
                        },
                        { error ->
                            isAdblockerToggling = false
                            Log.e(TAG, "Failed to toggle adblocker state", error)
                        }
                    )
                } ?: run {
                    isAdblockerToggling = false
                }
            },
            { error ->
                isAdblockerToggling = false
                Log.e(TAG, "Failed to ensure built-in adblocker", error)
            }
        )
    }

    private fun syncUniversalCopyState(shouldReload: Boolean = false) {
        copyManager?.installAndSync(isUniversalCopyEnabled, onComplete = {
            isUniversalCopyToggling = false
            if (shouldReload) {
                currentSettingsVersion++
                val activeId = activeTabId
                if (activeId != null) {
                    val idx = tabs.indexOfFirst { it.id == activeId }
                    if (idx != -1) {
                        tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    reload()
                }
            }
        })
    }

    private fun syncMediaGrabberState(shouldReload: Boolean = false) {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/media_grabber/",
            GRABBER_ID
        ).accept(
            { ext ->
                grabberExtension = ext
                ext?.let {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                    val action = if (isMediaGrabberEnabled) {
                        val enableResult = runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                        setupNativeAppMessageDelegate(it)
                        enableResult
                    } else {
                        runtime.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    }

                    action.accept(
                        {
                            isMediaGrabberToggling = false
                            if (shouldReload) {
                                currentSettingsVersion++
                                val activeId = activeTabId
                                if (activeId != null) {
                                    val idx = tabs.indexOfFirst { it.id == activeId }
                                    if (idx != -1) {
                                        tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
                                    }
                                }
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    reload()
                                }
                            }
                        },
                        { error ->
                            isMediaGrabberToggling = false
                            Log.e(TAG, "Failed to toggle media grabber state", error)
                        }
                    )
                } ?: run {
                    isMediaGrabberToggling = false
                }
            },
            { error ->
                isMediaGrabberToggling = false
                Log.e(TAG, "Failed to ensure built-in media grabber", error)
            }
        )
    }

    private fun syncNativePlayerStateInPage() {
        // Handled automatically via background.js polling GET_NATIVE_PLAYER_STATE
    }

    fun toggleAdblock(context: Context) {
        if (isAdblockerToggling) return
        isAdblockerToggling = true
        viewModelScope.launch {
            val newState = !isAdblockerEnabled
            isAdblockerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[UBLOCK_ENABLED_KEY] = newState
            }
            syncAdblockerState(shouldReload = true)
        }
    }

    fun togglePopupBlocker(context: Context) {
        viewModelScope.launch {
            val newState = !isPopupBlockerEnabled
            isPopupBlockerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[POPUP_BLOCKER_ENABLED_KEY] = newState
            }
        }
    }

    fun toggleUniversalCopy(context: Context) {
        if (isUniversalCopyToggling) return
        isUniversalCopyToggling = true
        viewModelScope.launch {
            val newState = !isUniversalCopyEnabled
            isUniversalCopyEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[UNIVERSAL_COPY_ENABLED_KEY] = newState
            }
            syncUniversalCopyState(shouldReload = true)
        }
    }

    fun toggleAiBlocker(context: Context) {
        if (isAiBlockerToggling) return
        isAiBlockerToggling = true
        viewModelScope.launch {
            val newState = !isAiBlockerEnabled
            isAiBlockerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[AI_BLOCKER_ENABLED_KEY] = newState
            }
            syncAiBlockerState(shouldReload = true)
        }
    }

    private fun syncAiBlockerState(shouldReload: Boolean = false) {
        val manager = aiBlockerManager ?: return
        manager.setEnabled(isAiBlockerEnabled, onComplete = {
            isAiBlockerToggling = false
            if (shouldReload) {
                currentSettingsVersion++
                val activeId = activeTabId
                if (activeId != null) {
                    val idx = tabs.indexOfFirst { it.id == activeId }
                    if (idx != -1) {
                        tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    reload()
                }
            }
        })
    }

    private fun getAiBlockerPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AI_BLOCKER_ENABLED_KEY] ?: false
        }
    }

    private fun getAdblockPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[UBLOCK_ENABLED_KEY] ?: true
        }
    }

    private fun getPopupBlockerPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[POPUP_BLOCKER_ENABLED_KEY] ?: true  // Default ON
        }
    }

    private fun getUniversalCopyPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[UNIVERSAL_COPY_ENABLED_KEY] ?: false
        }
    }

    private fun getNativePlayerPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[NATIVE_PLAYER_ENABLED_KEY] ?: true // Default ON
        }
    }

    private fun getMediaGrabberPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[MEDIA_GRABBER_ENABLED_KEY] ?: true // Default ON
        }
    }

    fun getCustomVpnConfig(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_VPN_CONFIG_KEY]
        }
    }

    fun saveCustomVpnConfig(context: Context, config: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_VPN_CONFIG_KEY] = config
            }
            customVpnConfig = config
        }
    }

    fun getSearchEnginePreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[SEARCH_ENGINE_KEY] ?: "Google"
        }
    }

    fun getCustomSearchUrlPreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_SEARCH_URL_KEY] ?: ""
        }
    }

    fun saveSearchEngine(context: Context, engine: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[SEARCH_ENGINE_KEY] = engine
            }
            selectedSearchEngine = engine
        }
    }

    fun saveCustomSearchUrl(context: Context, url: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_SEARCH_URL_KEY] = url
            }
            customSearchUrl = url
        }
    }

    fun getDarkThemePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DARK_THEME_ENABLED_KEY] ?: true
        }
    }

    fun saveDarkTheme(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DARK_THEME_ENABLED_KEY] = enabled
            }
            isDarkThemeEnabled = enabled
            
            geckoRuntime?.settings?.preferredColorScheme = if (enabled) {
                GeckoRuntimeSettings.COLOR_SCHEME_DARK
            } else {
                GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            }
        }
    }

    // Accent Theme settings helper methods

    fun getAccentThemePreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[ACCENT_THEME_KEY] ?: "Ocean Blue"
        }
    }

    fun saveAccentTheme(context: Context, theme: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[ACCENT_THEME_KEY] = theme
            }
            selectedAccentTheme = theme
        }
    }

    fun saveForceDarkWebsites(context: Context, forceDark: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[FORCE_DARK_WEBSITES_KEY] = forceDark }
            forceDarkWebsites = forceDark
        }
    }

    fun saveNavBarHideTop(context: Context, hideTop: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[NAV_BAR_HIDE_TOP_KEY] = hideTop }
            navBarHideTop = hideTop
        }
    }

    fun saveNavBarHideBottom(context: Context, hideBottom: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[NAV_BAR_HIDE_BOTTOM_KEY] = hideBottom }
            navBarHideBottom = hideBottom
        }
    }

    fun saveAddressBarPosition(context: Context, position: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[ADDRESS_BAR_POSITION_KEY] = position }
            addressBarPosition = position
        }
    }

    fun saveAppIconState(context: Context, state: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[APP_ICON_STATE_KEY] = state }
            appIconState = state
        }
    }

    fun saveBrowserWallpaperUri(context: Context, uri: String?) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                if (uri == null) prefs.remove(BROWSER_WALLPAPER_URI_KEY)
                else prefs[BROWSER_WALLPAPER_URI_KEY] = uri
            }
            browserWallpaperUri = uri
        }
    }

    fun saveChangeWallpaperDaily(context: Context, changeDaily: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[CHANGE_WALLPAPER_DAILY_KEY] = changeDaily }
            changeWallpaperDaily = changeDaily
        }
    }

    fun saveShowDiscoverFeed(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_DISCOVER_FEED_KEY] = show }
            showDiscoverFeed = show
        }
    }

    fun saveShowBottomNavBar(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_BOTTOM_NAV_BAR_KEY] = show }
            showBottomNavBar = show
        }
    }



    fun getLanguagePreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_LANGUAGE_KEY] ?: "en"
        }
    }

    suspend fun saveLanguagePreference(context: Context, langCode: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGE_KEY] = langCode
        }
        try {
            val sp = context.applicationContext.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
            sp.edit().putString("selected_language", langCode).apply()
        } catch (e: Exception) { /* ignore */ }
        selectedLanguageCode = langCode
    }

    fun getLanguageSelectionDone(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[LANGUAGE_SELECTION_DONE_KEY] ?: false
        }
    }

    suspend fun saveLanguageSelectionDone(context: Context, done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_SELECTION_DONE_KEY] = done
        }
        isLanguageSelectionDone = done
    }

    fun saveLanguageSettings(context: Context, langCode: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            appCtx.dataStore.edit { preferences ->
                preferences[SELECTED_LANGUAGE_KEY] = langCode
                preferences[LANGUAGE_SELECTION_DONE_KEY] = true
            }
            try {
                val sp = appCtx.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                sp.edit().putString("selected_language", langCode).apply()
            } catch (e: Exception) { /* ignore */ }
            selectedLanguageCode = langCode
            isLanguageSelectionDone = true
            withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun getOnboardingCompletedPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] ?: false
        }
    }

    fun saveOnboardingCompleted(context: Context, completed: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED_KEY] = completed
            }
            isOnboardingCompleted = completed
        }
    }

    fun getQrOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[QR_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveQrOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[QR_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenQrOverview = seen
        }
    }

    fun getPdfOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PDF_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun savePdfOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                // Reset the PDF export theme to "default" when the overview is dismissed —
                // the overview picker sets a custom theme, so dismissing it should revert.
                preferences[PDF_EXPORT_THEME_KEY] = if (seen) "default" else ""
                preferences[PDF_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenPdfOverview = seen
        }
    }

    fun getVideoOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[VIDEO_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveVideoOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[VIDEO_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenVideoOverview = seen
        }
    }

    fun getExtensionsOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[EXTENSIONS_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveExtensionsOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[EXTENSIONS_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenExtensionsOverview = seen
        }
    }

    fun getEditPageOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[EDIT_PAGE_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveEditPageOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[EDIT_PAGE_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenEditPageOverview = seen
        }
    }

    fun getConsoleOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[CONSOLE_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveConsoleOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CONSOLE_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenConsoleOverview = seen
        }
    }

    fun getDevNotesOverviewSeenPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DEV_NOTES_OVERVIEW_SEEN_KEY] ?: false
        }
    }
    fun saveDevNotesOverviewSeen(context: Context, seen: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DEV_NOTES_OVERVIEW_SEEN_KEY] = seen
            }
            hasSeenDevNotesOverview = seen
        }
    }

    // Player preferences persistence helper

    fun savePlayerSetting(context: Context, key: String, value: Any) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                when (key) {
                    "quality" -> { preferences[PLAYER_DEFAULT_QUALITY_KEY] = value as String; playerDefaultQuality = value }
                    "autoplay" -> { preferences[PLAYER_AUTOPLAY_KEY] = value as Boolean; isPlayerAutoPlayEnabled = value }
                    "loop" -> { preferences[PLAYER_LOOP_KEY] = value as Boolean; isPlayerLoopEnabled = value }
                    "brightness_gesture" -> { preferences[PLAYER_BRIGHTNESS_GESTURE_KEY] = value as Boolean; isPlayerBrightnessGestureEnabled = value }
                    "volume_gesture" -> { preferences[PLAYER_VOLUME_GESTURE_KEY] = value as Boolean; isPlayerVolumeGestureEnabled = value }
                    "resume" -> { preferences[PLAYER_RESUME_PLAYBACK_KEY] = value as Boolean; isPlayerResumePlaybackEnabled = value }
                    "background" -> { preferences[PLAYER_BACKGROUND_PLAYBACK_KEY] = value as Boolean; isPlayerBackgroundPlaybackEnabled = value }
                }
            }
        }
    }

    private suspend fun loadPlayerSettings(context: Context) {
        val prefs = context.dataStore.data.first()
        playerDefaultQuality = prefs[PLAYER_DEFAULT_QUALITY_KEY] ?: "Auto"
        isPlayerAutoPlayEnabled = prefs[PLAYER_AUTOPLAY_KEY] ?: true
        isPlayerLoopEnabled = prefs[PLAYER_LOOP_KEY] ?: false
        isPlayerBrightnessGestureEnabled = prefs[PLAYER_BRIGHTNESS_GESTURE_KEY] ?: true
        isPlayerVolumeGestureEnabled = prefs[PLAYER_VOLUME_GESTURE_KEY] ?: true
        isPlayerResumePlaybackEnabled = prefs[PLAYER_RESUME_PLAYBACK_KEY] ?: true
        isPlayerBackgroundPlaybackEnabled = prefs[PLAYER_BACKGROUND_PLAYBACK_KEY] ?: false
        pdfExportTheme = prefs[PDF_EXPORT_THEME_KEY] ?: "default"
    }

    fun savePdfExportTheme(context: Context, theme: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PDF_EXPORT_THEME_KEY] = theme
            }
            pdfExportTheme = theme
        }
    }

    val videoPlaybackPositions = mutableMapOf<String, Long>()

    fun getVideoPosition(url: String): Long {
        return if (isPlayerResumePlaybackEnabled) {
            videoPlaybackPositions[url] ?: 0L
        } else {
            0L
        }
    }

    fun saveVideoPosition(url: String, position: Long) {
        if (isPlayerResumePlaybackEnabled) {
            videoPlaybackPositions[url] = position
        }
    }

    fun toggleMediaGrabber(context: Context) {
        if (isMediaGrabberToggling) return
        isMediaGrabberToggling = true
        viewModelScope.launch {
            val newState = !isMediaGrabberEnabled
            isMediaGrabberEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[MEDIA_GRABBER_ENABLED_KEY] = newState
            }
            syncMediaGrabberState(shouldReload = true)
        }
    }

    fun toggleUserExtension(extension: WebExtension, context: Context) {
        if (togglingUserExtensionIds.contains(extension.id)) return
        togglingUserExtensionIds.add(extension.id)
        val runtime = geckoRuntime ?: run {
            togglingUserExtensionIds.remove(extension.id)
            return
        }
        val currentlyEnabled = extension.metaData.enabled
        val action = if (currentlyEnabled) {
            runtime.webExtensionController.disable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
        } else {
            runtime.webExtensionController.enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
        }
        action.accept(
            {
                syncUserExtensions()
                currentSettingsVersion++
                val activeId = activeTabId
                if (activeId != null) {
                    val idx = tabs.indexOfFirst { it.id == activeId }
                    if (idx != -1) {
                        tabs[idx] = tabs[idx].copy(settingsVersion = currentSettingsVersion)
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    togglingUserExtensionIds.remove(extension.id)
                    reload()
                }
            },
            { error ->
                Log.e(TAG, "Failed to toggle user extension: ${extension.id}", error)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    togglingUserExtensionIds.remove(extension.id)
                }
            }
        )
    }

    fun toggleNativePlayer(context: Context) {
        viewModelScope.launch {
            val newState = !isNativePlayerEnabled
            isNativePlayerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[NATIVE_PLAYER_ENABLED_KEY] = newState
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                reload()
            }
        }
    }

    fun connectVpn(context: Context, serverIp: String, clientKey: String, serverKey: String) {
        vpnManager.connectContaboVps(serverIp, clientKey, serverKey)
    }

    fun connectCustomVpn() {
        val config = customVpnConfig ?: return
        vpnManager.connect(config)
    }

    fun disconnectVpn() {
        vpnManager.disconnect()
    }

    fun getSearchUrlForQuery(query: String): String {
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: java.io.UnsupportedEncodingException) {
            query.replace(" ", "+")
        }
        return when (selectedSearchEngine) {
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encodedQuery"
            "Brave" -> "https://search.brave.com/search?q=$encodedQuery"
            "Bing" -> "https://www.bing.com/search?q=$encodedQuery"
            "Custom" -> {
                val customUrl = customSearchUrl
                if (!customUrl.isNullOrBlank() && customUrl.contains("%s")) {
                    customUrl.replace("%s", encodedQuery)
                } else {
                    "https://duckduckgo.com/?q=$encodedQuery"
                }
            }
            else -> {
                val base = "https://www.google.com/search?q=$encodedQuery"
                if (isAiBlockerEnabled) "$base&udm=14" else base
            }
        }
    }

    // --- Browser Navigation ---
    fun loadUrl(url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://") && !formattedUrl.startsWith("about:") && !formattedUrl.startsWith("javascript:")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                getSearchUrlForQuery(formattedUrl)
            }
        }

        // Intercept direct video playback if native player is enabled
        if (isNativePlayerEnabled && isDirectVideoUrl(formattedUrl)) {
            Log.i(TAG, "🎬 Direct video URL loaded: $formattedUrl. Launching native player...")
            viewModelScope.launch(Dispatchers.Main) {
                val callback = onPlayVideoRequestReceived
                if (callback != null) {
                    callback.invoke(formattedUrl, formattedUrl)
                } else {
                    pendingVideoUrl = formattedUrl
                }
            }
            return
        }

        if (formattedUrl.startsWith("about:") || formattedUrl.startsWith("javascript:")) {
            val activeId = activeTabId
            if (activeId != null) {
                val idx = tabs.indexOfFirst { it.id == activeId }
                if (idx != -1 && formattedUrl.startsWith("about:")) {
                    tabs[idx] = tabs[idx].copy(url = formattedUrl, title = if (formattedUrl == "about:blank") "New Tab" else formattedUrl, isUriLoaded = true)
                    currentUrl = formattedUrl
                }
            }
            geckoSession.loadUri(formattedUrl)
            return
        }
        
        val activeId = activeTabId
        if (activeId != null) {
            val idx = tabs.indexOfFirst { it.id == activeId }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...", isUriLoaded = true)
            }
        }
        currentUrl = formattedUrl
        geckoSession.loadUri(formattedUrl)
    }

    fun goBack() {
        try {
            if (canGoBack) geckoSession.goBack()
        } catch (e: Exception) {
            Log.w(TAG, "goBack() failed: ${e.message}")
        }
    }

    fun goForward() {
        try {
            if (canGoForward) geckoSession.goForward()
        } catch (e: Exception) {
            Log.w(TAG, "goForward() failed: ${e.message}")
        }
    }

    fun reload() {
        try {
            geckoSession.reload()
        } catch (e: Exception) {
            Log.w(TAG, "reload() failed: ${e.message}")
        }
    }

    /**
     * Navigate to the home screen (about:blank) by updating only the ViewModel
     * state — does NOT touch geckoSession. This avoids crashes during video player
     * teardown or when the session is in an inconsistent state.
     */
    fun navigateHomeDirectly() {
        val activeId = activeTabId ?: return
        val idx = tabs.indexOfFirst { it.id == activeId }
        if (idx != -1) {
            tabs[idx] = tabs[idx].copy(url = "about:blank", title = "New Tab", isUriLoaded = true)
            currentUrl = "about:blank"
            canGoBack = false
        }
        // Then actually load it in the session so back history is cleared
        viewModelScope.launch(Dispatchers.Main) {
            try { geckoSession.loadUri("about:blank") } catch (e: Exception) {
                Log.w(TAG, "navigateHomeDirectly geckoSession.loadUri failed: ${e.message}")
            }
        }
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }

    fun toggleDesktopMode(context: Context) {
        isDesktopMode = !isDesktopMode
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        try {
            val uaMode = if (isDesktopMode) {
                org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            val vpMode = if (isDesktopMode) {
                org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            } else {
                org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            }
            // Set both user-agent and viewport mode — this is the correct dual approach
            // used by Chrome, Firefox, and Brave on Android to trigger desktop layouts
            activeTab.session.settings.userAgentMode = uaMode
            activeTab.session.settings.viewportMode = vpMode
            applyUserAgentForTab(activeTab)
            activeTab.session.reload()
            Log.i(TAG, "Desktop mode ${if (isDesktopMode) "ON" else "OFF"}: ua=$uaMode vp=$vpMode")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling desktop mode", e)
        }
    }

    fun toggleReaderMode() {
        if (!isReaderModeActive) {
            val js = "javascript:(function(){" +
                    "var title = document.querySelector('h1')?.innerText || document.title;" +
                    "var clone = (document.querySelector('main') || document.querySelector('article') || document.querySelector('#content') || document.querySelector('.content') || document.body).cloneNode(true);" +
                    "var unwantedSelectors = ['script','style','noscript','iframe','header','footer','nav','aside','form','button','input','select','textarea','.ads','.ad','.social','.share','.comments','.sidebar','.menu','.footer','.nav','.widget','.banner','.popup','.cookie','#disqus_thread','.disqus','.auth-box','.promo','.newsletter','.advertisement','.newsletter-signup','.post-sharing'];" +
                    "unwantedSelectors.forEach(function(s){ clone.querySelectorAll(s).forEach(function(el){ el.parentNode.removeChild(el); }); });" +
                    "var candidates = [];" +
                    "var paragraphs = clone.querySelectorAll('p, pre, code, blockquote, li');" +
                    "paragraphs.forEach(function(p){" +
                    "    var parent = p.parentNode;" +
                    "    if(!parent || parent.tagName === 'BODY') return;" +
                    "    if(!parent.score){" +
                    "        parent.score = 0;" +
                    "        if(parent.tagName === 'DIV') parent.score += 5;" +
                    "        else if(parent.tagName === 'ARTICLE') parent.score += 20;" +
                    "        else if(parent.tagName === 'SECTION') parent.score += 10;" +
                    "        candidates.push(parent);" +
                    "    }" +
                    "    var text = p.innerText.trim();" +
                    "    if(text.length > 20){ parent.score += Math.floor(text.length/50) + 1; }" +
                    "});" +
                    "candidates.forEach(function(c){" +
                    "    var links = c.querySelectorAll('a');" +
                    "    var linkLength = 0;" +
                    "    links.forEach(function(l){ linkLength += l.innerText.trim().length; });" +
                    "    var totalLength = c.innerText.trim().length;" +
                    "    if(totalLength > 0){" +
                    "        var ratio = linkLength / totalLength;" +
                    "        if(ratio > 0.4){ c.score -= 50; }" +
                    "    }" +
                    "});" +
                    "candidates.sort(function(a,b){ return b.score - a.score; });" +
                    "var best = candidates[0] || clone;" +
                    "var allowedTags = ['p','pre','code','blockquote','li','ul','ol','img','h1','h2','h3','h4','h5','h6','strong','em','span','b','i','a','table','thead','tbody','tr','th','td'];" +
                    "var cleanContentDiv = document.createElement('div');" +
                    "function cleanNode(node, parentDest){" +
                    "    if(node.nodeType === 3){ parentDest.appendChild(node.cloneNode(true)); return; }" +
                    "    if(node.nodeType === 1){" +
                    "        var tagName = node.tagName.toLowerCase();" +
                    "        if(allowedTags.indexOf(tagName) !== -1){" +
                    "            var newEl = document.createElement(tagName);" +
                    "            if(tagName === 'img'){ newEl.src = node.src; newEl.alt = node.alt; }" +
                    "            if(tagName === 'a'){ newEl.href = node.href; }" +
                    "            node.childNodes.forEach(function(child){ cleanNode(child, newEl); });" +
                    "            parentDest.appendChild(newEl);" +
                    "        } else {" +
                    "            node.childNodes.forEach(function(child){ cleanNode(child, parentDest); });" +
                    "        }" +
                    "    }" +
                    "}" +
                    "best.childNodes.forEach(function(child){ cleanNode(child, cleanContentDiv); });" +
                    "var wordCount = cleanContentDiv.innerText.split(/\\s+/).filter(function(w){ return w.length > 0; }).length;" +
                    "var readingTime = Math.max(1, Math.round(wordCount / 200));" +
                    "var headings = cleanContentDiv.querySelectorAll('h2, h3');" +
                    "var tocHtml = '';" +
                    "if(headings.length > 1){" +
                    "    tocHtml += '<div id=\"omni-reader-toc\" style=\"margin:20px 0;padding:16px;border-radius:12px;background:rgba(128,128,128,0.08);border:1px solid rgba(128,128,128,0.15);\"><div style=\"font-weight:bold;margin-bottom:10px;font-size:1.1em;display:flex;align-items:center;justify-content:space-between;cursor:pointer;\" onclick=\"var l = document.getElementById(\\'omni-toc-list\\'); l.style.display = l.style.display===\\'none\\'?\\'block\\':\\'none\\';\"><span>📖 Table of Contents</span><span style=\"font-size:0.8em;\">▼</span></div><ul id=\"omni-toc-list\" style=\"margin:0;padding-left:20px;display:none;list-style-type:square;line-height:1.8;\">';" +
                    "    headings.forEach(function(h, idx){" +
                    "        h.id = 'omni-heading-' + idx;" +
                    "        var indent = h.tagName.toLowerCase() === 'h3' ? 'margin-left: 15px;' : '';" +
                    "        tocHtml += '<li style=\"' + indent + '\"><a href=\"#' + h.id + '\" style=\"text-decoration:none;font-size:0.95em;\">' + h.innerText + '</a></li>';" +
                    "    });" +
                    "    tocHtml += '</ul></div>';" +
                    "}" +
                    "cleanContentDiv.querySelectorAll('pre code, pre').forEach(function(codeBlock){" +
                    "    var raw = codeBlock.innerHTML;" +
                    "    var html = raw" +
                    "        .replace(/\\b(const|let|var|function|return|import|export|class|extends|if|else|for|while|do|switch|case|break|continue|new|try|catch|finally|throw|typeof|instanceof|val|var|fun|def|print|echo|public|private|protected|static|final|interface|implements|package|void|int|double|float|char|boolean|byte|short|long|null|true|false)\\b/g, '<span style=\"color:#f92672;font-weight:bold;\">$1</span>')" +
                    "        .replace(/(\\/\\/[^\\n]*)/g, '<span style=\"color:#75715e;font-style:italic;\">$1</span>')" +
                    "        .replace(/(\\/\\*[\\s\\S]*?\\*\\/)/g, '<span style=\"color:#75715e;font-style:italic;\">$1</span>')" +
                    "        .replace(/(\".*?\"|\\'.*?\\'|\\`.*?\\`)/g, '<span style=\"color:#e6db74;\">$1</span>');" +
                    "    codeBlock.innerHTML = html;" +
                    "});" +
                    "var htmlPayload = '<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>' + title.replace(/\\'/g, \"\\\\'\") + '</title><style id=\"omni-reader-styles\"></style></head><body style=\"margin:0;padding:0;\"><div id=\"omni-reader-progress\" style=\"position:fixed;top:0;left:0;height:4px;width:0%;z-index:10000;transition:width 0.1s ease-out;\"></div><div id=\"omni-reader-container\"><h1 id=\"omni-reader-title\">' + title + '</h1><div id=\"omni-reader-meta\" style=\"font-size:0.88em;opacity:0.75;margin-bottom:24px;border-bottom:1px solid rgba(128,128,128,0.25);padding-bottom:12px;\">⏱️ ' + readingTime + ' min read &bull; ' + wordCount + ' words</div>' + tocHtml + cleanContentDiv.innerHTML + '</div></body></html>';" +
                    "document.open();" +
                    "document.write(htmlPayload);" +
                    "document.close();" +
                    "window.addEventListener('scroll', function(){" +
                    "    var winScroll = document.documentElement.scrollTop || document.body.scrollTop;" +
                    "    var height = document.documentElement.scrollHeight - document.documentElement.clientHeight;" +
                    "    var scrolled = (winScroll / height) * 100;" +
                    "    var bar = document.getElementById('omni-reader-progress');" +
                    "    if(bar){ bar.style.width = scrolled + '%'; }" +
                    "});" +
                    "})();"
            geckoSession.loadUri(js)
            isReaderModeActive = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                applyReaderSettings()
            }, 300)
        } else {
            reload()
            isReaderModeActive = false
            stopTts()
        }
    }

    fun applyReaderSettings() {
        val fontSizePx = readerFontSize.toString() + "px"
        val lineHeightVal = readerLineHeight
        val widthPx = when (readerWidth) {
            "Narrow" -> "500px"
            "Wide" -> "800px"
            else -> "650px"
        }
        val fontStack = when (readerFontFamily) {
            "Serif" -> "Georgia, 'Times New Roman', serif"
            "Sans-Serif" -> "'Helvetica Neue', Arial, sans-serif"
            "Monospace" -> "'Courier New', Courier, monospace"
            "Dyslexic" -> "OpenDyslexic, Comic Sans MS, cursive"
            else -> "system-ui, -apple-system, BlinkMacSystemFont, sans-serif" // System default
        }
        val (bgColor, textColor, linkColor) = when (readerTheme) {
            "Sepia" -> Triple("#F4ECD8", "#5B4636", "#7A4E2D")
            "Dark" -> Triple("#121212", "#E0E0E0", "#7BAFD4")
            else -> Triple("#FFFFFF", "#1A1A1A", "#0066CC")
        }
        val alignStyle = if (readerJustified) "justify" else "left"
        val letterSpacingVal = when (readerLetterSpacing) {
            "Wide" -> "0.08em"
            "Very Wide" -> "0.15em"
            else -> "normal"
        }
        val wordSpacingVal = when (readerWordSpacing) {
            "Wide" -> "0.15em"
            "Very Wide" -> "0.3em"
            else -> "normal"
        }

        val css = "* { font-family: $fontStack !important; } " +
                  "body { background-color: $bgColor !important; color: $textColor !important; } " +
                  "#omni-reader-progress { background-color: $linkColor !important; } " +
                  "#omni-reader-container { font-size: ${fontSizePx} !important; line-height: ${lineHeightVal} !important; max-width: $widthPx !important; text-align: $alignStyle !important; letter-spacing: $letterSpacingVal !important; word-spacing: $wordSpacingVal !important; margin: 0 auto; padding: 24px 20px 80px 20px; min-height: 100vh; box-sizing: border-box; } " +
                  "p, span, li, div, h1, h2, h3, h4, h5 { color: $textColor !important; } " +
                  "a { color: $linkColor !important; } " +
                  "img { max-width: 100% !important; height: auto !important; border-radius: 8px !important; } " +
                  "pre { background-color: #272822 !important; color: #f8f8f2 !important; padding: 16px !important; border-radius: 8px !important; overflow-x: auto !important; font-family: \\'Courier New\\', Courier, monospace !important; font-size: 0.9em !important; line-height: 1.5 !important; border: 1px solid #3e3d32 !important; } " +
                  "code { background-color: rgba(128,128,128,0.15) !important; color: #e74c3c !important; padding: 2px 6px !important; border-radius: 4px !important; font-family: \\'Courier New\\', Courier, monospace !important; font-size: 0.95em !important; } " +
                  "pre code { background-color: transparent !important; color: inherit !important; padding: 0 !important; border-radius: 0 !important; }"
        val escapedCss = css.replace("'", "\\'").replace("\n", " ")
        val js = "javascript:(function(){" +
                 "  var style = document.getElementById('omni-reader-styles');" +
                 "  if (style) { style.innerHTML = '$escapedCss'; }" +
                 "})();"
        geckoSession.loadUri(js)
    }

    fun updateReaderFontFamily(family: String) {
        readerFontFamily = family
        applyReaderSettings()
    }

    fun updateReaderWidth(width: String) {
        readerWidth = width
        applyReaderSettings()
    }

    fun increaseReaderLineHeight() {
        if (readerLineHeight < 2.4f) {
            readerLineHeight = (readerLineHeight + 0.2f).coerceAtMost(2.4f)
            applyReaderSettings()
        }
    }

    fun decreaseReaderLineHeight() {
        if (readerLineHeight > 1.2f) {
            readerLineHeight = (readerLineHeight - 0.2f).coerceAtLeast(1.2f)
            applyReaderSettings()
        }
    }

    fun increaseReaderFontSize() {
        if (readerFontSize < 32) {
            readerFontSize += 2
            applyReaderSettings()
        }
    }

    fun decreaseReaderFontSize() {
        if (readerFontSize > 12) {
            readerFontSize -= 2
            applyReaderSettings()
        }
    }

    fun setReaderThemeMode(theme: String) {
        readerTheme = theme
        applyReaderSettings()
    }

    fun toggleReaderJustified() {
        readerJustified = !readerJustified
        applyReaderSettings()
    }

    fun updateReaderLetterSpacing(spacing: String) {
        readerLetterSpacing = spacing
        applyReaderSettings()
    }

    fun updateReaderWordSpacing(spacing: String) {
        readerWordSpacing = spacing
        applyReaderSettings()
    }

    fun readAloudCurrentPage() {
        val js = "javascript:(function(){" +
                 "  var text = document.getElementById('omni-reader-container')?.innerText || document.body.innerText || '';" +
                 "  window.postMessage({ type: 'OMNI_CONSOLE_LOG', level: 'READER_TTS_CONTENT', message: text }, '*');" +
                 "})();"
        geckoSession.loadUri(js)
    }

    fun toggleIncognitoMode(context: Context) {
        val nextMode = !isIncognitoMode
        isIncognitoMode = nextMode
        
        val targetTabId = if (nextMode) activeIncognitoTabId else activeNormalTabId
        val targetTabExists = tabs.any { it.id == targetTabId && it.isIncognito == nextMode }
        
        if (targetTabExists && targetTabId != null) {
            selectTab(targetTabId)
        } else {
            val modeTabs = tabs.filter { it.isIncognito == nextMode }
            if (modeTabs.isNotEmpty()) {
                selectTab(modeTabs.first().id)
            } else {
                createNewTab(context, "about:blank")
            }
        }
    }



    private fun injectZoomEnabler() {
        val js = "javascript:(function() {" +
                 "  var meta = document.querySelector('meta[name=viewport]');" +
                 "  if (meta) {" +
                 "    var content = meta.getAttribute('content');" +
                 "    if (content) {" +
                 "      content = content.replace(/user-scalable\\s*=\\s*no/g, 'user-scalable=yes');" +
                 "      content = content.replace(/maximum-scale\\s*=\\s*[0-9.]+/g, 'maximum-scale=5.0');" +
                 "      meta.setAttribute('content', content);" +
                 "    }" +
                 "  } else {" +
                 "    meta = document.createElement('meta');" +
                 "    meta.name = 'viewport';" +
                 "    meta.content = 'width=device-width, initial-scale=1.0, user-scalable=yes, maximum-scale=5.0';" +
                 "    document.getElementsByTagName('head')[0].appendChild(meta);" +
                 "  }" +
                 "  document.body.style.touchAction = 'pan-x pan-y';" +
                 "})();"
        geckoSession.loadUri(js)
    }

    fun installExtensionFromUrl(url: String, context: Context) {
        val runtime = geckoRuntime ?: return
        Log.d(TAG, "Installing external extension from URL: $url")
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Installing extension...", Toast.LENGTH_SHORT).show()
        }

        runtime.webExtensionController.install(url)
            .accept(
                { ext ->
                    Log.i(TAG, "Successfully installed extension: ${ext?.id}")
                    if (ext != null) {
                        runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                        runtime.webExtensionController.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                        ext.setActionDelegate(object : WebExtension.ActionDelegate {

                            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                                registerExtensionAction(extension.id, session, action)
                            }
                            override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                                registerExtensionAction(extension.id, session, action)
                            }
                            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                return handleExtensionOpenPopup(extension, action)
                            }
                            override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                return handleExtensionOpenPopup(extension, action)
                            }
                        })
                    }
                    syncUserExtensions()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🧩 Extension installed: ${ext?.id}", Toast.LENGTH_LONG).show()
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to install extension from: $url", error)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "❌ Installation failed: ${error?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
    }

    fun syncUserExtensions() {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.list()
            .accept(
                { list ->
                    val coreIds = listOf(UBLOCK_ID, GRABBER_ID, "omni-universal-copy@omnibrowser.app", AI_BLOCKER_ID, "omni-agent@omnibrowser.app")
                    val filtered = list?.filter { it.id !in coreIds } ?: emptyList()
                    val leftoverAgent = list?.find { it.id == "omni-agent@omnibrowser.app" }
                    if (leftoverAgent != null) {
                        Log.i(TAG, "Leftover Omni Agent extension found in profile database. Uninstalling...")
                        runtime.webExtensionController.uninstall(leftoverAgent)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        filtered.forEach { ext ->
                            runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                            ext.setActionDelegate(object : WebExtension.ActionDelegate {

                                override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                                    registerExtensionAction(extension.id, session, action)
                                }
                                override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                                    registerExtensionAction(extension.id, session, action)
                                }
                                override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                    return handleExtensionOpenPopup(extension, action)
                                }
                                override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                    return handleExtensionOpenPopup(extension, action)
                                }
                            })
                        }
                        userExtensions.clear()
                        userExtensions.addAll(filtered)
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to list extensions", error)
                }
            )
    }

    fun uninstallUserExtension(extension: WebExtension, context: Context) {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.uninstall(extension)
            .accept(
                {
                    Log.i(TAG, "Successfully uninstalled extension: ${extension.id}")
                    syncUserExtensions()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "🗑️ Extension removed: ${extension.id}", Toast.LENGTH_SHORT).show()
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to uninstall extension: ${extension.id}", error)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "❌ Uninstallation failed: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
    }


    val bookmarksList = mutableStateListOf<BookmarkEntry>()

    
    private fun loadBookmarks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_bookmarks.json")
            if (!file.exists()) return@launch
            try {
                val jsonArray = JSONArray(file.readText())
                val temp = mutableListOf<BookmarkEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    temp.add(BookmarkEntry(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
                withContext(Dispatchers.Main) {
                    bookmarksList.clear()
                    bookmarksList.addAll(temp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bookmarks", e)
            }
        }
    }
    
    private fun saveBookmarks(context: Context) {
        val bookmarksSnapshot = bookmarksList.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_bookmarks.json")
            try {
                val jsonArray = JSONArray()
                bookmarksSnapshot.forEach { entry ->
                    jsonArray.put(JSONObject().apply {
                        put("title", entry.title)
                        put("url", entry.url)
                        put("timestamp", entry.timestamp)
                    })
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bookmarks", e)
            }
        }
    }
    
    fun addToBookmarks(title: String, url: String) {
        val context = appContext ?: return
        if (url == "about:blank" || url.trim().isEmpty()) return
        
        bookmarksList.removeAll { it.url == url }
        bookmarksList.add(0, BookmarkEntry(title, url, System.currentTimeMillis()))
        saveBookmarks(context)
    }
    
    fun removeBookmark(url: String) {
        val context = appContext ?: return
        bookmarksList.removeAll { it.url == url }
        saveBookmarks(context)
    }

    fun clearAllBookmarks() {
        val context = appContext ?: return
        bookmarksList.clear()
        saveBookmarks(context)
    }
    
    fun isBookmarked(url: String): Boolean {
        return bookmarksList.any { it.url == url }
    }

    val shortcutsList = mutableStateListOf<HomeShortcut>()
    
    private fun loadShortcuts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_shortcuts.json")
            if (!file.exists()) {
                val defaultList = listOf(
                    HomeShortcut("rebelroot", "RebelRoot", "https://www.rebelroot.xyz/omnibrowser", isPermanent = true),
                    HomeShortcut("twitter", "Twitter", "https://twitter.com"),
                    HomeShortcut("spotify", "Spotify", "https://spotify.com"),
                    HomeShortcut("amazon", "Amazon", "https://amazon.com"),
                    HomeShortcut("pinterest", "Pinterest", "https://pinterest.com"),
                    HomeShortcut("downloads", "Downloads", "downloads", isFeature = true),
                    HomeShortcut("history", "History", "history", isFeature = true),
                    HomeShortcut("bookmarks", "Bookmarks", "bookmarks", isFeature = true),
                    HomeShortcut("incognito", "Incognito", "incognito", isFeature = true)
                )
                withContext(Dispatchers.Main) {
                    shortcutsList.clear()
                    shortcutsList.addAll(defaultList)
                }
                saveShortcuts(context)
                return@launch
            }
            try {
                val jsonArray = JSONArray(file.readText())
                val temp = mutableListOf<HomeShortcut>()
                
                // Always ensure the permanent RebelRoot shortcut is at the beginning
                temp.add(HomeShortcut("rebelroot", "RebelRoot", "https://www.rebelroot.xyz/omnibrowser", isPermanent = true))
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val title = obj.optString("title", "")
                    val url = obj.optString("url", "")
                    
                    // Skip duplicate/old RebelRoot entries
                    if (id == "rebelroot" || url == "https://www.rebelroot.xyz/omnibrowser" || title.equals("RebelRoot", ignoreCase = true)) {
                        continue
                    }
                    
                    temp.add(HomeShortcut(
                        id = id,
                        title = title,
                        url = url,
                        isFeature = obj.optBoolean("isFeature", false),
                        isPermanent = obj.optBoolean("isPermanent", false)
                    ))
                }
                withContext(Dispatchers.Main) {
                    shortcutsList.clear()
                    shortcutsList.addAll(temp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading shortcuts", e)
            }
        }
    }
    
    fun saveShortcuts(context: Context) {
        val shortcutsSnapshot = shortcutsList.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "browser_shortcuts.json")
            try {
                val jsonArray = JSONArray()
                shortcutsSnapshot.forEach { shortcut ->
                    jsonArray.put(JSONObject().apply {
                        put("id", shortcut.id)
                        put("title", shortcut.title)
                        put("url", shortcut.url)
                        put("isFeature", shortcut.isFeature)
                        put("isPermanent", shortcut.isPermanent)
                    })
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving shortcuts", e)
            }
        }
    }
    
    fun addShortcut(title: String, url: String) {
        val context = appContext ?: return
        val id = UUID.randomUUID().toString()
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        
        // Prevent adding custom shortcuts that point to RebelRoot or have the title RebelRoot
        if (formattedUrl == "https://www.rebelroot.xyz/omnibrowser" || title.equals("RebelRoot", ignoreCase = true)) {
            return
        }
        
        val exists = shortcutsList.any { it.url == formattedUrl }
        if (exists) {
            Toast.makeText(context, "Shortcut already exists", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add to index 1 (just after permanent RebelRoot shortcut) if RebelRoot is at index 0
        if (shortcutsList.isNotEmpty() && shortcutsList[0].isPermanent) {
            shortcutsList.add(1, HomeShortcut(id, title, formattedUrl))
        } else {
            shortcutsList.add(0, HomeShortcut(id, title, formattedUrl))
        }
        saveShortcuts(context)
    }

    fun editShortcut(shortcut: HomeShortcut, newTitle: String, newUrl: String) {
        if (shortcut.isPermanent) return
        val context = appContext ?: return
        var formattedUrl = newUrl.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        val idx = shortcutsList.indexOfFirst { it.id == shortcut.id }
        if (idx != -1) {
            shortcutsList[idx] = shortcut.copy(title = newTitle, url = formattedUrl)
            saveShortcuts(context)
        }
    }
    
    fun deleteShortcut(shortcut: HomeShortcut) {
        if (shortcut.isPermanent) return
        val context = appContext ?: return
        shortcutsList.removeAll { it.id == shortcut.id }
        saveShortcuts(context)
    }

    val newsArticles = mutableStateListOf<NewsArticle>()
    var selectedNewsCategory by mutableStateOf("Trending")
    var isNewsLoading by mutableStateOf(false)

    fun fetchNews(category: String = "Trending") {
        selectedNewsCategory = category
        isNewsLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<NewsArticle>()
            try {
                // Determine localized parameters based on selected language code
                val (hl, gl, ceid) = when (selectedLanguageCode) {
                    "hi" -> Triple("hi", "IN", "IN:hi")
                    "es" -> Triple("es-419", "MX", "MX:es-419")
                    "fr" -> Triple("fr", "FR", "FR:fr")
                    "de" -> Triple("de", "DE", "DE:de")
                    "zh" -> Triple("zh-CN", "CN", "CN:zh-Hans")
                    "ja" -> Triple("ja", "JP", "JP:ja")
                    "ru" -> Triple("ru", "RU", "RU:ru")
                    "pt" -> Triple("pt-BR", "BR", "BR:pt")
                    else -> Triple("en-US", "US", "US:en")
                }

                val topicPath = when (category) {
                    "World"         -> "headlines/section/topic/WORLD"
                    "Technology"    -> "headlines/section/topic/TECHNOLOGY"
                    "Sports"        -> "headlines/section/topic/SPORTS"
                    "Business"      -> "headlines/section/topic/BUSINESS"
                    "Science"       -> "headlines/section/topic/SCIENCE"
                    "Entertainment" -> "headlines/section/topic/ENTERTAINMENT"
                    "Health"        -> "headlines/section/topic/HEALTH"
                    else            -> ""
                }

                val rssUrl = if (topicPath.isNotEmpty()) {
                    "https://news.google.com/rss/$topicPath?hl=$hl&gl=$gl&ceid=$ceid"
                } else {
                    "https://news.google.com/rss?hl=$hl&gl=$gl&ceid=$ceid"
                }

                val conn = java.net.URL(rssUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout    = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                val parser = android.util.Xml.newPullParser()
                parser.setInput(conn.inputStream, "UTF-8")

                var eventType   = parser.eventType
                var insideItem  = false
                var currentTag  = ""
                var title       = ""
                var link        = ""
                var pubDate     = ""
                var description = ""
                var source      = ""
                var sourceUrl   = ""

                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> {
                            currentTag = parser.name ?: ""
                            if (currentTag.equals("item", ignoreCase = true)) {
                                insideItem = true
                            }
                            if (insideItem && currentTag.equals("source", ignoreCase = true)) {
                                sourceUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                        }
                        org.xmlpull.v1.XmlPullParser.TEXT -> {
                            if (insideItem) {
                                val text = parser.text ?: ""
                                if (text.isNotEmpty()) {
                                    when (currentTag.lowercase()) {
                                        "title"       -> title       += text
                                        "link"        -> link        += text
                                        "pubdate"     -> pubDate     += text
                                        "description" -> description += text
                                        "source"      -> source      += text
                                    }
                                }
                            }
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> {
                            if ((parser.name ?: "").equals("item", ignoreCase = true)) {
                                insideItem = false

                                val rawTitle = title.trim()
                                val rawLink  = link.trim()

                                if (rawTitle.isNotEmpty() && rawLink.isNotEmpty()) {
                                    // Strip trailing source info from Google News titles
                                    val cleanTitle = if (rawTitle.contains(" - "))
                                        rawTitle.substringBeforeLast(" - ").trim()
                                    else rawTitle.trim()

                                    // 2. Reject titles that are too short or look like junk
                                    if (cleanTitle.length < 12) {
                                        title = ""; link = ""; pubDate = ""
                                        description = ""; source = ""; sourceUrl = ""
                                        currentTag = ""
                                        eventType = parser.next()
                                        continue
                                    }

                                    // 3. Source name: prefer what Google appended; fall back to <source> tag
                                    val sourceName = if (rawTitle.contains(" - "))
                                        rawTitle.substringAfterLast(" - ").trim()
                                    else source.trim().ifEmpty { "News" }

                                    // 4. Parse date — format: "Tue, 16 Jun 2026 05:06:59 GMT"
                                    val cleanDate = try {
                                        val parts = pubDate.trim().split(" ")
                                        // parts: [Tue,] [16] [Jun] [2026] ...
                                        if (parts.size >= 4) "${parts[2]} ${parts[3]}" else pubDate.trim()
                                    } catch (e: Exception) { pubDate.trim() }

                                    // 5. Extract image from description
                                    //    Google News HTML-encodes the description, so unescape first
                                    val decodedDesc = android.text.Html.fromHtml(
                                        description, android.text.Html.FROM_HTML_MODE_COMPACT
                                    ).toString()

                                    // Try to get src from the decoded HTML (plain text won't have tags,
                                    // but the raw description string still has encoded HTML we can scan)
                                    val imgMatch = "<img[^>]+src=[\"']([^\"']+)[\"']"
                                        .toRegex(RegexOption.IGNORE_CASE)
                                        .find(description)
                                    val encodedImgMatch = "src=(?:&quot;|%22|\"')([^&\"']+)(?:&quot;|%22|\"')"
                                        .toRegex(RegexOption.IGNORE_CASE)
                                        .find(description)

                                    var imageUrl = imgMatch?.groupValues?.getOrNull(1)?.trim()
                                        ?: encodedImgMatch?.groupValues?.getOrNull(1)?.trim()
                                        ?: ""

                                    // 6. Fallback: use source domain favicon (64 px — decent quality)
                                    if (imageUrl.isEmpty()) {
                                        val domain = extractDomain(sourceUrl.ifEmpty { null }, sourceName)
                                        imageUrl = "https://www.google.com/s2/favicons?sz=64&domain=$domain"
                                    }

                                    // 7. Deduplicate by title
                                    if (list.none { it.title.equals(cleanTitle, ignoreCase = true) }) {
                                        list.add(NewsArticle(
                                            title    = cleanTitle,
                                            link     = rawLink,
                                            source   = sourceName,
                                            pubDate  = cleanDate,
                                            imageUrl = imageUrl
                                        ))
                                    }
                                }

                                title = ""; link = ""; pubDate = ""
                                description = ""; source = ""; sourceUrl = ""
                            }
                            currentTag = ""
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching news RSS feed: ${e.message}", e)
            }

            launch(Dispatchers.Main) {
                newsArticles.clear()
                // Final quality pass: remove anything with a suspiciously short title
                newsArticles.addAll(list.filter { it.title.length >= 12 })
                isNewsLoading = false
            }
        }
    }

    /** Extract a clean domain from the source URL attribute or fall back to a lookup table. */
    private fun extractDomain(sourceUrl: String?, sourceName: String): String {
        if (!sourceUrl.isNullOrEmpty()) {
            try {
                val host = Uri.parse(sourceUrl).host ?: ""
                return if (host.startsWith("www.")) host.substring(4) else host
            } catch (_: Exception) { }
        }
        return getDomainForSource(sourceName)
    }
    private fun getDomainForSource(sourceName: String): String {
        val clean = sourceName.trim().lowercase()
            .replace(" ", "")
            .replace("[^a-z0-9]".toRegex(), "")
        if (clean.isEmpty()) return "google.com"
        
        return when (clean) {
            "wsj" -> "wsj.com"
            "bbc" -> "bbc.com"
            "bbcnews" -> "bbc.com"
            "ap" -> "apnews.com"
            "associatedpress" -> "apnews.com"
            "thenewyorktimes" -> "nytimes.com"
            "newyorktimes" -> "nytimes.com"
            "thewashingtonpost" -> "washingtonpost.com"
            "washingtonpost" -> "washingtonpost.com"
            "usatoday" -> "usatoday.com"
            "thenextweb" -> "thenextweb.com"
            "theverge" -> "theverge.com"
            "techcrunch" -> "techcrunch.com"
            "9to5mac" -> "9to5mac.com"
            "macrumors" -> "macrumors.com"
            "cnet" -> "cnet.com"
            "gizmodo" -> "gizmodo.com"
            "wired" -> "wired.com"
            "forbes" -> "forbes.com"
            "bloomberg" -> "bloomberg.com"
            "cnbc" -> "cnbc.com"
            "time" -> "time.com"
            "reuters" -> "reuters.com"
            "politico" -> "politico.com"
            "nbc" -> "nbcnews.com"
            "cbs" -> "cbsnews.com"
            "abc" -> "abcnews.go.com"
            "cnn" -> "cnn.com"
            else -> {
                "$clean.com"
            }
        }
    }

    private var tts: TextToSpeech? = null
    var isTtsPlaying by mutableStateOf(false)
    
    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "TTS Engine successfully initialized.")
                }
            }
        }
    }
    
    fun speakText(text: String) {
        val engine = tts ?: return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "omni_tts")
        isTtsPlaying = true
    }
    
    fun stopTts() {
        tts?.stop()
        isTtsPlaying = false
    }

    fun installWebAppShortcut(context: Context, title: String, url: String) {
        val appCtx = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val domain = try {
                Uri.parse(url).host ?: url
            } catch (e: Exception) {
                url
            }
            val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$domain"
            
            var bitmap: android.graphics.Bitmap? = null
            try {
                val loader = coil.ImageLoader(appCtx)
                val request = coil.request.ImageRequest.Builder(appCtx)
                    .data(faviconUrl)
                    .allowHardware(false) // Must be false to convert to Bitmap safely
                    .build()
                val result = loader.execute(request)
                bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching favicon for webapp shortcut", e)
            }
            
            launch(Dispatchers.Main) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(appCtx)) {
                    val intent = Intent(appCtx, com.rebelroot.omni.MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    
                    val icon = if (bitmap != null) {
                        IconCompat.createWithBitmap(bitmap)
                    } else {
                        IconCompat.createWithResource(appCtx, com.rebelroot.omni.R.mipmap.ic_launcher)
                    }
                    
                    val shortcutInfo = ShortcutInfoCompat.Builder(appCtx, url)
                        .setShortLabel(title)
                        .setLongLabel(title)
                        .setIcon(icon)
                        .setIntent(intent)
                        .build()
                    
                    ShortcutManagerCompat.requestPinShortcut(appCtx, shortcutInfo, null)
                    Toast.makeText(appCtx, "Adding webapp shortcut with website logo...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "Pinning shortcuts is not supported by your launcher", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleEditMode() {
        val activeId = activeTabId ?: return
        val idx = tabs.indexOfFirst { it.id == activeId }
        if (idx == -1) return
        val activeTab = tabs[idx]
        
        val newEditMode = !activeTab.isEditModeEnabled
        tabs[idx] = activeTab.copy(isEditModeEnabled = newEditMode)
        
        if (newEditMode) {
            // Turn on designMode and focus the body so the cursor appears immediately
            geckoSession.loadUri(
                "javascript:(function(){" +
                "  document.designMode = 'on';" +
                "  document.body && document.body.focus();" +
                "})();"
            )
            // Delay to let the bottom sheet dismiss animation finish, then request
            // focus on the GeckoView and show the soft keyboard.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val geckoView = activeGeckoViewRef?.get()
                if (geckoView != null) {
                    geckoView.requestFocus()
                    val imm = geckoView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(geckoView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    Log.d(TAG, "toggleEditMode: keyboard requested on GeckoView")
                }
            }, 300)
        } else {
            // Turn off designMode and hide the keyboard
            geckoSession.loadUri("javascript:(function(){ document.designMode = 'off'; })();")
            val geckoView = activeGeckoViewRef?.get()
            if (geckoView != null) {
                val imm = geckoView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(geckoView.windowToken, 0)
            }
        }
    }

    

    fun printCurrentPage(context: Context) {
        val activeId = activeTabId ?: return
        val activeTab = tabs.find { it.id == activeId } ?: return

        // Always resolve the activity from MainActivity companion — the Compose LocalContext
        // is a configuration-wrapped ContextImpl that fails instanceof Activity checks.
        val activity = com.rebelroot.omni.MainActivity.getActiveActivity() ?: run {
            Log.e(TAG, "printCurrentPage: no active MainActivity found, aborting print")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Cannot open print dialog right now", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Build print CSS based on the user's PDF theme setting.
        // We use evaluateJS (not loadUri) so the CSS is injected into the LIVE page
        // without triggering a new navigation cycle, which would block saveAsPdf().
        val isDark = when (pdfExportTheme) {
            "dark"  -> true
            "light" -> false
            else    -> isDarkThemeEnabled   // "default" → follow app theme
        }
        val printCss = if (isDark) {
            "@media print { " +
            "  * { background-color: #121212 !important; color: #E0E0E0 !important; " +
            "      border-color: #333 !important; -webkit-print-color-adjust: exact !important; " +
            "      color-adjust: exact !important; } " +
            "  a, a * { color: #8AB4F8 !important; } " +
            "  img, video, canvas { filter: brightness(0.8) !important; background-color: transparent !important; } " +
            "}"
        } else {
            "@media print { " +
            "  * { background-color: #FFFFFF !important; color: #111111 !important; " +
            "      border-color: #E2E8F0 !important; -webkit-print-color-adjust: exact !important; " +
            "      color-adjust: exact !important; } " +
            "  a, a * { color: #1A0DAB !important; } " +
            "  img, video, canvas { background-color: transparent !important; } " +
            "}"
        }

        // Escape the CSS for safe embedding in a JS string literal
        val escapedCss = printCss
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")

        // JS that idempotently injects / replaces the <style id="omni-print-style"> tag
        val injectCssJs = """
            (function() {
                var el = document.getElementById('omni-print-style');
                if (!el) {
                    el = document.createElement('style');
                    el.id = 'omni-print-style';
                    (document.head || document.documentElement).appendChild(el);
                }
                el.textContent = '$escapedCss';
            })();
        """.trimIndent()

        // Inject CSS, then after a short settle delay call saveAsPdf
        val doSavePdf: () -> Unit = {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    activeTab.session.saveAsPdf().accept(
                        { inputStream ->
                            if (inputStream != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    try {
                                        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                                        val printAdapter = org.mozilla.geckoview.GeckoViewPrintDocumentAdapter(inputStream, activity)
                                        printManager.print("Omni Browser — Print", printAdapter, android.print.PrintAttributes.Builder().build())
                                        Log.i(TAG, "printCurrentPage: PrintManager.print() called successfully")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "printCurrentPage: PrintManager error", e)
                                        android.widget.Toast.makeText(activity, "Print failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Log.e(TAG, "printCurrentPage: saveAsPdf returned null stream")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(activity, "Could not generate PDF for this page", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        { error ->
                            Log.e(TAG, "printCurrentPage: saveAsPdf error: ${error?.message}")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(activity, "PDF generation failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "printCurrentPage: exception calling saveAsPdf", e)
                }
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                // loadUri("javascript:...") executes JS in the current page context without navigating
                activeTab.session.loadUri("javascript:$injectCssJs")
                Log.i(TAG, "printCurrentPage: CSS injected via javascript: URI (theme=$pdfExportTheme)")
            } catch (e: Exception) {
                Log.w(TAG, "printCurrentPage: JS injection failed (non-fatal), proceeding without theme CSS: $e")
            }
            // Small delay to let the browser apply the injected stylesheet before rendering to PDF
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ doSavePdf() }, 80)
        }
    }


    sealed interface UpdateCheckResult {
        data class NewUpdateAvailable(val versionName: String, val playStoreUrl: String) : UpdateCheckResult
        object NoUpdateAvailable : UpdateCheckResult
        data class Error(val message: String) : UpdateCheckResult
    }

    fun checkAppUpdates(context: Context, onResult: (UpdateCheckResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://raw.githubusercontent.com/rebelroot/omni-browser/main/version.json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.connect()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val serverVersionName = json.getString("versionName")
                    val serverVersionCode = json.optInt("versionCode", 0)
                    val updateUrl = json.optString("updateUrl", "market://details?id=com.rebelroot.omni")

                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        pInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode.toLong()
                    }

                    if (serverVersionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            onResult(UpdateCheckResult.NewUpdateAvailable(serverVersionName, updateUrl))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(UpdateCheckResult.NoUpdateAvailable)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(UpdateCheckResult.Error("Server returned HTTP ${connection.responseCode}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                withContext(Dispatchers.Main) {
                    onResult(UpdateCheckResult.Error(e.localizedMessage ?: "Connection error"))
                }
            }
        }
    }



    fun sendFeedbackToTelegram(
        name: String,
        email: String,
        rating: Int,
        message: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://rebelroot-backend.parasdevprojects.workers.dev/api/feedback")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val jsonPayload = """
                    {
                        "name": ${escapeJson(name)},
                        "email": ${escapeJson(email)},
                        "rating": "${rating}",
                        "product": "Omni Browser",
                        "message": ${escapeJson(message)}
                    }
                """.trimIndent()
                
                conn.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                
                val code = conn.responseCode
                if (code in 200..299) {
                    withContext(Dispatchers.Main) {
                        onResult(true, null)
                    }
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP Error $code"
                    withContext(Dispatchers.Main) {
                        onResult(false, errorMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage)
                }
            }
        }
    }
    
    private fun escapeJson(str: String): String {
        val escaped = str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    override fun onCleared() {
        super.onCleared()
        dismissExtensionPopup()
        translationManager.close()
        tts?.shutdown()
    }
}

data class ContextMenuElement(
    val linkUri: String?,
    val srcUri: String?,
    val linkText: String?
)
