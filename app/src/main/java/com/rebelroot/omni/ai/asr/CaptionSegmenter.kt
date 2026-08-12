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
import kotlinx.coroutines.withContext

/**
 * Turns word-level ASR timestamps into practical caption cues.
 *
 * Splits on:
 *  - speech pauses (gap wider than [Options.maxGapMs]),
 *  - maximum cue duration ([Options.maxDurationMs]),
 *  - maximum character length ([Options.maxChars]).
 *
 * This avoids giant paragraphs and keeps cues comfortably readable per the
 * caption-timing requirements. Pure and unit-testable.
 */
object CaptionSegmenter {

    data class Options(
        val maxDurationMs: Long = 7_000,
        val maxChars: Int = 42,
        val maxGapMs: Long = 900
    )

    fun segment(
        words: List<WordTimestamp>,
        options: Options = Options()
    ): List<CaptionSegment> = withContextResult(words, options)

    /**
     * Pure function exposed for tests/other callers; coroutine wrapper only adds
     * an IO hop (ASR results are usually already off the main thread).
     */
    suspend fun segmentOnIo(
        words: List<WordTimestamp>,
        options: Options = Options()
    ): List<CaptionSegment> = withContext(Dispatchers.Default) { segment(words, options) }

    private fun withContextResult(words: List<WordTimestamp>, options: Options): List<CaptionSegment> {
        if (words.isEmpty()) return emptyList()

        val segments = mutableListOf<CaptionSegment>()
        var current = mutableListOf<WordTimestamp>()
        var segStart = words.first().startMs

        fun flush() {
            if (current.isEmpty()) return
            val endMs = current.last().endMs.coerceAtLeast(segStart + 1)
            val text = current.joinToString(" ") { it.word }
            segments.add(CaptionSegment(segStart, endMs, text))
            current = mutableListOf()
        }

        for (w in words) {
            if (current.isNotEmpty()) {
                val gap = w.startMs - current.last().endMs
                val exceedsDuration = w.endMs - segStart > options.maxDurationMs
                val exceedsChars = current.sumOf { it.word.length } + 1 + w.word.length > options.maxChars
                if (gap > options.maxGapMs || exceedsDuration || exceedsChars) {
                    flush()
                    segStart = w.startMs
                }
            }
            current.add(w)
        }
        flush()
        return segments
    }
}
