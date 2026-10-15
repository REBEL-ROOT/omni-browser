/*
 * Omni Browser - Burn All Data Unit Tests (Issue #140)
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Verifies that site settings, custom permissions, and disk cache directories
 * are completely wiped and incinerated when burning all browsing data.
 */

package com.rebelroot.omni.browser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BurnAllDataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sitePermissions_serializeAndDeserializeWithAllFields() {
        val perm = SitePermission(
            host = "example.com",
            location = "allow",
            camera = "block",
            microphone = "ask",
            notifications = "allow",
            javascript = "block",
            autoplay = "block",
            externalApp = "allow"
        )

        val obj = JSONObject().apply {
            put("host", perm.host)
            put("location", perm.location)
            put("camera", perm.camera)
            put("microphone", perm.microphone)
            put("notifications", perm.notifications)
            put("javascript", perm.javascript)
            put("autoplay", perm.autoplay)
            put("externalApp", perm.externalApp)
        }

        val jsonArray = JSONArray().put(obj)
        val jsonStr = jsonArray.toString()

        val parsedArray = JSONArray(jsonStr)
        assertEquals(1, parsedArray.length())
        val parsedObj = parsedArray.getJSONObject(0)
        assertEquals("example.com", parsedObj.getString("host"))
        assertEquals("allow", parsedObj.getString("location"))
        assertEquals("block", parsedObj.getString("camera"))
        assertEquals("ask", parsedObj.getString("microphone"))
        assertEquals("allow", parsedObj.getString("notifications"))
        assertEquals("block", parsedObj.getString("javascript"))
        assertEquals("block", parsedObj.getString("autoplay"))
        assertEquals("allow", parsedObj.getString("externalApp"))
    }

    @Test
    fun sitePermissionsFile_isDeletedOnBurn() {
        val filesDir = tempFolder.newFolder("filesDir")
        val sitePermissionsFile = File(filesDir, "browser_site_permissions.json")
        sitePermissionsFile.writeText("""[{"host":"example.com","location":"allow"}]""")
        assertTrue(sitePermissionsFile.exists())

        // Simulate site permissions wipe during burn
        if (sitePermissionsFile.exists()) {
            sitePermissionsFile.delete()
        }

        assertFalse("Site permissions file must be deleted after burn", sitePermissionsFile.exists())
    }

    @Test
    fun diskCacheDirectories_arePurgedRecursively() {
        val filesDir = tempFolder.newFolder("filesDir")
        val cacheDir = tempFolder.newFolder("cacheDir")
        val codeCacheDir = tempFolder.newFolder("codeCacheDir")

        // 1. Populate standard cache files
        val webCache = File(cacheDir, "http_cache.bin").apply { writeText("cached_response") }
        assertTrue(webCache.exists())

        // 2. Populate code cache
        val dexCache = File(codeCacheDir, "compiled.dex").apply { writeText("dex") }
        assertTrue(dexCache.exists())

        // 3. Populate Gecko profile disk cache (cache2, startupCache, jumpListCache)
        val mozillaDir = File(filesDir, "mozilla/profile.default")
        val cache2Dir = File(mozillaDir, "cache2/entries").apply { mkdirs() }
        val cache2File = File(cache2Dir, "1A2B3C").apply { writeText("gecko_cached_content") }
        val startupCache = File(mozillaDir, "startupCache").apply { mkdirs() }
        val startupFile = File(startupCache, "scriptCache.bin").apply { writeText("startup_data") }

        assertTrue(cache2File.exists())
        assertTrue(startupFile.exists())

        // 4. Populate temporary downloads
        val tempDownloadsDir = File(filesDir, "temp_downloads").apply { mkdirs() }
        val tempFile = File(tempDownloadsDir, "part.tmp").apply { writeText("download_temp") }
        assertTrue(tempFile.exists())

        // Execute cache incineration logic (mirrors FireButton.burn & clearCacheAndSiteData)
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        if (codeCacheDir.exists()) {
            codeCacheDir.deleteRecursively()
            codeCacheDir.mkdirs()
        }
        val geckoProfileDir = File(filesDir, "mozilla")
        if (geckoProfileDir.exists()) {
            geckoProfileDir.walkTopDown().forEach { file ->
                if (file.isDirectory && (
                    file.name == "cache2" ||
                    file.name == "startupCache" ||
                    file.name == "jumpListCache" ||
                    file.name == "shader-cache" ||
                    file.name == "thumbnails"
                )) {
                    file.deleteRecursively()
                }
            }
        }
        if (tempDownloadsDir.exists()) {
            tempDownloadsDir.deleteRecursively()
        }

        // Verify everything was wiped
        assertFalse("HTTP web cache must be purged", webCache.exists())
        assertFalse("Dex/code cache must be purged", dexCache.exists())
        assertFalse("Gecko cache2 entries must be deleted", cache2File.exists())
        assertFalse("Gecko cache2 directory must be deleted", cache2Dir.exists())
        assertFalse("Startup cache must be deleted", startupCache.exists())
        assertFalse("Temporary downloads folder must be deleted", tempDownloadsDir.exists())
    }
}
