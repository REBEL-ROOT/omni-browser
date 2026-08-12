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

/**
 * A self-contained on-device translation engine.
 *
 * An engine loads a model from application-private storage, translates text, and
 * can release the model when idle. Engines must not touch the network, GeckoView,
 * Compose, or navigation. They are selected by [TranslationEngineManager] based
 * on the requested language pair and the models currently installed.
 *
 * Implementations:
 *  - [LexiconTranslationEngine] : tiny bundled dictionary, fully offline, no
 *    model download. Low quality, used as a guaranteed baseline.
 *  - Model-backed engines (added later) : load a downloaded, checksum-verified
 *    NMT model from [com.rebelroot.omni.ai.models] storage.
 */
interface OfflineTranslationEngine {

    /** Stable engine id, e.g. "lexicon" or "bergamot:<modelId>". */
    val id: String

    /** Relative quality hint (0..100) used to rank engines for the same pair. */
    val quality: Int

    /** True once the underlying model/runtime is resident in memory. */
    fun isLoaded(): Boolean

    /** Whether this engine can serve the given pair with current resources. */
    suspend fun supports(sourceLanguage: String?, targetLanguage: String): Boolean

    /** Load the model into memory. Must be cheap/no-op if already loaded. */
    suspend fun load()

    /** Translate text fully on-device. Never performs network I/O. */
    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): EngineTranslation

    /** Release the model from memory. Safe to call when not loaded. */
    suspend fun unload()

    /** Approximate resident memory footprint in bytes when loaded (for budgeting). */
    fun estimatedMemoryBytes(): Long = 0L
}

data class EngineTranslation(
    val text: String,
    val detectedSourceLanguage: String
)
