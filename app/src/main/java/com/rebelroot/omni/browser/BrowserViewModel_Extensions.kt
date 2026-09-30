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
import kotlinx.coroutines.delay
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

import android.content.Intent
import com.rebelroot.omni.media.StreamDownloadEngine

val WebExtension?.safeId: String?
    get() = if (this == null) null else try { id } catch (_: Throwable) { null }

val WebExtension?.safeMetaData: WebExtension.MetaData?
    get() = if (this == null) null else try { metaData } catch (_: Throwable) { null }

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
            val currentMap = sessionExtensionActions[tab.id]?.toMutableMap() ?: mutableMapOf()
            currentMap[id] = action
            sessionExtensionActions[tab.id] = currentMap
        }
    } else {
        defaultExtensionActions[id] = action
    }
}

fun BrowserViewModel.getActionForExtension(extensionId: String): WebExtension.Action? {
    val activeId = activeTabId ?: return defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
    return sessionExtensionActions[activeId]?.get(extensionId) ?: defaultExtensionActions[extensionId] ?: extensionActions[extensionId]
}

fun BrowserViewModel.openUserExtension(extension: WebExtension, context: Context) {
    val extId = extension.safeId ?: return
    val currentExtension = userExtensions.find { it.safeId == extId } ?: extension

    val activeAction = currentExtension.safeId?.let { getActionForExtension(it) }
    if (activeAction != null) {
        try {
            activeAction.click()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to click extension action for $extId", e)
        }
    }

    // Fallback: If no action popup registered yet or click didn't trigger a popup, open options page in a new tab if available
    val meta = currentExtension.safeMetaData
    val rawOptions = meta?.optionsPageUrl
    val baseUrl = meta?.baseUrl ?: ""
    val targetUrl = when {
        !rawOptions.isNullOrBlank() -> {
            if (rawOptions.startsWith("moz-extension://") || rawOptions.startsWith("http://") || rawOptions.startsWith("https://")) rawOptions
            else "${baseUrl.removeSuffix("/")}/${rawOptions.removePrefix("/")}"
        }
        else -> null
    }

    if (targetUrl != null) {
        createNewTab(context, targetUrl)
    } else {
        Toast.makeText(context, "${meta?.name ?: extId} is active", Toast.LENGTH_SHORT).show()
    }
}

fun BrowserViewModel.handleExtensionOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> {
    if (isNativeSheetOpen) {
        val result = GeckoResult<GeckoSession>()
        result.completeExceptionally(IllegalStateException("Blocked: Native toolbox/notes sheet is active."))
        return result
    }

    val oldSession = activeExtensionPopupSession
    if (oldSession != null) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                oldSession.close()
            } catch (_: Exception) {}
        }, 400)
    }

    val settings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
        .usePrivateMode(false)
        .allowJavascript(true)
        .viewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        .build()

    val session = GeckoSession(settings)

    // Show spinner while the popup page loads and reset measured height
    activeExtensionPopupLoading = true
    activeExtensionPopupContentHeightDp = null

    // Content delegate — dismiss popup if the extension page closes itself
    session.contentDelegate = object : GeckoSession.ContentDelegate {
        override fun onCloseRequest(session: GeckoSession) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                dismissExtensionPopup()
            }
        }
    }

    // Prompt delegate — handle height measurements, alerts/confirms, and <select> choice prompts
    session.promptDelegate = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val msg = prompt.message ?: ""
            if (msg.startsWith("OMNI_EXT_POPUP_HEIGHT:")) {
                val rawHeight = msg.removePrefix("OMNI_EXT_POPUP_HEIGHT:").trim().toIntOrNull()
                if (rawHeight != null && rawHeight > 0) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        activeExtensionPopupContentHeightDp = rawHeight.coerceIn(140, 2400)
                    }
                }
            }
            return GeckoResult.fromValue(prompt.dismiss())
        }
        override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            return GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
        }
        override fun onChoicePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ChoicePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val choices = prompt.choices ?: return GeckoResult.fromValue(prompt.dismiss())
            if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss())
            cancelChoicePrompt()
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            pendingChoicePrompt = BrowserViewModel.PendingChoicePrompt(
                geckoResult = result,
                prompt = prompt
            )
            return result
        }
    }

    // Progress delegate — track loading state, apply universal responsive scaling, and measure UI height
    session.progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                activeExtensionPopupLoading = false
            }
            if (success) {
                // Inject universal mobile responsive, scrollability, and dynamic height measurement
                // into every extension popup page (Firefox Mobile style).
                val fixJs = buildExtensionPopupUiJs()
                session.loadUri("javascript:$fixJs")
                val autofillBridgeJs = buildExtensionAutofillBridgeJs().replace("\n", " ")
                session.loadUri("javascript:$autofillBridgeJs")
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
            val url = request.uri ?: return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            return when {
                // Explicitly allow all moz-extension://, about:, blob:, and data: pages
                url.startsWith("moz-extension://") || url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:") || url.startsWith("javascript:") -> {
                    GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                // Intercept external http(s) links — open in main browser
                url.startsWith("http://") || url.startsWith("https://") -> {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dismissExtensionPopup()
                        loadUrl(url)
                    }
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                else -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }
    }

    val run = runtime
    if (run != null && !session.isOpen) {
        session.open(run)
    }

    android.os.Handler(android.os.Looper.getMainLooper()).post {
        activeExtensionPopupSession = session
        activeExtensionPopupName = extension.safeMetaData?.name ?: extension.safeId ?: "Extension"
        activeExtensionPopupId = extension.safeId ?: ""
    }

    return GeckoResult.fromValue(session)
}

fun BrowserViewModel.dismissExtensionPopup() {
    val sessionToClose = activeExtensionPopupSession
    activeExtensionPopupSession = null
    activeExtensionPopupName = ""
    activeExtensionPopupId = ""
    activeExtensionPopupLoading = true
    activeExtensionPopupContentHeightDp = null
    // Re-assert the active browser tab so extensions (e.g. Bitwarden inline autofill)
    // immediately see the web tab as the active tab after closing a popup.
    val currentActiveTab = tabs.find { it.id == activeTabId }
    if (currentActiveTab != null && currentActiveTab.session.isOpen) {
        try {
            runtime?.webExtensionController?.setTabActive(currentActiveTab.session, true)
        } catch (_: Exception) {}
    }
    if (sessionToClose != null) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                sessionToClose.close()
            } catch (_: Exception) {}
        }, 400)
    }
}

// Bump the version whenever the patch payload changes so `.xpi` archives patched by an earlier
// build are detected as stale and re-patched instead of being skipped.
private const val OMNI_EXT_PATCH_MARKER = "/* __OMNI_MOBILE_EXT_PATCH_V6__ */"

/**
 * Builds the JS polyfill & Bitwarden inline-autofill bootstrapper prepended directly to
 * `background.js` (and background scripts) inside installed `.xpi` archives so it executes
 * under `'self'` CSP at line 1 before `MainBackground` is constructed:
 * 1. Polyfills `chrome.windows` / `browser.windows` (absent on GeckoView Android), allowing
 *    `TabsBackground.init()` and `OverlayBackground.handleOverlayCiphersUpdate()` to succeed.
 * 2. Polyfills `chrome.windows.create` to open extension popout flows ("Unlock account",
 *    "New login", "View item") as browser tabs via `chrome.tabs.create`.
 * 3. Wraps `chrome.runtime.onMessage.addListener` and `chrome.tabs.query` so `windowId: -2`
 *    (`WINDOW_ID_CURRENT`) and `currentWindow: true` reliably resolve the active `http(s)` web tab.
 * 4. Ensures Bitwarden's `inlineMenuVisibility` defaults to `OnFieldFocus (1)`, optimizes mobile
 *    soft-keyboard `triggerOverlayReposition` debounce, and preloads inline autofill ciphers.
 */
