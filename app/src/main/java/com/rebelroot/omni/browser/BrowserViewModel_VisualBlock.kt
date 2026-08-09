/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser

import android.widget.Toast

fun BrowserViewModel.toggleVisualBlockMode() {
    val session = geckoSession ?: return
    isVisualBlockModeActive = !isVisualBlockModeActive
    if (isVisualBlockModeActive) {
        // Calculate bottom offset so the JS toolbar always floats above the browser chrome.
        // Split mode: address bar on top + bottom nav bar visible → need extra clearance (~200px).
        // Bottom mode: address bar + nav bar both at bottom → 96px clears it comfortably.
        // Top mode: address bar on top, no bottom nav → keep a modest 32px buffer above system nav.
        val bottomOffsetPx = when {
            addressBarPosition == "Split" -> 200   // bottom nav bar + system insets
            addressBarPosition == "Bottom" -> 96   // standard bottom chrome clearance
            else -> 32                             // Top: only system nav gesture zone
        }
        val script = visualBlockManager.getInspectorJsScript(bottomOffsetPx)
        session.loadUri(script)
        appContext?.let {
            Toast.makeText(it, "Block Area: Tap any element to select & hide", Toast.LENGTH_SHORT).show()
        }
    } else {
        session.loadUri("javascript:(function(){ if (window.__omniVisualBlockCleanup) window.__omniVisualBlockCleanup(); })();")
    }
}

fun BrowserViewModel.applyVisualBlockRulesToTab(tab: TabState? = activeTab) {
    val targetTab = tab ?: activeTab ?: return
    val liveTab = tabs.find { it.id == targetTab.id }
    val url = when {
        targetTab.id == activeTabId && currentUrl.isNotBlank() && currentUrl != "about:blank" -> currentUrl
        liveTab != null && liveTab.url.isNotBlank() && liveTab.url != "about:blank" -> liveTab.url
        targetTab.url.isNotBlank() && targetTab.url != "about:blank" -> targetTab.url
        else -> currentUrl
    }
    
    val css = visualBlockManager.buildCosmeticCssForDomain(url)
    if (css.isBlank()) return

    val cssEscaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "")
    val js = """
        javascript:(function() {
            function applyStyle() {
                try {
                    const styleId = 'omni-custom-visual-block-style';
                    let styleEl = document.getElementById(styleId);
                    if ('$cssEscaped' === '') {
                        if (styleEl) styleEl.remove();
                    } else {
                        if (!styleEl) {
                            styleEl = document.createElement('style');
                            styleEl.id = styleId;
                            styleEl.type = 'text/css';
                            (document.head || document.documentElement || document.body).appendChild(styleEl);
                        }
                        styleEl.textContent = '$cssEscaped';
                    }
                } catch(e) {}
            }
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', applyStyle);
            }
            applyStyle();
            setTimeout(applyStyle, 100);
            setTimeout(applyStyle, 400);
            setTimeout(applyStyle, 1000);
            setTimeout(applyStyle, 2500);
        })();
    """.trimIndent()
    targetTab.session.loadUri(js)
}

fun BrowserViewModel.applyVisualBlockRulesToActiveTab() {
    applyVisualBlockRulesToTab(activeTab)
}

fun BrowserViewModel.applyUserAgentForTab(tab: TabState? = activeTab, url: String? = null) {
    val targetTab = tab ?: activeTab ?: return
    val liveTab = tabs.find { it.id == targetTab.id }
    val targetUrl = url ?: when {
        targetTab.id == activeTabId && currentUrl.isNotBlank() && currentUrl != "about:blank" -> currentUrl
        liveTab != null && liveTab.url.isNotBlank() && liveTab.url != "about:blank" -> liveTab.url
        targetTab.url.isNotBlank() && targetTab.url != "about:blank" -> targetTab.url
        else -> currentUrl
    }
    val resolvedUa = runCatching { userAgentManager.resolveUserAgent(targetUrl) }.getOrNull()
    targetTab.session.settings.userAgentOverride = resolvedUa

    if (!resolvedUa.isNullOrBlank()) {
        val isDesktopUa = resolvedUa.contains("Windows NT") || 
                          resolvedUa.contains("Macintosh") || 
                          resolvedUa.contains("X11; Linux x86_64")
        if (isDesktopUa) {
            targetTab.session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            targetTab.session.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            targetTab.session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            targetTab.session.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
    } else if (isDesktopMode) {
        targetTab.session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        targetTab.session.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
    } else {
        targetTab.session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        targetTab.session.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }
}
