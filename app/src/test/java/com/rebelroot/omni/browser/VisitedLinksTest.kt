package com.rebelroot.omni.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisitedLinksTest {

    private fun createDummySession(): org.mozilla.geckoview.GeckoSession {
        return try {
            org.mozilla.geckoview.GeckoSession()
        } catch (_: Throwable) {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null) as sun.misc.Unsafe
            unsafe.allocateInstance(org.mozilla.geckoview.GeckoSession::class.java) as org.mozilla.geckoview.GeckoSession
        }
    }

    private fun createTestViewModel(): BrowserViewModel {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        val vm = unsafe.allocateInstance(BrowserViewModel::class.java) as BrowserViewModel
        
        // Initialize mutableStateListOf and visitedUrlsCache directly
        val histField = BrowserViewModel::class.java.getDeclaredField("historyList")
        histField.isAccessible = true
        histField.set(vm, androidx.compose.runtime.mutableStateListOf<HistoryEntry>())

        val cacheField = BrowserViewModel::class.java.getDeclaredField("visitedUrlsCache")
        cacheField.isAccessible = true
        cacheField.set(vm, java.util.concurrent.ConcurrentHashMap.newKeySet<String>())

        return vm
    }

    @Test
    fun testMarkUrlVisitedInMemory_exactMatch() {
        val vm = createTestViewModel()
        assertFalse(vm.isUrlVisited("https://example.com/test"))

        vm.markUrlVisitedInMemory("https://example.com/test")
        assertTrue(vm.isUrlVisited("https://example.com/test"))
        assertFalse(vm.isUrlVisited("https://example.com/other"))
    }

    @Test
    fun testMarkUrlVisitedInMemory_trailingSlashNormalization() {
        val vm = createTestViewModel()
        // Record with trailing slash
        vm.markUrlVisitedInMemory("https://example.com/dir/")
        // Both with and without slash should be considered visited
        assertTrue(vm.isUrlVisited("https://example.com/dir/"))
        assertTrue(vm.isUrlVisited("https://example.com/dir"))

        // Record without trailing slash
        vm.markUrlVisitedInMemory("https://example.org/path")
        assertTrue(vm.isUrlVisited("https://example.org/path"))
        assertTrue(vm.isUrlVisited("https://example.org/path/"))
    }

    @Test
    fun testMarkUrlVisitedInMemory_hashFragmentNormalization() {
        val vm = createTestViewModel()
        // Record base URL
        vm.markUrlVisitedInMemory("https://example.com/article")
        // Any fragment link to this article should be considered visited
        assertTrue(vm.isUrlVisited("https://example.com/article#section1"))
        assertTrue(vm.isUrlVisited("https://example.com/article#heading-2"))

        // Record a URL with fragment
        vm.markUrlVisitedInMemory("https://example.com/page#intro")
        // Base URL should be recognized as visited
        assertTrue(vm.isUrlVisited("https://example.com/page"))
    }

    @Test
    fun testIsUrlVisited_emptyAndBlank() {
        val vm = createTestViewModel()
        assertFalse(vm.isUrlVisited(""))
        assertFalse(vm.isUrlVisited("   "))
        assertFalse(vm.isUrlVisited("about:blank"))
    }

    @Test
    fun testRebuildVisitedUrlsCache() {
        val vm = createTestViewModel()
        vm.historyList.add(HistoryEntry("One", "https://site1.com", 1000L))
        vm.historyList.add(HistoryEntry("Two", "https://site2.com/doc/", 2000L))

        vm.rebuildVisitedUrlsCache()

        assertTrue(vm.isUrlVisited("https://site1.com"))
        assertTrue(vm.isUrlVisited("https://site1.com/"))
        assertTrue(vm.isUrlVisited("https://site2.com/doc/"))
        assertTrue(vm.isUrlVisited("https://site2.com/doc"))
        assertFalse(vm.isUrlVisited("https://site3.com"))
    }

    @Test
    fun testOverrideCss_containsPurpleVisitedLinks() {
        val overrideCssFile = File("src/main/assets/web_extensions/force_dark/override.css")
        val content = if (overrideCssFile.exists()) {
            overrideCssFile.readText()
        } else {
            File("app/src/main/assets/web_extensions/force_dark/override.css").readText()
        }

        assertTrue("override.css must contain a:visited rule", content.contains("html.omni-force-dark-active a:visited"))
        assertTrue("override.css must style visited links with purple #c084fc", content.contains("#c084fc !important"))
    }
}
