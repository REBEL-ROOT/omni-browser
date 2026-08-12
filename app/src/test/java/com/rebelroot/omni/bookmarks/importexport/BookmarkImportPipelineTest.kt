/*
 * Omni Browser — Bookmark Import Pipeline Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 04 gate: validation, duplicate policy, transactional merge.
 */

package com.rebelroot.omni.bookmarks.importexport

import com.rebelroot.omni.bookmarks.model.*
import org.junit.Test
import org.junit.Assert.*

class BookmarkImportPipelineTest {

    private fun emptyCollection() = BookmarkCollection { 1_000_000L }

    // ═══════════════════════════════════════════════════════════════════════
    // KEEP_BOTH policy
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_keepBoth_addsAllBookmarksAndFolders() {
        val source = emptyCollection()
        source.addBookmark("A", "https://a.example")
        source.addBookmark("B", "https://b.example")
        val folder = source.addFolder("Work")
        source.addBookmark("C", "https://c.example", parentId = folder.id)

        val target = emptyCollection()
        val result = importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)

        assertEquals(3, result.addedBookmarks)
        assertEquals(1, result.addedFolders)
        assertEquals(0, result.skippedBookmarks)
        assertEquals(3, target.bookmarkCount())
        assertEquals(1, target.folderCount())
    }

    @Test
    fun import_keepBoth_duplicateUrlsBothKept() {
        val source = emptyCollection()
        source.addBookmark("A", "https://dup.example")

        val target = emptyCollection()
        target.addBookmark("B", "https://dup.example")

        val result = importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)
        assertEquals(1, result.addedBookmarks)
        assertEquals(2, target.allBookmarks().count { it.url == "https://dup.example" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SKIP policy
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_skip_duplicateUrlsSkipped() {
        val source = emptyCollection()
        source.addBookmark("A", "https://a.example")
        source.addBookmark("B", "https://dup.example")

        val target = emptyCollection()
        target.addBookmark("C", "https://dup.example")

        val result = importBookmarks(source, target, DuplicatePolicy.SKIP)
        assertEquals(1, result.addedBookmarks)
        assertEquals(1, result.skippedBookmarks)
        assertEquals(2, target.bookmarkCount()) // C + A
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REPLACE policy
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_replace_existingReplaced() {
        val source = emptyCollection()
        source.addBookmark("New Title", "https://dup.example")

        val target = emptyCollection()
        target.addBookmark("Old Title", "https://dup.example")

        val result = importBookmarks(source, target, DuplicatePolicy.REPLACE)
        assertEquals(1, result.replacedBookmarks)
        assertEquals(1, target.bookmarkCount())
        assertEquals("New Title", target.allBookmarks().first().title)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MERGE policy
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_merge_titleUpdatedWhenDifferent() {
        val source = emptyCollection()
        source.addBookmark("Updated", "https://dup.example")

        val target = emptyCollection()
        target.addBookmark("Original", "https://dup.example")

        val result = importBookmarks(source, target, DuplicatePolicy.MERGE)
        assertEquals(1, result.mergedBookmarks)
        assertEquals(1, target.bookmarkCount())
        assertEquals("Updated", target.allBookmarks().first().title)
    }

    @Test
    fun import_merge_sameTitle_skipped() {
        val source = emptyCollection()
        source.addBookmark("Same", "https://dup.example")

        val target = emptyCollection()
        target.addBookmark("Same", "https://dup.example")

        val result = importBookmarks(source, target, DuplicatePolicy.MERGE)
        assertEquals(0, result.mergedBookmarks)
        assertEquals(1, result.skippedBookmarks)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Nested folders
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_nestedFolders_preserved() {
        val source = emptyCollection()
        val outer = source.addFolder("Outer")
        val inner = source.addFolder("Inner", parentId = outer.id)
        source.addBookmark("Deep", "https://deep.example", parentId = inner.id)

        val target = emptyCollection()
        val result = importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)

        assertEquals(2, result.addedFolders)
        assertEquals(1, result.addedBookmarks)
        assertEquals(2, target.folderCount())

        val importedInner = target.allFolders().first { it.title == "Inner" }
        val importedOuter = target.allFolders().first { it.title == "Outer" }
        assertEquals(importedOuter.id, importedInner.parentId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transactional safety
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_emptySource_noChangeToTarget() {
        val source = emptyCollection()
        val target = emptyCollection()
        target.addBookmark("Existing", "https://existing.example")

        importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)
        assertEquals(1, target.bookmarkCount())
    }

    @Test
    fun import_preservesExistingStructure() {
        val source = emptyCollection()
        source.addBookmark("New", "https://new.example")

        val target = emptyCollection()
        val folder = target.addFolder("Existing")
        target.addBookmark("Old", "https://old.example", parentId = folder.id)

        importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)
        assertEquals(2, target.bookmarkCount())
        assertEquals(1, target.folderCount())
        assertTrue(target.allFolders().any { it.title == "Existing" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun import_cycleInSource_throws() {
        val source = emptyCollection()
        // Manually inject a cycle by creating folders that reference each other.
        // This simulates a corrupted parser output.
        val f1 = source.addFolder("F1")
        val f2 = source.addFolder("F2", parentId = f1.id)
        // Force a cycle by directly mutating the internal state (simulating bad data).
        // In practice this would come from a corrupted file.
        // We'll simulate by creating a new collection with bad data.
        val badSource = BookmarkCollection()
        badSource.replaceAll(
            newBookmarks = emptyList(),
            newFolders = listOf(
                OmniBookmarkFolder("f1", "f2", 0, "F1", 1, 1),
                OmniBookmarkFolder("f2", "f1", 0, "F2", 1, 1)
            )
        )

        val target = emptyCollection()
        importBookmarks(badSource, target, DuplicatePolicy.KEEP_BOTH)
    }

    @Test
    fun import_validData_noValidationIssues() {
        val source = emptyCollection()
        source.addBookmark("A", "https://a.example")
        source.addFolder("F")

        val target = emptyCollection()
        val result = importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)
        assertTrue(result.validationIssues.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Folder ID remapping
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_folderIdsRemapped_noCollisionWithTarget() {
        val source = emptyCollection()
        val srcFolder = source.addFolder("Work")
        source.addBookmark("Doc", "https://doc.example", parentId = srcFolder.id)

        val target = emptyCollection()
        val targetFolder = target.addFolder("Work") // same title, different ID
        target.addBookmark("Existing", "https://existing.example", parentId = targetFolder.id)

        importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)

        // Should have 2 folders (both "Work" folders preserved)
        assertEquals(2, target.folderCount())
        // Should have 2 bookmarks
        assertEquals(2, target.bookmarkCount())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Position preservation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun import_positionsAreDenseInTarget() {
        val source = emptyCollection()
        source.addBookmark("A", "https://a.example")
        source.addBookmark("B", "https://b.example")
        source.addBookmark("C", "https://c.example")

        val target = emptyCollection()
        importBookmarks(source, target, DuplicatePolicy.KEEP_BOTH)

        val positions = target.allBookmarks().map { it.position }.sorted()
        assertEquals(listOf(0L, 1L, 2L), positions)
    }
}
