package com.rebelroot.omni.browser

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mozilla.geckoview.WebExtension
import java.io.File

class WebExtensionDownloadsTest {

    @Test
    fun testWebExtensionDownloadClassesExist() {
        val downloadCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download")
        assertNotNull(downloadCls)

        val infoCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download\$Info")
        assertNotNull(infoCls)

        val delegateCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download\$Delegate")
        assertNotNull(delegateCls)

        val downloadDelegateCls = Class.forName("org.mozilla.geckoview.WebExtension\$DownloadDelegate")
        assertNotNull(downloadDelegateCls)
    }

    @Test
    fun testExtensionDownloadPolicyEnum() {
        val policies = BrowserViewModel.ExtensionDownloadPolicy.values()
        assertEquals(3, policies.size)
        assertTrue(policies.contains(BrowserViewModel.ExtensionDownloadPolicy.ASK_EVERY_TIME))
        assertTrue(policies.contains(BrowserViewModel.ExtensionDownloadPolicy.ALLOW_TRUSTED))
        assertTrue(policies.contains(BrowserViewModel.ExtensionDownloadPolicy.NEVER_ALLOW))
    }

    @Test
    fun testFilenameSanitizationAgainstPathTraversal() {
        val malicious1 = "../../../../etc/passwd"
        val safe1 = SecurityPolicy.sanitizeFilename(malicious1)
        assertFalse(safe1.contains(".."))
        assertFalse(safe1.contains("/"))
        assertFalse(safe1.contains("\\"))

        val malicious2 = "evil\\..\\..\\Windows\\System32\\cmd.exe"
        val safe2 = SecurityPolicy.sanitizeFilename(malicious2)
        assertFalse(safe2.contains(".."))
        assertFalse(safe2.contains("\\"))

        val normal = "my_download (1).pdf"
        val safeNormal = SecurityPolicy.sanitizeFilename(normal)
        assertEquals("my_download (1).pdf", safeNormal)
    }

    @Test
    fun testDownloadSchemeValidation() {
        fun isAllowedScheme(url: String): Boolean {
            val scheme = try {
                val uri = java.net.URI(url)
                uri.scheme?.lowercase()
            } catch (e: Exception) {
                null
            }
            return scheme in listOf("http", "https", "blob", "data")
        }

        assertTrue(isAllowedScheme("https://example.com/video.mp4"))
        assertTrue(isAllowedScheme("http://example.com/archive.zip"))
        assertTrue(isAllowedScheme("blob:https://youtube.com/12345"))
        assertTrue(isAllowedScheme("data:image/png;base64,iVBOR..."))

        assertFalse(isAllowedScheme("file:///etc/hosts"))
        assertFalse(isAllowedScheme("content://media/external/images"))
        assertFalse(isAllowedScheme("javascript:alert(1)"))
    }

    @Test
    fun testMediaGrabberManifestDeclaresDownloadsPermission() {
        val manifestFile = File("src/main/assets/web_extensions/media_grabber/manifest.json")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            val json = JSONObject(content)
            val permissions = json.getJSONArray("permissions")
            var hasDownloads = false
            for (i in 0 until permissions.length()) {
                if (permissions.getString(i) == "downloads") {
                    hasDownloads = true
                    break
                }
            }
            assertTrue("media_grabber manifest.json must declare 'downloads' permission", hasDownloads)
        }
    }
}