internal fun buildExtensionAutofillBridgeJs(): String {
    return """
        $OMNI_EXT_PATCH_MARKER
        (function() {
            try {
                var rootWin = typeof globalThis !== 'undefined' ? globalThis : (typeof self !== 'undefined' ? self : window);
                function setupWindowsAndTabsPolyfill(win, bgRef) {
                    if (!win) return;
                    var c = win.chrome;
                    var b = win.browser;
                    if (!c) return;
                    var extOrigin = (win.location && win.location.origin) ? win.location.origin : '';
                    function resolveExtUrl(u) {
                        if (!u || typeof u !== 'string') return u;
                        if (u.indexOf('://') !== -1 || u.indexOf('about:') === 0 || u.indexOf('data:') === 0 || u.indexOf('blob:') === 0) return u;
                        if (extOrigin) return extOrigin.replace(/\/$/, '') + '/' + u.replace(/^\//, '');
                        return u;
                    }
                    var fakeWin = { id: -2, focused: true, type: 'normal', state: 'normal', incognito: false, alwaysOnTop: false, top: 0, left: 0, width: 380, height: 760 };
                    var noopEvent = { addListener: function(){}, removeListener: function(){}, hasListener: function(){ return false; } };
                    if (!c.windows) {
                        var winPolyfill = {
                            WINDOW_ID_NONE: -1,
                            WINDOW_ID_CURRENT: -2,
                            getCurrent: function(getInfo, cb) {
                                var fn = typeof getInfo === 'function' ? getInfo : cb;
                                if (typeof fn === 'function') { setTimeout(function(){ fn(fakeWin); }, 0); }
                                return Promise.resolve(fakeWin);
                            },
                            get: function(windowId, getInfo, cb) {
                                var fn = typeof getInfo === 'function' ? getInfo : cb;
                                if (typeof fn === 'function') { setTimeout(function(){ fn(fakeWin); }, 0); }
                                return Promise.resolve(fakeWin);
                            },
                            getLastFocused: function(getInfo, cb) {
                                var fn = typeof getInfo === 'function' ? getInfo : cb;
                                if (typeof fn === 'function') { setTimeout(function(){ fn(fakeWin); }, 0); }
                                return Promise.resolve(fakeWin);
                            },
                            getAll: function(getInfo, cb) {
                                var fn = typeof getInfo === 'function' ? getInfo : cb;
                                if (typeof fn === 'function') { setTimeout(function(){ fn([fakeWin]); }, 0); }
                                return Promise.resolve([fakeWin]);
                            },
                            create: function(createData, cb) {
                                var rawUrl = createData && createData.url ? (Array.isArray(createData.url) ? createData.url[0] : createData.url) : 'about:blank';
                                var fullUrl = resolveExtUrl(rawUrl);
                                if (c.tabs && typeof c.tabs.create === 'function') {
                                    return new Promise(function(resolve) {
                                        try {
                                            c.tabs.create({ url: fullUrl, active: true }, function(tab) {
                                                var res = Object.assign({}, fakeWin, { tabs: tab ? [tab] : [] });
                                                if (typeof cb === 'function') cb(res);
                                                resolve(res);
                                            });
                                        } catch (_e) {
                                            if (typeof cb === 'function') cb(fakeWin);
                                            resolve(fakeWin);
                                        }
                                    });
                                }
                                if (typeof cb === 'function') setTimeout(function(){ cb(fakeWin); }, 0);
                                return Promise.resolve(fakeWin);
                            },
                            remove: function(windowId, cb) {
                                if (typeof cb === 'function') setTimeout(cb, 0);
                                return Promise.resolve();
                            },
                            update: function(windowId, updateInfo, cb) {
                                if (typeof cb === 'function') setTimeout(function(){ cb(fakeWin); }, 0);
                                return Promise.resolve(fakeWin);
                            },
                            onFocusChanged: noopEvent,
                            onCreated: noopEvent,
                            onRemoved: noopEvent
                        };
                        try { c.windows = winPolyfill; } catch (_e) {}
                        try { if (b && !b.windows) b.windows = winPolyfill; } catch (_e) {}
                    }
                    if (c.runtime && c.runtime.onMessage && typeof c.runtime.onMessage.addListener === 'function' && !c.runtime.onMessage.__omniMsgPatched) {
                        var origAddListener = c.runtime.onMessage.addListener.bind(c.runtime.onMessage);
                        c.runtime.onMessage.__omniMsgPatched = true;
                        c.runtime.onMessage.addListener = function(fn) {
                            if (typeof fn !== 'function') return origAddListener(fn);
                            return origAddListener(function(message, sender, sendResponse) {
                                try {
                                    if (sender && sender.tab && sender.tab.url && (sender.tab.url.indexOf('http://') === 0 || sender.tab.url.indexOf('https://') === 0)) {
                                        win.__omniLastSenderTab = sender.tab;
                                        if (bgRef) bgRef.__omniLastSenderTab = sender.tab;
                                    }
                                } catch (_e) {}
                                return fn.apply(this, arguments);
                            });
                        };
                    }
                    if (c.tabs && typeof c.tabs.query === 'function' && !c.tabs.__omniTabsQueryPatched) {
                        var origQuery = c.tabs.query.bind(c.tabs);
                        c.tabs.__omniTabsQueryPatched = true;
                        c.tabs.query = function(queryInfo, callback) {
                            var q = Object.assign({}, queryInfo || {});
                            if (q.windowId === -2 || q.windowId === -1) {
                                delete q.windowId;
                                q.currentWindow = true;
                            }
                            function pickWebTabs(tabsList, allTabsList) {
                                var pool = (tabsList && tabsList.length > 0) ? tabsList : (allTabsList || []);
                                var httpTabs = pool.filter(function(t) {
                                    return t && t.url && (t.url.indexOf('http://') === 0 || t.url.indexOf('https://') === 0);
                                });
                                if (httpTabs.length > 0) {
                                    var activeHttp = httpTabs.filter(function(t) { return t.active; });
                                    return activeHttp.length > 0 ? activeHttp : [httpTabs[httpTabs.length - 1]];
                                }
                                var senderFallback = (bgRef && bgRef.__omniLastSenderTab) || (win && win.__omniLastSenderTab);
                                if (senderFallback && senderFallback.url) {
                                    return [senderFallback];
                                }
                                return (tabsList && tabsList.length > 0) ? tabsList : (allTabsList || []);
                            }
                            if (typeof callback === 'function') {
                                return origQuery(q, function(tabs) {
                                    var hasHttp = tabs && tabs.some(function(t) { return t && t.url && t.url.indexOf('http') === 0; });
                                    if (hasHttp || !(q.active || q.currentWindow)) {
                                        callback(tabs || []);
                                        return;
                                    }
                                    origQuery({}, function(allTabs) {
                                        callback(pickWebTabs(tabs, allTabs));
                                    });
                                });
                            }
                            return new Promise(function(resolve) {
                                origQuery(q, function(tabs) {
                                    var hasHttp = tabs && tabs.some(function(t) { return t && t.url && t.url.indexOf('http') === 0; });
                                    if (hasHttp || !(q.active || q.currentWindow)) {
                                        resolve(tabs || []);
                                        return;
                                    }
                                    origQuery({}, function(allTabs) {
                                        resolve(pickWebTabs(tabs, allTabs));
                                    });
                                });
                            });
                        };
                    }
                }

                var bgWin = null;
                try {
                    if (typeof chrome !== 'undefined' && chrome.extension && typeof chrome.extension.getBackgroundPage === 'function') {
                        bgWin = chrome.extension.getBackgroundPage();
                    }
                } catch (_e) {}

                setupWindowsAndTabsPolyfill(rootWin, bgWin || rootWin);
                if (bgWin && bgWin !== rootWin) {
                    setupWindowsAndTabsPolyfill(bgWin, bgWin);
                }

                function patchOverlayBackground(main, targetBg) {
                    var ob = main ? main.overlayBackground : null;
                    if (!ob || ob.__omniOverlayPatched) return Boolean(ob && ob.__omniOverlayPatched);
                    ob.__omniOverlayPatched = true;
                    if (typeof ob.handleOverlayCiphersUpdate === 'function') {
                        ob.updateOverlayCiphers = async function(refocusField, updateAllCipherTypes) {
                            try {
                                await ob.handleOverlayCiphersUpdate({
                                    updateAllCipherTypes: updateAllCipherTypes !== undefined ? updateAllCipherTypes : true,
                                    refocusField: Boolean(refocusField)
                                });
                            } catch (_e) {}
                        };
                    }
                    if (typeof ob.triggerOverlayReposition === 'function') {
                        ob.triggerOverlayReposition = async function(sender) {
                            try {
                                if (!ob.checkShouldRepositionInlineMenu(sender)) return;
                                ob.resetFocusedFieldSubFrameOffsets(sender);
                                if (ob.__omniRepositionTimer) clearTimeout(ob.__omniRepositionTimer);
                                ob.__omniRepositionTimer = setTimeout(function() {
                                    try {
                                        if (typeof ob.repositionInlineMenu === 'function') {
                                            ob.repositionInlineMenu(sender);
                                        }
                                    } catch (_e) {}
                                }, 140);
                            } catch (_e) {}
                        };
                    }
                    if (typeof ob.repositionInlineMenu === 'function') {
                        var origReposition = ob.repositionInlineMenu.bind(ob);
                        ob.repositionInlineMenu = async function(sender) {
                            try {
                                if (!sender || !sender.tab) return;
                                if (ob.isFieldCurrentlyFocused && sender.tab.id != null) {
                                    var frameId = ob.focusedFieldData ? ob.focusedFieldData.frameId : 0;
                                    if (targetBg.chrome && targetBg.chrome.tabs && typeof targetBg.chrome.tabs.sendMessage === 'function') {
                                        await new Promise(function(resolve) {
                                            try {
                                                targetBg.chrome.tabs.sendMessage(
                                                    sender.tab.id,
                                                    { command: 'checkIsMostRecentlyFocusedFieldWithinViewport' },
                                                    { frameId: frameId || 0 },
                                                    function() { resolve(); }
                                                );
                                            } catch (_e) { resolve(); }
                                        });
                                    }
                                    if (frameId != null && frameId > 0 && ob.rebuildSubFrameOffsets$) {
                                        ob.rebuildSubFrameOffsets$.next(sender);
                                    }
                                    if (ob.startUpdateInlineMenuPosition$) {
                                        ob.startUpdateInlineMenuPosition$.next(sender);
                                    }
                                    return;
                                }
                            } catch (_e) {}
                            return origReposition(sender);
                        };
                    }
                    if (typeof ob.openInlineMenu === 'function') {
                        var origOpenInlineMenu = ob.openInlineMenu.bind(ob);
                        ob.openInlineMenu = async function(sender, isOpeningFullInlineMenu) {
                            try {
                                if (sender && sender.tab && sender.tab.url && sender.tab.url.indexOf('http') === 0) {
                                    targetBg.__omniLastSenderTab = sender.tab;
                                }
                                if ((!ob.inlineMenuCiphers || ob.inlineMenuCiphers.size === 0) && typeof ob.handleOverlayCiphersUpdate === 'function') {
                                    await ob.handleOverlayCiphersUpdate({ updateAllCipherTypes: true, refocusField: false });
                                }
                            } catch (_e) {}
                            return origOpenInlineMenu(sender, isOpeningFullInlineMenu);
                        };
                    }
                    try { ob.updateOverlayCiphers(false, true); } catch (_e) {}

                    // Mobile viewport guard. Bitwarden's list iframe is force-closed when a
                    // viewport boundary check fails; on GeckoView visualViewport.height shrinks
                    // with the soft keyboard / bottom toolbars. The check itself lives in the
                    // page-side content script (patched directly, see patchSingleExtensionXpi),
                    // but drop any list-targeted force close that still reaches the background.
                    if (typeof ob.closeInlineMenu === 'function' && !ob.__omniClosePatched) {
                        ob.__omniClosePatched = true;
                        var origCloseInlineMenu = ob.closeInlineMenu.bind(ob);
                        ob.closeInlineMenu = function(sender, opts) {
                            try {
                                if (opts && opts.forceCloseInlineMenu && opts.overlayElement === 'autofill-inline-menu-list') {
                                    return;
                                }
                            } catch (_e) {}
                            return origCloseInlineMenu.apply(this, arguments);
                        };
                    }

                    return true;
                }

                function tryBootstrapBitwarden(targetBg) {
                    if (!targetBg || !targetBg.bitwardenMain) return false;
                    var main = targetBg.bitwardenMain;
                    if (!main.autofillSettingsService || !main.autofillService) return false;

                    if (typeof main.initOverlayAndTabsBackground === 'function' && !main.__omniInitOverlayWrapped) {
                        main.__omniInitOverlayWrapped = true;
                        var origInitOverlay = main.initOverlayAndTabsBackground.bind(main);
                        main.initOverlayAndTabsBackground = async function() {
                            var res = await origInitOverlay.apply(this, arguments);
                            try { patchOverlayBackground(main, targetBg); } catch (_e) {}
                            return res;
                        };
                    }

                    if (main.autofillService && !main.autofillService.__omniAutofillServicePatched) {
                        main.autofillService.__omniAutofillServicePatched = true;
                        if (typeof main.autofillService.getBootstrapAutofillContentScript === 'function') {
                            var origGetScript = main.autofillService.getBootstrapAutofillContentScript.bind(main.autofillService);
                            main.autofillService.getBootstrapAutofillContentScript = async function(activeAccount) {
                                var scriptName = await origGetScript(activeAccount);
                                if (activeAccount && scriptName === 'bootstrap-autofill-overlay-notifications.js') {
                                    try {
                                        var vis = await main.autofillService.getInlineMenuVisibility();
                                        if (vis === 0 && !targetBg.__omniInlineVisCheckedOnce) {
                                            targetBg.__omniInlineVisCheckedOnce = true;
                                            await main.autofillSettingsService.setInlineMenuVisibility(1);
                                            return 'bootstrap-autofill-overlay.js';
                                        }
                                    } catch (_e) {}
                                }
                                return scriptName;
                            };
                        }
                    }

                    var overlayReady = patchOverlayBackground(main, targetBg);

                    if (!targetBg.__omniStorageInitChecked && targetBg.chrome && targetBg.chrome.storage && targetBg.chrome.storage.local) {
                        targetBg.__omniStorageInitChecked = true;
                        targetBg.chrome.storage.local.get(['global_autofillSettingsLocal_inlineMenuVisibility', '__omni_bw_inline_autofill_v4'], function(items) {
                            try {
                                var visKey = 'global_autofillSettingsLocal_inlineMenuVisibility';
                                var curVis = items ? items[visKey] : undefined;
                                var alreadySeeded = items && items['__omni_bw_inline_autofill_v4'];
                                if (!alreadySeeded && (curVis === undefined || curVis === null || curVis === 0)) {
                                    targetBg.chrome.storage.local.set({ '__omni_bw_inline_autofill_v4': true }, function() {});
                                    if (main.autofillSettingsService && typeof main.autofillSettingsService.setInlineMenuVisibility === 'function') {
                                        Promise.resolve(main.autofillSettingsService.setInlineMenuVisibility(1)).then(function() {
                                            try {
                                                if (main.autofillService && typeof main.autofillService.reloadAutofillScripts === 'function') {
                                                    main.autofillService.reloadAutofillScripts();
                                                }
                                                if (main.overlayBackground && typeof main.overlayBackground.updateOverlayCiphers === 'function') {
                                                    main.overlayBackground.updateOverlayCiphers(false, true);
                                                }
                                            } catch (_e) {}
                                        });
                                    }
                                } else {
                                    try {
                                        if (main.autofillService && typeof main.autofillService.reloadAutofillScripts === 'function') {
                                            main.autofillService.reloadAutofillScripts();
                                        }
                                        if (main.overlayBackground && typeof main.overlayBackground.updateOverlayCiphers === 'function') {
                                            main.overlayBackground.updateOverlayCiphers(false, true);
                                        }
                                    } catch (_e) {}
                                }
                            } catch (_e) {}
                        });
                    }
                    return overlayReady;
                }

                var targetBg = bgWin || rootWin;
                if (!tryBootstrapBitwarden(targetBg)) {
                    var attempts = 0;
                    var pollId = setInterval(function() {
                        attempts++;
                        if (tryBootstrapBitwarden(bgWin || rootWin) || attempts >= 40) {
                            clearInterval(pollId);
                        }
                    }, 350);
                }
            } catch (_err) {}
        })();
    """.trimIndent()
}

