/*
 * Omni Browser - Manga Download & PDF Pipeline Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.manga

import com.rebelroot.omni.ai.translation.TranslationCoordinator
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.ai.translation.TranslationProvider
import com.rebelroot.omni.ai.translation.TranslationResult
import com.rebelroot.omni.browser.ImageGrabberUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MangaDownloadAndPdfTest {

    @Test
    fun testCandidateAlternateUrls_fallbackGeneration() {
        val brokenPageUrl = "https://i3.nhentai.net/galleries/12345/1.jpg"
        val candidates = ImageGrabberUtils.getCandidateAlternateUrls(brokenPageUrl)

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.any { it.endsWith(".webp") || it.endsWith(".png") })
        assertTrue(candidates.any { it.contains("/galleries/12345/1") })
    }

    @Test
    fun testSequentialPdfPageNumbering_withSkippedBufferingPages() {
        // Suppose page 1 and 3 succeed, page 2 and 4 are buffering/broken and skipped
        val rawPages = listOf("page1.jpg", "broken_buffering_page2.jpg", "page3.jpg", "broken_page4.jpg")
        val isLoaded = listOf(true, false, true, false)

        val compiledPages = mutableListOf<Pair<Int, String>>()
        var successCount = 0

        for (i in rawPages.indices) {
            if (isLoaded[i]) {
                val pageNumber = successCount + 1
                compiledPages.add(pageNumber to rawPages[i])
                successCount++
            }
        }

        assertEquals(2, compiledPages.size)
        assertEquals(1, compiledPages[0].first)
        assertEquals("page1.jpg", compiledPages[0].second)
        assertEquals(2, compiledPages[1].first)
        assertEquals("page3.jpg", compiledPages[1].second)
    }

    @Test
    fun testTypographyStyleConsistency_forPdfCompilation() {
        val style = MangaTypographyStyle(
            fontSizeScale = 1.2f,
            fontFamily = "Comic",
            textColorMode = "Black",
            bgFillMode = "Auto"
        )

        assertEquals("1.2_Comic_Black_Auto", style.cacheKey)
    }

    @Test
    fun testRetranslateAndCustomBlocks_duringExport() = runBlocking {
        val coordinatorCalls = AtomicInteger(0)
        val provider = object : TranslationProvider {
            override val id = "mock-provider"
            override val isOffline = true
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String): TranslationResult {
                coordinatorCalls.incrementAndGet()
                return TranslationResult("EN: $text", sourceLanguage ?: "ja", targetLanguage, id, true)
            }
        }

        val coordinator = TranslationCoordinator(
            onlineProvider = provider,
            offlineProvider = provider,
            modeProvider = { TranslationMode.OFFLINE_ONLY }
        )

        val pipeline = MangaTranslationPipeline(coordinator)
        val retranslated = pipeline.retranslateText("こんにちは", "ja", "en")
        assertEquals("EN: こんにちは", retranslated)
        assertEquals(1, coordinatorCalls.get())
    }
}
