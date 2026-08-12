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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tiny, fully-offline dictionary translator used as a guaranteed baseline when no
 * downloaded NMT model is installed.
 *
 * IMPORTANT — quality: this is a word-by-word lexicon with no grammar, no
 * reordering, and a very small vocabulary. It exists to prove the offline
 * pipeline end-to-end without bundling large model weights, and to give users a
 * working (if rough) translation. It is NOT a substitute for a real NMT engine.
 * The model platform can download a proper NMT model (see docs) which the
 * engine manager will prefer automatically.
 *
 * Privacy: operates entirely in memory, no network, no disk.
 *
 * @param lexicon keyed by "src|tgt" (e.g. "en|es"); each entry maps a lowercase
 *   source word to its translation. Direction is taken from the requested pair.
 */
class LexiconTranslationEngine(
    private val lexicon: Map<String, Map<String, String>> = BUILTIN_LEXICON
) : OfflineTranslationEngine {

    override val id: String = "lexicon"
    override val quality: Int = 10
    override fun isLoaded(): Boolean = true
    override fun estimatedMemoryBytes(): Long = lexicon.size.toLong() * 512L

    override suspend fun supports(sourceLanguage: String?, targetLanguage: String): Boolean {
        val src = sourceLanguage?.lowercase()
        if (src == null || src == "auto") return false // lexicon cannot detect source
        return lexicon.containsKey("$src|$targetLanguage")
    }

    override suspend fun load() {
        // Nothing to load: lexicon already resides in memory.
    }

    override suspend fun unload() {
        // Nothing to release.
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): EngineTranslation = withContext(Dispatchers.Default) {
        val src = (sourceLanguage ?: "auto").lowercase()
        val map = lexicon["$src|$targetLanguage"]
        if (map == null) {
            // No lexicon direction: return source unchanged rather than fabricate.
            return@withContext EngineTranslation(text, detectedSourceLanguage = src)
        }

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
            val replacement = map[lower]
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
        private val WORD_PATTERN = Regex("\\p{L}[\\p{L}\\p{N}'-]*")

        /**
         * A deliberately small demonstration lexicon. Real coverage is provided by
         * downloaded NMT models; this keeps the app functional offline out-of-the-box
         * for a few common pairs without bundling large weights.
         */
        val BUILTIN_LEXICON: Map<String, Map<String, String>> = mapOf(
            "en|es" to mapOf(
                "hello" to "hola", "world" to "mundo", "the" to "el", "a" to "un",
                "cat" to "gato", "dog" to "perro", "book" to "libro", "water" to "agua",
                "food" to "comida", "house" to "casa", "car" to "coche", "friend" to "amigo",
                "love" to "amor", "time" to "tiempo", "day" to "día", "night" to "noche",
                "good" to "bueno", "bad" to "malo", "big" to "grande", "small" to "pequeño",
                "red" to "rojo", "blue" to "azul", "green" to "verde", "happy" to "feliz",
                "sad" to "triste", "yes" to "sí", "no" to "no", "please" to "por favor",
                "thanks" to "gracias", "welcome" to "bienvenido", "open" to "abrir",
                "close" to "cerrar", "read" to "leer", "write" to "escribir", "eat" to "comer",
                "drink" to "beber", "go" to "ir", "come" to "venir", "see" to "ver",
                "hear" to "oír", "speak" to "hablar", "know" to "saber", "think" to "pensar",
                "language" to "idioma", "browser" to "navegador", "page" to "página",
                "video" to "vídeo", "music" to "música", "phone" to "teléfono", "computer" to "ordenador"
            ),
            "en|fr" to mapOf(
                "hello" to "bonjour", "world" to "monde", "the" to "le", "a" to "un",
                "cat" to "chat", "dog" to "chien", "book" to "livre", "water" to "eau",
                "food" to "nourriture", "house" to "maison", "car" to "voiture", "friend" to "ami",
                "love" to "amour", "time" to "temps", "day" to "jour", "night" to "nuit",
                "good" to "bon", "bad" to "mauvais", "big" to "grand", "small" to "petit",
                "red" to "rouge", "blue" to "bleu", "green" to "vert", "happy" to "heureux",
                "sad" to "triste", "yes" to "oui", "no" to "non", "please" to "s'il vous plaît",
                "thanks" to "merci", "welcome" to "bienvenue", "open" to "ouvrir",
                "close" to "fermer", "read" to "lire", "write" to "écrire", "eat" to "manger",
                "drink" to "boire", "go" to "aller", "come" to "venir", "see" to "voir",
                "hear" to "entendre", "speak" to "parler", "know" to "savoir", "think" to "penser",
                "language" to "langue", "browser" to "navigateur", "page" to "page",
                "video" to "vidéo", "music" to "musique", "phone" to "téléphone", "computer" to "ordinateur"
            ),
            "en|de" to mapOf(
                "hello" to "hallo", "world" to "welt", "the" to "der", "a" to "ein",
                "cat" to "katze", "dog" to "hund", "book" to "buch", "water" to "wasser",
                "food" to "essen", "house" to "haus", "car" to "auto", "friend" to "freund",
                "love" to "liebe", "time" to "zeit", "day" to "tag", "night" to "nacht",
                "good" to "gut", "bad" to "schlecht", "big" to "groß", "small" to "klein",
                "red" to "rot", "blue" to "blau", "green" to "grün", "happy" to "glücklich",
                "sad" to "traurig", "yes" to "ja", "no" to "nein", "please" to "bitte",
                "thanks" to "danke", "welcome" to "willkommen", "open" to "öffnen",
                "close" to "schließen", "read" to "lesen", "write" to "schreiben", "eat" to "essen",
                "drink" to "trinken", "go" to "gehen", "come" to "kommen", "see" to "sehen",
                "hear" to "hören", "speak" to "sprechen", "know" to "wissen", "think" to "denken",
                "language" to "sprache", "browser" to "browser", "page" to "seite",
                "video" to "video", "music" to "musik", "phone" to "telefon", "computer" to "computer"
            )
        )
    }
}
