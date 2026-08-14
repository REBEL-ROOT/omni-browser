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

internal fun BrowserViewModel.setupTabSessionListeners(tab: TabState, context: Context) {
    applyUserAgentForTab(tab)
    tab.session.contentBlockingDelegate = object : org.mozilla.geckoview.ContentBlocking.Delegate {
        override fun onContentBlocked(session: GeckoSession, event: org.mozilla.geckoview.ContentBlocking.BlockEvent) {
            incrementTrackersBlocked(context, 1)
            try { adBlockManager.incrementBlockedCount(1) } catch (_: Exception) {}
        }
    }
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
            val hasCamera = permissions?.any { it == android.Manifest.permission.CAMERA } == true
            val hasMic    = permissions?.any { it == android.Manifest.permission.RECORD_AUDIO } == true
            val hasLoc    = permissions?.any {
                it == android.Manifest.permission.ACCESS_FINE_LOCATION ||
                it == android.Manifest.permission.ACCESS_COARSE_LOCATION
            } == true

            val title = when {
                hasCamera && hasMic -> "Camera & Microphone"
                hasCamera          -> "Camera"
                hasMic             -> "Microphone"
                hasLoc             -> "Location"
                else               -> "System Permission"
            }
            val body = when {
                hasCamera && hasMic -> "This site needs camera and microphone access. Grant only if you trust the site."
                hasCamera          -> "This site needs your camera. Grant only if you trust the site."
                hasMic             -> "This site needs your microphone. Grant only if you trust the site."
                hasLoc             -> "This site needs your precise location. Grant only if you trust the site."
                else               -> "This site is requesting a system permission."
            }

            activeSystemPermissionRequest = SystemPermissionRequest(
                permissions = permissions,
                rationaleTitle = title,
                rationaleBody = body,
                onGranted = { callback.grant() },
                onDenied = { callback.reject() }
            )
        }

        override fun onContentPermissionRequest(
            session: GeckoSession,
            permission: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int>? {
            Log.d(TAG, "onContentPermissionRequest: type=${permission.permission}, uri=${permission.uri}")

            // Auto-approve Storage Access and Identity permissions for verified auth origins.
            // Use OriginVerifier instead of substring matching to prevent spoofing
            // (e.g., evilgoogle.com must NOT match google.com).
            val isAuthOrigin = OriginVerifier.isExactOriginMatch(permission.uri, "accounts.google.com") ||
                               OriginVerifier.isSubdomainOf(permission.uri, "google.com") ||
                               OriginVerifier.isSubdomainOf(permission.uri, "appleid.apple.com") ||
                               OriginVerifier.isSubdomainOf(permission.uri, "facebook.com")
            if (isAuthOrigin || permission.permission == 6 || permission.permission == 7 || permission.permission == 8) {
                Log.i(TAG, "Auto-granting auth/storage permission (${permission.permission}) for ${permission.uri}")
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            }
            
            if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) {
                val autoplayVal = getSitePermissionValue(permission.uri, "autoplay")
                return if (autoplayVal == "allow") {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                } else {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
            }

            val permissionTypeStr = when (permission.permission) {
                1 -> "location"
                2 -> "notifications"
                3 -> "camera"
                4 -> "microphone"
                5 -> "drm"
                else -> null
            }

            if (permissionTypeStr != null) {
                val currentVal = getSitePermissionValue(permission.uri, permissionTypeStr)
                if (currentVal == "allow") {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                } else if (currentVal == "block") {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
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
                    if (permissionTypeStr != null) updateSitePermission(permission.uri, permissionTypeStr, "allow")
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                },
                onAllowOnce = {
                    // Grant for this session only — do NOT persist to site permissions
                    activePermissionPrompt = null
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                },
                onDeny = {
                    activePermissionPrompt = null
                    if (permissionTypeStr != null) updateSitePermission(permission.uri, permissionTypeStr, "block")
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

            // Check permissions rules
            val cameraVal = if (hasVideo) getSitePermissionValue(uri, "camera") else "allow"
            val micVal = if (hasAudio) getSitePermissionValue(uri, "microphone") else "allow"

            if (cameraVal == "block" || micVal == "block") {
                Log.d(TAG, "onMediaPermissionRequest: Blocked media access based on settings rule")
                callback.reject()
                return
            }

            if (cameraVal == "allow" && micVal == "allow") {
                Log.d(TAG, "onMediaPermissionRequest: Allowed media access based on settings rule")
                val videoSource = video?.firstOrNull()
                val audioSource = audio?.firstOrNull()
                callback.grant(videoSource, audioSource)
                return
            }

            activeMediaPermissionPrompt = MediaPermissionPrompt(
                siteUri = uri,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                videoSources = video,
                audioSources = audio,
                onAllow = { selectedVideo, selectedAudio ->
                    activeMediaPermissionPrompt = null
                    if (hasVideo) updateSitePermission(uri, "camera", "allow")
                    if (hasAudio) updateSitePermission(uri, "microphone", "allow")
                    callback.grant(selectedVideo, selectedAudio)
                },
                onAllowOnce = { selectedVideo, selectedAudio ->
                    // Grant for this session only — do NOT persist
                    activeMediaPermissionPrompt = null
                    callback.grant(selectedVideo, selectedAudio)
                },
                onDeny = {
                    activeMediaPermissionPrompt = null
                    if (hasVideo) updateSitePermission(uri, "camera", "block")
                    if (hasAudio) updateSitePermission(uri, "microphone", "block")
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

        /**
         * Validates that the current tab's origin is trusted for receiving privileged
         * OMNI_* messages. These messages control browser chrome features (visual blocking,
         * page stats, console eval) and must only come from:
         * 1. Our own moz-extension:// content scripts (built-in extensions)
         * 2. The current top-level web page (not a cross-origin iframe)
         *
         * Rejects messages from about:blank, data:, javascript:, blob: origins.
         */
        private fun isTrustedOmniOrigin(): Boolean {
            val url = tab.url
            if (url.isNullOrBlank()) return false
            // Always trust our own built-in extensions
            if (url.startsWith("moz-extension://")) return true
            // Reject dangerous origins that could spoof messages
            val lower = url.lowercase()
            if (lower.startsWith("about:") || lower.startsWith("data:") ||
                lower.startsWith("javascript:") || lower.startsWith("blob:")) {
                Log.w(TAG, "🛡️ Rejected OMNI_* message from dangerous origin: $url")
                return false
            }
            // Trust any http/https page — the message came from the top-level content
            // script injection, not a cross-origin iframe (Gecko isolates prompts by origin)
            return lower.startsWith("http://") || lower.startsWith("https://")
        }

        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val message = prompt.message ?: ""

            // All OMNI_* prefixed messages are privileged native-app channels.
            // Reject them from untrusted origins to prevent privilege escalation.
            if (message.startsWith("OMNI_") && !isTrustedOmniOrigin()) {
                Log.w(TAG, "🛡️ Blocked OMNI_* alert from untrusted origin: ${tab.url}, messagePrefix=${message.take(30)}")
                return GeckoResult.fromValue(prompt.dismiss())
            }

            if (message.startsWith("OMNI_VISUAL_BLOCK_ADD:")) {
                val jsonStr = message.removePrefix("OMNI_VISUAL_BLOCK_ADD:")
                try {
                    val obj = org.json.JSONObject(jsonStr)
                    val selector = obj.optString("selector", "")
                    val preview = obj.optString("preview", "")
                    val payloadDomain = obj.optString("domain", "").lowercase().removePrefix("www.")
                    val rawDomain = if (payloadDomain.isNotBlank() && payloadDomain != "about:blank") payloadDomain else {
                        try { android.net.Uri.parse(tab.url).host?.lowercase()?.removePrefix("www.") ?: "*" } catch(_: Exception) { "*" }
                    }
                    val cleanDomain = if (rawDomain.isBlank() || rawDomain == "about:blank") "*" else rawDomain
                    if (selector.isNotBlank()) {
                        viewModelScope.launch(Dispatchers.Main) {
                            visualBlockManager.addRule(cleanDomain, selector, preview)
                            isVisualBlockModeActive = false
                            applyVisualBlockRulesToActiveTab()
                            Toast.makeText(context, "Element blocked & rule saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding visual block rule", e)
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            if (message.startsWith("OMNI_VISUAL_BLOCK_CANCEL:")) {
                viewModelScope.launch(Dispatchers.Main) {
                    isVisualBlockModeActive = false
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            if (message.startsWith("OMNI_VISUAL_BLOCK_SETTINGS:")) {
                viewModelScope.launch(Dispatchers.Main) {
                    isVisualBlockModeActive = false
                    navigateToVisualBlockSettingsTrigger = true
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            if (message.startsWith("OMNI_IMAGES:")) {
                val json = message.removePrefix("OMNI_IMAGES:")
                try {
                    val jsonArray = org.json.JSONArray(json)
                    val urls = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val imgUrl = jsonArray.getString(i)
                        if (imgUrl.isNotBlank() && !urls.contains(imgUrl)) {
                            urls.add(imgUrl)
                        }
                    }
                    viewModelScope.launch(Dispatchers.Main) {
                        extractedImagesList = urls
                        isExtractingImages = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing extracted images", e)
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            if (message.startsWith("OMNI_EVAL_RESULT:")) {
                val jsonStr = message.removePrefix("OMNI_EVAL_RESULT:")
                try {
                    val obj = org.json.JSONObject(jsonStr)
                    val ok = obj.optBoolean("ok", true)
                    val resultVal = obj.optString("val", "")
                    viewModelScope.launch(Dispatchers.Main) {
                        consoleEvalError = !ok
                        consoleEvalResult = resultVal
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing eval result", e)
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            if (message.startsWith("OMNI_PAGE_STATS:")) {
                val jsonStr = message.removePrefix("OMNI_PAGE_STATS:")
                try {
                    val obj = org.json.JSONObject(jsonStr)
                    val activeTab = tabs.find { it.id == activeTabId }
                    val titleText = activeTab?.title?.ifEmpty { "Webpage" } ?: "Webpage"

                    val metaList = mutableListOf<BrowserViewModel.MetaTagInfo>()
                    val metaArray = obj.optJSONArray("meta")
                    if (metaArray != null) {
                        for (idx in 0 until metaArray.length()) {
                            val mObj = metaArray.getJSONObject(idx)
                            metaList.add(BrowserViewModel.MetaTagInfo(mObj.optString("n"), mObj.optString("c")))
                        }
                    }

                    val domList = mutableListOf<BrowserViewModel.DomNodeInfo>()
                    val domArray = obj.optJSONArray("dom")
                    if (domArray != null) {
                        for (idx in 0 until domArray.length()) {
                            val dObj = domArray.getJSONObject(idx)
                            domList.add(BrowserViewModel.DomNodeInfo(
                                tag = dObj.optString("t"),
                                id = dObj.optString("i"),
                                className = dObj.optString("c"),
                                childCount = dObj.optInt("ch"),
                                snippet = dObj.optString("s")
                            ))
                        }
                    }

                    val resList = mutableListOf<BrowserViewModel.ResourceInfo>()
                    val resArray = obj.optJSONArray("res")
                    if (resArray != null) {
                        for (idx in 0 until resArray.length()) {
                            val rObj = resArray.getJSONObject(idx)
                            resList.add(BrowserViewModel.ResourceInfo(
                                url = rObj.optString("u"),
                                type = rObj.optString("t"),
                                durationMs = rObj.optInt("d"),
                                sizeBytes = rObj.optLong("s")
                            ))
                        }
                    }

                    val ckList = mutableListOf<BrowserViewModel.StorageItem>()
                    val ckArray = obj.optJSONArray("ck")
                    if (ckArray != null) {
                        for (idx in 0 until ckArray.length()) {
                            val cObj = ckArray.getJSONObject(idx)
                            ckList.add(BrowserViewModel.StorageItem(cObj.optString("k"), cObj.optString("v")))
                        }
                    }

                    val lsList = mutableListOf<BrowserViewModel.StorageItem>()
                    val lsArray = obj.optJSONArray("ls")
                    if (lsArray != null) {
                        for (idx in 0 until lsArray.length()) {
                            val lObj = lsArray.getJSONObject(idx)
                            lsList.add(BrowserViewModel.StorageItem(lObj.optString("k"), lObj.optString("v")))
                        }
                    }

                    viewModelScope.launch(Dispatchers.Main) {
                        pageInspectorStats = BrowserViewModel.PageStats(
                            title = titleText,
                            wordCount = obj.optInt("w", 0),
                            readTimeMinutes = obj.optInt("r", 1),
                            imageCount = obj.optInt("i", 0),
                            linkCount = obj.optInt("l", 0),
                            scriptCount = obj.optInt("s", 0),
                            cssCount = obj.optInt("c", 0),
                            charCount = obj.optInt("ch", 0),
                            h1Count = obj.optInt("h1", 0),
                            h2Count = obj.optInt("h2", 0),
                            h3Count = obj.optInt("h3", 0),
                            metaTags = metaList,
                            domNodes = domList,
                            resources = resList,
                            cookies = ckList,
                            localStorageItems = lsList
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing page stats", e)
                }
                return GeckoResult.fromValue(prompt.dismiss())
            }
            return null
        }

        override fun onChoicePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ChoicePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            if (tab.id != activeTabId) return GeckoResult.fromValue(prompt.dismiss())
            val choices = prompt.choices ?: return GeckoResult.fromValue(prompt.dismiss())
            if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss())

            // Dismiss any previously pending choice prompt to prevent stale GeckoResult.
            // At most one prompt should be active per session at a time.
            cancelChoicePrompt()

            // Store the pending prompt so the Compose UI can show a native choice dialog
            // with a radio/checkbox list (fixes Issue #74: <select> not responding).
            Log.i(TAG, "onChoicePrompt: ${choices.size} choices, type=${prompt.type}")
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            pendingChoicePrompt = BrowserViewModel.PendingChoicePrompt(
                geckoResult = result,
                prompt = prompt
            )
            return result
            return result
        }

        override fun onDateTimePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.DateTimePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            if (tab.id != activeTabId) return GeckoResult.fromValue(prompt.dismiss())

            // Dismiss any previously pending date/time prompt to prevent stale GeckoResult.
            cancelDateTimePrompt()

            // Store the pending prompt so the Compose UI can show native Android
            // DatePickerDialog / TimePickerDialog (fixes Issue #74: date pickers not responding).
            val typeLabel = when (prompt.type) {
                GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE -> "DATE"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> "MONTH"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> "WEEK"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> "TIME"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> "DATETIME_LOCAL"
                else -> "UNKNOWN(${prompt.type})"
            }
            Log.i(TAG, "onDateTimePrompt: type=$typeLabel, default=${prompt.defaultValue}, " +
                    "min=${prompt.minValue}, max=${prompt.maxValue}")
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            pendingDatePrompt = BrowserViewModel.PendingDatePrompt(
                geckoResult = result,
                prompt = prompt
            )
            return result
        }

        override fun onColorPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ColorPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            if (tab.id != activeTabId) return GeckoResult.fromValue(prompt.dismiss())

            // Fall through to GeckoView's default color picker behavior.
            // We do not override this — GeckoView handles <input type="color"> natively.
            return null
        }

        override fun onLoginSelect(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            // Dismiss Gecko's native auto-fill prompt so it doesn't automatically overwrite 
            // input fields when the user types or edits text (e.g. backspacing).
            // Password autofill is handled explicitly via our Compose UI.
            return GeckoResult.fromValue(prompt.dismiss())
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
                    originHost.removePrefix("www.").lowercase()
                } else {
                    java.net.URI(tab.url).host?.removePrefix("www.")?.lowercase() ?: ""
                }
            } catch (e: Exception) {
                try { java.net.URI(tab.url).host?.removePrefix("www.")?.lowercase() ?: "" } catch (ex: Exception) { "" }
            }
            if (neverSavePasswordDomains.contains(host)) {
                Log.i(TAG, "Suppression rule active for $host — ignoring password save prompt")
                return GeckoResult.fromValue(prompt.dismiss())
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
            // Maximum reasonable scroll values to prevent memory/DoS from malformed JS
            val maxScrollMetric = 100_000_000f
            title?.let {
                // Intercept scroll metrics sent from injected JS
                if (it.startsWith("__omni__:")) {
                    try {
                        val parts = it.removePrefix("__omni__:").split(":")
                        if (parts.size >= 2) {
                            val scrollHeight = parts[0].toFloatOrNull()
                            val viewportHeight = parts[1].toFloatOrNull()
                            // Validate: must be non-negative and within reasonable bounds
                            if (scrollHeight != null && viewportHeight != null &&
                                scrollHeight >= 0f && viewportHeight >= 0f &&
                                scrollHeight <= maxScrollMetric && viewportHeight <= maxScrollMetric
                            ) {
                                pageScrollHeight = scrollHeight
                                pageViewportHeight = viewportHeight
                            } else {
                                Log.w(TAG, "🛡️ Rejected out-of-bounds scroll metrics: scrollHeight=$scrollHeight, viewportHeight=$viewportHeight")
                            }
                        }
                    } catch (_: Exception) {}
                    return
                }

                // Sanitize title before storing — strip control characters that could
                // corrupt history, tabs, or UI rendering
                val sanitizedTitle = it.filter { c -> c.code >= 32 || c == '\t' || c == '\n' || c == '\r' }
                    .take(500) // Reasonable max title length

                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx != -1) {
                    val currentTabUrl = tabs[idx].url
                    tabs[idx] = tabs[idx].copy(title = sanitizedTitle)
                    if (!isIncognitoMode) {
                        addToHistory(sanitizedTitle, currentTabUrl)
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
                selectionScreenRect = selection.screenRect
            }
        }

        override fun onHideAction(session: GeckoSession, reason: Int) {
            if (tab.id == activeTabId) {
                activeTextSelection = null
                activeSelectionObject = null
                selectionScreenRect = null
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
                // Dynamically update allowJavascript setting for the new domain
                session.settings.allowJavascript = (getSitePermissionValue(it, "javascript") == "allow")

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
                    notifyPageNavigation()
                    isVideoPlayingInPage = false
                    applyUserAgentForTab(tab, it)
                    injectStealthDefuserScriptlet(tab)
                    applySiteStyleToTab(tab)
                    // Re-suppress the translate badge on SPA navigations within translate.goog
                    if (it.contains(".translate.goog")) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            injectTranslateBadgeSuppressor()
                        }, 800)
                    }
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

            // Always allow extension-internal resources (moz-extension://) immediately
            if (lowerUri.startsWith("moz-extension://")) {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            if (tab.id == activeTabId) {
                mediaInterceptor.onMediaRequestDetected(uri)
            }

            // ── Google OAuth Native Account Picker ─────────────────────────────────
            // MUST be the first check — before popup blocker — so that OAuth windows
            // opened via window.open() (TARGET_WINDOW_NEW) are caught here and shown
            // in the native account picker instead of being silently blocked.
            //
            // Grace period: after the user picks an account, oauthGracePeriodByTab[tab.id]
            // is set for 15 seconds. During that window, ALL accounts.google.com navigations
            // on this tab pass through — covering multi-hop redirect chains like:
            //   site → accounts.google.com → site-callback → accounts.google.com (again) → site
            // When the tab reaches a non-Google URL, the grace period is cleared so the
            // next "Sign in with Google" on any site shows the picker again normally.
            val isGoogleAuthHost = OriginVerifier.isExactOriginMatch(uri, "accounts.google.com")

            // Clear grace period once the OAuth redirect chain lands on a non-Google page
            if (!isGoogleAuthHost && oauthGracePeriodByTab.containsKey(tab.id)) {
                oauthGracePeriodByTab.remove(tab.id)
                Log.i(TAG, "🔑 Google OAuth grace period cleared for tab ${tab.id} (reached non-Google URL)")
            }

            // If we are within the grace period for this tab, allow all accounts.google.com through
            val gracePeriodExpiry = oauthGracePeriodByTab[tab.id]
            val isInGracePeriod = gracePeriodExpiry != null && System.currentTimeMillis() < gracePeriodExpiry
            if (isInGracePeriod && isGoogleAuthHost) {
                Log.i(TAG, "🔑 Google Auth grace pass-through (${((gracePeriodExpiry!! - System.currentTimeMillis()) / 1000)}s remaining): $uri")
                return null  // ALLOW — part of the active OAuth redirect chain
            }
            // Expired grace periods are cleaned up lazily
            if (gracePeriodExpiry != null && !isInGracePeriod) {
                oauthGracePeriodByTab.remove(tab.id)
            }

            // Intercept first-time navigation to Google sign-in and show native account picker.
            // Covers all Google sign-in entry points:
            //  - accounts.google.com/o/oauth2/       (classic OAuth 2.0 redirect)
            //  - accounts.google.com/v3/signin/      (modern Gmail / Google app login)
            //  - accounts.google.com/signin/v2/      (legacy Google login)
            //  - accounts.google.com/ServiceLogin    (service login page)
            //  - accounts.google.com/AccountChooser (account chooser)
            //  - accounts.google.com/gsi/            (Google Sign-In JS library)
            //  - accounts.google.com with client_id / response_type / continue params
            val isGoogleOAuth = tab.id == activeTabId && isGoogleAuthHost &&
                (lowerUri.contains("/o/oauth2/") ||
                 lowerUri.contains("/v3/signin/") ||
                 lowerUri.contains("/signin/v2/") ||
                 lowerUri.contains("/signin/oauth") ||
                 lowerUri.contains("/servicelogin") ||
                 lowerUri.contains("/accountchooser") ||
                 lowerUri.contains("/addsession") ||
                 lowerUri.contains("/gsi/select") ||
                 lowerUri.contains("/gsi/issue") ||
                 lowerUri.contains("client_id=") ||
                 lowerUri.contains("response_type=") ||
                 lowerUri.contains("continue="))
            if (isGoogleOAuth) {
                Log.i(TAG, "🔑 Google Auth intercepted: $uri")
                viewModelScope.launch(Dispatchers.Main) {
                    pendingGoogleOAuthRequest = BrowserViewModel.PendingGoogleOAuthRequest(
                        oauthUrl = uri,
                        tabId = tab.id
                    )
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            if (request.target == org.mozilla.geckoview.GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                // No built-in popup blocker — all new-window navigations fall through to tab creation below.
            }

            val host = SecurityPolicy.extractEffectiveHost(uri)
            if (host.isNotEmpty() && adBlockManager.isHostBlocked(host)) {
                Log.w(TAG, "🚫 onLoadRequest: Blocked ad/tracker sub-navigation: $uri")
                incrementTrackersBlocked(context, 1)
                try { adBlockManager.incrementBlockedCount(1) } catch (_: Exception) {}
                return GeckoResult.fromValue(AllowOrDeny.DENY)
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

            val isYouTube = OriginVerifier.isSubdomainOf(uri, "youtube.com") || OriginVerifier.isSubdomainOf(uri, "youtu.be")
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

            // ── Native App Delegation: ordinary HTTP/HTTPS stays in Omni ─────────
            // An installed Android app must never take over merely because it can
            // handle the same HTTP/HTTPS URL (YouTube, Instagram, Maps, …). The
            // presence of a native handler must not pull the user out of the
            // browser, regardless of the per-site "externalApp" permission —
            // "allow" only applies to explicit external-app requests (intent:,
            // market:, custom schemes) handled below through the permission flow.

            if (!lowerUri.startsWith("http://") && 
                !lowerUri.startsWith("https://") && 
                !lowerUri.startsWith("about:") && 
                !lowerUri.startsWith("javascript:") && 
                !lowerUri.startsWith("data:")
            ) {
                val sourceHost = try { 
                    val h = Uri.parse(tab.url).host?.lowercase()?.removePrefix("www.")
                    if (!h.isNullOrBlank() && h != "blank") h else Uri.parse(uri).host?.lowercase()?.removePrefix("www.") ?: ""
                } catch (_: Exception) { "" }
                val sitePerm = getSitePermissionValue(sourceHost, "externalApp")

                // Helper for a browser-style external-app prompt.
                val performFallback: (intentPackage: String?, isBlocked: Boolean) -> Unit = { intentPackage, isBlocked ->
                    val fallbackUrl = extractFallbackUrl(uri)
                    if (!intentPackage.isNullOrBlank() || fallbackUrl != null) {
                        viewModelScope.launch(Dispatchers.Main) {
                            pendingExternalAppRequest = BrowserViewModel.PendingExternalAppRequest(
                                uri = uri,
                                packageName = intentPackage,
                                fallbackUrl = fallbackUrl,
                                blockedAutomatically = isBlocked,
                                sourceHost = sourceHost
                            )
                        }
                    } else {
                        val toastMsg = if (isBlocked) {
                            "Blocked automatic redirect to an external app"
                        } else {
                            "No app found to handle this link"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                }

                // Global toggle OFF → block everything
                if (!isOpenExternalAppAllowed) {
                    Log.w(TAG, "🚫 onLoadRequest: External app launches disabled in settings: $uri")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Check per-site permission for all external app intents
                when (sitePerm) {
                    "allow" -> {
                        // Per-site always-allow: proceed to launch external app intent
                        Log.i(TAG, "✅ onLoadRequest: External app launch allowed by site permission for $sourceHost: $uri")
                    }
                    "block" -> {
                        Log.w(TAG, "🚫 onLoadRequest: External app launch blocked by site permission for $sourceHost: $uri")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    else -> {
                        // "ask" (default): ALWAYS prompt user for permission before opening any external app!
                        Log.i(TAG, "❓ onLoadRequest: External app launch queued for user permission ($sourceHost): $uri")
                        viewModelScope.launch(Dispatchers.Main) {
                            val intentPackage = if (lowerUri.startsWith("intent:")) {
                                try { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getPackage() } catch (_: Exception) { null }
                            } else if (lowerUri.startsWith("market:")) {
                                try { Uri.parse(uri).getQueryParameter("id") } catch (_: Exception) { null }
                            } else {
                                try {
                                    Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getPackage()
                                        ?: Uri.parse(uri).getQueryParameter("package")
                                } catch (_: Exception) { null }
                            }
                            performFallback(intentPackage, false)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }

                if (lowerUri.startsWith("intent:") || lowerUri.startsWith("market:")) {
                    Log.i(TAG, "Intercepted intent/market URI: $uri")
                    
                    try {
                        val intent = if (lowerUri.startsWith("intent:")) {
                            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        }
                        val intentPackage = if (lowerUri.startsWith("intent:")) {
                            intent.getPackage()
                        } else {
                            Uri.parse(uri).getQueryParameter("id")
                        }
                        
                        val isCalendarSpam = intentPackage?.contains("calendar") == true || intentPackage?.contains("cal") == true ||
                                intent.dataString?.contains("calendar") == true || intent.dataString?.contains("webcal") == true || intent.dataString?.contains(".ics") == true
                        
                        if (isCalendarSpam) {
                            Log.w(TAG, "🚫 Blocked calendar/adware intent: package=$intentPackage, data=${intent.dataString}")
                            viewModelScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Blocked calendar spam intent", Toast.LENGTH_SHORT).show()
                            }
                        } else if (sitePerm == "allow") {
                            Log.i(TAG, "Launching external app intent safely (sitePerm=allow): package=$intentPackage")
                            viewModelScope.launch(Dispatchers.Main) {
                                try {
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    intent.setComponent(null)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        intent.setSelector(null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    performFallback(intentPackage, false)
                                }
                            }
                        } else {
                            // Default ("ask") or "block": ALWAYS prompt user for permission!
                            Log.i(TAG, "❓ External intent queued for user decision ($sourceHost, sitePerm=$sitePerm): $uri")
                            viewModelScope.launch(Dispatchers.Main) {
                                performFallback(intentPackage, false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing intent URI", e)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                Log.i(TAG, "Handling custom protocol URI: $uri")
                viewModelScope.launch(Dispatchers.Main) {
                    val intentPackage = try {
                        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getPackage()
                            ?: Uri.parse(uri).getQueryParameter("package")
                    } catch (_: Exception) {
                        null
                    }
                    if (sitePerm == "allow") {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            performFallback(intentPackage, false)
                        }
                    } else {
                        Log.i(TAG, "❓ Custom protocol queued for user decision ($sourceHost, sitePerm=$sitePerm): $uri")
                        performFallback(intentPackage, false)
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
            
            val lowerUri = uri?.lowercase() ?: ""
            val isGoogleAuthHost = OriginVerifier.isExactOriginMatch(uri, "accounts.google.com")

            if (lowerUri.startsWith("moz-extension://")) {
                Log.i(TAG, "🧩 Suppressed load error for extension URI: $uri")
                return null
            }
            
            // Check if this error is an ERROR_UNKNOWN (17) caused by us returning DENY
            // in onLoadRequest for OAuth pages, direct videos, spam calendars, or external app links.
            val isDeniedByCustomIntercept = error.code == org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN && (
                isGoogleAuthHost ||
                (isNativePlayerEnabled && isDirectVideoUrl(uri ?: "")) ||
                (!lowerUri.startsWith("http://") && !lowerUri.startsWith("https://") && !lowerUri.startsWith("about:") && !lowerUri.startsWith("javascript:") && !lowerUri.startsWith("data:"))
            )
            
            if (isDeniedByCustomIntercept) {
                Log.i(TAG, "🔑 Ignored onLoadError (code 17) for custom denied URL: $uri")
                return null
            }
            
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

                Log.i(TAG, "onNewSession: opening new tab for popup URI $uri")
                val runtime = getGeckoRuntime(context)
                val isAuthUri = OriginVerifier.isExactOriginMatch(uri, "accounts.google.com") ||
                                  lowerUri.contains("oauth") || lowerUri.contains("gsi")
                val isJsAllowed = isAuthUri || getSitePermissionValue(uri, "javascript") == "allow"
                val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                    .usePrivateMode(isIncognitoMode)
                    .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .allowJavascript(isJsAllowed)
                    .build()
                val newSession = GeckoSession(settings)
                val tabId = java.util.UUID.randomUUID().toString()
                val newTab = TabState(
                    id = tabId,
                    session = newSession,
                    title = "New Tab",
                    url = uri,
                    isIncognito = isIncognitoMode,
                    settingsVersion = currentSettingsVersion
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
            applyUserAgentForTab(tab, url)
            applySiteStyleToTab(tab)
            if (forceDarkWebsites || isDarkThemeEnabled) {
                injectForceDarkCssIfNeeded(tab)
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
                applySiteStyleToTab(tab)
                if (forceDarkWebsites || isDarkThemeEnabled) {
                    injectForceDarkCssIfNeeded(tab)
                }
                if (tab.id == activeTabId) {
                    injectStealthDefuserScriptlet(tab)
                    if (accessibilityForceZoom) {
                        injectZoomEnabler()
                    }
                    val cosmeticCss = try { adBlockManager.getCosmeticAdBlockCss() } catch(_: Exception) { "" }
                    if (cosmeticCss.isNotEmpty()) {
                        val cleanCss = cosmeticCss.replace("\n", " ").replace("'", "\\'")
                        tab.session.loadUri("javascript:(function(){try{var s=document.createElement('style');s.innerHTML='$cleanCss';document.head.appendChild(s);}catch(e){}})();")
                    }
                    if (tab.url.contains(".translate.goog")) {
                        injectTranslateBadgeSuppressor()
                    }
                    if (showScrollButtons) {
                        tab.session.loadUri("javascript:(function(){try{var s=document.createElement('style');s.id='omni-hide-scrollbars';s.innerHTML='*::-webkit-scrollbar { display: none !important; } html, body { scrollbar-width: none !important; -ms-overflow-style: none !important; }';document.head.appendChild(s);}catch(e){}})();")
                        tab.session.loadUri("javascript:(function(){var sh=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;if(sh&&vh){var ot=document.title;document.title='__omni__:'+sh+':'+vh;setTimeout(function(){if(document.title.indexOf('__omni__:')===0)document.title=ot;},10);}})();")
                    }
                    applyVisualBlockRulesToTab(tab)
                }
            }
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            if (progress in 5..25) {
                injectStealthDefuserScriptlet(tab)
                applySiteStyleToTab(tab)
            }
            if (tab.id == activeTabId) {
                loadingProgress = (progress / 100f).coerceIn(0.05f, 1f)
            }
        }

        // Continuously capture session state so it's always available for suspension.
        // GeckoView delivers the serializable SessionState here after every navigation.
        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                tabs[idx] = tabs[idx].copy(savedSessionState = sessionState)
            }
        }
    }
}

internal fun BrowserViewModel.injectStealthDefuserScriptlet(tab: TabState) {
    try {
        val defuserJs = adBlockManager.getStealthDefuserJs()
        if (defuserJs.isNotBlank()) {
            val cleanJs = defuserJs.replace("\n", " ")
            tab.session.loadUri("javascript:(function(){$cleanJs})();")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error injecting stealth defuser scriptlet", e)
    }
}

/**
 * Queries Android PackageManager to resolve non-browser native app handlers for deep links.
 * Filters out Omni Browser itself as well as generic web browsers.
 */
internal fun getNativeAppHandlers(context: Context, uri: String): List<android.content.pm.ResolveInfo> {
    return try {
        val parsedUri = Uri.parse(uri)
        val scheme = parsedUri.scheme?.lowercase() ?: return emptyList()

        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val pm = context.packageManager

        val query: (Intent) -> List<android.content.pm.ResolveInfo> = { targetIntent ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    targetIntent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(
                    targetIntent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )
            }
        }

        val resolveInfos = query(intent)

        if (scheme == "http" || scheme == "https") {
            // Query a dummy URL to identify generic web browsers registered for general HTTP/HTTPS
            val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://a.b.c.invalid.test.domain.xyz")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val browserPackages = query(dummyIntent)
                .map { it.activityInfo.packageName }
                .toSet()

            resolveInfos.filter { info ->
                val pkg = info.activityInfo.packageName
                pkg != context.packageName && !browserPackages.contains(pkg)
            }
        } else {
            resolveInfos.filter { info ->
                info.activityInfo.packageName != context.packageName
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
