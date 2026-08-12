/*
 * Omni Browser - Netscape Bookmark HTML Exporter Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 06: Verifies export correctness, entity encoding, determinism,
 * and structural round-trip with the parser.
 */

package com.rebelroot.omni.bookmarks.export

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.parser.parseNetscapeBookmarkHtml
import org.junit.Assert.*
import org.junit.Test

class NetscapeBookmarkExporterTest {

    @Test
    fun `export produces required header tags`() {
        val collection = BookmarkCollection()
        collection.addBookmark("Google", "https://google.com")

        val html = exportNetscapeBookmarkHtml(collection, title = "My Bookmarks")

        assertTrue("DOCTYPE" in html)
        assertTrue("NETSCAPE-Bookmark-file-1" in html)
        assertTrue("<TITLE>My Bookmarks</TITLE>" in html)
        assertTrue("<H1>My Bookmarks</H1>" in html)
        assertTrue("<META HTTP-EQUIV" in html)
        assertTrue("charset=UTF-8" in html)
    }

    @Test
    fun `export empty collection produces minimal structure`() {
        val collection = BookmarkCollection()
        val html = exportNetscapeBookmarkHtml(collection)

        assertTrue("<DL><p>" in html)
        assertTrue("</DL><p>" in html)
        assertFalse("<A HREF" in html)
        assertFalse("<H3" in html)
    }

    @Test
    fun `export includes bookmarks with correct HREF and title`() {
        val collection = BookmarkCollection()
        collection.addBookmark("Google", "https://google.com")
        collection.addBookmark("GitHub", "https://github.com")

        val html = exportNetscapeBookmarkHtml(collection)

        assertTrue("HREF=\"https://google.com\"" in html)
        assertTrue(">Google</A>" in html)
        assertTrue("HREF=\"https://github.com\"" in html)
        assertTrue(">GitHub</A>" in html)
    }

    @Test
    fun `export includes folders with H3 tags`() {
        val collection = BookmarkCollection()
        val folder = collection.addFolder("Dev")
        collection.addBookmark("GitHub", "https://github.com", parentId = folder.id)

        val html = exportNetscapeBookmarkHtml(collection)

        assertTrue("<H3" in html)
        assertTrue(">Dev</H3>" in html)
        // Folder should have a nested DL
        val folderH3Index = html.indexOf(">Dev</H3>")
        val afterH3 = html.substring(folderH3Index)
        assertTrue("<DL><p>" in afterH3)
        assertTrue("HREF=\"https://github.com\"" in afterH3)
    }

    @Test
    fun `export nests folders correctly`() {
        val collection = BookmarkCollection()
        val f1 = collection.addFolder("Level 1")
        val f2 = collection.addFolder("Level 2", parentId = f1.id)
        collection.addBookmark("Deep", "https://deep.com", parentId = f2.id)

        val html = exportNetscapeBookmarkHtml(collection)

        // Level 1 H3 should appear before Level 2 H3
        val level1Index = html.indexOf(">Level 1</H3>")
        val level2Index = html.indexOf(">Level 2</H3>")
        assertTrue("Level 1 should appear before Level 2", level1Index < level2Index)

        // Deep bookmark should be inside Level 2's DL
        val deepIndex = html.indexOf("https://deep.com")
        assertTrue("Deep bookmark should be after Level 2", deepIndex > level2Index)
    }

    @Test
    fun `export encodes HTML entities in titles`() {
        val collection = BookmarkCollection()
        collection.addBookmark("A & B <C>", "https://example.com")

        val html = exportNetscapeBookmarkHtml(collection)

        // Title should be encoded, raw chars should NOT appear
        assertTrue("A &amp; B &lt;C&gt;" in html)
        assertFalse(">A & B <C></A>" in html)
    }

