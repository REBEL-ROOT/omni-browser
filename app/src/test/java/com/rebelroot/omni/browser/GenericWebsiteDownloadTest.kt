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
        // Torrents
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://example.com/downloads/ubuntu-24.04.iso.torrent"))
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://academictorrents.com/download/12345.torrent"))
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

    @Test
    fun testMediaFireAndFileHostLandingPagesNotClassifiedAsDownloads() {
        // MediaFire landing pages should NOT be intercepted as raw downloads
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://www.mediafire.com/file/5abc123xyz/MyCoolApp.apk/file"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://www.mediafire.com/file/5abc123xyz/MyCoolApp.apk"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://www.mediafire.com/view/5abc123xyz/MyCoolApp.apk"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://www.mediafire.com/download/5abc123xyz/MyCoolApp.apk"))

        // Google Drive, Mega, Dropbox landing pages
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://drive.google.com/file/d/123456789/view"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://mega.nz/file/12345#abcdef"))
        assertFalse(SecurityPolicy.isGenericDownloadUrl("https://www.dropbox.com/s/12345/app.apk?dl=0"))

        // Direct CDN file downloads from MediaFire subdomains SHOULD be recognized
        assertTrue(SecurityPolicy.isGenericDownloadUrl("https://download1592.mediafire.com/token123/5abc/MyCoolApp.apk"))
    }

    @Test
    fun testGuessDownloadFilenameFromMediaFireUrl() {
        // MediaFire URL with /file at the end should extract the actual APK name
        val filename1 = SecurityPolicy.guessDownloadFilename("https://www.mediafire.com/file/5abc123xyz/MyCoolApp.apk/file", null)
        assertEquals("MyCoolApp.apk", filename1)

        val filename2 = SecurityPolicy.guessDownloadFilename("https://www.mediafire.com/view/5abc123xyz/MyApp-v2.0.apk", null)
        assertEquals("MyApp-v2.0.apk", filename2)

        val filename3 = SecurityPolicy.guessDownloadFilename("https://download1592.mediafire.com/token123/5abc/MyCoolApp.apk", null)
        assertEquals("MyCoolApp.apk", filename3)
    }

    @Test
    fun testGuessDownloadFilenameWithMimeType() {
        // APK MIME type mapping
        val apkFilename = SecurityPolicy.guessDownloadFilename("https://example.com/download/12345", "application/vnd.android.package-archive")
        assertEquals("12345.apk", apkFilename)

        // Fallback without filename segment but with APK MIME type
        val genericApkFilename = SecurityPolicy.guessDownloadFilename("https://example.com/download/", "application/vnd.android.package-archive")
        assertEquals("download.apk", genericApkFilename)

        // ZIP MIME type
        val zipFilename = SecurityPolicy.guessDownloadFilename("https://example.com/api/get-archive", "application/zip")
        assertEquals("get-archive.zip", zipFilename)
    }

    @Test
    fun testParseFilenameFromContentDisposition() {
        // Standard filename
        val name1 = SecurityPolicy.parseFilenameFromContentDisposition("""attachment; filename="MyCoolApp.apk"""")
        assertEquals("MyCoolApp.apk", name1)

        // Unquoted filename
        val name2 = SecurityPolicy.parseFilenameFromContentDisposition("attachment; filename=MyCoolApp.apk")
        assertEquals("MyCoolApp.apk", name2)

        // RFC 5987 / RFC 6266 filename* prioritizing over fallback filename
        val name3 = SecurityPolicy.parseFilenameFromContentDisposition(
            """attachment; filename="fallback.bin"; filename*=UTF-8''RealApp.apk"""
        )
        assertEquals("RealApp.apk", name3)

        // RFC 5987 with URL percent encoding and language tag
        val name4 = SecurityPolicy.parseFilenameFromContentDisposition(
            "attachment; filename*=UTF-8'en'My%20Cool%20App%20v1.0.apk"
        )
        assertEquals("My Cool App v1.0.apk", name4)
    }
}
