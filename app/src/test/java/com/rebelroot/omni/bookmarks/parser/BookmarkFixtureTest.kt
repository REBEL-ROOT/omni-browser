/*
 * Omni Browser - Bookmark Fixture Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 08: Parses real-world browser bookmark exports (Chrome, Firefox,
 * Safari, Edge) and edge-case fixtures. Verifies structural correctness,
 * round-trip fidelity, and edge-case handling.
 */

package com.rebelroot.omni.bookmarks.parser

import com.rebelroot.omni.bookmarks.export.exportNetscapeBookmarkHtml
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.BookmarkNode
import org.junit.Assert.*
import org.junit.Test

class BookmarkFixtureTest {

    // ── Chrome fixture ───────────────────────────────────────────────────────

    @Test
    fun `parse chrome bookmarks fixture`() {
        val html = loadFixture("chrome_bookmarks.html")
        val result = parseNetscapeBookmarkHtml(html)

        assertEquals(0, result.warnings.size)
        assertEquals(8, result.importedBookmarks)
        assertEquals(3, result.importedFolders)

        val tree = result.collection.buildTree()
        val rootChildren = tree.children
        assertEquals(3, rootChildren.size) // Bookmarks Bar, Dev Tools, Hacker News

        // Bookmarks Bar has 3 bookmarks
        val bookmarksBar = rootChildren[0] as BookmarkNode.Folder
        assertEquals("Bookmarks Bar", bookmarksBar.title)
        assertEquals(3, bookmarksBar.children.size)

        // Dev Tools has MDN, Frontend folder, Kotlin
        val devTools = rootChildren[1] as BookmarkNode.Folder
        assertEquals("Dev Tools", devTools.title)
        assertEquals(3, devTools.children.size)

        // Frontend subfolder
        val frontend = devTools.children[1] as BookmarkNode.Folder
        assertEquals("Frontend", frontend.title)
        assertEquals(2, frontend.children.size)
    }

