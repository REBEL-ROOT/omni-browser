package com.rebelroot.omni.sync

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.sync.adapter.ApplyResult
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BookmarkAdapterTest {

    private lateinit var clock: HlcClock
    private lateinit var adapter: BookmarkAdapter
    private lateinit var collection: BookmarkCollection

    @Before
    fun setup() {
        clock = HlcClock("dev_test_01")
        adapter = BookmarkAdapter(clock)
        collection = BookmarkCollection()
    }

    @Test
    fun exportToOperations_exportsFullTreeCorrectly() {
        val f = collection.addFolder("Tools")
        collection.addBookmark("Omni", "https://omnibrowser.app", f.id)

        val ops = adapter.exportToOperations(collection)
        assertEquals(2, ops.size)
        assertEquals(SyncEntityType.FOLDER, ops[0].entityType)
        assertEquals(SyncEntityType.BOOKMARK, ops[1].entityType)
    }

    @Test
    fun applyRemoteOperation_createsFolderAndBookmark() {
        val folderOp = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.FOLDER,
            entityId = "fld_remote_01",
            hlc = Hlc.initial("dev_remote_01", 1000L),
            folderPayload = FolderPayload(parentId = "root", title = "Remote Folder")
        )
        val res1 = adapter.applyRemoteOperation(collection, folderOp)
        assertTrue(res1 is ApplyResult.Applied)
        assertNotNull(collection.folder("fld_remote_01"))

        val bmkOp = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_remote_01",
            hlc = Hlc.initial("dev_remote_01", 1001L),
            bookmarkPayload = BookmarkPayload(
                parentId = "fld_remote_01",
                title = "GitHub",
                url = "https://github.com"
            )
        )
        val res2 = adapter.applyRemoteOperation(collection, bmkOp)
        assertTrue(res2 is ApplyResult.Applied)
        val bmk = collection.bookmark("bmk_remote_01")
        assertNotNull(bmk)
        assertEquals("fld_remote_01", bmk?.parentId)
    }

    @Test
    fun applyRemoteOperation_recoversOrphansToRoot() {
        val orphanOp = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_orphan_01",
            hlc = Hlc.initial("dev_remote_01", 1000L),
            bookmarkPayload = BookmarkPayload(
                parentId = "fld_non_existent",
                title = "Orphan Item",
                url = "https://example.com"
            )
        )
        val res = adapter.applyRemoteOperation(collection, orphanOp)
        assertTrue(res is ApplyResult.Applied)
        val bmk = collection.bookmark("bmk_orphan_01")
        assertNotNull(bmk)
        assertEquals("Orphan must safely fall back to root folder", ROOT_FOLDER_ID, bmk?.parentId)
    }

    @Test
    fun applyRemoteOperation_rejectsUnsafeUriSchemes() {
        val xssOp = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_xss_01",
            hlc = Hlc.initial("dev_remote_01", 1000L),
            bookmarkPayload = BookmarkPayload(
                parentId = "root",
                title = "Evil XSS",
                url = "javascript:alert(document.cookie)"
            )
        )
        val res = adapter.applyRemoteOperation(collection, xssOp)
        assertTrue("javascript: scheme must be rejected", res is ApplyResult.Rejected)
        assertNull(collection.bookmark("bmk_xss_01"))
    }

    @Test
    fun applyRemoteOperation_rejectsCycles() {
        val f1 = collection.addFolderWithId("fld_1", "Folder 1", ROOT_FOLDER_ID)
        val f2 = collection.addFolderWithId("fld_2", "Folder 2", f1.id)

        val cycleOp = SyncOperation(
            opType = SyncOpType.MOVE_REORDER,
            entityType = SyncEntityType.FOLDER,
            entityId = "fld_1",
            hlc = Hlc.initial("dev_remote_01", 1000L),
            folderPayload = FolderPayload(parentId = "fld_2", title = "Folder 1")
        )
        val res = adapter.applyRemoteOperation(collection, cycleOp)
        assertTrue("Moving parent into child must be rejected", res is ApplyResult.Rejected)
        assertEquals(ROOT_FOLDER_ID, collection.folder("fld_1")?.parentId)
    }
}
