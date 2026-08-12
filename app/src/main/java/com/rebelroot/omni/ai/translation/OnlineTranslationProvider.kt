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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online translation backend backed by Google's public "gtx" endpoint.
 *
 * This preserves the exact behaviour of the previous [com.rebelroot.omni.tools.TranslationManager]
 * so existing online translation keeps working. It performs network I/O and must
 * therefore never be selected while [TranslationMode.OFFLINE_ONLY] is active.
 *
 * Privacy: this provider sends the page/selection text to a third-party service.
 * It is only engaged when the user's mode permits online translation.
 */
class OnlineTranslationProvider(
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 8000
) : TranslationProvider {

    override val id: String = "online"
    override val isOffline: Boolean = false

    override suspend fun isAvailable(
        sourceLanguage: String?,
        targetLanguage: String
    ): Boolean {
        // The public endpoint supports arbitrary language pairs; it is always
        // reachable when network is present. We report availability conservatively
        // (true) and let [translate] surface real failures.
        return targetLanguage.isNotBlank()
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext TranslationResult(
                translatedText = "",
                sourceLanguage = sourceLanguage ?: "auto",
                targetLanguage = targetLanguage,
                providerId = id,
                isOffline = false
            )
        }

        val sl = (sourceLanguage ?: "auto").lowercase()
        val tl = targetLanguage.lowercase()

        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedText"

            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val translated = parseTranslationResponse(responseText)
                TranslationResult(
                    translatedText = translated,
                    sourceLanguage = sl,
                    targetLanguage = tl,
                    providerId = id,
                    isOffline = false
                )
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "HTTP Error $responseCode: $errorText")
                throw TranslationException("Online translation API error ($responseCode)")
            }
        } catch (e: TranslationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Online translation failed", e)
            throw TranslationException("Online translation failed: ${e.message}", e)
        }
    }

    private fun parseTranslationResponse(response: String): String {
        return try {
            val jsonArray = JSONArray(response)
            val segments = jsonArray.optJSONArray(0) ?: return ""
            val result = StringBuilder()
            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i)
                if (segment != null) {
                    val translatedText = segment.optString(0)
                    if (!translatedText.isNullOrEmpty() && translatedText != "null") {
                        result.append(translatedText)
                    }
                }
            }
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse translation response JSON", e)
            ""
        }
    }

    companion object {
        private const val TAG = "OnlineTranslationProvider"
    }
}
