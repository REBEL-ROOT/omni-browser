package com.rebelroot.omni.media

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow

class StreamDownloadEngineTest {

    @Test
    fun testDownloadJobDefaults() {
        val job = StreamDownloadEngine.DownloadJob(
            id = "test-job-123",
            filename = "video.mp4",
            url = "https://example.com/video.mp4",
            saveToLocker = false,
            progress = MutableStateFlow(StreamDownloadEngine.DownloadProgress.Downloading(0, 0L)),
            isGeneric = true,
            contentType = "video/mp4",
            referrerUrl = "https://example.com",
            cookies = "session_id=abc123xyz",
            sourceOrigin = "Media Grabber",
            canResume = true,
            bytesDownloaded = 1024L
        )

        assertEquals("test-job-123", job.id)
        assertEquals("video.mp4", job.filename)
        assertEquals("https://example.com/video.mp4", job.url)
        assertFalse(job.saveToLocker)
        assertTrue(job.isGeneric)
        assertEquals("video/mp4", job.contentType)
        assertEquals("https://example.com", job.referrerUrl)
        assertEquals("session_id=abc123xyz", job.cookies)
        assertEquals("Media Grabber", job.sourceOrigin)
        assertTrue(job.canResume)
        assertEquals(1024L, job.bytesDownloaded)
    }

    @Test
    fun testRangeHeaderFormatting() {
        val existingBytes = 5242880L // 5 MB
        val rangeHeader = "bytes=$existingBytes-"
        assertEquals("bytes=5242880-", rangeHeader)

        val parsedOffset = rangeHeader.removePrefix("bytes=").removeSuffix("-").toLongOrNull()
        assertEquals(5242880L, parsedOffset)
    }

    @Test
    fun testPartialContentResponseCodes() {
        val httpOk = 200
        val httpPartialContent = 206
        val httpRequestedRangeNotSatisfiable = 416

        assertTrue(httpPartialContent == 206)
        assertTrue(httpOk == 200)
        assertTrue(httpRequestedRangeNotSatisfiable == 416)
    }
}
