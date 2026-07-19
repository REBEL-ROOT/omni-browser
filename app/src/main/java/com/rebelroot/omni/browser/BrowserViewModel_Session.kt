package com.rebelroot.omni.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

internal fun BrowserViewModel.applyUserAgentForTab(tab: TabState) {
    val ua = if (isDesktopMode) {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    } else {
        "Mozilla/5.0 (Android 13; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    }
    tab.session.settings.setUserAgentOverride(ua)
}

internal fun BrowserViewModel.setupTabSessionListeners(tab: TabState, context: Context) {
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
            pendingFilePrompt = BrowserViewModel.PendingFilePrompt(
                geckoResult = result,
                prompt = prompt,
                allowMultiple = allowMultiple,
                mimeTypes = mimeTypes
            )
            return result
        }

        override fun onLoginSave(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            if (tab.id != activeTabId) return GeckoResult.fromValue(prompt.dismiss())
            val entry = prompt.options.firstOrNull()?.value ?: return GeckoResult.fromValue(prompt.dismiss())
            val host = try {
                val originHost = java.net.URI(entry.origin ?: "").host
                if (!originHost.isNullOrBlank()) {
                    originHost.removePrefix("www.")
                } else {
                    java.net.URI(tab.url).host?.removePrefix("www.") ?: ""
                }
            } catch (e: Exception) {
                try { java.net.URI(tab.url).host?.removePrefix("www.") ?: "" } catch (ex: Exception) { "" }
            }
            if (host.isNotBlank() && entry.username.isNotEmpty() && entry.password.isNotEmpty()) {
                pendingSaveCredential = BrowserViewModel.SavedPassword(
                    domain = host,
                    username = entry.username,
                    password = entry.password
                )
            }
            return GeckoResult.fromValue(prompt.dismiss())
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
                    val currentTabUrl = tabs[idx].url
                    tabs[idx] = tabs[idx].copy(title = it)
                    if (!isIncognitoMode) {
                        addToHistory(it, currentTabUrl)
                    }
                    saveTabs()
                }
            }
        }

        override fun onCrash(session: GeckoSession) {
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
            if (tab.id == activeTabId && selection.text.isNotEmpty()) {
                activeTextSelection = selection.text
                activeSelectionObject = selection
            }
        }

        override fun onHideAction(session: GeckoSession, reason: Int) {
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
                    if (it == "about:blank" && currentTabUrl != "about:blank" && currentTabUrl.isNotEmpty()) {
                        return
                    }
                    tabs[idx] = tabs[idx].copy(url = it, settingsVersion = currentSettingsVersion)
                    saveTabs()
                }
                if (tab.id == activeTabId) {
                    currentUrl = it
                    checkAutofillForUrl(it)
                    mediaInterceptor.clear()
                    isVideoPlayingInPage = false
                }
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBackValue: Boolean) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(canGoBack = canGoBackValue)
            }
            if (tab.id == activeTabId) {
                canGoBack = canGoBackValue
            }
        }

        override fun onCanGoForward(session: GeckoSession, canGoForwardValue: Boolean) {
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

            if (request.target == org.mozilla.geckoview.GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                if (isPopupBlockerEnabled) {
                    if (!request.hasUserGesture) {
                        Log.w(TAG, "🚫 onLoadRequest: Blocked auto-popup (no user gesture) to $uri")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    if (request.uri.isEmpty() || lowerUri == "about:blank" ||
                        lowerUri.startsWith("data:") || lowerUri.startsWith("javascript:")) {
                        Log.w(TAG, "🚫 onLoadRequest: Blocked blank/script popup")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    val host = try { Uri.parse(uri).host?.lowercase() ?: "" } catch (e: Exception) { "" }
                    val isAdPopup = BrowserViewModel.POPUP_AD_DOMAINS.any { domain -> lowerUri.contains(domain) } ||
                        BrowserViewModel.POPUP_HOST_KEYWORDS.any { kw -> host.contains(kw) }
                    if (isAdPopup) {
                        Log.w(TAG, "🚫 onLoadRequest: Blocked ad popup to $uri")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
            }

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

            val isYouTube = lowerUri.contains("youtube.com") || lowerUri.contains("youtu.be")
            if (isNativePlayerEnabled && isDirectVideoUrl(uri) && (!isYouTube || isYouTubeEnabled)) {
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

            if (uri.endsWith(".xpi") || uri.contains("/firefox/downloads/file/")) {
                Log.d(TAG, "Intercepted addon install click: $uri")
                installExtensionFromUrl(uri, context)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            if (tab.id == activeTabId && isGenericDownloadUrl(uri) && (!isYouTube || isYouTubeEnabled)) {
                Log.i(TAG, "📥 Intercepted file download URL: $uri")
                viewModelScope.launch(Dispatchers.Main) {
                    val filename = guessDownloadFilename(uri, null)
                    pendingGenericDownload = BrowserViewModel.PendingGenericDownload(
                        url = uri,
                        filename = filename,
                        contentType = null
                    )
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            if (!lowerUri.startsWith("http://") && 
                !lowerUri.startsWith("https://") && 
                !lowerUri.startsWith("about:") && 
                !lowerUri.startsWith("javascript:") && 
                !lowerUri.startsWith("data:")
            ) {
                if (lowerUri.startsWith("intent:") || lowerUri.startsWith("market:")) {
                    Log.i(TAG, "Intercepted intent/market URI: $uri")
                    
                    try {
                        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                        val intentPackage = intent.getPackage()
                        
                        val isCalendarSpam = intentPackage?.contains("calendar") == true || intentPackage?.contains("cal") == true ||
                                intent.dataString?.contains("calendar") == true || intent.dataString?.contains("webcal") == true || intent.dataString?.contains(".ics") == true
                        
                        if (isCalendarSpam) {
                            Log.w(TAG, "🚫 Blocked calendar/adware intent: package=$intentPackage, data=${intent.dataString}")
                            viewModelScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Blocked calendar spam intent", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.i(TAG, "Launching external app intent safely: package=$intentPackage")
                            viewModelScope.launch(Dispatchers.Main) {
                                try {
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    intent.setComponent(null)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        intent.setSelector(null)
                                    }
                                    val chooser = Intent.createChooser(intent, "Open with")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Log.w(TAG, "No app found for intent, checking for fallback URL", e)
                                    val fallbackUrl = extractFallbackUrl(uri)
                                    if (fallbackUrl != null) {
                                        Log.i(TAG, "Navigating to fallback URL: $fallbackUrl")
                                        loadUrl(fallbackUrl)
                                    } else if (intentPackage != null) {
                                        try {
                                            val marketIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("market://details?id=$intentPackage")
                                            )
                                            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(marketIntent)
                                        } catch (e2: Exception) {
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

                Log.i(TAG, "Handling custom protocol URI: $uri")
                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        val chooserTitle = when {
                            lowerUri.startsWith("upi:") -> "Pay with"
                            lowerUri.startsWith("mailto:") -> "Send email with"
                            lowerUri.startsWith("tel:") -> "Call with"
                            lowerUri.startsWith("sms:") || lowerUri.startsWith("smsto:") -> "Send SMS with"
                            else -> "Open with"
                        }
                        val chooser = Intent.createChooser(intent, chooserTitle)
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                val lowerUri = uri.lowercase().trim()

                if (isPopupBlockerEnabled) {
                    if (uri.isEmpty() || lowerUri == "about:blank" ||
                        lowerUri.startsWith("data:") || lowerUri.startsWith("javascript:")) {
                        Log.w(TAG, "🚫 onNewSession: Blocked blank/script popup")
                        return GeckoResult.fromValue(null)
                    }

                    val host = try { Uri.parse(uri).host?.lowercase() ?: "" } catch (e: Exception) { "" }
                    val isAdPopup = BrowserViewModel.POPUP_AD_DOMAINS.any { domain -> lowerUri.contains(domain) } ||
                        BrowserViewModel.POPUP_HOST_KEYWORDS.any { kw -> host.contains(kw) }
                    if (isAdPopup) {
                        Log.w(TAG, "🚫 onNewSession: Blocked ad popup — $uri")
                        return GeckoResult.fromValue(null)
                    }

                    val isOAuthLogin = lowerUri.contains("accounts.google") ||
                                       lowerUri.contains("facebook.com/dialog") ||
                                       lowerUri.contains("api.twitter") ||
                                       lowerUri.contains("github.com/login/oauth") ||
                                       lowerUri.contains("appleid.apple.com") ||
                                       lowerUri.contains("login.microsoftonline.com")
                    if (!isOAuthLogin && lowerUri.startsWith("http")) {
                        Log.i(TAG, "🔀 onNewSession: reusing active tab for target_blank: $uri")
                        viewModelScope.launch(Dispatchers.Main) {
                            loadUrl(uri)
                        }
                        return GeckoResult.fromValue(null)
                    }
                }

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
                loadingProgress = 0.05f
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
                loadingProgress = 1f
                checkAutofillForUrl(tab.url)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (loadingProgress >= 1f) isLoading = false
                }, 300)
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

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            if (tab.id == activeTabId) {
                loadingProgress = (progress / 100f).coerceIn(0.05f, 1f)
            }
        }
    }
}
