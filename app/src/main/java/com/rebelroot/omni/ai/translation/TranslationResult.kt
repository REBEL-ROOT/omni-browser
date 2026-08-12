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

package com.rebelroot.omni.ai.translation

/**
 * Result of a single translation request.
 *
 * @param translatedText The translated text. For an offline provider this never
 *   leaves the device; for the online provider it originates from a remote
 *   service chosen by the active [TranslationMode].
 * @param sourceLanguage The language the input was detected/used as (e.g. "en").
 * @param targetLanguage The requested target language (e.g. "es").
 * @param providerId Identifier of the provider that produced the result
 *   ("online" or "offline"). Used for UI labelling and audits.
 * @param isOffline True when the translation was performed fully on-device and
 *   no network request was made.
 */
data class TranslationResult(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val providerId: String,
    val isOffline: Boolean
)
