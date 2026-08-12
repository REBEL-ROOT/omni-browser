/*
 * Omni Browser - Offline AI translation provider tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.translation

import com.rebelroot.omni.ai.engine.LexiconTranslationEngine
import com.rebelroot.omni.ai.engine.TranslationEngineManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fake online provider that FAILS the test if it is ever invoked. Used to prove
 * that OFFLINE_ONLY never silently falls back to a cloud service.
 */
private class FailingOnlineProvider : TranslationProvider {
    override val id = "online-fake"
    override val isOffline = false
    override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String): Boolean =
        throw IllegalStateException("ONLINE PROVIDER MUST NOT BE CALLED IN OFFLINE_ONLY")
    override suspend fun translate(
        text: String, sourceLanguage: String?, targetLanguage: String
    ): TranslationResult = throw IllegalStateException("ONLINE PROVIDER MUST NOT BE CALLED IN OFFLINE_ONLY")
}

class TranslationProviderTest {

    private fun offlineProvider() =
        OfflineTranslationProvider(TranslationEngineManager.withDefaults(LexiconTranslationEngine()))

    // ── Lexicon engine ──────────────────────────────────────────────────────

    @Test
    fun lexicon_translatesKnownWordsAndPreservesUnknown() = runBlocking {
        val engine = LexiconTranslationEngine()
        val r = engine.translate("hello world cat", "en", "es")
        assertEquals("hola mundo gato", r.text)
    }

    @Test
    fun lexicon_preservesCapitalizationAndPunctuation() = runBlocking {
        val engine = LexiconTranslationEngine()
        val r = engine.translate("Hello, cat!", "en", "es")
        // "Hello" -> capitalized "Hola"; comma and "!" kept; "cat" -> "gato"
        assertEquals("Hola, gato!", r.text)
    }

    @Test
    fun lexicon_keepsUnknownWordsVerbatim() = runBlocking {
        val engine = LexiconTranslationEngine()
        val r = engine.translate("the xyz dog", "en", "es")
        assertEquals("el xyz perro", r.text)
    }

    @Test
    fun lexicon_supportsExplicitPairButNotAuto() = runBlocking {
        val engine = LexiconTranslationEngine()
        assertTrue(engine.supports("en", "es"))
        assertFalse(engine.supports("auto", "es"))
        assertFalse(engine.supports("en", "zh"))
    }

    // ── Offline provider ────────────────────────────────────────────────────

    @Test
    fun offlineProvider_availableForBundledPair() = runBlocking {
        assertTrue(offlineProvider().isAvailable("en", "es"))
    }

    // ── Coordinator: OFFLINE_ONLY never contacts the cloud ──────────────────

    @Test
    fun coordinator_offlineOnly_usesOfflineWhenAvailable() = runBlocking {
        val coordinator = TranslationCoordinator(
            onlineProvider = FailingOnlineProvider(),
            offlineProvider = offlineProvider(),
            modeProvider = { TranslationMode.OFFLINE_ONLY }
        )
        val result = coordinator.translate("hello cat", "en", "es")
        assertEquals("offline", result.providerId)
        assertTrue(result.isOffline)
        assertEquals("hola gato", result.translatedText)
    }

    @Test
    fun coordinator_offlineOnly_failsWhenOfflineUnavailable_andNeverCallsOnline() {
        val coordinator = TranslationCoordinator(
            onlineProvider = FailingOnlineProvider(),
            offlineProvider = offlineProvider(),
            modeProvider = { TranslationMode.OFFLINE_ONLY }
        )
        assertThrows(UnsupportedLanguagePairException::class.java) {
            runBlocking { coordinator.translate("hello", "en", "zh") }
        }
    }

    @Test
    fun coordinator_onlineOnly_usesOnline() = runBlocking {
        val online = object : TranslationProvider {
            override val id = "online-fake"
            override val isOffline = false
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(
                text: String, sourceLanguage: String?, targetLanguage: String
            ) = TranslationResult(text.reversed(), sourceLanguage ?: "auto", targetLanguage, id, false)
        }
        val coordinator = TranslationCoordinator(
            onlineProvider = online,
            offlineProvider = offlineProvider(),
            modeProvider = { TranslationMode.ONLINE_ONLY }
        )
        val result = coordinator.translate("abc", "en", "es")
        assertEquals("online-fake", result.providerId)
        assertFalse(result.isOffline)
        assertEquals("cba", result.translatedText)
    }

    @Test
    fun coordinator_ask_prefersOfflineWhenAvailable() = runBlocking {
        var onlineCalled = false
        val online = object : TranslationProvider {
            override val id = "online-fake"
            override val isOffline = false
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(
                text: String, sourceLanguage: String?, targetLanguage: String
            ): TranslationResult {
                onlineCalled = true
                return TranslationResult("CLOUD", sourceLanguage ?: "auto", targetLanguage, id, false)
            }
        }
        val coordinator = TranslationCoordinator(
            onlineProvider = online,
            offlineProvider = offlineProvider(),
            modeProvider = { TranslationMode.ASK }
        )
        val result = coordinator.translate("hello", "en", "es")
        assertFalse(onlineCalled)
        assertEquals("offline", result.providerId)
        assertEquals("hola", result.translatedText)
    }

    @Test
    fun coordinator_ask_fallsBackToOnlineWhenOfflineUnavailable() = runBlocking {
        var onlineCalled = false
        val online = object : TranslationProvider {
            override val id = "online-fake"
            override val isOffline = false
            override suspend fun isAvailable(sourceLanguage: String?, targetLanguage: String) = true
            override suspend fun translate(
                text: String, sourceLanguage: String?, targetLanguage: String
            ): TranslationResult {
                onlineCalled = true
                return TranslationResult("CLOUD", sourceLanguage ?: "auto", targetLanguage, id, false)
            }
        }
        val coordinator = TranslationCoordinator(
            onlineProvider = online,
            offlineProvider = offlineProvider(),
            modeProvider = { TranslationMode.ASK }
        )
        val result = coordinator.translate("hello", "en", "zh")
        assertTrue(onlineCalled)
        assertEquals("CLOUD", result.translatedText)
    }
}
