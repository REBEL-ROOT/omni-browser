package com.omni.browser.tools

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationManager {

    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        object DownloadingModel : TranslationStatus()
        object Ready : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status

    private var translator: Translator? = null

    /**
     * Initializes offline translation model downloading
     */
    fun setupLanguage(sourceLang: String, targetLang: String, onSuccess: () -> Unit) {
        _status.value = TranslationStatus.DownloadingModel
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        val localTranslator = Translation.getClient(options)
        translator = localTranslator

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        localTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                _status.value = TranslationStatus.Ready
                onSuccess()
                Log.i("TranslationManager", "On-device translation model ready.")
            }
            .addOnFailureListener { e ->
                _status.value = TranslationStatus.Error(e.message ?: "Failed to download model")
                Log.e("TranslationManager", "Offline model download failure", e)
            }
    }

    suspend fun translateText(text: String): String = suspendCancellableCoroutine { continuation ->
        val activeTranslator = translator
        if (activeTranslator == null) {
            continuation.resumeWithException(IllegalStateException("Translator not initialized. Call setupLanguage first."))
            return@suspendCancellableCoroutine
        }

        activeTranslator.translate(text)
            .addOnSuccessListener { result ->
                continuation.resume(result)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    fun close() {
        translator?.close()
        translator = null
        _status.value = TranslationStatus.Idle
    }
}
