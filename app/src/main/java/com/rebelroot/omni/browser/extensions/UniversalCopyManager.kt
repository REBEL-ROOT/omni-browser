/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.browser.extensions

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

    fun installAndSync(enabled: Boolean, onComplete: (() -> Unit)? = null) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/universal_copy/",
            EXTENSION_ID
        ).accept(
            { ext ->
                extension = ext
                Log.i(TAG, "Universal Copy Extension installed.")
                setEnabled(enabled, onComplete)
            },
            { error ->
                Log.e(TAG, "Failed to load Universal Copy Extension", error)
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
            Log.d(TAG, "Enabling Universal Copy...")
            runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
        } else {
            Log.d(TAG, "Disabling Universal Copy...")
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
        }
        action.accept(
            {
                onComplete?.invoke()
            },
            { error ->
                Log.e(TAG, "Failed to enable/disable Universal Copy", error)
                onComplete?.invoke()
            }
        )
    }

    fun uninstall(onComplete: (() -> Unit)? = null) {
        val ext = extension
        if (ext != null) {
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
            runtime.webExtensionController.uninstall(ext).accept(
                {
                    extension = null
                    onComplete?.invoke()
                },
                {
                    extension = null
                    onComplete?.invoke()
                }
            )
        } else {
            onComplete?.invoke()
        }
    }
}