/**
 * Builds the popup UI responsive + height measurement script without `MutationObserver(attributes: true)`
 * or `ResizeObserver(document.body)` feedback loops (which previously caused a 60ms infinite
 * layout/WebRender GL loop on Android 9 / API 28).
 */
internal fun buildExtensionPopupUiJs(): String {
    return """
        ;(function() {
            try {
                function applyPopupMobileResponsive() {
                    try {
                        var existing = document.querySelector('meta[name="viewport"]');
                        if (!existing) {
                            var meta = document.createElement('meta');
                            meta.name = 'viewport';
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                            (document.head || document.documentElement).appendChild(meta);
                        } else if (!existing.content || existing.content.indexOf('width=device-width') === -1) {
                            existing.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                        }
                        if (!document.getElementById('omni-ext-popup-responsive')) {
                            var style = document.createElement('style');
                            style.id = 'omni-ext-popup-responsive';
                            style.innerHTML = [
                                'html { max-width: 100vw !important; width: 100% !important; min-width: 0 !important; height: 100% !important; display: flex !important; flex-direction: column !important; overflow-x: hidden !important; overflow-y: auto !important; -webkit-overflow-scrolling: touch !important; }',
                                'body { max-width: 100vw !important; width: 100% !important; min-width: 0 !important; height: 100% !important; min-height: 100% !important; display: flex !important; flex-direction: column !important; overflow-x: hidden !important; overflow-y: auto !important; overscroll-behavior-y: contain !important; -webkit-overflow-scrolling: touch !important; touch-action: pan-x pan-y pinch-zoom !important; box-sizing: border-box !important; }',
                                '*, *::before, *::after { box-sizing: border-box !important; }',
                                'app-root, #app, #root, bit-layout, anon-layout, user-layout { display: flex !important; flex-direction: column !important; width: 100% !important; height: 100% !important; flex: 1 1 0% !important; min-height: 0 !important; overflow: hidden !important; }',
                                'app-root > *, #app > *, #root > * { flex-shrink: 0 !important; }',
                                'main, [role="main"], .tw-flex-1, .flex-1 { flex: 1 1 0% !important; overflow-y: auto !important; min-height: 0 !important; max-width: 100vw !important; }',
                                'anon-layout .tw-overflow-hidden, bit-layout .tw-overflow-hidden, app-root .tw-overflow-hidden, main.tw-overflow-hidden, body > .tw-overflow-hidden { overflow-y: auto !important; }',
                                '.container, .wrapper, .content, .inner, .card, .panel, .popup, .popup-container, .popup-inner, .app, .app-container, .main { max-width: 100vw !important; min-width: 0 !important; }',
                                'button, input, select, textarea, a { max-width: 100% !important; word-break: break-word !important; }',
                                'nav, footer { position: sticky !important; bottom: 0 !important; top: auto !important; width: 100% !important; flex-shrink: 0 !important; z-index: 100 !important; }',
                                '.tw-h-screen, .h-screen { height: 100% !important; }',
                                '.tw-h-full, .h-full { height: 100% !important; }'
                            ].join(' ');
                            (document.head || document.documentElement).appendChild(style);
                        }
                    } catch (_e) {}
                }
                function measureAndReportHeight() {
                    try {
                        var body = document.body;
                        var docEl = document.documentElement;
                        if (!body || !docEl) return;
                        var bodyBg = window.getComputedStyle(body).backgroundColor;
                        if (bodyBg && bodyBg !== 'rgba(0, 0, 0, 0)' && bodyBg !== 'transparent' && docEl.style.backgroundColor !== bodyBg) {
                            docEl.style.backgroundColor = bodyBg;
                        }
                        var zoom = parseFloat(docEl.style.zoom) || 1;
                        var bodyRect = body.getBoundingClientRect();
                        var maxBottom = 0;
                        var fixedHeight = 0;
                        var els = body.querySelectorAll('body > *, app-root, main, [role="main"], form, footer, nav, button, .card, .panel');
                        if (!els || els.length === 0) {
                            els = body.children;
                        }
                        var limit = Math.min(els.length, 120);
                        for (var i = 0; i < limit; i++) {
                            var el = els[i];
                            var cs = window.getComputedStyle(el);
                            if (cs.display === 'none' || cs.visibility === 'hidden') continue;
                            var r = el.getBoundingClientRect();
                            if (cs.position === 'fixed' || cs.position === 'sticky') {
                                if (r.height > 0) fixedHeight += r.height;
                                continue;
                            }
                            if (r.height > 0 && r.width > 0) {
                                var relBottom = r.bottom - bodyRect.top + 16;
                                if (relBottom > maxBottom) maxBottom = relBottom;
                            }
                            if (el.scrollHeight > maxBottom && el.clientHeight > 0) {
                                maxBottom = Math.max(maxBottom, el.scrollHeight + 16);
                            }
                        }
                        maxBottom += fixedHeight;
                        if (maxBottom < 80) {
                            maxBottom = body.scrollHeight || body.offsetHeight || 0;
                        }
                        var finalHeight = Math.ceil(maxBottom * zoom);
                        if (finalHeight >= 120 && Math.abs(finalHeight - (window.__omniLastPopupHeight || 0)) >= 12) {
                            window.__omniLastPopupHeight = finalHeight;
                            window.alert('OMNI_EXT_POPUP_HEIGHT:' + finalHeight);
                        }
                    } catch (_e) {}
                }
                window.__omniMeasurePopupHeight = measureAndReportHeight;
                function unlockScrollAndMeasure() {
                    applyPopupMobileResponsive();
                    try {
                        var candidates = document.querySelectorAll('.tw-overflow-hidden, .overflow-hidden');
                        for (var i = 0; i < candidates.length; i++) {
                            var el = candidates[i];
                            if (!el || !el.style || el.getAttribute('data-omni-unlocked') === '1') continue;
                            var cs = window.getComputedStyle(el);
                            if (cs.position === 'fixed' || cs.position === 'sticky') continue;
                            if (cs.overflowY === 'hidden') {
                                el.setAttribute('data-omni-unlocked', '1');
                                el.style.setProperty('overflow-y', 'auto', 'important');
                            }
                        }
                    } catch (_e) {}
                    measureAndReportHeight();
                }
                function initPopupObservers() {
                    unlockScrollAndMeasure();
                    setTimeout(unlockScrollAndMeasure, 180);
                    setTimeout(unlockScrollAndMeasure, 550);
                    if (!window.__omniPopupScrollObserver && (document.documentElement || document.body)) {
                        var timer = null;
                        window.__omniPopupScrollObserver = new MutationObserver(function() {
                            if (timer) clearTimeout(timer);
                            timer = setTimeout(unlockScrollAndMeasure, 180);
                        });
                        // Observe childList ONLY (never attributes: true) so style adjustments never re-trigger the observer
                        window.__omniPopupScrollObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
                    }
                }
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initPopupObservers);
                } else {
                    initPopupObservers();
                }
            } catch (_e) {}
        })();
    """.trimIndent()
}

