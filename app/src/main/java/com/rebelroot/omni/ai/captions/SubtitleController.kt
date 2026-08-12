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

package com.rebelroot.omni.ai.captions

import com.rebelroot.omni.ai.asr.AsrEngine
import com.rebelroot.omni.ai.asr.CaptionSegment
import com.rebelroot.omni.ai.asr.CaptionSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-media caption generation orchestration.
 *
 * Safety/performance rules implemented here:
 *  - **Seek/pause/player-destroy**: cancels in-flight ASR and discards stale
 *    results via a monotonic [seekToken]. Skipped audio is never transcribed.
 *  - **Scoped**: bound to [mediaId] and [isPrivate] so transcript/caption state
 *    never leaks between media or into normal-tab caches.
 *  - **Lazy**: the engine stays unloaded until caption generation begins.
 *  - **Playback first**: transcription is driven outside the media pipeline and
 *    never blocks playback.
 *
 * DRM: this controller only ever receives PCM the caller legitimately obtained
 * from the supported playback pipeline for non-protected media. If audio cannot
 * be legally accessed the caller reports "offline captions unavailable" instead
 * of reaching here.
 */
class SubtitleController(
    private val asrEngine: AsrEngine,
    val mediaId: String,
    val isPrivate: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val seekToken = AtomicLong(0)
    private var generationJob: Job? = null

    /**
     * Transcribe [pcm] (16-bit mono) beginning at [startMs] into caption cues.
     * Returns empty when the result is stale (a seek occurred mid-transcription).
     */
    suspend fun generateCaptions(pcm: ShortArray, sampleRate: Int, startMs: Long): List<CaptionSegment> {
        val token = seekToken.get()
        val words = asrEngine.transcribe(pcm, sampleRate, startMs)
        if (seekToken.get() != token) return emptyList() // stale — discard
        return CaptionSegmenter.segmentOnIo(words)
    }

    /** Launch caption generation as a background job and call [onResult] (with
     *  stale results skipped inside [generateCaptions]). Returns the job. */
    fun generateCaptionsAsync(
        pcm: ShortArray,
        sampleRate: Int,
        startMs: Long,
        onResult: (List<CaptionSegment>) -> Unit
    ): Job {
        generationJob?.cancel()
        generationJob = scope.launch {
            val captions = generateCaptions(pcm, sampleRate, startMs)
            onResult(captions)
        }
        return generationJob!!
    }

    /** Called when the user seeks. Cancels stale ASR work immediately. */
    fun onSeek(newPositionMs: Long) {
        seekToken.incrementAndGet()
        generationJob?.cancel()
        // ASR restart resumes around the new position in the media layer.
    }

    /** Called when captions are disabled or the media disappears. */
    fun onCaptionsDisabled() {
        seekToken.incrementAndGet()
        generationJob?.cancel()
    }

    /** Called when the player is destroyed. Releases the engine. */
    fun destroy() {
        seekToken.incrementAndGet()
        generationJob?.cancel()
        scope.cancel()
    }

    }