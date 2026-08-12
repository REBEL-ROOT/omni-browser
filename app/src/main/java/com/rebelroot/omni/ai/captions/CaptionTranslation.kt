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

package com.rebelroot.omni.ai.captions

import com.rebelroot.omni.ai.translation.TranslationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Translated captions pipeline: existing/generated subtitles → local MT →
 * translated subtitles. Timing is preserved; only the text changes.
 *
 * Uses the same [TranslationCoordinator] as page translation, so:
 *  - OFFLINE_ONLY never produces a cloud translation request,
 *  - ASK prefers the offline engine when available.
 *
 * Deduplicates identical caption texts so repeated lines are translated once.
 */
object CaptionTranslation {

    suspend fun translate(
        source: SubtitleSource,
        coordinator: TranslationCoordinator,
        targetLanguage: String,
        sourceLanguage: String? = source.language
    ): SubtitleSource = withContext(Dispatchers.Default) {
        val uniqueTexts = source.segments.map { it.text }.distinct()
        val translatedByText = uniqueTexts.associateWith { text ->
            runCatching {
                coordinator.translate(text, sourceLanguage, targetLanguage).translatedText
            }.getOrDefault(text)
        }

        val translatedSegments = source.segments.map { seg ->
            seg.copy(text = translatedByText[seg.text] ?: seg.text)
        }

        val origin = when (source.origin) {
            SubtitleOrigin.GENERATED -> SubtitleOrigin.GENERATED_AND_TRANSLATED
            else -> SubtitleOrigin.TRANSLATED
        }
        SubtitleSource(targetLanguage, origin, translatedSegments)
    }
}