/*
 * Omni Browser - Offline AI model repository (end-to-end) tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai.models

import com.rebelroot.omni.ai.LocalModelServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelRepositoryTest {

    private var server: LocalModelServer? = null
    private lateinit var dir: File

    @After
    fun teardown() {
        server?.stop()
        server = null
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun makeDescriptor(url: String, data: ByteArray, version: String, sha: String?): ModelDescriptor {
        val f = File.createTempFile("serve", "").also { it.writeBytes(data); it.deleteOnExit() }
        return ModelDescriptor(
            id = "repo-model", version = version, task = ModelTask.ASR,
            name = "Repo", sourceLanguage = "en", targetLanguage = null,
            sizeBytes = data.size.toLong(), downloadUrl = url, sha256 = sha,
            license = "Apache-2.0", sourceProject = "Test"
        )
    }

    @Test
    fun repository_downloadVerifyInstallEndToEnd() = runBlocking {
        val data = ByteArray(12_000) { (it and 0xFF).toByte() }
        dir = File.createTempFile("repo", "").also { it.delete(); it.mkdirs() }
        val storage = ModelStorage(dir)
        val sha = ModelVerifier().sha256Of(
            File.createTempFile("tmp", "").also { it.writeBytes(data); it.deleteOnExit() }
        )
        server = LocalModelServer(data)
        val port = server!!.start()
        val desc = makeDescriptor("http://127.0.0.1:$port/model.bin", data, "1.0", sha)

        val catalog = ModelCatalog.parse(
            """{"models":[${desc.toJson()}]}"""
        )
        val repo = ModelRepository(catalog, storage, ModelDownloader(requireHttps = false))

        val state = repo.install(desc)
        assertEquals(ModelInstallState.INSTALLED, state.status)
        assertTrue(storage.isInstalled(desc))
        assertEquals(data.size.toLong(), storage.installedSize(desc))
        // File content integrity
        assertArrayEquals(data, storage.finalFile(desc).readBytes())
    }

    @Test
    fun repository_rejectsTamperedFile() = runBlocking {
        val data = ByteArray(8_000) { 7 }
        dir = File.createTempFile("repo", "").also { it.delete(); it.mkdirs() }
        val storage = ModelStorage(dir)
        // Pin the WRONG sha so verification must fail.
        val wrongSha = "0".repeat(64)
        server = LocalModelServer(data)
        val port = server!!.start()
        val desc = makeDescriptor("http://127.0.0.1:$port/model.bin", data, "1.0", wrongSha)

        val catalog = ModelCatalog.parse("""{"models":[${desc.toJson()}]}""")
        val repo = ModelRepository(catalog, storage, ModelDownloader(requireHttps = false))

        val state = repo.install(desc)
        assertEquals(ModelInstallState.FAILED, state.status)
        assertFalse("tampered model must NOT be installed", storage.isInstalled(desc))
    }

    @Test
    fun repository_updateKeepsOldUntilNewVerified() = runBlocking {
        val v1 = ByteArray(4_000) { 1 }
        val v2 = ByteArray(4_000) { 2 }
        dir = File.createTempFile("repo", "").also { it.delete(); it.mkdirs() }
        val storage = ModelStorage(dir)
        val shaV1 = ModelVerifier().sha256Of(File.createTempFile("tmp","").also { it.writeBytes(v1); it.deleteOnExit() })
        val shaV2 = ModelVerifier().sha256Of(File.createTempFile("tmp","").also { it.writeBytes(v2); it.deleteOnExit() })

        // Serve v1 first
        server = LocalModelServer(v1)
        var port = server!!.start()
        val d1 = makeDescriptor("http://127.0.0.1:$port/model.bin", v1, "1.0", shaV1)
        var catalog = ModelCatalog.parse("""{"models":[${d1.toJson()}]}""")
        var repo = ModelRepository(catalog, storage, ModelDownloader(requireHttps = false))
        assertEquals(ModelInstallState.INSTALLED, repo.install(d1).status)
        assertEquals(1, repo.installedModels().size)

        // Now serve v2 and install the new version (old server still serves v1,
        // so point the descriptor's URL at a NEW server serving v2).
        server!!.stop()
        server = LocalModelServer(v2)
        port = server!!.start()
        val d2 = makeDescriptor("http://127.0.0.1:$port/model.bin", v2, "2.0", shaV2)
        catalog = ModelCatalog.parse("""{"models":[${d2.toJson()}]}""")
        repo = ModelRepository(catalog, storage, ModelDownloader(requireHttps = false))
        assertEquals(ModelInstallState.INSTALLED, repo.install(d2).status)

        // Only the new version should remain installed (old removed after success).
        val installed = repo.installedModels()
        assertEquals(1, installed.size)
        assertEquals("2.0", installed.first().version)
    }
}
