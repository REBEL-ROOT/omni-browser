package com.rebelroot.omni.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslationManager {

    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        object DownloadingModel : TranslationStatus()
        object Ready : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status

    private var sourceLang: String = "auto"
    private var targetLang: String = "en"

    /**
     * Initializes online translation. Since it's online, the API is immediately ready without downloading models.
     */
    fun setupLanguage(sourceLang: String, targetLang: String, onSuccess: () -> Unit) {
        this.sourceLang = sourceLang.lowercase()
        this.targetLang = targetLang.lowercase()
        
        Log.i("TranslationManager", "Configuring online translator: $sourceLang -> $targetLang")
        _status.value = TranslationStatus.Ready
        onSuccess()
    }

    /**
     * Translates custom text using Google's free gtx translation endpoint.
     */
    suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                parseTranslationResponse(responseText)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("TranslationManager", "HTTP Error $responseCode: $errorText")
                throw Exception("Translation API error ($responseCode)")
            }
        } catch (e: Exception) {
            Log.e("TranslationManager", "Online translation failed", e)
            throw e
        }
    }

    private fun parseTranslationResponse(response: String): String {
        try {
            val jsonArray = JSONArray(response)
            val segments = jsonArray.optJSONArray(0) ?: return ""
            val result = StringBuilder()
            for (i in 0 until segments.length()) {
                val segment = segments.optJSONArray(i)
                if (segment != null) {
                    val translatedText = segment.optString(0)
                    if (translatedText != null && translatedText != "null") {
                        result.append(translatedText)
                    }
                }
            }
            return result.toString()
        } catch (e: Exception) {
            Log.e("TranslationManager", "Failed to parse translation response JSON", e)
            return ""
        }
    }

    fun close() {
        _status.value = TranslationStatus.Idle
    }
}
