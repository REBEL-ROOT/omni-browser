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
    val lastActiveTime: Long = System.currentTimeMillis()
)
