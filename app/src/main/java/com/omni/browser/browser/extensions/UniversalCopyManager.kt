package com.omni.browser.browser.extensions

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class UniversalCopyManager(private val runtime: GeckoRuntime) {

    companion object {
        private const val TAG = "UniversalCopyManager"
        private const val EXTENSION_ID = "omni-universal-copy@omnibrowser.app"
    }

    private var extension: WebExtension? = null

    fun installAndSync(enabled: Boolean) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/universal_copy/",
            EXTENSION_ID
        ).accept(
            { ext ->
                extension = ext
                Log.i(TAG, "Universal Copy Extension installed.")
                setEnabled(enabled)
            },
            { error ->
                Log.e(TAG, "Failed to load Universal Copy Extension", error)
            }
        )
    }

    fun setEnabled(enabled: Boolean) {
        val ext = extension ?: return
        if (enabled) {
            Log.d(TAG, "Enabling Universal Copy...")
            runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            Log.d(TAG, "Disabling Universal Copy...")
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
        }
    }
}
