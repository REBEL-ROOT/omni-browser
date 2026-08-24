package com.rebelroot.omni.sync.mozilla

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MozillaBookmarkBridgeTest {

    private lateinit var bridge: MozillaBookmarkBridge
    private lateinit var collection: BookmarkCollection

    @Before
    fun setUp() {
        bridge = MozillaBookmarkBridge()
        collection = BookmarkCollection()
    }

    @Test
    fun testExportToMozillaFormat() {
        val folder = collection.addFolder("Work")
        collection.addBookmark("GitHub", "https://github.com", parentId = folder.id)
        collection.addBookmark("Omni Browser", "https://omnibrowser.org", parentId = ROOT_FOLDER_ID)

        val items = bridge.exportCollectionToMozilla(collection)
        assertEquals(3, items.size)

        val folderItem = items.find { it.type == BookmarkType.FOLDER }
        assertNotNull(folderItem)
        assertEquals("Work", folderItem?.title)
        assertEquals(MozillaBookmarkBridge.MOBILE_GUID, folderItem?.parentGuid)

        val childBookmark = items.find { it.url == "https://github.com" }
        assertNotNull(childBookmark)
        assertEquals(folder.id.take(12), childBookmark?.parentGuid)
    }

    @Test
    fun testExportToBsoRecordsAndParseBack() {
        collection.addBookmark("Mozilla", "https://mozilla.org")
        val bsoList = bridge.exportToBsoRecords(collection)
        assertEquals(1, bsoList.size)

        val parsedItems = bridge.parseBsoRecords(bsoList)
        assertEquals(1, parsedItems.size)
        assertEquals("Mozilla", parsedItems[0].title)
        assertEquals("https://mozilla.org", parsedItems[0].url)
        assertFalse(parsedItems[0].isDeleted)
    }

    @Test
    fun testImportMozillaToCollectionWithHierarchyAndDeletions() {
        val remoteItems = listOf(
            BookmarkItem(
                guid = "fld_dev12345",
                parentGuid = MozillaBookmarkBridge.TOOLBAR_GUID,
                title = "Dev Tools",
                url = null,
                type = BookmarkType.FOLDER
            ),
            BookmarkItem(
                guid = "bmk_kt123456",
                parentGuid = "fld_dev12345",
                title = "Kotlin Docs",
                url = "https://kotlinlang.org",
                type = BookmarkType.BOOKMARK
            )
        )

        bridge.importMozillaToCollection(remoteItems, collection)

        val folders = collection.allFolders()
        assertEquals(1, folders.size)
        assertEquals("Dev Tools", folders[0].title)

        val bookmarks = collection.allBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("Kotlin Docs", bookmarks[0].title)
        assertEquals("https://kotlinlang.org", bookmarks[0].url)
        assertEquals(folders[0].id, bookmarks[0].parentId)

        // Test tombstone deletion
        val deletionItems = listOf(
            BookmarkItem(
                guid = "bmk_kt123456",
                parentGuid = "fld_dev12345",
                title = "",
                url = null,
                type = BookmarkType.BOOKMARK,
                isDeleted = true
            )
        )

        bridge.importMozillaToCollection(deletionItems, collection)
        assertEquals(0, collection.allBookmarks().size)
    }

    @Test
    fun testOrphanGuidSafeReParenting() {
        val orphanItem = listOf(
            BookmarkItem(
                guid = "orphan_12345",
                parentGuid = "non_existent_folder_guid",
                title = "Orphan Bookmark",
                url = "https://example.com/orphan",
                type = BookmarkType.BOOKMARK
            )
        )

        // Should safely import to root folder rather than crashing or creating detached nodes
        bridge.importMozillaToCollection(orphanItem, collection)
        val allBmk = collection.allBookmarks()
        assertEquals(1, allBmk.size)
        assertEquals("Orphan Bookmark", allBmk[0].title)
        assertEquals(ROOT_FOLDER_ID, allBmk[0].parentId)
    }
}
