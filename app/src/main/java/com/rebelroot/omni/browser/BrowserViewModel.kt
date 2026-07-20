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
import androidx.annotation.Keep
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.browser.extensions.UniversalCopyManager
import com.rebelroot.omni.browser.extensions.BuiltInExtensionManager
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

class BrowserViewModel : ViewModel() {

    companion object {
        internal const val TAG = "BrowserViewModel"

        internal const val GRABBER_ID = "omni-media-grabber@omnibrowser.app"
        internal const val AI_BLOCKER_ID = "omni-ai-blocker@omnibrowser.app"

        /** Known ad popup / pop-under network domains.
         *  Popups opening any of these URLs are silently blocked regardless of user gesture. */
        /** Known ad popup / pop-under network domains and suspicious host keywords.
         *  Checked via String.contains() so subdomains are covered automatically. */
        internal val POPUP_AD_DOMAINS = setOf(
            // Pop-under networks
            "popads.net", "popcash.net", "popunder.net", "popmyads.com",
            "pop.network", "popmagic.com", "trafficshop.com",
            // Push / redirect ad networks
            "exoclick.com", "trafficjunky.net", "juicyads.com",
            "adsterra.com", "propellerads.com", "hilltopads.net",
            "clickadu.com", "evadav.com", "megapush.com",
            "push.house", "richpush.co", "pushground.com",
            "mgpusher.com", "pu.sh",
            // URL shorteners / cloakers used by ads
            "adf.ly", "j.gs", "link.tl", "linkvertise.com", "hrefli.com",
            "exe.io", "za.gl", "fc.lc", "shrinke.me",
            // Traffic / redirect brokers
            "trafficfactory.biz", "tsyndicate.com", "doublelift.net",
            "adskeeper.com", "voluumtrk.com", "atominik.com",
            "mondoagency.net", "traffik.io", "traffective.com",
            // Ad infrastructure
            "adnxs.com", "openx.net", "rubiconproject.com",
            "doubleclick.net", "googlesyndication.com", "adtech.de",
            "bidswitch.net", "contextweb.com", "casalemedia.com",
            "pubmatic.com", "quantserve.com", "scorecardresearch.com",
            // Malvertising / fake-update / browser-hijacker patterns
            "cdn77.org/ad", "go2cloud.org", "bestads.com",
            "clickfunnel.net", "adclickfunnels.com", "cpmrevenuegate.com",
            "revcontent.com", "taboola.com", "outbrain.com",
            "zergnet.com", "mgid.com", "shareaholic.com"
        )

        /** Suspicious sub-strings found in popup/redirect hostnames. */
        internal val POPUP_HOST_KEYWORDS = listOf(
            "adserver", "adsystem", "adservice", "adnserver",
            "clicksmart", "fastclick", "popunder", "popads",
            "redirector", "onclick", "adcash", "adclick",
            "track.", "trk.", "clk.", "redir.", "go.", "exit."
        )
        

        val POPUP_BLOCKER_ENABLED_KEY = booleanPreferencesKey("popup_blocker_enabled")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
        val AI_BLOCKER_ENABLED_KEY = booleanPreferencesKey("ai_blocker_enabled")
        val NATIVE_PLAYER_ENABLED_KEY = booleanPreferencesKey("native_player_enabled")
        val YOUTUBE_ENABLED_KEY = booleanPreferencesKey("youtube_enabled")
        val MEDIA_GRABBER_ENABLED_KEY = booleanPreferencesKey("media_grabber_enabled")
        val CUSTOM_VPN_CONFIG_KEY = stringPreferencesKey("custom_vpn_config")
        val SEARCH_ENGINE_KEY = stringPreferencesKey("default_search_engine")
        val CUSTOM_SEARCH_URL_KEY = stringPreferencesKey("custom_search_url")
        val CUSTOM_SEARCH_ENGINES_KEY = stringPreferencesKey("custom_search_engines")
        val DARK_THEME_ENABLED_KEY = booleanPreferencesKey("dark_theme_enabled")
        val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
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
        val CUSTOM_ICON_PATH_KEY = stringPreferencesKey("custom_icon_path")
        val BROWSER_WALLPAPER_URI_KEY = stringPreferencesKey("browser_wallpaper_uri")
        val CHANGE_WALLPAPER_DAILY_KEY = booleanPreferencesKey("change_wallpaper_daily")
        val SHOW_DISCOVER_FEED_KEY = booleanPreferencesKey("show_discover_feed")
        val SHOW_BOTTOM_NAV_BAR_KEY = booleanPreferencesKey("show_bottom_nav_bar")
        val CHROME_NAV_BAR_KEY = booleanPreferencesKey("chrome_nav_bar_enabled")
        val SHOW_HOME_LOGO_KEY = booleanPreferencesKey("show_home_logo")
        val SHOW_HOME_SHORTCUTS_KEY = booleanPreferencesKey("show_home_shortcuts")
        val WALLPAPER_DIM_KEY = floatPreferencesKey("wallpaper_dim")
        val WALLPAPER_BLUR_KEY = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_SCALE_KEY = floatPreferencesKey("wallpaper_scale")
        val WALLPAPER_OFFSET_X_KEY = floatPreferencesKey("wallpaper_offset_x")
        val WALLPAPER_OFFSET_Y_KEY = floatPreferencesKey("wallpaper_offset_y")
        val QUICK_TOOLS_ORDER_KEY = stringPreferencesKey("quick_tools_order")



        // Native Player Settings Keys
        val PLAYER_DEFAULT_QUALITY_KEY = stringPreferencesKey("player_default_quality")
        val PLAYER_AUTOPLAY_KEY = booleanPreferencesKey("player_autoplay")
        val PLAYER_LOOP_KEY = booleanPreferencesKey("player_loop")
        val PLAYER_BRIGHTNESS_GESTURE_KEY = booleanPreferencesKey("player_brightness_gesture")
        val PLAYER_VOLUME_GESTURE_KEY = booleanPreferencesKey("player_volume_gesture")
        val PLAYER_RESUME_PLAYBACK_KEY = booleanPreferencesKey("player_resume_playback")
        val PLAYER_BACKGROUND_PLAYBACK_KEY = booleanPreferencesKey("player_background_playback")
        val EXTENSION_ORDER_KEY = stringPreferencesKey("extension_order")
        val EXTENSION_VIEW_MODE_KEY = stringPreferencesKey("extension_view_mode")
        
        val COOKIE_BEHAVIOR_KEY = androidx.datastore.preferences.core.intPreferencesKey("cookie_behavior")
        val DO_NOT_TRACK_KEY = booleanPreferencesKey("do_not_track")
        val SAFE_BROWSING_LEVEL_KEY = androidx.datastore.preferences.core.intPreferencesKey("safe_browsing_level")
        val PRELOAD_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("preload_pages")
        val LOCK_INCOGNITO_KEY = booleanPreferencesKey("lock_incognito")
        val COMPROMISED_PASSWORD_WARNING_KEY = booleanPreferencesKey("compromised_password_warning")
        val HTTPS_ONLY_MODE_KEY = booleanPreferencesKey("https_only_mode")
        val DEV_NOTES_OVERVIEW_SEEN_KEY = booleanPreferencesKey("dev_notes_overview_seen")
        val UI_SCALE_KEY = floatPreferencesKey("ui_scale")
        
        val TAB_LAYOUT_MODE_KEY = stringPreferencesKey("tab_layout_mode")
        val AUTO_CLOSE_TABS_DAYS_KEY = androidx.datastore.preferences.core.intPreferencesKey("auto_close_tabs_days")
        val OPEN_TABS_IN_BACKGROUND_KEY = booleanPreferencesKey("open_tabs_in_background")
        val ACCESSIBILITY_TEXT_SCALE_KEY = floatPreferencesKey("accessibility_text_scale")
        val ACCESSIBILITY_FORCE_ZOOM_KEY = booleanPreferencesKey("accessibility_force_zoom")
        val ACCESSIBILITY_HIGH_CONTRAST_KEY = booleanPreferencesKey("accessibility_high_contrast")
        val TAB_GROUPS_FILE = "browser_tab_groups.json"
        
        val DEFAULT_GEOLOCATION_KEY = stringPreferencesKey("default_geolocation")
        val DEFAULT_CAMERA_KEY = stringPreferencesKey("default_camera")
        val DEFAULT_MICROPHONE_KEY = stringPreferencesKey("default_microphone")
        val DEFAULT_NOTIFICATIONS_KEY = stringPreferencesKey("default_notifications")
        val DEFAULT_JAVASCRIPT_KEY = booleanPreferencesKey("default_javascript")
        val DEFAULT_AUTOPLAY_KEY = booleanPreferencesKey("default_autoplay")
        val SITE_PERMISSIONS_FILE = "browser_site_permissions.json"

        @Volatile
        @Keep
        internal var geckoRuntime: GeckoRuntime? = null
    }

    /** Exposed to the UI so a native-library load failure renders GeckoErrorScreen
     *  instead of a silent blank/black screen. Set to a non-null message when
     *  [getGeckoRuntime] catches a Throwable (e.g. UnsatisfiedLinkError from dlopen). */
    var geckoRuntimeError by mutableStateOf<String?>(null)

    // Engine Session & Runtime
    var geckoSession by mutableStateOf(GeckoSession())
        private set
    var isIncognitoMode by mutableStateOf(false)
        private set

    @get:Keep
    val runtime: GeckoRuntime? get() = geckoRuntime

    // Extension Action System (Compose-friendly maps & states)
    val extensionActions = mutableStateMapOf<String, WebExtension.Action>()
    val defaultExtensionActions = mutableStateMapOf<String, WebExtension.Action>()
    val sessionExtensionActions = mutableStateMapOf<String, MutableMap<String, WebExtension.Action>>()
    var activeExtensionPopupSession by mutableStateOf<GeckoSession?>(null)
    var activeExtensionPopupName by mutableStateOf("")
    var activeExtensionPopupLoading by mutableStateOf(true)