/**
 * Builds the JS patch prepended to extension popup scripts (`popup/main.js`, etc.) inside `.xpi`
 * bundles so that popup responsive layout, scroll unlocking, dynamic height measurement, and
 * `chrome.windows` / `chrome.tabs.query` polyfills execute from `'self'` without being blocked
 * by extension Content-Security-Policy (`script-src 'self'`).
 */
internal fun buildExtensionPopupPatchJs(): String {
    val bridgeJs = buildExtensionAutofillBridgeJs()
    val popupUiJs = buildExtensionPopupUiJs()
    return "$bridgeJs\n$popupUiJs"
}

/**
 * Patches installed `.xpi` files in the Gecko profile's `extensions/` directory BEFORE
 * `GeckoRuntime.create` is called (so no `.xpi` is memory-mapped by Gecko while being rewritten).
 *
 * Returns `true` if one or more `.xpi` archives were patched.
 */
internal fun patchInstalledUserExtensionsForMobile(context: Context): Boolean {
    val mozillaDir = java.io.File(context.applicationContext.filesDir, "mozilla")
    if (!mozillaDir.exists() || !mozillaDir.isDirectory) return false
    var anyPatched = false

    val profileDirs = mozillaDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
    for (profileDir in profileDirs) {
        val extDir = java.io.File(profileDir, "extensions")
        if (!extDir.exists() || !extDir.isDirectory) continue
        val xpiFiles = extDir.listFiles { f -> f.isFile && f.name.endsWith(".xpi", ignoreCase = true) } ?: continue
        var profilePatched = false
        for (xpiFile in xpiFiles) {
            try {
                if (patchSingleExtensionXpi(xpiFile)) {
                    profilePatched = true
                    anyPatched = true
                    Log.i(TAG, "Patched installed extension XPI for mobile autofill & popup compatibility: ${xpiFile.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to patch extension XPI ${xpiFile.name}", e)
            }
        }
        if (profilePatched) {
            // Clear Gecko's compiled script startupCache so patched background.js / popup scripts
            // are re-read from the XPI rather than served from stale cached bytecode.
            val startupCacheDir = java.io.File(profileDir, "startupCache")
            if (startupCacheDir.exists() && startupCacheDir.isDirectory) {
                startupCacheDir.listFiles()?.forEach { cacheFile ->
                    runCatching { cacheFile.delete() }
                }
            }
        }
    }
    return anyPatched
}

/**
 * Downloads an extension `.xpi` from `url` to a temporary file in `cacheDir` and applies
 * `patchSingleExtensionXpi` BEFORE GeckoView installs and memory-maps it. Falls back to the
 * original `url` if downloading or patching fails.
 */
internal fun preparePatchedExtensionInstallUrl(url: String, context: Context): String {
    return try {
        val cacheDir = java.io.File(context.cacheDir, "omni_ext_install")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tmpXpi = java.io.File(cacheDir, "ext_${System.currentTimeMillis()}.xpi")
        when {
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:138.0) Gecko/138.0 Firefox/138.0")
                }
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(tmpXpi).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            url.startsWith("file://", ignoreCase = true) -> {
                val srcPath = android.net.Uri.parse(url).path ?: return url
                val srcFile = java.io.File(srcPath)
                if (!srcFile.exists()) return url
                srcFile.copyTo(tmpXpi, overwrite = true)
            }
            else -> return url
        }
        if (tmpXpi.exists() && tmpXpi.length() > 0L) {
            patchSingleExtensionXpi(tmpXpi)
            android.net.Uri.fromFile(tmpXpi).toString()
        } else {
            url
        }
    } catch (e: Exception) {
        Log.w(TAG, "preparePatchedExtensionInstallUrl fallback to original URL ($url): ${e.message}")
        url
    }
}

