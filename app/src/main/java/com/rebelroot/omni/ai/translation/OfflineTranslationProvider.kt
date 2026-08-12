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

import com.rebelroot.omni.ai.engine.TranslationEngineManager

/**
 * Offline translation backend. Drives on-device [TranslationEngineManager].
 *
 * This provider MUST never contact a network. When no engine can serve the
 * requested pair it reports unavailable and [translate] throws
 * [UnsupportedLanguagePairException]; it never silently falls back to a cloud
 * service. This is what enforces the OFFLINE_ONLY privacy guarantee.
 */
class OfflineTranslationProvider(
    private val engineManager: TranslationEngineManager
) : TranslationProvider {

    override val id: String = "offline"
    override val isOffline: Boolean = true

    override suspend fun isAvailable(
        sourceLanguage: String?,
        targetLanguage: String
    ): Boolean = engineManager.hasEngine(sourceLanguage, targetLanguage)

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): TranslationResult = engineManager.translate(text, sourceLanguage, targetLanguage)
}
