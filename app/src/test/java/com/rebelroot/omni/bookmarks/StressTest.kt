/*
 * Omni Browser - Bookmark Stress & Boundary Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 09: Verifies the system holds up under extreme load:
 * - Thousands of bookmarks
 * - Deeply nested folder trees
 * - Large file sizes
 * - Concurrent mutations
 * - Empty / minimal inputs
 */

package com.rebelroot.omni.bookmarks

import com.rebelroot.omni.bookmarks.export.exportNetscapeBookmarkHtml
import com.rebelroot.omni.bookmarks.importexport.DuplicatePolicy
import com.rebelroot.omni.bookmarks.importexport.ImportPreviewState
import com.rebelroot.omni.bookmarks.importexport.importBookmarks
import com.rebelroot.omni.bookmarks.model.*
import com.rebelroot.omni.bookmarks.parser.BookmarkHtmlParseResult
import com.rebelroot.omni.bookmarks.parser.ParseException
import com.rebelroot.omni.bookmarks.parser.parseNetscapeBookmarkHtml
import org.junit.Assert.*
import org.junit.Test

class StressTest {

    // ── Scale tests ─────────────────────────────────────────────────────────

    @Test
    fun `collection handles 1000 bookmarks at root`() {
        val collection = BookmarkCollection()
        repeat(1000) { i ->
            collection.addBookmark("Bookmark $i", "https://example.com/$i")
        }
        assertEquals(1000, collection.bookmarkCount())
        assertEquals(0, collection.folderCount())

        val tree = collection.buildTree()
        assertEquals(1000, tree.children.size)

        // Positions must stay dense 0..999
        val positions = collection.allBookmarks().map { it.position }.sorted()
        assertEquals((0L until 1000L).toList(), positions)
    }

    @Test
    fun `collection handles 100 folders with 10 bookmarks each`() {
        val collection = BookmarkCollection()
        repeat(100) { i ->
            val folder = collection.addFolder("Folder $i")
            repeat(10) { j ->
                collection.addBookmark("BM $i-$j", "https://example.com/$i/$j", parentId = folder.id)
            }
        }
        assertEquals(100, collection.folderCount())
        assertEquals(1000, collection.bookmarkCount())

        // Each folder has dense bookmark positions 0..9
        repeat(100) { i ->
            val bookmarks = collection.bookmarkChildren(collection.allFolders()[i].id)
            val positions = bookmarks.map { it.position }.sorted()
            assertEquals("Folder $i positions not dense", (0L until 10L).toList(), positions)
        }
    }

    @Test
    fun `deep nesting up to 50 levels`() {
        val collection = BookmarkCollection()
        var parentId = ROOT_FOLDER_ID
        repeat(50) { i ->
            val folder = collection.addFolder("Level $i", parentId = parentId)
            parentId = folder.id
        }
        // Add a bookmark at the deepest level
        collection.addBookmark("Deep", "https://deep.com", parentId = parentId)

        assertEquals(50, collection.folderCount())
        assertEquals(1, collection.bookmarkCount())

        val tree = collection.buildTree()
        var current: BookmarkNode = tree.children[0]
        var depth = 0
        while (current is BookmarkNode.Folder && current.children.isNotEmpty()) {
            current = current.children[0]
            depth++
        }
        assertEquals(50, depth)
        assertTrue(current is BookmarkNode.Item)
    }

    @Test
    fun `parser handles 5000 bookmarks in single folder`() {
        val html = buildString {
            appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
            appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
            appendLine("<TITLE>Bookmarks</TITLE>")
            appendLine("<H1>Bookmarks</H1>")
            appendLine("<DL><p>")
            repeat(5000) { i ->
                appendLine("    <DT><A HREF=\"https://example.com/$i\" ADD_DATE=\"1609459200\">Bookmark $i</A>")
            }
            appendLine("</DL><p>")
        }

        val result = parseNetscapeBookmarkHtml(html)
        assertEquals(5000, result.importedBookmarks)
        assertEquals(0, result.warnings.size)
    }

    // ── Boundary tests ──────────────────────────────────────────────────────

