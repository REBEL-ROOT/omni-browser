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

package com.rebelroot.omni.browser

import android.util.Log
import com.rebelroot.omni.ai.web.WebTranslationController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtensionController

private const val OMNI_TRANSLATE_EXTENSION_ID = "omni-translate@omnibrowser.app"

/**
 * Installs the always-on `omni_translate` content-script bridge. The content
 * script sends page text to [BrowserViewModel.omniTranslateBridge] which applies
 * the active translation policy (OFFLINE_ONLY / ONLINE_ONLY / ASK) via the app's
 * coordinator. The page never receives native model/runtime access.
 */
internal fun BrowserViewModel.installOmniTranslateExtension(runtime: GeckoRuntime) {
    runtime.webExtensionController.ensureBuiltIn(
        "resource://android/assets/web_extensions/omni_translate/",
        OMNI_TRANSLATE_EXTENSION_ID
    ).accept(
        { ext ->
            ext?.let {
                runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                runtime.webExtensionController.enable(it, WebExtensionController.EnableSource.APP)
                omniTranslateBridge.register(it)
            }
        },
        { error ->
            Log.e(BrowserViewModel.Companion.TAG, "Failed to load Omni Translate extension", error)
        }
    )
}

/**
 * Begin page translation for [tabId]'s [session]. Each call rebinds the controller
 * to the current session object so a stale closed session can never be written to.
 */
internal fun BrowserViewModel.translatePage(
    tabId: String,
    session: GeckoSession,
    sourceLanguage: String?,
    targetLanguage: String,
    isPrivate: Boolean
) {
    stopPageTranslation(tabId)
    val controller = WebTranslationController(session, tabId, isPrivate, omniTranslateBridge)
    pageTranslationControllers[tabId] = controller
    controller.translatePage(sourceLanguage, targetLanguage)
}

/** Stop and restore page translation for [tabId] (navigation / tab close / off). */
internal fun BrowserViewModel.stopPageTranslation(tabId: String) {
    pageTranslationControllers.remove(tabId)?.stop()
}

/** Stop all active page translations (used by burn-data / close-all paths). */
internal fun BrowserViewModel.stopAllPageTranslations() {
    val ids = pageTranslationControllers.keys.toList()
    ids.forEach { stopPageTranslation(it) }
}
