/*
 * Omni Browser - Offline AI model catalog tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private val json = """
    {
      "allowHosts": ["alphacephei.com", "huggingface.co"],
      "models": [
        {
          "id": "vosk-en", "version": "0.15", "task": "asr", "name": "Vosk EN",
          "sourceLanguage": "en", "targetLanguage": null, "sizeBytes": 50000000,
          "downloadUrl": "https://alphacephei.com/vosk/models/x.zip",
          "sha256": "abc", "license": "Apache-2.0", "sourceProject": "Vosk"
        },
        {
          "id": "bergamot-en-es", "version": "1.0", "task": "translation",
          "name": "Bergamot EN-ES", "sourceLanguage": "en", "targetLanguage": "es",
          "sizeBytes": 100, "downloadUrl": "https://huggingface.co/omni/m.bin",
          "sha256": "def", "license": "MPL-2.0", "sourceProject": "Bergamot"
        },
        {
          "id": "bad-http", "version": "1.0", "task": "translation",
          "name": "Bad", "sourceLanguage": "en", "targetLanguage": "fr",
          "sizeBytes": 10, "downloadUrl": "http://insecure.example.com/m.bin",
          "sha256": null, "license": "Unknown", "sourceProject": "X"
        },
        {
          "id": "bad-host", "version": "1.0", "task": "translation",
          "name": "BadHost", "sourceLanguage": "en", "targetLanguage": "de",
          "sizeBytes": 10, "downloadUrl": "https://evil.example.com/m.bin",
          "sha256": null, "license": "Unknown", "sourceProject": "X"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun catalog_dropsInsecureAndDisallowedHosts() {
        val catalog = ModelCatalog.parse(json)
        val ids = catalog.all().map { it.id }
        assertTrue("vosk-en present", ids.contains("vosk-en"))
        assertTrue("bergamot present", ids.contains("bergamot-en-es"))
        assertFalse("http url dropped", ids.contains("bad-http"))
        assertFalse("disallowed host dropped", ids.contains("bad-host"))
    }

    @Test
    fun catalog_routesByTaskAndLanguage() {
        val catalog = ModelCatalog.parse(json)
        assertEquals(1, catalog.findForTranslation("en", "es").size)
        assertEquals("bergamot-en-es", catalog.findForTranslation("en", "es").first().id)
        assertEquals(1, catalog.findForAsr("en").size)
        assertTrue(catalog.isHostAllowed("alphacephei.com"))
        assertFalse(catalog.isHostAllowed("evil.example.com"))
    }

    @Test
    fun catalog_keepsAssetSchemeTranslationModels() {
        val json = """
        {
          "allowHosts": ["alphacephei.com"],
          "models": [
            {
              "id": "omni-translation-en-es", "version": "1.0", "task": "translation",
              "name": "Omni EN-ES", "sourceLanguage": "en", "targetLanguage": "es",
              "sizeBytes": 884, "downloadUrl": "asset://ai/models/translation_en_es.json",
              "sha256": null, "license": "Omni", "sourceProject": "Omni Lexicon"
            }
          ]
        }
        """.trimIndent()
        val catalog = ModelCatalog.parse(json)
        assertEquals(1, catalog.all().size)
        assertEquals("omni-translation-en-es", catalog.all().first().id)
        assertEquals(1, catalog.findForTranslation("en", "es").size)
    }

    @Test
    fun catalog_parsesDescriptorRoundTrip() {
        val d = ModelDescriptor(
            id = "x", version = "1", task = ModelTask.TRANSLATION, name = "X",
            sourceLanguage = "en", targetLanguage = "es", sizeBytes = 123,
            downloadUrl = "https://huggingface.co/x/m.bin", sha256 = "deadbeef",
            license = "MPL-2.0", sourceProject = "Bergamot"
        )
        val parsed = parseModelDescriptor(d.toJson())
        assertEquals(d.id, parsed.id)
        assertEquals(d.sizeBytes, parsed.sizeBytes)
        assertEquals(d.sha256, parsed.sha256)
        assertEquals(d.task, parsed.task)
    }
}
