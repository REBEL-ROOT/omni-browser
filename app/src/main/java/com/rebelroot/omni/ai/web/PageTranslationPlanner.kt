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

import com.rebelroot.omni.ai.translation.TranslationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure (GeckoView-independent) logic for translating a batch of page segments.
 *
 * Responsibilities:
 *  - **Deduplication**: identical source strings are translated once and the
 *    result fanned back out, reducing model invocations and network calls.
 *  - **Graceful degradation**: a single segment that cannot be translated keeps
 *    its original text rather than crashing the whole page.
 *  - **Privacy**: it only talks to the [TranslationCoordinator], which enforces
 *    the active [com.rebelroot.omni.ai.translation.TranslationMode] (so
 *    OFFLINE_ONLY never reaches the cloud).
 *
 * This class is fully unit-testable without a browser.
 */
object PageTranslationPlanner {

    suspend fun translateSegments(
        coordinator: TranslationCoordinator,
        segments: List<PageSegment>,
        sourceLanguage: String?,
        targetLanguage: String
    ): PageTranslationOutput = withContext(Dispatchers.Default) {
        val unique = segments.distinctBy { it.text }
        val results = mutableMapOf<String, String>()
        var failures = 0

        for (seg in unique) {
            val translated = runCatching {
                coordinator.translate(seg.text, sourceLanguage, targetLanguage).translatedText
            }.onFailure { failures++ }.getOrDefault(seg.text)
            results[seg.text] = translated
        }

        val translated = segments.map { seg ->
            TranslatedSegment(seg.id, results[seg.text] ?: seg.text)
        }
        PageTranslationOutput(translated, failures)
    }
}

data class PageTranslationOutput(
    val segments: List<TranslatedSegment>,
    /** Number of segments whose translation failed (kept as original text). */
    val failureCount: Int
)
