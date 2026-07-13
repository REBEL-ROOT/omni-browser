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

/**
 * Generic manager for Omni's built-in GeckoView WebExtensions.
 *
 * Each built-in extension lives under `assets/web_extensions/<assetPath>/` and is
 * loaded via `resource://android/assets/...` — GeckoView's mechanism for bundling
 * extensions inside the APK rather than fetching them from addons.mozilla.org.
 *
 * Why a single generic class instead of one class per extension:
 * All built-in extensions share the same lifecycle — ensureBuiltIn → setAllowedInPrivateBrowsing
 * → enable/disable. The only differences are the asset path and extension ID, both of
 * which are injected at construction time. Duplicating this logic per-extension was
 * causing silent divergence (e.g., one manager missing the private-browsing grant).
 *
 * Usage:
 * ```
 * val aiBlocker = BuiltInExtensionManager(
 *     runtime = geckoRuntime,
 *     assetPath = "web_extensions/ai_blocker/",
 *     extensionId = "omni-ai-blocker@omnibrowser.app",
 *     label = "AI Blocker"
 * )
 * aiBlocker.installAndSync(enabled = true)
 * ```
 *
 * Thread-safety: GeckoView's [WebExtensionController] callbacks fire on the main thread.
 * The [onComplete] lambda is always invoked on the calling thread of the GeckoResult callback.
 */
open class BuiltInExtensionManager(
    protected val runtime: GeckoRuntime,
    protected val assetPath: String,
    protected val extensionId: String,
    protected val label: String
) {
    private val tag = "BuiltIn[$label]"

    // Holds the installed WebExtension reference after ensureBuiltIn resolves.
    // Null until the first successful installAndSync call.
    private var extension: WebExtension? = null

    /**
     * Ensures the extension is installed (or already installed) and immediately
     * enables or disables it according to [enabled].
     *
     * GeckoView's [ensureBuiltIn] is idempotent — calling it when the extension is
     * already installed simply resolves with the existing instance, so it's safe to
     * call on every app startup without duplicating the extension.
     *
     * Private-browsing access is always granted here. Omni extensions are privacy
     * tools — they should work in incognito tabs too. If a future extension should
     * NOT run in private tabs, override this after construction.
     */
    fun installAndSync(enabled: Boolean, onComplete: (() -> Unit)? = null) {
        val resourceUri = "resource://android/assets/$assetPath"
        runtime.webExtensionController.ensureBuiltIn(resourceUri, extensionId).accept(
            { ext ->
                extension = ext
                if (ext != null) {
                    // Grant private-browsing access unconditionally for all built-in
                    // extensions. Without this, the extension silently does nothing in
                    // incognito tabs — GeckoView does not throw an error.
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                }
                Log.i(tag, "$label installed (id=$extensionId)")
                setEnabled(enabled, onComplete)
            },
            { error ->
                // Installation failure is non-fatal — the browser works without the
                // extension. Log the error and call onComplete so callers don't hang.
                Log.e(tag, "Failed to install $label — continuing without it", error)
                onComplete?.invoke()
            }
        )
    }

    /**
     * Enables or disables the extension at runtime without reinstalling it.
     * Safe to call at any time after [installAndSync] has resolved.
     *
     * Uses [WebExtensionController.EnableSource.APP] — this is the correct source
     * for programmatic control by the host app, as opposed to [EnableSource.USER]
     * which would be triggered by user action inside the extension's own UI.
     */
    fun setEnabled(enabled: Boolean, onComplete: (() -> Unit)? = null) {
        val ext = extension ?: run {
            // Extension not yet installed — this can happen if setEnabled is called
            // before installAndSync completes (race condition). Silently skip.
            onComplete?.invoke()
            return
        }

        val action = if (enabled) {
            runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
        } else {
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
        }

        val stateLabel = if (enabled) "enabled" else "disabled"
        action.accept(
            {
                Log.d(tag, "$label $stateLabel")
                onComplete?.invoke()
            },
            { error ->
                Log.e(tag, "Failed to ${stateLabel.dropLast(1)} $label", error)
                onComplete?.invoke()
            }
        )
    }

    /** Returns true if the extension is currently installed (regardless of enabled state). */
    fun isInstalled(): Boolean = extension != null
}
