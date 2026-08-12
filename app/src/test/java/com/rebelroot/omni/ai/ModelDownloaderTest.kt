/*
 * Omni Browser - Offline AI model downloader tests
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

class ModelDownloaderTest {

    private var server: LocalModelServer? = null

    @After
    fun teardown() {
        server?.stop()
        server = null
    }

    private fun descriptorFor(url: String, size: Long): ModelDescriptor = ModelDescriptor(
        id = "dl", version = "1.0", task = ModelTask.ASR,
        name = "DL", sourceLanguage = "en", targetLanguage = null,
        sizeBytes = size, downloadUrl = url, sha256 = null,
        license = "Apache-2.0", sourceProject = "Test"
    )

    @Test
    fun download_fullSucceedsAndMatches() = runBlocking {
        val data = ByteArray(20_000) { (it % 256).toByte() }
        server = LocalModelServer(data)
        val port = server!!.start()
        val desc = descriptorFor("http://127.0.0.1:$port/model.bin", data.size.toLong())
        val partial = File.createTempFile("part", "").also { it.delete() }

        val outcome = ModelDownloader(requireHttps = false)
            .download(desc, partial, allowHosts = null)
        assertTrue(outcome is ModelDownloader.DownloadOutcome.Success)
        assertArrayEquals(data, partial.readBytes())
    }

    @Test
    fun download_resumesFromPartialViaRange() = runBlocking {
        val data = ByteArray(20_000) { (it % 256).toByte() }
        server = LocalModelServer(data)
        val port = server!!.start()
        val desc = descriptorFor("http://127.0.0.1:$port/model.bin", data.size.toLong())
        val partial = File.createTempFile("part", "").also { it.delete() }

        // Simulate a previous, interrupted download that left the first half.
        val half = data.size / 2
        partial.writeBytes(data.copyOfRange(0, half))
        assertEquals(half.toLong(), partial.length())

        val outcome = ModelDownloader(requireHttps = false)
            .download(desc, partial, allowHosts = null)
        assertTrue(outcome is ModelDownloader.DownloadOutcome.Success)
        val srv = server!!
        assertEquals("server should have received a Range request", true, srv.lastRange?.startsWith("bytes=") == true)
        assertArrayEquals(data, partial.readBytes())
    }

    @Test
    fun download_refusesNonHttpsInProduction() = runBlocking {
        val desc = descriptorFor("http://127.0.0.1:1/model.bin", 10)
        val partial = File.createTempFile("part", "").also { it.delete() }
        val outcome = ModelDownloader(requireHttps = true)
            .download(desc, partial, allowHosts = null)
        assertTrue(outcome is ModelDownloader.DownloadOutcome.Failed)
        assertFalse(partial.exists())
    }

    @Test
    fun download_refusesDisallowedHost() = runBlocking {
        val desc = descriptorFor("https://evil.example.com/m.bin", 10)
        val partial = File.createTempFile("part", "").also { it.delete() }
        val outcome = ModelDownloader(requireHttps = true)
            .download(desc, partial, allowHosts = setOf("good.com"))
        assertTrue(outcome is ModelDownloader.DownloadOutcome.Failed)
        assertFalse(partial.exists())
    }

    @Test
    fun download_cancelsCleanly() = runBlocking {
        val data = ByteArray(20_000) { 1 }
        server = LocalModelServer(data)
        val port = server!!.start()
        val desc = descriptorFor("http://127.0.0.1:$port/model.bin", data.size.toLong())
        val partial = File.createTempFile("part", "").also { it.delete() }
        val outcome = ModelDownloader(requireHttps = false)
            .download(desc, partial, allowHosts = null, isCancelled = { true })
        assertTrue(outcome is ModelDownloader.DownloadOutcome.Cancelled)
    }
}
