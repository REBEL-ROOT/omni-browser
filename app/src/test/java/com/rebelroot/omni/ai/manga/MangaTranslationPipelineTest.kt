/*
 * Omni Browser - Manga & Image Translation Pipeline Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.manga

import com.rebelroot.omni.ai.translation.TranslationCoordinator
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.ai.translation.TranslationProvider
import com.rebelroot.omni.ai.translation.TranslationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MangaTranslationPipelineTest {

    @Test
    fun testDialogueBlock_creationAndDefaults() {
        val line1 = MangaDialogueLine("お前はもう", MangaRect(100f, 50f, 130f, 200f))
        val line2 = MangaDialogueLine("死んでいる", MangaRect(60f, 50f, 90f, 200f))

        val block = MangaDialogueBlock(
            id = "bubble_1",
            rawText = "お前はもう死んでいる",
            translatedText = "You are already dead.",
            boundingBox = MangaRect(50f, 40f, 140f, 210f),
            lines = listOf(line1, line2),
            isVertical = true
        )

        assertEquals("bubble_1", block.id)
        assertEquals("お前はもう死んでいる", block.rawText)
        assertEquals("You are already dead.", block.translatedText)
        assertTrue(block.isVertical)
        assertEquals(2, block.lines.size)
        assertEquals(90f, block.boundingBox.width, 0.01f)
        assertEquals(170f, block.boundingBox.height, 0.01f)
    }

    @Test
    fun testTypographyStyle_defaultsAndCustom() {
        val defaultStyle = MangaTypographyStyle()
        assertEquals(1.0f, defaultStyle.fontSizeScale, 0.01f)
        assertEquals("Comic", defaultStyle.fontFamily)
        assertEquals("Auto", defaultStyle.textColorMode)
        assertEquals("Auto", defaultStyle.bgFillMode)

        val customStyle = MangaTypographyStyle(
            fontSizeScale = 1.35f,
            fontFamily = "Serif",
            textColorMode = "Black",
            bgFillMode = "White"
        )
        assertEquals("1.35_Serif_Black_White", customStyle.cacheKey)
    }

    @Test
    fun testReadingOrder_RtlMangaVsLtrWebtoon() {
        // Two speech bubbles in the same panel band (top ~ 50px)
        // Bubble A on Right (x=300..450), Bubble B on Left (x=50..200)
        val rightBubble = MangaDialogueBlock(
            id = "right",
            rawText = "First to read in Manga (Right)",
            boundingBox = MangaRect(300f, 50f, 450f, 150f)
        )
        val leftBubble = MangaDialogueBlock(
            id = "left",
            rawText = "Second to read in Manga (Left)",
            boundingBox = MangaRect(50f, 50f, 200f, 150f)
        )

        val blocks = listOf(leftBubble, rightBubble)

        // Japanese Manga (RTL): Right bubble must come first
        val bandHeight = 120f
        val rtlSorted = blocks.sortedWith { a, b ->
            val aBand = (a.boundingBox.top / bandHeight).toInt()
            val bBand = (b.boundingBox.top / bandHeight).toInt()
            if (aBand != bBand) {
                aBand.compareTo(bBand)
            } else {
                b.boundingBox.centerX.compareTo(a.boundingBox.centerX) // Right to Left
            }
        }
        assertEquals("right", rtlSorted[0].id)
        assertEquals("left", rtlSorted[1].id)

        // Webtoon (LTR): Left bubble must come first
        val ltrSorted = blocks.sortedWith { a, b ->
            val aBand = (a.boundingBox.top / bandHeight).toInt()
            val bBand = (b.boundingBox.top / bandHeight).toInt()
            if (aBand != bBand) {
                aBand.compareTo(bBand)
            } else {
                a.boundingBox.centerX.compareTo(b.boundingBox.centerX) // Left to Right
            }
        }
        assertEquals("left", ltrSorted[0].id)
        assertEquals("right", ltrSorted[1].id)
    }

    @Test
    fun testPipelineCoordinatorIntegration() = runBlocking {
        val translateCalls = AtomicInteger(0)
        val provider = object : TranslationProvider {
            override val id = "mock-provider"
            override val isOffline = true
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String): TranslationResult {
                translateCalls.incrementAndGet()
                return TranslationResult("TRANSLATED: $text", sourceLanguage ?: "ja", targetLanguage, id, true)
            }
        }

        val coordinator = TranslationCoordinator(
            onlineProvider = provider,
            offlineProvider = provider,
            modeProvider = { TranslationMode.OFFLINE_ONLY }
        )

        val res = coordinator.translate("こんにちは世界", "ja", "en")
        assertEquals("TRANSLATED: こんにちは世界", res.translatedText)
        assertEquals(1, translateCalls.get())
    }
}
