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

package com.rebelroot.omni.tools

import android.util.Log
import com.rebelroot.omni.ai.engine.LexiconTranslationEngine
import com.rebelroot.omni.ai.engine.TranslationEngineManager
import com.rebelroot.omni.ai.translation.OnlineTranslationProvider
import com.rebelroot.omni.ai.translation.OfflineTranslationProvider
import com.rebelroot.omni.ai.translation.TranslationCoordinator
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.ai.translation.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Public translation facade used by the browser UI.
 *
 * This now delegates to the unified [TranslationCoordinator] which selects
 * between the online backend ([OnlineTranslationProvider], the original Google
 * "gtx" endpoint) and the offline backend ([OfflineTranslationProvider],
 * driven by on-device engines via [TranslationEngineManager]).
 *
 * The default [TranslationMode.ASK] preserves the previous behaviour: it uses a
 * local model when one is installed for the pair, otherwise falls back to the
 * online service. Users can switch to [TranslationMode.OFFLINE_ONLY] in settings;
 * in that mode no cloud request is ever made.
 */
class TranslationManager {

    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        object DownloadingModel : TranslationStatus()
        object Ready : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status

    private val lexiconEngine = LexiconTranslationEngine()
    private val engineManager = TranslationEngineManager.withDefaults(lexiconEngine)
    private val onlineProvider = OnlineTranslationProvider()
    private val offlineProvider = OfflineTranslationProvider(engineManager)

    private val coordinator: TranslationCoordinator = TranslationCoordinator.default(
        onlineProvider = onlineProvider,
        offlineProvider = offlineProvider,
        modeProvider = { translationMode }
    )

    /** The active translation policy. */
    @Volatile var translationMode: TranslationMode = TranslationMode.ASK
        private set

    private var sourceLang: String = "auto"
    private var targetLang: String = "en"

    /** Configure the language pair. Kept for backwards compatibility with the
     *  existing translation dialog. Online translation is immediately ready. */
    fun setupLanguage(sourceLang: String, targetLang: String, onSuccess: () -> Unit) {
        this.sourceLang = sourceLang.lowercase()
        this.targetLang = targetLang.lowercase()
        Log.i(TAG, "Configuring translator: $sourceLang -> $targetLang (mode=$translationMode)")
        _status.value = TranslationStatus.Ready
        onSuccess()
    }

    /** Translate arbitrary text using the active mode. Suspending; caller should
     *  show progress and handle exceptions. */
    suspend fun translateText(text: String): String = withContext(Dispatchers.IO) {
        coordinator.translate(text, sourceLang, targetLang).translatedText
    }

    /** Rich translation using the active mode (returns provider + offline flag). */
    suspend fun translate(text: String): TranslationResult =
        coordinator.translate(text, sourceLang, targetLang)

    /** Whether an offline engine can serve the currently configured pair. */
    suspend fun isOfflineAvailable(): Boolean =
        coordinator.canTranslateOffline(sourceLang, targetLang)

    fun setMode(newMode: TranslationMode) {
        translationMode = newMode
        Log.i(TAG, "Translation mode set to $translationMode")
    }

    fun getMode(): TranslationMode = translationMode

    /** Expose the underlying coordinator (used by the page-translation bridge). */
    val translationCoordinator: TranslationCoordinator get() = coordinator

    /** Release resident offline models (call when translation is no longer active). */
    suspend fun releaseModels() {
        runCatching { engineManager.releaseAll() }
    }

    fun close() {
        _status.value = TranslationStatus.Idle
    }

    companion object {
        private const val TAG = "TranslationManager"
    }
}