private fun patchSingleExtensionXpi(xpiFile: java.io.File): Boolean {
    if (!xpiFile.exists() || xpiFile.length() == 0L) return false
    val origLastModified = xpiFile.lastModified()

    val bgScripts = mutableSetOf("background.js")
    val popupScripts = mutableSetOf("popup/main.js")
    var alreadyPatched = false

    java.util.zip.ZipFile(xpiFile).use { zip ->
        val manifestEntry = zip.getEntry("manifest.json") ?: return false
        val manifestText = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        runCatching {
            val json = org.json.JSONObject(manifestText)
            val bgObj = json.optJSONObject("background")
            val scriptsArr = bgObj?.optJSONArray("scripts")
            if (scriptsArr != null) {
                for (i in 0 until scriptsArr.length()) {
                    val s = scriptsArr.optString(i)?.removePrefix("/")?.trim()
                    if (!s.isNullOrEmpty()) bgScripts.add(s)
                }
            }
        }
        val primaryBgEntry = bgScripts.firstNotNullOfOrNull { zip.getEntry(it) }
        if (primaryBgEntry != null) {
            val head = zip.getInputStream(primaryBgEntry).bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(256)
                val n = reader.read(buf)
                if (n > 0) String(buf, 0, n) else ""
            }
            if (head.contains(OMNI_EXT_PATCH_MARKER)) {
                alreadyPatched = true
            }
        }
    }

    if (alreadyPatched) return false

    val bgPatchBytes = (buildExtensionAutofillBridgeJs() + "\n").toByteArray(Charsets.UTF_8)
    val popupPatchBytes = (buildExtensionPopupPatchJs() + "\n").toByteArray(Charsets.UTF_8)
    // Content-script patch for the Bitwarden inline-menu bundles
    // (`bootstrap-autofill-overlay.js` and `bootstrap-autofill-overlay-menu.js`). These run
    // in the page and drive the inline suggestion list. On GeckoView the viewport boundary
    // check (`isElementCompletelyWithinViewport`) fails as soon as the soft keyboard or the
    // bottom toolbars shrink `visualViewport.height`, so the service force-closes the menu it
    // just opened. Neutralise both the check and the viewport-triggered close command at the
    // source by wrapping the runtime port used to talk to the background overlay.
    val overlayContentPatch = """
        $OMNI_EXT_PATCH_MARKER
        ;(function(){try{
            if (!/Android|Mobile/i.test((typeof navigator !== 'undefined' && navigator.userAgent) || '')) return;
            window.__omniInlineMenuContentPatched = true;
            var LIST_PORT = 'autofill-inline-menu-list-port';
            var LIST_ELEMENT = 'autofill-inline-menu-list';
            var LIST_MAX_HEIGHT = 180;   // matches Bitwarden's own max-height for the list
            var LIST_MIN_HEIGHT = 96;
            function clampListStyles(styles) {
                try {
                    if (!styles) return styles;
                    var vv = (window.visualViewport && window.visualViewport.height) || window.innerHeight || 0;
                    if (!vv) return styles;
                    var top = parseInt(styles.top, 10);
                    var h = parseInt(styles.height, 10);
                    // Bitwarden sends only width/top/left for the list position; the real height
                    // arrives in a later message (or never, on GeckoView). Without it the list
                    // iframe keeps its initial 0px height and renders as a bordered "line".
                    if (isNaN(h) || h <= 0) {
                        h = Math.min(LIST_MAX_HEIGHT, Math.max(LIST_MIN_HEIGHT, vv - 8));
                        styles.height = h + 'px';
                    } else if (h > LIST_MAX_HEIGHT) {
                        h = LIST_MAX_HEIGHT;
                        styles.height = h + 'px';
                    }
                    // Keep the whole list inside the visible viewport (soft keyboard / toolbars).
                    if (!isNaN(top) && top + h > vv - 4) {
                        styles.top = Math.max(0, vv - h - 4) + 'px';
                    }
                } catch (_e) {}
                return styles;
            }
            function patchPort(port) {
                if (!port) return port;
                // Drop viewport-triggered close commands so the suggestion list stays open.
                if (typeof port.postMessage === 'function' && !port.__omniCloseDropPatched) {
                    port.__omniCloseDropPatched = true;
                    var origPost = port.postMessage.bind(port);
                    port.postMessage = function(msg) {
                        if (msg && msg.overlayElement === LIST_ELEMENT &&
                            (msg.command === 'closeAutofillInlineMenu' || msg.command === 'updateAutofillInlineMenuPositionAndClose')) {
                            return;
                        }
                        return origPost(msg);
                    };
                }
                // Size the list iframe when the background hands out its position.
                if (port.name === LIST_PORT && port.onMessage && typeof port.onMessage.addListener === 'function' && !port.__omniListClampPatched) {
                    port.__omniListClampPatched = true;
                    var origAddListener = port.onMessage.addListener.bind(port.onMessage);
                    port.onMessage.addListener = function(fn) {
                        if (typeof fn !== 'function') return origAddListener(fn);
                        return origAddListener(function(msg) {
                            try {
                                if (msg && msg.command === 'updateAutofillInlineMenuPosition') {
                                    msg.styles = clampListStyles(msg.styles || {});
                                }
                            } catch (_e) {}
                            return fn.apply(this, arguments);
                        });
                    };
                }
                return port;
            }
            var api = (typeof chrome !== 'undefined' && chrome.runtime) ? chrome
                    : (typeof browser !== 'undefined' && browser.runtime) ? browser : null;
            // Distinct flag from earlier patch versions so this wrapper still installs when an
            // already-patched bundle is re-patched (stale prepended code stays ahead of it).
            if (api && api.runtime && typeof api.runtime.connect === 'function' && !api.runtime.__omniConnectClampPatched) {
                api.runtime.__omniConnectClampPatched = true;
                var origConnect = api.runtime.connect.bind(api.runtime);
                api.runtime.connect = function() { return patchPort(origConnect.apply(this, arguments)); };
            }
        }catch(_e){}})();
    """.trimIndent()
    val overlayContentPatchBytes = (overlayContentPatch + "\n").toByteArray(Charsets.UTF_8)
    // Match by archive basename so the patch still applies if the bundle layout nests the
    // content scripts in a different directory (e.g. `content/` vs a hashed chunk path).
    val contentScriptNamesToPatch = setOf(
        "bootstrap-autofill-overlay.js",
        "bootstrap-autofill-overlay-menu.js"
    )
    val tmpFile = java.io.File(xpiFile.parentFile, "${'$'}{xpiFile.name}.omni_patch.tmp")
    if (tmpFile.exists()) tmpFile.delete()

    java.util.zip.ZipFile(xpiFile).use { zipIn ->
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(tmpFile))).use { zipOut ->
            val entries = zipIn.entries()
            val buf = ByteArray(16384)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                // Strip META-INF signatures so Gecko treats the patched archive as unsigned
                // (allowed because MOZ_REQUIRE_SIGNING / xpinstall.signatures.required is false)
                // rather than failing signature verification on modified script entries.
                if (name.startsWith("META-INF/", ignoreCase = true)) {
                    continue
                }
                val newEntry = java.util.zip.ZipEntry(name)
                newEntry.time = entry.time
                zipOut.putNextEntry(newEntry)
                if (!entry.isDirectory) {
                    val normalized = name.removePrefix("/")
                    val isContentOverlay = normalized.substringAfterLast('/') in contentScriptNamesToPatch
                    when {
                        normalized in bgScripts -> {
                            zipOut.write(bgPatchBytes)
                        }
                        normalized in popupScripts || (normalized.startsWith("popup/") && normalized.endsWith("main.js")) -> {
                            zipOut.write(popupPatchBytes)
                        }
                        isContentOverlay -> {
                            zipOut.write(overlayContentPatchBytes)
                        }
                    }
                    if (isContentOverlay) {
                        // The inline-menu viewport/focus logic lives inside these bundles and is
                        // unreachable from a prepended script (private class methods), so rewrite
                        // the two offending expressions in place before writing the entry.
                        val original = zipIn.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        zipOut.write(rewriteInlineMenuScript(original).toByteArray(Charsets.UTF_8))
                    } else {
                        zipIn.getInputStream(entry).use { input ->
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                zipOut.write(buf, 0, read)
                            }
                        }
                    }
                }
                zipOut.closeEntry()
            }
        }
    }

    if (!tmpFile.renameTo(xpiFile)) {
        tmpFile.copyTo(xpiFile, overwrite = true)
        tmpFile.delete()
    }
    // Preserve original lastModified so XPIStates.scanForChanges keeps the existing XPIState entry active.
    xpiFile.setLastModified(origLastModified)
    return true
}

