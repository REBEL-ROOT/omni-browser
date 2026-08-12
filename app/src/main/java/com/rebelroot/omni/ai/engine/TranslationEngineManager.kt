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

package com.rebelroot.omni.ai.engine

import com.rebelroot.omni.ai.translation.TranslationResult
import com.rebelroot.omni.ai.translation.UnsupportedLanguagePairException

/**
 * Selects and drives the best available on-device translation engine for a pair.
 *
 * Engines are registered in priority order (highest [OfflineTranslationEngine.quality]
 * first). When a model-backed engine is installed it is preferred over the bundled
 * lexicon fallback. The manager owns lazy loading: an engine is loaded on first use
 * and the caller is responsible for releasing it via [releaseAll] when the feature
 * goes idle (see performance rules: unload models when not required).
 */
class TranslationEngineManager(
    private val engines: List<OfflineTranslationEngine>
) {

    private val loadedEngines = LinkedHashSet<String>()

    /** All registered engines (for diagnostics / UI). */
    fun engines(): List<OfflineTranslationEngine> = engines.toList()

    /** Whether any registered engine can serve the pair. */
    suspend fun hasEngine(sourceLanguage: String?, targetLanguage: String): Boolean =
        engines.any { it.supports(sourceLanguage, targetLanguage) }

    /** List engines that can serve the pair, best quality first. */
    suspend fun enginesForPair(
        sourceLanguage: String?,
        targetLanguage: String
    ): List<OfflineTranslationEngine> =
        engines.filter { it.supports(sourceLanguage, targetLanguage) }
            .sortedByDescending { it.quality }

    /**
     * Translate fully on-device using the best available engine.
     * @throws UnsupportedLanguagePairException if no engine supports the pair.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): TranslationResult {
        val candidate = enginesForPair(sourceLanguage, targetLanguage).firstOrNull()
            ?: throw UnsupportedLanguagePairException(
                "No offline engine available for ${sourceLanguage ?: "auto"} -> $targetLanguage",
                providerId = "offline",
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )

        if (!candidate.isLoaded()) {
            candidate.load()
            loadedEngines.add(candidate.id)
        }

        val engineResult: EngineTranslation = candidate.translate(text, sourceLanguage, targetLanguage)
        return TranslationResult(
            translatedText = engineResult.text,
            sourceLanguage = engineResult.detectedSourceLanguage,
            targetLanguage = targetLanguage,
            providerId = "offline",
            isOffline = true
        )
    }

    /** Unload every engine currently resident in memory. */
    suspend fun releaseAll() {
        for (engine in engines) {
            if (engine.isLoaded()) engine.unload()
        }
        loadedEngines.clear()
    }

    /** Approximate total resident memory of loaded engines (bytes). */
    fun loadedMemoryBytes(): Long = engines.sumOf { if (it.isLoaded()) it.estimatedMemoryBytes() else 0L }

    companion object {
        /** Build a manager with the guaranteed bundled lexicon engine. */
        fun withDefaults(lexicon: LexiconTranslationEngine): TranslationEngineManager =
            TranslationEngineManager(listOf(lexicon))
    }
}
