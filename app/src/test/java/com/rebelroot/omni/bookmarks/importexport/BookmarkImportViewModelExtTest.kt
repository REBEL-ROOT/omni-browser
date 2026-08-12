/*
 * Omni Browser - Bookmark Import ViewModel Extension Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 05: Tests for the import preview helper functions.
 */

package com.rebelroot.omni.bookmarks.importexport

import com.rebelroot.omni.bookmarks.model.*
import org.junit.Assert.*
import org.junit.Test

class BookmarkImportViewModelExtTest {

    @Test
    fun `flattenTreeForPreview produces correct depth ordering`() {
        val collection = BookmarkCollection()
        val f1 = collection.addFolder("Folder 1")
        val f2 = collection.addFolder("Folder 2", parentId = f1.id)
        collection.addBookmark("B1", "https://b1.com", parentId = f2.id)
        collection.addBookmark("B2", "https://b2.com", parentId = ROOT_FOLDER_ID)

        val tree = collection.buildTree()
        val flat = flattenTreeForPreview(tree)

        // Root at depth 0, then children interleaved by position
        assertEquals(0, flat[0].first) // root
        assertTrue(flat[0].second is BookmarkNode.Folder)

        // Find items by title
        val byTitle = flat.map { entry ->
            val title = (entry.second as? BookmarkNode.Item)?.title
                ?: (entry.second as BookmarkNode.Folder).title
            entry.first to title
        }

        val titles = byTitle.map { entry -> entry.second }
        assertTrue("Folder 1" in titles)
        assertTrue("Folder 2" in titles)
        assertTrue("B1" in titles)
        assertTrue("B2" in titles)

        // Check depths
        val folder1Depth = flat.find { (_, node) -> node is BookmarkNode.Folder && node.title == "Folder 1" }!!.first
        val folder2Depth = flat.find { (_, node) -> node is BookmarkNode.Folder && node.title == "Folder 2" }!!.first
        val b1Depth = flat.find { (_, node) -> node is BookmarkNode.Item && node.title == "B1" }!!.first

        assertEquals(1, folder1Depth)
        assertEquals(2, folder2Depth)
        assertEquals(3, b1Depth)
    }

    @Test
    fun `flattenTreeForPreview handles empty tree`() {
        val collection = BookmarkCollection()
        val tree = collection.buildTree()
        val flat = flattenTreeForPreview(tree)

        assertEquals(1, flat.size)
        assertEquals(0, flat[0].first)
        assertTrue(flat[0].second is BookmarkNode.Folder)
    }

    @Test
    fun `ImportPreviewState detects fatal issues`() {
        val collection = BookmarkCollection()
        // Create a cycle manually (this would be caught by validate)
        val folder = collection.addFolder("Cycle")
        // We can't easily create a cycle through the public API,
        // so we test the hasFatalIssues flag directly.

        val preview = ImportPreviewState(
            sourceCollection = collection,
            tree = collection.buildTree(),
            totalBookmarks = 0,
            totalFolders = 1,
            duplicateCount = 0,
            warnings = emptyList(),
            validationIssues = listOf("PARENT_CYCLE: folder is its own ancestor")
        )

        assertTrue(preview.hasFatalIssues)
        assertFalse(preview.hasWarnings)
    }

    @Test
    fun `ImportPreviewState detects warnings`() {
        val collection = BookmarkCollection()
        val preview = ImportPreviewState(
            sourceCollection = collection,
            tree = collection.buildTree(),
            totalBookmarks = 0,
            totalFolders = 0,
            duplicateCount = 0,
            warnings = listOf("Line 5: Skipped invalid URL"),
            validationIssues = emptyList()
        )

        assertFalse(preview.hasFatalIssues)
        assertTrue(preview.hasWarnings)
    }
}
