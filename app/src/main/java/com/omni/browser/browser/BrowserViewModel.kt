package com.omni.browser.browser

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
import com.omni.browser.browser.extensions.UniversalCopyManager
import com.omni.browser.media.FFmpegBridge
import com.omni.browser.media.FFmpegLoader
import com.omni.browser.media.MediaInterceptor
import com.omni.browser.media.StreamDownloadEngine
import com.omni.browser.privacy.VpnManager
import com.omni.browser.tools.locker.PrivateLockerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "omni_settings")

data class TabState(
    val id: String,
    val session: GeckoSession,
    val title: String,
    val url: String
)

data class HistoryEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

class BrowserViewModel : ViewModel() {

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val GRABBER_ID = "omni-media-grabber@omnibrowser.app"
        
        val UBLOCK_ENABLED_KEY = booleanPreferencesKey("ublock_adblocker_enabled")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
    }

    // Engine Session & Runtime
    var geckoSession by mutableStateOf(GeckoSession())
        private set
    var isIncognitoMode by mutableStateOf(false)
        private set
    private var geckoRuntime: GeckoRuntime? = null

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
    val translationManager = com.omni.browser.tools.TranslationManager()
    private var copyManager: UniversalCopyManager? = null
    private var appContext: Context? = null

    // UI States
    var currentUrl by mutableStateOf("https://google.com")
    var isFullscreen by mutableStateOf(false)
    var isAdblockerEnabled by mutableStateOf(true)
    var isUniversalCopyEnabled by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    // Extensions References
    private var uBlockExtension: WebExtension? = null
    private var grabberExtension: WebExtension? = null
    
    val userExtensions = mutableStateListOf<WebExtension>()

    // --- Tab Management ---
    fun initTabs(context: Context) {
        if (tabs.isEmpty()) {
            createNewTab(context, "https://google.com")
        }
    }

    fun createNewTab(context: Context, url: String) {
        val runtime = getGeckoRuntime(context)
        val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognitoMode)
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
    }

    fun selectTab(tabId: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        activeTabId = tabId
        geckoSession = tab.session
        currentUrl = tab.url
        
        // Reset/update canGoBack & canGoForward dynamically based on active tab properties
        // Wait, since compose binds to viewModel state variables, we should sync them here.
        // GeckoView session doesn't let us read history length synchronously, but we can query canGoBack/canGoForward
        // by checking if navigationDelegate had fired. But to be safe, GeckoView's navigationDelegate will fire
        // again or we can simply let Compose handle it dynamically.
    }

    fun closeTab(tabId: String, context: Context) {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return
        
        val tabToClose = tabs[tabIndex]
        try {
            tabToClose.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tab session", e)
        }
        tabs.removeAt(tabIndex)
        
        if (tabs.isEmpty()) {
            createNewTab(context, "https://google.com")
        } else if (activeTabId == tabId) {
            val nextSelectIndex = if (tabIndex < tabs.size) tabIndex else tabs.size - 1
            selectTab(tabs[nextSelectIndex].id)
        }
    }

    private fun loadUrlInTab(tab: TabState, url: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                "https://www.google.com/search?q=${formattedUrl.replace(" ", "+")}"
            }
        }
        tab.session.loadUri(formattedUrl)
    }

    private fun setupTabSessionListeners(tab: TabState, context: Context) {
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
                    }
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
                        tabs[idx] = tabs[idx].copy(url = it)
                    }
                    if (tab.id == activeTabId) {
                        currentUrl = it
                        mediaInterceptor.clear()
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBackValue: Boolean) {
                if (tab.id == activeTabId) {
                    canGoBack = canGoBackValue
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForwardValue: Boolean) {
                if (tab.id == activeTabId) {
                    canGoForward = canGoForwardValue
                }
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (tab.id == activeTabId) {
                    mediaInterceptor.onMediaRequestDetected(uri)
                }
                
                if (uri.endsWith(".xpi") || uri.contains("/firefox/downloads/file/")) {
                    Log.d(TAG, "Intercepted addon install click: $uri")
                    installExtensionFromUrl(uri, context)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (tab.id == activeTabId) {
                    isLoading = true
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
            
            geckoRuntime = GeckoRuntime.create(appCtx)
            
            // Set up extension prompt delegate for native third-party installations
            geckoRuntime!!.webExtensionController.setPromptDelegate(object : org.mozilla.geckoview.WebExtensionController.PromptDelegate {
                override fun onInstallPrompt(
                    extension: org.mozilla.geckoview.WebExtension
                ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                    Log.d(TAG, "Auto-approving install prompt for extension: ${extension.id}")
                    return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                }
            })

            // Initialize dependency engines
            ffmpegLoader = FFmpegLoader(appCtx)
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
                getAdblockPreference(appCtx).collect { isEnabled ->
                    isAdblockerEnabled = isEnabled
                    syncAdblockerState()
                }
            }

            viewModelScope.launch {
                getUniversalCopyPreference(appCtx).collect { isEnabled ->
                    isUniversalCopyEnabled = isEnabled
                    syncUniversalCopyState()
                }
            }

            // Auto load MSE Aggressive Grabber Extension on Engine initialization
            loadMediaGrabberExtension()
        }
        return geckoRuntime!!
    }


    private fun loadMediaGrabberExtension() {
        val runtime = geckoRuntime ?: return
        Log.d(TAG, "Registering MSE Aggressive Media Grabber...")
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/media_grabber/",
            GRABBER_ID
        ).accept(
            { ext ->
                grabberExtension = ext
                ext?.let {
                    runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
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
                try {
                    // message structure typically parsed as Map under GeckoView SDK interop
                    val msgMap = message as? Map<*, *>
                    val type = msgMap?.get("type") as? String
                    if (type == "MEDIA_GRABBED") {
                        val url = msgMap["url"] as? String
                        val mime = msgMap["mimeType"] as? String
                        if (url != null) {
                            mediaInterceptor.onAggressiveMediaGrabbed(url, mime ?: "video/mp4")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing grabbed media extension port message", e)
                }
                return null
            }
        }, "omniApp")
    }

    private fun syncAdblockerState() {
        val runtime = geckoRuntime ?: return
        if (isAdblockerEnabled) {
            runtime.webExtensionController.ensureBuiltIn(
                "resource://android/assets/web_extensions/ublock/",
                UBLOCK_ID
            ).accept { ext ->
                uBlockExtension = ext
                ext?.let {
                    runtime.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                }
            }
        } else {
            uBlockExtension?.let { ext ->
                runtime.webExtensionController.disable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
            }
        }
    }

    private fun syncUniversalCopyState() {
        copyManager?.installAndSync(isUniversalCopyEnabled)
    }

    fun toggleAdblock(context: Context) {
        viewModelScope.launch {
            val newState = !isAdblockerEnabled
            context.dataStore.edit { preferences ->
                preferences[UBLOCK_ENABLED_KEY] = newState
            }
        }
    }

    fun toggleUniversalCopy(context: Context) {
        viewModelScope.launch {
            val newState = !isUniversalCopyEnabled
            context.dataStore.edit { preferences ->
                preferences[UNIVERSAL_COPY_ENABLED_KEY] = newState
            }
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

        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                "https://www.google.com/search?q=${formattedUrl.replace(" ", "+")}"
            }
        }
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
