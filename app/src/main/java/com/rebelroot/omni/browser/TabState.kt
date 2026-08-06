/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

import org.mozilla.geckoview.GeckoSession

data class TabState(
    val id: String,
    val session: GeckoSession,
    val title: String,
    val url: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadError: String? = null,
    val isEditModeEnabled: Boolean = false,
    val settingsVersion: Int = 0,
    val isUriLoaded: Boolean = true,
    val isIncognito: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis(),
    /** True when the GeckoSession has been closed to reclaim memory. The tab
     *  metadata (url, title, history flags) is preserved; the session is
     *  re-created and the page reloaded when the tab is focused again. */
    val isSuspended: Boolean = false,
    /** Optional low-resolution thumbnail captured just before suspension,
     *  used to show a preview in the tab strip while the tab is suspended. */
    val suspendThumbnail: android.graphics.Bitmap? = null,
    /** Serialized GeckoSession state preserved during suspension to restore
     *  exact page state, form inputs, scroll position, and history stack. */
    val savedSessionState: GeckoSession.SessionState? = null
)
