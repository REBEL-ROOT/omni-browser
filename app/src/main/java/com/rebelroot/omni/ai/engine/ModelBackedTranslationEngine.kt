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

import android.util.Log
import com.rebelroot.omni.ai.models.Json
import com.rebelroot.omni.ai.models.JsonValue
import com.rebelroot.omni.ai.models.ModelDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline translation engine backed by a downloaded, verified translation model.
 *
 * This is the "model-backed tier" the offline-translation architecture reserves
 * for installed models. Until a native NMT runtime (Bergamot/OPUS-MT) is bundled,
 * the engine consumes a **lexicon translation model** — a JSON object mapping a
 * lowercase source word to its target translation — loaded from the model file in
 * [com.rebelroot.omni.ai.models.ModelStorage]. This keeps translation fully
 * on-device (no cloud) and lets a model downloaded through the shared model
 * platform actually drive translation instead of falling back to Google.
 *
 * Supports auto-detected source (`null` / `"auto"`) so the default page
 * translation path prefers it over the cloud. It is preferred over the bundled
 * [LexiconTranslationEngine] because of its higher [quality].
 *
 * Privacy: operates entirely on the installed model file; never touches the
 * network, GeckoView, or Compose.
 */
class ModelBackedTranslationEngine(
    private val descriptor: ModelDescriptor,
    private val modelFile: File
) : OfflineTranslationEngine {

    override val id: String = "model:${descriptor.id}"
    override val quality: Int = 70

    @Volatile private var loaded = false
    @Volatile private var usable = false
    private var lexicon: Map<String, String> = emptyMap()

    override fun isLoaded(): Boolean = loaded

    override fun estimatedMemoryBytes(): Long = (lexicon.size * 64L).coerceAtLeast(0L)

    override suspend fun supports(sourceLanguage: String?, targetLanguage: String): Boolean {
        if (!descriptor.targetLanguage.equals(targetLanguage, true)) return false
        val src = sourceLanguage?.lowercase()
        if (src == null || src == "auto") return true
        return descriptor.sourceLanguage.equals(src, true)
    }

    override suspend fun load() {
        if (loaded) return
        runCatching {
            val text = modelFile.readText()
            val obj = Json.parse(text) as? JsonValue.Obj
            val map = LinkedHashMap<String, String>()
            obj?.fields?.forEach { (key, value) ->
                if (value is JsonValue.Str && value.value.isNotEmpty()) {
                    map[key.lowercase()] = value.value
                }
            }
            lexicon = map
            usable = map.isNotEmpty()
        }.onFailure { e ->
            // Log may be unavailable off-device (e.g. JVM unit tests); never let
            // logging break model loading — the model is simply marked unusable.
            runCatching { Log.e(TAG, "Failed to load translation model ${descriptor.id}", e) }
            usable = false
            lexicon = emptyMap()
        }
        loaded = true
    }

    override suspend fun unload() {
        lexicon = emptyMap()
        usable = false
        loaded = false
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): EngineTranslation = withContext(Dispatchers.Default) {
        if (!usable) {
            // Model could not be used: return the source unchanged rather than
            // fabricate a translation or silently route to the cloud.
            return@withContext EngineTranslation(text, detectedSourceLanguage = sourceLanguage ?: "auto")
        }
        val src = (sourceLanguage ?: "auto").lowercase()
        val result = StringBuilder()
        var position = 0
        val matcher = WORD_PATTERN.findAll(text)
        for (match in matcher) {
            // Append the gap (whitespace/punctuation) verbatim.
            if (match.range.first > position) {
                result.append(text.substring(position, match.range.first))
            }
            val token = match.value
            val lower = token.lowercase()
            val replacement = lexicon[lower]
            result.append(
                if (replacement != null) {
                    if (token.first().isUpperCase()) capitalize(replacement) else replacement
                } else {
                    token
                }
            )
            position = match.range.last + 1
        }
        if (position < text.length) result.append(text.substring(position))

        EngineTranslation(result.toString(), detectedSourceLanguage = src)
    }

    private fun capitalize(s: String): String =
        if (s.isEmpty()) s else s.first().uppercase() + s.drop(1)

    companion object {
        private const val TAG = "ModelBackedTranslationEngine"
        private val WORD_PATTERN = Regex("\\p{L}[\\p{L}\\p{N}'-]*")
    }
}
