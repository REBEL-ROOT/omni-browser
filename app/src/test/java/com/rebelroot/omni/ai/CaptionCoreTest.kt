/*
 * Omni Browser - Offline AI captions tests (segmenter, WebVTT, translation)
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai

import com.rebelroot.omni.ai.asr.CaptionSegment
import com.rebelroot.omni.ai.asr.CaptionSegmenter
import com.rebelroot.omni.ai.asr.WebVtt
import com.rebelroot.omni.ai.asr.WordTimestamp
import com.rebelroot.omni.ai.captions.CaptionTranslation
import com.rebelroot.omni.ai.captions.SubtitleOrigin
import com.rebelroot.omni.ai.captions.SubtitleSource
import com.rebelroot.omni.ai.translation.TranslationCoordinator
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.ai.translation.TranslationProvider
import com.rebelroot.omni.ai.translation.TranslationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSegmenterTest {

    @Test
    fun segmenter_splitsOnLongPauses() {
        val words = listOf(
            WordTimestamp("hello", 1000, 1400),
            WordTimestamp("world", 1800, 2200),
            // 3s gap > 900ms default
            WordTimestamp("second", 5200, 5800)
        )
        val segs = CaptionSegmenter.segment(words)
        assertEquals(2, segs.size)
        assertEquals("hello world", segs[0].text)
        assertEquals(1000, segs[0].startMs)
        assertEquals(2200, segs[0].endMs)
        assertEquals("second", segs[1].text)
        assertEquals(5200, segs[1].startMs)
    }

    @Test
    fun segmenter_splitsOnMaxDuration() {
        val words = (0 until 20).map { WordTimestamp("word$it", it * 500L, it * 500L + 400) }
        val segs = CaptionSegmenter.segment(words, CaptionSegmenter.Options(maxDurationMs = 5000))
        // Every segment must respect the max duration.
        assertTrue(segs.all { it.endMs - it.startMs <= 5000 })
        assertTrue(segs.size >= 3)
    }

    @Test
    fun segmenter_splitsOnMaxChars() {
        val words = listOf(
            WordTimestamp("aaaaaaaaaa", 0, 500),
            WordTimestamp("bbbbbbbbbb", 600, 1100),
            WordTimestamp("ccc", 1200, 1500)
        )
        val segs = CaptionSegmenter.segment(words, CaptionSegmenter.Options(maxChars = 12))
        // Each 10-char word exceeds the 12-char budget with its neighbour, so each
        // becomes its own cue (and never exceeds the budget).
        assertEquals(3, segs.size)
        assertTrue(segs.all { it.text.length <= 12 })
    }

    @Test
    fun segmenter_emptyInput() {
        assertEquals(0, CaptionSegmenter.segment(emptyList()).size)
    }
}

class WebVttTest {

    @Test
    fun webvtt_rendersTimestampsAndHeader() {
        val vtt = WebVtt.render(listOf(CaptionSegment(1000, 3500, "Hello everyone.")))
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.500"))
        assertTrue(vtt.contains("Hello everyone."))
    }

    @Test
    fun webvtt_roundTripsSegments() {
        val segs = listOf(
            CaptionSegment(0, 2500, "First line"),
            CaptionSegment(3000, 96000, "Second line with minutes")
        )
        val parsed = WebVtt.parse(WebVtt.render(segs))
        assertEquals(segs, parsed)
    }

    @Test
    fun webvtt_hourFormatPads() {
        assertEquals("01:00:00.000", WebVtt.formatTime(3_600_000))
        assertEquals("00:00:00.007", WebVtt.formatTime(7))
    }
}

class CaptionTranslationTest {

    private fun offlineProvider() = object : TranslationProvider {
        override val id = "offline-fake"
        override val isOffline = true
        override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
        override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String): TranslationResult =
            TranslationResult("{${text.uppercase()}}", sourceLanguage ?: "auto", targetLanguage, id, true)
    }

    private fun coordinator(mode: TranslationMode) = TranslationCoordinator(
        onlineProvider = object : TranslationProvider {
            override val id = "online"
            override val isOffline = false
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String) =
                TranslationResult("CLOUD", sourceLanguage ?: "auto", targetLanguage, id, false)
        },
        offlineProvider = offlineProvider(),
        modeProvider = { mode }
    )

    @Test
    fun captionTranslation_preservesTimingAndDedups() = runBlocking {
        val source = SubtitleSource(
            "en", SubtitleOrigin.GENERATED,
            listOf(
                CaptionSegment(0, 1000, "hello"),
                CaptionSegment(1000, 2000, "world"),
                CaptionSegment(2000, 3000, "hello") // duplicate text
            )
        )
        val out = CaptionTranslation.translate(source, coordinator(TranslationMode.ASK), "es")
        assertEquals("{HELLO}", out.segments[0].text)
        assertEquals("{WORLD}", out.segments[1].text)
        assertEquals("{HELLO}", out.segments[2].text)
        assertEquals(0, out.segments[0].startMs)
        assertEquals(3000, out.segments[2].endMs)
        assertEquals(SubtitleOrigin.GENERATED_AND_TRANSLATED, out.origin)
        assertEquals("es", out.language)
    }

    @Test
    fun captionTranslation_nativeTrackBecomesTranslated() = runBlocking {
        val source = SubtitleSource(
            "fr", SubtitleOrigin.NATIVE, listOf(CaptionSegment(0, 1000, "bonjour"))
        )
        val out = CaptionTranslation.translate(source, coordinator(TranslationMode.ASK), "en")
        assertEquals(SubtitleOrigin.TRANSLATED, out.origin)
        assertEquals("{BONJOUR}", out.segments.first().text)
    }

    @Test
    fun captionTranslation_offlineOnlyNeverCallsCloud() = runBlocking {
        val source = SubtitleSource(
            "en", SubtitleOrigin.NATIVE, listOf(CaptionSegment(0, 1000, "hello"))
        )
        // In OFFLINE_ONLY the fake offline provider supports the pair, so the
        // result MUST come from it (the online fake would return "CLOUD").
        val out = CaptionTranslation.translate(source, coordinator(TranslationMode.OFFLINE_ONLY), "es")
        assertEquals("{HELLO}", out.segments.first().text)
    }
}