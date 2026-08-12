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

/** A single translatable text node extracted from a page, tagged with a stable id. */
data class PageSegment(val id: String, val text: String)

/** The translated form of a [PageSegment]. */
data class TranslatedSegment(val id: String, val text: String)

/** Lifecycle state of an in-progress page translation, bound to one session/tab. */
sealed class WebTranslationState {
    object Idle : WebTranslationState()
    object Extracting : WebTranslationState()
    object Translating : WebTranslationState()
    object Applying : WebTranslationState()
    object Active : WebTranslationState()
    data class Error(val message: String?) : WebTranslationState()
}

/**
 * A single page-translation request, scoped to a session/tab so a late result
 * from a previous page can never mutate the new one.
 */
data class TranslationRequest(
    val sessionId: String,
    val sourceLanguage: String?,
    val targetLanguage: String
)
