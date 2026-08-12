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

import com.rebelroot.omni.ai.asr.VoskAsrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/** One committed caption line shown in the player overlay. */
data class CaptionLine(val text: String, val timeMs: Long)

/**
 * Live (near-real-time) caption pipeline for the native player.
 *
 * PCM arrives from the Media3 audio tap (see [com.rebelroot.omni.ai.media.PcmTeeAudioProcessor])
 * on the audio thread. This controller:
 *  1. converts to 16 kHz mono,
 *  2. queues the chunk (bounded) and lets a single worker drain it into Vosk —
 *     recognition never runs on the audio/UI thread, so playback cannot stutter,
 *  3. publishes committed [lines] and the in-progress [partial] line for the UI.
 *
 * The engine is only created/loaded while captions are enabled; [release] tears
 * everything down when the player closes or captions turn off.
 */
class LiveCaptionEngine(
    private val asr: VoskAsrEngine,
    private val clockMs: () -> Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val queue = ConcurrentLinkedQueue<ShortArray>()
    private val _lines = MutableStateFlow<List<CaptionLine>>(emptyList())
    val lines: StateFlow<List<CaptionLine>> = _lines

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    @Volatile private var released = false

    init {
        scope.launch {
            while (isActive && !released) {
                val chunk = queue.poll()
                if (chunk == null) {
                    delay(16)
                    continue
                }
                runCatching {
                    val isFinal = asr.feed(chunk)
                    if (isFinal) {
                        val text = asr.finalText()
                        if (text.isNotBlank()) {
                            _lines.value = (_lines.value + CaptionLine(text, clockMs())).takeLast(MAX_LINES)
                            _partial.value = ""
                        }
                    } else {
                        _partial.value = asr.partialText()
                    }
                }
            }
        }
    }

    /**
     * Feed a raw PCM chunk from the audio tap (interleaved, 16-bit little-endian).
     * Cheap conversion happens on the caller's thread; recognition happens on the
     * worker so audio latency is unaffected.
     */
    fun feedPcm(bytes: ByteArray, sampleRate: Int, channelCount: Int) {
        if (released || bytes.isEmpty()) return
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer().let { b -> ShortArray(b.remaining()).also { b.get(it) } }
        val mono = VoskAsrEngine.toMono16k(shorts, sampleRate, channelCount)
        if (mono.isNotEmpty()) {
            // Bounded queue: drop the oldest chunk if the worker falls behind.
            if (queue.size > MAX_QUEUE) queue.poll()
            queue.add(mono)
        }
    }

    /** Clear captions and reset the recognizer (e.g. after a seek). */
    suspend fun reset() {
        _lines.value = emptyList()
        _partial.value = ""
        runCatching { asr.reset() }
    }

    fun release() {
        if (released) return
        released = true
        scope.launch { runCatching { asr.release() } }
        scope.cancel()
    }

    companion object {
        private const val MAX_LINES = 3
        private const val MAX_QUEUE = 64
    }
}
