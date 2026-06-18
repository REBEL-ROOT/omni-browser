package com.rebelroot.omni.browser.extensions

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class AiBlockerManager(private val runtime: GeckoRuntime) {

    companion object {
        private const val TAG = "AiBlockerManager"
        private const val EXTENSION_ID = "omni-ai-blocker@omnibrowser.app"
    }

    private var extension: WebExtension? = null

    fun installAndSync(enabled: Boolean, onComplete: (() -> Unit)? = null) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/ai_blocker/",
            EXTENSION_ID
        ).accept(
            { ext ->
                extension = ext
                Log.i(TAG, "AI Blocker Extension installed.")
                setEnabled(enabled, onComplete)
            },
            { error ->
                Log.e(TAG, "Failed to load AI Blocker Extension", error)
                onComplete?.invoke()
            }
        )
    }

    fun setEnabled(enabled: Boolean, onComplete: (() -> Unit)? = null) {
        val ext = extension
        if (ext == null) {
            onComplete?.invoke()
            return
        }
        val action = if (enabled) {
            Log.d(TAG, "Enabling AI Blocker...")
            runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
        } else {
            Log.d(TAG, "Disabling AI Blocker...")
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
        }
        action.accept(
            {
                onComplete?.invoke()
            },
            { error ->
                Log.e(TAG, "Failed to enable/disable AI Blocker", error)
                onComplete?.invoke()
            }
        )
    }
}
