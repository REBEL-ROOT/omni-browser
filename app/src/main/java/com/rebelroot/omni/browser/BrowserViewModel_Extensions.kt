package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.media.handoff.MediaHandoff
import com.rebelroot.omni.media.handoff.MediaSourceClassifier
import com.rebelroot.omni.media.handoff.MediaSourceType
import com.rebelroot.omni.media.handoff.WebVideoSession
import com.rebelroot.omni.media.handoff.WebVideoSessionState
import com.rebelroot.omni.media.handoff.WebVideoSourceResolver

internal val extensionTabIdToOmniTabId = java.util.concurrent.ConcurrentHashMap<String, String>()
internal val omniTabIdToExtensionTabId = java.util.concurrent.ConcurrentHashMap<String, String>()

internal fun BrowserViewModel.resolveOmniTabId(
    extensionTabId: String?,
    pageUrl: String?,
    senderSession: GeckoSession? = null
): String? {
    if (senderSession != null) {
        val tab = tabs.find { it.session === senderSession }
        if (tab != null) {
            if (!extensionTabId.isNullOrEmpty()) {
                extensionTabIdToOmniTabId[extensionTabId] = tab.id
                omniTabIdToExtensionTabId[tab.id] = extensionTabId
            }
            return tab.id
        }
    }

    if (!extensionTabId.isNullOrEmpty()) {
        val mapped = extensionTabIdToOmniTabId[extensionTabId]
        if (mapped != null && tabs.any { it.id == mapped }) {
            return mapped
        }
    }

    if (!pageUrl.isNullOrEmpty()) {
        val cleanPageUrl = pageUrl.substringBefore("#")
        val tab = tabs.find { it.url.substringBefore("#") == cleanPageUrl }
        if (tab != null) {
            if (!extensionTabId.isNullOrEmpty()) {
                extensionTabIdToOmniTabId[extensionTabId] = tab.id
                omniTabIdToExtensionTabId[tab.id] = extensionTabId
            }
            return tab.id
        }
    }

    val active = activeTabId
    if (active != null && !extensionTabId.isNullOrEmpty()) {
        extensionTabIdToOmniTabId[extensionTabId] = active
        omniTabIdToExtensionTabId[active] = extensionTabId
    }
    return active
}

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

        // Maximum size for extension JSON messages to prevent DoS via memory exhaustion
        private val MAX_MESSAGE_STRING_LENGTH = 1_000_000 // 1 MB

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            Log.d(TAG, "🎬 onMessage called! nativeApp = $nativeApp, messageType = ${message.javaClass.name}")

            // Reject oversized messages to prevent memory DoS
            val messageString = message.toString()
            if (messageString.length > MAX_MESSAGE_STRING_LENGTH) {
                Log.w(TAG, "🛡️ Rejected oversized extension message (${messageString.length} chars) from $nativeApp")
                return null
            }

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
                } else if (type == "REQUEST_HANDOFF") {
                    handleRequestHandoff(message, sender)
                } else if (type == "REQUEST_DOWNLOAD") {
                    handleRequestDownload(message, sender)
                } else if (type == "HANDOFF_RESTORED") {
                    handleHandoffRestored(message)
                } else if (type == "PLAY_IN_NATIVE") {
                    // Legacy fallback — kept for backward compatibility with older inject.js
                    handleLegacyPlayInNative(message)
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

internal fun BrowserViewModel.getMediaSnifferBlocklistPreference(context: Context): Flow<Set<String>> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.MEDIA_SNIFFER_BLOCKLIST_KEY] ?: emptySet()
    }
}

internal fun BrowserViewModel.getMediaSnifferMinDurationSecPreference(context: Context): Flow<Int> {
    return context.dataStore.data.map { preferences ->
        preferences[BrowserViewModel.MEDIA_SNIFFER_MIN_DURATION_SEC_KEY] ?: 0
    }
}

// ── Media Handoff & Quetta-Style Video Session Handlers ───────────────────

/**
 * Handles the REQUEST_HANDOFF message from the JS extension.
 * Creates an authoritative WebVideoSession, classifies the source, and either:
 *   (a) Accepts: sends HANDOFF_ACCEPTED and PAUSE_AND_LAUNCH to JS, stores session, launches native player
 *   (b) Rejects: sends HANDOFF_REJECTED and RESUME_WEBSITE to JS, leaves webpage playing
 */