/**
 * Applies the minimum source edits Bitwarden's inline autofill menu needs to work under
 * GeckoView. Both replacements are guarded so an unrecognised (future) bundle is left untouched.
 *
 * 1. `AutofillInlineMenuIframeService.updateIframePosition` early-returns while
 *    `globalThis.document.hasFocus()` is false. On GeckoView the page document loses focus as soon
 *    as the user taps the inline-menu button (focus moves into the extension iframe), so the
 *    position and height styles for the suggestion list are never applied and the list iframe
 *    stays at its initial `height: 0px`. With its 1px solid border that renders as a horizontal
 *    "line" instead of the cipher list.
 * 2. `isElementCompletelyWithinViewport` force-closes the list whenever it is not fully inside
 *    `visualViewport`. With the soft keyboard up that check almost always fails on mobile, so the
 *    list is closed again immediately after it opens.
 */
internal fun rewriteInlineMenuScript(source: String): String {
    var out = source
    out = out.replace(
        "if (!position || !globalThis.document.hasFocus()) {",
        "if (!position) {"
    )
    out = out.replace(
        "isElementCompletelyWithinViewport(elementPosition) {",
        "isElementCompletelyWithinViewport(elementPosition) { return true;"
    )
    return out
}

/**
 * Re-asserts the active tab on extension readiness without modifying live mmap'd `.xpi` files.
 */
internal fun BrowserViewModel.ensureExtensionAutofillReady(extension: WebExtension) {
    val run = runtime ?: return
    val currentActiveTab = tabs.find { it.id == activeTabId }
    if (currentActiveTab != null && currentActiveTab.session.isOpen && !currentActiveTab.isSuspended) {
        try {
            run.webExtensionController.setTabActive(currentActiveTab.session, true)
        } catch (_: Exception) {}
    }
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
                setupWebExtensionDelegates(it)
            }
            Log.i(TAG, "Aggressive Media Grabber active.")
        },
        { error ->
            Log.e(TAG, "Failed to load Aggressive Media Grabber", error)
        }
    )
}

