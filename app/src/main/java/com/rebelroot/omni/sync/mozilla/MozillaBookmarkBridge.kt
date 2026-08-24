package com.rebelroot.omni.sync.mozilla

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.OmniBookmark
import com.rebelroot.omni.bookmarks.model.OmniBookmarkFolder
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BookmarkType {
    FOLDER, BOOKMARK, SEPARATOR
}

data class BookmarkItem(
    val guid: String,
    val parentGuid: String,
    val position: Long = 0L,
    val title: String,
    val url: String?,
    val type: BookmarkType,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val childrenGuids: List<String> = emptyList()
)

class MozillaBookmarkBridge {

    /**
     * Converts Omni [BookmarkCollection] into a flat list of [BookmarkItem] records
     * using Mozilla Sync 1.5 GUID standards and well-known root mappings.
     */
    fun exportCollectionToMozilla(collection: BookmarkCollection): List<BookmarkItem> {
        val items = mutableListOf<BookmarkItem>()

        collection.allFolders().forEach { folder ->
            val parentGuid = mapOmniParentToMozillaGuid(folder.parentId)
            items.add(
                BookmarkItem(
                    guid = mapOmniIdToGuid(folder.id),
                    parentGuid = parentGuid,
                    position = folder.position,
                    title = folder.title,
                    url = null,
                    type = BookmarkType.FOLDER,
                    dateAdded = folder.createdAt,
                    lastModified = folder.modifiedAt
                )
            )
        }

        collection.allBookmarks().forEach { bookmark ->
            val parentGuid = mapOmniParentToMozillaGuid(bookmark.parentId)
            items.add(
                BookmarkItem(
                    guid = mapOmniIdToGuid(bookmark.id),
                    parentGuid = parentGuid,
                    position = bookmark.position,
                    title = bookmark.title,
                    url = bookmark.url,
                    type = BookmarkType.BOOKMARK,
                    dateAdded = bookmark.createdAt,
                    lastModified = bookmark.modifiedAt
                )
            )
        }

        return items
    }

    /**
     * Converts [BookmarkCollection] directly into BSO records ready to upload to Mozilla Sync.
     */
    fun exportToBsoRecords(collection: BookmarkCollection): List<BsoRecord> {
        val items = exportCollectionToMozilla(collection)
        return items.map { item ->
            val payload = JSONObject().apply {
                put("id", item.guid)
                put("type", if (item.type == BookmarkType.FOLDER) "folder" else "bookmark")
                put("title", item.title)
                put("parentid", item.parentGuid)
                put("pos", item.position)
                if (item.url != null) {
                    put("bmkUri", item.url)
                }
                put("dateAdded", item.dateAdded)
                put("lastModified", item.lastModified)
            }
            BsoRecord(
                id = item.guid,
                modified = item.lastModified / 1000.0,
                payload = payload.toString()
            )
        }
    }

    /**
     * Parses BSO records received from Mozilla Sync into [BookmarkItem] records.
     */
    fun parseBsoRecords(bsoList: List<BsoRecord>): List<BookmarkItem> {
        val items = mutableListOf<BookmarkItem>()
        for (bso in bsoList) {
            try {
                val json = JSONObject(bso.payload)
                val isDeleted = json.optBoolean("deleted", false)
                val id = json.optString("id", bso.id)
                val typeStr = json.optString("type", "bookmark")
                val type = when (typeStr.lowercase()) {
                    "folder" -> BookmarkType.FOLDER
                    "separator" -> BookmarkType.SEPARATOR
                    else -> BookmarkType.BOOKMARK
                }
                val title = json.optString("title", "")
                val parentId = json.optString("parentid", MOBILE_GUID)
                val url = json.optString("bmkUri", json.optString("url", "")).takeIf { it.isNotBlank() }
                val position = json.optLong("pos", 0L)
                val dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
                val lastModified = (bso.modified * 1000.0).toLong()

                val children = mutableListOf<String>()
                val childrenArr = json.optJSONArray("children")
                if (childrenArr != null) {
                    for (i in 0 until childrenArr.length()) {
                        children.add(childrenArr.getString(i))
                    }
                }

                items.add(
                    BookmarkItem(
                        guid = id,
                        parentGuid = parentId,
                        position = position,
                        title = title,
                        url = url,
                        type = type,
                        dateAdded = dateAdded,
                        lastModified = lastModified,
                        isDeleted = isDeleted,
                        childrenGuids = children
                    )
                )
            } catch (e: Exception) {
                // Skip malformed record
            }
        }
        return items
    }

