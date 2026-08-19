package com.rebelroot.omni.browser

import org.junit.Assert.*
import org.junit.Test

class GenericWebsiteDownloadTest {

    @Test
    fun testGenericDownloadUrlIdentification() {
        // APK
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://github.com/user/repo/releases/download/v1.0/app-universal-debug.apk"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/downloads/app.apk"))

        // ZIP & Archives
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/files/archive.zip"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://github.com/user/repo/archive/refs/tags/v1.0.tar.gz"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/assets/package.7z"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/downloads/bundle.rar"))

        // Documents
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/docs/manual.pdf"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/files/report.docx"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/files/spreadsheet.xlsx"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/data/export.csv"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/data/schema.json"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/notes.txt"))

        // Media direct file links
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/audio/podcast.mp3"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/video/sample.mp4"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/image/photo.png"))
    }

    @Test
    fun testNormalWebpageNavigationsNotClassifiedAsDownloads() {
        // Normal web pages & articles
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/article"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/blog/my-first-post"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/index.html"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/page.php?id=123"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/search.aspx"))

        // Bare domains
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com/"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.com"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://sub.domain.pk"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://example.org"))

        // Non-http schemes
        assertFalse(SecurityPolicy.isGenericDownloadUrl("about:blank"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("javascript:void(0)"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("data:text/html,<h1>Test</h1>"))
    }

    @Test
    fun testFilenameSanitizationForGenericDownloads() {
        val dangerous1 = "../../../../etc/shadow"
        val safe1 = SecurityPolicy.sanitizeFilename(dangerous1)
        assertFalse(safe1.contains(".."))
        assertFalse(safe1.contains("/"))
        assertFalse(safe1.contains("\\"))

        val dangerous2 = "evil\u0000file.apk"
        val safe2 = SecurityPolicy.sanitizeFilename(dangerous2)
        assertFalse(safe2.contains("\u0000"))

        val validApk = "app-universal-debug (1).apk"
        val safeValid = SecurityPolicy.sanitizeFilename(validApk)
        assertEquals("app-universal-debug (1).apk", safeValid)
    }

    @Test
    fun testPendingGenericDownloadStateModel() {
        val download = BrowserViewModel.PendingGenericDownload(
            url = "https://example.com/file.zip",
            filename = "file.zip",
            contentType = "application/zip"
        )
        assertEquals("https://example.com/file.zip", download.url)
        assertEquals("file.zip", download.filename)
        assertEquals("application/zip", download.contentType)
    }
}
