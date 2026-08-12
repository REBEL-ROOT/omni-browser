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

package com.rebelroot.omni.ai.asr

/**
 * A self-contained on-device speech-to-text engine.
 *
 * Loads a model from application-private storage (downloaded + verified by the
 * shared model platform), transcribes PCM audio given as 16-bit mono shorts, and
 * returns word-level timestamps so [CaptionSegmenter] can build caption cues.
 *
 * Engines MUST be fully on-device: no audio is ever uploaded. They must not know
 * about GeckoView, Compose, or Media3.
 *
 * DRM: an engine never receives protected audio. Media access is gated upstream —
 * if audio cannot be legitimately accessed the caller reports "offline captions
 * unavailable" instead.
 */
interface AsrEngine {

    /** Stable engine id, e.g. "vosk-small-en-us" or "whisper-tiny". */
    val id: String

    /** Relative quality tier (0..100) used to pick/compare engines. */
    val quality: Int

    fun isLoaded(): Boolean

    /** Load the model into memory (no-op if already loaded). */
    suspend fun load()

    /**
     * Transcribe 16-bit mono PCM starting at [startMs] into the media timeline.
     * Returns word timestamps (absolute offsets in ms).
     */
    suspend fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        startMs: Long
    ): List<WordTimestamp>

    /** Release the model from memory. Safe when not loaded. */
    suspend fun unload()

    /** Approximate resident memory in bytes when loaded. */
    fun estimatedMemoryBytes(): Long = 0L
}
