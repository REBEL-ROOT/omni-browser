package com.rebelroot.omni.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG
import com.rebelroot.omni.browser.session.SessionRecoveryDiagnostics
import com.rebelroot.omni.browser.session.SessionRecoveryCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Detects whether a URL is related to authentication, OAuth, SSO, or login flows.
 * Used to bypass ad-blocking for legitimate auth popups (Google, Facebook, Deezer, etc.).
 */
private fun isAuthRelatedUrl(uri: String): Boolean {
    if (uri.isBlank()) return true // GeckoView may provide empty URI initially in onNewSession
    val lower = uri.lowercase()

    // ── Known auth hosts ────────────────────────────────────────────────
    val knownAuthHosts = listOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "github.com/login",
        "github.com/sessions",
        "connect.deezer.com",
        "www.facebook.com/v",          // Facebook OAuth dialog: /v<N>/dialog/oauth
        "www.facebook.com/dialog",
        "m.facebook.com/v",
        "m.facebook.com/dialog",
        "accounts.spotify.com",
        "discord.com/oauth",
        "discord.com/api/oauth",
        "id.twitch.tv",
        "api.twitter.com/oauth",
        "twitter.com/i/oauth",
        "x.com/i/oauth",
        "login.yahoo.com",
        "login.live.com",
        "auth0.com",
        "auth.atlassian.com",
        "signin.aws.amazon.com",
        "login.salesforce.com",
        "sso.godaddy.com",
        "id.heroku.com",
        "gitlab.com/oauth",
        "bitbucket.org/site/oauth",
        "stackexchange.com/oauth",
        "stackoverflow.com/oauth",
        "open.spotify.com/authorize",
    )
    if (knownAuthHosts.any { lower.contains(it) }) return true

    // ── Auth path patterns (covers OAuth, OIDC, SAML, SSO) ──────────────
    val authPathPatterns = listOf(
        "/oauth",
        "/auth/",
        "/authorize",
        "/login",
        "/signin",
        "/sign-in",
        "/sign_in",
        "/sso",
        "/saml",
        "/openid",
        "/connect/authorize",
        "/dialog/oauth",
        "response_type=code",
        "response_type=token",
        "redirect_uri=",
        "client_id=",
    )
    if (authPathPatterns.any { lower.contains(it) }) return true

    // ── Generic keyword patterns ────────────────────────────────────────
    if (lower.contains("oauth") || lower.contains("gsi")) return true

    return false
}