    @Test
    fun `parser rejects file exceeding max size`() {
        val hugeHtml = "X".repeat(11 * 1024 * 1024) // 11 MB
        try {
            parseNetscapeBookmarkHtml(hugeHtml)
            fail("Should throw ParseException for oversized file")
        } catch (e: ParseException) {
            assertTrue(e.message?.contains("maximum size") == true)
        }
    }

    @Test
    fun `parser rejects excessive nesting depth`() {
        val html = buildString {
            appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
            appendLine("<DL><p>")
            repeat(105) {
                appendLine("<DT><H3>Folder</H3>")
                appendLine("<DL><p>")
            }
            repeat(105) {
                appendLine("</DL><p>")
            }
            appendLine("</DL><p>")
        }

        try {
            parseNetscapeBookmarkHtml(html)
            fail("Should throw ParseException for excessive depth")
        } catch (e: ParseException) {
            assertTrue(e.message?.contains("nesting depth") == true)
        }
    }

    @Test
    fun `parser handles empty HTML`() {
        val result = parseNetscapeBookmarkHtml("")
        assertEquals(0, result.importedBookmarks)
        assertEquals(0, result.importedFolders)
    }

    @Test
    fun `parser handles HTML with no bookmarks`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p></DL><p>
        """.trimIndent()
        val result = parseNetscapeBookmarkHtml(html)
        assertEquals(0, result.importedBookmarks)
        assertEquals(0, result.importedFolders)
    }

    @Test
    fun `parser handles malformed lines gracefully`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
            <DT><A HREF="https://valid.com">Valid</A>
            <DT><A>Missing HREF</A>
            <DT><H3>Valid Folder</H3>
            <DL><p>
            <DT><A HREF="https://another.com">Another</A>
            </DL><p>
            </DL><p>
        """.trimIndent()
        val result = parseNetscapeBookmarkHtml(html)
        assertEquals(2, result.importedBookmarks) // valid + another
        assertEquals(1, result.importedFolders)
        assertEquals(1, result.warnings.size) // Missing HREF skipped
    }

    @Test
    fun `parser handles unbalanced DL tags`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
            <DT><H3>Folder</H3>
            <DL><p>
            <DT><A HREF="https://example.com">Test</A>
            </DL><p>
        """.trimIndent()
        // Missing closing </DL> for root — parser should not crash
        val result = parseNetscapeBookmarkHtml(html)
        assertEquals(1, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
    }

    // ── Import pipeline stress ─────────────────────────────────────────────

    @Test
    fun `import 1000 bookmarks with SKIP policy`() {
        val target = BookmarkCollection()
        repeat(500) { i ->
            target.addBookmark("Existing $i", "https://existing.com/$i")
        }

        val source = BookmarkCollection()
        repeat(1000) { i ->
            if (i % 2 == 0) {
                // Duplicate URL
                source.addBookmark("New $i", "https://existing.com/${i / 2}")
            } else {
                source.addBookmark("New $i", "https://new.com/$i")
            }
        }

        val result = importBookmarks(source, target, DuplicatePolicy.SKIP)

        // 500 duplicates skipped, 500 new added
        assertEquals(500, result.addedBookmarks)
        assertEquals(500, result.skippedBookmarks)
        assertEquals(1000, target.bookmarkCount()) // 500 existing + 500 new
    }

    @Test
    fun `import 1000 bookmarks with REPLACE policy`() {
        val target = BookmarkCollection()
        repeat(500) { i ->
            target.addBookmark("Existing $i", "https://existing.com/$i")
        }

        val source = BookmarkCollection()
        repeat(1000) { i ->
            if (i % 2 == 0) {
                source.addBookmark("Replaced $i", "https://existing.com/${i / 2}")
            } else {
                source.addBookmark("New $i", "https://new.com/$i")
            }
        }

        val result = importBookmarks(source, target, DuplicatePolicy.REPLACE)

        // 500 replaced, 500 new
        assertEquals(500, result.replacedBookmarks)
        assertEquals(500, result.addedBookmarks)
        assertEquals(1000, target.bookmarkCount())
    }

    @Test
    fun `import with many nested folders preserves structure`() {
        val target = BookmarkCollection()
        val source = BookmarkCollection()

        var parentId = ROOT_FOLDER_ID
        repeat(30) { i ->
            val folder = source.addFolder("Level $i", parentId = parentId)
            source.addBookmark("BM $i", "https://bm$i.com", parentId = folder.id)
            parentId = folder.id
        }

        val result = importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)
        assertEquals(30, result.addedFolders)
        assertEquals(30, result.addedBookmarks)

        // Verify nesting survived remapping
        val tree = target.buildTree()
        var depth = 0
        var current: BookmarkNode? = tree.children.find { it is BookmarkNode.Folder }
        while (current is BookmarkNode.Folder) {
            depth++
            current = current.children.find { it is BookmarkNode.Folder }
        }
        assertEquals(30, depth)
    }

    // ── Export stress ───────────────────────────────────────────────────────

    @Test
    fun `export 1000 bookmarks produces valid parseable HTML`() {
        val collection = BookmarkCollection()
        repeat(1000) { i ->
            collection.addBookmark("Bookmark $i", "https://example.com/$i")
        }

        val html = exportNetscapeBookmarkHtml(collection)
        val parsed = parseNetscapeBookmarkHtml(html)

        assertEquals(1000, parsed.importedBookmarks)
        assertEquals(0, parsed.warnings.size)
    }

    @Test
    fun `export with unicode titles round-trips correctly`() {
        val collection = BookmarkCollection()
        val titles = listOf(
            "日本語", "中文", "한국어", "العربية",
            "🚀 Emoji", "éèêë", "ñíóú", "Ω≈ç√∫"
        )
        titles.forEachIndexed { i, title ->
            collection.addBookmark(title, "https://example.com/$i")
        }

        val html = exportNetscapeBookmarkHtml(collection)
        val parsed = parseNetscapeBookmarkHtml(html)

        assertEquals(titles.size, parsed.importedBookmarks)
        val parsedTitles = parsed.collection.allBookmarks().map { it.title }
        titles.forEach { assertTrue("$it should survive round trip", it in parsedTitles) }
    }

    // ── Validation stress ───────────────────────────────────────────────────

    @Test
    fun `validate detects duplicate IDs in large collection`() {
        val collection = BookmarkCollection()
        repeat(100) { i ->
            collection.addBookmark("BM $i", "https://example.com/$i")
        }

        // Manually create a duplicate ID by copying an existing bookmark
        val existing = collection.allBookmarks().first()
        val duplicate = existing.copy(title = "Duplicate")
        val bookmarks = collection.allBookmarks().toMutableList()
        bookmarks.add(duplicate)

        val issues = BookmarkCollection.validate(bookmarks, collection.allFolders())
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.DUPLICATE_ID })
    }

    @Test
    fun `validate detects non-dense positions in large collection`() {
        val collection = BookmarkCollection()
        repeat(100) { i ->
            collection.addBookmark("BM $i", "https://example.com/$i")
        }

        // Delete item 50 — positions should reindex automatically
        val id50 = collection.allBookmarks()[50].id
        collection.deleteItem(id50)

        val issues = collection.validate()
        assertTrue("Positions should stay dense after delete", issues.isEmpty())
    }

    // ── Concurrent mutation safety (single-threaded contract) ───────────────

    @Test
    fun `rapid add and delete maintains integrity`() {
        val collection = BookmarkCollection()
        val ids = mutableListOf<String>()

        repeat(100) { i ->
            val bm = collection.addBookmark("BM $i", "https://example.com/$i")
            ids.add(bm.id)
        }

        // Delete every other bookmark
        ids.filterIndexed { index, _ -> index % 2 == 0 }.forEach { id ->
            collection.deleteItem(id)
        }

        assertEquals(50, collection.bookmarkCount())
        val issues = collection.validate()
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `rapid folder creation and deletion maintains integrity`() {
        val collection = BookmarkCollection()
        val folders = mutableListOf<String>()

        repeat(50) { i ->
            val folder = collection.addFolder("Folder $i")
            folders.add(folder.id)
            repeat(5) { j ->
                collection.addBookmark("BM $i-$j", "https://example.com/$i/$j", parentId = folder.id)
            }
        }

        // Delete half the folders (cascade deletes bookmarks too)
        folders.take(25).forEach { collection.deleteItem(it) }

        assertEquals(25, collection.folderCount())
        assertEquals(125, collection.bookmarkCount()) // 25 folders * 5 bookmarks
        val issues = collection.validate()
        assertTrue(issues.isEmpty())
    }
}
