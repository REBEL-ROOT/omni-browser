/*
 * Omni Browser - Netscape Bookmark HTML Parser Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 03 gate: parser correctness, security limits, malformed input handling.
 */

package com.rebelroot.omni.bookmarks.parser

import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import org.junit.Test
import org.junit.Assert.*

class NetscapeBookmarkParserTest {

    private fun parse(html: String) = parseNetscapeBookmarkHtml(html) { 1_000_000L }

    // ═══════════════════════════════════════════════════════════════════════
    // Basic parsing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_emptyInput_producesEmptyCollection() {
        val result = parse("")
        assertEquals(0, result.importedBookmarks)
        assertEquals(0, result.importedFolders)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun parse_singleBookmark() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><A HREF="https://google.com" ADD_DATE="1234567890">Google</A>
</DL><p>
"""
        val result = parse(html)
        assertEquals(1, result.importedBookmarks)
        assertEquals(0, result.importedFolders)
        val bm = result.collection.allBookmarks().first()
        assertEquals("Google", bm.title)
        assertEquals("https://google.com", bm.url)
        assertEquals(ROOT_FOLDER_ID, bm.parentId)
    }

    @Test
    fun parse_multipleBookmarks() {
        val html = """<DL><p>
    <DT><A HREF="https://a.example">A</A>
    <DT><A HREF="https://b.example">B</A>
    <DT><A HREF="https://c.example">C</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(3, result.importedBookmarks)
        assertEquals(listOf("A", "B", "C"), result.collection.allBookmarks().map { it.title })
    }

    @Test
    fun parse_folderWithBookmarks() {
        val html = """<DL><p>
    <DT><H3>Work</H3>
    <DL><p>
        <DT><A HREF="https://docs.example">Docs</A>
        <DT><A HREF="https://sheet.example">Sheet</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(1, result.importedFolders)
        assertEquals(2, result.importedBookmarks)
        val folder = result.collection.allFolders().first()
        assertEquals("Work", folder.title)
        assertEquals(ROOT_FOLDER_ID, folder.parentId)
        assertEquals(2, result.collection.bookmarkChildren(folder.id).size)
    }

    @Test
    fun parse_nestedFolders() {
        val html = """<DL><p>
    <DT><H3>Outer</H3>
    <DL><p>
        <DT><H3>Inner</H3>
        <DL><p>
            <DT><A HREF="https://deep.example">Deep</A>
        </DL><p>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedFolders)
        assertEquals(1, result.importedBookmarks)
        val outer = result.collection.allFolders().first { it.title == "Outer" }
        val inner = result.collection.allFolders().first { it.title == "Inner" }
        assertEquals(outer.id, inner.parentId)
        val bm = result.collection.allBookmarks().first()
        assertEquals(inner.id, bm.parentId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HTML entities
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_htmlEntitiesInTitle() {
        val html = """<DL><p>
    <DT><A HREF="https://example.com">AT&amp;T &lt;test&gt;</A>
</DL><p>"""
        val result = parse(html)
        assertEquals("AT&T <test>", result.collection.allBookmarks().first().title)
    }

    @Test
    fun parse_htmlEntitiesInUrl() {
        val html = """<DL><p>
    <DT><A HREF="https://example.com?q=a&amp;b=c">Test</A>
</DL><p>"""
        val result = parse(html)
        // URLs should NOT have HTML entities decoded — the browser exports them encoded.
        assertEquals("https://example.com?q=a&amp;b=c", result.collection.allBookmarks().first().url)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Security / malformed input
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_javascriptScheme_skippedWithWarning() {
        val html = """<DL><p>
    <DT><A HREF="javascript:alert('xss')">Bad</A>
    <DT><A HREF="https://good.example">Good</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(1, result.importedBookmarks)
        assertEquals(1, result.skippedEntries)
        assertTrue(result.warnings.any { it.message.contains("Skipped") })
    }

    @Test
    fun parse_dataScheme_skipped() {
        val html = """<DL><p>
    <DT><A HREF="data:text/html,&lt;script&gt;alert(1)&lt;/script&gt;">Bad</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(0, result.importedBookmarks)
        assertEquals(1, result.skippedEntries)
    }

    @Test(expected = ParseException::class)
    fun parse_excessiveDepth_throws() {
        val html = buildString {
            append("<DL><p>\n")
            repeat(101) { append("<DT><H3>F</H3>\n<DL><p>\n") }
        }
        parse(html)
    }

    @Test(expected = ParseException::class)
    fun parse_excessiveFileSize_throws() {
        val huge = "A".repeat(11 * 1024 * 1024)
        parse(huge)
    }

    @Test
    fun parse_emptyHref_skipped() {
        val html = """<DL><p>
    <DT><A HREF="">Empty</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(0, result.importedBookmarks)
        assertEquals(1, result.skippedEntries)
    }

    @Test
    fun parse_missingHref_notTreatedAsBookmark() {
        val html = """<DL><p>
    <DT><A>Missing href</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(0, result.importedBookmarks)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_emptyFolder() {
        val html = """<DL><p>
    <DT><H3>Empty</H3>
    <DL><p>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(1, result.importedFolders)
        assertEquals(0, result.importedBookmarks)
    }

    @Test
    fun parse_duplicateUrls_allowed() {
        val html = """<DL><p>
    <DT><A HREF="https://dup.example">First</A>
    <DT><A HREF="https://dup.example">Second</A>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
    }

    @Test
    fun parse_positionsAreDense() {
        val html = """<DL><p>
    <DT><A HREF="https://a.example">A</A>
    <DT><A HREF="https://b.example">B</A>
    <DT><A HREF="https://c.example">C</A>
</DL><p>"""
        val result = parse(html)
        val positions = result.collection.allBookmarks().map { it.position }.sorted()
        assertEquals(listOf(0L, 1L, 2L), positions)
    }

    @Test
    fun parse_mixedFoldersAndBookmarks_orderPreserved() {
        val html = """<DL><p>
    <DT><A HREF="https://a.example">A</A>
    <DT><H3>F1</H3>
    <DL><p>
        <DT><A HREF="https://b.example">B</A>
    </DL><p>
    <DT><A HREF="https://c.example">C</A>
</DL><p>"""
        val result = parse(html)
        val rootIds = result.collection.childIds(ROOT_FOLDER_ID)
        assertEquals(3, rootIds.size) // A, F1, C
        assertEquals("A", result.collection.bookmark(rootIds[0])?.title)
        assertEquals("F1", result.collection.folder(rootIds[1])?.title)
        assertEquals("C", result.collection.bookmark(rootIds[2])?.title)
    }

    @Test
    fun parse_bookmarkWithoutTitle_usesUrlAsTitle() {
        val html = """<DL><p>
    <DT><A HREF="https://example.com"></A>
</DL><p>"""
        val result = parse(html)
        assertEquals("https://example.com", result.collection.allBookmarks().first().title)
    }

    @Test
    fun parse_noBookmarksOrFolders_returnsEmpty() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(0, result.totalEntries)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Real-world format variations
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_chromeStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<!-- This is an automatically generated file.
     It will be read and overwritten.
     DO NOT EDIT! -->
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><A HREF="https://www.google.com/" ADD_DATE="1609459200" ICON="data:image/png;base64,iVBOR...">Google</A>
    <DT><H3 ADD_DATE="1609459200" LAST_MODIFIED="1609459200">Bookmarks Bar</H3>
    <DL><p>
        <DT><A HREF="https://github.com/" ADD_DATE="1609459200">GitHub</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
        assertTrue(result.collection.allBookmarks().any { it.title == "Google" })
        assertTrue(result.collection.allBookmarks().any { it.title == "GitHub" })
    }

    @Test
    fun parse_firefoxStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks Menu</H1>
<DL><p>
    <DT><H3 PERSONAL_TOOLBAR_FOLDER="true">Bookmarks Toolbar</H3>
    <DL><p>
        <DT><A HREF="https://mozilla.org" ADD_DATE="1234567890" LAST_MODIFIED="1234567890">Mozilla</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(1, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
    }

    @Test
    fun parse_safariStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3>Favorites</H3>
    <DL><p>
        <DT><A HREF="https://apple.com">Apple</A>
        <DT><A HREF="https://icloud.com">iCloud</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
    }

    @Test
    fun parse_braveStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><A HREF="https://brave.com" ADD_DATE="1609459200">Brave</A>
    <DT><H3 ADD_DATE="1609459200">Other Bookmarks</H3>
    <DL><p>
        <DT><A HREF="https://duckduckgo.com" ADD_DATE="1609459200">DuckDuckGo</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
    }

    @Test
    fun parse_edgeStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3>Favorites bar</H3>
    <DL><p>
        <DT><A HREF="https://bing.com">Bing</A>
    </DL><p>
    <DT><H3>Other favorites</H3>
    <DL><p>
        <DT><A HREF="https://microsoft.com">Microsoft</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
        assertEquals(2, result.importedFolders)
    }

    @Test
    fun parse_operaStyleExport() {
        val html = """<!DOCTYPE NETSCAPE-Bookmark-file-1>
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><A HREF="https://opera.com">Opera</A>
    <DT><H3>Speed Dial</H3>
    <DL><p>
        <DT><A HREF="https://news.opera.com">News</A>
    </DL><p>
</DL><p>"""
        val result = parse(html)
        assertEquals(2, result.importedBookmarks)
        assertEquals(1, result.importedFolders)
    }
}
