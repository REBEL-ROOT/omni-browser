package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG

fun BrowserViewModel.registerExtensionAction(id: String, session: GeckoSession?, action: WebExtension.Action) {
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

fun BrowserViewModel.getActionForExtension(extensionId: String): WebExtension.Action? {
    val activeId = activeTabId ?: return defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
    return sessionExtensionActions[activeId]?.get(extensionId) ?: defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
}

fun BrowserViewModel.handleExtensionOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
    val result = GeckoResult<GeckoSession>()
    if (isIncognitoMode) {
        // Block extension popups in incognito if they are not explicitly allowed or for security
    }
    if (isNativeSheetOpen) {
        result.completeExceptionally(IllegalStateException("Blocked: Native toolbox/notes sheet is active."))
        return result
    }
    var completed = false
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            val run = runtime
            if (run == null) {
                completed = true
                result.completeExceptionally(IllegalStateException("GeckoRuntime not ready"))
                return@post
            }
            activeExtensionPopupSession?.close() // close previous popup session to avoid leaks

            // Use mobile viewport — desktop mode renders at ~1280px causing tiny popups on phones
            val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                .allowJavascript(true)
                .userAgentMode(org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build()

            val session = GeckoSession(settings)

            // Show spinner while the popup page loads
            activeExtensionPopupLoading = true

            // Content delegate — dismiss popup if the extension page closes itself
            session.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onCloseRequest(session: GeckoSession) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dismissExtensionPopup()
                    }
                }
            }

            // Progress delegate — inject auto-fit CSS once page finishes loading
            session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    // Inject CSS + viewport meta so the extension popup fills the phone screen.
                    val js = """
                        (function(){
                            try {
                                // Set/update viewport meta for device-width scaling
                                var vp = document.querySelector('meta[name="viewport"]');
                                if (!vp) {
                                    vp = document.createElement('meta');
                                    vp.name = 'viewport';
                                    document.head.appendChild(vp);
                                }
                                vp.content = 'width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes';

                                // Strip desktop min-width constraints so the popup fills available width
                                var style = document.createElement('style');
                                style.id = '_omni_ext_fit';
                                style.textContent = [
                                    'html { min-width: unset !important; width: 100% !important; box-sizing: border-box !important; }',
                                    'body { min-width: unset !important; width: 100% !important; max-width: 100vw !important; box-sizing: border-box !important; overflow-x: hidden !important; }',
                                    '* { max-width: 100% !important; }'
                                ].join('\n');
                                var old = document.getElementById('_omni_ext_fit');
                                if (old) old.remove();
                                document.head.appendChild(style);
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    try { session.loadUri("javascript:$js") } catch (_: Exception) {}

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        activeExtensionPopupLoading = false
                    }
                }
                override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {}
            }

            // Navigation delegate — allow extension-internal navigation (moz-extension:// links)
            session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {}
                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {}
                override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {}
                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny>? {
                    val url = request.uri ?: return null
                    return when {
                        // Allow all moz-extension:// and about: pages
                        url.startsWith("moz-extension://") || url.startsWith("about:") -> null
                        // Intercept external http(s) links — open in main browser
                        url.startsWith("http://") || url.startsWith("https://") -> {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                dismissExtensionPopup()
                                loadUrl(url)
                            }
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        else -> null
                    }
                }
            }

            session.open(run)
            activeExtensionPopupSession = session
            activeExtensionPopupName = try { extension.metaData?.name ?: extension.id } catch (_: Exception) { extension.id }
            completed = true
            result.complete(session)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open popup for ${extension.id}", e)
            if (!completed) {
                completed = true
                result.completeExceptionally(e)
            }
        }
    }
    return result
}

fun BrowserViewModel.dismissExtensionPopup() {
    activeExtensionPopupSession?.close()
    activeExtensionPopupSession = null
    activeExtensionPopupName = ""
    activeExtensionPopupLoading = true
}

internal fun BrowserViewModel.refreshAndLoadBuiltInExtensions(context: Context) {
    Log.d(TAG, "Refreshing and loading built-in extensions...")
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        loadExtensionsClean(context)
    }
}

internal fun BrowserViewModel.loadExtensionsClean(context: Context) {
    val run = runtime ?: return
    viewModelScope.launch {
        isMediaGrabberEnabled = getMediaGrabberPreference(context).first()
        installGrabberExtension(run)
        
        isUniversalCopyEnabled = getUniversalCopyPreference(context).first()
        syncUniversalCopyState(shouldReload = false)
        
        isAiBlockerEnabled = getAiBlockerPreference(context).first()
        aiBlockerManager?.installAndSync(isAiBlockerEnabled, onComplete = null)
    }
}

internal fun BrowserViewModel.installGrabberExtension(runtime: GeckoRuntime) {
    runtime.webExtensionController.ensureBuiltIn(
        "resource://android/assets/web_extensions/media_grabber/",
        BrowserViewModel.GRABBER_ID
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

internal fun BrowserViewModel.setupNativeAppMessageDelegate(extension: WebExtension) {
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
                } else if (type == "INNER_SCROLL_STATE") {
                    val isScrolled = if (message is org.json.JSONObject) {
                        if (message.has("isScrolled")) message.getBoolean("isScrolled") else false
                    } else {
                        (message as? Map<*, *>)?.get("isScrolled") as? Boolean ?: false
                    }
                    viewModelScope.launch(Dispatchers.Main) {
                        isInnerScrolled = isScrolled
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
                            consoleLogs.add(BrowserViewModel.ConsoleLogEntry(level, msg))
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

internal fun BrowserViewModel.syncUniversalCopyState(shouldReload: Boolean = false) {
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

internal fun BrowserViewModel.syncMediaGrabberState(shouldReload: Boolean = false) {
    val run = runtime ?: return
    run.webExtensionController.ensureBuiltIn(
        "resource://android/assets/web_extensions/media_grabber/",
        BrowserViewModel.GRABBER_ID
    ).accept(
        { ext ->
            grabberExtension = ext
            ext?.let {
                run.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                val action = if (isMediaGrabberEnabled) {
                    val enableResult = run.webExtensionController.enable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    setupNativeAppMessageDelegate(it)
                    enableResult
                } else {
                    run.webExtensionController.disable(it, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
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

fun BrowserViewModel.togglePopupBlocker(context: Context) {
    viewModelScope.launch {
        val newState = !isPopupBlockerEnabled
        isPopupBlockerEnabled = newState
        context.dataStore.edit { preferences ->
            preferences[BrowserViewModel.POPUP_BLOCKER_ENABLED_KEY] = newState
        }
    }
}

fun BrowserViewModel.toggleUniversalCopy(context: Context) {
    if (isUniversalCopyToggling) return
    isUniversalCopyToggling = true
    viewModelScope.launch {
        val newState = !isUniversalCopyEnabled
        isUniversalCopyEnabled = newState
        context.dataStore.edit { preferences ->
            preferences[BrowserViewModel.UNIVERSAL_COPY_ENABLED_KEY] = newState
        }
        syncUniversalCopyState(shouldReload = true)
    }
}

fun BrowserViewModel.uninstallUniversalCopy(context: Context) {
    if (isUniversalCopyToggling) return
    isUniversalCopyToggling = true
    viewModelScope.launch {
        isUniversalCopyEnabled = false
        context.dataStore.edit { preferences ->
            preferences[BrowserViewModel.UNIVERSAL_COPY_ENABLED_KEY] = false
        }
        copyManager?.uninstall(onComplete = {
            isUniversalCopyToggling = false
            currentSettingsVersion++
            reload()
        })
    }
}

fun BrowserViewModel.uninstallAiBlocker(context: Context) {
    if (isAiBlockerToggling) return
    isAiBlockerToggling = true
    viewModelScope.launch {
        isAiBlockerEnabled = false
        context.dataStore.edit { preferences ->
            preferences[BrowserViewModel.AI_BLOCKER_ENABLED_KEY] = false
        }
        aiBlockerManager?.uninstall(onComplete = {
            isAiBlockerToggling = false
            currentSettingsVersion++
            reload()
        })
    }
}

fun BrowserViewModel.toggleAiBlocker(context: Context) {
    if (isAiBlockerToggling) return
    isAiBlockerToggling = true
    viewModelScope.launch {
        val newState = !isAiBlockerEnabled
        isAiBlockerEnabled = newState
        context.dataStore.edit { preferences ->
            preferences[BrowserViewModel.AI_BLOCKER_ENABLED_KEY] = newState
        }
        syncAiBlockerState(shouldReload = true)
    }
}

internal fun BrowserViewModel.syncAiBlockerState(shouldReload: Boolean = false) {
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

internal fun BrowserViewModel.getAiBlockerPreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.AI_BLOCKER_ENABLED_KEY] ?: false
    }
}

internal fun BrowserViewModel.getPopupBlockerPreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.POPUP_BLOCKER_ENABLED_KEY] ?: true  // Default ON
    }
}

internal fun BrowserViewModel.getUniversalCopyPreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.UNIVERSAL_COPY_ENABLED_KEY] ?: false
    }
}

internal fun BrowserViewModel.getNativePlayerPreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.NATIVE_PLAYER_ENABLED_KEY] ?: true // Default ON
    }
}

internal fun BrowserViewModel.getMediaGrabberPreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.MEDIA_GRABBER_ENABLED_KEY] ?: true // Default ON
    }
}

internal fun BrowserViewModel.getYouTubePreference(context: Context): Flow<Boolean> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.YOUTUBE_ENABLED_KEY] ?: false // Default OFF
    }
}
