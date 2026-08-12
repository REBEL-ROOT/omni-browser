/*
 * Omni Browser - Canonical Bookmark Model Hardening Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 01 hardening gate: invariant validation, ordering, property tests.
 * Tests are grouped by the hardening requirements document.
 */

package com.rebelroot.omni.bookmarks.model

import org.junit.Test
import org.junit.Assert.*

class BookmarkCollectionHardeningTest {

    private fun newCollection() = BookmarkCollection { 1_000_000L }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. VALIDATION TESTS — Malformed candidate datasets
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun validate_emptyId_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.EMPTY_ID })
    }

    @Test
    fun validate_reservedRootId_detectedInBookmark() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark(ROOT_FOLDER_ID, ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.RESERVED_ROOT_ID })
    }

    @Test
    fun validate_reservedRootId_detectedInFolder() {
        val issues = BookmarkCollection.validate(
            newBookmarks = emptyList(),
            newFolders = listOf(OmniBookmarkFolder(ROOT_FOLDER_ID, ROOT_FOLDER_ID, 0, "F", 1, 1))
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.RESERVED_ROOT_ID })
    }

    @Test
    fun validate_duplicateId_acrossBookmarkAndFolder() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("dup", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1)),
            newFolders = listOf(OmniBookmarkFolder("dup", ROOT_FOLDER_ID, 1, "F", 1, 1))
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.DUPLICATE_ID })
    }

    @Test
    fun validate_unknownParent_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", "missing", 0, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.UNKNOWN_PARENT })
    }

    @Test
    fun validate_negativePosition_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, -1, "A", "https://a.example", 1, 1)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.NEGATIVE_POSITION })
    }

    @Test
    fun validate_duplicatePosition_detected() {
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
    fun validate_positionGaps_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(
                OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1),
                OmniBookmark("b2", ROOT_FOLDER_ID, 2, "B", "https://b.example", 1, 1)
            ),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.NON_DENSE_POSITION })
    }

    @Test
    fun validate_positionStartingAtOne_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(
                OmniBookmark("b1", ROOT_FOLDER_ID, 1, "A", "https://a.example", 1, 1)
            ),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.NON_DENSE_POSITION })
    }

    @Test
    fun validate_selfParentingFolder_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = emptyList(),
            newFolders = listOf(OmniBookmarkFolder("f1", "f1", 0, "F", 1, 1))
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.SELF_PARENT })
    }

    @Test
    fun validate_cycle_aToB_bToA_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = emptyList(),
            newFolders = listOf(
                OmniBookmarkFolder("f1", "f2", 0, "A", 1, 1),
                OmniBookmarkFolder("f2", "f1", 0, "B", 1, 1)
            )
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.PARENT_CYCLE })
    }

    @Test
    fun validate_cycle_threeDeep_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = emptyList(),
            newFolders = listOf(
                OmniBookmarkFolder("f1", "f2", 0, "A", 1, 1),
                OmniBookmarkFolder("f2", "f3", 0, "B", 1, 1),
                OmniBookmarkFolder("f3", "f1", 0, "C", 1, 1)
            )
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.PARENT_CYCLE })
    }

    @Test
    fun validate_bookmarkParentedToBookmark_detectedAsUnknownParent() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(
                OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 1, 1),
                OmniBookmark("b2", "b1", 0, "B", "https://b.example", 1, 1)
            ),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.UNKNOWN_PARENT })
    }

    @Test
    fun validate_timestampReversed_detectedAsWarning() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 100, 50)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.TIMESTAMP_REVERSED })
    }

    @Test
    fun validate_timestampOutOfRange_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", -1, 50)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.TIMESTAMP_OUT_OF_RANGE })
    }

    @Test
    fun validate_timestampFarFuture_detected() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 5_000_000_000_000L, 5_000_000_000_000L)),
            newFolders = emptyList()
        )
        assertTrue(issues.any { it.kind == BookmarkValidationIssue.Kind.TIMESTAMP_OUT_OF_RANGE })
    }

    @Test
    fun validate_wellFormedData_noIssues() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(
                OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 100, 200),
                OmniBookmark("b2", "f1", 0, "B", "https://b.example", 100, 200)
            ),
            newFolders = listOf(
                OmniBookmarkFolder("f1", ROOT_FOLDER_ID, 1, "F", 100, 200)
            )
        )
        assertTrue("Expected no issues but got: $issues", issues.isEmpty())
    }

    @Test
    fun validate_zeroTimestamps_areAcceptable() {
        val issues = BookmarkCollection.validate(
            newBookmarks = listOf(OmniBookmark("b1", ROOT_FOLDER_ID, 0, "A", "https://a.example", 0, 0)),
            newFolders = emptyList()
        )
        assertFalse(issues.any { it.kind == BookmarkValidationIssue.Kind.TIMESTAMP_OUT_OF_RANGE })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. ORDERING TESTS — Exhaustive move coverage
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun ordering_moveFirstToLast_withinSameParent() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        assertTrue(c.moveItem(a.id, ROOT_FOLDER_ID, 2))
        assertEquals(listOf(b.id, d.id, a.id), c.childIds(ROOT_FOLDER_ID))
        assertEquals(listOf(0L, 1L, 2L), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.position })
    }

    @Test
    fun ordering_moveLastToFirst_withinSameParent() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        assertTrue(c.moveItem(d.id, ROOT_FOLDER_ID, 0))
        assertEquals(listOf(d.id, a.id, b.id), c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun ordering_moveMiddleToAnotherMiddle_withinSameParent() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        val e = c.addBookmark("E", "https://e.example")
        // Move B (index 1) to index 2 → A, D, B, E
        assertTrue(c.moveItem(b.id, ROOT_FOLDER_ID, 2))
        assertEquals(listOf(a.id, d.id, b.id, e.id), c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun ordering_moveFolder_withinSameParent() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val f = c.addFolder("F")
        val b = c.addBookmark("B", "https://b.example")
        assertTrue(c.moveItem(f.id, ROOT_FOLDER_ID, 2)) // move to end
        assertEquals(listOf(a.id, b.id, f.id), c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun ordering_moveBookmarkToEmptyFolder() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val f = c.addFolder("F")
        assertTrue(c.moveItem(a.id, f.id, 0))
        assertEquals(listOf(f.id), c.childIds(ROOT_FOLDER_ID)) // root still has the folder
        assertEquals(listOf(a.id), c.childIds(f.id))
        assertEquals(0L, c.bookmark(a.id)!!.position)
    }

    @Test
    fun ordering_moveFolderToEmptyFolder() {
        val c = newCollection()
        val f1 = c.addFolder("F1")
        val f2 = c.addFolder("F2")
        assertTrue(c.moveItem(f1.id, f2.id, 0))
        assertEquals(listOf(f1.id), c.childIds(f2.id))
        assertEquals(0L, c.folder(f1.id)!!.position)
    }

    @Test
    fun ordering_moveFromEmptySource_parentStaysValid() {
        val c = newCollection()
        val f = c.addFolder("F")
        val a = c.addBookmark("A", "https://a.example")
        assertTrue(c.moveItem(a.id, f.id, 0))
        assertEquals(1, c.childCount(ROOT_FOLDER_ID)) // root still has the folder F
    }

    @Test
    fun ordering_crossParentMixedTypes_interleavesCorrectly() {
        val c = newCollection()
        val src = c.addFolder("Src")
        val dst = c.addFolder("Dst")
        val bm1 = c.addBookmark("BM1", "https://1.example", parentId = src.id)
        val fSub = c.addFolder("Sub", parentId = src.id)
        val bm2 = c.addBookmark("BM2", "https://2.example", parentId = src.id)
        // Move folder Sub to Dst at position 0.
        assertTrue(c.moveItem(fSub.id, dst.id, 0))
        assertEquals(listOf(bm1.id, bm2.id), c.childIds(src.id))
        assertEquals(listOf(fSub.id), c.childIds(dst.id))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. PROPERTY / INVARIANT TESTS
    // ═══════════════════════════════════════════════════════════════════════

    /** Helper: assert every parent in [c] has dense 0-based positions. */
    private fun assertDensePositions(c: BookmarkCollection) {
        val allParents = (c.allBookmarks().map { it.parentId } + c.allFolders().map { it.parentId } + ROOT_FOLDER_ID).toSet()
        allParents.forEach { parentId ->
            val ids = c.childIds(parentId)
            val positions = ids.map { c.bookmark(it)?.position ?: c.folder(it)?.position ?: -1L }
            val expected = (0L until positions.size.toLong()).toList()
            assertEquals("Positions for parent $parentId must be dense 0-based", expected, positions)
        }
    }

    /** Helper: assert no duplicate IDs anywhere. */
    private fun assertNoDuplicateIds(c: BookmarkCollection) {
        val allIds = c.allBookmarks().map { it.id } + c.allFolders().map { it.id }
        assertEquals("All ids must be unique", allIds.size, allIds.toSet().size)
    }

    /** Helper: assert every non-root parent exists as a folder. */
    private fun assertParentsExist(c: BookmarkCollection) {
        val folderIds = c.allFolders().map { it.id }.toSet()
        c.allBookmarks().forEach { assertTrue("Bookmark ${it.id} parent ${it.parentId} must exist", it.parentId == ROOT_FOLDER_ID || it.parentId in folderIds) }
        c.allFolders().forEach { assertTrue("Folder ${it.id} parent ${it.parentId} must exist", it.parentId == ROOT_FOLDER_ID || it.parentId in folderIds) }
    }

    /** Helper: assert no cycles in the folder graph. */
    private fun assertNoCycles(c: BookmarkCollection) {
        val parentOf = c.allFolders().associate { it.id to it.parentId }
        c.allFolders().forEach { f ->
            val visited = mutableSetOf<String>()
            var current = f.parentId
            while (current != ROOT_FOLDER_ID && current in parentOf) {
                assertFalse("Cycle detected involving ${f.id}", current in visited)
                visited.add(current)
                current = parentOf[current] ?: break
                if (current == f.id) {
                    fail("Folder ${f.id} is its own ancestor")
                }
            }
        }
    }

    @Test
    fun invariant_afterAddBookmark_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        c.addBookmark("A", "https://a.example")
        c.addBookmark("B", "https://b.example")
        val f = c.addFolder("F")
        c.addBookmark("C", "https://c.example", parentId = f.id)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterAddFolder_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val l1 = c.addFolder("L1")
        val l2 = c.addFolder("L2", parentId = l1.id)
        val l3 = c.addFolder("L3", parentId = l2.id)
        c.addBookmark("deep", "https://deep.example", parentId = l3.id)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterMoveItem_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        val f = c.addFolder("F")
        c.moveItem(a.id, f.id, 0)
        c.moveItem(d.id, ROOT_FOLDER_ID, 0)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterMoveFolder_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val outer = c.addFolder("Outer")
        val inner = c.addFolder("Inner", parentId = outer.id)
        c.addBookmark("x", "https://x.example", parentId = inner.id)
        val dst = c.addFolder("Dst")
        c.moveItem(outer.id, dst.id, 0)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterDeleteBookmark_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val d = c.addBookmark("D", "https://d.example")
        c.deleteItem(a.id)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterDeleteFolder_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val outer = c.addFolder("Outer")
        val inner = c.addFolder("Inner", parentId = outer.id)
        c.addBookmark("x", "https://x.example", parentId = inner.id)
        c.addBookmark("y", "https://y.example", parentId = outer.id)
        c.deleteItem(outer.id)
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterRename_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        c.rename(a.id, "Renamed")
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterSetUrl_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        c.setUrl(a.id, "https://new.example")
        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_afterComplexSequence_denseNoDuplicatesParentsExistNoCycles() {
        val c = newCollection()
        val work = c.addFolder("Work")
        val personal = c.addFolder("Personal")
        val docs = c.addFolder("Docs", parentId = work.id)
        c.addBookmark("Sheet", "https://sheet.example", parentId = work.id)
        val resume = c.addBookmark("Resume", "https://resume.example", parentId = docs.id)

        c.moveItem(docs.id, personal.id, 0)
        c.rename(resume.id, "CV")
        c.deleteItem(personal.id)

        assertDensePositions(c)
        assertNoDuplicateIds(c)
        assertParentsExist(c)
        assertNoCycles(c)
    }

    @Test
    fun invariant_positionsMatchChildCounts() {
        val c = newCollection()
        val f1 = c.addFolder("F1")
        val a = c.addBookmark("A", "https://a.example")
        c.addBookmark("B", "https://b.example", parentId = f1.id)
        c.addFolder("F2", parentId = f1.id)

        assertEquals(2, c.childCount(ROOT_FOLDER_ID)) // F1 + A
        assertEquals(listOf(0L, 1L), c.childIds(ROOT_FOLDER_ID).indices.map { it.toLong() })
        assertEquals(2, c.childCount(f1.id)) // B + F2
        assertEquals(listOf(0L, 1L), c.childIds(f1.id).indices.map { it.toLong() })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. ROOT SEMANTICS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun rootFolderId_isImplicit_neverPersisted() {
        val c = newCollection()
        c.addBookmark("A", "https://a.example")
        c.addFolder("F")
        assertNull(c.folder(ROOT_FOLDER_ID))
        assertEquals(1, c.folderCount()) // one real folder 'F'
        assertEquals(1, c.bookmarkCount())
    }

    @Test
    fun rootFolderId_isTopLevelParent() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        assertEquals(ROOT_FOLDER_ID, c.bookmark(a.id)!!.parentId)
        assertEquals(listOf(a.id), c.bookmarkChildren(ROOT_FOLDER_ID).map { it.id })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. ADDITIONAL EDGE CASES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun moveItem_toSamePosition_withinSameParent_noOp() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        val original = c.childIds(ROOT_FOLDER_ID)
        assertTrue(c.moveItem(a.id, ROOT_FOLDER_ID, 0))
        assertEquals(original, c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun moveItem_clampsNegativeIndexToZero() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        val b = c.addBookmark("B", "https://b.example")
        assertTrue(c.moveItem(b.id, ROOT_FOLDER_ID, -5))
        assertEquals(listOf(b.id, a.id), c.childIds(ROOT_FOLDER_ID))
    }

    @Test
    fun deleteOnlyItem_leavesParentEmpty() {
        val c = newCollection()
        val a = c.addBookmark("A", "https://a.example")
        c.deleteItem(a.id)
        assertEquals(0, c.childCount(ROOT_FOLDER_ID))
        assertTrue(c.childIds(ROOT_FOLDER_ID).isEmpty())
    }

    @Test
    fun buildTree_emptyCollection_returnsRootWithNoChildren() {
        val c = newCollection()
        val root = c.buildTree()
        assertEquals(ROOT_FOLDER_ID, root.id)
        assertTrue(root.children.isEmpty())
    }

    @Test
    fun descendantsOf_leafFolder_returnsEmpty() {
        val c = newCollection()
        val f = c.addFolder("F")
        assertEquals(emptySet<String>(), c.descendantsOf(f.id))
    }

    @Test
    fun descendantsOf_unknownId_returnsEmpty() {
        val c = newCollection()
        assertEquals(emptySet<String>(), c.descendantsOf("ghost"))
    }
}
