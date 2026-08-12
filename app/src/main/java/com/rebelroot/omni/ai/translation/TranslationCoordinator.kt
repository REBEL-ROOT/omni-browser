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
 * Applies the user's [TranslationMode] to choose between the online and offline
 * providers. This is the single place that enforces the privacy policy:
 *
 *  - OFFLINE_ONLY : only [offlineProvider] may run. If it cannot serve the pair,
 *    translation fails — it never falls back to the cloud.
 *  - ONLINE_ONLY  : only [onlineProvider] may run.
 *  - ASK          : prefers offline when a model exists, otherwise online (used
 *    when no explicit user choice is available).
 *
 * The coordinator itself performs no I/O and knows nothing about UI/Gecko.
 */
class TranslationCoordinator(
    private val onlineProvider: TranslationProvider,
    private val offlineProvider: TranslationProvider,
    private val modeProvider: () -> TranslationMode
) {

    fun currentMode(): TranslationMode = modeProvider()

    /** Whether an offline engine can serve the pair right now. */
    suspend fun canTranslateOffline(
        sourceLanguage: String?,
        targetLanguage: String
    ): Boolean = offlineProvider.isAvailable(sourceLanguage, targetLanguage)

    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): TranslationResult {
        return when (modeProvider()) {
            TranslationMode.OFFLINE_ONLY -> {
                if (!offlineProvider.isAvailable(sourceLanguage, targetLanguage)) {
                    throw UnsupportedLanguagePairException(
                        "Offline mode is enabled but no offline model supports " +
                            "${sourceLanguage ?: "auto"} -> $targetLanguage",
                        providerId = offlineProvider.id,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage
                    )
                }
                offlineProvider.translate(text, sourceLanguage, targetLanguage)
            }

            TranslationMode.ONLINE_ONLY -> {
                onlineProvider.translate(text, sourceLanguage, targetLanguage)
            }

            TranslationMode.ASK -> {
                if (offlineProvider.isAvailable(sourceLanguage, targetLanguage)) {
                    offlineProvider.translate(text, sourceLanguage, targetLanguage)
                } else {
                    onlineProvider.translate(text, sourceLanguage, targetLanguage)
                }
            }
        }
    }

    companion object {
        fun default(
            onlineProvider: TranslationProvider = OnlineTranslationProvider(),
            offlineProvider: TranslationProvider,
            modeProvider: () -> TranslationMode
        ): TranslationCoordinator = TranslationCoordinator(onlineProvider, offlineProvider, modeProvider)
    }
}