    var pendingIntentUrl: String? = null
    var isVideoPlayerScreenActive by mutableStateOf(false)
    var isInPictureInPictureMode by mutableStateOf(false)

    private var isViewModelInitialized = false

    // Real Tab System
    val tabs = mutableStateListOf<TabState>()
    var activeTabId by mutableStateOf<String?>(null)
        private set
    var activeNormalTabId by mutableStateOf<String?>(null)
        private set
    var activeIncognitoTabId by mutableStateOf<String?>(null)
        private set

    // Tab Groups
    val tabGroups = mutableStateListOf<TabGroup>()


    // Context Menu State
    var activeContextMenu by mutableStateOf<ContextMenuElement?>(null)
        internal set

    // Text Selection State
    var activeTextSelection by mutableStateOf<String?>(null)
        internal set
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
    internal var copyManager: UniversalCopyManager? = null
    internal var aiBlockerManager: BuiltInExtensionManager? = null
    internal var appContext: Context? = null

    // GeckoView Reference for capturePixels
    internal var activeGeckoViewRef: WeakReference<GeckoView>? = null

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

    val DEFAULT_QUICK_TOOLS_ORDER = listOf(
        "qr_scanner", "safe_locker", "translator", "edit_page",
        "save_pdf", "pin_web_app", "auto_scroll", "qr_scan_page",
        "qr_generator", "console_log", "dev_notes", "site_style"
    )
    var quickToolsOrder by mutableStateOf(listOf(
        "qr_scanner", "safe_locker", "translator", "edit_page",
        "save_pdf", "pin_web_app", "auto_scroll", "qr_scan_page",
        "qr_generator", "console_log", "dev_notes", "site_style"
    ))

    // UI States
    var currentUrl by mutableStateOf("about:blank")
    var isFullscreen by mutableStateOf(false)
    var isVideoPlayingInPage by mutableStateOf(false)
    var isInnerScrolled by mutableStateOf(false)

    /** When true (default), popup windows not triggered by a real user tap are blocked. */
    var isPopupBlockerEnabled by mutableStateOf(true)
    var isUniversalCopyEnabled by mutableStateOf(false)
    var isAiBlockerEnabled by mutableStateOf(false)
    var isMediaGrabberEnabled by mutableStateOf(true)
    var isNativePlayerEnabled by mutableStateOf(true)
    var isYouTubeEnabled by mutableStateOf(false)
    var pendingVideoUrl: String? = null
    var activeVideoCookies by mutableStateOf<String?>(null)
    var customVpnConfig by mutableStateOf<String?>(null)
    var selectedSearchEngine by mutableStateOf("Google")
    var customSearchUrl by mutableStateOf("")
    var customSearchEngines by mutableStateOf<List<CustomSearchEngine>>(emptyList())
    val searchSuggestions = androidx.compose.runtime.mutableStateListOf<String>()
    var isDarkThemeEnabled by mutableStateOf(true)
    var isAmoledMode by mutableStateOf(false)
    var isDynamicColorEnabled by mutableStateOf(false)
    var isIncognitoUnlocked by mutableStateOf(false)
    var cookieBehavior by mutableStateOf(3)
    var doNotTrack by mutableStateOf(true)
    var safeBrowsingLevel by mutableStateOf(1)
    var preloadPages by mutableStateOf(1)
    var lockIncognito by mutableStateOf(false)
    var compromisedPasswordWarning by mutableStateOf(true)
    var httpsOnlyMode by mutableStateOf(false)
    var tabLayoutMode by mutableStateOf("Grid")
    var autoCloseTabsDays by mutableStateOf(0)
    var openTabsInBackground by mutableStateOf(false)
    var accessibilityTextScale by mutableStateOf(1.0f)
    var accessibilityForceZoom by mutableStateOf(false)
    var accessibilityHighContrast by mutableStateOf(false)
    
    // Site settings defaults
    var defaultGeolocation by mutableStateOf("ask")
    var defaultCamera by mutableStateOf("ask")
    var defaultMicrophone by mutableStateOf("ask")
    var defaultNotifications by mutableStateOf("ask")
    var defaultJavascriptAllowed by mutableStateOf(true)
    var defaultAutoplayAllowed by mutableStateOf(true)
    val sitePermissions = androidx.compose.runtime.mutableStateListOf<SitePermission>()
    
    var selectedLanguageCode by mutableStateOf("en")
    var isLanguageSelectionDone by mutableStateOf(false)
    var isOnboardingCompleted by mutableStateOf(false)
    var selectedAccentTheme by mutableStateOf("Ocean Blue")
    var forceDarkWebsites by mutableStateOf(false)
    var navBarHideTop by mutableStateOf(true)
    var navBarHideBottom by mutableStateOf(true)
    var addressBarPosition by mutableStateOf("Top")
    var appIconState by mutableStateOf("Default")
    var customIconPath by mutableStateOf<String?>(null)
    var browserWallpaperUri by mutableStateOf<String?>(null)
    var changeWallpaperDaily by mutableStateOf(false)
    var showDiscoverFeed by mutableStateOf(true)
    var showHomeLogo by mutableStateOf(true)
    var showHomeShortcuts by mutableStateOf(true)
    var showBottomNavBar by mutableStateOf(true)
    var chromeNavBarEnabled by mutableStateOf(false)
    var uiScale by mutableStateOf(1.0f)
    var wallpaperDim by mutableStateOf(-1f)
    var wallpaperBlur by mutableStateOf(0f)
    var wallpaperScale by mutableStateOf(1.0f)
    var wallpaperOffsetX by mutableStateOf(0f)
    var wallpaperOffsetY by mutableStateOf(0f)



    // --- Custom Site Style Config ---
    var siteStyleFontSize by mutableStateOf(100)
    var siteStyleTheme by mutableStateOf("DEFAULT")
    var siteStyleLineSpacing by mutableStateOf(1.4f)
    var siteStyleLetterSpacing by mutableStateOf(0f)
    var siteStyleFontFamily by mutableStateOf("inherit")
    var siteStyleAppliedGlobally by mutableStateOf(false)
    var siteStyleHideImages by mutableStateOf(false)
    var siteStyleGrayscale by mutableStateOf(false)
    var siteStyleWarmFilter by mutableStateOf(false)
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


    var isUniversalCopyToggling by mutableStateOf(false)
    var isAiBlockerToggling by mutableStateOf(false)
    var isMediaGrabberToggling by mutableStateOf(false)
    val togglingUserExtensionIds = mutableStateListOf<String>()
    var currentSettingsVersion by mutableStateOf(0)

    // Navigation event: set true when an external link intent should open the browser screen
    var openBrowserScreenEvent by mutableStateOf(false)
    fun triggerOpenBrowserScreen() { openBrowserScreenEvent = true }
    fun consumeOpenBrowserScreenEvent() { openBrowserScreenEvent = false }



