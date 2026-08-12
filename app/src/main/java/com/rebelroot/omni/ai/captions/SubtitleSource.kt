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

import com.rebelroot.omni.ai.asr.CaptionSegment

/** Where a subtitle track came from. */
enum class SubtitleOrigin {
    /** Provided natively by the site/player (never pretend generated ones are native). */
    NATIVE,

    /** Loaded from an external file. */
    EXTERNAL,

    /** Produced on-device by ASR. */
    GENERATED,

    /** A translation of an existing (native/external) track. */
    TRANSLATED,

    /** A translation of generated (ASR) captions. */
    GENERATED_AND_TRANSLATED
}

/** Unified representation of a subtitle track so the player treats all sources
 *  (native, external, generated, translated) consistently. */
data class SubtitleSource(
    val language: String,
    val origin: SubtitleOrigin,
    val segments: List<CaptionSegment>
)
