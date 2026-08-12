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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import com.rebelroot.omni.privacy.TorManager
import com.rebelroot.omni.privacy.EmbeddedTorManager
import com.rebelroot.omni.privacy.TorState
import com.rebelroot.omni.tools.locker.PrivateLockerManager
import com.rebelroot.omni.tools.passwords.PasswordEntry
import com.rebelroot.omni.tools.passwords.PasswordVaultManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.json.JSONObject
import org.json.JSONArray
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import com.rebelroot.omni.tools.qrcode.QrCodeDecoder
import com.rebelroot.omni.ThemeStateHolder
import java.lang.ref.WeakReference
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.speech.tts.TextToSpeech
import java.util.UUID
import kotlinx.coroutines.Dispatchers

sealed class BackupImportResult {
    data class Success(val restored: Int, val skipped: Int) : BackupImportResult()
    object InvalidFile : BackupImportResult()
    object InvalidVersion : BackupImportResult()
}

val Context.dataStore by preferencesDataStore(name = "omni_settings")

class BrowserViewModel : ViewModel() {

    companion object {
        internal const val TAG = "BrowserViewModel"

        /** Default maximum number of background tabs with live GeckoSessions. */
        internal const val DEFAULT_MAX_LIVE_TABS = 24

        /**
         * Maximum number of background tabs that may be soft-suspended (session open
         * but inactive). Once this is exceeded, the oldest soft-suspended tabs are
         * hard-suspended (session closed). Keeping sessions open preserves JS/DOM state
         * for dynamic sites (YouTube, Instagram, etc.) so switching never shows a blank page.
         */
        internal const val MAX_SOFT_SUSPENDED_TABS = 8

        internal const val GRABBER_ID = "omni-media-grabber@omnibrowser.app"
        internal const val AI_BLOCKER_ID = "omni-ai-blocker@omnibrowser.app"
        // Bundled WebExtension that routes traffic through the active Tor / SOCKS
        // proxy via the WebExtension `proxy` API (network.proxy.* prefs do not
        // route on GeckoView/Android). Always-on; treated as a protected core id.
        internal const val PROXY_ROUTER_ID = "omni-proxy-router@omnibrowser.app"

        val OPEN_EXTERNAL_APP_ALLOWED_KEY = booleanPreferencesKey("open_external_app_allowed")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
        val AI_BLOCKER_ENABLED_KEY = booleanPreferencesKey("ai_blocker_enabled")
        val NATIVE_PLAYER_ENABLED_KEY = booleanPreferencesKey("native_player_enabled")
        val YOUTUBE_ENABLED_KEY = booleanPreferencesKey("youtube_enabled")
        val MEDIA_GRABBER_ENABLED_KEY = booleanPreferencesKey("media_grabber_enabled")
        /** Background media detection master toggle (Issue #73). */
        val MEDIA_DETECTION_ENABLED_KEY = booleanPreferencesKey("media_detection_enabled")
        /** Show the dedicated media action button in the address bar (Issue #73). */
        val MEDIA_BUTTON_ENABLED_KEY = booleanPreferencesKey("media_button_enabled")
        /** Legacy/advanced: automatically open the media panel (default OFF — no intrusive popup). */
        val MEDIA_AUTO_OPEN_KEY = booleanPreferencesKey("media_auto_open")
        /** Validate streams before showing them as playable (Issue #73). */
        val MEDIA_VALIDATE_ENABLED_KEY = booleanPreferencesKey("media_validate_enabled")
        val EXTERNAL_DOWNLOAD_MANAGER_KEY = booleanPreferencesKey("external_download_manager_enabled")
        val DEFAULT_DOWNLOADER_KEY = stringPreferencesKey("default_downloader")
        val ASK_BEFORE_DOWNLOAD_KEY = booleanPreferencesKey("ask_before_download")
        val DOWNLOAD_WIFI_ONLY_KEY = booleanPreferencesKey("download_wifi_only")
        val MAX_CONCURRENT_DOWNLOADS_KEY = intPreferencesKey("max_concurrent_downloads")
        val DOWNLOAD_NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("download_notifications_enabled")
        val DOWNLOAD_SOUND_ENABLED_KEY = booleanPreferencesKey("download_sound_enabled")
        val DOWNLOAD_VIBRATE_ENABLED_KEY = booleanPreferencesKey("download_vibrate_enabled")
        // Chrome-on-Android UA shared by the download hand-off (matches the one used by
        // StreamDownloadEngine so external managers see the same identity as the browser).
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val PROXY_PROVIDER_KEY = stringPreferencesKey("proxy_provider")
        val TOR_USE_BRIDGES_KEY = booleanPreferencesKey("tor_use_bridges")
        val TOR_AUTO_CONNECT_KEY = booleanPreferencesKey("tor_auto_connect")
        val CUSTOM_SOCKS_HOST_KEY = stringPreferencesKey("custom_socks_host")
        val CUSTOM_SOCKS_PORT_KEY = intPreferencesKey("custom_socks_port")
        val CUSTOM_DNS_KEY = stringPreferencesKey("custom_dns")
        val DOH_ENABLED_KEY = booleanPreferencesKey("doh_enabled")
        val DOH_URI_KEY = stringPreferencesKey("doh_uri")
        val DOT_ENABLED_KEY = booleanPreferencesKey("dot_enabled")
        val DOT_HOST_KEY = stringPreferencesKey("dot_host")
        val BLOCK_QUIC_KEY = booleanPreferencesKey("block_quic")
        val DISABLE_WEBRTC_KEY = booleanPreferencesKey("disable_webrtc")
        val RANDOMIZE_UA_KEY = booleanPreferencesKey("randomize_ua")
        val FINGERPRINT_PROTECTION_KEY = booleanPreferencesKey("fingerprint_protection")
        val CLEAR_COOKIES_ON_SHUTDOWN_KEY = booleanPreferencesKey("clear_cookies_on_shutdown")
        val AUTO_ROTATE_IDENTITY_KEY = booleanPreferencesKey("auto_rotate_identity")
        val SEARCH_ENGINE_KEY = stringPreferencesKey("default_search_engine")
        val CUSTOM_SEARCH_URL_KEY = stringPreferencesKey("custom_search_url")
        val CUSTOM_SUGGEST_URL_KEY = stringPreferencesKey("custom_suggest_url")
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
        private const val FORCE_DARK_EXTENSION_ID = "omni-force-dark@omnibrowser.app"
        val SHOW_SCROLL_BUTTONS_KEY = booleanPreferencesKey("show_scroll_buttons")
        val NAV_BAR_HIDE_TOP_KEY = booleanPreferencesKey("nav_bar_hide_top")
        val NAV_BAR_HIDE_BOTTOM_KEY = booleanPreferencesKey("nav_bar_hide_bottom")
        val HIDE_REFRESH_INDICATOR_KEY = booleanPreferencesKey("hide_refresh_indicator")
        val ADDRESS_BAR_POSITION_KEY = stringPreferencesKey("address_bar_position")
        val APP_ICON_STATE_KEY = stringPreferencesKey("app_icon_state")
        val CUSTOM_ICON_PATH_KEY = stringPreferencesKey("custom_icon_path")
        val BROWSER_WALLPAPER_URI_KEY = stringPreferencesKey("browser_wallpaper_uri")
        val CHANGE_WALLPAPER_DAILY_KEY = booleanPreferencesKey("change_wallpaper_daily")
        val LAST_DAILY_WALLPAPER_DATE_KEY = stringPreferencesKey("last_daily_wallpaper_date")
        val DAILY_WALLPAPER_SEED_KEY = intPreferencesKey("daily_wallpaper_seed")
        val SHOW_DISCOVER_FEED_KEY = booleanPreferencesKey("show_discover_feed")
        val SHOW_BOTTOM_NAV_BAR_KEY = booleanPreferencesKey("show_bottom_nav_bar")
        val HIDE_HOME_BOTTOM_NAV_KEY = booleanPreferencesKey("hide_home_bottom_nav")
        val CHROME_NAV_BAR_KEY = booleanPreferencesKey("chrome_nav_bar_enabled")
        val SHOW_HOME_LOGO_KEY = booleanPreferencesKey("show_home_logo")
        val SHOW_HOME_SHORTCUTS_KEY = booleanPreferencesKey("show_home_shortcuts")
        val WALLPAPER_DIM_KEY = floatPreferencesKey("wallpaper_dim")
        val WALLPAPER_BLUR_KEY = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_SCALE_KEY = floatPreferencesKey("wallpaper_scale")
        val WALLPAPER_OFFSET_X_KEY = floatPreferencesKey("wallpaper_offset_x")
        val WALLPAPER_OFFSET_Y_KEY = floatPreferencesKey("wallpaper_offset_y")
        val SHORTCUT_TILE_STYLE_KEY = stringPreferencesKey("shortcut_tile_style")
        val HOME_UI_SCALE_KEY = floatPreferencesKey("home_ui_scale")
        val BOTTOM_NAV_SCALE_KEY = floatPreferencesKey("bottom_nav_scale")
        val SHOW_PRIVACY_STATS_KEY = booleanPreferencesKey("show_privacy_stats")
        val MINIMALIST_FOCUS_MODE_KEY = booleanPreferencesKey("minimalist_focus_mode")
        val TRACKERS_BLOCKED_COUNT_KEY = intPreferencesKey("trackers_blocked_count")
        val QUICK_TOOLS_ORDER_KEY = stringPreferencesKey("quick_tools_order")
        val MEDIA_SNIFFER_BLOCKLIST_KEY = stringSetPreferencesKey("media_sniffer_blocklist")
        val MEDIA_SNIFFER_MIN_DURATION_SEC_KEY = intPreferencesKey("media_sniffer_min_duration_sec")
        val NEVER_SAVE_PASSWORD_DOMAINS_KEY = stringSetPreferencesKey("never_save_password_domains")



        // Native Player Settings Keys
        val PLAYER_DEFAULT_QUALITY_KEY = stringPreferencesKey("player_default_quality")
        val PLAYER_AUTOPLAY_KEY = booleanPreferencesKey("player_autoplay")
        val PLAYER_LOOP_KEY = booleanPreferencesKey("player_loop")
        val PLAYER_BRIGHTNESS_GESTURE_KEY = booleanPreferencesKey("player_brightness_gesture")
        val PLAYER_VOLUME_GESTURE_KEY = booleanPreferencesKey("player_volume_gesture")
        val PLAYER_RESUME_PLAYBACK_KEY = booleanPreferencesKey("player_resume_playback")
        val PLAYER_BACKGROUND_PLAYBACK_KEY = booleanPreferencesKey("player_background_playback")
        val EXTENSION_ORDER_KEY = stringPreferencesKey("extension_order")
        val EXTENSION_DISABLED_IDS_KEY = stringPreferencesKey("extension_disabled_ids")
        val EXTENSION_VIEW_MODE_KEY = stringPreferencesKey("extension_view_mode")
        val CREAMY_MODE_KEY = booleanPreferencesKey("creamy_mode")
        
        val COOKIE_BEHAVIOR_KEY = androidx.datastore.preferences.core.intPreferencesKey("cookie_behavior")
        val DO_NOT_TRACK_KEY = booleanPreferencesKey("do_not_track")
        val SAFE_BROWSING_LEVEL_KEY = androidx.datastore.preferences.core.intPreferencesKey("safe_browsing_level")
        val PRELOAD_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("preload_pages")
        val LOCK_INCOGNITO_KEY = booleanPreferencesKey("lock_incognito")
        val COMPROMISED_PASSWORD_WARNING_KEY = booleanPreferencesKey("compromised_password_warning")
        val HTTPS_ONLY_MODE_KEY = booleanPreferencesKey("https_only_mode")
        val WEBRENDER_ALL_KEY = booleanPreferencesKey("gfx_webrender_all")
        val LAYERS_ACCELERATION_KEY = booleanPreferencesKey("layers_acceleration_force_enabled")
        val FORCE_HIGH_REFRESH_RATE_KEY = booleanPreferencesKey("layout_frame_rate_120")
        val DEV_NOTES_OVERVIEW_SEEN_KEY = booleanPreferencesKey("dev_notes_overview_seen")
        val PASSWORD_MIGRATION_DONE_KEY = booleanPreferencesKey("password_migration_done")
        val DEVNOTES_PASSWORD_MIGRATION_KEY = booleanPreferencesKey("devnotes_password_migration_done")
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
    val activeTab: TabState? get() = tabs.find { it.id == activeTabId }
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
    var selectionScreenRect by mutableStateOf<android.graphics.RectF?>(null)


    // Browser History System
    val historyList = mutableStateListOf<HistoryEntry>()

    // Feature Modules
    val mediaInterceptor = MediaInterceptor()
    /** Monotonic counter used to tag media with the active page/session for
     *  deterministic per-page invalidation (no stale media across navigations). */
    private var mediaPageIdCounter = 0
    fun notifyPageNavigation() {
        mediaPageIdCounter++
        mediaInterceptor.setActivePage("page-$mediaPageIdCounter")
    }
    lateinit var ffmpegLoader: FFmpegLoader
    lateinit var ffmpegBridge: FFmpegBridge
    lateinit var streamDownloadEngine: StreamDownloadEngine
    lateinit var vpnManager: VpnManager
    lateinit var torManager: TorManager
    lateinit var embeddedTorManager: EmbeddedTorManager
    lateinit var adBlockManager: com.rebelroot.omni.browser.adblock.AdBlockManager
    lateinit var visualBlockManager: com.rebelroot.omni.browser.adblock.VisualBlockManager
    lateinit var userAgentManager: com.rebelroot.omni.browser.useragent.UserAgentManager
    var isVisualBlockModeActive by mutableStateOf(false)
    var navigateToVisualBlockSettingsTrigger by mutableStateOf(false)
    var navigateToUserAgentSettingsTrigger by mutableStateOf(false)
    val translationManager = com.rebelroot.omni.tools.TranslationManager()
    /** Bridge for offline/hybrid page translation (content script <-> coordinator). */
    internal val omniTranslateBridge = com.rebelroot.omni.ai.web.OmniTranslateBridge(translationManager.translationCoordinator)
    /** Active per-tab page-translation controllers. */
    internal val pageTranslationControllers = mutableMapOf<String, com.rebelroot.omni.ai.web.WebTranslationController>()
    internal var copyManager: UniversalCopyManager? = null
    internal var aiBlockerManager: BuiltInExtensionManager? = null
    internal var forceDarkManager: BuiltInExtensionManager? = null
    internal var appContext: Context? = null

    // GeckoView Reference for capturePixels
    internal var activeGeckoViewRef: WeakReference<GeckoView>? = null

    fun setActiveGeckoView(geckoView: GeckoView) {
        activeGeckoViewRef = WeakReference(geckoView)
    }

    fun clearActiveGeckoView(geckoView: GeckoView? = null) {
        if (geckoView == null || activeGeckoViewRef?.get() == geckoView) {
            activeGeckoViewRef = null
        }
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
        "save_pdf", "vpn", "pin_web_app", "auto_scroll", "qr_scan_page",
        "qr_generator", "console_log", "dev_notes", "site_style"
    )
    var quickToolsOrder by mutableStateOf(DEFAULT_QUICK_TOOLS_ORDER)

    // UI States
    var currentUrl by mutableStateOf("about:blank")
    var isFullscreen by mutableStateOf(false)
    var isVideoPlayingInPage by mutableStateOf(false)
    var isInnerScrolled by mutableStateOf(false)
    
    /** Flag set when the browser activity/session was launched via an external ACTION_VIEW intent (e.g., RSS reader, email) */
    var isExternalIntentLaunch by mutableStateOf(false)

    /** When true (default), user-initiated link taps can open external apps. Automatic redirects are always blocked. */
    var isOpenExternalAppAllowed by mutableStateOf(true)
    var isUniversalCopyEnabled by mutableStateOf(false)
    var isAiBlockerEnabled by mutableStateOf(false)
    var isMediaGrabberEnabled by mutableStateOf(true)
    /** Background media detection (Issue #73). Defaults ON. */
    var isMediaDetectionEnabled by mutableStateOf(true)
    /** Show the dedicated media action button in the address bar (Issue #73). Defaults ON. */
    var isMediaButtonEnabled by mutableStateOf(true)
    /** Legacy/advanced auto-open of the media panel. Defaults OFF (no intrusive popup). */
    var isMediaAutoOpenEnabled by mutableStateOf(false)
    /** Validate streams before showing them as playable (Issue #73). Defaults ON. */
    var isMediaValidateEnabled by mutableStateOf(true)
    data class ExternalDownloaderApp(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable? = null
    )

    /** When true, downloads are handed off to an external download manager (ADM / 1DM / …) via a chooser. Default OFF. */
    var isExternalDownloadManagerEnabled by mutableStateOf(false)
    var defaultDownloader by mutableStateOf("internal") // "internal", "system", "external_chooser", or "package:<pkg_name>"
    var askBeforeDownload by mutableStateOf(false)
    var downloadWifiOnly by mutableStateOf(false)
    var maxConcurrentDownloads by mutableStateOf(3)
    var downloadNotificationsEnabled by mutableStateOf(true)
    var downloadSoundEnabled by mutableStateOf(true)
    var downloadVibrateEnabled by mutableStateOf(false)
    var isNativePlayerEnabled by mutableStateOf(true)
    var isYouTubeEnabled by mutableStateOf(false)
    var mediaSnifferBlocklist by mutableStateOf<Set<String>>(emptySet())
    var mediaSnifferMinDurationSec by mutableStateOf(0)
    var neverSavePasswordDomains by mutableStateOf<Set<String>>(emptySet())
    var pendingVideoUrl: String? = null
    internal var passwordVaultManager: PasswordVaultManager? = null
    internal var passwordVaultSyncJob: Job? = null
    /** Credentials saved before the vault finished opening — flushed in attachPasswordVault. */
    internal val pendingVaultWrites = mutableListOf<PasswordEntry>()
    data class PendingExternalAppRequest(
        val uri: String,
        val packageName: String? = null,
        val fallbackUrl: String? = null,
        val blockedAutomatically: Boolean = false,
        val sourceHost: String = ""
    )
    var pendingExternalAppRequest by mutableStateOf<PendingExternalAppRequest?>(null)
    var activeVideoCookies by mutableStateOf<String?>(null)
    val customVpnConfig: String? = null
    var proxyProvider by mutableStateOf("direct")
    var isTorUseBridges by mutableStateOf(false)
    var isTorAutoConnect by mutableStateOf(false)
    var customSocksHost by mutableStateOf("")
    var customSocksPort by mutableStateOf(9050)
    var customDns by mutableStateOf("")
    var isDohEnabled by mutableStateOf(false)
    var dohUri by mutableStateOf("https://dns.google/dns-query")
    var isDotEnabled by mutableStateOf(false)
    var dotHost by mutableStateOf("")
    var isBlockQuic by mutableStateOf(true)
    var isDisableWebrtc by mutableStateOf(false)
    var isRandomizeUa by mutableStateOf(false)
    var isFingerprintProtection by mutableStateOf(false)
    var isClearCookiesOnShutdown by mutableStateOf(false)
    var isAutoRotateIdentity by mutableStateOf(false)
    var privacyRestartNeeded by mutableStateOf(false)
    var selectedSearchEngine by mutableStateOf("Google")
    var customSearchUrl by mutableStateOf("")
    var customSuggestUrl by mutableStateOf("")
    var customSearchEngines by mutableStateOf<List<CustomSearchEngine>>(emptyList())
    val searchSuggestions = androidx.compose.runtime.mutableStateListOf<String>()
    var isDarkThemeEnabled by mutableStateOf(true)
    var isAmoledMode by mutableStateOf(false)
    var isCreamyMode by mutableStateOf(false)
    var isDynamicColorEnabled by mutableStateOf(false)
    var isIncognitoUnlocked by mutableStateOf(false)
    var cookieBehavior by mutableStateOf(5)
    var doNotTrack by mutableStateOf(true)
    var safeBrowsingLevel by mutableStateOf(1)
    var preloadPages by mutableStateOf(1)
    var lockIncognito by mutableStateOf(false)
    var compromisedPasswordWarning by mutableStateOf(true)
    var httpsOnlyMode by mutableStateOf(false)
    var isWebRenderEnabled by mutableStateOf(true)
    var isGpuAccelerationEnabled by mutableStateOf(true)
    var isForceHighRefreshRate by mutableStateOf(true)
    var tabLayoutMode by mutableStateOf("Grid")
    var autoCloseTabsDays by mutableStateOf(0)
    var openTabsInBackground by mutableStateOf(false)
    var lastBackgroundTabOpenedTitle by mutableStateOf("")
    var lastBackgroundTabOpenedUrl by mutableStateOf("")
    var lastBackgroundTabOpenedId by mutableStateOf("")
    var showBackgroundTabNotification by mutableStateOf(false)
    private var backgroundTabDismissJob: kotlinx.coroutines.Job? = null

    fun triggerBackgroundTabNotification(tab: TabState) {
        lastBackgroundTabOpenedTitle = tab.title?.takeIf { it.isNotBlank() && it != "New Tab" && it != "about:blank" }
            ?: try {
                android.net.Uri.parse(tab.url).host?.removePrefix("www.") ?: tab.url
            } catch (_: Exception) {
                tab.url
            }
        if (lastBackgroundTabOpenedTitle.isBlank() || lastBackgroundTabOpenedTitle == "about:blank") {
            lastBackgroundTabOpenedTitle = "New Tab"
        }
        lastBackgroundTabOpenedUrl = tab.url
        lastBackgroundTabOpenedId = tab.id
        showBackgroundTabNotification = true

        backgroundTabDismissJob?.cancel()
        backgroundTabDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            showBackgroundTabNotification = false
        }
    }
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
    var showScrollButtons by mutableStateOf(true)
    var currentScrollRange by mutableStateOf(0)
    var currentScrollExtent by mutableStateOf(0)
    var currentScrollOffset by mutableStateOf(0)
    var refreshScrollMetrics: (() -> Unit)? = null
    var pageScrollHeight by mutableStateOf(0f)
    var pageViewportHeight by mutableStateOf(0f)
    var navBarHideTop by mutableStateOf(true)
    var navBarHideBottom by mutableStateOf(true)
    var hideRefreshIndicator by mutableStateOf(false)
    var addressBarPosition by mutableStateOf("Split")
    var appIconState by mutableStateOf("Default")
    var customIconPath by mutableStateOf<String?>(null)
    // Wallpaper and UI scale fields are pre-seeded from UiStateHolder, which is
    // populated synchronously in OmniApplication.onCreate() before the first
    // Compose frame — this eliminates the visible layout jump and wallpaper pop-in.
    var browserWallpaperUri by mutableStateOf<String?>(com.rebelroot.omni.UiStateHolder.browserWallpaperUri)
    var changeWallpaperDaily by mutableStateOf(false)
    var showDiscoverFeed by mutableStateOf(false)
    var showHomeLogo by mutableStateOf(true)
    var showHomeShortcuts by mutableStateOf(true)
    var showBottomNavBar by mutableStateOf(true)
    var hideHomeBottomNav by mutableStateOf(false)
    var chromeNavBarEnabled by mutableStateOf(false)
    var uiScale by mutableStateOf(com.rebelroot.omni.UiStateHolder.uiScale)
    var wallpaperDim by mutableStateOf(com.rebelroot.omni.UiStateHolder.wallpaperDim)
    var wallpaperBlur by mutableStateOf(com.rebelroot.omni.UiStateHolder.wallpaperBlur)
    var wallpaperScale by mutableStateOf(com.rebelroot.omni.UiStateHolder.wallpaperScale)
    var wallpaperOffsetX by mutableStateOf(com.rebelroot.omni.UiStateHolder.wallpaperOffsetX)
    var wallpaperOffsetY by mutableStateOf(com.rebelroot.omni.UiStateHolder.wallpaperOffsetY)
    var lastDailyWallpaperDate by mutableStateOf<String?>(null)
    var dailyWallpaperSeed by mutableStateOf(0)
    var shouldOpenTabsSheetOnLaunch by mutableStateOf(false)
    var shortcutTileStyle by mutableStateOf("Circle")
    var homeUiScale by mutableStateOf(com.rebelroot.omni.UiStateHolder.homeUiScale)
    var bottomNavScale by mutableStateOf(com.rebelroot.omni.UiStateHolder.bottomNavScale)
    var showPrivacyStatsWidget by mutableStateOf(true)
    var isMinimalistFocusMode by mutableStateOf(false)
    var trackersBlockedCount by androidx.compose.runtime.mutableIntStateOf(0)



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
            val parsed = Uri.parse(intentUri)
            val lower = intentUri.lowercase()

