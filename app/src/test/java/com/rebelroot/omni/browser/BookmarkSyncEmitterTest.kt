/*
 * Omni Browser - Bookmark sync emitter tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.sync.model.SyncEntityType
import com.rebelroot.omni.sync.model.SyncOpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkSyncEmitterTest {

    @Test
    fun `phone root maps to the browser's existing Mobile Bookmarks container`() {
        assertEquals("mobile_bookmarks", BookmarkSyncEmitter.parentIdFor(ROOT_FOLDER_ID))
        // Real folder ids pass through unchanged so nesting is preserved.
        assertEquals("fld_123", BookmarkSyncEmitter.parentIdFor("fld_123"))
    }

    @Test
    fun `create op carries the mapped parent and a fractional position`() {
        val collection = BookmarkCollection()
        val folder = collection.addFolder("Tech")
        val bookmark = collection.addBookmark("Kotlin", "https://kotlinlang.org", parentId = folder.id)

        val folderOp = BookmarkSyncEmitter.createOp(collection, folder.id)
        assertNotNull(folderOp)
        assertEquals(SyncOpType.CREATE, folderOp!!.opType)
        assertEquals(SyncEntityType.FOLDER, folderOp.entityType)
        assertEquals("mobile_bookmarks", folderOp.folderPayload?.parentId)
        assertTrue(folderOp.folderPayload!!.position.isNotBlank())

        val bookmarkOp = BookmarkSyncEmitter.createOp(collection, bookmark.id)
        assertNotNull(bookmarkOp)
        assertEquals(SyncEntityType.BOOKMARK, bookmarkOp!!.entityType)
        // Nested bookmarks keep their real folder as parent, so the tree survives.
        assertEquals(folder.id, bookmarkOp.bookmarkPayload?.parentId)
        assertEquals("https://kotlinlang.org", bookmarkOp.bookmarkPayload?.url)
    }

    @Test
    fun `snapshot emits folders before their children`() {
        val collection = BookmarkCollection()
        val tech = collection.addFolder("Tech")
        val android = collection.addFolder("Android", parentId = tech.id)
        collection.addBookmark("Kotlin", "https://kotlinlang.org", parentId = android.id)

        val ops = BookmarkSyncEmitter.snapshotOps(collection)
        val folderIds = ops.filter { it.entityType == SyncEntityType.FOLDER }.map { it.entityId }

        assertEquals(listOf(tech.id, android.id), folderIds)

        // Every folder op points at a parent that is emitted before it.
        val seen = mutableSetOf<String>()
        for (op in ops) {
            val parent = op.folderPayload?.parentId ?: op.bookmarkPayload?.parentId
            if (parent != null && parent != "mobile_bookmarks") {
                assertTrue("parent $parent must be emitted before ${op.entityId}", parent in seen)
            }
            seen.add(op.entityId)
        }
    }
}