internal fun BrowserViewModel.setupTabSessionListeners(tab: TabState, context: Context) {
    fun liveTab(): TabState = tabs.find { it.id == tab.id } ?: tab
    fun liveTabUrl(): String {
        val current = liveTab().url
        return when {
            current.isNotBlank() && current != "about:blank" -> current
            tab.id == activeTabId && currentUrl.isNotBlank() && currentUrl != "about:blank" -> currentUrl
            else -> current
        }
    }

    applyUserAgentForTab(liveTab())

    // Attach SessionTabDelegate and ActionDelegate for active WebExtensions
    if (userExtensions.isNotEmpty()) {
        val sessionTabDel = createSessionTabDelegate(context)
        val sessionActionDel = createSessionActionDelegate(tab.session)
        userExtensions.forEach { ext ->
            try {
                tab.session.webExtensionController.setTabDelegate(ext, sessionTabDel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting SessionTabDelegate on tab ${tab.id} for ${ext.safeId}", e)
            }
            try {
                tab.session.webExtensionController.setActionDelegate(ext, sessionActionDel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting ActionDelegate on tab ${tab.id} for ${ext.safeId}", e)
            }
        }
    }

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

            // PERMISSION_TRACKING (7): Always DENY to keep GeckoView's anti-tracking protection active.
            // Returning ALLOW here would disable ALL tracking protection for the requesting site.
            if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING) {
                Log.d(TAG, "Denying PERMISSION_TRACKING for ${permission.uri} — tracking protection stays active")
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }

            // PERMISSION_STORAGE_ACCESS (8): Only grant for verified auth origins that need
            // cross-site cookie access for OAuth/login flows. Deny for all other third parties.
            if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS) {
                val isAuthOrigin = OriginVerifier.isExactOriginMatch(permission.uri, "accounts.google.com") ||
                                   OriginVerifier.isExactOriginMatch(permission.uri, "accounts.youtube.com") ||
                                   OriginVerifier.isExactOriginMatch(permission.uri, "appleid.apple.com") ||
                                   OriginVerifier.isExactOriginMatch(permission.uri, "login.microsoftonline.com")
                if (isAuthOrigin) {
                    Log.i(TAG, "Granting PERMISSION_STORAGE_ACCESS for verified auth origin ${permission.uri}")
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
                Log.d(TAG, "Denying PERMISSION_STORAGE_ACCESS for third-party ${permission.uri}")
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }

            // Auto-grant DRM (Widevine / EME) permission for media playback unless explicitly blocked
            if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
                val drmVal = getSitePermissionValue(permission.uri, "drm")
                if (drmVal != "block") {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
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
                GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "location"              // 0
                GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications" // 1
                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "storage"         // 2
                // Camera and Microphone are handled via onMediaPermissionRequest, not here
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "drm"        // 6
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
                    if (permissionTypeStr != null && !tab.isIncognito) {
                        updateSitePermission(permission.uri, permissionTypeStr, "allow")
                    }
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                },
                onAllowOnce = {
                    // Grant for this session only — do NOT persist to site permissions
                    activePermissionPrompt = null
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                },
                onDeny = {
                    activePermissionPrompt = null
                    if (permissionTypeStr != null && !tab.isIncognito) {
                        updateSitePermission(permission.uri, permissionTypeStr, "block")
                    }
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
                runCatching { callback.reject() }
                return
            }

            val hasVideo = !video.isNullOrEmpty()
            val hasAudio = !audio.isNullOrEmpty()

            if (!hasVideo && !hasAudio) {
                runCatching { callback.reject() }
                return
            }

            // Check permissions rules
            val cameraVal = if (hasVideo) getSitePermissionValue(uri, "camera") else "allow"
            val micVal = if (hasAudio) getSitePermissionValue(uri, "microphone") else "allow"

            if (cameraVal == "block" || micVal == "block") {
                Log.d(TAG, "onMediaPermissionRequest: Blocked media access based on settings rule")
                runCatching { callback.reject() }
                return
            }

            if (cameraVal == "allow" && micVal == "allow") {
                Log.d(TAG, "onMediaPermissionRequest: Allowed media access based on settings rule")
                val videoSource = video?.firstOrNull()
                val audioSource = audio?.firstOrNull()
                runCatching { callback.grant(videoSource, audioSource) }
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
                    if (hasVideo && !tab.isIncognito) updateSitePermission(uri, "camera", "allow")
                    if (hasAudio && !tab.isIncognito) updateSitePermission(uri, "microphone", "allow")
                    runCatching { callback.grant(selectedVideo, selectedAudio) }
                },
                onAllowOnce = { selectedVideo, selectedAudio ->
                    // Grant for this session only — do NOT persist
                    activeMediaPermissionPrompt = null
                    runCatching { callback.grant(selectedVideo, selectedAudio) }
                },
                onDeny = {
                    activeMediaPermissionPrompt = null
                    if (hasVideo && !tab.isIncognito) updateSitePermission(uri, "camera", "block")
                    if (hasAudio && !tab.isIncognito) updateSitePermission(uri, "microphone", "block")
                    runCatching { callback.reject() }
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
            val url = liveTabUrl()
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
                Log.w(TAG, "🛡️ Blocked OMNI_* alert from untrusted origin: ${liveTabUrl()}, messagePrefix=${message.take(30)}")
                if (message.startsWith("OMNI_IMAGES:")) {
                    viewModelScope.launch(Dispatchers.Main) {
                        isExtractingImages = false
                    }
                }
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
                        try { android.net.Uri.parse(liveTabUrl()).host?.lowercase()?.removePrefix("www.") ?: "*" } catch(_: Exception) { "*" }
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
                    val rawUrls = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val imgUrl = jsonArray.getString(i)
                        if (imgUrl.isNotBlank()) {
                            rawUrls.add(imgUrl)
                        }
                    }
                    val processedUrls = ImageGrabberUtils.processAndUpgradeExtractedImages(rawUrls)
                    viewModelScope.launch(Dispatchers.Main) {
                        extractedImagesList = processedUrls
                        isExtractingImages = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing extracted images", e)
                    viewModelScope.launch(Dispatchers.Main) {
                        isExtractingImages = false
                    }
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
            if (tab.id != activeTabId) return GeckoResult.fromValue(prompt.dismiss())
            val options = prompt.options
            Log.d(TAG, "🔑 [Autofill] onLoginSelect received with ${options.size} option(s)")

            val host = try { java.net.URI(liveTabUrl()).host?.removePrefix("www.")?.lowercase() ?: "" } catch(_: Exception) { "" }
            val vaultMatches = if (host.isNotEmpty()) getPasswordsForDomain(host) else emptyList()

            val matches = if (vaultMatches.isNotEmpty()) {
                vaultMatches
            } else {
                options.mapNotNull { opt ->
                    val entry = opt.value
                    if (entry != null && entry.username.isNotEmpty()) {
                        BrowserViewModel.SavedPassword(
                            domain = host.ifEmpty { entry.origin ?: "" },
                            username = entry.username,
                            password = entry.password
                        )
                    } else null
                }
            }

            if (matches.isNotEmpty() && isOmniPasswordManagerEnabled) {
                viewModelScope.launch(Dispatchers.Main) {
                    autofillMatches = matches
                    showAutofillBottomSheet = true
                }
            }

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
                    java.net.URI(liveTabUrl()).host?.removePrefix("www.")?.lowercase() ?: ""
                }
            } catch (e: Exception) {
                try { java.net.URI(liveTabUrl()).host?.removePrefix("www.")?.lowercase() ?: "" } catch (ex: Exception) { "" }
            }
            if (!isOmniPasswordManagerEnabled) {
                Log.d(TAG, "Omni password manager is disabled — ignoring onLoginSave")
                return GeckoResult.fromValue(prompt.dismiss())
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

        override fun onKill(session: GeckoSession) {
            android.util.Log.e(TAG, "GeckoSession content process killed for tab ${tab.id}")
            com.rebelroot.omni.browser.session.SessionRecoveryDiagnostics.logSessionKilled(tab.id, tab.sessionGenerationId)

            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx == -1) return

            // Mark session as dead and invalidate generation so stale callbacks are ignored.
            val deadGeneration = tabs[idx].sessionGenerationId
            val deadUrl = tabs[idx].url
            val isDeadHome = (deadUrl == "about:blank" || deadUrl.isEmpty())
            val deadState = if (isDeadHome) null else tabs[idx].savedSessionState
            val deadIncognito = tabs[idx].isIncognito

            // Close the dead session.
            runCatching { session.close() }

            // Update tab state to reflect death.
            tabs[idx] = tabs[idx].copy(
                isSuspended = true,
                savedSessionState = if (isDeadHome) null else tabs[idx].savedSessionState
            )

            // If this is the active tab, trigger recovery.
            if (tab.id == activeTabId) {
                val context = appContext ?: return
                val runtime = getGeckoRuntime(context)
                isRecoveringActiveTab = true
                lastRecoveryFailed = false
                recoveryCoordinator?.recoverTab(
                    tabId = tab.id,
                    context = context,
                    runtime = runtime,
                    url = if (isDeadHome) "about:blank" else deadUrl,
                    isIncognito = deadIncognito,
                    isDesktopMode = isDesktopMode,
                    inMemoryState = deadState,
                    createSession = { newSession ->
                        val newGen = recoveryCoordinator?.nextGenerationId() ?: 0L
                        tabs[idx] = tabs[idx].copy(
                            session = newSession,
                            isSuspended = false,
                            sessionGenerationId = newGen,
                            savedSessionState = null,
                            url = if (isDeadHome) "about:blank" else tabs[idx].url,
                            title = if (isDeadHome) "New Tab" else tabs[idx].title
                        )
                        if (tab.id == activeTabId) {
                            geckoSession = newSession
                            if (isDeadHome) {
                                currentUrl = "about:blank"
                                canGoBack = false
                                canGoForward = false
                            }
                        }
                        setupTabSessionListeners(tabs[idx], context)
                        recoveryCoordinator?.registerTab(tab.id, newGen, com.rebelroot.omni.browser.session.SessionRecoveryCoordinator.GeckoState.OPEN)
                    },
                    onComplete = { success, method ->
                        isRecoveringActiveTab = false
                        if (success) {
                            Log.i(TAG, "Session recovered for ${tab.id} via $method")
                            // Re-attach to GeckoView only if active tab and NOT on home screen.
                            if (tab.id == activeTabId && method != "about_blank_clean" && tabs[idx].url != "about:blank" && tabs[idx].url.isNotEmpty()) {
                                geckoSession = tabs[idx].session
                                activeGeckoViewRef?.get()?.let { gv ->
                                    gv.setSession(tabs[idx].session)
                                    tabs[idx].session.setActive(true)
                                }
                                try {
                                    runtime.webExtensionController.setTabActive(tabs[idx].session, true)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error setting recovered tab active for extensions", e)
                                }
                            }
                        } else {
                            Log.w(TAG, "Session recovery failed for ${tab.id}")
                            lastRecoveryFailed = true
                        }
                    }
                )
            }
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
                    linkText = element.title ?: element.altText
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

    tab.session.historyDelegate = object : GeckoSession.HistoryDelegate {
        override fun onHistoryStateChange(
            session: GeckoSession,
            historyList: GeckoSession.HistoryDelegate.HistoryList
        ) {
            if (recoveryCoordinator?.isStaleCallback(tab.id, tab.sessionGenerationId) == true) return
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx == -1) return
            val currentTab = tabs[idx]
            val list = ArrayList<SessionHistoryEntry>(historyList.size)
            for (i in 0 until historyList.size) {
                val item = historyList[i]
                val uri = item.uri ?: ""
                val title = item.title?.takeIf { it.isNotBlank() } ?: uri
                list.add(SessionHistoryEntry(index = i, url = uri, title = title))
            }
            val currentIdx = historyList.currentIndex
            val sessionCanGoBack = currentIdx > 0
            val sessionCanGoForward = currentIdx < historyList.size - 1
            val isHome = (currentTab.url == "about:blank" || currentTab.url.isEmpty())
            val effectiveCanGoBack = if (isHome) false else (sessionCanGoBack || !isExternalIntentLaunch)
            val effectiveCanGoForward = if (isHome) (!currentTab.lastWebUrl.isNullOrEmpty()) else sessionCanGoForward

            tabs[idx] = currentTab.copy(
                canGoBackInSession = sessionCanGoBack,
                canGoForwardInSession = sessionCanGoForward,
                canGoBack = effectiveCanGoBack,
                canGoForward = effectiveCanGoForward,
                sessionHistory = list
            )
            if (tab.id == activeTabId) {
                canGoBack = effectiveCanGoBack
                canGoForward = effectiveCanGoForward
                activeSessionHistory = list
                activeHistoryIndex = currentIdx
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
            // Stale-callback protection: ignore callbacks from a dead/invalid session generation.
            if (recoveryCoordinator?.isStaleCallback(tab.id, tab.sessionGenerationId) == true) return
            url?.let {
                // Dynamically update allowJavascript setting for the new domain
                session.settings.allowJavascript = (getSitePermissionValue(it, "javascript") == "allow")

                val isBlankOrEmpty = (it == "about:blank" || it.isEmpty())
                val idx = tabs.indexOfFirst { it.id == tab.id }
                if (idx == -1) return

                val currentTab = tabs[idx]
                // 🛡️ CRITICAL: Ignore transient initial "about:blank" fired by Gecko during session
                // initialization or resumption when the tab is intended to be a real web page.
                // Otherwise, Gecko's initial blank document will clobber the real URL, wipe savedSessionState,
                // call session.stop(), and throw the user back to the home screen!
                if (isBlankOrEmpty && currentTab.url.isNotEmpty() && currentTab.url != "about:blank") {
                    Log.d(TAG, "onLocationChange: ignoring transient initial about:blank for tab ${tab.id} (target=${currentTab.url})")
                    return
                }

                val isHome = isBlankOrEmpty
                val lastUrl = if (!isHome) it else currentTab.lastWebUrl
                val lastTitle = if (!isHome) (if (it == currentTab.url) currentTab.title else it) else currentTab.lastWebTitle
                val effectiveCanGoBack = if (isHome) false else (currentTab.canGoBackInSession || !isExternalIntentLaunch)
                val effectiveCanGoForward = if (isHome) (!lastUrl.isNullOrEmpty()) else currentTab.canGoForwardInSession

                tabs[idx] = currentTab.copy(
                    url = it,
                    title = if (isHome) "New Tab" else tabs[idx].title,
                    savedSessionState = if (isHome) null else tabs[idx].savedSessionState,
                    canGoBack = effectiveCanGoBack,
                    canGoForward = effectiveCanGoForward,
                    lastWebUrl = lastUrl,
                    lastWebTitle = lastTitle,
                    settingsVersion = currentSettingsVersion
                )
                if (isHome) {
                    sessionStatePersistence?.removeDurableState(tab.id)
                }
                saveTabs()

                if (tab.id == activeTabId) {
                    currentUrl = it
                    canGoBack = effectiveCanGoBack
                    canGoForward = effectiveCanGoForward
                    if (isHome) {
                        runCatching { session.stop() }
                    }
                    syncActiveSession(session)
                    checkAutofillForUrl(it)
                    mediaInterceptor.clear()
                    notifyPageNavigation()
                    isVideoPlayingInPage = false
                    applyUserAgentForTab(tab, it)
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
            if (idx == -1) return
            val currentTab = tabs[idx]
            val isHome = (currentTab.url == "about:blank" || currentTab.url.isEmpty())
            val effective = if (isHome) false else (canGoBackValue || !isExternalIntentLaunch)
            tabs[idx] = currentTab.copy(
                canGoBackInSession = canGoBackValue,
                canGoBack = effective
            )
            if (tab.id == activeTabId) {
                canGoBack = effective
            }
        }

        override fun onCanGoForward(session: GeckoSession, canGoForwardValue: Boolean) {
            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx == -1) return
            val currentTab = tabs[idx]
            val isHome = (currentTab.url == "about:blank" || currentTab.url.isEmpty())
            val effective = if (isHome) (!currentTab.lastWebUrl.isNullOrEmpty()) else canGoForwardValue
            tabs[idx] = currentTab.copy(
                canGoForwardInSession = canGoForwardValue,
                canGoForward = effective
            )
            if (tab.id == activeTabId) {
                canGoForward = effective
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

            // ── Authentication & OAuth Diagnostic Logging ───────────────────────────
            // Allow standard web authentication (Google, YouTube, Apple, Microsoft, GitHub, OAuth)
            // to execute natively in GeckoView without interception or denial.
            val isGoogleAuthHost = OriginVerifier.isExactOriginMatch(uri, "accounts.google.com")
            val isAuthHost = isGoogleAuthHost ||
                             OriginVerifier.isExactOriginMatch(uri, "accounts.youtube.com") ||
                             OriginVerifier.isExactOriginMatch(uri, "appleid.apple.com") ||
                             OriginVerifier.isExactOriginMatch(uri, "login.microsoftonline.com") ||
                             OriginVerifier.isExactOriginMatch(uri, "github.com") ||
                             isAuthRelatedUrl(uri)

            if (isAuthHost) {
                val effectiveHost = SecurityPolicy.extractEffectiveHost(uri)
                Log.i(TAG, "AUTH_NAV: host=$effectiveHost, target=${request.target}, isDirect=${request.isDirectNavigation}, hasGesture=${request.hasUserGesture}, tab=${tab.id}")
            }

            if (request.target == org.mozilla.geckoview.GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                // No built-in popup blocker — all new-window navigations fall through to tab creation below.
            }

            // A server redirect (301/302/303/307/308) must be followed by Gecko,
            // never intercepted or denied. onLoadRequest fires for these too, with
            // isDirectNavigation = false, so the ad-block check below used to deny
            // them and strand the user on the "Page has moved" response; the
            // file/video interception further down hijacked others into a download
            // (issue #132). Anything non-renderable behind the redirect still goes
            // through GeckoView's own onExternalResponse download path.
            if (request.isRedirect) {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            val host = SecurityPolicy.extractEffectiveHost(uri)
            if (SecurityPolicy.shouldDenyBlockedSubNavigation(
                    isRedirect = request.isRedirect,
                    isDirectNavigation = request.isDirectNavigation,
                    isAuthHost = isAuthHost,
                    host = host,
                    hostIsBlocked = adBlockManager.isHostBlocked(host)
                )
            ) {
                Log.w(TAG, "🚫 onLoadRequest: Blocked ad/tracker sub-navigation: $uri")
                incrementTrackersBlocked(context, 1)
                try { adBlockManager.incrementBlockedCount(1) } catch (_: Exception) {}
                if (tab.parentId != null) {
                    Log.i(TAG, "🚫 onLoadRequest: Auto-closing blocked popup tab ${tab.id} (parent=${tab.parentId})")
                    viewModelScope.launch(Dispatchers.Main) {
                        closeTab(tab.id, context)
                    }
                }
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
                        callback.invoke(uri, liveTabUrl())
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

            if (isGenericDownloadUrl(uri) && (!isYouTube || isYouTubeEnabled)) {
                Log.i(TAG, "📥 Intercepted file download URL: $uri (tabId=${tab.id}, parentId=${tab.parentId})")
                viewModelScope.launch(Dispatchers.Main) {
                    if (tab.parentId != null) {
                        closeTab(tab.id, context)
                    }
                    val filename = guessDownloadFilename(uri, null)
                    handleGenericDownload(uri, filename, null, context)
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            if (lowerUri.startsWith("magnet:")) {
                Log.i(TAG, "🧲 Intercepted magnet link: $uri (tabId=${tab.id}, parentId=${tab.parentId})")
                viewModelScope.launch(Dispatchers.Main) {
                    if (tab.parentId != null) {
                        closeTab(tab.id, context)
                    }
                    val magnetIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val hasExternalApp = try {
                        context.packageManager.queryIntentActivities(magnetIntent, 0).isNotEmpty()
                    } catch (_: Exception) { false }

                    if (hasExternalApp) {
                        try {
                            val chooser = Intent.createChooser(magnetIntent, "Open Magnet Link")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        } catch (_: Exception) {
                            pendingTorrentUrl = uri
                        }
                    } else {
                        pendingTorrentUrl = uri
                    }
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
                    val h = Uri.parse(liveTabUrl()).host?.lowercase()?.removePrefix("www.")
                    if (!h.isNullOrBlank() && h != "blank") h else Uri.parse(uri).host?.lowercase()?.removePrefix("www.") ?: ""
                } catch (_: Exception) { "" }
                val sitePerm = getSitePermissionValue(sourceHost, "externalApp")

                // Global toggle OFF → block everything
                if (!isOpenExternalAppAllowed) {
                    Log.w(TAG, "🚫 onLoadRequest: External app launches disabled in settings: $uri")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Per-site "block" → deny silently
                if (sitePerm == "block") {
                    Log.w(TAG, "🚫 onLoadRequest: External app launch blocked by site permission for $sourceHost: $uri")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // ── intent:// and market:// URIs ─────────────────────────────────────
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

                        // Calendar-spam guard (must run before any launch)
                        val isCalendarSpam = intentPackage?.contains("calendar") == true || intentPackage?.contains("cal") == true ||
                                intent.dataString?.contains("calendar") == true || intent.dataString?.contains("webcal") == true || intent.dataString?.contains(".ics") == true

                        if (isCalendarSpam) {
                            Log.w(TAG, "🚫 Blocked calendar/adware intent: package=$intentPackage, data=${intent.dataString}")
                            viewModelScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Blocked calendar spam intent", Toast.LENGTH_SHORT).show()
                            }
                        } else if (sitePerm == "allow") {
                            // Per-site always-allow: launch directly
                            Log.i(TAG, "✅ onLoadRequest: Launching intent (sitePerm=allow): package=$intentPackage uri=$uri")
                            viewModelScope.launch(Dispatchers.Main) {
                                try {
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    intent.setComponent(null)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        intent.setSelector(null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Intent launch failed, showing fallback", e)
                                    val fallbackUrl = extractFallbackUrl(uri)
                                    if (!fallbackUrl.isNullOrBlank()) {
                                        loadUrl(fallbackUrl)
                                    } else {
                                        Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            // "ask" (default): show consent dialog — ALWAYS, even if package is null
                            Log.i(TAG, "❓ onLoadRequest: intent queued for user permission ($sourceHost): $uri")
                            val fallbackUrl = extractFallbackUrl(uri)
                            viewModelScope.launch(Dispatchers.Main) {
                                pendingExternalAppRequest = BrowserViewModel.PendingExternalAppRequest(
                                    uri = uri,
                                    packageName = intentPackage,
                                    fallbackUrl = fallbackUrl,
                                    blockedAutomatically = false,
                                    sourceHost = sourceHost
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing intent URI", e)
                    }
                    // Cache so onLoadError can suppress the spurious error page
                    lastDeniedExternalAppUrl = uri
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // ── Custom scheme URIs (myapp://, tel:, mailto:, etc.) ───────────────
                Log.i(TAG, "Handling custom protocol URI: $uri")
                viewModelScope.launch(Dispatchers.Main) {
                    val intentPackage = try {
                        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getPackage()
                            ?: Uri.parse(uri).getQueryParameter("package")
                    } catch (_: Exception) {
                        null
                    }
                    if (sitePerm == "allow") {
                        // Per-site always-allow: launch directly
                        Log.i(TAG, "✅ onLoadRequest: Launching custom scheme (sitePerm=allow): $uri")
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Custom scheme launch failed, showing fallback", e)
                            val fallbackUrl = extractFallbackUrl(uri)
                            if (!fallbackUrl.isNullOrBlank()) {
                                loadUrl(fallbackUrl)
                            } else {
                                Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // "ask" (default): show consent dialog — ALWAYS
                        Log.i(TAG, "❓ onLoadRequest: Custom protocol queued for user permission ($sourceHost): $uri")
                        val fallbackUrl = extractFallbackUrl(uri)
                        pendingExternalAppRequest = BrowserViewModel.PendingExternalAppRequest(
                            uri = uri,
                            packageName = intentPackage,
                            fallbackUrl = fallbackUrl,
                            blockedAutomatically = false,
                            sourceHost = sourceHost
                        )
                    }
                }
                // Cache so onLoadError can suppress the spurious error page
                lastDeniedExternalAppUrl = uri
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            
            // ── Issue #113: offer the native app for user-tapped https(s) app links ──
            // Plain https:// deep links (YouTube, Reddit, etc.) previously always
            // loaded in the browser even with "Open links in external apps" enabled,
            // because the ask-first prompt only covered intent:// and custom schemes.
            // For genuine user-gesture, same-window navigations (never redirects,
            // iframes, or sub-resources — a site must never bounce the user into an
            // app), resolve non-browser native app handlers for the URL and queue the
            // consent dialog. Nothing ever opens natively without an explicit user
            // confirmation; dismissing simply loads the page in the browser.
            val isSearchEngine = host.contains("google.") || host.contains("bing.") || host.contains("duckduckgo.") || host.contains("yahoo.") || host.contains("yandex.") || host.contains("brave.") || host.contains("ecosia.") || host.contains("startpage.") || host.contains("qwant.")
            val currentTabHost = try { Uri.parse(liveTabUrl()).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            val isSameSiteNavigation = currentTabHost.isNotEmpty() && (host == currentTabHost || host.endsWith(".$currentTabHost") || currentTabHost.endsWith(".$host"))

            if ((request.hasUserGesture || request.isDirectNavigation) &&
                request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT &&
                isOpenExternalAppAllowed &&
                pendingExternalAppRequest == null &&
                !isAuthHost &&
                !isSearchEngine &&
                !isSameSiteNavigation &&
                (lowerUri.startsWith("http://") || lowerUri.startsWith("https://"))
            ) {
                val externalSitePerm = getSitePermissionValue(host, "externalApp")
                if (externalSitePerm != "block") {
                    val handler = getNativeAppHandlers(context, uri).firstOrNull()
                    val handlerPkg = handler?.activityInfo?.packageName
                    if (handlerPkg != null) {
                        if (externalSitePerm == "allow") {
                            // Remembered consent ("Always open" chosen previously for this site)
                            Log.i(TAG, "✅ onLoadRequest: https app-link allowed by site permission for $host: $uri")
                            viewModelScope.launch(Dispatchers.Main) {
                                try {
                                    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                        setPackage(handlerPkg)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(appIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "https app-link launch failed — loading in browser instead", e)
                                    tab.session.loadUri(uri)
                                }
                            }
                        } else {
                            Log.i(TAG, "❓ onLoadRequest: https app-link queued for user decision ($host): $uri")
                            viewModelScope.launch(Dispatchers.Main) {
                                pendingExternalAppRequest = BrowserViewModel.PendingExternalAppRequest(
                                    uri = uri,
                                    packageName = handlerPkg,
                                    fallbackUrl = null,
                                    blockedAutomatically = false,
                                    sourceHost = host,
                                    webUrlFallback = true
                                )
                            }
                        }
                        // Cache so onLoadError can suppress the spurious error page
                        lastDeniedExternalAppUrl = uri
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
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
            // in onLoadRequest for direct videos, spam calendars, or external app links.
            val deniedExtUrl = lastDeniedExternalAppUrl
            val isDeniedByCustomIntercept = error.code == org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN && (
                (isNativePlayerEnabled && isDirectVideoUrl(uri ?: "")) ||
                (!lowerUri.startsWith("http://") && !lowerUri.startsWith("https://") && !lowerUri.startsWith("about:") && !lowerUri.startsWith("javascript:") && !lowerUri.startsWith("data:")) ||
                // Issue #113: http(s) URL denied for external app prompt
                (deniedExtUrl != null && (deniedExtUrl == uri || deniedExtUrl.equals(uri, ignoreCase = true)))
            )
            
            if (isDeniedByCustomIntercept) {
                // Clear the cache so subsequent genuine errors are not suppressed
                if (deniedExtUrl != null) lastDeniedExternalAppUrl = null
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
                val isYouTube = OriginVerifier.isSubdomainOf(uri, "youtube.com") || OriginVerifier.isSubdomainOf(uri, "youtu.be")

                if (isGenericDownloadUrl(uri) && (!isYouTube || isYouTubeEnabled)) {
                    Log.i(TAG, "📥 Intercepted new-window file download URL: $uri")
                    viewModelScope.launch(Dispatchers.Main) {
                        val filename = guessDownloadFilename(uri, null)
                        handleGenericDownload(uri, filename, null, context)
                    }
                    return null
                }

                if (lowerUri.startsWith("magnet:")) {
                    Log.i(TAG, "🧲 Intercepted new-window magnet link: $uri")
                    viewModelScope.launch(Dispatchers.Main) {
                        val magnetIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val hasExternalApp = try {
                            context.packageManager.queryIntentActivities(magnetIntent, 0).isNotEmpty()
                        } catch (_: Exception) { false }

                        if (hasExternalApp) {
                            try {
                                val chooser = Intent.createChooser(magnetIntent, "Open Magnet Link")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            } catch (_: Exception) {
                                pendingTorrentUrl = uri
                            }
                        } else {
                            pendingTorrentUrl = uri
                        }
                    }
                    return null
                }

                // Block ad/tracker popup popups before creating sessions
                val host = SecurityPolicy.extractEffectiveHost(uri)
                val isAuthUri = isAuthRelatedUrl(uri) ||
                                isAuthRelatedUrl(liveTabUrl()) || // parent tab is on an auth page
                                OriginVerifier.isExactOriginMatch(uri, "accounts.google.com") ||
                                OriginVerifier.isExactOriginMatch(uri, "accounts.youtube.com") ||
                                OriginVerifier.isExactOriginMatch(uri, "appleid.apple.com") ||
                                OriginVerifier.isExactOriginMatch(uri, "login.microsoftonline.com") ||
                                OriginVerifier.isExactOriginMatch(uri, "github.com")

                if (host.isNotEmpty() && !isAuthUri && adBlockManager.isHostBlocked(host)) {
                    Log.w(TAG, "🚫 onNewSession: Blocked ad/tracker popup to $uri")
                    incrementTrackersBlocked(context, 1)
                    try { adBlockManager.incrementBlockedCount(1) } catch (_: Exception) {}
                    return null
                }

                // ── Issue #113: offer the native app for new-window https(s) deep links ──
                // Most YouTube/Reddit/etc. links from external sites open in a new tab
                // (TARGET_WINDOW_NEW → onNewSession). These were missed by the same-window
                // check in onLoadRequest, so we handle them here.
                //
                // Conditions: global toggle on, no pending dialog, URL is http(s), the host
                // is not an auth host, the site permission is not "block", and a non-browser
                // native app on the device can handle the URL. If "allow" was previously
                // chosen for this site, launch the app directly — no prompt.
                val isSearchEngine = host.contains("google.") || host.contains("bing.") || host.contains("duckduckgo.") || host.contains("yahoo.") || host.contains("yandex.") || host.contains("brave.") || host.contains("ecosia.") || host.contains("startpage.") || host.contains("qwant.")
                if (isOpenExternalAppAllowed &&
                    pendingExternalAppRequest == null &&
                    !isAuthUri &&
                    !isSearchEngine &&
                    (lowerUri.startsWith("http://") || lowerUri.startsWith("https://"))
                ) {
                    val externalSitePerm = getSitePermissionValue(host, "externalApp")
                    if (externalSitePerm != "block") {
                        val handler = getNativeAppHandlers(context, uri).firstOrNull()
                        val handlerPkg = handler?.activityInfo?.packageName
                        if (handlerPkg != null) {
                            if (externalSitePerm == "allow") {
                                Log.i(TAG, "✅ onNewSession: https app-link auto-allowed for $host: $uri")
                                viewModelScope.launch(Dispatchers.Main) {
                                    try {
                                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                            addCategory(Intent.CATEGORY_BROWSABLE)
                                            setPackage(handlerPkg)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(appIntent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onNewSession: https app-link launch failed — loading in browser instead", e)
                                        // Create a new tab as fallback
                                        viewModelScope.launch(Dispatchers.Main) {
                                            createNewTab(context, uri)
                                        }
                                    }
                                }
                                return null
                            } else {
                                Log.i(TAG, "❓ onNewSession: https app-link queued for user decision ($host): $uri")
                                viewModelScope.launch(Dispatchers.Main) {
                                    pendingExternalAppRequest = BrowserViewModel.PendingExternalAppRequest(
                                        uri = uri,
                                        packageName = handlerPkg,
                                        fallbackUrl = null,
                                        blockedAutomatically = false,
                                        sourceHost = host,
                                        webUrlFallback = true,
                                        isNewWindow = true
                                    )
                                }
                                return null
                            }
                        }
                    }
                }

                Log.i(TAG, "onNewSession: opening new tab for popup URI $uri (opener tabId=${tab.id})")
                val isJsAllowed = isAuthUri || getSitePermissionValue(uri, "javascript") == "allow"
                val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
                    .usePrivateMode(isIncognitoMode)
                    .userAgentMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(if (isDesktopMode) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .allowJavascript(isJsAllowed)
                    .build()
                val newSession = GeckoSession(settings)
                val tabId = java.util.UUID.randomUUID().toString()
                val gen = nextTabGeneration()
                val newTab = TabState(
                    id = tabId,
                    session = newSession,
                    title = "New Tab",
                    url = uri,
                    isIncognito = isIncognitoMode,
                    settingsVersion = currentSettingsVersion,
                    sessionGenerationId = gen,
                    parentId = tab.id
                )

                setupTabSessionListeners(newTab, context)
                tabs.add(newTab)
                recoveryCoordinator?.registerTab(tabId, gen, com.rebelroot.omni.browser.session.SessionRecoveryCoordinator.GeckoState.OPEN)

                // GeckoView's NavigationDelegate.onNewSession contract mandates returning an UNOPENED
                // GeckoSession instance. GeckoView opens and attaches it internally to the parent window.
                // Calling newSession.open() here causes:
                // "java.lang.AssertionError: Must use an unopened GeckoSession instance" and crashes the app.
                val isBlankPopup = uri.isBlank() || uri == "about:blank"
                val shouldSelectImmediately = isAuthUri
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (shouldSelectImmediately) {
                        selectTab(newTab.id)
                    }
                    saveTabs()
                }

                Log.i(TAG, "AUTH_POPUP: created new tab $tabId with parentId ${tab.id} for uri $uri (selectImmediately=$shouldSelectImmediately)")
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
            applyUserAgentForTab(liveTab(), url)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            val currentLiveTab = liveTab()
            val currentLiveUrl = liveTabUrl()
            if (tab.id == activeTabId) {
                loadingProgress = 1f
                checkAutofillForUrl(currentLiveUrl)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (loadingProgress >= 1f) isLoading = false
                }, 300)
            }
            if (success) {
                applySiteStyleToTab(currentLiveTab)
                if (forceDarkWebsites || isDarkThemeEnabled) {
                    injectForceDarkCssIfNeeded(currentLiveTab)
                }
                injectExtensionOverlayMobileFix(currentLiveTab)
                if (currentLiveUrl.startsWith("moz-extension://")) {
                    injectExtensionPopupResponsiveFix(currentLiveTab)
                }
                if (tab.id == activeTabId) {
                    injectStealthDefuserScriptlet(currentLiveTab)
                    if (accessibilityForceZoom) {
                        injectZoomEnabler()
                    }
                    val isAuthPage = OriginVerifier.isExactOriginMatch(currentLiveUrl, "accounts.google.com") ||
                                     OriginVerifier.isExactOriginMatch(currentLiveUrl, "accounts.youtube.com") ||
                                     OriginVerifier.isExactOriginMatch(currentLiveUrl, "appleid.apple.com") ||
                                     OriginVerifier.isExactOriginMatch(currentLiveUrl, "login.microsoftonline.com") ||
                                     OriginVerifier.isExactOriginMatch(currentLiveUrl, "apis.google.com")
                    if (!isAuthPage) {
                        val cosmeticCss = try { adBlockManager.getCosmeticAdBlockCss() } catch(_: Exception) { "" }
                        if (cosmeticCss.isNotEmpty()) {
                            val cleanCss = cosmeticCss.replace("\n", " ").replace("'", "\\'")
                            tab.session.loadUri("javascript:(function(){try{var s=document.createElement('style');s.innerHTML='$cleanCss';document.head.appendChild(s);}catch(e){}})();")
                        }
                    }
                    if (currentLiveUrl.contains(".translate.goog")) {
                        injectTranslateBadgeSuppressor()
                    }
                    if (showScrollButtons) {
                        tab.session.loadUri("javascript:(function(){try{var s=document.createElement('style');s.id='omni-hide-scrollbars';s.innerHTML='*::-webkit-scrollbar { display: none !important; } html, body { scrollbar-width: none !important; -ms-overflow-style: none !important; }';document.head.appendChild(s);}catch(e){}})();")
                        tab.session.loadUri("javascript:(function(){try{var se=document.scrollingElement||document.documentElement||document.body;var sh=Math.max(document.documentElement?document.documentElement.scrollHeight:0,document.body?document.body.scrollHeight:0,se?se.scrollHeight:0);var vh=window.innerHeight||(document.documentElement?document.documentElement.clientHeight:0);if(sh&&vh){var ot=document.title;document.title='__omni__:'+sh+':'+vh;setTimeout(function(){if(document.title.indexOf('__omni__:')===0)document.title=ot;},10);}}catch(e){}})();")
                    }
                    applyVisualBlockRulesToTab(currentLiveTab)
                }
            }
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            // Stale-callback protection: ignore callbacks from a dead/invalid session generation.
            if (recoveryCoordinator?.isStaleCallback(tab.id, tab.sessionGenerationId) == true) return

            if (tab.id == activeTabId) {
                loadingProgress = (progress / 100f).coerceIn(0.05f, 1f)
            }
        }

        // Continuously capture session state so it's always available for suspension
        // AND persist it durably (debounced) so it survives process death.
        // GeckoView delivers the serializable SessionState here after every navigation.
        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            // Stale-callback protection: ignore callbacks from a dead/invalid session generation.
            if (recoveryCoordinator?.isStaleCallback(tab.id, tab.sessionGenerationId) == true) {
                com.rebelroot.omni.browser.session.SessionRecoveryDiagnostics.logSessionStateChanged(tab.id, tab.sessionGenerationId, accepted = false)
                return
            }

            val idx = tabs.indexOfFirst { it.id == tab.id }
            if (idx != -1) {
                // Do not capture or persist session state for about:blank / home screen
                if (tabs[idx].url == "about:blank" || tabs[idx].url.isEmpty()) {
                    tabs[idx] = tabs[idx].copy(savedSessionState = null)
                    sessionStatePersistence?.removeDurableState(tab.id)
                    return
                }

                tabs[idx] = tabs[idx].copy(savedSessionState = sessionState)
                // Debounced durable persistence (incognito tabs are skipped inside the persistence layer).
                val current = tabs[idx]
                sessionStatePersistence?.requestPersist(
                    tabId = tab.id,
                    sessionState = sessionState,
                    metadata = com.rebelroot.omni.browser.session.OmniSessionState.TabMetadata(
                        title = current.title,
                        url = current.url,
                        isIncognito = current.isIncognito,
                        lastActiveTime = current.lastActiveTime,
                        canGoBack = current.canGoBack,
                        canGoForward = current.canGoForward
                    )
                )
                com.rebelroot.omni.browser.session.SessionRecoveryDiagnostics.logSessionStateChanged(tab.id, tab.sessionGenerationId, accepted = true)
            }
        }
    }
}

internal fun BrowserViewModel.injectStealthDefuserScriptlet(tab: TabState) {
    try {
        val liveTab = tabs.find { it.id == tab.id } ?: tab
        val url = when {
            liveTab.url.isNotBlank() && liveTab.url != "about:blank" -> liveTab.url
            tab.id == activeTabId && currentUrl.isNotBlank() && currentUrl != "about:blank" -> currentUrl
            else -> liveTab.url
        }
        val isAuthOrigin = OriginVerifier.isExactOriginMatch(url, "accounts.google.com") ||
                           OriginVerifier.isExactOriginMatch(url, "accounts.youtube.com") ||
                           OriginVerifier.isExactOriginMatch(url, "appleid.apple.com") ||
                           OriginVerifier.isExactOriginMatch(url, "login.microsoftonline.com") ||
                           OriginVerifier.isExactOriginMatch(url, "apis.google.com")
        if (isAuthOrigin) return

        val defuserJs = adBlockManager.getStealthDefuserJs()
        if (defuserJs.isNotBlank()) {
            val cleanJs = defuserJs.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") }
                .joinToString(" ")
            tab.session.loadUri("javascript:$cleanJs")
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

            val filtered = resolveInfos.filter { info ->
                val pkg = info.activityInfo.packageName
                pkg != context.packageName && !browserPackages.contains(pkg)
            }
            if (filtered.isNotEmpty()) {
                Log.d("getNativeAppHandlers", "Found native handlers for $uri: ${filtered.map { it.activityInfo.packageName }}")
            }
            filtered
        } else {
            val filtered = resolveInfos.filter { info ->
                info.activityInfo.packageName != context.packageName
            }
            if (filtered.isNotEmpty()) {
                Log.d("getNativeAppHandlers", "Found native handlers for $uri: ${filtered.map { it.activityInfo.packageName }}")
            }
            filtered
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Universal Extension Mobile Compatibility Engine
//
// Handles ALL web extensions (Bitwarden, uBlock, LastPass, Privacy Badger,
// Dark Reader, etc.) that inject iframes, notification bars, or overlay panels
// into pages. On small phone screens these elements overflow because they were
// designed for desktop-width viewports (min-width: 400-600px, fixed positioning).
//
// Strategy:
//   1. CSS patch  — static rules to constrain any moz-extension:// iframe that
//                   is already in the DOM when the page loads.
//   2. DOM scan   — immediately fix any iframes already present in the page.
//   3. Overflow   — clamp any fixed-position element that is wider than the
//                   viewport (catches notification banners from any extension).
//   4. Observer   — MutationObserver watches for dynamically injected elements
//                   and applies the same fixes as they arrive.
//   5. Popup fix  — For moz-extension:// popup pages opened in the bottom sheet,
//                   inject a responsive viewport meta tag and box-model CSS so
//                   the popup's own content fits within a mobile screen.
// ──────────────────────────────────────────────────────────────────────────────

fun BrowserViewModel.injectExtensionOverlayMobileFix(tab: TabState) {
    val js = """
        (function() {
            try {
                if (window.__omni_ext_compat_installed) return;
                window.__omni_ext_compat_installed = true;

                // Constrain full-width top notification bars injected by password managers
                // (e.g. Bitwarden's notification/bar.html save-password banner) to the mobile
                // viewport width, while NEVER touching [popover] hosts or inline field autofill
                // menus (overlay/menu-button.html, overlay/menu-list.html) whose position/size
                // and MutationObservers are managed by the extension's content script.
                var style = document.createElement('style');
                style.id = 'omni-ext-compat-css';
                style.innerHTML = [
                    'iframe[src*="notification/bar.html"],',
                    '#bit-notification-bar-iframe,',
                    'bit-notification-bar-root,',
                    '[id*="bit-notification-bar"],',
                    '[data-lastpass-root],',
                    '[data-onepassword-notification],',
                    '[id^="dashlane-notification"],',
                    '#password-notification-bar,',
                    '#credential-notification-bar {',
                    '  max-width: 100vw !important;',
                    '  width: 100% !important;',
                    '  left: 0 !important;',
                    '  right: 0 !important;',
                    '  margin: 0 auto !important;',
                    '  border: none !important;',
                    '  box-sizing: border-box !important;',
                    '}',
                    '[popover] {',
                    '  max-width: 100vw !important;',
                    '}'
                ].join(' ');
                (document.head || document.documentElement).appendChild(style);
            } catch (e) {}
        })();
    """.trimIndent().replace("\n", " ")

    tab.session.loadUri("javascript:$js")
}

// ── 5. Extension Popup Responsive Fix ────────────────────────────────────────
// Applied to moz-extension:// pages opened inside the bottom-sheet popup viewer.
// Adds a mobile viewport meta tag (if missing) and overrides desktop-only
// box-model CSS so the popup's own content (buttons, cards, forms) fits a phone.
fun BrowserViewModel.injectExtensionPopupResponsiveFix(tab: TabState) {
    val js = """
        (function() {
            try {
                var existing = document.querySelector('meta[name="viewport"]');
                if (!existing) {
                    var meta = document.createElement('meta');
                    meta.name    = 'viewport';
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                    (document.head || document.documentElement).appendChild(meta);
                } else if (!existing.content || existing.content.indexOf('width=device-width') === -1) {
                    existing.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                }

                if (!document.getElementById('omni-ext-popup-responsive')) {
                    var style = document.createElement('style');
                    style.id = 'omni-ext-popup-responsive';
                    style.innerHTML = [
                        'html { max-width: 100vw !important; width: 100% !important; min-width: 0 !important; height: 100% !important; overflow-x: hidden !important; overflow-y: auto !important; -webkit-overflow-scrolling: touch !important; }',
                        'body { max-width: 100vw !important; width: 100% !important; min-width: 0 !important; min-height: 100% !important; height: auto !important; max-height: none !important; overflow-x: hidden !important; overflow-y: auto !important; overscroll-behavior-y: contain !important; -webkit-overflow-scrolling: touch !important; touch-action: pan-x pan-y pinch-zoom !important; padding-bottom: 28px !important; box-sizing: border-box !important; }',
                        '*, *::before, *::after { box-sizing: border-box !important; }',
                        'body > *, app-root, anon-layout, user-layout, bit-layout, #app, #root, main, [role="main"], .tw-h-screen, .h-screen { height: auto !important; min-height: 0 !important; max-height: none !important; overflow-y: visible !important; max-width: 100vw !important; min-width: 0 !important; }',
                        'anon-layout .tw-overflow-hidden, bit-layout .tw-overflow-hidden, app-root .tw-overflow-hidden, main.tw-overflow-hidden, body > .tw-overflow-hidden { overflow-y: auto !important; height: auto !important; max-height: none !important; }',
                        '.container, .wrapper, .content, .inner, .card, .panel, .popup, .popup-container, .popup-inner, .app, .app-container, .main { max-width: 100vw !important; min-width: 0 !important; }',
                        'button, input, select, textarea, a { max-width: 100% !important; word-break: break-word !important; }',
                        '[style*="position: fixed"], [style*="position:fixed"] { max-width: 100vw !important; width: 100% !important; left: 0 !important; right: 0 !important; }'
                    ].join(' ');
                    (document.head || document.documentElement).appendChild(style);
                }

                function unlockScroll() {
                    try {
                        var candidates = document.querySelectorAll('body, body > *, app-root, anon-layout, user-layout, bit-layout, main, [role="main"], .tw-overflow-hidden, .overflow-hidden, .tw-h-full, .tw-h-screen');
                        var vw = window.innerWidth || 360;
                        for (var i = 0; i < candidates.length; i++) {
                            var el = candidates[i];
                            if (!el || !el.style) continue;
                            var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                            if (rect && rect.width >= vw * 0.7 && rect.height >= 120) {
                                var cs = window.getComputedStyle(el);
                                if (cs.overflowY === 'hidden' || el.scrollHeight > el.clientHeight + 2) {
                                    el.style.setProperty('overflow-y', 'auto', 'important');
                                    el.style.setProperty('height', 'auto', 'important');
                                    el.style.setProperty('max-height', 'none', 'important');
                                }
                            }
                        }
                    } catch (_e) {}
                }
                unlockScroll();
                setTimeout(unlockScroll, 150);
                setTimeout(unlockScroll, 500);
                if (!window.__omniPopupScrollObserver) {
                    var timer = null;
                    window.__omniPopupScrollObserver = new MutationObserver(function() {
                        if (timer) clearTimeout(timer);
                        timer = setTimeout(unlockScroll, 80);
                    });
                    window.__omniPopupScrollObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
                }
            } catch (e) {}
        })();
    """.trimIndent().replace("\n", " ")

    tab.session.loadUri("javascript:$js")
}
