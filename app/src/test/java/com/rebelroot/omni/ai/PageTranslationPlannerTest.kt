/*
 * Omni Browser - Offline AI page translation planner tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.web

import com.rebelroot.omni.ai.translation.TranslationCoordinator
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.ai.translation.TranslationProvider
import com.rebelroot.omni.ai.translation.TranslationResult
import com.rebelroot.omni.ai.translation.UnsupportedLanguagePairException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PageTranslationPlannerTest {

    private fun countingOfflineProvider(counter: AtomicInteger) = object : TranslationProvider {
        override val id = "offline-fake"
        override val isOffline = true
        override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
        override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String): TranslationResult {
            counter.incrementAndGet()
            return TranslationResult(text.uppercase(), sourceLanguage ?: "auto", targetLanguage, id, true)
        }
    }

    @Test
    fun planner_deduplicatesIdenticalSegments() = runBlocking {
        val calls = AtomicInteger(0)
        val coordinator = TranslationCoordinator(
            onlineProvider = object : TranslationProvider {
                override val id = "online"
                override val isOffline = false
                override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
                override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String) =
                    TranslationResult(text, sourceLanguage ?: "auto", targetLanguage, id, false)
            },
            offlineProvider = countingOfflineProvider(calls),
            modeProvider = { TranslationMode.ASK }
        )
        val segments = listOf(
            PageSegment("0", "hello"),
            PageSegment("1", "hello"),
            PageSegment("2", "world")
        )
        val out = PageTranslationPlanner.translateSegments(coordinator, segments, "en", "es")
        assertEquals(listOf(
            TranslatedSegment("0", "HELLO"),
            TranslatedSegment("1", "HELLO"),
            TranslatedSegment("2", "WORLD")
        ), out.segments)
        // "hello" translated once despite appearing twice.
        assertEquals(2, calls.get())
        assertEquals(0, out.failureCount)
    }

    @Test
    fun planner_keepsOriginalWhenOfflinePairUnsupported() = runBlocking {
        val offline = object : TranslationProvider {
            override val id = "offline-fake"
            override val isOffline = true
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = false
            override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String): TranslationResult {
                throw UnsupportedLanguagePairException("nope", id, sourceLanguage, targetLanguage)
            }
        }
        val online = object : TranslationProvider {
            override val id = "online-fake"
            override val isOffline = false
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(text: String, sourceLanguage: String?, targetLanguage: String) =
                TranslationResult("CLOUD", sourceLanguage ?: "auto", targetLanguage, id, false)
        }
        // OFFLINE_ONLY must never reach the online provider.
        val coordinator = TranslationCoordinator(online, offline) { TranslationMode.OFFLINE_ONLY }
        val segments = listOf(PageSegment("0", "hello"))
        val out = PageTranslationPlanner.translateSegments(coordinator, segments, "en", "zh")
        // Graceful: original kept, not silently sent to cloud.
        assertEquals("hello", out.segments.first().text)
        assertEquals(1, out.failureCount)
    }
}
