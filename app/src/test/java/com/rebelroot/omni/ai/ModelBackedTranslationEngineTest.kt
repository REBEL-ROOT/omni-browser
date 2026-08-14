/*
 * Omni Browser - Offline AI model-backed translation engine tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.engine

import com.rebelroot.omni.ai.models.ModelDescriptor
import com.rebelroot.omni.ai.models.ModelTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelBackedTranslationEngineTest {

    private fun descriptor(id: String, src: String?, tgt: String): ModelDescriptor = ModelDescriptor(
        id = id, version = "1.0", task = ModelTask.TRANSLATION, name = id,
        sourceLanguage = src, targetLanguage = tgt, sizeBytes = 1,
        downloadUrl = "asset://ai/models/$id.json", sha256 = null,
        license = "Omni", sourceProject = "Omni Lexicon"
    )

    private fun writeModel(content: String): File =
        File.createTempFile("model", ".json").also { it.writeText(content); it.deleteOnExit() }

    @Test
    fun engine_loadsLexiconAndTranslates() = runBlocking {
        val file = writeModel("""{"hello":"hola","world":"mundo","cat":"gato"}""")
        val engine = ModelBackedTranslationEngine(descriptor("en-es", "en", "es"), file)
        assertTrue(engine.supports("auto", "es"))
        assertTrue(engine.supports("en", "es"))
        assertFalse(engine.supports("en", "fr"))

        engine.load()
        val r = engine.translate("Hello cat", "en", "es")
        assertEquals("Hola gato", r.text)
    }

    @Test
    fun engine_handlesUnparseableModelGracefully() = runBlocking {
        val file = writeModel("this is not json")
        val engine = ModelBackedTranslationEngine(descriptor("en-es", "en", "es"), file)
        // Usable flag is false, but supports() still reports the pair so the
        // coordinator can fall back to another engine / online rather than
        // returning untranslated text as if it succeeded.
        engine.load()
        assertTrue(engine.supports("auto", "es"))
        val r = engine.translate("hello world", "en", "es")
        // Falls back to returning the source unchanged (no fabrication).
        assertEquals("hello world", r.text)
    }

    @Test
    fun engineManager_prefersModelOverLexicon() = runBlocking {
        // "zebra" is unique to the model (not in the bundled lexicon), so a
        // successful translation proves the model-backed engine ran.
        val file = writeModel("""{"hello":"hola","zebra":"cebra"}""")
        val modelEngine = ModelBackedTranslationEngine(descriptor("en-es", "en", "es"), file)
        val lexicon = LexiconTranslationEngine()

        val manager = TranslationEngineManager(listOf(lexicon, modelEngine))
        val candidates = manager.enginesForPair("auto", "es")
        assertTrue(candidates.isNotEmpty())
        // Model-backed engine has higher quality and is selected first.
        assertEquals(modelEngine.id, candidates.first().id)

        val r = manager.translate("zebra", "en", "es")
        // The coordinator always reports the offline provider id; the key proof
        // is that the model-only word was translated.
        assertEquals("offline", r.providerId)
        assertEquals("cebra", r.translatedText)
        assertTrue(r.isOffline)
    }

    @Test
    fun engineManager_replaceEnginesPicksUpInstalledModel() = runBlocking {
        val manager = TranslationEngineManager.withDefaults(LexiconTranslationEngine())
        // Initially no model-backed engine for es.
        val before = manager.enginesForPair("auto", "es")
        assertFalse(before.any { it.id.startsWith("model:") })

        val file = writeModel("""{"hello":"hola"}""")
        val modelEngine = ModelBackedTranslationEngine(descriptor("en-es", "en", "es"), file)
        manager.replaceEngines(listOf(modelEngine, LexiconTranslationEngine()))

        val after = manager.enginesForPair("auto", "es")
        assertTrue(after.first().id.startsWith("model:"))
    }
}
