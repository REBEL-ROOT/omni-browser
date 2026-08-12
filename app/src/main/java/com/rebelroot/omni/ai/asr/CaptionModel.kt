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
 * A single timed caption cue.
 * @param startMs start offset in the media (ms).
 * @param endMs end offset in the media (ms).
 * @param text The caption text (may be the original ASR text or a translation).
 */
data class CaptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * A single recognized word with its timing. Produced by an ASR engine and fed to
 * [CaptionSegmenter] to build well-spaced caption cues.
 */
data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long
)
