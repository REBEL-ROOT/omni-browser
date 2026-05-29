package com.omni.browser.browser

import android.content.Context
import android.net.Uri
import android.util.Log
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
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

private val Context.dataStore by preferencesDataStore(name = "omni_settings")

class BrowserViewModel : ViewModel() {

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
        private const val GRABBER_ID = "omni-media-grabber@omnibrowser.app"
        
        val UBLOCK_ENABLED_KEY = booleanPreferencesKey("ublock_adblocker_enabled")
        val UNIVERSAL_COPY_ENABLED_KEY = booleanPreferencesKey("universal_copy_enabled")
    }

    // Engine Session & Runtime
    val geckoSession = GeckoSession()
    private var geckoRuntime: GeckoRuntime? = null

    // Feature Modules
    val mediaInterceptor = MediaInterceptor()
    lateinit var ffmpegLoader: FFmpegLoader
    lateinit var ffmpegBridge: FFmpegBridge
    lateinit var streamDownloadEngine: StreamDownloadEngine
    lateinit var vpnManager: VpnManager
    val translationManager = com.omni.browser.tools.TranslationManager()
    private var copyManager: UniversalCopyManager? = null

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

    init {
        setupSessionListeners()
    }

    private fun setupSessionListeners() {
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?) {
                url?.let {
                    currentUrl = it
                    // Clear grabbed assets on new domain visits
                    mediaInterceptor.clear()
                }
            }

            override fun onCanGoBackChange(session: GeckoSession, canGoBackValue: Boolean) {
                canGoBack = canGoBackValue
            }

            override fun onCanGoForwardChange(session: GeckoSession, canGoForwardValue: Boolean) {
                canGoForward = canGoForwardValue
            }

            // GeckoView network request sniffing fallback
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                mediaInterceptor.onMediaRequestDetected(uri)
                return GeckoResult.ALLOW
            }
        }

        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                isLoading = true
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                isLoading = false
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {}
        }
    }

    fun getGeckoRuntime(context: Context): GeckoRuntime {
        if (geckoRuntime == null) {
            val appCtx = context.applicationContext
            geckoRuntime = GeckoRuntime.create(appCtx)

            // Initialize dependency engines
            ffmpegLoader = FFmpegLoader(appCtx)
            ffmpegBridge = FFmpegBridge(ffmpegLoader)
            val locker = PrivateLockerManager(appCtx)
            streamDownloadEngine = StreamDownloadEngine(appCtx, ffmpegBridge, locker)
            vpnManager = VpnManager(appCtx)
            copyManager = UniversalCopyManager(geckoRuntime!!)

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
                runtime.webExtensionController.enable(ext)
                setupNativeAppMessageDelegate(ext)
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
                runtime.webExtensionController.enable(ext)
            }
        } else {
            uBlockExtension?.let { ext ->
                runtime.webExtensionController.disable(ext)
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

    override fun onCleared() {
        super.onCleared()
        translationManager.close()
    }
}
