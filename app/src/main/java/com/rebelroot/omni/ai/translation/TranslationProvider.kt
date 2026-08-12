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
 * Abstraction over a translation backend.
 *
 * Implementations must be self-contained: they must not know about GeckoView,
 * Compose, navigation, or any UI layer. A provider either performs translation
 * locally (offline) or delegates to a remote service (online).
 *
 * The offline provider MUST never contact a cloud service, even when its model
 * is missing. When it cannot serve a request it reports unavailability through
 * [isAvailable] and throws [UnsupportedLanguagePairException] from [translate].
 */
interface TranslationProvider {

    /** Stable identifier, e.g. "online" or "offline". */
    val id: String

    /** True for on-device providers that perform no network I/O. */
    val isOffline: Boolean

    /**
     * Whether this provider can translate the given pair right now.
     * For an offline provider this typically depends on an installed model.
     */
    suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String): Boolean

    /**
     * Translate [text] from [sourceLanguage] to [targetLanguage].
     *
     * @param sourceLanguage The source language code, or null/"auto" to detect.
     * @throws UnsupportedLanguagePairException if the pair cannot be served.
     * @throws TranslationException on transient/runtime failures.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): TranslationResult
}

/** Thrown when a provider cannot serve the requested language pair. */
class UnsupportedLanguagePairException(
    message: String,
    val providerId: String,
    val sourceLanguage: String?,
    val targetLanguage: String
) : Exception(message)

/** Thrown for transient or runtime translation failures. */
class TranslationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
