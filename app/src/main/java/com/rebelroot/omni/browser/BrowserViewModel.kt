package com.rebelroot.omni.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.browser.extensions.UniversalCopyManager
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
import org.json.JSONObject
import org.json.JSONArray
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "omni_settings")

data class TabState(
    val id: String,
    val session: GeckoSession,
    val title: String,
    val url: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadError: String? = null
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
        
        val UBLOCK_ENABLED_KEY = booleanPreferencesKey("ublock_adblocker_enabled")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
        val NATIVE_PLAYER_ENABLED_KEY = booleanPreferencesKey("native_player_enabled")
        val MEDIA_GRABBER_ENABLED_KEY = booleanPreferencesKey("media_grabber_enabled")

        @Volatile
        private var geckoRuntime: GeckoRuntime? = null
    }

    // Engine Session & Runtime
    var geckoSession by mutableStateOf(GeckoSession())
        private set
    var isIncognitoMode by mutableStateOf(false)
        private set
    var pendingIntentUrl: String? = null

    // Real Tab System
    val tabs = mutableStateListOf<TabState>()
    var activeTabId by mutableStateOf<String?>(null)
        private set

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
    private var appContext: Context? = null

    // UI States
    var currentUrl by mutableStateOf("about:blank")
    var isFullscreen by mutableStateOf(false)
    var isVideoPlayingInPage by mutableStateOf(false)
    var isAdblockerEnabled by mutableStateOf(true)
    var isUniversalCopyEnabled by mutableStateOf(false)
    var isMediaGrabberEnabled by mutableStateOf(true)
    var isNativePlayerEnabled by mutableStateOf(true)
    var pendingVideoUrl: String? = null

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

    // Web Video Play Takeover Lambda
    var onPlayVideoRequestReceived: ((String, String) -> Unit)? = null

    // Extensions References
    private var uBlockExtension: WebExtension? = null
    private var grabberExtension: WebExtension? = null
    
    val userExtensions = mutableStateListOf<WebExtension>()
    
    // Console Logs
    val consoleLogs = mutableStateListOf<ConsoleLogEntry>()
    
    data class ConsoleLogEntry(
        val level: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // --- Tab Management ---
    fun saveTabs() {
        val context = appContext ?: return
        val file = File(context.filesDir, "browser_tabs.json")
        try {
            val jsonArray = org.json.JSONArray()
            tabs.forEach { tab ->
                val obj = org.json.JSONObject().apply {
                    put("id", tab.id)
                    put("title", tab.title)
                    put("url", tab.url)
                    put("isActive", tab.id == activeTabId)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tabs", e)
        }
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
                        
                        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                            .usePrivateMode(isIncognitoMode)
                            .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                            .build()
                        val session = GeckoSession(settings)
                        val tab = TabState(
                            id = id,
                            session = session,
                            title = title,
                            url = url
                        )
                        setupTabSessionListeners(tab, context)
                        tabs.add(tab)
                        session.open(getGeckoRuntime(context))
                        
                        if (url != "about:blank" && url.isNotEmpty()) {
                            session.loadUri(url)
                        }
                        
                        if (isActive) {
                            activeId = id
                        }
                    }
                    if (tabs.isNotEmpty()) {
                        selectTab(activeId ?: tabs.first().id)
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
            .build()
        val session = GeckoSession(settings)
        val tabId = java.util.UUID.randomUUID().toString()
        val newTab = TabState(
            id = tabId,
            session = session,
            title = "New Tab",
            url = url
        )
        
        setupTabSessionListeners(newTab, context)
        tabs.add(newTab)
        selectTab(newTab.id)
        
        session.open(runtime)
        loadUrlInTab(newTab, url)
        saveTabs()
    }

    fun selectTab(tabId: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        activeTabId = tabId
        geckoSession = tab.session
        currentUrl = tab.url
        
        // Restore the tab's own saved navigation state
        canGoBack = tab.canGoBack
        canGoForward = tab.canGoForward

        // Clear media list when switching tabs to ensure only active tab's media is tracked
        mediaInterceptor.clear()
        isVideoPlayingInPage = false
        saveTabs()
    }

    fun closeTab(tabId: String, context: Context) {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return
        
        if (tabs.size <= 1) {
            // Keep at least one tab open, reset it to the Home screen
            val tab = tabs[0]
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(
                    url = "about:blank",
                    title = "New Tab",
                    canGoBack = false,
                    canGoForward = false,
                    loadError = null
                )
            }
            if (tab.id == activeTabId) {
                currentUrl = "about:blank"
                canGoBack = false
                canGoForward = false
            }
            try {
                tab.session.stop()
                tab.session.loadUri("about:blank")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting last tab session", e)
            }
            saveTabs()
            return
        }

        val tabToClose = tabs[tabIndex]
        try {
            tabToClose.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tab session", e)
        }
        tabs.removeAt(tabIndex)
        
        if (tabs.isEmpty()) {
            createNewTab(context, "about:blank")
        } else if (activeTabId == tabId) {
            val nextSelectIndex = if (tabIndex < tabs.size) tabIndex else tabs.size - 1
            selectTab(tabs[nextSelectIndex].id)
        }
        saveTabs()
    }

    private fun loadUrlInTab(tab: TabState, url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        if (formattedUrl.startsWith("about:")) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(url = formattedUrl, title = if (formattedUrl == "about:blank") "New Tab" else formattedUrl)
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
                "https://www.google.com/search?q=${formattedUrl.replace(" ", "+")}"
            }
        }
        val idx = tabs.indexOfFirst { it.id == tab.id }
        if (idx != -1) {
            tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...")
        }
        if (tab.id == activeTabId) {
            currentUrl = formattedUrl
        }
        tab.session.loadUri(formattedUrl)
    }

    private fun setupTabSessionListeners(tab: TabState, context: Context) {
        tab.session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                Log.d(TAG, "onAndroidPermissionsRequest: ${permissions?.joinToString()}")
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

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
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
                        tabs[idx] = tabs[idx].copy(url = it)
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
                if (tab.id == activeTabId) {
                    mediaInterceptor.onMediaRequestDetected(uri)
                }

                val lowerUri = uri.lowercase().trim()

                // 1. Intercept and block calendar spam subscription redirects from adware
                if (lowerUri.startsWith("webcal://") || lowerUri.startsWith("webcal:") || 
                    lowerUri.startsWith("calendar:") || lowerUri.endsWith(".ics") || 
                    lowerUri.contains(".ics?") || lowerUri.contains("calendar.google.com") ||
                    (lowerUri.startsWith("intent:") && (lowerUri.contains("calendar") || lowerUri.contains(".ics") || lowerUri.contains("webcal")))
                ) {
                    Log.w(TAG, "🚫 Intercepted and blocked potential spam calendar request: $uri")
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Blocked calendar spam attempt", Toast.LENGTH_SHORT).show()
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // 2. Intercept clicks to direct video files and launch native player
                if (isNativePlayerEnabled && isDirectVideoUrl(uri)) {
                    Log.i(TAG, "🎬 Intercepted direct video load request: $uri. Opening in native player...")
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val callback = onPlayVideoRequestReceived
                        if (callback != null) {
                            callback.invoke(uri, tab.url)
                        } else {
                            pendingVideoUrl = uri
                        }
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
                    // Block intent/market redirection to calendar or play store spam
                    if (lowerUri.startsWith("intent:") || lowerUri.startsWith("market:")) {
                        Log.i(TAG, "🚫 Intercepted intent/market URI: $uri")
                        
                        try {
                            val intent = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME)
                            val intentPackage = intent.getPackage()
                            
                            val isCalendarSpam = (intentPackage != null && (intentPackage.contains("calendar") || intentPackage.contains("cal"))) ||
                                    (intent.dataString != null && (intent.dataString!!.contains("calendar") || intent.dataString!!.contains("webcal") || intent.dataString!!.contains(".ics")))
                            
                            if (isCalendarSpam) {
                                Log.w(TAG, "🚫 Blocked calendar/adware intent: package=$intentPackage, data=${intent.dataString}")
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, "Blocked calendar spam intent", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.i(TAG, "Launching external app intent safely: package=$intentPackage")
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    try {
                                        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                        intent.setComponent(null)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            intent.setSelector(null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch external intent", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing intent URI", e)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    Log.w(TAG, "Denying unknown scheme load request: $uri")
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
        }

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (tab.id == activeTabId) {
                    isLoading = true
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
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {}
        }
    }

    // --- Persistent Browser History ---
    private fun loadHistory(context: Context) {
        val file = File(context.filesDir, "browser_history.json")
        if (!file.exists()) return
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
            historyList.clear()
            historyList.addAll(temp)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
        }
    }

    private fun saveHistory(context: Context) {
        val file = File(context.filesDir, "browser_history.json")
        try {
            val jsonArray = JSONArray()
            historyList.forEach { entry ->
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

    fun getGeckoRuntime(context: Context): GeckoRuntime {
        if (geckoRuntime == null) {
            val appCtx = context.applicationContext
            appContext = appCtx
            
            // Load persistent history
            loadHistory(appCtx)
            
            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .consoleOutput(true)
                .debugLogging(true)
                .remoteDebuggingEnabled(true)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .build()
            
            geckoRuntime = GeckoRuntime.create(appCtx, settings)
            
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

            // Initialize dependency engines
            ffmpegLoader = FFmpegLoader(appCtx)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ffmpegLoader.downloadAndInstall()
            }
            ffmpegBridge = FFmpegBridge(ffmpegLoader)
            val locker = PrivateLockerManager(appCtx)
            streamDownloadEngine = StreamDownloadEngine(appCtx, ffmpegBridge, locker)
            vpnManager = VpnManager(appCtx)
            copyManager = UniversalCopyManager(geckoRuntime!!)
            
            // Sync user extensions on start
            syncUserExtensions()

            // Initialize multi-tabs
            initTabs(appCtx)

            // Load saved settings
            viewModelScope.launch {
                isAdblockerEnabled = getAdblockPreference(appCtx).first()
                syncAdblockerState(shouldReload = false)
            }

            viewModelScope.launch {
                isUniversalCopyEnabled = getUniversalCopyPreference(appCtx).first()
                syncUniversalCopyState(shouldReload = false)
            }

            viewModelScope.launch {
                isNativePlayerEnabled = getNativePlayerPreference(appCtx).first()
                syncNativePlayerStateInPage()
            }

            viewModelScope.launch {
                isMediaGrabberEnabled = getMediaGrabberPreference(appCtx).first()
                syncMediaGrabberState(shouldReload = false)
            }

            // Auto load MSE Aggressive Grabber Extension on Engine initialization
            loadMediaGrabberExtension()
        }
        return geckoRuntime!!
    }


    private fun loadMediaGrabberExtension() {
        val runtime = geckoRuntime ?: return
        Log.d(TAG, "Registering MSE Aggressive Media Grabber...")
        
        // Unconditionally uninstall the old version first during startup to ensure latest assets are loaded
        runtime.webExtensionController.list().accept(
            { extensions ->
                val oldExtension = extensions?.find { it.id == GRABBER_ID }
                if (oldExtension != null) {
                    Log.d(TAG, "Uninstalling old grabber extension to reload new assets...")
                    runtime.webExtensionController.uninstall(oldExtension).accept(
                        {
                            installGrabberExtension(runtime)
                        },
                        { error ->
                            Log.e(TAG, "Failed to uninstall old grabber extension", error)
                            installGrabberExtension(runtime)
                        }
                    )
                } else {
                    installGrabberExtension(runtime)
                }
            },
            { error ->
                Log.e(TAG, "Failed to list extensions for uninstall check", error)
                installGrabberExtension(runtime)
            }
        )
    }

    private fun installGrabberExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/media_grabber/",
            GRABBER_ID
        ).accept(
            { ext ->
                grabberExtension = ext
                ext?.let {
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
                        val response = mapOf("enabled" to isNativePlayerEnabled)
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
                        if (videoUrl != null && isNativePlayerEnabled) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                Log.i(TAG, "🎬 Native player takeover starting for: $videoUrl")
                                if (onPlayVideoRequestReceived == null) {
                                    Log.e(TAG, "onPlayVideoRequestReceived is NULL! Cannot navigate to VideoPlayerScreen.")
                                } else {
                                    onPlayVideoRequestReceived?.invoke(videoUrl, pageUrl)
                                }
                            }
                        }
                    } else if (type == "VIDEO_STATE_CHANGE") {
                        val playing = if (message is org.json.JSONObject) {
                            if (message.has("isPlaying")) message.getBoolean("isPlaying") else false
                        } else {
                            (message as? Map<*, *>)?.get("isPlaying") as? Boolean ?: false
                        }
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
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
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            consoleLogs.add(ConsoleLogEntry(level, msg))
                            if (consoleLogs.size > 200) {
                                consoleLogs.removeFirst()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing grabbed media extension port message", e)
                }
                val defaultResponse = mapOf("success" to true)
                return GeckoResult.fromValue(defaultResponse)
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
                    val action = if (isAdblockerEnabled) {
                        runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    } else {
                        runtime.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    }
                    if (shouldReload) {
                        action.accept(
                            {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    reload()
                                }
                            },
                            { error ->
                                Log.e(TAG, "Failed to toggle adblocker state", error)
                            }
                        )
                    }
                }
            },
            { error ->
                Log.e(TAG, "Failed to ensure built-in adblocker", error)
            }
        )
    }

    private fun syncUniversalCopyState(shouldReload: Boolean = false) {
        copyManager?.installAndSync(isUniversalCopyEnabled, onComplete = {
            if (shouldReload) {
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
                    val action = if (isMediaGrabberEnabled) {
                        runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    } else {
                        runtime.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    }
                    if (shouldReload) {
                        action.accept(
                            {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    reload()
                                }
                            },
                            { error ->
                                Log.e(TAG, "Failed to toggle media grabber state", error)
                            }
                        )
                    }
                }
            },
            { error ->
                Log.e(TAG, "Failed to ensure built-in media grabber", error)
            }
        )
    }

    private fun syncNativePlayerStateInPage() {
        // Handled automatically via background.js polling GET_NATIVE_PLAYER_STATE
    }

    fun toggleAdblock(context: Context) {
        viewModelScope.launch {
            val newState = !isAdblockerEnabled
            isAdblockerEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[UBLOCK_ENABLED_KEY] = newState
            }
            syncAdblockerState(shouldReload = true)
        }
    }

    fun toggleUniversalCopy(context: Context) {
        viewModelScope.launch {
            val newState = !isUniversalCopyEnabled
            isUniversalCopyEnabled = newState
            context.dataStore.edit { preferences ->
                preferences[UNIVERSAL_COPY_ENABLED_KEY] = newState
            }
            syncUniversalCopyState(shouldReload = true)
        }
    }

    private fun getAdblockPreference(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[UBLOCK_ENABLED_KEY] ?: true
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

    fun toggleMediaGrabber(context: Context) {
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
        val runtime = geckoRuntime ?: return
        val currentlyEnabled = extension.metaData.enabled
        val action = if (currentlyEnabled) {
            runtime.webExtensionController.disable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
        } else {
            runtime.webExtensionController.enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
        }
        action.accept(
            {
                syncUserExtensions()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    reload()
                }
            },
            { error ->
                Log.e(TAG, "Failed to toggle user extension: ${extension.id}", error)
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

    fun disconnectVpn() {
        vpnManager.disconnect()
    }

    // --- Browser Navigation ---
    fun loadUrl(url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://") && !formattedUrl.startsWith("about:") && !formattedUrl.startsWith("javascript:")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                "https://www.google.com/search?q=${formattedUrl.replace(" ", "+")}"
            }
        }

        // Intercept direct video playback if native player is enabled
        if (isNativePlayerEnabled && isDirectVideoUrl(formattedUrl)) {
            Log.i(TAG, "🎬 Direct video URL loaded: $formattedUrl. Launching native player...")
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
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
                    tabs[idx] = tabs[idx].copy(url = formattedUrl, title = if (formattedUrl == "about:blank") "New Tab" else formattedUrl)
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
                tabs[idx] = tabs[idx].copy(url = formattedUrl, title = "Loading...")
            }
        }
        currentUrl = formattedUrl
        geckoSession.loadUri(formattedUrl)
    }

    fun goBack() {
        if (canGoBack) geckoSession.goBack()
    }

    fun goForward() {
        if (canGoForward) geckoSession.goForward()
    }

    fun reload() {
        geckoSession.reload()
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }

    fun toggleDesktopMode(context: Context) {
        isDesktopMode = !isDesktopMode
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        try {
            val mode = if (isDesktopMode) {
                org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            activeTab.session.settings.userAgentMode = mode
            activeTab.session.reload()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling desktop mode", e)
        }
    }

    fun toggleReaderMode() {
        geckoSession.loadUri("javascript:(function(){" +
                "var body = document.body;" +
                "var main = document.querySelector('main') || document.querySelector('article') || body;" +
                "var title = document.querySelector('h1')?.innerText || document.title;" +
                "var cleanHtml = '<div style=\"max-width:600px;margin:0 auto;padding:20px;font-family:sans-serif;line-height:1.6;font-size:18px;color:#111;background-color:#fefefe;\">' + " +
                "'<h1 style=\"font-size:28px;margin-bottom:20px;\">' + title + '</h1>' + " +
                "main.innerHTML + '</div>';" +
                "document.open();" +
                "document.write(cleanHtml);" +
                "document.close();" +
                "})();")
    }

    fun toggleIncognitoMode(context: Context) {
        isIncognitoMode = !isIncognitoMode
        
        val activeTab = tabs.find { it.id == activeTabId } ?: return
        val runtime = getGeckoRuntime(context)
        
        try {
            activeTab.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tab session", e)
        }

        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognitoMode)
            .build()
        
        val newSession = GeckoSession(settings)
        val updatedTab = activeTab.copy(
            session = newSession,
            url = if (isIncognitoMode) "about:blank" else activeTab.url
        )
        
        val idx = tabs.indexOf(activeTab)
        if (idx != -1) {
            tabs[idx] = updatedTab
        }
        
        setupTabSessionListeners(updatedTab, context)
        geckoSession = newSession
        currentUrl = updatedTab.url
        
        newSession.open(runtime)
        loadUrl(updatedTab.url)
        saveTabs()
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
                    val coreIds = listOf(UBLOCK_ID, GRABBER_ID, "omni-universal-copy@omnibrowser.app")
                    val filtered = list?.filter { it.id !in coreIds } ?: emptyList()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
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

    override fun onCleared() {
        super.onCleared()
        translationManager.close()
    }
}