    @Test
    fun `chrome fixture round trip preserves structure`() {
        val html = loadFixture("chrome_bookmarks.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        assertTreesStructurallyEqual(parsed.collection.buildTree(), reparsed.collection.buildTree())
    }

    // ── Firefox fixture ──────────────────────────────────────────────────────

    @Test
    fun `parse firefox bookmarks fixture`() {
        val html = loadFixture("firefox_bookmarks.html")
        val result = parseNetscapeBookmarkHtml(html)

        assertEquals(0, result.warnings.size)
        assertEquals(5, result.importedBookmarks)
        assertEquals(3, result.importedFolders)

        val tree = result.collection.buildTree()
        val rootChildren = tree.children
        assertEquals(3, rootChildren.size) // Bookmarks Toolbar, Other Bookmarks, Reddit
    }

    @Test
    fun `firefox fixture round trip preserves structure`() {
        val html = loadFixture("firefox_bookmarks.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        assertTreesStructurallyEqual(parsed.collection.buildTree(), reparsed.collection.buildTree())
    }

    // ── Safari fixture ───────────────────────────────────────────────────────

    @Test
    fun `parse safari bookmarks fixture`() {
        val html = loadFixture("safari_bookmarks.html")
        val result = parseNetscapeBookmarkHtml(html)

        assertEquals(0, result.warnings.size)
        assertEquals(4, result.importedBookmarks)
        assertEquals(2, result.importedFolders)

        val tree = result.collection.buildTree()
        assertEquals(3, tree.children.size)
    }

    @Test
    fun `safari fixture round trip preserves structure`() {
        val html = loadFixture("safari_bookmarks.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        assertTreesStructurallyEqual(parsed.collection.buildTree(), reparsed.collection.buildTree())
    }

    // ── Edge fixture ─────────────────────────────────────────────────────────

    @Test
    fun `parse edge bookmarks fixture`() {
        val html = loadFixture("edge_bookmarks.html")
        val result = parseNetscapeBookmarkHtml(html)

        assertEquals(0, result.warnings.size)
        assertEquals(7, result.importedBookmarks)
        assertEquals(3, result.importedFolders)

        val tree = result.collection.buildTree()
        assertEquals(3, tree.children.size)
    }

    @Test
    fun `edge fixture round trip preserves structure`() {
        val html = loadFixture("edge_bookmarks.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        assertTreesStructurallyEqual(parsed.collection.buildTree(), reparsed.collection.buildTree())
    }

    // ── Edge cases fixture ─────────────────────────────────────────────────

    @Test
    fun `parse edge cases fixture handles empty titles`() {
        val html = loadFixture("edge_cases.html")
        val result = parseNetscapeBookmarkHtml(html)

        // Empty folder title should parse
        val folders = result.collection.allFolders()
        assertTrue("Should have empty-titled folder", folders.any { it.title.isEmpty() })

        // Empty bookmark title should use URL as fallback
        val bookmarks = result.collection.allBookmarks()
        val emptyTitle = bookmarks.find { it.title == "https://example.com/" }
        assertNotNull("Empty title bookmark should fallback to URL", emptyTitle)
    }

    @Test
    fun `parse edge cases fixture decodes HTML entities`() {
        val html = loadFixture("edge_cases.html")
        val result = parseNetscapeBookmarkHtml(html)

        val bookmarks = result.collection.allBookmarks().associateBy { it.url }

        // URLs stay encoded (browsers export them that way), titles get decoded
        assertEquals("A & B", bookmarks["https://example.com/a&amp;b"]?.title)
        assertEquals("5 < 10 > 3", bookmarks["https://example.com/less-than"]?.title)
        assertEquals("\"Quoted\" 'Single'", bookmarks["https://example.com/quotes"]?.title)
    }

    @Test
    fun `parse edge cases fixture handles unicode`() {
        val html = loadFixture("edge_cases.html")
        val result = parseNetscapeBookmarkHtml(html)

        val bookmarks = result.collection.allBookmarks().associateBy { it.url }
        assertEquals("日本語 中文 🚀 émojis", bookmarks["https://example.com/unicode"]?.title)
    }

    @Test
    fun `parse edge cases fixture handles long URLs`() {
        val html = loadFixture("edge_cases.html")
        val result = parseNetscapeBookmarkHtml(html)

        val bookmarks = result.collection.allBookmarks()
        val longUrl = bookmarks.find { it.title == "Long URL" }
        assertNotNull("Long URL bookmark should be parsed", longUrl)
        assertTrue("URL should be long", longUrl!!.url.length > 200)
    }

    @Test
    fun `parse edge cases fixture skips dangerous URLs`() {
        val html = loadFixture("edge_cases.html")
        val result = parseNetscapeBookmarkHtml(html)

        val urls = result.collection.allBookmarks().map { it.url }
        assertFalse("javascript: should be skipped", urls.any { it.startsWith("javascript:") })
        assertFalse("data: should be skipped", urls.any { it.startsWith("data:") })

        // Should have skipped entries for dangerous URLs
        assertTrue("Should have skipped dangerous URLs", result.skippedEntries > 0)
    }

    @Test
    fun `edge cases fixture round trip preserves special characters`() {
        val html = loadFixture("edge_cases.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        val original = parsed.collection.allBookmarks().associateBy { it.url }
        val roundTrip = reparsed.collection.allBookmarks().associateBy { it.url }

        // Compare titles for bookmarks that survived the round trip
        // (dangerous URLs are skipped during initial parse, so they won't be present)
        original.forEach { (url, bookmark) ->
            val rt = roundTrip[url]
            assertNotNull("Round-trip should preserve $url", rt)
            assertEquals("Title mismatch for $url", bookmark.title, rt!!.title)
        }
    }

    @Test
    fun `edge cases fixture round trip preserves empty folder title`() {
        val html = loadFixture("edge_cases.html")
        val parsed = parseNetscapeBookmarkHtml(html)
        val exported = exportNetscapeBookmarkHtml(parsed.collection, "Bookmarks")
        val reparsed = parseNetscapeBookmarkHtml(exported)

        val originalEmpty = parsed.collection.allFolders().any { it.title.isEmpty() }
        val roundTripEmpty = reparsed.collection.allFolders().any { it.title.isEmpty() }
        assertEquals("Empty folder title should survive round trip", originalEmpty, roundTripEmpty)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("bookmark_fixtures/$name")
            ?: throw IllegalStateException("Fixture not found: $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun assertTreesStructurallyEqual(
        expected: BookmarkNode,
        actual: BookmarkNode
    ) {
        when {
            expected is BookmarkNode.Folder && actual is BookmarkNode.Folder -> {
                assertEquals(
                    "Folder title mismatch",
                    expected.title,
                    actual.title
                )
                assertEquals(
                    "Child count mismatch for folder '${expected.title}'",
                    expected.children.size,
                    actual.children.size
                )
                expected.children.zip(actual.children).forEach { (e, a) ->
                    assertTreesStructurallyEqual(e, a)
                }
            }
            expected is BookmarkNode.Item && actual is BookmarkNode.Item -> {
                assertEquals(
                    "Bookmark title mismatch",
                    expected.title,
                    actual.title
                )
                assertEquals(
                    "Bookmark URL mismatch for '${expected.title}'",
                    expected.url,
                    actual.url
                )
            }
            else -> fail("Node type mismatch: expected ${expected::class}, got ${actual::class}")
        }
    }
}
