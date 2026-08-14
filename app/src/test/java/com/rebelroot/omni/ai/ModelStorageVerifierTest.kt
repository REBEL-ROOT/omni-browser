/*
 * Omni Browser - Offline AI model storage & verifier tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.models

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelStorageVerifierTest {

    private lateinit var dir: File
    private lateinit var storage: ModelStorage
    private lateinit var verifier: ModelVerifier

    private val desc = ModelDescriptor(
        id = "test-model", version = "1.0", task = ModelTask.TRANSLATION,
        name = "Test", sourceLanguage = "en", targetLanguage = "es",
        sizeBytes = 0, downloadUrl = "https://example.com/m.bin",
        sha256 = null, license = "Apache-2.0", sourceProject = "Test"
    )

    @Before
    fun setup() {
        dir = File.createTempFile("omni-models", "").also { it.delete(); it.mkdirs() }
        storage = ModelStorage(dir)
        verifier = ModelVerifier()
    }

    @After
    fun teardown() {
        dir.deleteRecursively()
    }

    @Test
    fun storage_commitPromotesPartialAtomically() {
        val partial = storage.partialFile(desc)
        partial.writeBytes("MODEL-DATA".toByteArray())
        assertTrue(storage.hasPartial(desc))

        val ok = storage.commit(desc)
        assertTrue(ok)
        assertTrue(storage.isInstalled(desc))
        assertFalse(partial.exists())
        assertEquals("MODEL-DATA", storage.finalFile(desc).readText())
        assertTrue(storage.listInstalled().any { it.id == "test-model" })
    }

    @Test
    fun storage_deleteRemovesInstalledModel() {
        storage.partialFile(desc).writeBytes("x".toByteArray())
        storage.commit(desc)
        assertTrue(storage.isInstalled(desc))
        assertTrue(storage.delete(desc))
        assertFalse(storage.isInstalled(desc))
        assertTrue(storage.listInstalled().none { it.id == "test-model" })
    }

    @Test
    fun verifier_sha256Matches() {
        val f = File(dir, "v.bin").also { it.writeBytes("hello".toByteArray()) }
        val sha = verifier.sha256Of(f)
        val d = desc.copy(sizeBytes = 5, sha256 = sha)
        assertTrue(verifier.verify(f, d) is VerificationResult.Verified)
    }

    @Test
    fun verifier_sha256MismatchFails() {
        val f = File(dir, "v.bin").also { it.writeBytes("hello".toByteArray()) }
        val d = desc.copy(sizeBytes = 5, sha256 = "deadbeef")
        val r = verifier.verify(f, d)
        assertTrue(r is VerificationResult.Failed)
    }

    @Test
    fun verifier_sizeMismatchUnverifiedWhenNoHashPinned() {
        // When no SHA-256 is pinned, a size mismatch is warned (Unverified), not
        // rejected — see ModelVerifier: size is advisory so upstream drift never
        // blocks an unverifiable model.
        val f = File(dir, "v.bin").also { it.writeBytes("hello".toByteArray()) }
        val d = desc.copy(sizeBytes = 999, sha256 = null)
        val r = verifier.verify(f, d)
        assertTrue(r is VerificationResult.Unverified)
    }

    @Test
    fun verifier_unpinnedShaIsUnverifiedButSizeOk() {
        val f = File(dir, "v.bin").also { it.writeBytes("hello".toByteArray()) }
        val d = desc.copy(sizeBytes = 5, sha256 = null)
        assertTrue(verifier.verify(f, d) is VerificationResult.Unverified)
    }
}