    /**
     * Extracts the S.browser_fallback_url from an intent:// URI.
     * Payment gateways (Razorpay, PayU, etc.) embed this URL so browsers
     * can redirect users to a web fallback when the target app isn't installed.
     *
     * Format: intent://...;S.browser_fallback_url=https%3A%2F%2Fexample.com;end
     */
    internal fun extractFallbackUrl(intentUri: String): String? {
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

    internal fun guessDownloadFilename(url: String, contentType: String?): String {
        val parsed = try {
            Uri.parse(url).lastPathSegment
        } catch (e: Exception) {
            null
        }
        if (!parsed.isNullOrBlank() && parsed.contains('.')) {
            return parsed
        }
        if (!parsed.isNullOrBlank() && parsed.isNotBlank()) {
            return "$parsed.bin"
        }
        val cleanContentType = contentType?.trim()?.lowercase()
        return when {
            cleanContentType == null -> "download.bin"
            cleanContentType.contains("pdf") -> "download.pdf"
            cleanContentType.contains("zip") -> "download.zip"
            cleanContentType.contains("msword") || cleanContentType.contains("wordprocessingml.document") -> "download.docx"
            cleanContentType.contains("excel") || cleanContentType.contains("spreadsheetml.sheet") -> "download.xlsx"
            cleanContentType.contains("presentation") || cleanContentType.contains("presentationml.presentation") -> "download.pptx"
            cleanContentType.contains("text/plain") -> "download.txt"
            cleanContentType.contains("text/html") -> "download.html"
            cleanContentType.contains("json") -> "download.json"
            cleanContentType.contains("xml") -> "download.xml"
            cleanContentType.startsWith("image/") -> "download${cleanContentType.substringAfter("/")}"
            cleanContentType.startsWith("audio/") -> "download.audio"
            cleanContentType.startsWith("video/") -> "download.video"
            cleanContentType.contains("octet-stream") -> "download.bin"
            else -> "download.bin"
        }
    }

    internal fun isGenericDownloadUrl(url: String): Boolean {
        val lower = url.lowercase().trim()
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("about:")) return false

        // Drop fragment (#...) and query (?...) — neither affects whether the
        // *path* points at a downloadable file.
        val noFrag = lower.substringBeforeLast("#")
        val pathAndQuery = noFrag.substringBeforeLast("?")

        // The final path segment (everything after the last '/').
        val lastSegment = pathAndQuery.substringAfterLast("/")
        if (lastSegment.isBlank() || lastSegment.contains(" ")) {
            // URL ends in '/' (e.g. https://example.com/) or has no filename
            // at all — it is a directory/domain, not a downloadable file.
            return false
        }

        val ext = lastSegment.substringAfterLast('.', "").lowercase()

        if (ext.isEmpty()) {
            // No dot in the final segment at all (e.g. "/report", "/file").
            // Treat as a page unless the segment is a known download endpoint word.
            val downloadWords = setOf("download", "file", "get", "serve", "attachment", "export", "report")
            return lastSegment.substringBefore('/').lowercase() in downloadWords
        }
        if (ext.length > 10) return false

        val htmlExtensions = setOf("html", "htm", "php", "asp", "aspx", "jsp", "htmx", "xhtml")
        if (ext in htmlExtensions) return false

        // CRITICAL FIX: if the "extension" is actually a top-level domain
        // (e.g. example.com, site.io, my.app), this is a *bare domain*, not a
        // file. Previously the TLD was mistaken for a file extension, so every
        // ".com" site was wrongly intercepted as a download (download.bin) and
        // the main navigation was DENYed, leaving pages blank.
        val commonTlds = setOf(
            "com","net","org","io","co","ai","app","dev","xyz","info","biz","me","tv",
            "us","uk","de","fr","ru","jp","cn","in","ca","au","gov","edu","mil","int",
            "name","pro","mobi","tech","online","store","site","website","blog","cloud",
            "live","news","shop","email","press","wiki","design","game","gg","sh","top",
            "vip","work","space","fun","club","world","cyou","bid","trade","wang","ren",
            "group","luxe","art","fit","run","plus","zone","care","sale","life","fund",
            "band","cool","best","realty","properties","agency","expert","center","digital",
            "systems","solutions","today","farm","city","town","cash","money","bet",
            "casino","poker","loan","credit","insurance","investments","finance","tax",
            "legal","host","web","law","yoga","pro","tech"
        )
        if (ext in commonTlds) return false

        return true
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
            saveToLocker = saveToLocker,
            cookies = activeVideoCookies,
            referrerUrl = currentUrl
        )
    }

    internal fun handleExternalDownloadResponse(response: org.mozilla.geckoview.WebResponse, context: Context) {
        val headers = response.headers
        val disposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        val contentType = headers["Content-Type"] ?: headers["content-type"]
        val isAttachment = disposition?.contains("attachment", true) == true
        // Only treat this as a download when the server explicitly marks it as an
        // attachment OR the URL clearly points at a downloadable file. This prevents
        // normal HTML page navigations (content-type text/html) from being wrongly
        // intercepted as a "downloaded-file.bin" download.
        if ((isAttachment || response.requestExternalApp) && isGenericDownloadUrl(response.uri)) {
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
    var loadingProgress by mutableStateOf(0f)
    var pullToRefreshOffset by mutableStateOf(0f)

    fun onPullRelease(thresholdPx: Float) {
        if (pullToRefreshOffset * 0.4f >= thresholdPx) {
            reload()
        }
        pullToRefreshOffset = 0f
    }
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isDesktopMode by mutableStateOf(false)
        private set

    // Permissions System
    var activePermissionPrompt by mutableStateOf<ContentPermissionPrompt?>(null)
    var activeSystemPermissionRequest by mutableStateOf<SystemPermissionRequest?>(null)
    var activeMediaPermissionPrompt by mutableStateOf<MediaPermissionPrompt?>(null)
        internal set

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

    // ── Google OAuth Native Account Picker ──────────────────────────────────────
    // When a site triggers Google OAuth, we intercept the navigation and show a
    // native Android account picker. Google only processes the site's token.
    // The browser never calls any Google SDK — it uses standard AccountManager.

    data class PendingGoogleOAuthRequest(
        /** The original accounts.google.com OAuth URL intercepted from GeckoView */
        val oauthUrl: String,
        /** The tab ID that initiated the OAuth */
        val tabId: String
    )

    var pendingGoogleOAuthRequest by mutableStateOf<PendingGoogleOAuthRequest?>(null)

    /**
     * Per-tab OAuth grace period: maps tabId → expiry epoch-ms.
     * After the user picks an account (or taps "Continue"), ALL accounts.google.com
     * navigations on that tab are allowed through until the expiry time.
     * This covers multi-hop redirect chains (site → Google → callback → Google again → site).
     * Cleared automatically when the tab navigates to a non-Google URL.
     */
    internal val oauthGracePeriodByTab = mutableMapOf<String, Long>()

    /**
     * Called when the user picks an account from the native picker.
     * Injects the email as `login_hint` into the OAuth URL and loads it.
     * If [email] is null, navigates to the raw OAuth URL without a hint.
     */
    fun resumeGoogleOAuth(email: String?) {
        val pending = pendingGoogleOAuthRequest ?: return
        pendingGoogleOAuthRequest = null
        val finalUrl = if (email != null) {
            try {
                val uri = android.net.Uri.parse(pending.oauthUrl)
                val encodedQuery = uri.encodedQuery
                val newQueryParts = mutableListOf<String>()
                
                if (!encodedQuery.isNullOrEmpty()) {
                    // Split the raw query by '&' to preserve all original encodings (+, %, etc.)
                    encodedQuery.split("&").forEach { part ->
                        val eqIdx = part.indexOf('=')
                        val key = if (eqIdx != -1) part.substring(0, eqIdx) else part
                        val decodedKey = try {
                            java.net.URLDecoder.decode(key, "UTF-8")
                        } catch (e: Exception) {
                            key
                        }
                        if (!decodedKey.equals("login_hint", ignoreCase = true) && 
                            !decodedKey.equals("Email", ignoreCase = true)) {
                            newQueryParts.add(part)
                        }
                    }
                }
                
                // Safely encode and append the new pre-fill hints
                val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
                newQueryParts.add("login_hint=$encodedEmail")
                newQueryParts.add("Email=$encodedEmail")
                
                val rebuiltQuery = newQueryParts.joinToString("&")
                uri.buildUpon().encodedQuery(rebuiltQuery).build().toString()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inject email hints into OAuth URL: ${e.message}")
                pending.oauthUrl
            }
        } else {
            pending.oauthUrl
        }
        viewModelScope.launch(Dispatchers.Main) {
            Log.i(TAG, "🔑 Resuming Google OAuth${if (email != null) " with hint=$email" else " without hint"}: $finalUrl")
            // Start a 15-second grace period so ALL subsequent accounts.google.com hops
            // in the redirect chain are allowed through without triggering the picker again.
            oauthGracePeriodByTab[pending.tabId] = System.currentTimeMillis() + 15_000L
            // Use the specific tab's session so we load in the correct tab
            // even if the user switched tabs while the picker was showing.
            val targetSession = tabs.firstOrNull { it.id == pending.tabId }?.session ?: geckoSession
            targetSession.loadUri(finalUrl)
        }
    }

    /**
     * Called when the user cancels the native account picker entirely.
     * Clears the pending request without loading any URL.
     */
    fun dismissGoogleOAuth() {
        Log.i(TAG, "🔑 Google OAuth account picker dismissed by user")
        pendingGoogleOAuthRequest = null
    }

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

    // ── Find In Page ─────────────────────────────────────────────────────────────
    var showFindInPage  by mutableStateOf(false)
        internal set
    var findQuery       by mutableStateOf("")
        internal set
    var findMatchCurrent by mutableStateOf(0)
        internal set
    var findMatchTotal  by mutableStateOf(0)
        internal set
    var findMatchFound  by mutableStateOf(true)
        internal set

    // ─────────────────────────────────────────────────────────────────────────────

    // Extensions References

    internal var grabberExtension: WebExtension? = null
    
    val userExtensions = mutableStateListOf<WebExtension>()
    val extensionIcons = mutableStateMapOf<String, android.graphics.Bitmap>()
    var extensionViewMode by mutableStateOf("List") // "List" or "Grid"
    
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

    // --- Password Manager ---
    data class SavedPassword(
        val id: String = java.util.UUID.randomUUID().toString(),
        val domain: String,
        val username: String,
        val password: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    val savedPasswords = mutableStateListOf<SavedPassword>()

    /** Set when GeckoView detects a form submission with credentials — triggers the save banner */
    var pendingSaveCredential by mutableStateOf<SavedPassword?>(null)

    /** Set when user navigates to a site with a saved password — triggers the autofill bar */
    var autofillSuggestion by mutableStateOf<SavedPassword?>(null)
    
    var showAutofillBottomSheet by mutableStateOf(false)
    var autofillMatches by mutableStateOf<List<SavedPassword>>(emptyList())

    /** True while DevNotes or Toolbox sheet is open — extensions are gated from opening their UI */
    var isNativeSheetOpen by mutableStateOf(false)


    val devNotes = mutableStateListOf<DevNote>()

    // --- Tab Management ---
    fun saveTabs() {
        val context = appContext ?: return
        // Do not persist incognito tabs to disk. This ensures they are automatically
        // closed when the browser is closed / process is terminated.
        val tabsSnapshot = tabs.filter { !it.isIncognito }
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
                        put("lastActiveTime", tab.lastActiveTime)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving tabs", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────
    // Tab Groups: Chrome-style tab grouping with color labels and persistence
    // ─────────────────────────────────────────────────────────────────────────────────

    fun saveTabGroups() {
        val context = appContext ?: return
        val snapshot = tabGroups.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, TAB_GROUPS_FILE)
            try {
                val jsonArray = JSONArray()
                snapshot.forEach { group ->
                    val obj = JSONObject().apply {
                        put("id", group.id)
                        put("title", group.title)
                        put("color", group.color)
                        val ids = JSONArray()
                        group.tabIds.forEach { ids.put(it) }
                        put("tabIds", ids)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving tab groups", e)
            }
        }
    }

    fun loadTabGroups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, TAB_GROUPS_FILE)
            if (!file.exists()) return@launch
            try {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                val loaded = mutableListOf<TabGroup>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.optJSONArray("tabIds") ?: JSONArray()
                    val tabIds = (0 until idsArr.length()).map { idsArr.getString(it) }
                    loaded.add(
                        TabGroup(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            color = obj.optLong("color", 0xFF4285F4),
                            tabIds = tabIds
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    tabGroups.clear()
                    tabGroups.addAll(loaded)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tab groups", e)
            }
        }
    }

    fun createTabGroup(title: String, color: Long, initialTabId: String? = null) {
        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            title = title,
            color = color,
            tabIds = if (initialTabId != null) listOf(initialTabId) else emptyList()
        )
        tabGroups.add(group)
        saveTabGroups()
    }

    fun addTabToGroup(tabId: String, groupId: String) {
        // Remove from any existing group first
        removeTabFromAllGroups(tabId)
        val idx = tabGroups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            tabGroups[idx] = tabGroups[idx].copy(tabIds = tabGroups[idx].tabIds + tabId)
            saveTabGroups()
        }
    }

    fun removeTabFromGroup(tabId: String, groupId: String) {
        val idx = tabGroups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            val updated = tabGroups[idx].copy(tabIds = tabGroups[idx].tabIds - tabId)
            if (updated.tabIds.isEmpty()) {
                tabGroups.removeAt(idx)
            } else {
                tabGroups[idx] = updated
            }
            saveTabGroups()
        }
    }

    private fun removeTabFromAllGroups(tabId: String) {
        val updatedGroups = tabGroups.map { g ->
            g.copy(tabIds = g.tabIds - tabId)
        }.filter { it.tabIds.isNotEmpty() }
        tabGroups.clear()
        tabGroups.addAll(updatedGroups)
    }

    fun deleteTabGroup(groupId: String) {
        tabGroups.removeAll { it.id == groupId }
        saveTabGroups()
    }

    fun renameTabGroup(groupId: String, newTitle: String) {
        val idx = tabGroups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            tabGroups[idx] = tabGroups[idx].copy(title = newTitle)
            saveTabGroups()
        }
    }

    fun changeTabGroupColor(groupId: String, color: Long) {
        val idx = tabGroups.indexOfFirst { it.id == groupId }
        if (idx != -1) {
            tabGroups[idx] = tabGroups[idx].copy(color = color)
            saveTabGroups()
        }
    }

    fun getGroupForTab(tabId: String): TabGroup? = tabGroups.find { tabId in it.tabIds }

    // ─────────────────────────────────────────────────────────────────────────────────
    // Site Settings: Manage global default permissions and site-specific overrides
    // ─────────────────────────────────────────────────────────────────────────────────

    fun getDomain(url: String): String {
        if (url.isBlank() || url == "about:blank") return "about:blank"
        return try {
            val cleanUrl = if (!url.contains("://")) "https://$url" else url
            val uri = java.net.URI(cleanUrl)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            val hostPart = url.substringAfter("://").substringBefore("/")
            if (hostPart.startsWith("www.")) hostPart.substring(4) else hostPart
        }
    }

    fun saveSitePermissions() {
        val context = appContext ?: return
        val snapshot = sitePermissions.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, SITE_PERMISSIONS_FILE)
            try {
                val jsonArray = org.json.JSONArray()
                snapshot.forEach { perm ->
                    val obj = org.json.JSONObject().apply {
                        put("host", perm.host)
                        put("location", perm.location)
                        put("camera", perm.camera)
                        put("microphone", perm.microphone)
                        put("notifications", perm.notifications)
                        put("javascript", perm.javascript)
                        put("autoplay", perm.autoplay)
                    }
                    jsonArray.put(obj)
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving site permissions", e)
            }
        }
    }

    fun loadSitePermissions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, SITE_PERMISSIONS_FILE)
            if (!file.exists()) return@launch
            try {
                val jsonStr = file.readText()
                val jsonArray = org.json.JSONArray(jsonStr)
                val loaded = mutableListOf<SitePermission>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loaded.add(
                        SitePermission(
                            host = obj.getString("host"),
                            location = obj.optString("location", "ask"),
                            camera = obj.optString("camera", "ask"),
                            microphone = obj.optString("microphone", "ask"),
                            notifications = obj.optString("notifications", "ask"),
                            javascript = obj.optString("javascript", "allow"),
                            autoplay = obj.optString("autoplay", "allow")
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    sitePermissions.clear()
                    sitePermissions.addAll(loaded)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading site permissions", e)
            }
        }
    }

    fun getSitePermissionValue(host: String, type: String): String {
        val domain = getDomain(host)
        val perm = sitePermissions.find { it.host.equals(domain, ignoreCase = true) }
        return when (type) {
            "location" -> perm?.location ?: defaultGeolocation
            "camera" -> perm?.camera ?: defaultCamera
            "microphone" -> perm?.microphone ?: defaultMicrophone
            "notifications" -> perm?.notifications ?: defaultNotifications
            "javascript" -> perm?.javascript ?: (if (defaultJavascriptAllowed) "allow" else "block")
            "autoplay" -> perm?.autoplay ?: (if (defaultAutoplayAllowed) "allow" else "block")
            else -> "ask"
        }
    }

    fun updateSitePermission(host: String, type: String, value: String) {
        val domain = getDomain(host)
        val idx = sitePermissions.indexOfFirst { it.host.equals(domain, ignoreCase = true) }
        val current = if (idx != -1) sitePermissions[idx] else SitePermission(host = domain)
        val updated = when (type) {
            "location" -> current.copy(location = value)
            "camera" -> current.copy(camera = value)
            "microphone" -> current.copy(microphone = value)
            "notifications" -> current.copy(notifications = value)
            "javascript" -> current.copy(javascript = value)
            "autoplay" -> current.copy(autoplay = value)
            else -> current
        }
        if (idx != -1) {
            sitePermissions[idx] = updated
        } else {
            sitePermissions.add(updated)
        }
        saveSitePermissions()
        
        // Apply settings changes dynamically to open tabs matching this host
        tabs.filter { getDomain(it.url).equals(domain, ignoreCase = true) }.forEach { tab ->
            if (type == "javascript") {
                tab.session.settings.allowJavascript = (value == "allow")
            }
        }
    }

    fun clearSitePermission(host: String) {
        val domain = getDomain(host)
        sitePermissions.removeAll { it.host.equals(domain, ignoreCase = true) }
        saveSitePermissions()
    }

    fun clearAllSitePermissions() {
        sitePermissions.clear()
        saveSitePermissions()
    }

    fun updateGlobalSitePermission(type: String, value: String) {
        val context = appContext ?: return
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                when (type) {
                    "location" -> {
                        defaultGeolocation = value
                        preferences[DEFAULT_GEOLOCATION_KEY] = value
                    }
                    "camera" -> {
                        defaultCamera = value
                        preferences[DEFAULT_CAMERA_KEY] = value
                    }
                    "microphone" -> {
                        defaultMicrophone = value
                        preferences[DEFAULT_MICROPHONE_KEY] = value
                    }
                    "notifications" -> {
                        defaultNotifications = value
                        preferences[DEFAULT_NOTIFICATIONS_KEY] = value
                    }
                }
            }
        }
    }

    fun updateGlobalJavascriptAllowed(allowed: Boolean) {
        val context = appContext ?: return
        viewModelScope.launch {
            defaultJavascriptAllowed = allowed
            context.dataStore.edit { preferences ->
                preferences[DEFAULT_JAVASCRIPT_KEY] = allowed
            }
            // Propagate to all tabs that don't have custom overrides
            tabs.forEach { tab ->
                val host = getDomain(tab.url)
                val hasOverride = sitePermissions.any { it.host.equals(host, ignoreCase = true) }
                if (!hasOverride) {
                    tab.session.settings.allowJavascript = allowed
                }
            }
        }
    }

    fun updateGlobalAutoplayAllowed(allowed: Boolean) {
        val context = appContext ?: return
        viewModelScope.launch {
            defaultAutoplayAllowed = allowed
            context.dataStore.edit { preferences ->
                preferences[DEFAULT_AUTOPLAY_KEY] = allowed
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
                        val lastActiveTime = obj.optLong("lastActiveTime", System.currentTimeMillis())
                        
                        val isJsAllowed = getSitePermissionValue(url, "javascript") == "allow"
                        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                            .usePrivateMode(isIncognito)
                            .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                            .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                            .allowJavascript(isJsAllowed)
                            .build()
                        val session = GeckoSession(settings)
                        
                        val shouldLoadNow = isActive || (url == "about:blank" || url.isEmpty())
                        
                        val tab = TabState(
                            id = id,
                            session = session,
                            title = title,
                            url = url,
                            isIncognito = isIncognito,
                            isUriLoaded = shouldLoadNow,
                            lastActiveTime = lastActiveTime
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
                        checkAutoCloseTabs(context)
                        loadTabGroups(context)
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
        val isJsAllowed = getSitePermissionValue(url, "javascript") == "allow"
        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognitoMode)
            .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .allowJavascript(isJsAllowed)
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
        if (!openTabsInBackground || tabs.size == 1) {
            selectTab(newTab.id)
        } else {
            val isIncog = newTab.isIncognito
            if (isIncog && activeIncognitoTabId == null) {
                activeIncognitoTabId = newTab.id
            } else if (!isIncog && activeNormalTabId == null) {
                activeNormalTabId = newTab.id
            }
        }
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
        tabs[tabIndex] = tab.copy(lastActiveTime = System.currentTimeMillis())
        val oldSession = geckoSession
        activeTabId = tabId
        geckoSession = tab.session
        currentUrl = tab.url
        checkAutofillForUrl(tab.url)
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
        // Dismiss Find-in-Page when switching tabs — GeckoView highlights are per-session
        if (showFindInPage) closeFindInPage()
        
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
        // Clean up group membership for the closed tab
        removeTabFromAllGroups(tabId)
        
        if (activeTabId == tabId) {
            val remainingModeTabs = tabs.filter { it.isIncognito == tabToClose.isIncognito }
            if (remainingModeTabs.isNotEmpty()) {
                val nextSelect = remainingModeTabs.find { it.id == (if (tabToClose.isIncognito) activeIncognitoTabId else activeNormalTabId) } 
                    ?: remainingModeTabs.first()
                selectTab(nextSelect.id)
            }
        }
        saveTabs()
        saveTabGroups()
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





    /**
     * "Burn Data" — wipes everything:
     *  1. Clears in-memory + persisted browsing history.
     *  2. Closes every open tab (both normal and incognito).
     *  3. Opens a single fresh "about:blank" normal tab so the browser is usable.
     *  4. Exits incognito mode if active.
     *
     * The GeckoView-side purge (cookies, cache, DOM storage, etc.) is handled by
     * [com.rebelroot.omni.privacy.FireButton.burn], which the call site runs first.
     */
    fun burnAllData(context: Context) {
        // 1. Wipe history list and its persisted JSON file
        historyList.clear()
        val ctx = appContext ?: context
        saveHistory(ctx)

        // 2. Close every GeckoSession without going through the normal "last tab" guard
        val allTabs = tabs.toList()
        for (tab in allTabs) {
            try { tab.session.close() } catch (e: Exception) {
                Log.e(TAG, "burnAllData: error closing session for tab ${tab.id}", e)
            }
        }
        tabs.clear()

        // 3. Reset all tab-tracking state
        activeTabId = null
        activeNormalTabId = null
        activeIncognitoTabId = null
        isIncognitoMode = false
        currentUrl = "about:blank"
        canGoBack = false
        canGoForward = false

        // 4. Persist the now-empty tab list so it survives a relaunch
        saveTabs()

        // 5. Open one clean normal tab — browser must always have at least one tab
        createNewTab(ctx, "about:blank")

        Log.i(TAG, "🔥 burnAllData: history wiped, all ${allTabs.size} tab(s) closed, fresh tab opened.")
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

    @Keep
    fun getGeckoRuntime(context: Context): GeckoRuntime {
        val appCtx = context.applicationContext
        appContext = appCtx

        // Load persistent extension view mode settings
        viewModelScope.launch {
            try {
                val prefs = appCtx.dataStore.data.first()
                extensionViewMode = prefs[EXTENSION_VIEW_MODE_KEY] ?: "List"
            } catch (_: Exception) {}
        }

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

            val prefs = runBlocking { appCtx.dataStore.data.first() }
            val dnt = prefs[DO_NOT_TRACK_KEY] ?: true
            val hom = prefs[HTTPS_ONLY_MODE_KEY] ?: false
            val pl = prefs[PRELOAD_PAGES_KEY] ?: 1
            val cookieBeh = prefs[COOKIE_BEHAVIOR_KEY] ?: 3
            val sbLevel = prefs[SAFE_BROWSING_LEVEL_KEY] ?: 1
            val hc = prefs[ACCESSIBILITY_HIGH_CONTRAST_KEY] ?: false

            val cbSettings = org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                .antiTracking(
                    org.mozilla.geckoview.ContentBlocking.AntiTracking.AD or
                    org.mozilla.geckoview.ContentBlocking.AntiTracking.SOCIAL or
                    org.mozilla.geckoview.ContentBlocking.AntiTracking.ANALYTIC or
                    org.mozilla.geckoview.ContentBlocking.AntiTracking.FINGERPRINTING or
                    org.mozilla.geckoview.ContentBlocking.AntiTracking.CRYPTOMINING
                )
                .cookieBehavior(cookieBeh)
                .safeBrowsing(if (sbLevel > 0) org.mozilla.geckoview.ContentBlocking.SafeBrowsing.DEFAULT else 0)
                .build()

            val configFile = File(appCtx.filesDir, "geckoview-config.yaml")
            try {
                val sb = java.lang.StringBuilder()
                sb.append("pref:\n")
                sb.append("  dom.ipc.processCount: 1\n")
                sb.append("  dom.ipc.processCount.webIsolated: 1\n")
                sb.append("  privacy.donottrackheader.enabled: ${dnt}\n")
                sb.append("  dom.security.https_only_mode: ${hom || sbLevel == 2}\n")
                sb.append("  ui.useAccessibilityTheme: ${if (hc) 1 else 0}\n")
                if (pl == 0) {
                    sb.append("  network.dns.disablePrefetch: true\n")
                    sb.append("  network.prefetch-next: false\n")
                } else {
                    sb.append("  network.dns.disablePrefetch: false\n")
                    sb.append("  network.prefetch-next: true\n")
                }
                configFile.writeText(sb.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write geckoview-config.yaml", e)
            }

            val builder = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(isDebug)
                .consoleOutput(isDebug)
                .debugLogging(isDebug)
                .remoteDebuggingEnabled(isDebug)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .locales(arrayOf(lang)) // Configures Accept-Language headers automatically
                .contentBlocking(cbSettings)
                .configFilePath(configFile.absolutePath)

            val settings = builder.build()

            try {
                geckoRuntime = GeckoRuntime.create(appCtx, settings)
                geckoRuntimeError = null   // clear any previous failure
            } catch (t: Throwable) {
                // GeckoRuntime.create can fail when native .so loading fails at
                // runtime (e.g. UnsatisfiedLinkError / dlopen on Android 15+ with
                // unaligned libs). Log it, expose the error via state so the UI
                // can render GeckoErrorScreen, and rethrow for the existing
                // MainActivity catch block to also pick it up.
                Log.e(TAG, "GeckoRuntime.create FAILED: ${t.javaClass.simpleName}: ${t.message}", t)
                geckoRuntimeError = "${t.javaClass.simpleName}: ${t.message}"
                throw t
            }
            pruneTemporaryStorage(appCtx)

            // Register the autocomplete storage delegate so Gecko can query our saved passwords
            // for autofill and notify us when new credentials are submitted.
            geckoRuntime!!.setAutocompleteStorageDelegate(object : org.mozilla.geckoview.Autocomplete.StorageDelegate {
                override fun onLoginFetch(domain: String): GeckoResult<Array<org.mozilla.geckoview.Autocomplete.LoginEntry>>? {
                    val cleanDomain = domain.removePrefix("www.")
                    val matches = getPasswordsForDomain(cleanDomain)
                    if (matches.isEmpty()) return GeckoResult.fromValue(emptyArray())
                    val entries = matches.map { p ->
                        org.mozilla.geckoview.Autocomplete.LoginEntry.Builder()
                            .origin("https://${p.domain}")
                            .username(p.username)
                            .password(p.password)
                            .build()
                    }.toTypedArray()
                    return GeckoResult.fromValue(entries)
                }

                // StorageDelegate.onLoginSave fires when Gecko's own storage delegate intercepts a save.
                // We don't use this path — we handle saves via PromptDelegate.onLoginSave instead
                // so we can show our custom banner UI.
            })
            
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
            aiBlockerManager = BuiltInExtensionManager(
                runtime = geckoRuntime!!,
                assetPath = "web_extensions/ai_blocker/",
                extensionId = AI_BLOCKER_ID,
                label = "AI Blocker"
            )
            
            // Sync user extensions on start
            syncUserExtensions()

            // Initialize multi-tabs
            initTabs(appCtx)
            loadDevNotes(appCtx)
            loadSavedPasswords(appCtx)

            viewModelScope.launch {
                isPopupBlockerEnabled = getPopupBlockerPreference(appCtx).first()
            }

            viewModelScope.launch {
                isNativePlayerEnabled = getNativePlayerPreference(appCtx).first()
                syncNativePlayerStateInPage()
            }

            viewModelScope.launch {
                isYouTubeEnabled = false
                mediaInterceptor.isYouTubeEnabled = false
                try {
                    appCtx.dataStore.edit { preferences ->
                        preferences[YOUTUBE_ENABLED_KEY] = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting YouTube preference", e)
                }
            }


            viewModelScope.launch {
                customVpnConfig = getCustomVpnConfig(appCtx).first()
            }

            viewModelScope.launch {
                selectedSearchEngine = getSearchEnginePreference(appCtx).first()
                customSearchUrl = getCustomSearchUrlPreference(appCtx).first()
                val enginesJson = getCustomSearchEnginesPreference(appCtx).first()
                customSearchEngines = parseCustomSearchEngines(enginesJson)
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
                isAmoledMode = getAmoledModePreference(appCtx).first()
            }

            viewModelScope.launch {
                isDynamicColorEnabled = false
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
                uiScale = getUiScalePreference(appCtx).first()
            }

            viewModelScope.launch {
                val prefs = appCtx.dataStore.data.first()
                cookieBehavior = prefs[COOKIE_BEHAVIOR_KEY] ?: 3
                doNotTrack = prefs[DO_NOT_TRACK_KEY] ?: true
                safeBrowsingLevel = prefs[SAFE_BROWSING_LEVEL_KEY] ?: 1
                preloadPages = prefs[PRELOAD_PAGES_KEY] ?: 1
                lockIncognito = prefs[LOCK_INCOGNITO_KEY] ?: false
                compromisedPasswordWarning = prefs[COMPROMISED_PASSWORD_WARNING_KEY] ?: true
                httpsOnlyMode = prefs[HTTPS_ONLY_MODE_KEY] ?: false
                
                tabLayoutMode = prefs[TAB_LAYOUT_MODE_KEY] ?: "Grid"
                autoCloseTabsDays = prefs[AUTO_CLOSE_TABS_DAYS_KEY] ?: 0
                openTabsInBackground = prefs[OPEN_TABS_IN_BACKGROUND_KEY] ?: false
                accessibilityTextScale = prefs[ACCESSIBILITY_TEXT_SCALE_KEY] ?: 1.0f
                accessibilityForceZoom = prefs[ACCESSIBILITY_FORCE_ZOOM_KEY] ?: false
                accessibilityHighContrast = prefs[ACCESSIBILITY_HIGH_CONTRAST_KEY] ?: false
                
                defaultGeolocation = prefs[DEFAULT_GEOLOCATION_KEY] ?: "ask"
                defaultCamera = prefs[DEFAULT_CAMERA_KEY] ?: "ask"
                defaultMicrophone = prefs[DEFAULT_MICROPHONE_KEY] ?: "ask"
                defaultNotifications = prefs[DEFAULT_NOTIFICATIONS_KEY] ?: "ask"
                defaultJavascriptAllowed = prefs[DEFAULT_JAVASCRIPT_KEY] ?: true
                defaultAutoplayAllowed = prefs[DEFAULT_AUTOPLAY_KEY] ?: true
            }

            loadSitePermissions(appCtx)

            viewModelScope.launch {
                val sp = appCtx.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                siteStyleFontSize = sp.getInt("site_style_font_size", 100)
                siteStyleTheme = sp.getString("site_style_theme", "DEFAULT") ?: "DEFAULT"
                siteStyleLineSpacing = sp.getFloat("site_style_line_spacing", 1.4f)
                siteStyleLetterSpacing = sp.getFloat("site_style_letter_spacing", 0f)
                siteStyleFontFamily = sp.getString("site_style_font_family", "inherit") ?: "inherit"
                siteStyleAppliedGlobally = sp.getBoolean("site_style_applied_globally", false)
                siteStyleHideImages = sp.getBoolean("site_style_hide_images", false)
                siteStyleGrayscale = sp.getBoolean("site_style_grayscale", false)
                siteStyleWarmFilter = sp.getBoolean("site_style_warm_filter", false)
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
                    customIconPath = prefs[CUSTOM_ICON_PATH_KEY]
                    browserWallpaperUri = prefs[BROWSER_WALLPAPER_URI_KEY]
                    changeWallpaperDaily = prefs[CHANGE_WALLPAPER_DAILY_KEY] ?: false
                    showDiscoverFeed = prefs[SHOW_DISCOVER_FEED_KEY] ?: true
                    showBottomNavBar = prefs[SHOW_BOTTOM_NAV_BAR_KEY] ?: true
                    chromeNavBarEnabled = prefs[CHROME_NAV_BAR_KEY] ?: false
                    showHomeLogo = prefs[SHOW_HOME_LOGO_KEY] ?: true
                    showHomeShortcuts = prefs[SHOW_HOME_SHORTCUTS_KEY] ?: true
                    wallpaperDim = prefs[WALLPAPER_DIM_KEY] ?: -1f
                    wallpaperBlur = prefs[WALLPAPER_BLUR_KEY] ?: 0f
                    wallpaperScale = prefs[WALLPAPER_SCALE_KEY] ?: 1.0f
                    wallpaperOffsetX = prefs[WALLPAPER_OFFSET_X_KEY] ?: 0f
                    wallpaperOffsetY = prefs[WALLPAPER_OFFSET_Y_KEY] ?: 0f
                    quickToolsOrder = run {
                        val saved = prefs[QUICK_TOOLS_ORDER_KEY]
                        val default = listOf(
                            "qr_scanner", "safe_locker", "translator", "edit_page",
                            "save_pdf", "pin_web_app", "auto_scroll", "qr_scan_page",
                            "qr_generator", "console_log", "dev_notes", "site_style"
                        )
                        if (!saved.isNullOrBlank()) {
                            val savedList = saved.split(",").filter { it.isNotBlank() }
                            savedList + default.filter { it !in savedList }
                        } else default
                    }
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
            loadExtensionsClean(context)
        }
    }

    private fun loadExtensionsClean(context: Context) {
        val runtime = geckoRuntime ?: return
        viewModelScope.launch {
            isMediaGrabberEnabled = getMediaGrabberPreference(context).first()
            installGrabberExtension(runtime)
            

            
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
                        val response = org.json.JSONObject().apply {
                            put("enabled", isNativePlayerEnabled)
                            put("youtubeEnabled", isYouTubeEnabled)
                            pendingJsCommand?.let {
                                put("pendingJs", it)
                                pendingJsCommand = null
                            }
                        }
                        return GeckoResult.fromValue(response.toString())
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
                        val cookies = if (message is org.json.JSONObject) {
                            if (message.has("cookies")) message.getString("cookies") else null
                        } else {
                            (message as? Map<*, *>)?.get("cookies") as? String
                        }
                        if (url != null) {
                            mediaInterceptor.onAggressiveMediaGrabbed(url, mime ?: "video/mp4", cookies)
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
                        val cookies = if (message is org.json.JSONObject) {
                            if (message.has("cookies")) message.getString("cookies") else null
                        } else {
                            (message as? Map<*, *>)?.get("cookies") as? String
                        }
                        Log.i(TAG, "🎬 received PLAY_IN_NATIVE message. url=$videoUrl, pageUrl=$pageUrl, isNativePlayerEnabled=$isNativePlayerEnabled")
                        val isYouTube = pageUrl.lowercase().contains("youtube.com") || pageUrl.lowercase().contains("youtu.be") ||
                                        (videoUrl != null && (videoUrl.lowercase().contains("youtube.com") || videoUrl.lowercase().contains("youtu.be")))
                        if (videoUrl != null && isNativePlayerEnabled && (!isYouTube || isYouTubeEnabled)) {
                            activeVideoCookies = cookies
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
                    } else if (type == "FOCUS_LOGIN_INPUT") {
                        val pageUrl = if (message is org.json.JSONObject) {
                            if (message.has("url")) message.getString("url") else null
                        } else {
                            (message as? Map<*, *>)?.get("url") as? String
                        }
                        if (pageUrl != null) {
                            viewModelScope.launch(Dispatchers.Main) {
                                checkAutofillForFocus(pageUrl)
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



    fun togglePopupBlocker(context: Context) {
        viewModelScope.launch {
            val newState = !isPopupBlockerEnabled
            isPopupBlockerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[POPUP_BLOCKER_ENABLED_KEY] = newState
            }
        }
    }

    fun updatePopupBlockerEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            isPopupBlockerEnabled = enabled
            context.dataStore.edit { preferences ->
                preferences[POPUP_BLOCKER_ENABLED_KEY] = enabled
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



    fun uninstallUniversalCopy(context: Context) {
        if (isUniversalCopyToggling) return
        isUniversalCopyToggling = true
        viewModelScope.launch {
            isUniversalCopyEnabled = false
            context.dataStore.edit { preferences ->
                preferences[UNIVERSAL_COPY_ENABLED_KEY] = false
            }
            copyManager?.uninstall(onComplete = {
                isUniversalCopyToggling = false
                currentSettingsVersion++
                reload()
            })
        }
    }

    fun uninstallAiBlocker(context: Context) {
        if (isAiBlockerToggling) return
        isAiBlockerToggling = true
        viewModelScope.launch {
            isAiBlockerEnabled = false
            context.dataStore.edit { preferences ->
                preferences[AI_BLOCKER_ENABLED_KEY] = false
            }
            aiBlockerManager?.uninstall(onComplete = {
                isAiBlockerToggling = false
                currentSettingsVersion++
                reload()
            })
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

    private fun getYouTubePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[YOUTUBE_ENABLED_KEY] ?: false // Default OFF
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

    fun getCustomSearchEnginesPreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_SEARCH_ENGINES_KEY] ?: "[]"
        }
    }

    fun parseCustomSearchEngines(jsonStr: String): List<CustomSearchEngine> {
        val list = mutableListOf<CustomSearchEngine>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "")
                val queryUrl = obj.optString("queryUrl", "")
                if (name.isNotEmpty() && queryUrl.isNotEmpty()) {
                    list.add(CustomSearchEngine(name, queryUrl))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeCustomSearchEngines(list: List<CustomSearchEngine>): String {
        val array = org.json.JSONArray()
        for (engine in list) {
            val obj = org.json.JSONObject()
            obj.put("name", engine.name)
            obj.put("queryUrl", engine.queryUrl)
            array.put(obj)
        }
        return array.toString()
    }

    fun addCustomSearchEngine(context: Context, name: String, queryUrl: String) {
        val updatedList = customSearchEngines + CustomSearchEngine(name, queryUrl)
        saveCustomSearchEnginesList(context, updatedList)
    }

    fun deleteCustomSearchEngine(context: Context, engine: CustomSearchEngine) {
        val updatedList = customSearchEngines.filter { it.name != engine.name }
        saveCustomSearchEnginesList(context, updatedList)
        if (selectedSearchEngine == engine.name) {
            saveSearchEngine(context, "Google")
        }
    }

    private fun saveCustomSearchEnginesList(context: Context, list: List<CustomSearchEngine>) {
        viewModelScope.launch {
            val jsonStr = serializeCustomSearchEngines(list)
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_SEARCH_ENGINES_KEY] = jsonStr
            }
            customSearchEngines = list
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

    // AMOLED mode settings
    fun getAmoledModePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AMOLED_MODE_KEY] ?: false
        }
    }

    fun saveAmoledMode(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[AMOLED_MODE_KEY] = enabled }
            isAmoledMode = enabled
        }
    }

    // Dynamic color (Material You) settings
    fun getDynamicColorPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DYNAMIC_COLOR_KEY] ?: false
        }
    }

    fun saveDynamicColor(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
            isDynamicColorEnabled = false
        }
    }

    // UI Scale Settings
    fun getUiScalePreference(context: Context): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[UI_SCALE_KEY] ?: 1.0f
        }
    }

    fun saveUiScale(context: Context, scale: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[UI_SCALE_KEY] = scale }
            uiScale = scale
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

            val pm = context.packageManager
            val pkg = context.packageName
            val aliases = listOf(
                "Default" to ".MainActivityDefault",
                "Dark"    to ".MainActivityDark"
            )

            aliases.forEach { (name, aliasName) ->
                val comp = android.content.ComponentName(pkg, "$pkg$aliasName")
                val enableState = when {
                    name == state -> android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else -> android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                try {
                    pm.setComponentEnabledSetting(
                        comp,
                        enableState,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    android.util.Log.w("BrowserViewModel", "Failed to set icon alias $aliasName: ${e.message}")
                }
            }
        }
    }


    fun saveCustomIconPath(context: Context, path: String?) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                if (path == null) {
                    prefs.remove(CUSTOM_ICON_PATH_KEY)
                } else {
                    prefs[CUSTOM_ICON_PATH_KEY] = path
                }
            }
            customIconPath = path
        }
    }

    fun saveBrowserWallpaperUri(context: Context, uri: String?) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                if (uri == null) {
                    prefs.remove(BROWSER_WALLPAPER_URI_KEY)
                    prefs.remove(WALLPAPER_SCALE_KEY)
                    prefs.remove(WALLPAPER_OFFSET_X_KEY)
                    prefs.remove(WALLPAPER_OFFSET_Y_KEY)
                } else {
                    prefs[BROWSER_WALLPAPER_URI_KEY] = uri
                }
            }
            browserWallpaperUri = uri
            if (uri == null) {
                wallpaperScale = 1.0f
                wallpaperOffsetX = 0f
                wallpaperOffsetY = 0f
            }
        }
    }

    fun saveChangeWallpaperDaily(context: Context, changeDaily: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[CHANGE_WALLPAPER_DAILY_KEY] = changeDaily }
            changeWallpaperDaily = changeDaily
        }
    }

    fun saveWallpaperDim(context: Context, value: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[WALLPAPER_DIM_KEY] = value }
            wallpaperDim = value
        }
    }

    fun saveWallpaperBlur(context: Context, value: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[WALLPAPER_BLUR_KEY] = value }
            wallpaperBlur = value
        }
    }

    fun saveWallpaperCrop(context: Context, scale: Float, offsetX: Float, offsetY: Float) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[WALLPAPER_SCALE_KEY] = scale
                prefs[WALLPAPER_OFFSET_X_KEY] = offsetX
                prefs[WALLPAPER_OFFSET_Y_KEY] = offsetY
            }
            wallpaperScale = scale
            wallpaperOffsetX = offsetX
            wallpaperOffsetY = offsetY
        }
    }

    fun saveShowDiscoverFeed(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_DISCOVER_FEED_KEY] = show }
            showDiscoverFeed = show
        }
    }

    fun saveShowHomeLogo(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_HOME_LOGO_KEY] = show }
            showHomeLogo = show
        }
    }

    fun saveShowHomeShortcuts(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_HOME_SHORTCUTS_KEY] = show }
            showHomeShortcuts = show
        }
    }

    fun saveShowBottomNavBar(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_BOTTOM_NAV_BAR_KEY] = show }
            showBottomNavBar = show
        }
    }

    fun saveChromeNavBarEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[CHROME_NAV_BAR_KEY] = enabled }
            chromeNavBarEnabled = enabled
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

    fun saveQuickToolsOrder(context: Context, order: List<String>) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[QUICK_TOOLS_ORDER_KEY] = order.joinToString(",")
            }
            quickToolsOrder = order
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

    fun toggleYouTube(context: Context) {
        viewModelScope.launch {
            val newState = !isYouTubeEnabled
            isYouTubeEnabled = newState
            mediaInterceptor.isYouTubeEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[YOUTUBE_ENABLED_KEY] = newState
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

    private var searchSuggestJob: kotlinx.coroutines.Job? = null

    fun fetchSearchSuggestions(query: String) {
        searchSuggestJob?.cancel()
        if (query.trim().isBlank()) {
            searchSuggestions.clear()
            return
        }
        searchSuggestJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(200)
                val encodedQuery = try {
                    java.net.URLEncoder.encode(query, "UTF-8")
                } catch (_: Exception) {
                    query.replace(" ", "+")
                }
                
                val urlString = when (selectedSearchEngine) {
                    "Yahoo" -> "https://ff.search.yahoo.com/gossip?output=fxjson&command=$encodedQuery"
                    "Yandex" -> "https://suggest.yandex.com/suggest-ff.cgi?part=$encodedQuery"
                    "DuckDuckGo" -> "https://ac.duckduckgo.com/ac/?q=$encodedQuery"
                    "Brave" -> "https://search.brave.com/api/suggest?q=$encodedQuery"
                    "Bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
                    "Ecosia" -> "https://ac.ecosia.org/autocomplete?q=$encodedQuery"
                    "Startpage" -> "https://www.startpage.com/do/suggest?query=$encodedQuery"
                    "Qwant" -> "https://api.qwant.com/v3/suggest?q=$encodedQuery"
                    else -> "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                }

                val url = java.net.URL(urlString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.connect()

                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = mutableListOf<String>()
                    
                    if (selectedSearchEngine == "DuckDuckGo") {
                        val arr = org.json.JSONArray(text)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(obj.getString("phrase"))
                        }
                    } else {
                        val arr = org.json.JSONArray(text)
                        if (arr.length() > 1) {
                            val suggestionsArr = arr.getJSONArray(1)
                            for (i in 0 until suggestionsArr.length()) {
                                list.add(suggestionsArr.getString(i))
                            }
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        searchSuggestions.clear()
                        searchSuggestions.addAll(list.take(8))
                    }
                }
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "Error fetching suggestions", e)
            }
        }
    }

    fun getSearchUrlForQuery(query: String): String {
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: java.io.UnsupportedEncodingException) {
            query.replace(" ", "+")
        }
        return when (selectedSearchEngine) {
            "Yahoo" -> "https://search.yahoo.com/search?p=$encodedQuery"
            "Yandex" -> "https://yandex.com/search/?text=$encodedQuery"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encodedQuery"
            "Brave" -> "https://search.brave.com/search?q=$encodedQuery"
            "Bing" -> "https://www.bing.com/search?q=$encodedQuery"
            "Ecosia" -> "https://www.ecosia.org/search?q=$encodedQuery"
            "Startpage" -> "https://www.startpage.com/sp/search?query=$encodedQuery"
            "Qwant" -> "https://www.qwant.com/?q=$encodedQuery"
            "Custom" -> {
                val customUrl = customSearchUrl
                if (!customUrl.isNullOrBlank() && customUrl.contains("%s")) {
                    customUrl.replace("%s", encodedQuery)
                } else {
                    "https://duckduckgo.com/?q=$encodedQuery"
                }
            }
            else -> {
                val match = customSearchEngines.find { it.name == selectedSearchEngine }
                if (match != null) {
                    val customUrl = match.queryUrl
                    if (customUrl.contains("%s")) {
                        customUrl.replace("%s", encodedQuery)
                    } else {
                        customUrl + encodedQuery
                    }
                } else {
                    val base = "https://www.google.com/search?q=$encodedQuery"
                    if (isAiBlockerEnabled) "$base&udm=14" else base
                }
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



    internal fun injectZoomEnabler() {
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

    /**
     * Removes the persistent Google Translate floating badge and toolbar injected by
     * translate.goog pages. The widget consists of:
     *  - An <iframe> banner that shifts body.top
     *  - A floating circle button (#goog-gt-tt, .goog-te-balloon-frame)
     *  - .skiptranslate wrapper elements
     * We remove all of them via JS and attach a MutationObserver so they stay gone.
     */
    internal fun injectTranslateBadgeSuppressor() {
        val js = """javascript:(function(){
            function removeTranslateUI() {
                var ids = ['goog-gt-tt','goog-gt-','gt-res-content','gt-res-dir-ctr'];
                ids.forEach(function(id){ var el = document.getElementById(id); if(el) el.remove(); });
                var classes = ['goog-te-balloon-frame','goog-te-banner-frame','skiptranslate','goog-te-ftab-float'];
                classes.forEach(function(cls){
                    document.querySelectorAll('.'+cls).forEach(function(el){ el.remove(); });
                });
                document.querySelectorAll('iframe').forEach(function(el){
                    if(el.src && el.src.indexOf('translate.google') !== -1){ el.remove(); }
                });
                if(document.body) {
                    document.body.style.top = '0px';
                    document.body.style.position = '';
                    document.documentElement.style.overflow = '';
                }
            }
            removeTranslateUI();
            var observer = new MutationObserver(function(){ removeTranslateUI(); });
            observer.observe(document.documentElement, {childList:true, subtree:true});
        })();"""
        geckoSession.loadUri(js)
    }

    fun installExtensionFromUrl(url: String, context: Context) {
        val runtime = geckoRuntime ?: run {
            Log.w(TAG, "installExtensionFromUrl: GeckoRuntime not ready yet")
            return
        }
        Log.d(TAG, "Installing external extension from URL: $url")

        // Run the install on the main thread. GeckoView's WebExtensionController callbacks
        // fire on the calling thread, and an exception escaping an extension callback
        // (e.g. a heavy/unsupported extension like uBlock Origin) can crash the whole
        // app process. Containing it here keeps a bad extension from taking the app down.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Installing extension...", Toast.LENGTH_SHORT).show()
            try {
                runtime.webExtensionController.install(url)
                    .accept(
                        { ext ->
                            try {
                                Log.i(TAG, "Successfully installed extension: ${ext?.id}")
                                if (ext != null) {
                                    runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                                    runtime.webExtensionController.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                                }
                                syncUserExtensions()
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, "🧩 Extension installed: ${ext?.id}", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error finalizing installed extension: ${ext?.id}", e)
                            }
                        },
                        { error ->
                            Log.e(TAG, "Failed to install extension from: $url", error)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "❌ Installation failed: ${error?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
            } catch (e: Exception) {
                // Synchronous failure (malformed URL, unsupported package, etc.) must
                // never propagate and crash the app.
                Log.e(TAG, "Synchronous failure installing extension from: $url", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "❌ Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun syncUserExtensions() {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.list()
            .accept(
                { list ->
                    val coreIds = listOf(GRABBER_ID, "omni-universal-copy@omnibrowser.app", AI_BLOCKER_ID, "omni-agent@omnibrowser.app")
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
                                    try {
                                        registerExtensionAction(extension.id, session, action)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onBrowserAction crashed for ${extension.id}", e)
                                    }
                                }
                                override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                                    try {
                                        registerExtensionAction(extension.id, session, action)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onPageAction crashed for ${extension.id}", e)
                                    }
                                }
                                override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                    return try {
                                        handleExtensionOpenPopup(extension, action)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onOpenPopup crashed for ${extension.id}", e)
                                        null
                                    }
                                }
                                override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                                    return try {
                                        handleExtensionOpenPopup(extension, action)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onTogglePopup crashed for ${extension.id}", e)
                                        null
                                    }
                                }
                            })
                        }
                        val context = appContext
                        val savedOrder = if (context != null) {
                            runBlocking {
                                try {
                                    val prefs = context.dataStore.data.first()
                                    prefs[EXTENSION_ORDER_KEY]?.split(",") ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        } else {
                            emptyList()
                        }

                        val sorted = filtered.sortedWith(compareBy {
                            val idx = savedOrder.indexOf(it.id)
                            if (idx == -1) Int.MAX_VALUE else idx
                        })

                        userExtensions.clear()
                        userExtensions.addAll(sorted)

                        // Load real icons for each extension asynchronously
                        sorted.forEach { ext ->
                            try {
                                val iconImage = ext.metaData?.icon ?: return@forEach
                                iconImage.getBitmap(128).accept(
                                    { bitmap ->
                                        if (bitmap != null) {
                                            extensionIcons[ext.id] = bitmap
                                        }
                                    },
                                    { err -> Log.w(TAG, "Could not load icon for ${ext.id}: $err") }
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Icon load failed for ${ext.id}", e)
                            }
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to list extensions", error)
                }
            )
    }

    fun saveExtensionOrder() {
        val context = appContext ?: return
        viewModelScope.launch {
            try {
                context.dataStore.edit { preferences ->
                    val order = userExtensions.map { it.id }
                    preferences[EXTENSION_ORDER_KEY] = order.joinToString(",")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save extension order", e)
            }
        }
    }

    fun reorderUserExtensions(fromIndex: Int, toIndex: Int) {
        if (fromIndex in userExtensions.indices && toIndex in userExtensions.indices) {
            val item = userExtensions.removeAt(fromIndex)
            userExtensions.add(toIndex, item)
            saveExtensionOrder()
        }
    }

    fun saveExtensionViewMode(context: Context, mode: String) {
        viewModelScope.launch {
            try {
                context.dataStore.edit { preferences ->
                    preferences[EXTENSION_VIEW_MODE_KEY] = mode
                }
                extensionViewMode = mode
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save extension view mode", e)
            }
        }
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
        Toast.makeText(context, "Added to Home Shortcuts", Toast.LENGTH_SHORT).show()
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

    fun saveCookieBehavior(context: Context, value: Int) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[COOKIE_BEHAVIOR_KEY] = value }
            cookieBehavior = value
            updateRuntimeContentBlocking(context)
        }
    }
    
    fun saveDoNotTrack(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[DO_NOT_TRACK_KEY] = value }
            doNotTrack = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }
    
    fun saveSafeBrowsingLevel(context: Context, value: Int) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[SAFE_BROWSING_LEVEL_KEY] = value }
            safeBrowsingLevel = value
            updateRuntimeContentBlocking(context)
            writeGeckoConfigFile(context.applicationContext)
        }
    }
    
    fun savePreloadPages(context: Context, value: Int) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[PRELOAD_PAGES_KEY] = value }
            preloadPages = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }
    
    fun saveLockIncognito(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[LOCK_INCOGNITO_KEY] = value }
            lockIncognito = value
        }
    }
    
    fun saveCompromisedPasswordWarning(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[COMPROMISED_PASSWORD_WARNING_KEY] = value }
            compromisedPasswordWarning = value
        }
    }
    
    fun saveHttpsOnlyMode(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[HTTPS_ONLY_MODE_KEY] = value }
            httpsOnlyMode = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }

    fun writeGeckoConfigFile(context: Context) {
        val file = File(context.filesDir, "geckoview-config.yaml")
        try {
            val sb = java.lang.StringBuilder()
            sb.append("pref:\n")
            sb.append("  dom.ipc.processCount: 1\n")
            sb.append("  dom.ipc.processCount.webIsolated: 1\n")
            sb.append("  privacy.donottrackheader.enabled: ${doNotTrack}\n")
            sb.append("  dom.security.https_only_mode: ${httpsOnlyMode || safeBrowsingLevel == 2}\n")
            if (preloadPages == 0) {
                sb.append("  network.dns.disablePrefetch: true\n")
                sb.append("  network.prefetch-next: false\n")
            } else {
                sb.append("  network.dns.disablePrefetch: false\n")
                sb.append("  network.prefetch-next: true\n")
            }
            file.writeText(sb.toString())
            Log.d("BrowserViewModel", "Updated geckoview-config.yaml: \n$sb")
        } catch (e: Exception) {
            Log.e("BrowserViewModel", "Failed to write geckoview-config.yaml", e)
        }
    }

    fun updateRuntimeContentBlocking(context: Context) {
        writeGeckoConfigFile(context)
    }

    fun clearCustomBrowsingData(
        context: Context,
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearPasswords: Boolean,
        clearAutofill: Boolean,
        timeRangeMinutes: Int,
        onComplete: () -> Unit
    ) {
        val runtime = geckoRuntime
        val appCtx = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cutoffTime = if (timeRangeMinutes == -1) 0L else System.currentTimeMillis() - (timeRangeMinutes * 60 * 1000L)
                
                if (clearHistory) {
                    if (timeRangeMinutes == -1) {
                        clearAllHistory()
                    } else {
                        clearHistorySince(cutoffTime)
                    }
                }
                
                if (clearPasswords) {
                    if (timeRangeMinutes == -1) {
                        clearAllSavedPasswords()
                    } else {
                        clearSavedPasswordsSince(cutoffTime)
                    }
                }
                
                if (runtime != null) {
                    var flags: Long = 0L
                    if (clearCookies) {
                        flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
                                  org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA or
                                  org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES or
                                  org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
                    }
                    if (clearCache) {
                        flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.NETWORK_CACHE or
                                  org.mozilla.geckoview.StorageController.ClearFlags.IMAGE_CACHE
                    }
                    
                    if (flags != 0L) {
                        withContext(Dispatchers.Main) {
                            runtime.storageController.clearData(flags).accept(
                                { Log.d("BrowserViewModel", "Gecko custom clear completed.") },
                                { err -> Log.e("BrowserViewModel", "Gecko custom clear error", err) }
                            )
                        }
                    }
                }
                
                if (clearCache) {
                    val cacheDir = appCtx.cacheDir
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                        cacheDir.mkdirs()
                    }
                    val tempDownloadsDir = File(appCtx.filesDir, "temp_downloads")
                    if (tempDownloadsDir.exists()) {
                        tempDownloadsDir.deleteRecursively()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "Failed to clear custom browsing data", e)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun saveTabLayoutMode(context: Context, mode: String) {
        tabLayoutMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[TAB_LAYOUT_MODE_KEY] = mode
            }
        }
    }

    fun saveAutoCloseTabsDays(context: Context, days: Int) {
        autoCloseTabsDays = days
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[AUTO_CLOSE_TABS_DAYS_KEY] = days
            }
        }
    }

    fun saveOpenTabsInBackground(context: Context, value: Boolean) {
        openTabsInBackground = value
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[OPEN_TABS_IN_BACKGROUND_KEY] = value
            }
        }
    }

    fun saveAccessibilityTextScale(context: Context, scale: Float) {
        accessibilityTextScale = scale
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[ACCESSIBILITY_TEXT_SCALE_KEY] = scale
            }
            writeGeckoConfigFile(context.applicationContext)
            viewModelScope.launch(Dispatchers.Main) {
                reload()
            }
        }
    }

    fun saveAccessibilityForceZoom(context: Context, value: Boolean) {
        accessibilityForceZoom = value
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[ACCESSIBILITY_FORCE_ZOOM_KEY] = value
            }
            viewModelScope.launch(Dispatchers.Main) {
                reload()
            }
        }
    }

    fun saveAccessibilityHighContrast(context: Context, value: Boolean) {
        accessibilityHighContrast = value
        viewModelScope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { preferences ->
                preferences[ACCESSIBILITY_HIGH_CONTRAST_KEY] = value
            }
            writeGeckoConfigFile(context.applicationContext)
        }
    }

    fun checkAutoCloseTabs(context: Context) {
        val days = autoCloseTabsDays
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val toClose = tabs.filter { it.id != activeTabId && it.lastActiveTime < cutoff }
        if (toClose.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.Main) {
                toClose.forEach { tab ->
                    closeTab(tab.id, context)
                }
                saveTabs()
                Log.d("BrowserViewModel", "Auto-closed ${toClose.size} inactive tabs.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dismissExtensionPopup()
        translationManager.close()
        tts?.shutdown()
    }
}

