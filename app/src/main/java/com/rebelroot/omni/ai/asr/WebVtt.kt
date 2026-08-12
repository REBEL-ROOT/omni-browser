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
 * Renders [CaptionSegment]s as WebVTT (for Media3 `SubtitleConfiguration`) and
 * parses WebVTT back into segments (for existing/external subtitle tracks).
 *
 * Time format is `HH:MM:SS.mmm` as required by the caption-timing rules.
 */
object WebVtt {

    fun render(segments: List<CaptionSegment>): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        for ((i, s) in segments.withIndex()) {
            sb.append(i).append('\n')
            sb.append(formatTime(s.startMs)).append(" --> ").append(formatTime(s.endMs)).append('\n')
            sb.append(s.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    /** Parse a WebVTT document into [CaptionSegment]s (ignores styling/voices). */
    fun parse(vtt: String): List<CaptionSegment> {
        val lines = vtt.replace("\r\n", "\n").split('\n')
        val segments = mutableListOf<CaptionSegment>()
        var i = 0
        if (lines.isNotEmpty() && lines[0].trim().startsWith("WEBVTT")) i = 1
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("NOTE") || line.startsWith("STYLE") || line.startsWith("REGION")) {
                i++
                continue
            }
            // Optional cue index — skip numeric-only lines that are not timestamps.
            if (line.all { it.isDigit() }) {
                i++
                continue
            }
            val arrow = line.indexOf("-->")
            if (arrow < 0) { i++; continue }
            val start = parseTime(line.substring(0, arrow).trim())
            val end = parseTime(line.substring(arrow + 3).trim().substringBefore(' '))
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[i])
                i++
            }
            segments.add(CaptionSegment(start, end, text.toString().trim()))
            i++ // skip blank separator
        }
        return segments
    }

    /** `HH:MM:SS.mmm` (hour component is padded to at least 2 digits). */
    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1000
        val millis = total % 1000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    fun parseTime(s: String): Long {
        val parts = s.split(':')
        if (parts.size < 2) return 0L
        val secondsPart = parts.last().split('.')
        val sec = secondsPart[0].toLongOrNull() ?: 0L
        val millis = secondsPart.getOrNull(1)?.take(3)?.trim()?.let {
            (it + "000").take(3).toLong()
        } ?: 0L
        val min = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
        val hour = if (parts.size >= 3) parts[parts.size - 3].toLongOrNull() ?: 0L else 0L
        return hour * 3_600_000 + min * 60_000 + sec * 1000 + millis
    }
}
