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

package com.rebelroot.omni.ai.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoSession

/**
 * Per-session controller for offline (or hybrid) webpage translation.
 *
 * Responsibilities:
 *  - Hold the lifecycle/state for ONE tab/session.
 *  - Trigger translation by dispatching the `omni-translate-start` DOM event that
 *    the `omni-translate` content script listens for (GeckoView 145 has no
 *    `evaluateJS`; JS is executed via `loadUri("javascript:...")`).
 *  - Stop translation and restore the original page via `omni-translate-stop`.
 *  - Scope every request by [sessionId] (+ [isPrivate]) so a result from a
 *    previous page can never mutate the new one.
 *
 * The actual extraction, translation (via [OmniTranslateBridge] →
 * [TranslationCoordinator]) and DOM write happen in the content script + bridge;
 * this class deliberately knows nothing about the translation providers.
 */
class WebTranslationController(
    private val session: GeckoSession,
    val sessionId: String,
    val isPrivate: Boolean,
    private val bridge: OmniTranslateBridge
) {
    private val _state = MutableStateFlow<WebTranslationState>(WebTranslationState.Idle)
    val state: StateFlow<WebTranslationState> = _state

    @Volatile private var activeRequest: TranslationRequest? = null

    /** Begin translating the current page. */
    fun translatePage(sourceLanguage: String?, targetLanguage: String) {
        val req = TranslationRequest(sessionId, sourceLanguage, targetLanguage)
        activeRequest = req
        bridge.request = req
        _state.value = WebTranslationState.Extracting
        runCatching {
            session.loadUri("javascript:document.dispatchEvent(new Event('omni-translate-start'))")
        }.onFailure {
            _state.value = WebTranslationState.Error(it.message)
        }.onSuccess {
            _state.value = WebTranslationState.Active
        }
    }

    /** Stop translating and restore the original page text. */
    fun stop() {
        runCatching {
            session.loadUri("javascript:document.dispatchEvent(new Event('omni-translate-stop'))")
        }
        if (activeRequest?.sessionId == bridge.request?.sessionId) {
            bridge.request = null
        }
        activeRequest = null
        _state.value = WebTranslationState.Idle
    }

    /** Release resources (call when the tab/session is destroyed). */
    fun destroy() {
        stop()
    }
}
