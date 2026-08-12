/*
 * Omni Browser - Canonical Bookmark Model Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 01 gate: stable identity, folder hierarchy, deterministic ordering,
 * moves, deletes, duplicate bookmarks, empty folders and integrity checks.
 */

package com.rebelroot.omni.bookmarks.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkCollectionTest {

    /** Deterministic clock so timestamps are stable across test runs. */
    private fun newCollection() = BookmarkCollection { 1_000_000L }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Test
    fun addBookmark_assignsStableUniqueIds() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        assertNotEquals(a.id, b.id)
        assertNotNull(a.id)
        assertFalse(a.id.isBlank())
    }

    @Test
    fun addBookmark_sameUrlTwice_createsDistinctItems() {
        val c = newCollection()
        val a = c.addBookmark("Same", "https://dup.example")
        val b = c.addBookmark("Same", "https://dup.example")
        assertNotEquals(a.id, b.id)
        assertEquals(2, c.bookmarkCount())
        // URL identity must not collide with canonical ids.
        assertNull(c.bookmark(a.id + "x"))
    }

    @Test
    fun addBookmark_sameTitleDifferentUrl_allowed() {
        val c = newCollection()
        c.addBookmark("Title", "https://one.example")
        c.addBookmark("Title", "https://two.example")
        assertEquals(2, c.bookmarkCount())
    }

    @Test
    fun addToUnknownParent_throws() {
        val c = newCollection()
        try {
            c.addBookmark("x", "https://x.example", parentId = "nope")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            c.addFolder("x", parentId = "nope")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ── Hierarchy ────────────────────────────────────────────────────────────

    @Test
    fun addFolder_childrenAppearUnderParent() {
        val c = newCollection()
        val folder = c.addFolder("Work")
        val bm = c.addBookmark("Docs", "https://docs.example", parentId = folder.id)
        assertEquals(listOf(folder.id), c.folderChildren(ROOT_FOLDER_ID).map { it.id })
        assertEquals(listOf(bm.id), c.bookmarkChildren(folder.id).map { it.id })
        assertEquals(0, c.bookmarkChildren(ROOT_FOLDER_ID).size)
    }

    @Test
    fun nestedFolders_arbitraryDepth_allowed() {
        val c = newCollection()
        val l1 = c.addFolder("L1")
        val l2 = c.addFolder("L2", parentId = l1.id)
        val l3 = c.addFolder("L3", parentId = l2.id)
        val bm = c.addBookmark("deep", "https://deep.example", parentId = l3.id)
        assertEquals(l1.id, c.folder(l2.id)!!.parentId)
        assertEquals(l2.id, c.folder(l3.id)!!.parentId)
        assertEquals(l3.id, c.bookmark(bm.id)!!.parentId)
    }

    @Test
    fun emptyFolder_isAllowed() {
        val c = newCollection()
        val folder = c.addFolder("Empty")
        assertTrue(c.bookmarkChildren(folder.id).isEmpty())
        assertTrue(c.folderChildren(folder.id).isEmpty())
        assertEquals(1, c.folderCount())
    }

    // ── Ordering ─────────────────────────────────────────────────────────────

    @Test
    fun appendPositions_areSequential() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addFolder("D")
        val e = c.addBookmark("E", "https://e.example")
        assertEquals(0L, a.position)
        assertEquals(1L, b.position)
        assertEquals(2L, d.position)
        assertEquals(3L, e.position)
        // Folders and bookmarks share the same position space inside a parent.
        assertEquals(listOf(a.id, b.id, d.id, e.id), c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun childrenAreSortedByPosition() {
        val c = newCollection()
        val z = c.addBookmark("Z", "https://z.example", position = 3)
        val a = c.addBookmark("A", "https://a.example", position = 0)
        val m = c.addBookmark("M", "https://m.example", position = 1)
        assertEquals(listOf(a.id, m.id, z.id), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.id })
    }

    @Test
    fun moveItem_withinSameParent_shiftsDensely() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        // Move A to index 2 → B, D, A
        assertTrue(c.moveItem(a.id, ROOT_FOLDER_ID, 2))
        assertEquals(listOf(b.id, d.id, a.id), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.id })
        assertEquals(listOf(0L, 1L, 2L), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.position })
    }

    @Test
    fun moveItem_toBack_clampsToEnd() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        assertTrue(c.moveItem(a.id, ROOT_FOLDER_ID, 99))
        assertEquals(listOf(b.id, a.id), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.id })
    }

    @Test
    fun moveItem_acrossParents_reindexesBoth() {
        val c = newCollection()
        val folder = c.addFolder("Folder")
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        assertTrue(c.moveItem(a.id, folder.id, 0))
        assertEquals(listOf(b.id), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.id })
        assertEquals(1L, c.bookmark(b.id)!!.position) // folder occupies position 0
        assertEquals(listOf(a.id), c.bookmarkChildren(folder.id).map { it.id })
        assertEquals(folder.id, c.bookmark(a.id)!!.parentId)
    }

    @Test
    fun moveUnknownItem_returnsFalse() {
        val c = newCollection()
        assertFalse(c.moveItem("ghost", ROOT_FOLDER_ID, 0))
    }

    @Test
    fun moveFolder_intoItself_throws() {
        val c = newCollection()
        val f = c.addFolder("F")
        try {
            c.moveItem(f.id, f.id, 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun moveFolder_intoOwnDescendant_throws() {
        val c = newCollection()
        val l1 = c.addFolder("L1")
        val l2 = c.addFolder("L2", parentId = l1.id)
        try {
            c.moveItem(l1.id, l2.id, 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ── Deletion ─────────────────────────────────────────────────────────────

    @Test
    fun deleteBookmark_removesOnlyThatItem() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        assertTrue(c.deleteItem(a.id))
        assertNull(c.bookmark(a.id))
        assertNotNull(c.bookmark(b.id))
        assertEquals(1, c.bookmarkCount())
        assertEquals(0L, c.bookmark(b.id)!!.position) // stays dense
    }

    @Test
    fun deleteFolder_cascadesToDirectAndNestedChildren() {
        val c = newCollection()
        val outer = c.addFolder("Outer")
        val inner = c.addFolder("Inner", parentId = outer.id)
        val direct = c.addBookmark("direct", "https://direct.example", parentId = outer.id)
        val nested = c.addBookmark("nested", "https://nested.example", parentId = inner.id)
        val sibling = c.addBookmark("sibling", "https://sibling.example")
        assertTrue(c.deleteItem(outer.id))
        assertNull(c.folder(outer.id))
        assertNull(c.folder(inner.id))
        assertNull(c.bookmark(direct.id))
        assertNull(c.bookmark(nested.id))
        assertNotNull(c.bookmark(sibling.id))
        assertEquals(1, c.bookmarkCount())
        assertEquals(0, c.folderCount())
    }

    @Test
    fun deleteUnknownId_returnsFalse() {
        val c = newCollection()
        assertFalse(c.deleteItem("ghost"))
    }

    // ── Rename / URL update ──────────────────────────────────────────────────

    @Test
    fun rename_updatesTitleAndModifiedAt() {
        var t = 1_000L
        val c = BookmarkCollection { t++ }
        val bm = c.addBookmark("Old", "https://a.example")
        val before = c.bookmark(bm.id)!!.modifiedAt
        assertTrue(c.rename(bm.id, "New"))
        assertEquals("New", c.bookmark(bm.id)!!.title)
        assertTrue(c.bookmark(bm.id)!!.modifiedAt > before)
    }

    @Test
    fun renameUnknownId_returnsFalse() {
        val c = newCollection()
        assertFalse(c.rename("ghost", "x"))
    }

    @Test
    fun setUrl_updatesOnlyUrl() {
        val c = newCollection()
        val bm = c.addBookmark("A", "https://old.example")
        assertTrue(c.setUrl(bm.id, "https://new.example"))
        assertEquals("https://new.example", c.bookmark(bm.id)!!.url)
        assertEquals("A", c.bookmark(bm.id)!!.title)
    }

    @Test
    fun setUrl_onFolder_returnsFalse() {
        val c = newCollection()
        val f = c.addFolder("F")
        assertFalse(c.setUrl(f.id, "https://x.example"))
    }

    // ── Tree ─────────────────────────────────────────────────────────────────

    @Test
    fun buildTree_preservesHierarchyAndOrder() {
        val c = newCollection()
        val folder = c.addFolder("F")
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example", parentId = folder.id)
        val sub = c.addFolder("Sub", parentId = folder.id)
        val deep = c.addBookmark("Deep", "https://deep.example", parentId = sub.id)

        val root = c.buildTree()
        assertEquals(ROOT_FOLDER_ID, root.id)
        assertEquals(2, root.children.size)
        val rootChild = root.children.first { it.id == a.id } as BookmarkNode.Item
        assertEquals(a.id, rootChild.id)

        val folderNode = root.children.first { it.id == folder.id } as BookmarkNode.Folder
        assertEquals(listOf(b.id, sub.id), folderNode.children.map { it.id })
        val subNode = folderNode.children.last() as BookmarkNode.Folder
        assertEquals(listOf(deep.id), subNode.children.map { it.id })
    }

    @Test
    fun buildTree_sortsChildrenByPositionAcrossTypes() {
        val c = newCollection()
        val bm0 = c.addBookmark("bm0", "https://0.example", position = 1)
        val f = c.addFolder("folder", position = 0)
        val bm2 = c.addBookmark("bm2", "https://2.example", position = 2)
        val root = c.buildTree()
        assertEquals(listOf(f.id, bm0.id, bm2.id), root.children.map { it.id })
    }

    @Test
    fun descendantsOf_deepNested_findsAll() {
        val c = newCollection()
        val l1 = c.addFolder("L1")
        val l2 = c.addFolder("L2", parentId = l1.id)
        val l3 = c.addFolder("L3", parentId = l2.id)
        val other = c.addFolder("Other")
        assertEquals(setOf(l2.id, l3.id), c.descendantsOf(l1.id))
        assertEquals(emptySet<String>(), c.descendantsOf(other.id))
    }

    // ── Integrity ────────────────────────────────────────────────────────────

    @Test
    fun validate_wellFormedCollection_noIssues() {
        val c = newCollection()
        val f = c.addFolder("F")
        c.addBookmark("A", "https://a.example")
        c.addBookmark("B", "https://b.example", parentId = f.id)
        assertTrue(c.validate().isEmpty())
    }

    @Test
    fun validate_reportsUnknownParent() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", "missing", 0, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertEquals(1, issues.count { it.kind == BookmarkValidationIssue.Kind.UNKNOWN_PARENT })
    }

    @Test
    fun validate_reportsNegativePosition() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, -1, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.NEGATIVE_POSITION })
    }

    @Test
    fun validate_reportsDuplicatePosition() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(
                OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1),
                OmniBookmark("b2", ROOT_FOLDER_ID, 0, "B", "https://b.example", 1, 1)
            ),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.DUPLICATE_POSITION })
    }

    @Test
    fun unvalidatedCandidate_mustBeCheckedBeforeCommit() {
        val c = newCollection()
        val keep = c.addBookmark("keep", "https://keep.example")
        val candidate = listOf(OmniBookmark("b1", "missing-parent", 0, "A", "https://a.example", 1, 1))
        val issues = BookmarkCollection.validate(candidate, emptyList())
        assertTrue(issues.isNotEmpty())
        // Storage-layer pattern: commit only when validation is clean.
        if (issues.isEmpty()) c.replaceAll(candidate, emptyList())
        // Original state must be untouched.
        assertNotNull(c.bookmark(keep.id))
        assertEquals(1, c.bookmarkCount())
    }

    @Test
    fun replaceAll_validData_swapsState() {
        val c = newCollection()
        c.addBookmark("old", "https://old.example")
        c.replaceAll(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1)),
            newFolders = listOf(OmniBookmarkFolder("f1", ROOT_FOLDER_ID, 1, "F", 1, 1))
        )
        assertEquals(1, c.bookmarkCount())
        assertEquals(1, c.folderCount())
        assertEquals(listOf("b1", "f1"), c.childIds(ROOT_FOLDER_ID))
    }

    // ── Multi-edit sequence ──────────────────────────────────────────────────

    @Test
    fun complexSequence_remainsConsistent() {
        val c = newCollection()
        val work = c.addFolder("Work")
        val personal = c.addFolder("Personal")
        val docs = c.addFolder("Docs", parentId = work.id)
        c.addBookmark("Sheet", "https://sheet.example", parentId = work.id)
        val resume = c.addBookmark("Resume", "https://resume.example", parentId = docs.id)

        // Move the Docs folder (with Resume inside) from Work to Personal.
        assertTrue(c.moveItem(docs.id, personal.id, 0))
        assertEquals(personal.id, c.folder(docs.id)!!.parentId)
        assertEquals(docs.id, c.bookmark(resume.id)!!.parentId)
        assertTrue(c.validate().isEmpty())

        // Rename the Resume bookmark.
        assertTrue(c.rename(resume.id, "CV"))
        assertEquals("CV", c.bookmark(resume.id)!!.title)

        // Delete Personal → Docs → Resume cascade.
        assertTrue(c.deleteItem(personal.id))
        assertNull(c.folder(docs.id))
        assertNull(c.bookmark(resume.id))
        assertTrue(c.validate().isEmpty())
        assertEquals(listOf(work.id), c.folderChildren(ROOT_FOLDER_ID).map { it.id })
    }
}