    /**
     * Imports remote Mozilla [BookmarkItem] records into Omni's [BookmarkCollection].
     * Handles tombstones (deletions), folder hierarchy safety, and prevents loops.
     */
    fun importMozillaToCollection(items: List<BookmarkItem>, collection: BookmarkCollection) {
        // 1. Process deletions first
        items.filter { it.isDeleted }.forEach { deletedItem ->
            collection.deleteItem(deletedItem.guid)
        }

        // 2. Process folders
        val nonDeleted = items.filter { !it.isDeleted }
        val folders = nonDeleted.filter { it.type == BookmarkType.FOLDER }
        val leaves = nonDeleted.filter { it.type == BookmarkType.BOOKMARK }

        folders.forEach { item ->
            val safeParentId = resolveSafeParentId(item.parentGuid, collection)
            val existing = collection.folder(item.guid)
            if (existing == null) {
                collection.addFolderWithId(
                    id = item.guid,
                    title = item.title.ifBlank { "Folder" },
                    parentId = safeParentId,
                    createdAt = item.dateAdded,
                    modifiedAt = item.lastModified
                )
            } else {
                collection.rename(item.guid, item.title.ifBlank { "Folder" })
            }
        }

        // 3. Process bookmarks
        leaves.forEach { item ->
            val url = item.url ?: return@forEach
            val safeParentId = resolveSafeParentId(item.parentGuid, collection)
            val existing = collection.bookmark(item.guid)
            if (existing == null) {
                // Also check if same URL exists locally under the same parent to avoid duplication
                val duplicateUrl = collection.allBookmarks().find { it.url == url }
                if (duplicateUrl == null) {
                    collection.addBookmarkWithId(
                        id = item.guid,
                        title = item.title.ifBlank { url },
                        url = url,
                        parentId = safeParentId,
                        createdAt = item.dateAdded,
                        modifiedAt = item.lastModified
                    )
                }
            } else {
                // Update title if modified
                if (existing.title != item.title && item.title.isNotBlank()) {
                    collection.addBookmarkWithId(
                        id = item.guid,
                        title = item.title,
                        url = url,
                        parentId = safeParentId,
                        createdAt = existing.createdAt,
                        modifiedAt = item.lastModified
                    )
                }
            }
        }
    }

    private fun resolveSafeParentId(parentGuid: String, collection: BookmarkCollection): String {
        val mapped = mapMozillaGuidToOmniParent(parentGuid)
        if (mapped.isEmpty() || mapped == com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID) return com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
        // Verify parent actually exists to prevent orphan nodes
        return if (collection.folder(mapped) != null) mapped else com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
    }

    private fun mapOmniParentToMozillaGuid(parentId: String): String {
        return when (parentId) {
            "", "root", "ROOT", com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID -> MOBILE_GUID
            else -> mapOmniIdToGuid(parentId)
        }
    }

    private fun mapMozillaGuidToOmniParent(guid: String): String {
        return when (guid) {
            MOBILE_GUID, UNFILED_GUID, MENU_GUID, TOOLBAR_GUID, ROOT_GUID -> com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
            else -> guid
        }
    }

    private fun mapOmniIdToGuid(id: String): String {
        return if (id.length <= 12) id else id.take(12)
    }

    companion object {
        const val ROOT_GUID = "root________"
        const val MENU_GUID = "menu________"
        const val TOOLBAR_GUID = "toolbar_____"
        const val UNFILED_GUID = "unfiled_____"
        const val MOBILE_GUID = "mobile______"
    }
}
