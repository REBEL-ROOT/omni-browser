/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtensionController

private const val OMNI_SYNC_EXTENSION_ID = "omni-sync@rebelroot.com"

internal fun BrowserViewModel.installOmniSyncExtension(runtime: GeckoRuntime) {
    runtime.webExtensionController.ensureBuiltIn(
        "resource://android/assets/web_extensions/omni_sync/",
        OMNI_SYNC_EXTENSION_ID
    ).accept(
        { ext ->
            ext?.let {
                runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                runtime.webExtensionController.enable(it, WebExtensionController.EnableSource.APP)
                Log.i(BrowserViewModel.Companion.TAG, "Omni Sync pre-installed built-in WebExtension active.")
            }
        },
        { error ->
            Log.e(BrowserViewModel.Companion.TAG, "Failed to load Omni Sync WebExtension", error)
        }
    )
}