internal fun BrowserViewModel.setupNativeAppMessageDelegate(extension: WebExtension) {
    if (extension.id.isNullOrEmpty()) return
    try {
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {

        // Maximum size for extension JSON messages to prevent DoS via memory exhaustion
        private val MAX_MESSAGE_STRING_LENGTH = 1_000_000 // 1 MB

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
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
                    val sizeBytes = if (message is org.json.JSONObject) {
                        if (message.has("sizeBytes") && !message.isNull("sizeBytes")) message.optLong("sizeBytes", -1L).takeIf { it > 0 } else null
                    } else {
                        ((message as? Map<*, *>)?.get("sizeBytes") as? Number)?.toLong()?.takeIf { it > 0 }
                    }
                    if (url != null) {
                        mediaInterceptor.onAggressiveMediaGrabbed(url, mime ?: "video/mp4", cookies, sizeBytes)
                    }
                } else if (type == "REQUEST_HANDOFF") {
                    handleRequestHandoff(message, sender)
                } else if (type == "REQUEST_DOWNLOAD") {
                    handleRequestDownload(message, sender)
                } else if (type == "SITE_DOWNLOAD_REQUEST") {
                    handleSiteDownloadRequest(message, sender)
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
    } catch (e: Exception) {
        Log.e(TAG, "Failed to set native app message delegate for ${extension.id}", e)
    }
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
                MediaSourceType.DIRECT_WEBM -> MediaInterceptor.MediaType.WEBM
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
 * Handles SITE_DOWNLOAD_REQUEST directly from the site player overlay button.
 * Starts the download immediately without going through the resolution pipeline,
 * making it reliable for the Quetta-style download button on video elements.
 */
private fun BrowserViewModel.handleSiteDownloadRequest(message: Any, sender: WebExtension.MessageSender? = null) {
    val rawUrl = if (message is org.json.JSONObject) {
        if (message.has("url")) message.getString("url") else null
    } else {
        (message as? Map<*, *>)?.get("url") as? String
    } ?: ""

    if (rawUrl.isBlank()) {
        Log.w(TAG, "📥 SITE_DOWNLOAD_REQUEST ignored — no URL provided")
        return
    }

    val title = if (message is org.json.JSONObject) {
        if (message.has("title")) message.getString("title") else null
    } else {
        (message as? Map<*, *>)?.get("title") as? String
    } ?: "video_${System.currentTimeMillis()}"

    val mimeType = if (message is org.json.JSONObject) {
        if (message.has("mimeType")) message.getString("mimeType") else null
    } else {
        (message as? Map<*, *>)?.get("mimeType") as? String
    } ?: "video/mp4"

    val mediaType = when {
        mimeType.contains("m3u8") || mimeType.contains("mpegurl") -> MediaInterceptor.MediaType.HLS
        mimeType.contains("mpd") || mimeType.contains("dash") -> MediaInterceptor.MediaType.DASH
        mimeType.contains("webm") -> MediaInterceptor.MediaType.WEBM
        mimeType.contains("audio") -> MediaInterceptor.MediaType.AUDIO
        else -> MediaInterceptor.MediaType.MP4
    }

    val pageUrl = if (message is org.json.JSONObject) {
        if (message.has("pageUrl")) message.getString("pageUrl") else null
    } else {
        (message as? Map<*, *>)?.get("pageUrl") as? String
    } ?: rawUrl

    val cookies = if (message is org.json.JSONObject) {
        if (message.has("cookies")) message.getString("cookies") else null
    } else {
        (message as? Map<*, *>)?.get("cookies") as? String
    }

    val sizeBytes = if (message is org.json.JSONObject) {
        if (message.has("sizeBytes")) message.getLong("sizeBytes").takeIf { it > 0 } else null
    } else {
        ((message as? Map<*, *>)?.get("sizeBytes") as? Number)?.toLong()?.takeIf { it > 0 }
    }

    Log.i(TAG, "📥 SITE_DOWNLOAD_REQUEST: url=$rawUrl, type=$mediaType, title=$title, size=$sizeBytes")

    // Set the pending site download state so the UI can show a quality selector dialog
    this@handleSiteDownloadRequest.pendingSiteDownloadUrl = rawUrl
    this@handleSiteDownloadRequest.pendingSiteDownloadInfo = BrowserViewModel.SiteDownloadInfo(
        url = rawUrl,
        title = title,
        mimeType = mimeType,
        pageUrl = pageUrl,
        cookies = cookies,
        sizeBytes = sizeBytes
    )

    if (sizeBytes == null || sizeBytes <= 0) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = java.net.URL(rawUrl).openConnection() as? java.net.HttpURLConnection ?: return@launch
                conn.instanceFollowRedirects = true
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                cookies?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Cookie", it) }
                pageUrl.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Referer", it) }
                conn.setRequestProperty("User-Agent", BrowserViewModel.CHROME_UA)
                conn.connect()
                val len = conn.contentLengthLong.takeIf { it > 0 }
                conn.disconnect()
                var finalSize: Long? = len
                if (finalSize == null) {
                    val getConn = java.net.URL(rawUrl).openConnection() as java.net.HttpURLConnection
                    getConn.instanceFollowRedirects = true
                    getConn.requestMethod = "GET"
                    getConn.setRequestProperty("Range", "bytes=0-1")
                    getConn.connectTimeout = 4000
                    getConn.readTimeout = 4000
                    cookies?.takeIf { it.isNotBlank() }?.let { getConn.setRequestProperty("Cookie", it) }
                    pageUrl.takeIf { it.isNotBlank() }?.let { getConn.setRequestProperty("Referer", it) }
                    getConn.setRequestProperty("User-Agent", BrowserViewModel.CHROME_UA)
                    getConn.connect()
                    val contentRange = getConn.getHeaderField("Content-Range")
                    finalSize = contentRange?.substringAfterLast("/", "")?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    getConn.disconnect()
                }
                if (finalSize != null && finalSize > 0) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (this@handleSiteDownloadRequest.pendingSiteDownloadUrl == rawUrl) {
                            this@handleSiteDownloadRequest.pendingSiteDownloadInfo =
                                this@handleSiteDownloadRequest.pendingSiteDownloadInfo?.copy(sizeBytes = finalSize)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Also feed the media into the interceptor so the media sniffer banner appears
    // as a fallback — the user can always download from there.
    mediaInterceptor.onAggressiveMediaGrabbed(rawUrl, mimeType, cookies)

    // Show a toast to confirm the download request was received
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            android.widget.Toast.makeText(
                appContext,
                "Processing download request...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {}
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

/**
 * Sets up all required delegates for a WebExtension (native messaging and downloads).
 */
internal fun BrowserViewModel.setupWebExtensionDelegates(extension: WebExtension) {
    val extId = extension.safeId ?: return
    // Only Omni's built-in media grabber requires the "omniApp" native messaging port.
    // Registering third-party extensions with native messaging causes GeckoView's internal
    // WebExtension.Sender HashMap to throw NullPointerException when comparing sender IDs.
    if (extId == BrowserViewModel.GRABBER_ID) {
        setupNativeAppMessageDelegate(extension)
    }
    setupWebExtensionDownloadDelegate(extension)
}

/**
 * Registers GeckoView's DownloadDelegate on the WebExtension to bridge `browser.downloads.*`
 * into Omni's native StreamDownloadEngine.
 */
internal fun BrowserViewModel.setupWebExtensionDownloadDelegate(extension: WebExtension) {
    val extId = extension.safeId ?: return
    try {
        extension.setDownloadDelegate(object : WebExtension.DownloadDelegate {
            override fun onDownload(
                ext: WebExtension,
                request: WebExtension.DownloadRequest
            ): GeckoResult<WebExtension.DownloadInitData>? {
                return handleWebExtensionDownload(ext, request)
            }
        })
    } catch (e: Exception) {
        Log.e(TAG, "Failed to set download delegate for $extId", e)
    }
}

/**
 * Handles WebExtension download requests from standard `browser.downloads.download()`.
 */
internal fun BrowserViewModel.handleWebExtensionDownload(
    ext: WebExtension,
    request: WebExtension.DownloadRequest
): GeckoResult<WebExtension.DownloadInitData> {
    val result = GeckoResult<WebExtension.DownloadInitData>()

    // 1. Permission check
    val meta = ext.safeMetaData
    val extId = ext.safeId ?: "unknown"
    val hasDownloadPerm = ext.isBuiltIn ||
            meta?.requiredPermissions?.contains("downloads") == true ||
            meta?.optionalPermissions?.contains("downloads") == true ||
            meta?.grantedOptionalPermissions?.contains("downloads") == true ||
            extId == BrowserViewModel.GRABBER_ID

    if (!hasDownloadPerm) {
        Log.w(TAG, "🔒 [WebExtensionDownload] Extension [$extId] attempted download without 'downloads' permission")
        result.completeExceptionally(SecurityException("Extension lacks 'downloads' permission in manifest"))
        return result
    }

    // 2. Validate URL and scheme
    val rawUri = request.request.uri
    val parsedUri = try { android.net.Uri.parse(rawUri) } catch (e: Exception) { null }
    val scheme = parsedUri?.scheme?.lowercase()
    if (parsedUri == null || scheme !in listOf("http", "https", "blob", "data")) {
        Log.w(TAG, "🛡️ [WebExtensionDownload] Rejected dangerous/unsupported URI scheme: $rawUri")
        result.completeExceptionally(IllegalArgumentException("Unsupported download scheme: $scheme"))
        return result
    }

    // 3. Sanitize filename (prevent path traversal, dangerous chars)
    val rawFilename = request.filename?.takeIf { it.isNotBlank() }
        ?: parsedUri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "download_${System.currentTimeMillis()}"
    val safeFilename = SecurityPolicy.sanitizeFilename(rawFilename)
    val extName = safeFilename.substringAfterLast('.', "").lowercase()
    val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extName) ?: "application/octet-stream"

    // 4. Create live WebExtension.Download object via Gecko runtime
    val downloadId = nextWebExtensionDownloadId.incrementAndGet()
    val context = appContext
    val runtime = if (context != null) getGeckoRuntime(context) else null
    val geckoDownload = runtime?.webExtensionController?.createDownload(downloadId)
    if (geckoDownload == null) {
        Log.e(TAG, "❌ [WebExtensionDownload] Failed to create Gecko WebExtension.Download instance")
        result.completeExceptionally(IllegalStateException("Failed to create Gecko WebExtension.Download"))
        return result
    }

    val extDisplayName = meta?.name ?: ext.safeId ?: "Extension"

    val initialInfo = object : WebExtension.Download.Info {
        override fun filename() = safeFilename
        override fun state() = WebExtension.Download.STATE_IN_PROGRESS
        override fun bytesReceived() = 0L
        override fun totalBytes() = -1L
        override fun mime() = mimeType
        override fun paused() = false
        override fun canResume() = true
    }
    val initData = WebExtension.DownloadInitData(geckoDownload, initialInfo)

    // Helper to start the native download engine and attach delegates
    fun executeDownload() {
        val headers = request.request.headers
        val cookies = headers?.get("Cookie") ?: headers?.get("cookie")
        val referrer = request.request.referrer ?: headers?.get("Referer") ?: headers?.get("referer")

        Log.i(TAG, "📥 [WebExtensionDownload] Starting download: id=$downloadId, file=$safeFilename, ext=$extDisplayName")

        val jobId = streamDownloadEngine.startGenericFileDownload(
            url = rawUri,
            filename = safeFilename,
            contentType = mimeType,
            saveToLocker = false,
            cookies = cookies,
            referrerUrl = referrer,
            sourceOrigin = extDisplayName
        )

        // Attach WebExtension.Download.Delegate via reflection/proxy
        attachDownloadDelegate(geckoDownload, jobId, safeFilename, mimeType)

        // Observe progress from StreamDownloadEngine and propagate to geckoDownload.update(info)
        viewModelScope.launch(Dispatchers.Main) {
            val job = streamDownloadEngine.jobs.value.find { it.id == jobId }
            job?.progress?.collect { progress ->
                when (progress) {
                    is StreamDownloadEngine.DownloadProgress.Downloading -> {
                        geckoDownload.update(object : WebExtension.Download.Info {
                            override fun filename() = safeFilename
                            override fun state() = WebExtension.Download.STATE_IN_PROGRESS
                            override fun bytesReceived() = progress.bytesDownloaded
                            override fun totalBytes() = job.bytesDownloaded
                            override fun mime() = mimeType
                            override fun paused() = false
                            override fun canResume() = job.canResume
                        })
                    }
                    is StreamDownloadEngine.DownloadProgress.Complete -> {
                        geckoDownload.update(object : WebExtension.Download.Info {
                            override fun filename() = safeFilename
                            override fun state() = WebExtension.Download.STATE_COMPLETE
                            override fun bytesReceived() = progress.sizeBytes
                            override fun totalBytes() = progress.sizeBytes
                            override fun mime() = mimeType
                            override fun fileExists() = true
                        })
                    }
                    is StreamDownloadEngine.DownloadProgress.Error -> {
                        geckoDownload.update(object : WebExtension.Download.Info {
                            override fun filename() = safeFilename
                            override fun state() = WebExtension.Download.STATE_INTERRUPTED
                            override fun error(): Int? = null
                        })
                    }
                    else -> {}
                }
            }
        }

        result.complete(initData)
    }

    // 5. Policy & Confirmation Check
    when {
        extensionDownloadPolicy == BrowserViewModel.ExtensionDownloadPolicy.NEVER_ALLOW -> {
            Log.i(TAG, "🛡️ [WebExtensionDownload] Blocked by policy (NEVER_ALLOW)")
            result.completeExceptionally(SecurityException("Extension downloads are disabled in settings"))
        }
        extensionDownloadPolicy == BrowserViewModel.ExtensionDownloadPolicy.ALLOW_TRUSTED || ext.isBuiltIn -> {
            executeDownload()
        }
        else -> {
            // Prompt user for confirmation before writing to storage
            viewModelScope.launch(Dispatchers.Main) {
                pendingWebExtensionDownload = BrowserViewModel.PendingWebExtensionDownload(
                    downloadId = downloadId,
                    extensionId = ext.id,
                    extensionName = extDisplayName,
                    filename = safeFilename,
                    sourceUrl = rawUri,
                    mimeType = mimeType,
                    fileSize = -1L,
                    onConfirm = {
                        pendingWebExtensionDownload = null
                        executeDownload()
                    },
                    onCancel = {
                        pendingWebExtensionDownload = null
                        result.completeExceptionally(SecurityException("Download canceled by user"))
                    }
                )
            }
        }
    }

    return result
}

private fun BrowserViewModel.attachDownloadDelegate(
    geckoDownload: WebExtension.Download,
    jobId: String,
    safeFilename: String,
    mimeType: String
) {
    try {
        val delegateCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download\$Delegate")
        var proxyObj: Any? = null
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "onCancel" -> {
                    Log.d(TAG, "⏹️ [WebExtensionDownload] onCancel for jobId=$jobId")
                    streamDownloadEngine.cancelDownload(jobId)
                    GeckoResult.fromValue(null)
                }
                "onPause" -> {
                    Log.d(TAG, "⏸️ [WebExtensionDownload] onPause for jobId=$jobId")
                    streamDownloadEngine.pauseDownload(jobId)
                    GeckoResult.fromValue(null)
                }
                "onResume" -> {
                    Log.d(TAG, "▶️ [WebExtensionDownload] onResume for jobId=$jobId")
                    streamDownloadEngine.resumeDownload(jobId)
                    GeckoResult.fromValue(null)
                }
                "onOpen" -> {
                    val job = streamDownloadEngine.jobs.value.find { it.id == jobId }
                    val progress = job?.progress?.value
                    if (progress is StreamDownloadEngine.DownloadProgress.Complete) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(progress.openUri ?: android.net.Uri.fromFile(progress.file), mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            appContext?.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open downloaded file", e)
                        }
                    }
                    GeckoResult.fromValue(null)
                }
                "onErase", "onRemoveFile" -> {
                    streamDownloadEngine.deleteDownload(jobId, true)
                    GeckoResult.fromValue(null)
                }
                "hashCode" -> jobId.hashCode()
                "equals" -> args?.getOrNull(0) === proxyObj
                "toString" -> "WebExtensionDownloadDelegate(jobId=$jobId)"
                else -> null
            }
        }
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            delegateCls.classLoader,
            arrayOf(delegateCls),
            handler
        )
        proxyObj = proxy

        val setDelegateMethod = geckoDownload.javaClass.getDeclaredMethod("setDelegate", delegateCls)
        setDelegateMethod.isAccessible = true
        setDelegateMethod.invoke(geckoDownload, proxy)
    } catch (e: Exception) {
        Log.w(TAG, "Could not attach WebExtension.Download.Delegate via reflection", e)
    }
}