private fun BrowserViewModel.handleRequestHandoff(message: Any, sender: WebExtension.MessageSender? = null) {
    val videoUrl = if (message is org.json.JSONObject) {
        if (message.has("url")) message.getString("url") else null
    } else {
        (message as? Map<*, *>)?.get("url") as? String
    } ?: ""

    val pageUrl = (if (message is org.json.JSONObject) {
        if (message.has("pageUrl")) message.getString("pageUrl") else null
    } else {
        (message as? Map<*, *>)?.get("pageUrl") as? String
    }) ?: currentUrl

    val rawTabId = (if (message is org.json.JSONObject) {
        if (message.has("tabId")) message.getString("tabId") else null
    } else {
        (message as? Map<*, *>)?.get("tabId") as? String
    }) ?: ""

    val omniTabId = resolveOmniTabId(rawTabId, pageUrl, sender?.session) ?: activeTabId ?: ""

    val handoffJson = if (message is org.json.JSONObject) {
        if (message.has("handoff")) message.getJSONObject("handoff") else null
    } else {
        null
    }

    val associatedStreams = mutableListOf<String>()
    if (message is org.json.JSONObject && message.has("associatedStreams")) {
        val arr = message.getJSONArray("associatedStreams")
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty()) associatedStreams.add(s)
        }
    } else if (handoffJson != null && handoffJson.has("associatedStreams")) {
        val arr = handoffJson.getJSONArray("associatedStreams")
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty()) associatedStreams.add(s)
        }
    }

    Log.i(TAG, "🎬 REQUEST_HANDOFF received: videoUrl=$videoUrl, pageUrl=$pageUrl, omniTabId=$omniTabId (extTab=$rawTabId), streams=${associatedStreams.size}")

    // Parse authoritative WebVideoSession from JSON if present
    val rawSession = if (handoffJson != null) {
        try {
            WebVideoSession.fromJson(handoffJson).copy(tabId = omniTabId)
        } catch (e: Exception) {
            Log.e(TAG, "🎬 Failed to parse WebVideoSession from JSON", e)
            null
        }
    } else null

    val baseSession = rawSession ?: WebVideoSession(
        sessionId = "h_" + System.currentTimeMillis(),
        tabId = omniTabId,
        videoElementId = "omni_vid_handoff",
        sourceUri = videoUrl,
        pageUrl = pageUrl,
        mimeType = null,
        sourceType = MediaSourceType.UNKNOWN,
        cookies = activeVideoCookies
    )

    // Filter detected media strictly scoped to this tab / page
    val tabMedia = mediaInterceptor.detectedMedia.value.filter { item ->
        item.pageId == omniTabId || item.pageId == rawTabId ||
        (item.referrer != null && item.referrer.substringBefore("#") == pageUrl.substringBefore("#")) ||
        (item.url.isNotEmpty() && !item.url.startsWith("blob:"))
    }

    val resolution = WebVideoSourceResolver.resolve(
        session = baseSession,
        associatedStreams = associatedStreams,
        tabDetectedMedia = tabMedia
    )

    when (resolution) {
        is WebVideoSourceResolver.ResolutionResult.Success -> {
            val finalSession = baseSession.copy(
                sourceUri = resolution.resolvedUri,
                mimeType = resolution.mimeType,
                sourceType = resolution.sourceType,
                cookies = resolution.cookies ?: baseSession.cookies,
                headers = resolution.headers.ifEmpty { baseSession.headers },
                referrer = resolution.referrer ?: baseSession.referrer,
                origin = resolution.origin ?: baseSession.origin
            )

            finalSession.state = WebVideoSessionState.HANDOFF_TO_NATIVE
            activeVideoSession = finalSession
            pendingHandoff = finalSession.toMediaHandoff()
            if (!resolution.cookies.isNullOrEmpty()) {
                activeVideoCookies = resolution.cookies
            }

            Log.i(TAG, "🎬 Handoff accepted — pausing webpage video and launching native player for ${resolution.resolvedUri} at ${finalSession.currentPositionMs}ms")

            // Send explicit structured HANDOFF_ACCEPTED and PAUSE_AND_LAUNCH
            sendJsMessage(
                "HANDOFF_ACCEPTED",
                "{\"sessionId\":\"${finalSession.sessionId}\",\"videoId\":\"${finalSession.videoElementId}\",\"tabId\":\"$omniTabId\",\"url\":\"${finalSession.sourceUri}\"}",
                omniTabId
            )
            sendJsMessage(
                "PAUSE_AND_LAUNCH",
                "{\"handoffId\":\"${finalSession.sessionId}\",\"sessionId\":\"${finalSession.sessionId}\",\"videoId\":\"${finalSession.videoElementId}\"}",
                omniTabId
            )

            viewModelScope.launch(Dispatchers.Main) {
                if (onPlayVideoRequestReceived == null) {
                    Log.e(TAG, "onPlayVideoRequestReceived is NULL! Cannot navigate to VideoPlayerScreen.")
                } else {
                    onPlayVideoRequestReceived?.invoke(finalSession.sourceUri, pageUrl)
                }
            }
        }
        is WebVideoSourceResolver.ResolutionResult.Unsupported -> {
            Log.w(TAG, "🎬 Handoff rejected — ${resolution.reason}")
            sendJsMessage(
                "HANDOFF_REJECTED",
                "{\"sessionId\":\"${baseSession.sessionId}\",\"videoId\":\"${baseSession.videoElementId}\",\"reason\":\"${resolution.reason}\"}",
                omniTabId
            )
            sendJsMessage(
                "RESUME_WEBSITE",
                "{\"sessionId\":\"${baseSession.sessionId}\",\"videoId\":\"${baseSession.videoElementId}\"}",
                omniTabId
            )
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Native playback is unavailable for this video format", Toast.LENGTH_SHORT).show()
            }
        }
        is WebVideoSourceResolver.ResolutionResult.UnresolvedBlob,
        is WebVideoSourceResolver.ResolutionResult.NoMediaFound -> {
            val msg = if (resolution is WebVideoSourceResolver.ResolutionResult.UnresolvedBlob) resolution.message else "No media stream found"
            Log.w(TAG, "🎬 Handoff rejected — $msg")
            sendJsMessage(
                "HANDOFF_REJECTED",
                "{\"sessionId\":\"${baseSession.sessionId}\",\"videoId\":\"${baseSession.videoElementId}\",\"reason\":\"$msg\"}",
                omniTabId
            )
            sendJsMessage(
                "RESUME_WEBSITE",
                "{\"sessionId\":\"${baseSession.sessionId}\",\"videoId\":\"${baseSession.videoElementId}\"}",
                omniTabId
            )
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Native playback is unavailable for this video", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Handles REQUEST_DOWNLOAD directly from the Quetta overlay without opening the player.
 */
private fun BrowserViewModel.handleRequestDownload(message: Any, sender: WebExtension.MessageSender? = null) {
    val rawUrl = if (message is org.json.JSONObject) {
        if (message.has("url")) message.getString("url") else null
    } else {
        (message as? Map<*, *>)?.get("url") as? String
    } ?: ""

    val pageUrl = (if (message is org.json.JSONObject) {
        if (message.has("pageUrl")) message.getString("pageUrl") else null
    } else {
        (message as? Map<*, *>)?.get("pageUrl") as? String
    }) ?: currentUrl

    val rawTabId = (if (message is org.json.JSONObject) {
        if (message.has("tabId")) message.getString("tabId") else null
    } else {
        (message as? Map<*, *>)?.get("tabId") as? String
    }) ?: ""

    val omniTabId = resolveOmniTabId(rawTabId, pageUrl, sender?.session) ?: activeTabId ?: ""

    val videoId = (if (message is org.json.JSONObject) {
        if (message.has("videoId")) message.getString("videoId") else null
    } else {
        (message as? Map<*, *>)?.get("videoId") as? String
    }) ?: "vid_${System.currentTimeMillis()}"

    val requestId = (if (message is org.json.JSONObject) {
        if (message.has("requestId")) message.getString("requestId") else null
    } else {
        (message as? Map<*, *>)?.get("requestId") as? String
    }) ?: "dl_${System.currentTimeMillis()}"

    val mimeType = (if (message is org.json.JSONObject) {
        if (message.has("mimeType")) message.getString("mimeType") else null
    } else {
        (message as? Map<*, *>)?.get("mimeType") as? String
    }) ?: "video/mp4"

    val title = (if (message is org.json.JSONObject) {
        if (message.has("title")) message.getString("title") else null
    } else {
        (message as? Map<*, *>)?.get("title") as? String
    }) ?: "video_${System.currentTimeMillis()}"

    val cookies = if (message is org.json.JSONObject) {
        if (message.has("cookies")) message.getString("cookies") else null
    } else {
        (message as? Map<*, *>)?.get("cookies") as? String
    }

    val associatedStreams = mutableListOf<String>()
    if (message is org.json.JSONObject && message.has("associatedStreams")) {
        val arr = message.getJSONArray("associatedStreams")
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty()) associatedStreams.add(s)
        }
    }

    val tempSession = WebVideoSession(
        sessionId = requestId,
        tabId = omniTabId,
        videoElementId = videoId,
        sourceUri = rawUrl,
        pageUrl = pageUrl,
        mimeType = mimeType,
        sourceType = MediaSourceClassifier.classify(rawUrl, mimeType),
        cookies = cookies ?: activeVideoCookies
    )

    val tabMedia = mediaInterceptor.detectedMedia.value.filter { item ->
        item.pageId == omniTabId || item.pageId == rawTabId ||
        (item.referrer != null && item.referrer.substringBefore("#") == pageUrl.substringBefore("#")) ||
        (item.url.isNotEmpty() && !item.url.startsWith("blob:"))
    }

    val resolution = WebVideoSourceResolver.resolve(
        session = tempSession,
        associatedStreams = associatedStreams,
        tabDetectedMedia = tabMedia
    )

    when (resolution) {
        is WebVideoSourceResolver.ResolutionResult.Success -> {
            val effectiveUrl = resolution.resolvedUri
            val mediaType = when (resolution.sourceType) {
                MediaSourceType.HLS -> MediaInterceptor.MediaType.HLS
                MediaSourceType.DASH -> MediaInterceptor.MediaType.DASH
                MediaSourceType.PROGRESSIVE_WEBM -> MediaInterceptor.MediaType.WEBM
                MediaSourceType.AUDIO_STREAM -> MediaInterceptor.MediaType.AUDIO
                else -> MediaInterceptor.MediaType.MP4
            }

            Log.i(TAG, "📥 Download accepted: requestId=$requestId, url=$effectiveUrl, type=$mediaType")
            sendJsMessage(
                "DOWNLOAD_STARTED",
                "{\"requestId\":\"$requestId\",\"videoId\":\"$videoId\",\"url\":\"$effectiveUrl\"}",
                omniTabId
            )

            viewModelScope.launch(Dispatchers.Main) {
                try {
                    val suggestedName = if (title.isNotBlank() && title != "Video") {
                        val clean = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                        val ext = when (mediaType) {
                            MediaInterceptor.MediaType.HLS -> ".mp4"
                            MediaInterceptor.MediaType.DASH -> ".mp4"
                            MediaInterceptor.MediaType.WEBM -> ".webm"
                            MediaInterceptor.MediaType.AUDIO -> ".mp3"
                            MediaInterceptor.MediaType.MP4 -> ".mp4"
                        }
                        if (clean.endsWith(ext, ignoreCase = true)) clean else "$clean$ext"
                    } else {
                        "download_${System.currentTimeMillis()}.mp4"
                    }

                    streamDownloadEngine.startDownload(
                        url = effectiveUrl,
                        suggestedName = suggestedName,
                        type = mediaType,
                        saveToLocker = false,
                        referrerUrl = resolution.referrer ?: pageUrl,
                        cookies = resolution.cookies ?: cookies ?: activeVideoCookies,
                        audioUrl = null
                    )
                    Toast.makeText(appContext, "Download started: $suggestedName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start download for $effectiveUrl", e)
                    Toast.makeText(appContext, "Failed to start download", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else -> {
            Log.w(TAG, "📥 Download rejected — unresolved or unsupported media source")
            sendJsMessage(
                "DOWNLOAD_REJECTED",
                "{\"requestId\":\"$requestId\",\"videoId\":\"$videoId\",\"reason\":\"Media stream unavailable for download\"}",
                omniTabId
            )
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Media stream is unavailable for download", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Handles confirmation from the webpage video that it restored playback state.
 */
private fun BrowserViewModel.handleHandoffRestored(message: Any) {
    val sessionId = if (message is org.json.JSONObject) {
        if (message.has("sessionId")) message.getString("sessionId") else null
    } else {
        (message as? Map<*, *>)?.get("sessionId") as? String
    }
    val videoId = if (message is org.json.JSONObject) {
        if (message.has("videoId")) message.getString("videoId") else null
    } else {
        (message as? Map<*, *>)?.get("videoId") as? String
    }
    val currentTimeMs = if (message is org.json.JSONObject) {
        if (message.has("currentTimeMs")) message.optLong("currentTimeMs", 0L) else 0L
    } else {
        (message as? Map<*, *>)?.get("currentTimeMs") as? Long ?: 0L
    }
    val isPlaying = if (message is org.json.JSONObject) {
        if (message.has("isPlaying")) message.optBoolean("isPlaying", false) else false
    } else {
        (message as? Map<*, *>)?.get("isPlaying") as? Boolean ?: false
    }

    Log.i(TAG, "🎬 HANDOFF_RESTORED confirmed by webpage video $videoId at ${currentTimeMs}ms, isPlaying=$isPlaying, session=$sessionId")
    activeVideoSession?.state = WebVideoSessionState.RELEASED
    activeVideoSession = null
    pendingHandoff = null
}

/**
 * Legacy handler for old PLAY_IN_NATIVE messages (backward compatibility).
 */
private fun BrowserViewModel.handleLegacyPlayInNative(message: Any) {
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

    Log.i(TAG, "🎬 Legacy PLAY_IN_NATIVE received. url=$videoUrl, pageUrl=$pageUrl")
    val isYouTube = pageUrl.lowercase().contains("youtube.com") || pageUrl.lowercase().contains("youtu.be") ||
        (videoUrl != null && (videoUrl.lowercase().contains("youtube.com") || videoUrl.lowercase().contains("youtu.be")))
    if (videoUrl != null && isNativePlayerEnabled && (!isYouTube || isYouTubeEnabled)) {
        viewModelScope.launch(Dispatchers.Main) {
            onPlayVideoRequestReceived?.invoke(videoUrl, pageUrl)
        }
    }
}

/**
 * Parses a MediaHandoff from a JSON object received from the JS extension.
 */
private fun parseMediaHandoff(json: org.json.JSONObject): MediaHandoff {
    val handoffId = json.optString("handoffId", "")
    val tabId = json.optString("tabId", "")
    val videoElementId = json.optString("videoId", json.optString("videoElementId", ""))
    val sourceUri = json.optString("sourceUri", "")
    val pageUrl = json.optString("pageUrl", "")
    val title = json.optString("title", "").takeIf { it.isNotEmpty() }
    val currentPositionMs = json.optLong("currentPositionMs", 0L)
    val durationMs = if (json.has("durationMs") && !json.isNull("durationMs")) json.optLong("durationMs", -1L).takeIf { it >= 0 } else null
    val isPaused = json.optBoolean("isPaused", false)
    val playbackRate = json.optDouble("playbackRate", 1.0).toFloat()
    val volume = json.optDouble("volume", 1.0).toFloat()
    val muted = json.optBoolean("muted", false)
    val mimeType = json.optString("mimeType", "").takeIf { it.isNotEmpty() }
    val capturedAt = json.optLong("capturedAt", 0L)
    val videoWidth = json.optInt("videoWidth", 0)
    val videoHeight = json.optInt("videoHeight", 0)
    val poster = json.optString("poster", "").takeIf { it.isNotEmpty() }
    val cookies = json.optString("cookies", "").takeIf { it.isNotEmpty() }
    val referrer = json.optString("referrer", "").takeIf { it.isNotEmpty() }
    val origin = json.optString("origin", "").takeIf { it.isNotEmpty() }

    val sourceType = MediaSourceClassifier.classify(sourceUri, mimeType)

    return MediaHandoff(
        handoffId = handoffId,
        tabId = tabId,
        videoElementId = videoElementId,
        sourceUri = sourceUri,
        pageUrl = pageUrl,
        title = title,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        isPaused = isPaused,
        playbackRate = playbackRate,
        volume = volume,
        muted = muted,
        mimeType = mimeType,
        sourceType = sourceType,
        capturedAt = capturedAt,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        poster = poster,
        cookies = cookies,
        referrer = referrer,
        origin = origin
    )
}

/**
 * Sends a message to the JS extension via the native app message port.
 * Can target a specific tab or default to the active tab.
 */
internal fun BrowserViewModel.sendJsMessage(type: String, payload: String, targetTabId: String? = null) {
    val tab = if (!targetTabId.isNullOrEmpty()) {
        tabs.find { it.id == targetTabId }
            ?: extensionTabIdToOmniTabId[targetTabId]?.let { mappedId -> tabs.find { it.id == mappedId } }
            ?: tabs.find { it.id == activeTabId }
    } else {
        tabs.find { it.id == activeTabId }
    }
    val session = tab?.session ?: return

    try {
        val js = "window.postMessage({ type: '$type', payload: $payload }, '*');"
        session.loadUri("javascript:$js")
        Log.d(TAG, "📤 Sent JS message: type=$type to tabId=${tab.id}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send JS message: $type to tabId=${tab.id}", e)
    }
}