            // Extract target URL from Google redirect parameters e.g. google.com/url?url=https://m.youtube.com/... or ?q=...
            if (lower.contains("google.") && lower.contains("/url")) {
                val targetUrl = parsed.getQueryParameter("url") ?: parsed.getQueryParameter("q")
                if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    return targetUrl
                }
            }

            val intent = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank() && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                fallback
            } else {
                val regex = Regex("[;?&]S\\.browser_fallback_url=([^;&#]+)", RegexOption.IGNORE_CASE)
                val match = regex.find(intentUri)
                val url = match?.groupValues?.get(1)
                if (url != null) {
                    java.net.URLDecoder.decode(url, "UTF-8")
                } else if (lower.startsWith("intent://")) {
                    val data = intent.dataString
                    if (!data.isNullOrBlank() && (data.startsWith("http://") || data.startsWith("https://"))) {
                        data
                    } else {
                        val scheme = intent.scheme ?: "https"
                        val hostAndPath = intentUri.substringAfter("intent://").substringBefore("#Intent;").substringBefore(";")
                        if (hostAndPath.isNotBlank()) {
                            "$scheme://$hostAndPath"
                        } else null
                    }
                } else null
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
            return SecurityPolicy.sanitizeFilename(parsed)
        }
        if (!parsed.isNullOrBlank() && parsed.isNotBlank()) {
            return SecurityPolicy.sanitizeFilename("$parsed.bin")
        }
        val cleanContentType = contentType?.trim()?.lowercase()
        return SecurityPolicy.sanitizeFilename(when {
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
        })
    }

    internal fun isGenericDownloadUrl(url: String): Boolean {
        val lower = url.lowercase().trim()
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("about:")) return false

        // Drop fragment (#...) and query (?...) — neither affects whether the
        // *path* points at a downloadable file.
        val noFrag = lower.substringBeforeLast("#")
        val pathAndQuery = noFrag.substringBeforeLast("?")

        // SAFETY: Check if there is any path component after the authority/domain host.
        // For bare domain URLs (e.g. https://example.pk or https://sub.domain.pk/),
        // path is empty or "/", which must NEVER be treated as a downloadable file.
        val parsedUri = runCatching { Uri.parse(lower) }.getOrNull()
        val path = parsedUri?.path
        if (path.isNullOrBlank() || path == "/") {
            return false
        }

        val afterScheme = pathAndQuery.substringAfter("://", "")
        if (!afterScheme.contains("/")) {
            return false
        }
        val pathPart = afterScheme.substringAfter("/", "")
        if (pathPart.isBlank()) {
            return false
        }

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
        // (e.g. example.com, site.io, my.app, example.pk), this is a *bare domain*, not a
        // file. Previously the TLD was mistaken for a file extension if missing from commonTlds.
        val commonTlds = setOf(
            "com","net","org","io","co","ai","app","dev","xyz","info","biz","me","tv",
            "us","uk","de","fr","ru","jp","cn","in","ca","au","gov","edu","mil","int",
            "pk","com.pk","edu.pk","gov.pk","net.pk","org.pk",
            "name","pro","mobi","tech","online","store","site","website","blog","cloud",
            "live","news","shop","email","press","wiki","design","game","gg","sh","top",
            "vip","work","space","fun","club","world","cyou","bid","trade","wang","ren",
            "group","luxe","art","fit","run","plus","zone","care","sale","life","fund",
            "band","cool","best","realty","properties","agency","expert","center","digital",
            "systems","solutions","today","farm","city","town","cash","money","bet",
            "casino","poker","loan","credit","insurance","investments","finance","tax",
            "legal","host","web","law","yoga","pro","tech",
            // Additional TLDs commonly used
            "moe","rip","link","click","download","party","racing","win","date",
            "review","audio","video","photo","pics","pic","men","stream","accountant",
            "science","gq","tk","ml","cf","ga","buzz","guru","ninja","pink","red",
            "blue","black","kim","dad","foo","mov","zip","phd","nyc","one","two"
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

    fun startSystemDownload(
        context: Context,
        url: String,
        filename: String,
        mimeType: String? = null,
        cookies: String? = activeVideoCookies,
        referrerUrl: String? = currentUrl
    ): Boolean {
        // Sanitize filename to prevent path traversal attacks
        val safeFilename = SecurityPolicy.sanitizeFilename(filename)
        return runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(safeFilename)
                setDescription(url)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename)
                mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                if (downloadWifiOnly) {
                    setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI)
                }
                referrerUrl?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Referer", it) }
                cookies?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", CHROME_UA)
            }
            dm.enqueue(request)
            Toast.makeText(context, "Download started via System DownloadManager", Toast.LENGTH_SHORT).show()
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to enqueue system download", e)
            false
        }
    }

    fun startGenericDownload(download: PendingGenericDownload, saveToLocker: Boolean, context: Context) {
        pendingGenericDownload = null
        if (saveToLocker) {
            streamDownloadEngine.startGenericFileDownload(
                url = download.url,
                filename = download.filename,
                contentType = download.contentType,
                saveToLocker = true,
                cookies = activeVideoCookies,
                referrerUrl = currentUrl
            )
            return
        }

        val mode = defaultDownloader
        when {
            mode == "system" -> {
                val success = startSystemDownload(
                    context = context,
                    url = download.url,
                    filename = download.filename,
                    mimeType = download.contentType
                )
                if (!success) {
                    streamDownloadEngine.startGenericFileDownload(
                        url = download.url,
                        filename = download.filename,
                        contentType = download.contentType,
                        saveToLocker = false,
                        cookies = activeVideoCookies,
                        referrerUrl = currentUrl
                    )
                }
            }
            mode == "external_chooser" -> {
                val handedOff = handOffToExternalDownloadManager(
                    context = context,
                    url = download.url,
                    filename = download.filename,
                    contentType = download.contentType
                )
                if (!handedOff) {
                    streamDownloadEngine.startGenericFileDownload(
                        url = download.url,
                        filename = download.filename,
                        contentType = download.contentType,
                        saveToLocker = false,
                        cookies = activeVideoCookies,
                        referrerUrl = currentUrl
                    )
                }
            }
            mode.startsWith("package:") -> {
                val pkg = mode.substringAfter("package:")
                val handedOff = handOffToExternalDownloadManager(
                    context = context,
                    url = download.url,
                    filename = download.filename,
                    contentType = download.contentType,
                    targetPackage = pkg
                )
                if (!handedOff) {
                    streamDownloadEngine.startGenericFileDownload(
                        url = download.url,
                        filename = download.filename,
                        contentType = download.contentType,
                        saveToLocker = false,
                        cookies = activeVideoCookies,
                        referrerUrl = currentUrl
                    )
                }
            }
            else -> { // "internal"
                streamDownloadEngine.startGenericFileDownload(
                    url = download.url,
                    filename = download.filename,
                    contentType = download.contentType,
                    saveToLocker = false,
                    cookies = activeVideoCookies,
                    referrerUrl = currentUrl
                )
            }
        }
    }

    fun startGenericDownload(context: Context, url: String, filename: String, saveToLocker: Boolean = false) {
        streamDownloadEngine.startGenericFileDownload(
            url = url,
            filename = filename,
            contentType = null,
            saveToLocker = saveToLocker,
            cookies = activeVideoCookies,
            referrerUrl = currentUrl
        )
    }

    /**
     * Hands a download URL to an external download manager (ADM, 1DM, …) via the system
     * chooser or directly to a target package.
     */
    fun handOffToExternalDownloadManager(
        context: Context,
        url: String,
        filename: String? = null,
        contentType: String? = null,
        referrerUrl: String? = currentUrl,
        cookies: String? = activeVideoCookies,
        targetPackage: String? = null
    ): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parsed, contentType?.takeIf { it.isNotBlank() } ?: "*/*")
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val headers = Bundle().apply {
                referrerUrl?.takeIf { it.isNotBlank() }?.let { putString("Referer", it) }
                cookies?.takeIf { it.isNotBlank() }?.let { putString("Cookie", it) }
                putString("User-Agent", CHROME_UA)
            }
            putExtra(android.provider.Browser.EXTRA_HEADERS, headers)
            filename?.takeIf { it.isNotBlank() }?.let { putExtra("title", it) }
            if (!targetPackage.isNullOrEmpty()) {
                setPackage(targetPackage)
            }
        }

        return try {
            if (!targetPackage.isNullOrEmpty()) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, "Download with…").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (e: Exception) {
            if (!targetPackage.isNullOrEmpty()) {
                runCatching {
                    intent.setPackage(null)
                    val chooser = Intent.createChooser(intent, "Download with…").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        }
    }

    fun setDefaultDownloader(context: Context, downloader: String) {
        defaultDownloader = downloader
        isExternalDownloadManagerEnabled = (downloader != "internal" && downloader != "system")
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[DEFAULT_DOWNLOADER_KEY] = downloader
                preferences[EXTERNAL_DOWNLOAD_MANAGER_KEY] = isExternalDownloadManagerEnabled
            }
        }
    }

    fun toggleAskBeforeDownload(context: Context) {
        val newState = !askBeforeDownload
        askBeforeDownload = newState
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[ASK_BEFORE_DOWNLOAD_KEY] = newState
            }
        }
    }

    fun toggleDownloadWifiOnly(context: Context) {
        val newState = !downloadWifiOnly
        downloadWifiOnly = newState
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[DOWNLOAD_WIFI_ONLY_KEY] = newState
            }
        }
    }

    fun setMaxConcurrentDownloads(context: Context, limit: Int) {
        maxConcurrentDownloads = limit
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[MAX_CONCURRENT_DOWNLOADS_KEY] = limit
            }
        }
    }

    fun toggleDownloadNotificationsEnabled(context: Context) {
        val newState = !downloadNotificationsEnabled
        downloadNotificationsEnabled = newState
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[DOWNLOAD_NOTIFICATIONS_ENABLED_KEY] = newState
            }
        }
    }

    fun toggleDownloadSoundEnabled(context: Context) {
        val newState = !downloadSoundEnabled
        downloadSoundEnabled = newState
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[DOWNLOAD_SOUND_ENABLED_KEY] = newState
            }
        }
    }

    fun toggleDownloadVibrateEnabled(context: Context) {
        val newState = !downloadVibrateEnabled
        downloadVibrateEnabled = newState
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[DOWNLOAD_VIBRATE_ENABLED_KEY] = newState
            }
        }
    }

    fun getAvailableExternalDownloaders(context: Context): List<ExternalDownloaderApp> {
        val pm = context.packageManager
        val result = mutableMapOf<String, ExternalDownloaderApp>()

        val knownPackages = listOf(
            "com.dv.adm" to "ADM (Advanced Download Manager)",
            "com.dv.adm.pay" to "ADM Pro",
            "idm.internet.download.manager" to "1DM",
            "idm.internet.download.manager.lite" to "1DM Lite",
            "idm.internet.download.manager.adm" to "1DM+",
            "com.al.ndm" to "NDM (Download Manager)",
            "com.delphicoder.flud" to "Flud Torrent",
            "com.delphicoder.flud.paid" to "Flud Torrent Pro",
            "com.tt.android.dm" to "Download Navigator",
            "org.mrb.downloadmanager" to "Download Manager"
        )

        for ((pkg, label) in knownPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString().ifBlank { label }
                val icon = pm.getApplicationIcon(appInfo)
                result[pkg] = ExternalDownloaderApp(appName, pkg, icon)
            } catch (_: Exception) {}
        }

        runCatching {
            val testUri = Uri.parse("https://example.com/file.bin")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(testUri, "*/*")
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && !pkg.contains("browser") && !pkg.contains("chrome") && !pkg.contains("firefox")) {
                    if (!result.containsKey(pkg)) {
                        val appName = info.loadLabel(pm).toString()
                        val icon = info.loadIcon(pm)
                        result[pkg] = ExternalDownloaderApp(appName, pkg, icon)
                    }
                }
            }
        }

        return result.values.toList()
    }

    /**
     * Whether a detected media item can realistically be fetched by an external download
     * manager. Direct MP4/WEBM/audio URLs are fine; HLS/DASH manifests and YouTube split
     * streams (separate [audioUrl]) need FFmpeg muxing that only the internal engine does.
     */
    fun canHandOffMedia(type: MediaInterceptor.MediaType, audioUrl: String?): Boolean {
        if (type == MediaInterceptor.MediaType.HLS ||
            type == MediaInterceptor.MediaType.DASH) return false
        if (audioUrl != null) return false
        return true
    }

    internal fun handleExternalDownloadResponse(response: org.mozilla.geckoview.WebResponse, context: Context) {
        val headers = response.headers
        val disposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        val contentType = headers["Content-Type"] ?: headers["content-type"]
        val cleanContentType = contentType?.lowercase()?.trim() ?: ""

        // Ignore external file downloads if the server responds with HTML or XHTML content
        if (cleanContentType.contains("text/html") || cleanContentType.contains("application/xhtml+xml")) {
            Log.i(TAG, "Ignoring external download response for HTML content: ${response.uri}")
            return
        }

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
        selectionScreenRect = null
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

    // External extension install: GeckoView has NO native install-prompt UI — the
    // delegate IS the prompt. We hold the GeckoResult here until the user answers
    // the in-app permission dialog; returning null from onInstallPromptRequest
    // would abort the installation.
    data class PendingExtensionInstallPrompt(
        val extensionId: String?,
        val extensionName: String?,
        val permissions: List<String>,
        val origins: List<String>,
        val geckoResult: GeckoResult<WebExtension.PermissionPromptResponse>
    )
    var pendingExtensionInstallPrompt by mutableStateOf<PendingExtensionInstallPrompt?>(null)

    // <select> Dropdown Choice: holds the pending ChoicePrompt so the Compose UI
    // can present a native AlertDialog with radio-list of choices for the user to
    // select from. Replaces the previous auto-confirm behavior (Issue #74).
    data class PendingChoicePrompt(
        val geckoResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        val prompt: GeckoSession.PromptDelegate.ChoicePrompt
    )
    var pendingChoicePrompt by mutableStateOf<PendingChoicePrompt?>(null)

    // Date/Time Pickers: holds the pending DateTimePrompt so the Compose UI can
    // present native Android DatePickerDialog / TimePickerDialog for
    // <input type="date|time|month|week|datetime-local"> (Issue #74).
    data class PendingDatePrompt(
        val geckoResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        val prompt: GeckoSession.PromptDelegate.DateTimePrompt
    )
    var pendingDatePrompt by mutableStateOf<PendingDatePrompt?>(null)

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

    // ── <select> Choice Prompt Result Delivery ──────────────────────────────────
    /**
     * Called when the user selects a choice from the native dropdown dialog.
     * [choiceIndex] is the index into prompt.choices, or -1 to dismiss (cancel).
     */
    fun deliverChoicePromptResult(choiceIndex: Int) {
        val pending = pendingChoicePrompt ?: return
        pendingChoicePrompt = null
        if (choiceIndex < 0 || pending.prompt.choices.isEmpty()) {
            pending.geckoResult.complete(pending.prompt.dismiss())
        } else {
            val choices = pending.prompt.choices
            val safeIndex = choiceIndex.coerceAtMost(choices.size - 1)
            pending.geckoResult.complete(pending.prompt.confirm(choices[safeIndex]))
        }
    }

    /**
     * Cancels the pending choice prompt and dismisses it back to GeckoView.
     */
    fun cancelChoicePrompt() {
        val pending = pendingChoicePrompt ?: return
        pendingChoicePrompt = null
        pending.geckoResult.complete(pending.prompt.dismiss())
    }

    // ── Date/Time Picker Result Delivery ───────────────────────────────────────
    /**
     * Called when the user picks a date/time value from the native picker.
     * [isoValue] should be the ISO-format string expected by GeckoView:
     *   - DATE:          "yyyy-MM-dd"
     *   - TIME:          "HH:mm" or "HH:mm:ss"
     *   - MONTH:         "yyyy-MM"
     *   - WEEK:          "yyyy-Www"  (e.g. "2025-W01")
     *   - DATETIME_LOCAL:"yyyy-MM-dd'T'HH:mm"
     * Pass null or empty string to dismiss/cancel.
     */
    fun deliverDateTimePromptResult(isoValue: String?) {
        val pending = pendingDatePrompt ?: return
        pendingDatePrompt = null
        if (isoValue.isNullOrBlank()) {
            pending.geckoResult.complete(pending.prompt.dismiss())
        } else {
            pending.geckoResult.complete(pending.prompt.confirm(isoValue))
        }
    }

    /**
     * Cancels the pending date/time prompt and dismisses it back to GeckoView.
     */
    fun cancelDateTimePrompt() {
        val pending = pendingDatePrompt ?: return
        pendingDatePrompt = null
        pending.geckoResult.complete(pending.prompt.dismiss())
    }

    // ── Lifecycle Cancellation for All Pending Prompts ──────────────────────────
    /**
     * Cancels any pending prompts that belong to the specified tab.
     * Called when a tab is closed or navigated away from, ensuring that
     * GeckoResult handles are never left hanging.
     */
    fun cancelPendingPromptsForTab(tabId: String) {
        if (pendingFilePrompt != null) {
            // FilePrompt doesn't track tabId directly, but we check if the
            // active tab is being closed — if so, cancel it.
            // The pending prompt is tab-scoped via activeTabId checks in the delegate.
        }
        if (pendingChoicePrompt != null) {
            pendingChoicePrompt = null
            // The prompt.dismiss() was already called implicitly by the tab closing.
        }
        if (pendingDatePrompt != null) {
            pendingDatePrompt = null
        }
        if (pendingGoogleOAuthRequest?.tabId == tabId) {
            pendingGoogleOAuthRequest = null
        }
    }

    /**
     * Cancels ALL pending prompts regardless of tab. Used when the BrowserScreen
     * leaves composition or the entire activity is being destroyed.
     */
    fun cancelAllPendingPrompts() {
        if (pendingFilePrompt != null) {
            val p = pendingFilePrompt
            pendingFilePrompt = null
            p?.geckoResult?.complete(p.prompt.dismiss())
        }
        if (pendingChoicePrompt != null) {
            val p = pendingChoicePrompt
            pendingChoicePrompt = null
            p?.geckoResult?.complete(p.prompt.dismiss())
        }
        if (pendingDatePrompt != null) {
            val p = pendingDatePrompt
            pendingDatePrompt = null
            p?.geckoResult?.complete(p.prompt.dismiss())
        }
        pendingGoogleOAuthRequest = null
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

    /** Drops all cached extension icon bitmaps. They are re-fetched on next display.
     *  Called from MainActivity.onTrimMemory(MODERATE/CRITICAL). */
    fun clearIconCache() {
        extensionIcons.clear()
    }
    
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

    /** The credential that was most recently injected — used to show "Switch account" chip */
    var autofillLastUsed by mutableStateOf<SavedPassword?>(null)
    /** True after an autofill was performed on the current page, reset on navigation */
    var autofillWasPerformed by mutableStateOf(false)

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

    fun createNewTabInNewGroup(context: Context, url: String = "about:blank", isIncognito: Boolean = isIncognitoMode) {
        val newGroupId = UUID.randomUUID().toString()
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
        val chosenColor = groupColors[tabGroups.size % groupColors.size]
        val groupNumber = tabGroups.size + 1
        val groupTitle = "Group $groupNumber"

        val newGroup = TabGroup(
            id = newGroupId,
            title = groupTitle,
            color = chosenColor,
            tabIds = emptyList()
        )
        tabGroups.add(newGroup)
        saveTabGroups()

        createNewTab(context, url, groupId = newGroupId, isIncognito = isIncognito)
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
            "externalApp" -> perm?.externalApp ?: "ask"
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
            "externalApp" -> current.copy(externalApp = value)
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
                            lastActiveTime = lastActiveTime,
                            settingsVersion = currentSettingsVersion
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

    fun createNewTab(context: Context, url: String, groupId: String? = null, isIncognito: Boolean = isIncognitoMode) {
        val runtime = getGeckoRuntime(context)
        val isJsAllowed = getSitePermissionValue(url, "javascript") == "allow"
        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognito)
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
            isIncognito = isIncognito,
            settingsVersion = currentSettingsVersion
        )
        
        setupTabSessionListeners(newTab, context)
        tabs.add(newTab)
        if (groupId != null) {
            addTabToGroup(tabId, groupId)
        }
        session.open(runtime)
        if (!openTabsInBackground || tabs.size == 1 || groupId != null) {
            selectTab(newTab.id)
        } else {
            val isIncog = newTab.isIncognito
            if (isIncog && activeIncognitoTabId == null) {
                activeIncognitoTabId = newTab.id
            } else if (!isIncog && activeNormalTabId == null) {
                activeNormalTabId = newTab.id
            }
            triggerBackgroundTabNotification(newTab)
        }
        loadUrlInTab(newTab, url)
        saveTabs()
        // Enforce the live-session cap after adding the new tab.
        enforceSuspendLimit()
    }

    fun dismissContextMenu() {
        activeContextMenu = null
    }

    fun selectTab(tabId: String) {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return

        // Capture a small thumbnail of the outgoing tab while its GeckoView is
        // still live. Scale to 200×112 (16:9, ~87 KB RGB_565) — enough for a
        // preview in the tab strip, cheap enough to hold in memory per tab.
        val outgoingId = activeTabId
        if (outgoingId != null && outgoingId != tabId) {
            activeGeckoViewRef?.get()?.capturePixels()?.then { bmp ->
                if (bmp != null) {
                    val thumbW = 200
                    val thumbH = 112
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, thumbW, thumbH, true)
                    if (scaled !== bmp) bmp.recycle()
                    // Store as RGB_565 to halve memory (no alpha needed for screenshots).
                    val thumb = scaled.copy(android.graphics.Bitmap.Config.RGB_565, false)
                    if (thumb !== scaled) scaled.recycle()
                    val outIdx = tabs.indexOfFirst { it.id == outgoingId }
                    if (outIdx != -1) {
                        tabs[outIdx] = tabs[outIdx].copy(suspendThumbnail = thumb)
                    }
                }
                org.mozilla.geckoview.GeckoResult.fromValue(null)
            }
        }

        // If the target tab is suspended, restore its GeckoSession before switching.
        // resumeTab uses the stored appContext; it's a no-op if already live.
        val ctx = appContext
        if (tabs[tabIndex].isSuspended && ctx != null) {
            resumeTab(tabId, ctx)
        }

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
        
        applySiteStyleToActiveTab()
        
        // Restore the tab's own saved navigation state
        canGoBack = tab.canGoBack
        canGoForward = tab.canGoForward


        // Clear media list when switching tabs to ensure only active tab's media is tracked
        mediaInterceptor.clear()
        notifyPageNavigation()
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

        // After activating a tab, evict least-recently-used background tabs
        // beyond the live limit to keep session count bounded.
        enforceSuspendLimit()
    }

    fun closeTab(tabId: String, context: Context) {
        // Stop any in-flight page translation for this tab so it never mutates
        // a different tab (late-result isolation).
        stopPageTranslation(tabId)
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
                // Cancel any pending prompts so their GeckoResult is not left hanging.
                cancelAllPendingPrompts()
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
        // Cancel any pending prompts for the closing tab so their GeckoResult is not left hanging.
        if (tabToClose.id == activeTabId) {
            cancelAllPendingPrompts()
        }
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

    fun closeAllTabs(context: Context, incognito: Boolean) {
        val targetTabs = tabs.filter { it.isIncognito == incognito }.toList()
        targetTabs.forEach { tab ->
            closeTab(tab.id, context)
        }
    }

    private fun loadUrlInTab(tab: TabState, url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        val lowerInTab = formattedUrl.lowercase()
        if (lowerInTab == "omni:config" || lowerInTab == "omni://config" || lowerInTab == "about:config") {
            formattedUrl = "omni:config"
        }

        if (formattedUrl.startsWith("about:") || formattedUrl.startsWith("omni:")) {
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

        // Pass extension-internal pages (moz-extension://) through directly — never rewrite them as search queries
        if (formattedUrl.startsWith("moz-extension://")) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...", isUriLoaded = true)
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
        // 0. Cancel any pending prompts so their GeckoResult is not left hanging.
        cancelAllPendingPrompts()
        // 0b. Stop and restore any active page translations (all tabs).
        stopAllPageTranslations()

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
            val targetLocales = if (lang.startsWith("en", ignoreCase = true)) {
                arrayOf("en-US", "en")
            } else {
                arrayOf(lang, "en-US", "en")
            }

            val prefs = runBlocking { appCtx.dataStore.data.first() }
            val dnt = prefs[DO_NOT_TRACK_KEY] ?: true
            val hom = prefs[HTTPS_ONLY_MODE_KEY] ?: false
            val pl = prefs[PRELOAD_PAGES_KEY] ?: 1
            val cookieBeh = prefs[COOKIE_BEHAVIOR_KEY] ?: 5
            val sbLevel = prefs[SAFE_BROWSING_LEVEL_KEY] ?: 1
            val hc = prefs[ACCESSIBILITY_HIGH_CONTRAST_KEY] ?: false
            proxyProvider = prefs[PROXY_PROVIDER_KEY] ?: "direct"
            isTorUseBridges = prefs[TOR_USE_BRIDGES_KEY] ?: false
            customSocksHost = prefs[CUSTOM_SOCKS_HOST_KEY] ?: ""
            customSocksPort = prefs[CUSTOM_SOCKS_PORT_KEY] ?: 9050
            customDns = prefs[CUSTOM_DNS_KEY] ?: ""
            isDohEnabled = prefs[DOH_ENABLED_KEY] ?: false
            dohUri = prefs[DOH_URI_KEY] ?: "https://dns.google/dns-query"
            isDotEnabled = prefs[DOT_ENABLED_KEY] ?: false
            dotHost = prefs[DOT_HOST_KEY] ?: ""
            isBlockQuic = prefs[BLOCK_QUIC_KEY] ?: true
            isDisableWebrtc = prefs[DISABLE_WEBRTC_KEY] ?: false
            isRandomizeUa = prefs[RANDOMIZE_UA_KEY] ?: false
            isFingerprintProtection = prefs[FINGERPRINT_PROTECTION_KEY] ?: false
            isClearCookiesOnShutdown = prefs[CLEAR_COOKIES_ON_SHUTDOWN_KEY] ?: false
            isAutoRotateIdentity = prefs[AUTO_ROTATE_IDENTITY_KEY] ?: false

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
                sb.append("  intl.accept_languages: \"${targetLocales.joinToString(", ")}\"\n")
                // Security hardening: Enable Fission site isolation with multiple content
                // processes. Each origin runs in its own process, preventing cross-origin
                // data access even if a renderer is compromised.
                sb.append("  fission.autostart: true\n")
                sb.append("  fission.web_content_process_count: 8\n")
                sb.append("  dom.ipc.processCount: 8\n")
                sb.append("  dom.ipc.processCount.webIsolated: 1\n")
                sb.append("  privacy.donottrackheader.enabled: ${dnt}\n")
                sb.append("  dom.security.https_only_mode: ${hom || sbLevel == 2}\n")
                sb.append("  dom.security.https_first: true\n")
                sb.append("  security.fileuri.strict_origin_policy: true\n")
                sb.append("  privacy.partition.network_state: true\n")
                // Total Cookie Protection (dFPI): isolate cookies per top-level site
                sb.append("  network.cookie.cookieBehavior: 5\n")
                sb.append("  ui.useAccessibilityTheme: ${if (hc) 1 else 0}\n")
                if (pl == 0) {
                    sb.append("  network.dns.disablePrefetch: true\n")
                    sb.append("  network.prefetch-next: false\n")
                } else {
                    sb.append("  network.dns.disablePrefetch: false\n")
                    sb.append("  network.prefetch-next: true\n")
                }
                // Proxy routing via network.proxy.* prefs. On stock GeckoView the
                // WebExtension `proxy` API is NOT available (WebLibre uses a custom-
                // patched GeckoView for that). The prefs ARE honored: when type=1
                // and only SOCKS is set, Gecko uses SOCKS as the universal fallback
                // for all protocols (http/https/ftp). failover_direct=false ensures
                // that if the SOCKS port is unreachable (Tor still bootstrapping),
                // requests ERROR instead of silently leaking the real IP.
                if (proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "custom_proxy" || proxyProvider == "tor_builtin") {
                    val torPort = when {
                        proxyProvider == "tor_builtin" -> EmbeddedTorManager.EMBEDDED_SOCKS_PORT
                        isTorUseBridges -> TorManager.BRIDGE_SOCKS_PORT
                        else -> TorManager.DEFAULT_SOCKS_PORT
                    }
                    val targetHost = customSocksHost.ifBlank { "127.0.0.1" }
                    val targetPort = if (customSocksHost.isNotBlank()) customSocksPort else torPort
                    sb.append("  network.proxy.type: 1\n")
                    sb.append("  network.proxy.socks: $targetHost\n")
                    sb.append("  network.proxy.socks_port: $targetPort\n")
                    sb.append("  network.proxy.socks_remote_dns: true\n")
                    sb.append("  network.proxy.failover_direct: false\n")
                } else {
                    sb.append("  network.proxy.type: 5\n")
                }
                val isProxyActive = proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "custom_proxy" || proxyProvider == "tor_builtin"
                if (isDohEnabled && dohUri.isNotBlank() && !isProxyActive) {
                    sb.append("  network.trr.uri: $dohUri\n")
                    // mode 2 = TRR-first with native-DNS fallback. We deliberately
                    // avoid mode 3 (TRR-only): without a guaranteed bootstrap IP a
                    // misconfigured/unreachable TRR endpoint would black-hole ALL
                    // DNS and break browsing entirely. mode 2 still encrypts
                    // lookups whenever TRR works and degrades gracefully otherwise.
                    sb.append("  network.trr.mode: 2\n")
                } else {
                    sb.append("  network.trr.mode: 0\n")
                }
                val isTorSession = proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "tor_builtin"
                if (isTorSession) {
                    // Tor Security Hardening: Disable WebRTC to prevent STUN/TURN IP leaks over non-proxied interfaces
                    sb.append("  media.peerconnection.enabled: false\n")
                    sb.append("  media.peerconnection.ice.no_host: true\n")
                    // Disable UDP QUIC/HTTP3 to prevent UDP bypass leaks outside SOCKS5 TCP proxying
                    sb.append("  network.quic.enabled: false\n")
                    sb.append("  network.http.http3.enabled: false\n")
                    // Tor Browser anti-fingerprinting & cross-tab circuit isolation
                    sb.append("  privacy.resistFingerprinting: true\n")
                    sb.append("  privacy.firstparty.isolate: true\n")
                } else {
                    sb.append("  media.peerconnection.enabled: ${!isDisableWebrtc}\n")
                    sb.append("  network.quic.enabled: ${!isBlockQuic}\n")
                    sb.append("  network.http.http3.enabled: ${!isBlockQuic}\n")
                }
                if (isRandomizeUa) {
                    val uas = listOf(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
                    )
                    val chosen = uas[(System.currentTimeMillis() / 86400000L).toInt() % uas.size]
                    sb.append("  general.useragent.override: $chosen\n")
                }
                sb.append("  privacy.clearOnShutdown.cache: ${isClearCookiesOnShutdown}\n")
                sb.append("  privacy.clearOnShutdown.cookies: ${isClearCookiesOnShutdown}\n")
                // Disable automatic GeckoView handoff of HTTP/HTTPS URLs to external Android apps
                // (e.g. YouTube app), guaranteeing all web browsing loads cleanly inside Omni Browser tabs.
                sb.append("  network.protocol-handler.external.http: false\n")
                sb.append("  network.protocol-handler.external.https: false\n")
                sb.append("  network.protocol-handler.external-default: false\n")
                sb.append("  geckoview.external_app_handler.enabled: false\n")
                sb.append("  geckoview.intent_dispatched_in_app: false\n")
                if (isFingerprintProtection) {
                    sb.append("  webgl.disabled: true\n")
                    sb.append("  dom.enable_resource_timing: false\n")
                    sb.append("  dom.enable_user_timing: false\n")
                    sb.append("  beacon.enabled: false\n")
                    sb.append("  dom.battery.enabled: false\n")
                    sb.append("  canvas.captureStream.enabled: false\n")
                    sb.append("  dom.webaudio.enabled: false\n")
                }
                configFile.writeText(sb.toString())
                // DIAGNOSTIC: dump the config file content at startup so we can
                // verify the proxy prefs are actually present on disk (and not
                // being clobbered by writeGeckoConfigFile or any other writer).
                Log.i(TAG, "=== geckoview-config.yaml written at startup (proxyProvider=$proxyProvider) ===\n${sb}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write geckoview-config.yaml", e)
            }

            val builder = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(isDebug)
                .consoleOutput(isDebug)
                .debugLogging(isDebug)
                .remoteDebuggingEnabled(isDebug)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .locales(targetLocales) // Configures Accept-Language headers with English fallback
                .contentBlocking(cbSettings)
                .configFilePath(configFile.absolutePath)

            val settings = builder.build()

            try {
                geckoRuntime = GeckoRuntime.create(appCtx, settings)
                geckoRuntimeError = null   // clear any previous failure
                applyProxyPrefsLive()
            } catch (t: Throwable) {
                Log.e(TAG, "GeckoRuntime.create FAILED with config file, trying clean fallback", t)
                try {
                    // Fallback: Delete config file and retry initialization cleanly
                    val cfg = File(appCtx.filesDir, "geckoview-config.yaml")
                    if (cfg.exists()) cfg.delete()
                    
                    val fallbackSettings = GeckoRuntimeSettings.Builder()
                        .aboutConfigEnabled(isDebug)
                        .consoleOutput(isDebug)
                        .debugLogging(isDebug)
                        .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                        .locales(targetLocales)
                        .contentBlocking(cbSettings)
                        .build()
                        
                    geckoRuntime = GeckoRuntime.create(appCtx, fallbackSettings)
                    geckoRuntimeError = null
                    applyProxyPrefsLive()
                } catch (fallbackError: Throwable) {
                    Log.e(TAG, "GeckoRuntime fallback initialization also failed: ${fallbackError.message}", fallbackError)
                    geckoRuntimeError = "${fallbackError.javaClass.simpleName}: ${fallbackError.message}"
                    throw fallbackError
                }
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

                // Only auto-approve our own built-in extensions. External extensions must
                // go through the native GeckoView permission prompt so the user can review.
                private val BUNDLED_EXTENSION_IDS = setOf(
                    "omni-media-grabber@omnibrowser.app",
                    "omni-ai-blocker@omnibrowser.app",
                    "omni-force-dark@omnibrowser.app",
                    "omni-universal-copy@omnibrowser.app"
                )

                private fun isBundledExtension(extension: org.mozilla.geckoview.WebExtension): Boolean {
                    return BUNDLED_EXTENSION_IDS.contains(extension.id)
                }

                override fun onInstallPromptRequest(
                    extension: org.mozilla.geckoview.WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.WebExtension.PermissionPromptResponse>? {
                    if (isBundledExtension(extension)) {
                        Log.d(TAG, "Auto-approving install prompt for bundled extension: ${extension.id}")
                        return org.mozilla.geckoview.GeckoResult.fromValue(
                            org.mozilla.geckoview.WebExtension.PermissionPromptResponse(
                                true, // isPermissionsGranted
                                true, // isPrivateModeGranted
                                false // isTechnicalAndInteractionDataGranted
                            )
                        )
                    }
                    Log.w(TAG, "🔔 Install prompt for external extension ${extension.id}: ${permissions.toList()}. Showing in-app dialog.")
                    // GeckoView has no native prompt UI: returning null here would
                    // abort the installation. Instead, surface the request through
                    // [pendingExtensionInstallPrompt] so the UI can ask the user and
                    // complete the result with their choice.
                    val result = org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.WebExtension.PermissionPromptResponse>()
                    pendingExtensionInstallPrompt = PendingExtensionInstallPrompt(
                        extensionId = extension.id,
                        extensionName = extension.metaData?.name,
                        permissions = permissions.toList(),
                        origins = origins.toList(),
                        geckoResult = result
                    )
                    return result
                }

                override fun onOptionalPrompt(
                    extension: org.mozilla.geckoview.WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                    if (isBundledExtension(extension)) {
                        Log.d(TAG, "Auto-granting optional permissions for bundled extension: ${extension.id}")
                        return org.mozilla.geckoview.GeckoResult.fromValue(
                            org.mozilla.geckoview.AllowOrDeny.ALLOW
                        )
                    }
                    Log.w(TAG, "🛡️ Optional permissions NOT auto-granted for external extension: ${extension.id}")
                    return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                }

                override fun onUpdatePrompt(
                    extension: org.mozilla.geckoview.WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                    if (isBundledExtension(extension)) {
                        Log.d(TAG, "Auto-granting update permissions for bundled extension: ${extension.id}")
                        return org.mozilla.geckoview.GeckoResult.fromValue(
                            org.mozilla.geckoview.AllowOrDeny.ALLOW
                        )
                    }
                    Log.w(TAG, "🛡️ Update permissions NOT auto-granted for external extension: ${extension.id}")
                    return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
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
            torManager = TorManager(appCtx)
            embeddedTorManager = EmbeddedTorManager(appCtx)
            adBlockManager = com.rebelroot.omni.browser.adblock.AdBlockManager(appCtx)
            visualBlockManager = com.rebelroot.omni.browser.adblock.VisualBlockManager(appCtx)
            userAgentManager = com.rebelroot.omni.browser.useragent.UserAgentManager(appCtx)
            copyManager = UniversalCopyManager(geckoRuntime!!)
            aiBlockerManager = BuiltInExtensionManager(
                runtime = geckoRuntime!!,
                assetPath = "web_extensions/ai_blocker/",
                extensionId = AI_BLOCKER_ID,
                label = "AI Blocker"
            )
            forceDarkManager = BuiltInExtensionManager(
                runtime = geckoRuntime!!,
                assetPath = "web_extensions/force_dark/",
                extensionId = FORCE_DARK_EXTENSION_ID,
                label = "Force Dark Theme"
            )
            
            // Sync user extensions on start
            syncUserExtensions()

            // Initialize multi-tabs
            initTabs(appCtx)
            loadDevNotes(appCtx)
            loadSavedPasswords(appCtx)

            viewModelScope.launch {
                isOpenExternalAppAllowed = getOpenExternalAppAllowedPreference(appCtx).first()
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
                proxyProvider = getProxyProvider(appCtx).first()
                isTorUseBridges = getTorUseBridgesPreference(appCtx).first()
                isTorAutoConnect = getTorAutoConnectPreference(appCtx).first()
                customSocksHost = getCustomSocksHost(appCtx).first()
                customSocksPort = getCustomSocksPort(appCtx).first()
                customDns = getCustomDns(appCtx).first()
                isDohEnabled = getDohEnabled(appCtx).first()
                dohUri = getDohUri(appCtx).first()
                isDotEnabled = getDotEnabled(appCtx).first()
                dotHost = getDotHost(appCtx).first()
                isBlockQuic = getBlockQuic(appCtx).first()
                isDisableWebrtc = getDisableWebrtc(appCtx).first()
                isRandomizeUa = getRandomizeUa(appCtx).first()
                isFingerprintProtection = getFingerprintProtection(appCtx).first()
                isClearCookiesOnShutdown = getClearCookiesOnShutdown(appCtx).first()
                isAutoRotateIdentity = getAutoRotateIdentity(appCtx).first()
                if ((proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "tor_builtin") && isTorAutoConnect) {
                    connectTor()
                }
                if (isAutoRotateIdentity) {
                    // "Rotate identity on new session" = start each launch with a
                    // clean cookie jar. We deliberately do NOT auto-open Orbot for
                    // a new circuit here (that would pop Orbot's UI on every
                    // launch); the circuit is rotated manually from the Hub.
                    clearCookiesOnly()
                }
            }

            viewModelScope.launch {
                // Observe Tor state transitions (Disconnected <-> Connecting <-> Connected)
                // and dynamically apply or clear live proxy preferences so browsing never breaks.
                combine(embeddedTorManager.state, torManager.state) { eState, tState -> eState to tState }
                    .collect {
                        applyProxyPrefsLive()
                    }
            }

            viewModelScope.launch {
                selectedSearchEngine = getSearchEnginePreference(appCtx).first()
                customSearchUrl = getCustomSearchUrlPreference(appCtx).first()
                customSuggestUrl = getCustomSuggestUrlPreference(appCtx).first()
                val enginesJson = getCustomSearchEnginesPreference(appCtx).first()
                customSearchEngines = parseCustomSearchEngines(enginesJson)
            }

            viewModelScope.launch {
                val darkThemePref = getDarkThemePreference(appCtx).first()
                isDarkThemeEnabled = darkThemePref
                ThemeStateHolder.darkThemeEnabled = darkThemePref
                updateGeckoColorScheme()
            }

            viewModelScope.launch {
                val accentPref = getAccentThemePreference(appCtx).first()
                selectedAccentTheme = accentPref
                ThemeStateHolder.accentTheme = accentPref
            }

            viewModelScope.launch {
                val amoledPref = getAmoledModePreference(appCtx).first()
                isAmoledMode = amoledPref
                ThemeStateHolder.amoledMode = amoledPref
            }

            viewModelScope.launch {
                isCreamyMode = getCreamyModePreference(appCtx).first()
            }

            viewModelScope.launch {
                val dynamicPref = getDynamicColorPreference(appCtx).first()
                isDynamicColorEnabled = dynamicPref
                ThemeStateHolder.dynamicColorEnabled = dynamicPref
            }

            try {
                val sp = appCtx.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                val savedLang = sp.getString("selected_language", null)
                if (!savedLang.isNullOrEmpty()) {
                    selectedLanguageCode = savedLang
                }
            } catch (_: Exception) {}

            viewModelScope.launch {
                val langFromDs = getLanguagePreference(appCtx).first()
                if (langFromDs.isNotEmpty()) {
                    selectedLanguageCode = langFromDs
                }
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
                cookieBehavior = prefs[COOKIE_BEHAVIOR_KEY] ?: 5
                doNotTrack = prefs[DO_NOT_TRACK_KEY] ?: true
                safeBrowsingLevel = prefs[SAFE_BROWSING_LEVEL_KEY] ?: 1
                preloadPages = prefs[PRELOAD_PAGES_KEY] ?: 1
                lockIncognito = prefs[LOCK_INCOGNITO_KEY] ?: false
                compromisedPasswordWarning = prefs[COMPROMISED_PASSWORD_WARNING_KEY] ?: true
                httpsOnlyMode = prefs[HTTPS_ONLY_MODE_KEY] ?: false
                isWebRenderEnabled = prefs[WEBRENDER_ALL_KEY] ?: true
                isGpuAccelerationEnabled = prefs[LAYERS_ACCELERATION_KEY] ?: true
                isForceHighRefreshRate = prefs[FORCE_HIGH_REFRESH_RATE_KEY] ?: true
                
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
                    updateGeckoColorScheme()
                    showScrollButtons = prefs[SHOW_SCROLL_BUTTONS_KEY] ?: true
                    navBarHideTop = prefs[NAV_BAR_HIDE_TOP_KEY] ?: true
                    navBarHideBottom = prefs[NAV_BAR_HIDE_BOTTOM_KEY] ?: true
                    hideRefreshIndicator = prefs[HIDE_REFRESH_INDICATOR_KEY] ?: false
                    addressBarPosition = prefs[ADDRESS_BAR_POSITION_KEY] ?: "Split"
                    appIconState = prefs[APP_ICON_STATE_KEY] ?: "Default"
                    customIconPath = prefs[CUSTOM_ICON_PATH_KEY]
                    browserWallpaperUri = prefs[BROWSER_WALLPAPER_URI_KEY]
                    changeWallpaperDaily = prefs[CHANGE_WALLPAPER_DAILY_KEY] ?: false
                    lastDailyWallpaperDate = prefs[LAST_DAILY_WALLPAPER_DATE_KEY]
                    dailyWallpaperSeed = prefs[DAILY_WALLPAPER_SEED_KEY] ?: 0
                    showDiscoverFeed = prefs[SHOW_DISCOVER_FEED_KEY] ?: false
                    showBottomNavBar = prefs[SHOW_BOTTOM_NAV_BAR_KEY] ?: true
                    hideHomeBottomNav = prefs[HIDE_HOME_BOTTOM_NAV_KEY] ?: false
                    chromeNavBarEnabled = prefs[CHROME_NAV_BAR_KEY] ?: false
                    showHomeLogo = prefs[SHOW_HOME_LOGO_KEY] ?: true
                    showHomeShortcuts = prefs[SHOW_HOME_SHORTCUTS_KEY] ?: true
                    wallpaperDim = prefs[WALLPAPER_DIM_KEY] ?: -1f
                    wallpaperBlur = prefs[WALLPAPER_BLUR_KEY] ?: 0f
                    wallpaperScale = prefs[WALLPAPER_SCALE_KEY] ?: 1.0f
                    wallpaperOffsetX = prefs[WALLPAPER_OFFSET_X_KEY] ?: 0f
                    wallpaperOffsetY = prefs[WALLPAPER_OFFSET_Y_KEY] ?: 0f
                    shortcutTileStyle = prefs[SHORTCUT_TILE_STYLE_KEY] ?: "Circle"
                    homeUiScale = prefs[HOME_UI_SCALE_KEY] ?: 0.90f
                    bottomNavScale = prefs[BOTTOM_NAV_SCALE_KEY] ?: 1.0f
                    showPrivacyStatsWidget = prefs[SHOW_PRIVACY_STATS_KEY] ?: true
                    isMinimalistFocusMode = prefs[MINIMALIST_FOCUS_MODE_KEY] ?: false
                    trackersBlockedCount = prefs[TRACKERS_BLOCKED_COUNT_KEY] ?: 0
                    quickToolsOrder = run {
                        val saved = prefs[QUICK_TOOLS_ORDER_KEY]
                        val default = DEFAULT_QUICK_TOOLS_ORDER
                        if (!saved.isNullOrBlank()) {
                            val savedList = saved.split(",").map { if (it == "ad_blocker") "vpn" else it }.filter { it.isNotBlank() && it != "ad_blocker" }.distinct()
                            savedList + default.filter { it !in savedList }
                        } else default
                    }
                }
            }

            // Check daily wallpaper rotation after prefs are fully loaded
            checkAndRotateDailyWallpaper(appCtx)

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
            // Issue #73: load the new media settings and push them to the interceptor.
            isMediaDetectionEnabled = context.dataStore.data.map { it[MEDIA_DETECTION_ENABLED_KEY] ?: true }.first()
            isMediaButtonEnabled = context.dataStore.data.map { it[MEDIA_BUTTON_ENABLED_KEY] ?: true }.first()
            isMediaAutoOpenEnabled = context.dataStore.data.map { it[MEDIA_AUTO_OPEN_KEY] ?: false }.first()
            isMediaValidateEnabled = context.dataStore.data.map { it[MEDIA_VALIDATE_ENABLED_KEY] ?: true }.first()
            syncMediaInterceptorSettings()
            val blocklist = getMediaSnifferBlocklistPreference(context).first()
            mediaSnifferBlocklist = blocklist
            mediaInterceptor.blockedDomains = blocklist
            val minDur = getMediaSnifferMinDurationSecPreference(context).first()
            mediaSnifferMinDurationSec = minDur
            mediaInterceptor.minDurationSeconds = minDur
            neverSavePasswordDomains = context.dataStore.data.map { it[NEVER_SAVE_PASSWORD_DOMAINS_KEY] ?: emptySet() }.first()
            installGrabberExtension(runtime)
            // On-device page translation bridge (content script <-> coordinator).
            installOmniTranslateExtension(runtime)

            // Always-on: routes traffic through the active Tor / SOCKS proxy via
            // the WebExtension `proxy` API. No user toggle.
            installProxyRouterExtension(runtime)

            isUniversalCopyEnabled = getUniversalCopyPreference(context).first()
            syncUniversalCopyState(shouldReload = false)
            
            isAiBlockerEnabled = getAiBlockerPreference(context).first()
            aiBlockerManager?.installAndSync(isAiBlockerEnabled, onComplete = null)

            forceDarkManager?.installAndSync(forceDarkWebsites, onComplete = null)

            isExternalDownloadManagerEnabled = getExternalDownloadManagerPreference(context).first()
            defaultDownloader = context.dataStore.data.map { it[DEFAULT_DOWNLOADER_KEY] ?: if (isExternalDownloadManagerEnabled) "external_chooser" else "internal" }.first()
            askBeforeDownload = context.dataStore.data.map { it[ASK_BEFORE_DOWNLOAD_KEY] ?: false }.first()
            downloadWifiOnly = context.dataStore.data.map { it[DOWNLOAD_WIFI_ONLY_KEY] ?: false }.first()
            maxConcurrentDownloads = context.dataStore.data.map { it[MAX_CONCURRENT_DOWNLOADS_KEY] ?: 3 }.first()
            downloadNotificationsEnabled = context.dataStore.data.map { it[DOWNLOAD_NOTIFICATIONS_ENABLED_KEY] ?: true }.first()
            downloadSoundEnabled = context.dataStore.data.map { it[DOWNLOAD_SOUND_ENABLED_KEY] ?: true }.first()
            downloadVibrateEnabled = context.dataStore.data.map { it[DOWNLOAD_VIBRATE_ENABLED_KEY] ?: false }.first()
            isExternalDownloadManagerEnabled = (defaultDownloader != "internal" && defaultDownloader != "system")
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
     * Installs the always-on proxy_router WebExtension, which routes HTTP(S)
     * traffic through the active Tor / SOCKS proxy via the WebExtension `proxy`
     * API (browser.proxy.onRequest). The `network.proxy.*` preferences written
     * into geckoview-config.yaml do NOT route on GeckoView/Android, so this
     * extension is the only reliable routing mechanism — mirroring WebLibre.
     *
     * It is always enabled (no user toggle) and registers the shared "omniApp"
     * native-messaging delegate so it can poll [currentProxyEndpoint].
     */
    private fun installProxyRouterExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/proxy_router/",
            PROXY_ROUTER_ID
        ).accept(
            { ext ->
                ext?.let {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                    runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    // Dedicated delegate on its OWN native-app name "omniProxy".
                    // media_grabber already owns "omniApp"; reusing it here breaks
                    // native-message routing in GeckoView (the poll for the proxy
                    // endpoint would never be answered -> traffic goes direct).
                    setupProxyRouterMessageDelegate(it)
                }
                Log.i(TAG, "Proxy Router extension active.")
            },
            { error ->
                Log.e(TAG, "Failed to load Proxy Router extension", error)
            }
        )
    }

    /**
     * Native-messaging delegate for the proxy_router extension only. It answers
     * the extension's `GET_PROXY_ENDPOINT` poll with the current SOCKS endpoint
     * (or a null host = direct). Registered under the native-app name
     * "omniProxy", which MUST match `NATIVE_APP` in the extension's background.js
     * and MUST NOT collide with media_grabber's "omniApp".
     */
    private fun setupProxyRouterMessageDelegate(extension: WebExtension) {
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                try {
                    val type = if (message is org.json.JSONObject) {
                        if (message.has("type")) message.getString("type") else null
                    } else {
                        (message as? Map<*, *>)?.get("type") as? String
                    }
                    if (type == "GET_PROXY_ENDPOINT") {
                        val ep = currentProxyEndpoint()
                        val response = org.json.JSONObject().apply {
                            if (ep != null) {
                                put("host", ep.first)
                                put("port", ep.second)
                            } else {
                                put("host", org.json.JSONObject.NULL)
                            }
                        }
                        Log.i(TAG, "ProxyRouter GET_PROXY_ENDPOINT -> ${if (ep != null) "${ep.first}:${ep.second}" else "DIRECT"} (provider=$proxyProvider)")
                        return GeckoResult.fromValue(response.toString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ProxyRouter onMessage error", e)
                }
                return null
            }
        }, "omniProxy")
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
                            if (level == "READER_TTS_CONTENT" || msg.startsWith("READER_TTS_CONTENT:")) {
                                speakText(msg.removePrefix("READER_TTS_CONTENT:"))
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



    fun toggleOpenExternalAppAllowed(context: Context) {
        viewModelScope.launch {
            val newState = !isOpenExternalAppAllowed
            isOpenExternalAppAllowed = newState
            context.dataStore.edit { preferences ->
                preferences[OPEN_EXTERNAL_APP_ALLOWED_KEY] = newState
            }
        }
    }

    fun updateOpenExternalAppAllowed(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            isOpenExternalAppAllowed = enabled
            context.dataStore.edit { preferences ->
                preferences[OPEN_EXTERNAL_APP_ALLOWED_KEY] = enabled
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



    private fun getOpenExternalAppAllowedPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[OPEN_EXTERNAL_APP_ALLOWED_KEY] ?: true  // Default ON
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

    private fun getExternalDownloadManagerPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[EXTERNAL_DOWNLOAD_MANAGER_KEY] ?: false // Default OFF
        }
    }

    private fun getYouTubePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[YOUTUBE_ENABLED_KEY] ?: false // Default OFF
        }
    }

    fun getCustomVpnConfig(context: Context): Flow<String?> {
        return context.dataStore.data.map { _ -> null }
    }

    fun saveCustomVpnConfig(context: Context, config: String) {
        // no-op — WireGuard removed
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

    fun getCustomSuggestUrlPreference(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_SUGGEST_URL_KEY] ?: ""
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
                val suggestUrl = obj.optString("suggestUrl", "")
                if (name.isNotEmpty() && queryUrl.isNotEmpty()) {
                    list.add(CustomSearchEngine(name, queryUrl, suggestUrl))
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
            if (engine.suggestUrl.isNotEmpty()) {
                obj.put("suggestUrl", engine.suggestUrl)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun addCustomSearchEngine(context: Context, name: String, queryUrl: String, suggestUrl: String = "") {
        val updatedList = customSearchEngines + CustomSearchEngine(name, queryUrl, suggestUrl)
        saveCustomSearchEnginesList(context, updatedList)
    }

    fun updateCustomSearchEngine(context: Context, oldEngine: CustomSearchEngine, newEngine: CustomSearchEngine) {
        val updatedList = customSearchEngines.map { if (it.name == oldEngine.name) newEngine else it }
        saveCustomSearchEnginesList(context, updatedList)
        if (selectedSearchEngine == oldEngine.name && oldEngine.name != newEngine.name) {
            saveSearchEngine(context, newEngine.name)
        }
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

    fun saveCustomSuggestUrl(context: Context, url: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_SUGGEST_URL_KEY] = url
            }
            customSuggestUrl = url
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
            // Keep ThemeStateHolder in sync so recreate() (e.g. on language change)
            // re-applies the current theme instead of the stale process-start value.
            ThemeStateHolder.darkThemeEnabled = enabled
            updateGeckoColorScheme()
            if (enabled || forceDarkWebsites) {
                injectForceDarkCssIfNeeded()
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
            ThemeStateHolder.amoledMode = enabled
            updateGeckoColorScheme()
            applySiteStyleToActiveTab()
        }
    }

    // Creamy Mode settings
    fun getCreamyModePreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[CREAMY_MODE_KEY] ?: false
        }
    }

    fun saveCreamyMode(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[CREAMY_MODE_KEY] = enabled }
            isCreamyMode = enabled
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
            isDynamicColorEnabled = enabled
            ThemeStateHolder.dynamicColorEnabled = enabled
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
            com.rebelroot.omni.UiStateHolder.uiScale = scale
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
            ThemeStateHolder.accentTheme = theme
        }
    }

    fun updateGeckoColorScheme() {
        val isDark = isDarkThemeEnabled || forceDarkWebsites || isAmoledMode || siteStyleTheme == "DARK" || siteStyleTheme == "OLED"
        geckoRuntime?.settings?.preferredColorScheme = if (isDark) {
            GeckoRuntimeSettings.COLOR_SCHEME_DARK
        } else {
            GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        }
        forceDarkManager?.setEnabled(forceDarkWebsites)
    }

    fun injectForceDarkCssIfNeeded(targetTab: TabState? = activeTab) {
        updateGeckoColorScheme()
        applySiteStyleToTab(targetTab)
    }

    fun saveForceDarkWebsites(context: Context, forceDark: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[FORCE_DARK_WEBSITES_KEY] = forceDark }
            forceDarkWebsites = forceDark
            updateGeckoColorScheme()
            activeTab?.session?.reload()
        }
    }

    fun saveShowScrollButtons(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_SCROLL_BUTTONS_KEY] = enabled }
            showScrollButtons = enabled
            val session = geckoSession
            if (session != null) {
                if (enabled) {
                    session.loadUri("javascript:(function(){try{var s=document.createElement('style');s.id='omni-hide-scrollbars';s.innerHTML='*::-webkit-scrollbar { display: none !important; } html, body { scrollbar-width: none !important; -ms-overflow-style: none !important; }';document.head.appendChild(s);}catch(e){}})();")
                } else {
                    session.loadUri("javascript:(function(){var s=document.getElementById('omni-hide-scrollbars');if(s)s.remove();})();")
                }
            }
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

    fun saveHideRefreshIndicator(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[HIDE_REFRESH_INDICATOR_KEY] = value }
            hideRefreshIndicator = value
        }
    }

    fun saveAddressBarPosition(context: Context, position: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[ADDRESS_BAR_POSITION_KEY] = position }
            addressBarPosition = position
            saveChromeNavBarEnabled(context, position != "Split")
        }
    }

    fun saveAppIconState(context: Context, state: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[APP_ICON_STATE_KEY] = state }
            appIconState = state

            val pm = context.packageManager
            val pkg = context.packageName
            val aliases = listOf(
                "Default"    to ".MainActivityDefault",
                "Light"      to ".MainActivityLight",
                "Dark"       to ".MainActivityDark",
                "Aura Dark"  to ".MainActivityAuraDark",
                "Aura Light" to ".MainActivityAuraLight"
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

    var isWallpaperDownloading by mutableStateOf(false)
        private set
    var downloadingWallpaperUrl by mutableStateOf<String?>(null)
        private set
    private var downloadJob: Job? = null

    fun downloadAndSetWallpaper(context: Context, url: String?, onResult: ((Boolean) -> Unit)? = null) {
        // Cancel any existing download to avoid race conditions
        downloadJob?.cancel()

        if (url.isNullOrEmpty() || url == "null") {
            saveBrowserWallpaperUri(context, null)
            onResult?.invoke(true)
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            saveBrowserWallpaperUri(context, url)
            onResult?.invoke(true)
            return
        }

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            // For HLS streams, save remote URL directly (can't download as single file)
            if (url.lowercase().contains(".m3u8")) {
                saveBrowserWallpaperUri(context, url)
                withContext(Dispatchers.Main) {
                    isWallpaperDownloading = false
                    downloadingWallpaperUrl = null
                }
                onResult?.invoke(true)
                return@launch
            }

            withContext(Dispatchers.Main) {
                isWallpaperDownloading = true
                downloadingWallpaperUrl = url
            }
            try {
                val dir = File(context.filesDir, "wallpapers")
                if (!dir.exists()) dir.mkdirs()

                val extension = when {
                    url.lowercase().contains(".mp4") -> ".mp4"
                    url.lowercase().contains(".webm") -> ".webm"
                    url.lowercase().contains(".m3u8") -> ".m3u8"
                    url.lowercase().contains(".gif") -> ".gif"
                    else -> ".jpg"
                }
                val hashName = java.security.MessageDigest.getInstance("MD5")
                    .digest(url.toByteArray())
                    .joinToString("") { "%02x".format(it) } + extension
                val localFile = File(dir, hashName)

                if (!localFile.exists() || localFile.length() == 0L) {
                    var currentUrl = url
                    var redirectCount = 0
                    val maxRedirects = 5
                    var connection: java.net.HttpURLConnection? = null
                    var inputStream: java.io.InputStream? = null

                    while (redirectCount < maxRedirects) {
                        val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        conn.connectTimeout = 20000
                        conn.readTimeout = 40000
                        conn.instanceFollowRedirects = true
                        conn.connect()

                        val status = conn.responseCode
                        if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                            status == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                            status == 307 || status == 308 || status == 303) {
                            val newUrl = conn.getHeaderField("Location")
                            conn.disconnect()
                            if (newUrl != null) {
                                currentUrl = newUrl
                                redirectCount++
                                continue
                            }
                        }

                        if (status == java.net.HttpURLConnection.HTTP_OK) {
                            connection = conn
                            inputStream = conn.inputStream
                            break
                        } else {
                            conn.disconnect()
                            throw java.io.IOException("HTTP error code: $status")
                        }
                    }

                    if (inputStream == null || connection == null) {
                        throw java.io.IOException("Failed to connect or redirect limit exceeded")
                    }

                    val output = java.io.FileOutputStream(localFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                    output.close()
                    inputStream.close()
                    connection.disconnect()
                }

                val localUriStr = "file://${localFile.absolutePath}"
                withContext(Dispatchers.Main) {
                    saveBrowserWallpaperUri(context, localUriStr)
                    isWallpaperDownloading = false
                    downloadingWallpaperUrl = null
                    Toast.makeText(context, "Wallpaper downloaded & applied!", Toast.LENGTH_SHORT).show()
                    onResult?.invoke(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading wallpaper: $url", e)
                withContext(Dispatchers.Main) {
                    saveBrowserWallpaperUri(context, url)
                    isWallpaperDownloading = false
                    downloadingWallpaperUrl = null
                    Toast.makeText(context, "Using online wallpaper", Toast.LENGTH_SHORT).show()
                    onResult?.invoke(false)
                }
            }
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
                    prefs.remove(WALLPAPER_DIM_KEY)
                    prefs.remove(WALLPAPER_BLUR_KEY)
                } else {
                    prefs[BROWSER_WALLPAPER_URI_KEY] = uri
                }
            }
            browserWallpaperUri = uri
            com.rebelroot.omni.UiStateHolder.browserWallpaperUri = uri
            if (uri == null) {
                wallpaperScale = 1.0f
                wallpaperOffsetX = 0f
                wallpaperOffsetY = 0f
                wallpaperDim = -1f
                wallpaperBlur = 0f
                com.rebelroot.omni.UiStateHolder.wallpaperScale = 1.0f
                com.rebelroot.omni.UiStateHolder.wallpaperOffsetX = 0f
                com.rebelroot.omni.UiStateHolder.wallpaperOffsetY = 0f
                com.rebelroot.omni.UiStateHolder.wallpaperDim = -1f
                com.rebelroot.omni.UiStateHolder.wallpaperBlur = 0f
            }
        }
    }

    /** Atomic save of all wallpaper settings — replaces 4 separate coroutine calls */
    fun saveAllWallpaperSettings(context: Context, uri: String?, scale: Float, offsetX: Float, offsetY: Float, dim: Float, blur: Float) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                if (uri == null) {
                    prefs.remove(BROWSER_WALLPAPER_URI_KEY)
                    prefs.remove(WALLPAPER_DIM_KEY)
                    prefs.remove(WALLPAPER_BLUR_KEY)
                } else {
                    prefs[BROWSER_WALLPAPER_URI_KEY] = uri
                    prefs[WALLPAPER_DIM_KEY] = dim
                    prefs[WALLPAPER_BLUR_KEY] = blur
                }
                prefs[WALLPAPER_SCALE_KEY] = scale
                prefs[WALLPAPER_OFFSET_X_KEY] = offsetX
                prefs[WALLPAPER_OFFSET_Y_KEY] = offsetY
            }
            browserWallpaperUri = uri
            wallpaperDim = dim
            wallpaperBlur = blur
            wallpaperScale = scale
            wallpaperOffsetX = offsetX
            wallpaperOffsetY = offsetY
            com.rebelroot.omni.UiStateHolder.browserWallpaperUri = uri
            com.rebelroot.omni.UiStateHolder.wallpaperDim = dim
            com.rebelroot.omni.UiStateHolder.wallpaperBlur = blur
            com.rebelroot.omni.UiStateHolder.wallpaperScale = scale
            com.rebelroot.omni.UiStateHolder.wallpaperOffsetX = offsetX
            com.rebelroot.omni.UiStateHolder.wallpaperOffsetY = offsetY
        }
    }

    /** Daily wallpaper rotation — picks a new wallpaper when the date changes */
    fun checkAndRotateDailyWallpaper(context: Context) {
        if (!changeWallpaperDaily) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (lastDailyWallpaperDate != today) {
            dailyWallpaperSeed = today.hashCode()
            val url = "https://picsum.photos/seed/omni_daily_$dailyWallpaperSeed/1600/2560"
            viewModelScope.launch {
                downloadAndSetWallpaper(context, url)
                // Save the new date after rotation completes
                context.dataStore.edit { prefs ->
                    prefs[LAST_DAILY_WALLPAPER_DATE_KEY] = today
                    prefs[DAILY_WALLPAPER_SEED_KEY] = dailyWallpaperSeed
                }
                lastDailyWallpaperDate = today
            }
        }
    }

    /** Delete all downloaded wallpaper files */
    fun clearDownloadedWallpapers(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "wallpapers")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
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
            com.rebelroot.omni.UiStateHolder.wallpaperDim = value
        }
    }

    fun saveWallpaperBlur(context: Context, value: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[WALLPAPER_BLUR_KEY] = value }
            wallpaperBlur = value
            com.rebelroot.omni.UiStateHolder.wallpaperBlur = value
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
            com.rebelroot.omni.UiStateHolder.wallpaperScale = scale
            com.rebelroot.omni.UiStateHolder.wallpaperOffsetX = offsetX
            com.rebelroot.omni.UiStateHolder.wallpaperOffsetY = offsetY
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

        fun saveHomeUiScale(context: Context, scale: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[HOME_UI_SCALE_KEY] = scale }
            homeUiScale = scale
            com.rebelroot.omni.UiStateHolder.homeUiScale = scale
        }
    }

    fun saveBottomNavScale(context: Context, scale: Float) {
        viewModelScope.launch {
            context.dataStore.edit { it[BOTTOM_NAV_SCALE_KEY] = scale }
            bottomNavScale = scale
            com.rebelroot.omni.UiStateHolder.bottomNavScale = scale
        }
    }

    fun saveShortcutTileStyle(context: Context, style: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHORTCUT_TILE_STYLE_KEY] = style }
            shortcutTileStyle = style
        }
    }

    fun saveShowPrivacyStatsWidget(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_PRIVACY_STATS_KEY] = show }
            showPrivacyStatsWidget = show
        }
    }

    fun saveIsMinimalistFocusMode(context: Context, enable: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[MINIMALIST_FOCUS_MODE_KEY] = enable }
            isMinimalistFocusMode = enable
        }
    }

    fun incrementTrackersBlocked(context: Context, count: Int = 1) {
        viewModelScope.launch {
            val newCount = trackersBlockedCount + count
            trackersBlockedCount = newCount
            context.dataStore.edit { it[TRACKERS_BLOCKED_COUNT_KEY] = newCount }
        }
    }

    fun saveShowBottomNavBar(context: Context, show: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[SHOW_BOTTOM_NAV_BAR_KEY] = show }
            showBottomNavBar = show
        }
    }

    fun saveHideHomeBottomNav(context: Context, hide: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[HIDE_HOME_BOTTOM_NAV_KEY] = hide }
            hideHomeBottomNav = hide
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
                sp.edit().putString("selected_language", langCode).commit()
            } catch (e: Exception) { /* ignore */ }
            try {
                val acceptLangs = if (langCode.startsWith("en", ignoreCase = true)) "en-US, en" else "$langCode, en-US, en"
                GeckoPreferenceController.setGeckoPref("intl.accept_languages", acceptLangs, GeckoPreferenceController.PREF_BRANCH_USER)
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                try {
                    val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
                } catch (_: Exception) {}
                selectedLanguageCode = langCode
                isLanguageSelectionDone = true
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

    /** Pushes all media-related settings into the interceptor. Safe to call repeatedly. */
    fun syncMediaInterceptorSettings() {
        mediaInterceptor.isMediaDetectionEnabled = isMediaDetectionEnabled
        mediaInterceptor.isMediaValidationEnabled = isMediaValidateEnabled
        mediaInterceptor.isMediaButtonEnabled = isMediaButtonEnabled
        mediaInterceptor.isYouTubeEnabled = isYouTubeEnabled
    }

    fun toggleMediaDetection(context: Context) {
        viewModelScope.launch {
            val newState = !isMediaDetectionEnabled
            isMediaDetectionEnabled = newState
            context.dataStore.edit { preferences -> preferences[MEDIA_DETECTION_ENABLED_KEY] = newState }
            syncMediaInterceptorSettings()
        }
    }

    fun toggleMediaButton(context: Context) {
        viewModelScope.launch {
            val newState = !isMediaButtonEnabled
            isMediaButtonEnabled = newState
            context.dataStore.edit { preferences -> preferences[MEDIA_BUTTON_ENABLED_KEY] = newState }
            syncMediaInterceptorSettings()
        }
    }

    fun toggleMediaAutoOpen(context: Context) {
        viewModelScope.launch {
            val newState = !isMediaAutoOpenEnabled
            isMediaAutoOpenEnabled = newState
            context.dataStore.edit { preferences -> preferences[MEDIA_AUTO_OPEN_KEY] = newState }
        }
    }

    fun toggleMediaValidate(context: Context) {
        viewModelScope.launch {
            val newState = !isMediaValidateEnabled
            isMediaValidateEnabled = newState
            context.dataStore.edit { preferences -> preferences[MEDIA_VALIDATE_ENABLED_KEY] = newState }
            syncMediaInterceptorSettings()
        }
    }

    /**
     * Issue #73: explicit, user-initiated launch of the native player for a selected
     * stream. Preserves the stream's request context (cookies/referer/origin) in memory
     * and forwards to the navigation callback. The site's own player is never touched
     * unless the user chooses to do this.
     */
    fun playMedia(request: MediaInterceptor.MediaPlaybackRequest) {
        activeVideoCookies = request.cookies
        val callback = onPlayVideoRequestReceived
        if (callback != null) {
            callback.invoke(request.url, request.referrer ?: "")
        } else {
            pendingVideoUrl = request.url
        }
    }

    fun toggleExternalDownloadManager(context: Context) {
        viewModelScope.launch {
            val newState = !isExternalDownloadManagerEnabled
            isExternalDownloadManagerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[EXTERNAL_DOWNLOAD_MANAGER_KEY] = newState
            }
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
                if (extension.id == FORCE_DARK_EXTENSION_ID) {
                    saveForceDarkWebsites(context, !currentlyEnabled)
                }
                viewModelScope.launch {
                    try {
                        context.dataStore.edit { preferences ->
                            val currentDisabled = preferences[EXTENSION_DISABLED_IDS_KEY]
                                ?.split(",")
                                ?.filter { it.isNotBlank() }
                                ?.toMutableSet() ?: mutableSetOf()
                            if (currentlyEnabled) {
                                currentDisabled.add(extension.id)
                            } else {
                                currentDisabled.remove(extension.id)
                            }
                            preferences[EXTENSION_DISABLED_IDS_KEY] = currentDisabled.joinToString(",")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update disabled extensions preference", e)
                    }
                    syncUserExtensions()
                }
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

    fun isUrlBlockedByMediaSniffer(url: String): Boolean {
        return mediaInterceptor.isDomainBlocked(url)
    }

    fun addDomainToMediaSnifferBlocklist(context: Context, domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
        if (clean.isEmpty()) return
        val updated = mediaSnifferBlocklist + clean
        mediaSnifferBlocklist = updated
        mediaInterceptor.blockedDomains = updated
        viewModelScope.launch {
            try {
                context.dataStore.edit { it[MEDIA_SNIFFER_BLOCKLIST_KEY] = updated }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving media sniffer blocklist", e)
            }
        }
    }

    fun removeDomainFromMediaSnifferBlocklist(context: Context, domain: String) {
        val updated = mediaSnifferBlocklist - domain
        mediaSnifferBlocklist = updated
        mediaInterceptor.blockedDomains = updated
        viewModelScope.launch {
            try {
                context.dataStore.edit { it[MEDIA_SNIFFER_BLOCKLIST_KEY] = updated }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving media sniffer blocklist", e)
            }
        }
    }

    fun setMediaSnifferMinDurationSec(context: Context, durationSec: Int) {
        val validDur = durationSec.coerceAtLeast(0)
        mediaSnifferMinDurationSec = validDur
        mediaInterceptor.minDurationSeconds = validDur
        viewModelScope.launch {
            try {
                context.dataStore.edit { it[MEDIA_SNIFFER_MIN_DURATION_SEC_KEY] = validDur }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving media sniffer min duration", e)
            }
        }
    }

    fun connectVpn(context: Context, serverIp: String, clientKey: String, serverKey: String) {
        // no-op — WireGuard removed
    }

    fun connectCustomVpn() {
        // no-op — WireGuard removed
    }

    fun disconnectVpn() {
        // no-op — WireGuard removed
    }

    // Tor proxy methods
    fun getProxyProvider(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PROXY_PROVIDER_KEY] ?: "direct"
        }
    }

    fun saveProxyProvider(context: Context, provider: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PROXY_PROVIDER_KEY] = provider
            }
            proxyProvider = provider
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getTorUseBridgesPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[TOR_USE_BRIDGES_KEY] ?: false
        }
    }

    fun saveTorUseBridges(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[TOR_USE_BRIDGES_KEY] = enabled
            }
            isTorUseBridges = enabled
            // Bridges change the SOCKS port (9050 → 9052). With live pref
            // writes the running engine picks this up immediately — no restart.
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getTorAutoConnectPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[TOR_AUTO_CONNECT_KEY] ?: false
        }
    }

    fun saveTorAutoConnect(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[TOR_AUTO_CONNECT_KEY] = enabled
            }
            isTorAutoConnect = enabled
        }
    }

    fun getCustomSocksHost(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_SOCKS_HOST_KEY] ?: ""
        }
    }

    fun saveCustomSocksHost(context: Context, host: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_SOCKS_HOST_KEY] = host
            }
            customSocksHost = host
            if (host.isNotBlank()) {
                torManager.setCustomProxy(host, customSocksPort)
            } else {
                torManager.clearCustomProxy()
            }
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getCustomSocksPort(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_SOCKS_PORT_KEY] ?: 9050
        }
    }

    fun saveCustomSocksPort(context: Context, port: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_SOCKS_PORT_KEY] = port
            }
            customSocksPort = port
            if (customSocksHost.isNotBlank()) {
                torManager.setCustomProxy(customSocksHost, port)
            }
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getCustomDns(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_DNS_KEY] ?: ""
        }
    }

    fun saveCustomDns(context: Context, dns: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CUSTOM_DNS_KEY] = dns
                // Mutual exclusion: a plain custom DNS supersedes in-app DoH/DoT.
                if (dns.isNotBlank()) {
                    preferences[DOH_ENABLED_KEY] = false
                    preferences[DOT_ENABLED_KEY] = false
                }
            }
            customDns = dns
            if (dns.isNotBlank()) {
                isDohEnabled = false
                isDotEnabled = false
            }
            regenerateGeckoConfig()
        }
    }

    fun getDohEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DOH_ENABLED_KEY] ?: false
        }
    }

    fun saveDohEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DOH_ENABLED_KEY] = enabled
                // DoH is enforced in-engine, so it supersedes DoT / plain DNS.
                if (enabled) {
                    preferences[DOT_ENABLED_KEY] = false
                    preferences[CUSTOM_DNS_KEY] = ""
                }
            }
            isDohEnabled = enabled
            if (enabled) {
                isDotEnabled = false
                customDns = ""
            }
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getDohUri(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[DOH_URI_KEY] ?: "https://dns.google/dns-query"
        }
    }

    fun saveDohUri(context: Context, uri: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DOH_URI_KEY] = uri
            }
            dohUri = uri
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getDotEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DOT_ENABLED_KEY] ?: false
        }
    }

    fun saveDotEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DOT_ENABLED_KEY] = enabled
                // DoT cannot be enforced in-engine; it must not coexist with in-app DoH.
                if (enabled) {
                    preferences[DOH_ENABLED_KEY] = false
                }
            }
            isDotEnabled = enabled
            if (enabled) {
                isDohEnabled = false
            }
            regenerateGeckoConfig()
        }
    }

    fun getDotHost(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[DOT_HOST_KEY] ?: ""
        }
    }

    fun saveDotHost(context: Context, host: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DOT_HOST_KEY] = host
            }
            dotHost = host
            regenerateGeckoConfig()
        }
    }

    fun getBlockQuic(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[BLOCK_QUIC_KEY] ?: true
        }
    }

    fun saveBlockQuic(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[BLOCK_QUIC_KEY] = enabled
            }
            isBlockQuic = enabled
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getDisableWebrtc(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DISABLE_WEBRTC_KEY] ?: false
        }
    }

    fun saveDisableWebrtc(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DISABLE_WEBRTC_KEY] = enabled
            }
            isDisableWebrtc = enabled
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getRandomizeUa(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[RANDOMIZE_UA_KEY] ?: false
        }
    }

    fun saveRandomizeUa(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[RANDOMIZE_UA_KEY] = enabled
            }
            isRandomizeUa = enabled
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getFingerprintProtection(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[FINGERPRINT_PROTECTION_KEY] ?: false
        }
    }

    fun saveFingerprintProtection(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FINGERPRINT_PROTECTION_KEY] = enabled
            }
            isFingerprintProtection = enabled
            regenerateGeckoConfig()
            applyProxyPrefsLive()
        }
    }

    fun getClearCookiesOnShutdown(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[CLEAR_COOKIES_ON_SHUTDOWN_KEY] ?: false
        }
    }

    fun saveClearCookiesOnShutdown(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CLEAR_COOKIES_ON_SHUTDOWN_KEY] = enabled
            }
            isClearCookiesOnShutdown = enabled
        }
    }

    fun getAutoRotateIdentity(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_ROTATE_IDENTITY_KEY] ?: false
        }
    }

    fun saveAutoRotateIdentity(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[AUTO_ROTATE_IDENTITY_KEY] = enabled
            }
            isAutoRotateIdentity = enabled
        }
    }

    fun rotateIdentity() {
        try {
            val rt = geckoRuntime
            rt?.let {
                it.storageController.clearData(StorageController.ClearFlags.ALL).accept(
                    { Log.d(TAG, "Identity rotation: data cleared") },
                    { err -> Log.e(TAG, "Identity rotation clear error", err) }
                )
            }
            if (proxyProvider == "tor_builtin") {
                embeddedTorManager.requestNewCircuit()
            } else {
                torManager.requestNewCircuit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Identity rotation failed", e)
        }
    }

    fun clearCookiesOnly() {
        try {
            val rt = geckoRuntime
            rt?.let {
                it.storageController.clearData(StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES).accept(
                    { Log.d(TAG, "Cookies cleared") },
                    { err -> Log.e(TAG, "Cookie clear error", err) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clear cookies failed", e)
        }
    }

    /**
     * Ask the local Tor (Orbot) to build a fresh circuit WITHOUT wiping any
     * browser data. Opening Orbot's UI is unavoidable because Orbot exposes no
     * silent NEWNYM API without the Tor control port. For a remote/custom SOCKS
     * proxy there is no control channel at all, so the UI disables the control
     * in that case (see [com.rebelroot.omni.settings.PrivacyHubScreen]).
     */
    fun requestNewCircuit() {
        try {
            if (proxyProvider == "tor_builtin") {
                embeddedTorManager.requestNewCircuit()
            } else {
                torManager.requestNewCircuit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request new circuit failed", e)
        }
    }

    /**
     * Restarts the app process so that privacy settings which are only read at
     * [GeckoRuntime] creation (proxy, DoH, QUIC, WebRTC, fingerprinting, UA)
     * take effect. The config file is rewritten on every change, but the
     * running engine never re-reads it, so a process restart is the only way to
     * apply them in the current session.
     */
    fun restartApp(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                regenerateGeckoConfig()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush gecko config before restart", e)
            }
            privacyRestartNeeded = false

            try {
                val packageManager = context.packageManager
                val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null && launchIntent.component != null) {
                    val restartIntent = android.content.Intent.makeRestartActivityTask(launchIntent.component)
                    context.startActivity(restartIntent)
                    if (context is android.app.Activity) {
                        context.finishAffinity()
                    }
                    kotlinx.coroutines.delay(150)
                    Runtime.getRuntime().exit(0)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "makeRestartActivityTask failed, fallback to recreate", e)
            }

            if (context is android.app.Activity) {
                context.recreate()
            }
        }
    }

    /**
     * Returns the [StateFlow] for whichever Tor backend is currently active
     * (Orbot for `"tor"`/`"tor_over_vpn"`, or the built-in daemon for
     * `"tor_builtin"`).
     */
    fun activeTorState(): StateFlow<TorState> {
        return if (proxyProvider == "tor_builtin") embeddedTorManager.state else torManager.state
    }

    // Providers for which traffic must be routed through a SOCKS endpoint.
    // "direct" is the only value NOT in this set.
    private val proxyProviders = setOf("tor", "tor_over_vpn", "custom_proxy", "tor_builtin")

    /**
     * The SOCKS endpoint the bundled proxy_router extension should route through
     * for the current provider, or `null` when traffic must go direct. This is the
     * single source of truth — it replaces the three duplicated port/host blocks
     * that used to write dead `network.proxy.*` prefs into geckoview-config.yaml.
     *
     * Routing is applied LIVE by the extension via the WebExtension `proxy` API,
     * so toggling the provider / connecting Tor / editing a custom proxy takes
     * effect on the next request with no process restart.
     */
    private fun currentProxyEndpoint(): Pair<String, Int>? {
        if (proxyProvider !in proxyProviders) return null
        // A custom proxy with no host configured cannot route anywhere.
        if (proxyProvider == "custom_proxy") {
            if (customSocksHost.isBlank()) return null
            return customSocksHost to customSocksPort
        }
        // For Tor providers ("tor", "tor_builtin", "tor_over_vpn"), only route
        // through SOCKS when the Tor daemon is actually CONNECTED. If Tor is OFF,
        // disconnected, or still bootstrapping, fall back to null (direct connection)
        // so browsing never hangs or errors against a closed local port.
        val state = activeTorState().value
        if (state !is TorState.Connected) return null

        val torPort = when {
            proxyProvider == "tor_builtin" -> EmbeddedTorManager.EMBEDDED_SOCKS_PORT
            isTorUseBridges -> TorManager.BRIDGE_SOCKS_PORT
            else -> TorManager.DEFAULT_SOCKS_PORT
        }
        val host = customSocksHost.ifBlank { "127.0.0.1" }
        val port = if (customSocksHost.isNotBlank()) customSocksPort else torPort
        return host to port
    }

    /**
     * Writes the current SOCKS proxy configuration onto the RUNNING Gecko engine
     * via GeckoPreferenceController.setGeckoPref (the only public way to set
     * arbitrary prefs on a live stock-GeckoView runtime; no restart, no config-
     * file reread). This is what actually makes Tor routing take effect when the
     * user enables it mid-session.
     *
     * Why this exists: network.proxy.* written to geckoview-config.yaml are read
     * ONCE at GeckoRuntime.create(), and the cached runtime never re-reads them.
     * So enabling Tor in-session (the common case) left the engine stuck on
     * type:0 = direct = real-IP leak. This live write closes that hole.
     *
     * Fail-closed: for a proxy-active provider we ALWAYS set type:1 with
     * failover_direct:false, so an unreachable SOCKS port errors the request
     * instead of leaking direct. For "direct" we set type:0 and clear the socks
     * user-prefs so no stale proxy lingers.
     *
     * After applying, getGeckoPref reads the values back and logs them so routing
     * can be proven (VERIFY network.proxy...). Call from every state-change site.
     */
    private fun applyProxyPrefsLive() {
        // Controller API is static, but prefs cannot be applied before the runtime exists.
        if (geckoRuntime == null) {
            Log.d(TAG, "applyProxyPrefsLive: geckoRuntime not yet created, skipping (provider=$proxyProvider)")
            return
        }
        val ep = currentProxyEndpoint()
        val branch = GeckoPreferenceController.PREF_BRANCH_USER
        if (ep != null) {
            val (host, port) = ep
            Log.i(TAG, "applyProxyPrefsLive: -> PROXY ${host}:${port} (provider=$proxyProvider)")
            // Order matters: set host/port BEFORE flipping type to 1, so there is
            // never a window where type=1 points at a stale/empty SOCKS host.
            GeckoPreferenceController.setGeckoPref("network.proxy.socks", host, branch)
                .accept({ Log.d(TAG, "  set network.proxy.socks = $host") }, { e -> Log.e(TAG, "  FAILED network.proxy.socks", e) })
            GeckoPreferenceController.setGeckoPref("network.proxy.socks_port", port as Int, branch)
                .accept({ Log.d(TAG, "  set network.proxy.socks_port = $port") }, { e -> Log.e(TAG, "  FAILED network.proxy.socks_port", e) })
            GeckoPreferenceController.setGeckoPref("network.proxy.socks_remote_dns", true, branch)
                .accept({ Log.d(TAG, "  set network.proxy.socks_remote_dns = true") }, { e -> Log.e(TAG, "  FAILED network.proxy.socks_remote_dns", e) })
            GeckoPreferenceController.setGeckoPref("network.proxy.failover_direct", false, branch)
                .accept({ Log.d(TAG, "  set network.proxy.failover_direct = false") }, { e -> Log.e(TAG, "  FAILED network.proxy.failover_direct", e) })
            GeckoPreferenceController.setGeckoPref("network.proxy.type", 1, branch)
                .accept({ Log.d(TAG, "  set network.proxy.type = 1") }, { e -> Log.e(TAG, "  FAILED network.proxy.type", e) })
        } else {
            Log.i(TAG, "applyProxyPrefsLive: -> SYSTEM (provider=$proxyProvider)")
            // Clear stale user values first, then drop type to 5 (system proxy) last.
            GeckoPreferenceController.clearGeckoUserPref("network.proxy.socks")
            GeckoPreferenceController.clearGeckoUserPref("network.proxy.socks_port")
            GeckoPreferenceController.setGeckoPref("network.proxy.type", 5, branch)
                .accept({ Log.d(TAG, "  set network.proxy.type = 5 (system)") }, { e -> Log.e(TAG, "  FAILED network.proxy.type=5", e) })
        }

        // ── Leak prevention: disable UDP-based protocols that bypass SOCKS ──
        // WebRTC (STUN/TURN) and QUIC/HTTP3 both use UDP, which SOCKS5 cannot
        // proxy. If left enabled while a proxy is active, they reveal the real
        // IP. Disable them live for ALL proxy types, not just Tor.
        val isProxyActive = ep != null
        val isTorSession = proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "tor_builtin"

        val webrtcDisabled = isProxyActive || isDisableWebrtc
        GeckoPreferenceController.setGeckoPref("media.peerconnection.enabled", !webrtcDisabled, branch)
            .accept({ Log.d(TAG, "  set media.peerconnection.enabled = ${!webrtcDisabled}") }, { e -> Log.e(TAG, "  FAILED media.peerconnection.enabled", e) })
        GeckoPreferenceController.setGeckoPref("media.peerconnection.ice.no_host", webrtcDisabled, branch)
            .accept({ Log.d(TAG, "  set media.peerconnection.ice.no_host = $webrtcDisabled") }, { e -> Log.e(TAG, "  FAILED media.peerconnection.ice.no_host", e) })

        val quicDisabled = isProxyActive || isBlockQuic
        GeckoPreferenceController.setGeckoPref("network.quic.enabled", !quicDisabled, branch)
            .accept({ Log.d(TAG, "  set network.quic.enabled = ${!quicDisabled}") }, { e -> Log.e(TAG, "  FAILED network.quic.enabled", e) })
        GeckoPreferenceController.setGeckoPref("network.http.http3.enabled", !quicDisabled, branch)
            .accept({ Log.d(TAG, "  set network.http.http3.enabled = ${!quicDisabled}") }, { e -> Log.e(TAG, "  FAILED network.http.http3.enabled", e) })

        // DoH: when proxied, DNS must go through the proxy (socks_remote_dns),
        // not to a DoH provider — otherwise DNS queries leak browsing destinations.
        if (isProxyActive) {
            GeckoPreferenceController.setGeckoPref("network.trr.mode", 0, branch)
                .accept({ Log.d(TAG, "  set network.trr.mode = 0 (DoH off, proxied)") }, { e -> Log.e(TAG, "  FAILED network.trr.mode", e) })
        } else if (isDohEnabled && dohUri.isNotBlank()) {
            GeckoPreferenceController.setGeckoPref("network.trr.uri", dohUri, branch)
                .accept({ Log.d(TAG, "  set network.trr.uri = $dohUri") }, { e -> Log.e(TAG, "  FAILED network.trr.uri", e) })
            GeckoPreferenceController.setGeckoPref("network.trr.mode", 2, branch)
                .accept({ Log.d(TAG, "  set network.trr.mode = 2 (TRR-first)") }, { e -> Log.e(TAG, "  FAILED network.trr.mode=2", e) })
        } else {
            GeckoPreferenceController.setGeckoPref("network.trr.mode", 0, branch)
                .accept({ Log.d(TAG, "  set network.trr.mode = 0 (DoH off)") }, { e -> Log.e(TAG, "  FAILED network.trr.mode=0", e) })
        }

        // Tor-only hardening (matches Tor Browser behaviour).
        if (isTorSession) {
            GeckoPreferenceController.setGeckoPref("privacy.resistFingerprinting", true, branch)
                .accept({ Log.d(TAG, "  set privacy.resistFingerprinting = true") }, { e -> Log.e(TAG, "  FAILED privacy.resistFingerprinting", e) })
            GeckoPreferenceController.setGeckoPref("privacy.firstparty.isolate", true, branch)
                .accept({ Log.d(TAG, "  set privacy.firstparty.isolate = true") }, { e -> Log.e(TAG, "  FAILED privacy.firstparty.isolate", e) })
        } else {
            GeckoPreferenceController.setGeckoPref("privacy.resistFingerprinting", false, branch)
                .accept({ Log.d(TAG, "  set privacy.resistFingerprinting = false") }, { e -> Log.e(TAG, "  FAILED privacy.resistFingerprinting=false", e) })
            GeckoPreferenceController.setGeckoPref("privacy.firstparty.isolate", false, branch)
                .accept({ Log.d(TAG, "  set privacy.firstparty.isolate = false") }, { e -> Log.e(TAG, "  FAILED privacy.firstparty.isolate=false", e) })
        }

        // PROOF: read back what necko actually received. This turns "does it work?"
        // from a guess into a log fact. If these show type=1/socks=127.0.0.1/9150
        // yet ipify still returns the real IP, we're in the documented F2 case
        // (necko ignores user-branch network.proxy.*); otherwise routing is armed.
        GeckoPreferenceController.getGeckoPref("network.proxy.type").accept(
            { typePref ->
                val typeVal = typePref?.value
                GeckoPreferenceController.getGeckoPref("network.proxy.socks").accept(
                    { hostPref ->
                        val hostVal = hostPref?.value
                        GeckoPreferenceController.getGeckoPref("network.proxy.socks_port").accept(
                            { portPref ->
                                val portVal = portPref?.value
                                GeckoPreferenceController.getGeckoPref("media.peerconnection.enabled").accept(
                                    { webrtcPref ->
                                        val webrtcVal = webrtcPref?.value
                                        GeckoPreferenceController.getGeckoPref("network.quic.enabled").accept(
                                            { quicPref ->
                                                val quicVal = quicPref?.value
                                                Log.i(TAG, "VERIFY proxy.type=$typeVal socks=$hostVal socks_port=$portVal webrtc=$webrtcVal quic=$quicVal (provider=$proxyProvider)")
                                            },
                                            { Log.e(TAG, "VERIFY: could not read network.quic.enabled") }
                                        )
                                    },
                                    { Log.e(TAG, "VERIFY: could not read media.peerconnection.enabled") }
                                )
                            },
                            { Log.e(TAG, "VERIFY: could not read network.proxy.socks_port") }
                        )
                    },
                    { Log.e(TAG, "VERIFY: could not read network.proxy.socks") }
                )
            },
            { Log.e(TAG, "VERIFY: could not read network.proxy.type") }
        )
    }

    fun connectTor() {
        if (proxyProvider == "tor_builtin") {
            embeddedTorManager.startTor()
        } else {
            if (customSocksHost.isNotBlank()) {
                torManager.setCustomProxy(customSocksHost, customSocksPort)
                torManager.startTor(customSocksPort)
            } else {
                val port = if (isTorUseBridges) TorManager.BRIDGE_SOCKS_PORT else TorManager.DEFAULT_SOCKS_PORT
                torManager.startTor(port)
            }
        }
        // Point the running engine at the SOCKS proxy now (no restart), AND keep
        // the on-disk config file correct for the next launch.
        applyProxyPrefsLive()
        regenerateGeckoConfig()
    }

    fun disconnectTor() {
        if (proxyProvider == "tor_builtin") {
            embeddedTorManager.stopTor()
        } else {
            torManager.stopTor()
        }
        // The provider is still a Tor/custom type; leave routing armed so the
        // next connect picks it up, but note the daemon is down so requests will
        // fail closed until it reconnects. Refresh both live + on-disk state.
        applyProxyPrefsLive()
        regenerateGeckoConfig()
    }

    /**
     * Regenerates the full geckoview-config.yaml from current state variables.
     * Called after any privacy setting change so the next app launch picks up
     * the new values. Also sets [privacyRestartNeeded] so the UI can prompt
     * the user to restart for changes to take effect in the current session.
     */
    fun regenerateGeckoConfig() {
        val ctx = appContext ?: return
        val configFile = File(ctx.filesDir, "geckoview-config.yaml")
        try {
            val sb = StringBuilder()
            sb.append("pref:\n")
            sb.append("  fission.autostart: true\n")
            sb.append("  fission.web_content_process_count: 8\n")
            sb.append("  dom.ipc.processCount: 8\n")
            sb.append("  dom.ipc.processCount.webIsolated: 1\n")
            sb.append("  gfx.webrender.all: ${isWebRenderEnabled}\n")
            sb.append("  layers.acceleration.force-enabled: ${isGpuAccelerationEnabled}\n")
            if (isForceHighRefreshRate) {
                sb.append("  layout.frame_rate: 120\n")
            }
            sb.append("  privacy.donottrackheader.enabled: ${doNotTrack}\n")
            sb.append("  dom.security.https_only_mode: ${safeBrowsingLevel == 2}\n")
            sb.append("  dom.security.https_first: true\n")
            sb.append("  security.fileuri.strict_origin_policy: true\n")
            sb.append("  privacy.partition.network_state: true\n")
            sb.append("  network.cookie.cookieBehavior: 5\n")
            if (preloadPages == 0) {
                sb.append("  network.dns.disablePrefetch: true\n")
                sb.append("  network.prefetch-next: false\n")
            } else {
                sb.append("  network.dns.disablePrefetch: false\n")
                sb.append("  network.prefetch-next: true\n")
            }
            // Proxy routing via network.proxy.* prefs (see getGeckoRuntime for
            // the full explanation of why the WebExtension proxy API is not used
            // on stock GeckoView).
            if (proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "custom_proxy" || proxyProvider == "tor_builtin") {
                val torPort = when {
                    proxyProvider == "tor_builtin" -> EmbeddedTorManager.EMBEDDED_SOCKS_PORT
                    isTorUseBridges -> TorManager.BRIDGE_SOCKS_PORT
                    else -> TorManager.DEFAULT_SOCKS_PORT
                }
                val targetHost = customSocksHost.ifBlank { "127.0.0.1" }
                val targetPort = if (customSocksHost.isNotBlank()) customSocksPort else torPort
                sb.append("  network.proxy.type: 1\n")
                sb.append("  network.proxy.socks: $targetHost\n")
                sb.append("  network.proxy.socks_port: $targetPort\n")
                sb.append("  network.proxy.socks_remote_dns: true\n")
                sb.append("  network.proxy.failover_direct: false\n")
            } else {
                sb.append("  network.proxy.type: 5\n")
            }
            // DoH - Disabled when using Tor or Custom SOCKS proxy to prevent DNS leak bypassing the proxy resolver.
            val isProxyActive = proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "custom_proxy" || proxyProvider == "tor_builtin"
            if (isDohEnabled && dohUri.isNotBlank() && !isProxyActive) {
                sb.append("  network.trr.uri: $dohUri\n")
                // mode 2 = TRR-first with native-DNS fallback (see the inline
                // writer in getGeckoRuntime for why we avoid mode 3 here).
                sb.append("  network.trr.mode: 2\n")
            } else {
                sb.append("  network.trr.mode: 0\n")
            }
            val isTorSession = proxyProvider == "tor" || proxyProvider == "tor_over_vpn" || proxyProvider == "tor_builtin"
            val isAnyProxy = isTorSession || proxyProvider == "custom_proxy"
            if (isAnyProxy) {
                // WebRTC (STUN/TURN) and QUIC/HTTP3 use UDP, which SOCKS5 cannot
                // proxy. Disable them for ALL proxy types to prevent real-IP leaks.
                sb.append("  media.peerconnection.enabled: false\n")
                sb.append("  media.peerconnection.ice.no_host: true\n")
                sb.append("  network.quic.enabled: false\n")
                sb.append("  network.http.http3.enabled: false\n")
            } else {
                sb.append("  media.peerconnection.enabled: ${!isDisableWebrtc}\n")
                sb.append("  network.quic.enabled: ${!isBlockQuic}\n")
                sb.append("  network.http.http3.enabled: ${!isBlockQuic}\n")
            }
            if (isTorSession) {
                sb.append("  privacy.resistFingerprinting: true\n")
                sb.append("  privacy.firstparty.isolate: true\n")
            }
            // UA randomization
            if (isRandomizeUa) {
                val uas = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
                )
                // Use a random index seeded by hour-of-day for more variation
                val idx = (System.currentTimeMillis() / 3600000L).toInt() % uas.size
                sb.append("  general.useragent.override: ${uas[idx]}\n")
            }
            // Clear on shutdown
            sb.append("  privacy.clearOnShutdown.cache: ${isClearCookiesOnShutdown}\n")
            sb.append("  privacy.clearOnShutdown.cookies: ${isClearCookiesOnShutdown}\n")
            // Disable automatic GeckoView handoff of HTTP/HTTPS URLs to external Android apps
            sb.append("  network.protocol-handler.external.http: false\n")
            sb.append("  network.protocol-handler.external.https: false\n")
            sb.append("  network.protocol-handler.external-default: false\n")
            sb.append("  geckoview.external_app_handler.enabled: false\n")
            sb.append("  geckoview.intent_dispatched_in_app: false\n")
            // Fingerprint protection
            if (isFingerprintProtection) {
                sb.append("  webgl.disabled: true\n")
                sb.append("  dom.enable_resource_timing: false\n")
                sb.append("  dom.enable_user_timing: false\n")
                sb.append("  beacon.enabled: false\n")
                sb.append("  dom.battery.enabled: false\n")
                sb.append("  canvas.captureStream.enabled: false\n")
                sb.append("  dom.webaudio.enabled: false\n")
            }
            val tmpFile = File(ctx.filesDir, "geckoview-config.tmp")
            tmpFile.writeText(sb.toString())
            if (tmpFile.exists() && tmpFile.length() > 0) {
                tmpFile.renameTo(configFile)
            }
            privacyRestartNeeded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to regenerate geckoview-config.yaml", e)
        }
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
                    "Custom" -> {
                        val customSuggest = customSuggestUrl.trim()
                        if (customSuggest.isNotEmpty()) {
                            if (customSuggest.contains("%s")) customSuggest.replace("%s", encodedQuery)
                            else customSuggest + encodedQuery
                        } else {
                            "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                        }
                    }
                    else -> {
                        val match = customSearchEngines.find { it.name == selectedSearchEngine }
                        if (match != null && match.suggestUrl.isNotBlank()) {
                            val customSuggest = match.suggestUrl.trim()
                            if (customSuggest.contains("%s")) customSuggest.replace("%s", encodedQuery)
                            else customSuggest + encodedQuery
                        } else {
                            "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                        }
                    }
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
                    val trimmedText = text.trim()
                    
                    if (trimmedText.startsWith("[")) {
                        val arr = org.json.JSONArray(trimmedText)
                        if (arr.length() > 0) {
                            val firstItem = arr.opt(0)
                            if (firstItem is org.json.JSONObject && firstItem.has("phrase")) {
                                for (i in 0 until arr.length()) {
                                    val obj = arr.optJSONObject(i)
                                    if (obj != null && obj.has("phrase")) {
                                        list.add(obj.getString("phrase"))
                                    }
                                }
                            } else if (arr.length() > 1 && arr.opt(1) is org.json.JSONArray) {
                                val suggestionsArr = arr.getJSONArray(1)
                                for (i in 0 until suggestionsArr.length()) {
                                    list.add(suggestionsArr.getString(i))
                                }
                            } else if (firstItem is String) {
                                for (i in 0 until arr.length()) {
                                    val str = arr.optString(i)
                                    if (str.isNotEmpty()) list.add(str)
                                }
                            }
                        }
                    } else if (trimmedText.startsWith("{")) {
                        val obj = org.json.JSONObject(trimmedText)
                        val arr = obj.optJSONArray("suggestions") ?: obj.optJSONArray("results") ?: obj.optJSONArray("items")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.opt(i)
                                if (item is String) {
                                    list.add(item)
                                } else if (item is org.json.JSONObject) {
                                    val phrase = item.optString("phrase", item.optString("title", item.optString("name", "")))
                                    if (phrase.isNotEmpty()) list.add(phrase)
                                }
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
        val lang = selectedLanguageCode.ifBlank { "en" }
        val googleLangParam = if (lang != "en") "&hl=$lang" else ""
        val bingLangParam = if (lang != "en") "&setlang=$lang" else ""
        val ddgLangParam = if (lang != "en") "&kl=${lang}-${lang}" else ""

        return when (selectedSearchEngine) {
            "Google" -> {
                val base = "https://www.google.com/search?q=$encodedQuery$googleLangParam"
                if (isAiBlockerEnabled) "$base&udm=14" else base
            }
            "Yahoo" -> "https://search.yahoo.com/search?p=$encodedQuery"
            "Yandex" -> "https://yandex.com/search/?text=$encodedQuery"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encodedQuery$ddgLangParam"
            "Brave" -> "https://search.brave.com/search?q=$encodedQuery"
            "Bing" -> "https://www.bing.com/search?q=$encodedQuery$bingLangParam"
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
                    val base = "https://www.google.com/search?q=$encodedQuery$googleLangParam"
                    if (isAiBlockerEnabled) "$base&udm=14" else base
                }
            }
        }
    }

    // --- Browser Navigation ---
    fun loadUrl(url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        // Security: block dangerous schemes at the navigation entry point
        val scheme = try {
            android.net.Uri.parse(formattedUrl).scheme?.lowercase(java.util.Locale.ROOT)
        } catch (_: Exception) { null }
        if (SecurityPolicy.isDangerousExternalScheme(scheme)) {
            Log.w(TAG, "🛡️ loadUrl blocked dangerous scheme '$scheme': $formattedUrl")
            return
        }

        val lower = formattedUrl.lowercase()
        if (lower == "omni:config" || lower == "omni://config" || lower == "about:config") {
            formattedUrl = "omni:config"
        }

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://") && !formattedUrl.startsWith("about:") && !formattedUrl.startsWith("omni:") && !formattedUrl.startsWith("javascript:") && !formattedUrl.startsWith("moz-extension://")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                getSearchUrlForQuery(formattedUrl)
            }
        }

        // Pass extension-internal pages directly to GeckoSession — never proxy them
        if (formattedUrl.startsWith("moz-extension://")) {
            val activeId = activeTabId
            if (activeId != null) {
                val idx = tabs.indexOfFirst { it.id == activeId }
                if (idx != -1) {
                    tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...", isUriLoaded = true)
                }
            }
            currentUrl = formattedUrl
            geckoSession.loadUri(formattedUrl)
            return
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

        if (formattedUrl.startsWith("about:") || formattedUrl.startsWith("omni:") || formattedUrl.startsWith("javascript:")) {
            val activeId = activeTabId
            if (activeId != null) {
                val idx = tabs.indexOfFirst { it.id == activeId }
                if (idx != -1 && (formattedUrl.startsWith("about:") || formattedUrl.startsWith("omni:"))) {
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
                 "  try {" +
                 "    var metas = document.querySelectorAll('meta[name=viewport]');" +
                 "    metas.forEach(function(m){ if (m && m.parentNode) m.parentNode.removeChild(m); });" +
                 "    var meta = document.createElement('meta');" +
                 "    meta.name = 'viewport';" +
                 "    meta.content = 'width=device-width, initial-scale=1.0, minimum-scale=0.1, maximum-scale=10.0, user-scalable=yes';" +
                 "    (document.head || document.documentElement).appendChild(meta);" +
                 "    if (document.documentElement) document.documentElement.style.touchAction = 'auto';" +
                 "    if (document.body) document.body.style.touchAction = 'auto';" +
                 "    window.addEventListener('touchmove', function(e){ if (e.touches && e.touches.length > 1) e.stopPropagation(); }, true);" +
                 "  } catch(e) {}" +
                 "})();"
        try {
            val activeTab = tabs.find { it.id == activeTabId }
            val targetSession = activeTab?.session ?: geckoSession
            targetSession.loadUri(js)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting zoom enabler", e)
        }
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

    /** Complete the in-flight extension install prompt with the user's choice. */
    fun respondToInstallPrompt(allow: Boolean) {
        val pending = pendingExtensionInstallPrompt ?: return
        pendingExtensionInstallPrompt = null
        runCatching {
            pending.geckoResult.complete(
                org.mozilla.geckoview.WebExtension.PermissionPromptResponse(
                    allow, // isPermissionsGranted
                    allow, // isPrivateModeGranted
                    false  // isTechnicalAndInteractionDataGranted
                )
            )
        }
    }

    fun syncUserExtensions() {
        val runtime = geckoRuntime ?: return
        runtime.webExtensionController.list()
            .accept(
                { list ->
                    val coreIds = listOf(GRABBER_ID, "omni-universal-copy@omnibrowser.app", AI_BLOCKER_ID, "omni-agent@omnibrowser.app", PROXY_ROUTER_ID, FORCE_DARK_EXTENSION_ID, "omni-translate@omnibrowser.app")
                    // Skip null-id extensions (e.g. a built-in installed before its
                    // manifest declared applications.gecko.id): they cannot be
                    // enabled/disabled and would crash the UI which keys on ext.id.
                    val filtered = list?.filter { ext ->
                        val id = ext.id
                        id != null && id !in coreIds
                    } ?: emptyList()
                    val leftoverAgent = list?.find { it.id == "omni-agent@omnibrowser.app" }
                    if (leftoverAgent != null) {
                        Log.i(TAG, "Leftover Omni Agent extension found in profile database. Uninstalling...")
                        runtime.webExtensionController.uninstall(leftoverAgent)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val context = appContext
                        val (savedOrder, disabledIdsSet) = if (context != null) {
                            runBlocking {
                                try {
                                    val prefs = context.dataStore.data.first()
                                    val order = prefs[EXTENSION_ORDER_KEY]?.split(",") ?: emptyList()
                                    val disabledStr = prefs[EXTENSION_DISABLED_IDS_KEY] ?: ""
                                    val disabled = if (disabledStr.isBlank()) emptySet() else disabledStr.split(",").filter { it.isNotBlank() }.toSet()
                                    Pair(order, disabled)
                                } catch (e: Exception) {
                                    Pair(emptyList<String>(), emptySet<String>())
                                }
                            }
                        } else {
                            Pair(emptyList<String>(), emptySet<String>())
                        }

                        filtered.forEach { ext ->
                            runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                             if (ext.id == FORCE_DARK_EXTENSION_ID) {
                                 if (forceDarkWebsites) {
                                     runtime.webExtensionController.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                                 } else {
                                     runtime.webExtensionController.disable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                                 }
                             } else {
                                 if (disabledIdsSet.contains(ext.id)) {
                                     runtime.webExtensionController.disable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                                 } else {
                                     runtime.webExtensionController.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                                 }
                             }
                            ext.setTabDelegate(object : WebExtension.TabDelegate {
                                override fun onNewTab(
                                    extension: WebExtension,
                                    createDetails: WebExtension.CreateTabDetails
                                ): GeckoResult<GeckoSession>? {
                                    Log.d(TAG, "WebExtension ${extension.id} requested onNewTab: ${createDetails.url}")
                                    val url = createDetails.url ?: "about:blank"
                                    val result = GeckoResult<GeckoSession>()
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        try {
                                            val context = appContext
                                            if (context != null) {
                                                createNewTab(context, url)
                                                val createdSession = tabs.find { it.id == activeTabId }?.session ?: tabs.lastOrNull()?.session
                                                result.complete(createdSession)
                                            } else {
                                                result.completeExceptionally(IllegalStateException("Context is null"))
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error in WebExtension onNewTab", e)
                                            result.completeExceptionally(e)
                                        }
                                    }
                                    return result
                                }

                                override fun onOpenOptionsPage(extension: WebExtension) {
                                    Log.d(TAG, "WebExtension ${extension.id} requested onOpenOptionsPage")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        try {
                                            val context = appContext
                                            if (context != null) {
                                                val meta = extension.metaData
                                                val rawOptions = meta?.optionsPageUrl
                                                val baseUrl = meta?.baseUrl ?: ""
                                                val optionsUrl = if (!rawOptions.isNullOrBlank()) {
                                                    if (rawOptions.startsWith("moz-extension://") || rawOptions.startsWith("http://") || rawOptions.startsWith("https://")) rawOptions
                                                    else "${baseUrl.removeSuffix("/")}/${rawOptions.removePrefix("/")}"
                                                } else if (baseUrl.isNotBlank()) {
                                                    "${baseUrl.removeSuffix("/")}/options/index.html"
                                                } else null
                                                if (optionsUrl != null) {
                                                    createNewTab(context, optionsUrl)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error opening options page for ${extension.id}", e)
                                        }
                                    }
                                }
                            })
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

    // ── Bookmark Import State (Phase 05) ───────────────────────────────────
    var importPreview by mutableStateOf<com.rebelroot.omni.bookmarks.importexport.ImportPreviewState?>(null)
    var isImporting by mutableStateOf(false)

    val shortcutsList = mutableStateListOf<HomeShortcut>()
    
    private fun loadShortcuts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val torrentShortcuts = listOf(
                HomeShortcut("1337x", "1337x", "https://1337x.to"),
                HomeShortcut("piratebay", "The Pirate Bay", "https://thepiratebay.org"),
                HomeShortcut("yts", "YTS Movies", "https://yts.mx"),
                HomeShortcut("torrentgalaxy", "TorrentGalaxy", "https://torrentgalaxy.mx"),
                HomeShortcut("eztv", "EZTV Series", "https://eztv.re"),
                HomeShortcut("fitgirl", "FitGirl Repacks", "https://fitgirl-repacks.site"),
                HomeShortcut("limetorrents", "LimeTorrents", "https://www.limetorrents.lol"),
                HomeShortcut("nyaa", "Nyaa Anime", "https://nyaa.si"),
                HomeShortcut("rutracker", "RuTracker", "https://rutracker.org"),
                HomeShortcut("academictorrents", "Academic Torrents", "https://academictorrents.com")
            )
            val file = File(context.filesDir, "browser_shortcuts.json")
            if (!file.exists()) {
                val defaultList = mutableListOf(
                    HomeShortcut("rebelroot", "RebelRoot", "https://www.rebelroot.xyz/omnibrowser", isPermanent = true),
                    HomeShortcut("twitter", "Twitter", "https://twitter.com"),
                    HomeShortcut("spotify", "Spotify", "https://spotify.com"),
                    HomeShortcut("amazon", "Amazon", "https://amazon.com"),
                    HomeShortcut("pinterest", "Pinterest", "https://pinterest.com")
                )
                defaultList.addAll(torrentShortcuts)
                defaultList.addAll(listOf(
                    HomeShortcut("downloads", "Downloads", "downloads", isFeature = true),
                    HomeShortcut("history", "History", "history", isFeature = true),
                    HomeShortcut("bookmarks", "Bookmarks", "bookmarks", isFeature = true),
                    HomeShortcut("incognito", "Incognito", "incognito", isFeature = true)
                ))
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
                    
                    // Skip duplicate/old RebelRoot entries and about:blank
                    if (id == "rebelroot" || url == "https://www.rebelroot.xyz/omnibrowser" || title.equals("RebelRoot", ignoreCase = true) || url.isBlank() || url == "about:blank" || url.contains("about:blank")) {
                        continue
                    }
                    
                    // Migrate old/outdated torrent domains to official active ones
                    var migratedUrl = url
                    if (url.contains("torrentgalaxy.to") || url.contains("tgx.rs")) {
                        migratedUrl = url.replace("torrentgalaxy.to", "torrentgalaxy.mx").replace("tgx.rs", "torrentgalaxy.mx")
                    } else if (url.contains("eztvx.to") || url.contains("eztv.ag")) {
                        migratedUrl = url.replace("eztvx.to", "eztv.re").replace("eztv.ag", "eztv.re")
                    } else if (url.contains("limetorrents.co") || url.contains("limetorrents.info") || url.contains("limetorrents.cc")) {
                        migratedUrl = url.replace("www.limetorrents.co", "www.limetorrents.lol")
                            .replace("limetorrents.co", "limetorrents.lol")
                            .replace("limetorrents.info", "limetorrents.lol")
                            .replace("limetorrents.cc", "limetorrents.lol")
                    } else if (url.contains("thepiratebay.se")) {
                        migratedUrl = url.replace("thepiratebay.se", "thepiratebay.org")
                    } else if (url.contains("yts.am") || url.contains("yts.ag")) {
                        migratedUrl = url.replace("yts.am", "yts.mx").replace("yts.ag", "yts.mx")
                    }

                    temp.add(HomeShortcut(
                        id = id,
                        title = title,
                        url = migratedUrl,
                        isFeature = obj.optBoolean("isFeature", false),
                        isPermanent = obj.optBoolean("isPermanent", false)
                    ))
                }

                // Ensure popular torrent shortcuts are present if missing and updated if outdated
                for (ts in torrentShortcuts) {
                    val existingIndex = temp.indexOfFirst { it.id == ts.id }
                    if (existingIndex != -1) {
                        val existing = temp[existingIndex]
                        if (existing.url != ts.url) {
                            temp[existingIndex] = existing.copy(url = ts.url)
                        }
                    } else if (temp.none { it.url == ts.url }) {
                        val featureIndex = temp.indexOfFirst { it.isFeature }
                        if (featureIndex != -1) {
                            temp.add(featureIndex, ts)
                        } else {
                            temp.add(ts)
                        }
                    }
                }

                val cleanTemp = temp.filter { !it.url.isBlank() && it.url != "about:blank" && !it.url.contains("about:blank") }
                withContext(Dispatchers.Main) {
                    shortcutsList.clear()
                    shortcutsList.addAll(cleanTemp)
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
                shortcutsSnapshot.filter { !it.url.isBlank() && it.url != "about:blank" && !it.url.contains("about:blank") }.forEach { shortcut ->
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
        
        // Prevent adding blank pages or about:blank to shortcuts
        if (formattedUrl.isBlank() || formattedUrl == "about:blank" || formattedUrl.contains("about:blank")) {
            Toast.makeText(context, "Cannot add blank page to shortcuts", Toast.LENGTH_SHORT).show()
            return
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

    fun getTorrentMirrorFallback(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains("1337x.to") -> url.replace("1337x.to", "1337x.st")
            lower.contains("1337x.st") -> url.replace("1337x.st", "x1337x.ws")
            lower.contains("x1337x.ws") -> url.replace("x1337x.ws", "1337x.so")
            lower.contains("thepiratebay.org") -> url.replace("thepiratebay.org", "tpb.party")
            lower.contains("tpb.party") -> url.replace("tpb.party", "thepiratebay10.org")
            lower.contains("tgx.rs") -> url.replace("tgx.rs", "torrentgalaxy.mx")
            lower.contains("torrentgalaxy.to") -> url.replace("torrentgalaxy.to", "torrentgalaxy.mx")
            lower.contains("torrentgalaxy.mx") -> url.replace("torrentgalaxy.mx", "torrentgalaxy.one")
            lower.contains("eztvx.to") -> url.replace("eztvx.to", "eztv.re")
            lower.contains("eztv.re") -> url.replace("eztv.re", "eztv.tf")
            lower.contains("eztv.tf") -> url.replace("eztv.tf", "eztv.wf")
            lower.contains("limetorrents.co") -> url.replace("limetorrents.co", "www.limetorrents.lol")
            lower.contains("limetorrents.lol") -> url.replace("limetorrents.lol", "www.limetorrents.fun")
            lower.contains("limetorrents.fun") -> url.replace("limetorrents.fun", "www.limetorrents.info")
            lower.contains("rutracker.org") -> url.replace("rutracker.org", "rutracker.net")
            lower.contains("rutracker.net") -> url.replace("rutracker.net", "rutracker.nl")
            lower.contains("yts.mx") -> url.replace("yts.mx", "yts.lt")
            lower.contains("yts.lt") -> url.replace("yts.lt", "yts.do")
            lower.contains("nyaa.si") -> url.replace("nyaa.si", "nyaa.land")
            else -> null
        }
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
    var selectedNewsCategory by mutableStateOf("News")
    var isNewsLoading by mutableStateOf(false)
    var isMoreNewsLoading by mutableStateOf(false)
    var hasMoreNews by mutableStateOf(true)
    private var newsPageIndex = 0

    private val categoryNewsCache: MutableMap<String, List<NewsArticle>> =
        object : java.util.LinkedHashMap<String, List<NewsArticle>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, List<NewsArticle>>): Boolean =
                size > 8
        }

    fun fetchNews(category: String = "Top Stories", forceRefresh: Boolean = false) {
        selectedNewsCategory = category
        hasMoreNews = true
        newsPageIndex = 0

        val cached = categoryNewsCache[category]
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            newsArticles.clear()
            newsArticles.addAll(cached)
            isNewsLoading = false
            return
        }

        isNewsLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            var list = com.rebelroot.omni.news.data.PaperRunNewsProvider.fetchArticles(category)
            if (list.isEmpty()) {
                list = fetchRssArticles(category, page = 0)
            }
            val filtered = list.filter { it.title.length >= 10 }
            if (filtered.isNotEmpty()) {
                categoryNewsCache[category] = filtered
            }
            launch(Dispatchers.Main) {
                if (selectedNewsCategory == category) {
                    newsArticles.clear()
                    newsArticles.addAll(filtered)
                    isNewsLoading = false
                }
            }
        }
    }

    fun loadMoreNews() {
        if (isNewsLoading || isMoreNewsLoading || !hasMoreNews) return
        isMoreNewsLoading = true
        newsPageIndex++
        val pageToFetch = newsPageIndex
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = fetchRssArticles(selectedNewsCategory, page = pageToFetch)
            launch(Dispatchers.Main) {
                if (newItems.isNotEmpty()) {
                    val existingTitles = newsArticles.map { it.title.trim().lowercase() }.toSet()
                    val uniqueNew = newItems.filter { it.title.length >= 10 && !existingTitles.contains(it.title.trim().lowercase()) }
                    if (uniqueNew.isNotEmpty()) {
                        newsArticles.addAll(uniqueNew)
                    } else if (pageToFetch > 10) {
                        hasMoreNews = false
                    }
                } else {
                    hasMoreNews = false
                }
                isMoreNewsLoading = false
            }
        }
    }

    private fun fetchRssArticles(category: String, page: Int): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        try {
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
                "Technology", "Tech" -> "headlines/section/topic/TECHNOLOGY"
                "Sports"        -> "headlines/section/topic/SPORTS"
                "Business", "Finance" -> "headlines/section/topic/BUSINESS"
                "Science"       -> "headlines/section/topic/SCIENCE"
                "Entertainment" -> "headlines/section/topic/ENTERTAINMENT"
                "Health"        -> "headlines/section/topic/HEALTH"
                else            -> null
            }

            val rssUrl = when {
                page > 0 -> {
                    val query = when (page % 5) {
                        1 -> "$category latest news"
                        2 -> "$category breaking news"
                        3 -> "$category updates"
                        4 -> "$category top stories"
                        else -> "$category digest"
                    }
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    "https://news.google.com/rss/search?q=$encodedQuery&hl=$hl&gl=$gl&ceid=$ceid"
                }
                topicPath != null -> {
                    "https://news.google.com/rss/$topicPath?hl=$hl&gl=$gl&ceid=$ceid"
                }
                category == "Top Stories" || category == "News" || category == "All" -> {
                    "https://news.google.com/rss?hl=$hl&gl=$gl&ceid=$ceid"
                }
                else -> {
                    val encodedQuery = java.net.URLEncoder.encode(category, "UTF-8")
                    "https://news.google.com/rss/search?q=$encodedQuery&hl=$hl&gl=$gl&ceid=$ceid"
                }
            }

            val conn = java.net.URL(rssUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout    = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            val parser = android.util.Xml.newPullParser()
            parser.setInput(conn.inputStream, "UTF-8")

            var eventType     = parser.eventType
            var insideItem    = false
            var currentTag    = ""
            var title         = ""
            var link          = ""
            var pubDate       = ""
            var description   = ""
            var source        = ""
            var sourceUrl     = ""
            var mediaImageUrl = ""

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        currentTag = parser.name ?: ""
                        if (currentTag.equals("item", ignoreCase = true)) {
                            insideItem = true
                            mediaImageUrl = ""
                        }
                        if (insideItem) {
                            if (currentTag.equals("source", ignoreCase = true)) {
                                sourceUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                            if (currentTag.contains("media:content", ignoreCase = true) ||
                                currentTag.contains("media:thumbnail", ignoreCase = true) ||
                                currentTag.equals("thumbnail", ignoreCase = true)
                            ) {
                                val urlAttr = parser.getAttributeValue(null, "url") ?: ""
                                if (urlAttr.isNotEmpty() && mediaImageUrl.isEmpty()) {
                                    mediaImageUrl = urlAttr
                                }
                            }
                            if (currentTag.equals("enclosure", ignoreCase = true)) {
                                val encUrl  = parser.getAttributeValue(null, "url") ?: ""
                                val encType = parser.getAttributeValue(null, "type") ?: ""
                                if (encUrl.isNotEmpty() && (encType.contains("image", ignoreCase = true) || encUrl.contains(".jpg") || encUrl.contains(".png") || encUrl.contains(".webp"))) {
                                    if (mediaImageUrl.isEmpty()) {
                                        mediaImageUrl = encUrl
                                    }
                                }
                            }
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
                                val cleanTitle = if (rawTitle.contains(" - "))
                                    rawTitle.substringBeforeLast(" - ").trim()
                                else rawTitle.trim()

                                if (cleanTitle.length >= 10) {
                                    val sourceName = if (rawTitle.contains(" - "))
                                        rawTitle.substringAfterLast(" - ").trim()
                                    else source.trim().ifEmpty { "News" }

                                    val cleanDate = try {
                                        val parts = pubDate.trim().split(" ")
                                        if (parts.size >= 4) "${parts[2]} ${parts[3]}" else pubDate.trim()
                                    } catch (e: Exception) { pubDate.trim() }

                                    val imgRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                    val imgMatch = imgRegex.find(description)
                                    val htmlImgUrl = imgMatch?.groupValues?.getOrNull(1)?.trim() ?: ""

                                    val rawCandidate = when {
                                        mediaImageUrl.isNotEmpty() -> mediaImageUrl
                                        htmlImgUrl.isNotEmpty() -> htmlImgUrl
                                        else -> ""
                                    }

                                    val domain = extractDomain(sourceUrl.ifEmpty { null }, sourceName)
                                    val sourceFaviconUrl = "https://www.google.com/s2/favicons?sz=64&domain=$domain"

                                    val headlineImageUrl = resolveHeadlineImageUrl(rawCandidate, cleanTitle, category)

                                    if (list.none { it.title.equals(cleanTitle, ignoreCase = true) }) {
                                        list.add(NewsArticle(
                                            title            = cleanTitle,
                                            link             = rawLink,
                                            source           = sourceName,
                                            pubDate          = cleanDate,
                                            imageUrl         = headlineImageUrl,
                                            sourceFaviconUrl = sourceFaviconUrl,
                                            category         = category
                                        ))
                                    }
                                }
                            }

                            title = ""; link = ""; pubDate = ""
                            description = ""; source = ""; sourceUrl = ""
                            mediaImageUrl = ""
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching news RSS feed: ${e.message}", e)
        }
        return list
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

    private val topicHeadlinePhotos = mapOf(
        "Technology" to listOf(
            "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=800",
            "https://images.unsplash.com/photo-1498050108023-c5249f4df085?q=80&w=800",
            "https://images.unsplash.com/photo-1519389950473-47ba0277781c?q=80&w=800",
            "https://images.unsplash.com/photo-1531297484001-80022131f5a1?q=80&w=800",
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?q=80&w=800",
            "https://images.unsplash.com/photo-1550745165-9bc0b252726f?q=80&w=800",
            "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?q=80&w=800"
        ),
        "Sports" to listOf(
            "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?q=80&w=800",
            "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?q=80&w=800",
            "https://images.unsplash.com/photo-1546519638-68e109498ffc?q=80&w=800",
            "https://images.unsplash.com/photo-1530549387789-4c1017266635?q=80&w=800",
            "https://images.unsplash.com/photo-1517649763962-0c623266010b?q=80&w=800"
        ),
        "Business" to listOf(
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=800",
            "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=800",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?q=80&w=800",
            "https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?q=80&w=800",
            "https://images.unsplash.com/photo-1507679799987-c73779587ccf?q=80&w=800"
        ),
        "World" to listOf(
            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=800",
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?q=80&w=800",
            "https://images.unsplash.com/photo-1526778548025-fa2f459cd5c1?q=80&w=800",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=800",
            "https://images.unsplash.com/photo-1508873696983-2df515122519?q=80&w=800",
            "https://images.unsplash.com/photo-1477959858617-67f30ac4ce78?q=80&w=800"
        ),
        "Top Stories" to listOf(
            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=800",
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?q=80&w=800",
            "https://images.unsplash.com/photo-1477959858617-67f30ac4ce78?q=80&w=800",
            "https://images.unsplash.com/photo-1526778548025-fa2f459cd5c1?q=80&w=800"
        ),
        "News" to listOf(
            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=800",
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?q=80&w=800",
            "https://images.unsplash.com/photo-1477959858617-67f30ac4ce78?q=80&w=800"
        ),
        "Science" to listOf(
            "https://images.unsplash.com/photo-1614728894747-a83421e2b9c9?q=80&w=800",
            "https://images.unsplash.com/photo-1532094349884-543bc11b234d?q=80&w=800",
            "https://images.unsplash.com/photo-1507668077129-56e32842fceb?q=80&w=800",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=800"
        ),
        "Entertainment" to listOf(
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=800",
            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=800",
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=800",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=800"
        ),
        "Health" to listOf(
            "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?q=80&w=800",
            "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?q=80&w=800",
            "https://images.unsplash.com/photo-1532938911079-1b06ac7ceec7?q=80&w=800",
            "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=800"
        ),
        "Astrology" to listOf(
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?q=80&w=800",
            "https://images.unsplash.com/photo-1532968961962-8a0cb3a2d4f5?q=80&w=800",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=800"
        ),
        "Recipes" to listOf(
            "https://images.unsplash.com/photo-1498837167922-ddd27525d352?q=80&w=800",
            "https://images.unsplash.com/photo-1504674900247-0877df9cc836?q=80&w=800",
            "https://images.unsplash.com/photo-1476224203421-9ac39bcb3327?q=80&w=800"
        )
    )

    private fun resolveHeadlineImageUrl(
        xmlImage: String,
        title: String,
        category: String
    ): String {
        if (xmlImage.isNotBlank() && !xmlImage.contains("favicons") && !xmlImage.contains("favicon.ico")) {
            return xmlImage
        }

        val pool = topicHeadlinePhotos[category] ?: topicHeadlinePhotos["World"]!!
        val index = Math.abs(title.hashCode()) % pool.size
        return pool[index]
    }

    fun getFallbackCategoryPhoto(title: String, category: String): String {
        val lowerTitle = title.lowercase()
        val detectedCategory = when {
            category.isNotBlank() && topicHeadlinePhotos.containsKey(category) -> category
            lowerTitle.contains("ai") || lowerTitle.contains("tech") || lowerTitle.contains("phone") || lowerTitle.contains("app") || lowerTitle.contains("chip") || lowerTitle.contains("google") || lowerTitle.contains("apple") || lowerTitle.contains("nvidia") -> "Technology"
            lowerTitle.contains("crypto") || lowerTitle.contains("bitcoin") || lowerTitle.contains("stock") || lowerTitle.contains("market") || lowerTitle.contains("bank") || lowerTitle.contains("economy") || lowerTitle.contains("trade") -> "Business"
            lowerTitle.contains("match") || lowerTitle.contains("score") || lowerTitle.contains("football") || lowerTitle.contains("soccer") || lowerTitle.contains("cricket") || lowerTitle.contains("nba") || lowerTitle.contains("league") -> "Sports"
            lowerTitle.contains("movie") || lowerTitle.contains("film") || lowerTitle.contains("actor") || lowerTitle.contains("music") || lowerTitle.contains("star") || lowerTitle.contains("show") || lowerTitle.contains("series") -> "Entertainment"
            lowerTitle.contains("doctor") || lowerTitle.contains("hospital") || lowerTitle.contains("health") || lowerTitle.contains("virus") || lowerTitle.contains("medicine") || lowerTitle.contains("diet") -> "Health"
            lowerTitle.contains("space") || lowerTitle.contains("nasa") || lowerTitle.contains("science") || lowerTitle.contains("planet") || lowerTitle.contains("astronomy") -> "Science"
            else -> "World"
        }
        val pool = topicHeadlinePhotos[detectedCategory] ?: topicHeadlinePhotos["World"]!!
        val index = Math.abs(title.hashCode()) % pool.size
        return pool[index]
    }

    private fun enrichArticlesWithOriginalOgImages(articles: List<NewsArticle>, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            articles.chunked(4).forEach { chunk ->
                val jobs = chunk.map { article ->
                    async {
                        val realOgImage = fetchOriginalArticleImage(article.link)
                        if (!realOgImage.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                val index = newsArticles.indexOfFirst { it.link == article.link }
                                if (index != -1) {
                                    newsArticles[index] = newsArticles[index].copy(imageUrl = realOgImage)
                                }
                                categoryNewsCache[category]?.let { cachedList ->
                                    val cacheIdx = cachedList.indexOfFirst { it.link == article.link }
                                    if (cacheIdx != -1) {
                                        val mutable = cachedList.toMutableList()
                                        mutable[cacheIdx] = mutable[cacheIdx].copy(imageUrl = realOgImage)
                                        categoryNewsCache[category] = mutable
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }
    }

    private fun resolveGoogleNewsRedirect(googleNewsUrl: String): String {
        try {
            val conn = java.net.URL(googleNewsUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

            val inputStream = conn.inputStream
            val buf = ByteArray(32768)
            val read = inputStream.read(buf)
            conn.disconnect()

            if (read > 0) {
                val html = String(buf, 0, read, Charsets.UTF_8)
                val linkRegex = Regex("""<a[^>]+href=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                val match = linkRegex.find(html)
                val target = match?.groupValues?.getOrNull(1)
                if (!target.isNullOrEmpty() && !target.contains("news.google.com")) {
                    return target
                }
            }
        } catch (_: Exception) {}
        return googleNewsUrl
    }

    private fun fetchOriginalArticleImage(articleUrl: String): String? {
        if (articleUrl.isBlank()) return null
        return try {
            val targetUrl = if (articleUrl.contains("news.google.com")) {
                resolveGoogleNewsRedirect(articleUrl)
            } else {
                articleUrl
            }

            val conn = java.net.URL(targetUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val inputStream = conn.inputStream
            val buf = ByteArray(65536)
            val read = inputStream.read(buf)
            conn.disconnect()

            if (read > 0) {
                val html = String(buf, 0, read, Charsets.UTF_8)
                val ogPattern = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val ogPattern2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
                val twitterPattern = Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

                val match = ogPattern.find(html) ?: ogPattern2.find(html) ?: twitterPattern.find(html)
                var rawImg = match?.groupValues?.getOrNull(1)?.trim() ?: ""

                if (rawImg.isNotEmpty()) {
                    rawImg = rawImg.replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")

                    if (rawImg.startsWith("//")) {
                        rawImg = "https:$rawImg"
                    } else if (rawImg.startsWith("/") && !rawImg.startsWith("//")) {
                        val baseUri = Uri.parse(targetUrl)
                        val scheme = baseUri.scheme ?: "https"
                        val host = baseUri.host ?: ""
                        if (host.isNotEmpty()) {
                            rawImg = "$scheme://$host$rawImg"
                        }
                    }

                    if (rawImg.startsWith("http://") || rawImg.startsWith("https://")) {
                        if (!rawImg.endsWith(".svg", ignoreCase = true)) {
                            return rawImg
                        }
                    }
                }
                null
            } else null
        } catch (e: Exception) {
            null
        }
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
    var ttsRate by mutableStateOf(1.0f)
    
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
        engine.setSpeechRate(ttsRate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "omni_tts")
        isTtsPlaying = true
    }

    fun setTtsSpeechRate(rate: Float) {
        ttsRate = rate
        tts?.setSpeechRate(rate)
    }

    fun stopTts() {
        tts?.stop()
        isTtsPlaying = false
    }

    fun readCurrentPageAloud() {
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        val session = activeTab.session
        val js = "javascript:(function(){" +
                "  var el = document.querySelector('#omni-reader-container') || document.body;" +
                "  var text = el ? (el.innerText || el.textContent) : '';" +
                "  if (text) { console.warn('READER_TTS_CONTENT:' + text.substring(0, 8000)); }" +
                "})();"
        session.loadUri(js)
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
                val raw = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                // Scale to exactly 96×96 for the launcher shortcut icon.
                // The source is at most 128×128 (sz=128) so this is a cheap
                // downscale; it reduces the Bitmap memory held by ShortcutManager
                // from ~65 KB (128×128 ARGB_8888) to ~18 KB (96×96 ARGB_8888).
                bitmap = if (raw != null) {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(raw, 96, 96, true)
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else null
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
            val pInfo = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
            val currentVersionCode = if (pInfo != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode?.toLong() ?: 0L
            }
            val currentVersionName = pInfo?.versionName ?: "1.0.0"

            // Always use GitHub Releases API as the single source of truth.
            // This means no separate version.json to forget updating, and the
            // download path already fetches the latest release assets anyway.
            try {
                val apiUrl = java.net.URL("https://api.github.com/repos/REBEL-ROOT/omni-browser/releases/latest")
                val apiConn = apiUrl.openConnection() as java.net.HttpURLConnection
                apiConn.requestMethod = "GET"
                apiConn.setRequestProperty("Accept", "application/vnd.github+json")
                apiConn.setRequestProperty("User-Agent", "OmniBrowser-OTA-Checker")
                apiConn.connectTimeout = 8000
                apiConn.readTimeout = 8000
                apiConn.connect()

                if (apiConn.responseCode == 200) {
                    val apiResponse = apiConn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(apiResponse)
                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    val htmlUrl = json.optString("html_url", "https://github.com/REBEL-ROOT/omni-browser/releases/latest")

                    // Compare by version name — compareVersionNames handles 4-part versions
                    val hasNewerVersion = tagName.isNotEmpty() &&
                        compareVersionNames(tagName, currentVersionName) > 0

                    withContext(Dispatchers.Main) {
                        if (hasNewerVersion) {
                            onResult(UpdateCheckResult.NewUpdateAvailable(tagName, htmlUrl))
                        } else {
                            onResult(UpdateCheckResult.NoUpdateAvailable)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(UpdateCheckResult.Error("GitHub API returned HTTP ${apiConn.responseCode}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check GitHub releases for updates", e)
                withContext(Dispatchers.Main) {
                    onResult(UpdateCheckResult.Error(e.localizedMessage ?: "Connection error"))
                }
            }
        }
    }

    private fun compareVersionNames(v1: String, v2: String): Int {
        val parts1 = v1.split('.').mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split('.').mapNotNull { it.toIntOrNull() }
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    var isDownloadingUpdate by mutableStateOf(false)
    var updateDownloadProgress by mutableStateOf(0f)
    var updateDownloadError by mutableStateOf<String?>(null)

    fun downloadAndInstallApk(context: Context, downloadUrl: String) {
        isDownloadingUpdate = true
        updateDownloadProgress = 0f
        updateDownloadError = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var targetUrl = downloadUrl
                // If it is a github release url, parse it to extract the latest APK asset
                if (downloadUrl.contains("github.com") && downloadUrl.contains("/releases")) {
                    try {
                        val apiConnection = java.net.URL("https://api.github.com/repos/REBEL-ROOT/omni-browser/releases/latest").openConnection() as java.net.HttpURLConnection
                        apiConnection.requestMethod = "GET"
                        apiConnection.setRequestProperty("Accept", "application/vnd.github+json")
                        apiConnection.setRequestProperty("User-Agent", "OmniBrowser-OTA-Installer")
                        apiConnection.connectTimeout = 8000
                        apiConnection.connect()
                        if (apiConnection.responseCode == 200) {
                            val apiResponse = apiConnection.inputStream.bufferedReader().use { it.readText() }
                            val apiJson = org.json.JSONObject(apiResponse)
                            val assets = apiJson.optJSONArray("assets")
                            if (assets != null) {
                                // Map Android ABI names → keywords used in our APK filenames.
                                // e.g. arm64-v8a → aarch64, armeabi-v7a → arm, x86_64 → x86_64
                                val abiKeywordMap = mapOf(
                                    "arm64-v8a" to "aarch64",
                                    "armeabi-v7a" to "arm",
                                    "x86_64" to "x86_64",
                                    "x86" to "x86"
                                )
                                val supportedAbis = android.os.Build.SUPPORTED_ABIS.toList()
                                var universalUrl: String? = null
                                var abiSpecificUrl: String? = null
                                for (i in 0 until assets.length()) {
                                    val asset = assets.getJSONObject(i)
                                    val name = asset.getString("name").lowercase()
                                    if (name.endsWith(".apk")) {
                                        val assetDownloadUrl = asset.getString("browser_download_url")
                                        if (name.contains("universal")) {
                                            universalUrl = assetDownloadUrl
                                        } else {
                                            // Check if this asset matches any of the device's supported ABIs
                                            val matched = supportedAbis.any { abi ->
                                                val keyword = abiKeywordMap[abi] ?: abi.lowercase()
                                                name.contains(keyword)
                                            }
                                            if (matched && abiSpecificUrl == null) {
                                                abiSpecificUrl = assetDownloadUrl
                                            }
                                        }
                                    }
                                }
                                // Prefer ABI-specific APK; fall back to universal
                                val bestAssetUrl = abiSpecificUrl ?: universalUrl
                                if (bestAssetUrl != null) {
                                    targetUrl = bestAssetUrl
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "GitHub API fetch failed, fallback to direct url", e)
                    }
                }

                val url = java.net.URL(targetUrl)
                var connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 0 // No read timeout for large APK streaming downloads
                connection.connect()

                var responseCode = connection.responseCode
                var tries = 0
                while ((responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) && tries < 5) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection = java.net.URL(redirectUrl).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 0 // No read timeout for large APK streaming downloads
                    connection.connect()
                    responseCode = connection.responseCode
                    tries++
                }

                if (responseCode == 200) {
                    val length = connection.contentLength
                    val destination = java.io.File(context.cacheDir, "omni-browser-update.apk")
                    if (destination.exists()) destination.delete()

                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (length > 0) {
                                    val progress = totalBytesRead.toFloat() / length.toFloat()
                                    withContext(Dispatchers.Main) {
                                        updateDownloadProgress = progress
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        isDownloadingUpdate = false
                        try {
                            val apkFile = java.io.File(context.cacheDir, "omni-browser-update.apk")
                            if (apkFile.exists() && apkFile.length() > 0) {
                                // Android 8+ requires the app to have "Install Unknown Apps" permission
                                val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.packageManager.canRequestPackageInstalls()
                                } else {
                                    true
                                }
                                if (!canInstall) {
                                    // Send user to system settings to grant permission, then they can retry
                                    val settingsIntent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(settingsIntent)
                                    updateDownloadError = "Please enable 'Install Unknown Apps' for Omni Browser in settings, then try again."
                                    return@withContext
                                }
                                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    apkFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } else {
                                updateDownloadError = "Downloaded file is empty"
                            }
                        } catch (e: Exception) {
                            updateDownloadError = "Installation failed: ${e.localizedMessage}"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isDownloadingUpdate = false
                        updateDownloadError = "Server returned HTTP $responseCode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    updateDownloadError = e.localizedMessage ?: "Failed to download update"
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

    fun saveWebRenderEnabled(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[WEBRENDER_ALL_KEY] = value }
            isWebRenderEnabled = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }

    fun saveGpuAccelerationEnabled(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[LAYERS_ACCELERATION_KEY] = value }
            isGpuAccelerationEnabled = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }

    fun saveForceHighRefreshRate(context: Context, value: Boolean) {
        viewModelScope.launch {
            context.applicationContext.dataStore.edit { it[FORCE_HIGH_REFRESH_RATE_KEY] = value }
            isForceHighRefreshRate = value
            writeGeckoConfigFile(context.applicationContext)
        }
    }

    fun writeGeckoConfigFile(context: Context) {
        // DELEGATE to the canonical full-config writer. Previously this function
        // wrote a reduced set of prefs (dom.ipc, DNT, https-only, prefetch) and
        // CLOBBERED the proxy / Tor-hardening / DoH / fingerprinting settings that
        // getGeckoRuntime and regenerateGeckoConfig had written. That meant any
        // call to saveDoNotTrack / saveHttpsOnlyMode / saveSafeBrowsingLevel /
        // savePreloadPages / updateRuntimeContentBlocking would silently strip
        // proxy settings from the config file, causing the next restart to go
        // direct (leaking the real IP).
        regenerateGeckoConfig()
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

    // Image Grabber & Manga Reader Mode State
    data class MetaTagInfo(val name: String, val content: String)
    data class DomNodeInfo(val tag: String, val id: String, val className: String, val childCount: Int, val snippet: String)
    data class ResourceInfo(val url: String, val type: String, val durationMs: Int, val sizeBytes: Long)
    data class StorageItem(val key: String, val value: String)

    data class PageStats(
        val title: String,
        val wordCount: Int,
        val readTimeMinutes: Int,
        val imageCount: Int,
        val linkCount: Int,
        val scriptCount: Int,
        val cssCount: Int,
        val charCount: Int,
        val h1Count: Int = 0,
        val h2Count: Int = 0,
        val h3Count: Int = 0,
        val metaTags: List<MetaTagInfo> = emptyList(),
        val domNodes: List<DomNodeInfo> = emptyList(),
        val resources: List<ResourceInfo> = emptyList(),
        val cookies: List<StorageItem> = emptyList(),
        val localStorageItems: List<StorageItem> = emptyList()
    )

    var extractedImagesList by mutableStateOf<List<String>>(emptyList())
    var isExtractingImages by mutableStateOf(false)
    var pageInspectorStats by mutableStateOf<PageStats?>(null)
    var consoleEvalResult by mutableStateOf<String?>(null)
    var consoleEvalError by mutableStateOf(false)

    fun executeConsoleJs(code: String) {
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        val escaped = code.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        val js = """
            javascript:(function(){
                try {
                    var res = eval('$escaped');
                    var output = (res === undefined) ? 'undefined' : (res === null) ? 'null' : (typeof res === 'object') ? JSON.stringify(res, null, 2) : String(res);
                    alert('OMNI_EVAL_RESULT:' + JSON.stringify({ ok: true, val: output }));
                } catch(e) {
                    alert('OMNI_EVAL_RESULT:' + JSON.stringify({ ok: false, val: e.toString() }));
                }
            })();
        """.trimIndent()
        activeTab.session.loadUri(js)
    }

    fun inspectCurrentPage(context: Context) {
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        val js = """
            javascript:(function(){
                try {
                    var text = document.body ? document.body.innerText || '' : '';
                    var words = text.trim().split(/\s+/).filter(function(w){ return w.length > 0; });
                    var wordCount = words.length;
                    var readTime = Math.max(1, Math.ceil(wordCount / 200));
                    var imgs = document.querySelectorAll('img').length;
                    var links = document.querySelectorAll('a').length;
                    var scripts = document.querySelectorAll('script').length;
                    var css = document.querySelectorAll('link[rel="stylesheet"]').length;

                    var metas = [];
                    var metaEls = document.querySelectorAll('meta');
                    metaEls.forEach(function(m) {
                        var name = m.getAttribute('name') || m.getAttribute('property') || m.getAttribute('http-equiv') || '';
                        var content = m.getAttribute('content') || '';
                        if (name && content && metas.length < 20) {
                            metas.push({ n: name, c: content });
                        }
                    });

                    var h1 = document.querySelectorAll('h1').length;
                    var h2 = document.querySelectorAll('h2').length;
                    var h3 = document.querySelectorAll('h3').length;

                    var domNodes = [];
                    var keyEls = document.querySelectorAll('header, nav, main, article, section, footer, aside, form, table, script, style, div[id], div[class]');
                    keyEls.forEach(function(el) {
                        if (domNodes.length < 35) {
                            var tag = el.tagName.toLowerCase();
                            var id = el.id || '';
                            var cls = (el.className && typeof el.className === 'string') ? el.className.trim() : '';
                            var children = el.children ? el.children.length : 0;
                            var textSnippet = el.innerText ? el.innerText.trim().substring(0, 50) : '';
                            domNodes.push({ t: tag, i: id, c: cls, ch: children, s: textSnippet });
                        }
                    });

                    var resources = [];
                    if (window.performance && window.performance.getEntriesByType) {
                        var entries = window.performance.getEntriesByType('resource');
                        entries.forEach(function(entry) {
                            if (resources.length < 50) {
                                var u = entry.name;
                                var type = entry.initiatorType || 'other';
                                var dur = Math.round(entry.duration || 0);
                                var size = entry.transferSize || entry.encodedBodySize || 0;
                                resources.push({ u: u, t: type, d: dur, s: size });
                            }
                        });
                    }

                    var cookies = [];
                    if (document.cookie) {
                        var parts = document.cookie.split(';');
                        parts.forEach(function(p) {
                            var kv = p.trim().split('=');
                            if (kv[0] && cookies.length < 25) {
                                cookies.push({ k: kv[0], v: kv.slice(1).join('=') });
                            }
                        });
                    }

                    var localStore = [];
                    try {
                        if (window.localStorage) {
                            for (var i = 0; i < localStorage.length && i < 25; i++) {
                                var key = localStorage.key(i);
                                if (key) {
                                    localStore.push({ k: key, v: String(localStorage.getItem(key)) });
                                }
                            }
                        }
                    } catch(e){}

                    alert('OMNI_PAGE_STATS:' + JSON.stringify({
                        w: wordCount, r: readTime, i: imgs, l: links, s: scripts, c: css, ch: text.length,
                        meta: metas, h1: h1, h2: h2, h3: h3, dom: domNodes, res: resources, ck: cookies, ls: localStore
                    }));
                } catch(e) {}
            })();
        """.trimIndent()
        activeTab.session.loadUri(js)
    }

    fun extractPageImages(context: Context) {
        extractedImagesList = emptyList()
        isExtractingImages = true
        val activeTab = tabs.find { it.id == activeTabId }
        if (activeTab == null) {
            isExtractingImages = false
            return
        }

        // Automatic fallback: hide spinner after 8 seconds if script hasn't signaled DONE
        viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(8000)
            if (isExtractingImages) {
                isExtractingImages = false
            }
        }

        val js = """
            javascript:(function(){
                try {
                    var urls = [];
                    var seen = {};

                    function addUrl(src) {
                        if (!src || typeof src !== 'string') return;
                        src = src.trim();
                        if (src.startsWith('//')) src = window.location.protocol + src;
                        if (src.startsWith('/')) src = window.location.origin + src;
                        if (!src.startsWith('http://') && !src.startsWith('https://')) return;
                        
                        var lower = src.toLowerCase();
                        if (lower.includes('favicon') || lower.includes('pixel.gif') || lower.includes('spinner.gif') || 
                            lower.includes('loading.gif') || lower.includes('blank.png') || lower.includes('placeholder') ||
                            lower.includes('/r.png') || lower.includes('/star.png') || lower.includes('logo') || 
                            lower.includes('avatar') || lower.includes('icon') || lower.includes('badge') || 
                            lower.includes('emoji') || lower.includes('1x1') || lower.startsWith('data:image')) {
                            return;
                        }

                        if (!seen[src]) {
                            seen[src] = true;
                            urls.push(src);
                        }
                    }

                    /* Parse srcset and return ONLY the highest-resolution URL */
                    function bestFromSrcset(srcset) {
                        if (!srcset) return null;
                        var parts = srcset.split(',');
                        var best = null;
                        var bestW = 0;
                        parts.forEach(function(p) {
                            var tokens = p.trim().split(/\s+/);
                            var u = tokens[0];
                            var descriptor = tokens[1] || '';
                            var w = 0;
                            if (descriptor.endsWith('w')) {
                                w = parseInt(descriptor) || 0;
                            } else if (descriptor.endsWith('x')) {
                                w = (parseFloat(descriptor) || 1) * 1000;
                            } else {
                                w = 1;
                            }
                            if (w > bestW || !best) {
                                bestW = w;
                                best = u;
                            }
                        });
                        return best;
                    }

                    /* 1. Extract from DOM Elements (force eager loading across un-scrolled elements) */
                    function collectDomImages() {
                        var selector = 'img, picture source, [data-src], [data-original], [data-lazy-src], [data-url], [data-echo], [data-cdn], [data-actual-src], [data-cfsrc], [data-manga-src], [data-page-src], [data-full-src], [data-hi-res-src], [data-master], [data-img], [data-origin], [data-link], [data-lazy], [data-srcset], [srcset]';
                        var els = document.querySelectorAll(selector);
                        els.forEach(function(el) {
                            /* Force eager loading attribute */
                            if (el.tagName === 'IMG') {
                                el.loading = 'eager';
                                el.removeAttribute('loading');
                            }

                            /* 1. Best from srcset (highest resolution) */
                            var srcset = el.getAttribute('data-srcset') || el.getAttribute('srcset');
                            var bestSrcset = bestFromSrcset(srcset);

                            /* 2. Lazy-load / full-res data attributes */
                            var dataSrc = el.getAttribute('data-original') || 
                                          el.getAttribute('data-src') || 
                                          el.getAttribute('data-lazy-src') || 
                                          el.getAttribute('data-url') || 
                                          el.getAttribute('data-cdn') ||
                                          el.getAttribute('data-actual-src') ||
                                          el.getAttribute('data-cfsrc') ||
                                          el.getAttribute('data-manga-src') ||
                                          el.getAttribute('data-page-src') ||
                                          el.getAttribute('data-full-src') ||
                                          el.getAttribute('data-hi-res-src') ||
                                          el.getAttribute('data-master') ||
                                          el.getAttribute('data-img') ||
                                          el.getAttribute('data-origin') ||
                                          el.getAttribute('data-link') ||
                                          el.getAttribute('data-echo');

                            /* If element has lazy attribute and src is blank/placeholder, update src */
                            if (dataSrc && el.tagName === 'IMG') {
                                if (!el.src || el.src.indexOf('data:image') === 0 || el.src.indexOf('blank') !== -1 || el.src.indexOf('loading') !== -1 || el.src.indexOf('placeholder') !== -1) {
                                    try { el.src = dataSrc; } catch(_e) {}
                                }
                            }

                            /* 3. currentSrc or src */
                            var fallbackSrc = null;
                            if (el.currentSrc) {
                                var csLower = el.currentSrc.toLowerCase();
                                if (!csLower.includes('loading') && !csLower.includes('placeholder') && !csLower.includes('data:image') && !csLower.includes('blank')) {
                                    fallbackSrc = el.currentSrc;
                                }
                            }
                            if (!fallbackSrc && el.src) {
                                var srcLower = el.src.toLowerCase();
                                if (!srcLower.includes('loading') && !srcLower.includes('placeholder') && !srcLower.includes('data:image') && !srcLower.includes('blank')) {
                                    fallbackSrc = el.src;
                                }
                            }

                            var chosen = bestSrcset || dataSrc || fallbackSrc;
                            if (chosen) addUrl(chosen);
                        });

                        /* CSS Background images */
                        var bgEls = document.querySelectorAll('div[style*="background"], a[style*="background"], span[style*="background"], section[style*="background"], .gdtm, .gdtl, [style*="url("]');
                        bgEls.forEach(function(el) {
                            var bg = el.style.backgroundImage || window.getComputedStyle(el).backgroundImage;
                            if (bg && bg.indexOf('url(') !== -1) {
                                var matches = bg.match(/url\(['"]?(.*?)['"]?\)/g);
                                if (matches) {
                                    matches.forEach(function(m) {
                                        var clean = m.replace(/^url\(['"]?/, '').replace(/['"]?\)${'$'}/, '');
                                        addUrl(clean);
                                    });
                                }
                            }
                        });
                    }

                    /* 2. Extract Embedded Manga Chapter Arrays from Script Tags */
                    function extractScriptImages() {
                        try {
                            /* Check global window chapter data candidates */
                            var globalCandidates = [
                                window.chapter_images, window.pages, window.chapterData, window.imageData,
                                window.img_list, window.img_url, window.sources, window.page_list, window.manga_pages
                            ];
                            globalCandidates.forEach(function(arr) {
                                if (Array.isArray(arr)) {
                                    arr.forEach(function(item) {
                                        if (typeof item === 'string') addUrl(item);
                                        else if (item && typeof item === 'object') {
                                            var u = item.url || item.src || item.path || item.link || item.page;
                                            if (u) addUrl(u);
                                        }
                                    });
                                }
                            });

                            /* Parse inline <script> tags for image URL patterns */
                            var scripts = document.querySelectorAll('script');
                            var imageRegex = /https?:\/\/[^"'\s\\]+?\.(?:jpg|jpeg|png|webp|avif)(?:\?[^"'\s\\]*)?/gi;
                            scripts.forEach(function(s) {
                                var text = s.textContent || s.innerText || '';
                                if (!text || text.length < 20) return;
                                var matches = text.match(imageRegex);
                                if (matches) {
                                    matches.forEach(function(u) {
                                        var lower = u.toLowerCase();
                                        if (lower.includes('/chapter/') || lower.includes('/manga/') || lower.includes('/pages/') || 
                                            lower.includes('/uploads/') || lower.includes('/scans/') || lower.includes('/images/') ||
                                            lower.includes('cdn') || lower.includes('img') || lower.includes('page') ||
                                            /\d+\.(jpg|jpeg|png|webp|avif)/i.test(u)) {
                                            addUrl(u);
                                        }
                                    });
                                }
                            });
                        } catch(_e) {}
                    }

                    collectDomImages();
                    extractScriptImages();

                    /* Handle special paginated reader links (e.g. e-hentai / exhentai / gdtm) */
                    var readerLinks = document.querySelectorAll('.gdtm a, .gdtl a, a[href*="/s/"]');
                    if (readerLinks.length > 0) {
                        var linkUrls = [];
                        var linkSeen = {};
                        readerLinks.forEach(function(a) {
                            var href = a.href;
                            if (href && href.indexOf('/s/') !== -1 && !linkSeen[href]) {
                                linkSeen[href] = true;
                                linkUrls.push(href);
                            }
                        });

                        if (linkUrls.length > 0) {
                            var promises = linkUrls.map(function(pageUrl) {
                                return fetch(pageUrl)
                                    .then(function(res) { return res.text(); })
                                    .then(function(html) {
                                        var match = html.match(/<img\s+id="img"\s+src="([^"]+)"/i) || 
                                                    html.match(/src="([^"]+\/h\/[^"]+)"/i);
                                        return match ? match[1] : null;
                                    })
                                    .catch(function() { return null; });
                            });

                            Promise.all(promises).then(function(results) {
                                results.forEach(function(u) {
                                    if (u) addUrl(u);
                                });
                                alert('OMNI_IMAGES:' + JSON.stringify(urls));
                            });
                            return;
                        }
                    }

                    alert('OMNI_IMAGES:' + JSON.stringify(urls));
                } catch(e) {
                    alert('OMNI_IMAGES:[]');
                }
            })();
        """.trimIndent()
        activeTab.session.loadUri(js)
    }

    // Backup & Export Settings (GitHub #43)
    suspend fun buildSettingsBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("app", "OmniBrowser")
        root.put("schema_version", 1)
        root.put("exported_at_ms", System.currentTimeMillis())

        val ds = context.dataStore.data.first()
        val dsArray = JSONArray()
        var skippedCount = 0

        for ((k, v) in ds.asMap()) {
            val entry = JSONObject()
            entry.put("key", k.name)
            when (v) {
                is Boolean -> {
                    entry.put("type", "bool")
                    entry.put("value", v)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", v)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", v.toDouble())
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", v)
                }
                is Double -> {
                    entry.put("type", "double")
                    entry.put("value", v)
                }
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", v)
                }
                is Set<*> -> {
                    entry.put("type", "string_set")
                    val arr = JSONArray()
                    for (item in v) {
                        if (item != null) arr.put(item.toString())
                    }
                    entry.put("value", arr)
                }
                else -> {
                    skippedCount++
                    continue
                }
            }
            dsArray.put(entry)
        }

        val dsObj = JSONObject()
        dsObj.put("omni_settings", dsArray)
        root.put("datastore", dsObj)

        val sp = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE).all
        val spOmniPrefs = JSONObject()

        for ((key, v) in sp) {
            if (v == null) continue
            val entry = JSONObject()
            when (v) {
                is Boolean -> {
                    entry.put("type", "bool")
                    entry.put("value", v)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", v)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", v.toDouble())
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", v)
                }
                is Double -> {
                    entry.put("type", "double")
                    entry.put("value", v)
                }
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", v)
                }
                is Set<*> -> {
                    entry.put("type", "string_set")
                    val arr = JSONArray()
                    for (item in v) {
                        if (item != null) arr.put(item.toString())
                    }
                    entry.put("value", arr)
                }
                else -> {
                    skippedCount++
                    continue
                }
            }
            spOmniPrefs.put(key, entry)
        }

        val spObj = JSONObject()
        spObj.put("omni_prefs", spOmniPrefs)
        root.put("shared_prefs", spObj)

        if (skippedCount > 0) {
            Log.w(TAG, "buildSettingsBackupJson: skipped $skippedCount entries with unknown types")
        }

        return root.toString(2)
    }

    suspend fun restoreSettingsFromJson(context: Context, jsonText: String): BackupImportResult {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return BackupImportResult.InvalidFile
        }

        val app = root.optString("app", "")
        if (app.isNotEmpty() && app != "OmniBrowser") {
            return BackupImportResult.InvalidFile
        }

        val schemaVersion = root.optInt("schema_version", 1)
        if (schemaVersion > 1) {
            return BackupImportResult.InvalidVersion
        }

        var restoredCount = 0
        var skippedCount = 0

        try {
            val dsObj = root.optJSONObject("datastore")
            val omniSettingsArray = dsObj?.optJSONArray("omni_settings")
            if (omniSettingsArray != null) {
                context.dataStore.edit { prefs ->
                    for (i in 0 until omniSettingsArray.length()) {
                        val item = omniSettingsArray.optJSONObject(i) ?: continue
                        val name = item.optString("key", "")
                        val type = item.optString("type", "")
                        if (name.isEmpty()) {
                            skippedCount++
                            continue
                        }
                        val rawValue = item.opt("value")
                        if (rawValue == null || rawValue == JSONObject.NULL) {
                            skippedCount++
                            continue
                        }
                        when (type) {
                            "bool" -> {
                                prefs[booleanPreferencesKey(name)] = rawValue as Boolean
                                restoredCount++
                            }
                            "int" -> {
                                prefs[intPreferencesKey(name)] = (rawValue as Number).toInt()
                                restoredCount++
                            }
                            "float" -> {
                                prefs[floatPreferencesKey(name)] = (rawValue as Number).toFloat()
                                restoredCount++
                            }
                            "long" -> {
                                prefs[longPreferencesKey(name)] = (rawValue as Number).toLong()
                                restoredCount++
                            }
                            "double" -> {
                                prefs[doublePreferencesKey(name)] = (rawValue as Number).toDouble()
                                restoredCount++
                            }
                            "string" -> {
                                prefs[stringPreferencesKey(name)] = rawValue as String
                                restoredCount++
                            }
                            "string_set" -> {
                                val arr = rawValue as JSONArray
                                val set = mutableSetOf<String>()
                                for (j in 0 until arr.length()) {
                                    set.add(arr.getString(j))
                                }
                                prefs[stringSetPreferencesKey(name)] = set
                                restoredCount++
                            }
                            else -> {
                                skippedCount++
                            }
                        }
                    }
                }
            }

            val spObj = root.optJSONObject("shared_prefs")
            val omniPrefsObj = spObj?.optJSONObject("omni_prefs")
            if (omniPrefsObj != null) {
                val sp = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
                val editor = sp.edit()
                val keys = omniPrefsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entry = omniPrefsObj.optJSONObject(key) ?: continue
                    val type = entry.optString("type", "")
                    val rawValue = entry.opt("value")
                    if (rawValue == null || rawValue == JSONObject.NULL) {
                        skippedCount++
                        continue
                    }
                    when (type) {
                        "bool" -> {
                            editor.putBoolean(key, rawValue as Boolean)
                            restoredCount++
                        }
                        "int" -> {
                            editor.putInt(key, (rawValue as Number).toInt())
                            restoredCount++
                        }
                        "float" -> {
                            editor.putFloat(key, (rawValue as Number).toFloat())
                            restoredCount++
                        }
                        "long" -> {
                            editor.putLong(key, (rawValue as Number).toLong())
                            restoredCount++
                        }
                        "double" -> {
                            editor.putLong(key, java.lang.Double.doubleToRawLongBits((rawValue as Number).toDouble()))
                            restoredCount++
                        }
                        "string" -> {
                            editor.putString(key, rawValue as String)
                            restoredCount++
                        }
                        "string_set" -> {
                            val arr = rawValue as JSONArray
                            val set = mutableSetOf<String>()
                            for (j in 0 until arr.length()) {
                                set.add(arr.getString(j))
                            }
                            editor.putStringSet(key, set)
                            restoredCount++
                        }
                        else -> {
                            skippedCount++
                        }
                    }
                }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreSettingsFromJson failed", e)
            return BackupImportResult.InvalidFile
        }

        return BackupImportResult.Success(restoredCount, skippedCount)
    }

    suspend fun reloadSettingsAfterImport(context: Context) {
        loadPlayerSettings(context)

        val prefs = context.dataStore.data.first()
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
        hideRefreshIndicator = prefs[HIDE_REFRESH_INDICATOR_KEY] ?: false
        accessibilityTextScale = prefs[ACCESSIBILITY_TEXT_SCALE_KEY] ?: 1.0f
        accessibilityForceZoom = prefs[ACCESSIBILITY_FORCE_ZOOM_KEY] ?: false
        accessibilityHighContrast = prefs[ACCESSIBILITY_HIGH_CONTRAST_KEY] ?: false

        defaultGeolocation = prefs[DEFAULT_GEOLOCATION_KEY] ?: "ask"
        defaultCamera = prefs[DEFAULT_CAMERA_KEY] ?: "ask"
        defaultMicrophone = prefs[DEFAULT_MICROPHONE_KEY] ?: "ask"
        defaultNotifications = prefs[DEFAULT_NOTIFICATIONS_KEY] ?: "ask"
        defaultJavascriptAllowed = prefs[DEFAULT_JAVASCRIPT_KEY] ?: true
        defaultAutoplayAllowed = prefs[DEFAULT_AUTOPLAY_KEY] ?: true

        val sp = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
        val savedLang = sp.getString("selected_language", null)
        if (!savedLang.isNullOrEmpty()) {
            selectedLanguageCode = savedLang
        }
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

    // -------------------------------------------------------------------------
    // Tab suspension / LRU eviction — two-tier strategy (Chrome/Brave parity)
    //
    //  SOFT suspend  → session.setActive(false): Gecko renderer paused, session
    //                  stays open.  JS/DOM state preserved → no blank pages on
    //                  YouTube / Instagram / other SPAs.  Very cheap to resume.
    //
    //  HARD suspend  → session.close(): frees the full Gecko content process.
    //                  Used only when soft-suspended tab count exceeds
    //                  MAX_SOFT_SUSPENDED_TABS or under critical memory pressure.
    //                  Tab restores via restoreState() or URL reload.
    // -------------------------------------------------------------------------

    /** Maximum number of tabs that keep a live GeckoSession at any time.
     *  The active tab is always live; the (maxLiveTabs - 1) most-recently-used
     *  background tabs stay live; all others are soft-suspended.
     *  Exposed as a var so MainActivity.onTrimMemory can temporarily tighten
     *  the cap under low-memory pressure, then restore the default. */
    var maxLiveTabs: Int = DEFAULT_MAX_LIVE_TABS

    /**
     * Enforces the live-tab cap using soft suspension first.
     * Background tabs beyond [maxLiveTabs] are soft-suspended (session open,
     * renderer paused). When soft-suspended tabs exceed [MAX_SOFT_SUSPENDED_TABS],
     * the oldest ones are hard-suspended (session closed) to free RAM.
     * The active tab is never touched.
     */
    fun enforceSuspendLimit() {
        try {
            val currentActiveId = activeTabId ?: return

            // Soft-suspend background tabs that exceed the live cap
            val liveBackground = tabs
                .filter { !it.isSuspended && it.id != currentActiveId }
                .sortedByDescending { it.lastActiveTime }
            val allowedLive = (maxLiveTabs - 1).coerceAtLeast(0)
            liveBackground.drop(allowedLive).forEach { softSuspendTab(it.id) }

            // Hard-suspend the oldest soft-suspended tabs if they exceed the cap
            val softSuspended = tabs
                .filter { it.isSuspended && it.session.isOpen }
                .sortedBy { it.lastActiveTime }  // oldest first
            softSuspended.drop(MAX_SOFT_SUSPENDED_TABS).forEach { hardSuspendTab(it.id) }
        } catch (e: Exception) {
            Log.w(TAG, "enforceSuspendLimit: ${e.message}")
        }
    }

    /**
     * SOFT suspend: pauses the Gecko renderer without destroying the session.
     * JS/DOM state is preserved — tab switches are instant with no blank page.
     * Safe for all sites including SPAs (YouTube, Instagram, etc.).
     */
    fun softSuspendTab(tabId: String) {
        try {
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx == -1) return
            val tab = tabs[idx]
            if (tab.isSuspended) return
            if (tab.id == activeTabId) return
            tabs[idx] = tab.copy(isSuspended = true)
            Log.d(TAG, "Soft-suspended tab ${tab.id} (${tab.url})")
        } catch (e: Exception) {
            Log.w(TAG, "softSuspendTab($tabId): ${e.message}")
        }
    }

    /**
     * HARD suspend: closes the GeckoSession entirely to free the content process.
     * Used only when soft-suspended tab count is too high or memory is critical.
     * The SessionState captured by onSessionStateChange() is used for restoration.
     */
    fun hardSuspendTab(tabId: String) {
        try {
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx == -1) return
            val tab = tabs[idx]
            if (tab.id == activeTabId) return
            runCatching {
                tab.session.setActive(false)
                if (tab.session.isOpen) tab.session.close()
            }
            // Keep isSuspended = true (already set by soft-suspend or fresh hard-suspend)
            tabs[idx] = tab.copy(isSuspended = true)
            Log.d(TAG, "Hard-suspended tab ${tab.id} (${tab.url}), has state=${tab.savedSessionState != null}")
        } catch (e: Exception) {
            Log.w(TAG, "hardSuspendTab($tabId): ${e.message}")
        }
    }

    /**
     * Legacy entry point — routes to soft suspend by default.
     * Hard suspend is only triggered by [hardSuspendTab] or [onCriticalMemory].
     */
    fun suspendTab(tabId: String) = softSuspendTab(tabId)

    /**
     * Resumes a suspended tab.
     *
     * SOFT resume: session was never closed — just call setActive(true) and the
     *   page reappears instantly with full JS/DOM state. No reload needed.
     *   This covers YouTube, Instagram, and all SPAs.
     *
     * HARD resume: session was closed (extreme memory pressure) — create a new
     *   GeckoSession and restore from saved SessionState or fall back to URL.
     */
    fun resumeTab(tabId: String, context: Context) {
        try {
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx == -1) return
            val tab = tabs[idx]
            if (!tab.isSuspended) return

            if (tab.session.isOpen) {
                // ── SOFT resume ── session is still alive, just wake it up
                runCatching { tab.session.setActive(true) }
                tabs[idx] = tab.copy(isSuspended = false)
                Log.d(TAG, "Soft-resumed tab ${tab.id} (${tab.url}) — session was kept open")
                return
            }

            // ── HARD resume ── session was closed; reconstruct it
            val runtime = getGeckoRuntime(context)
            val isJsAllowed = getSitePermissionValue(tab.url, "javascript") == "allow"
            val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                .usePrivateMode(tab.isIncognito)
                .userAgentMode(
                    if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                )
                .viewportMode(
                    if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                )
                .allowJavascript(isJsAllowed)
                .build()

            val newSession = org.mozilla.geckoview.GeckoSession(settings)
            val savedState = tab.savedSessionState
            val resumedTab = tab.copy(
                session = newSession,
                isSuspended = false,
                isUriLoaded = true
            )
            setupTabSessionListeners(resumedTab, context)
            tabs[idx] = resumedTab
            newSession.open(runtime)

            if (savedState != null) {
                newSession.restoreState(savedState)
                Log.d(TAG, "Hard-resumed tab ${tab.id} (${tab.url}) with restored SessionState")
            } else if (tab.url != "about:blank" && tab.url.isNotEmpty()) {
                newSession.loadUri(tab.url)
                Log.d(TAG, "Hard-resumed tab ${tab.id} (${tab.url}) by URL reload")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resumeTab($tabId): ${e.message}")
        }
    }

    /**
     * Called from [MainActivity.onTrimMemory] on RUNNING_CRITICAL / COMPLETE.
     * Hard-suspends ALL background tabs immediately to free every Gecko content process.
     */
    fun onCriticalMemory() {
        try {
            val currentActiveId = activeTabId
            // First soft-suspend any still-live background tabs
            tabs.filter { !it.isSuspended && it.id != currentActiveId }
                .forEach { softSuspendTab(it.id) }
            // Then hard-suspend every soft-suspended tab (close all sessions)
            tabs.filter { it.isSuspended && it.session.isOpen && it.id != currentActiveId }
                .forEach { hardSuspendTab(it.id) }
            adBlockManager.trimBlockedDomains()
            categoryNewsCache.clear()
            extensionIcons.clear()
            Log.i(TAG, "onCriticalMemory: all background tabs hard-suspended, caches cleared")
        } catch (e: Exception) {
            Log.w(TAG, "onCriticalMemory: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------

    override fun onCleared() {
        // Close every live GeckoSession before the ViewModel is destroyed so
        // Gecko content processes are not leaked across the ViewModel lifecycle.
        // Both soft-suspended (session open) and live sessions must be closed.
        tabs.forEach { tab ->
            runCatching {
                if (tab.session.isOpen) {
                    tab.session.setActive(false)
                    tab.session.close()
                }
            }
        }
        runCatching { dismissExtensionPopup() }
        runCatching { tts?.shutdown() }
        runCatching { translationManager.close() }
        runCatching { adBlockManager.shutdown() }
        runCatching { torManager.shutdown() }
        runCatching { embeddedTorManager.shutdown() }
        super.onCleared()
    }
}

