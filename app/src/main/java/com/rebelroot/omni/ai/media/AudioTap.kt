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

package com.rebelroot.omni.ai.media

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * Pass-through Media3 [AudioProcessor] that tees the decoded PCM (16-bit) to an
 * on-device caption engine while leaving playback untouched.
 *
 * DRM: this tap only sees audio the Media3 pipeline legitimately decoded for
 * NON-protected playback. Protected/secure paths are never routed here; if the
 * stream cannot be accessed legally the player reports "offline captions
 * unavailable" instead.
 */
@UnstableApi
class PcmTeeAudioProcessor(
    private val sink: (bytes: ByteArray, sampleRate: Int, channelCount: Int) -> Unit
) : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat? = null
    private var pendingOutput = ByteBuffer.allocate(0)

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputFormat ?: return
        val bytes = ByteArray(inputBuffer.remaining())
        inputBuffer.get(bytes)
        runCatching { sink(bytes, format.sampleRate, format.channelCount) }
        pendingOutput = ByteBuffer.wrap(bytes)
    }

    override fun queueEndOfStream() {
        // Nothing to do: this is a pass-through tee.
    }

    override fun getOutput(): ByteBuffer = pendingOutput

    override fun isEnded(): Boolean = false

    override fun flush() {
        pendingOutput = ByteBuffer.allocate(0)
    }

    override fun reset() {
        flush()
        inputFormat = null
    }
}

/**
 * Renderers factory that injects [PcmTeeAudioProcessor] into the audio sink.
 * Used when live caption generation is enabled so the caption engine receives
 * PCM without affecting playback.
 */
@UnstableApi
class CaptionRenderersFactory(
    context: Context,
    private val tee: PcmTeeAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(tee))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