/**
 * Creates a WebExtension.SessionTabDelegate that handles extension requests to close or update tabs
 * (e.g. uBlock Origin closing ad popup windows via browser.tabs.remove).
 */
internal fun BrowserViewModel.createSessionTabDelegate(context: Context): WebExtension.SessionTabDelegate {
    return object : WebExtension.SessionTabDelegate {
        override fun onCloseTab(
            extension: WebExtension?,
            session: GeckoSession
        ): GeckoResult<AllowOrDeny> {
            val currentId = extension.safeId ?: "unknown"
            Log.d(TAG, "WebExtension $currentId requested onCloseTab")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val tabToClose = tabs.find { it.session == session }
                    if (tabToClose != null) {
                        Log.i(TAG, "Closing tab ${tabToClose.id} requested by extension $currentId")
                        closeTab(tabToClose.id, context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing tab on extension request", e)
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        override fun onUpdateTab(
            extension: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails
        ): GeckoResult<AllowOrDeny> {
            val currentId = extension.safeId ?: "unknown"
            Log.d(TAG, "WebExtension $currentId requested onUpdateTab: url=${details.url}, active=${details.active}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val targetTab = tabs.find { it.session == session }
                    if (targetTab != null) {
                        details.url?.let { url ->
                            if (url.isNotBlank()) {
                                loadUrlInTab(targetTab, url)
                            }
                        }
                        if (details.active == true) {
                            selectTab(targetTab.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating tab on extension request", e)
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
    }
}

/**
 * Creates a WebExtension.ActionDelegate for GeckoSession (SessionController)
 * to receive tab-specific browser action and page action events, badge updates, and popup triggers.
 */
internal fun BrowserViewModel.createSessionActionDelegate(session: GeckoSession? = null): WebExtension.ActionDelegate {
    return object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            eventSession: GeckoSession?,
            action: WebExtension.Action
        ) {
            try {
                val id = extension.safeId ?: return
                registerExtensionAction(id, eventSession ?: session, action)
            } catch (e: Exception) {
                Log.e(TAG, "Session ActionDelegate: onBrowserAction failed for ${extension.safeId}", e)
            }
        }

        override fun onPageAction(
            extension: WebExtension,
            eventSession: GeckoSession?,
            action: WebExtension.Action
        ) {
            try {
                val id = extension.safeId ?: return
                registerExtensionAction(id, eventSession ?: session, action)
            } catch (e: Exception) {
                Log.e(TAG, "Session ActionDelegate: onPageAction failed for ${extension.safeId}", e)
            }
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            return try {
                handleExtensionOpenPopup(extension, action)
            } catch (e: Exception) {
                Log.e(TAG, "Session ActionDelegate: onOpenPopup failed for ${extension.safeId}", e)
                null
            }
        }

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            return try {
                handleExtensionOpenPopup(extension, action)
            } catch (e: Exception) {
                Log.e(TAG, "Session ActionDelegate: onTogglePopup failed for ${extension.safeId}", e)
                null
            }
        }
    }
}

/**
 * Attaches both SessionTabDelegate and ActionDelegate for the specified extension across all live tabs.
 */
internal fun BrowserViewModel.attachSessionDelegates(extension: WebExtension, context: Context) {
    val tabDelegate = createSessionTabDelegate(context)
    tabs.forEach { tab ->
        if (!tab.session.isOpen || tab.isSuspended) return@forEach
        try {
            tab.session.webExtensionController.setTabDelegate(extension, tabDelegate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set SessionTabDelegate for ${extension.safeId} on tab ${tab.id}", e)
        }
        try {
            val actionDelegate = createSessionActionDelegate(tab.session)
            tab.session.webExtensionController.setActionDelegate(extension, actionDelegate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set ActionDelegate for ${extension.safeId} on tab ${tab.id}", e)
        }
    }
}

/**
 * Backward compatibility alias for attachSessionDelegates.
 */
internal fun BrowserViewModel.attachSessionTabDelegate(extension: WebExtension, context: Context) {
    attachSessionDelegates(extension, context)
}


