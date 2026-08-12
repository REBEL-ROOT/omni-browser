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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer

/**
 * Real on-device ASR engine backed by Vosk (native, ARM64-ready). Loads a
 * downloaded + verified + extracted model from application-private storage.
 *
 * Feed 16 kHz mono PCM via [feed]; when it returns true the utterance is final
 * and [finalText] holds the result. [partialText] exposes the in-progress line
 * for live captions. Fully on-device: no audio ever leaves the device.
 */
class VoskAsrEngine(modelDir: File) : AsrEngine {

    override val id: String = "vosk"
    override val quality: Int = 60

    private val model: Model = Model(modelDir.absolutePath)
    private val recognizer: Recognizer = Recognizer(model, SAMPLE_RATE.toFloat()).apply { setWords(true) }
    private val lock = Mutex()
    @Volatile private var released = false

    override fun isLoaded(): Boolean = !released

    override suspend fun load() {
        // Model is constructed with the engine; nothing else to load.
    }

    /** Feed one mono PCM chunk (16-bit, [SAMPLE_RATE] Hz). True when final. */
    suspend fun feed(mono16k: ShortArray): Boolean = withContext(Dispatchers.Default) {
        lock.withLock {
            val bytes = ByteBuffer.allocate(mono16k.size * 2)
            mono16k.forEach { bytes.putShort(it) }
            recognizer.acceptWaveForm(bytes.array(), bytes.array().size)
        }
    }

    /** Current in-progress (partial) transcript line. */
    suspend fun partialText(): String = withContext(Dispatchers.Default) {
        lock.withLock { partialOf(recognizer.getPartialResult()) }
    }

    /** Final transcript of the last accepted utterance. */
    suspend fun finalText(): String = withContext(Dispatchers.Default) {
        lock.withLock { textOf(recognizer.getResult()) }
    }

    /** Forget the current utterance (seek / captions off). */
    suspend fun reset() {
        lock.withLock { runCatching { recognizer.reset() } }
    }

    override suspend fun transcribe(
        pcm: ShortArray,
        sampleRate: Int,
        startMs: Long
    ): List<WordTimestamp> = withContext(Dispatchers.Default) {
        lock.withLock {
            // Batch path: convert to 16 kHz mono, feed, and return final words.
            val mono = toMono16k(pcm, sampleRate, 1)
            val bytes = ByteBuffer.allocate(mono.size * 2)
            mono.forEach { bytes.putShort(it) }
            val isFinal = recognizer.acceptWaveForm(bytes.array(), bytes.array().size)
            if (isFinal) wordsOf(recognizer.getResult(), startMs) else emptyList()
        }
    }

    override suspend fun unload() {
        release()
    }

    fun release() {
        if (released) return
        released = true
        runCatching { recognizer.close() }
        runCatching { model.close() }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** Mix to mono and (naively) resample to 16 kHz. */
        fun toMono16k(pcm: ShortArray, sampleRate: Int, channelCount: Int): ShortArray {
            if (sampleRate <= 0 || channelCount <= 0) return pcm
            val mono: ShortArray = if (channelCount <= 1) {
                pcm
            } else {
                ShortArray(pcm.size / channelCount).also { out ->
                    for (i in out.indices) {
                        var sum = 0
                        for (c in 0 until channelCount) sum += pcm[i * channelCount + c]
                        out[i] = (sum / channelCount).toShort()
                    }
                }
            }
            if (sampleRate == SAMPLE_RATE) return mono
            // Linear interpolation resample.
            val ratio = sampleRate.toDouble() / SAMPLE_RATE
            val outLen = (mono.size / ratio).toInt().coerceAtLeast(0)
            val out = ShortArray(outLen)
            for (i in 0 until outLen) {
                val srcPos = i * ratio
                val i0 = srcPos.toInt()
                val frac = srcPos - i0
                val s0 = mono.getOrElse(i0) { 0 }
                val s1 = mono.getOrElse(i0 + 1) { s0 }
                out[i] = (s0 + (s1 - s0) * frac).toInt().toShort()
            }
            return out
        }

        private fun partialOf(json: String): String = runCatching { JSONObject(json).optString("partial") }.getOrElse { "" }
        private fun textOf(json: String): String = runCatching { JSONObject(json).optString("text").trim() }.getOrElse { "" }

        /** Parse "{result:[{word,start,end},...]}" into absolute-ms word stamps. */
        fun wordsOf(json: String, startMs: Long): List<WordTimestamp> {
            return runCatching {
                val text = JSONObject(json).optString("text").ifBlank { return@runCatching emptyList() }
                val arr = JSONObject(json).optJSONArray("result") ?: return@runCatching emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val w = o.optString("word") ?: return@mapNotNull null
                    WordTimestamp(w, startMs + (o.optDouble("start", 0.0) * 1000).toLong(), startMs + (o.optDouble("end", 0.0) * 1000).toLong())
                }
            }.getOrDefault(emptyList())
        }
    }
}