    @Test
    fun `export keeps ampersand raw in URLs`() {
        val collection = BookmarkCollection()
        collection.addBookmark("Test", "https://example.com?a=1&b=2")

        val html = exportNetscapeBookmarkHtml(collection)

        // Ampersand in URLs stays raw (not &amp;) to avoid double-encoding
        // on round-trip. Browsers export hrefs with raw &.
        assertTrue("HREF=\"https://example.com?a=1&b=2\"" in html)
    }

    @Test
    fun `export encodes quotes in titles`() {
        val collection = BookmarkCollection()
        collection.addBookmark("Say \"Hello\"", "https://example.com")

        val html = exportNetscapeBookmarkHtml(collection)

        assertTrue("Say &quot;Hello&quot;" in html)
    }

    @Test
    fun `export converts millis to seconds`() {
        val collection = BookmarkCollection(clock = { 1_700_000_000_000L })
        collection.addBookmark("Test", "https://example.com")

        val html = exportNetscapeBookmarkHtml(collection)

        // 1_700_000_000_000 ms = 1_700_000_000 s
        assertTrue("ADD_DATE=\"1700000000\"" in html)
    }

    @Test
    fun `export is deterministic for same collection`() {
        val collection = BookmarkCollection()
        val f1 = collection.addFolder("A")
        collection.addBookmark("B1", "https://b1.com", parentId = f1.id)
        collection.addBookmark("B2", "https://b2.com", parentId = f1.id)

        val html1 = exportNetscapeBookmarkHtml(collection)
        val html2 = exportNetscapeBookmarkHtml(collection)

        assertEquals(html1, html2)
    }

    @Test
    fun `round trip preserves structure`() {
        val original = BookmarkCollection()
        val work = original.addFolder("Work")
        val personal = original.addFolder("Personal")
        val tools = original.addFolder("Tools", parentId = work.id)

        original.addBookmark("GitHub", "https://github.com", parentId = work.id)
        original.addBookmark("GitLab", "https://gitlab.com", parentId = tools.id)
        original.addBookmark("Reddit", "https://reddit.com", parentId = personal.id)
        original.addBookmark("Root BM", "https://root.com")

        val html = exportNetscapeBookmarkHtml(original)
        val parsed = parseNetscapeBookmarkHtml(html)

        // Build trees and compare shape
        val originalTree = original.buildTree()
        val parsedTree = parsed.collection.buildTree()

        assertTreesStructurallyEqual(originalTree, parsedTree)
    }

    @Test
    fun `round trip preserves empty folders`() {
        val original = BookmarkCollection()
        original.addFolder("Empty Folder")

        val html = exportNetscapeBookmarkHtml(original)
        val parsed = parseNetscapeBookmarkHtml(html)

        assertEquals(1, parsed.importedFolders)
        assertEquals(0, parsed.importedBookmarks)
    }

    @Test
    fun `round trip with special characters in titles`() {
        val original = BookmarkCollection()
        original.addBookmark("Tom & Jerry", "https://example.com/1")
        original.addBookmark("5 < 10 > 3", "https://example.com/2")
        original.addBookmark("\"Quoted\"", "https://example.com/3")

        val html = exportNetscapeBookmarkHtml(original)
        val parsed = parseNetscapeBookmarkHtml(html)

        assertEquals(3, parsed.importedBookmarks)
        val titles = parsed.collection.allBookmarks().map { it.title }.toSet()
        assertTrue("Tom & Jerry" in titles)
        assertTrue("5 < 10 > 3" in titles)
        assertTrue("\"Quoted\"" in titles)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun assertTreesStructurallyEqual(
        expected: com.rebelroot.omni.bookmarks.model.BookmarkNode,
        actual: com.rebelroot.omni.bookmarks.model.BookmarkNode
    ) {
        when {
            expected is com.rebelroot.omni.bookmarks.model.BookmarkNode.Folder &&
                actual is com.rebelroot.omni.bookmarks.model.BookmarkNode.Folder -> {
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
            expected is com.rebelroot.omni.bookmarks.model.BookmarkNode.Item &&
                actual is com.rebelroot.omni.bookmarks.model.BookmarkNode.Item -> {
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
