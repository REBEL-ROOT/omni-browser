package com.rebelroot.omni.sync.adapter

import com.rebelroot.omni.bookmarks.model.*
import com.rebelroot.omni.sync.model.*

sealed class ApplyResult {
    data class Applied(val entityId: String, val opType: SyncOpType) : ApplyResult()
    data class Rejected(val entityId: String, val reason: String) : ApplyResult()
    data class Quarantined(val entityId: String, val reason: String) : ApplyResult()
}

class BookmarkAdapter(
    private val clock: HlcClock
) {

    fun exportToOperations(collection: BookmarkCollection): List<SyncOperation> {
        val ops = mutableListOf<SyncOperation>()
        
        collection.allFolders().forEach { folder ->
            val hlc = clock.now()
            ops.add(
                SyncOperation(
                    opType = SyncOpType.CREATE,
                    entityType = SyncEntityType.FOLDER,
                    entityId = folder.id,
                    hlc = hlc,
                    folderPayload = FolderPayload(
                        parentId = folder.parentId,
                        position = FractionalIndex.fromDensePosition(folder.position),
                        title = folder.title,
                        createdAt = folder.createdAt,
                        modifiedAt = folder.modifiedAt
                    )
                )
            )
        }

        collection.allBookmarks().forEach { bookmark ->
            val hlc = clock.now()
            ops.add(
                SyncOperation(
                    opType = SyncOpType.CREATE,
                    entityType = SyncEntityType.BOOKMARK,
                    entityId = bookmark.id,
                    hlc = hlc,
                    bookmarkPayload = BookmarkPayload(
                        parentId = bookmark.parentId,
                        position = FractionalIndex.fromDensePosition(bookmark.position),
                        title = bookmark.title,
                        url = bookmark.url,
                        createdAt = bookmark.createdAt,
                        modifiedAt = bookmark.modifiedAt
                    )
                )
            )
        }

        return ops
    }

    fun applyRemoteOperation(collection: BookmarkCollection, op: SyncOperation): ApplyResult {
        clock.update(op.hlc)

        return when (op.entityType) {
            SyncEntityType.FOLDER -> applyFolderOp(collection, op)
            SyncEntityType.BOOKMARK -> applyBookmarkOp(collection, op)
            else -> ApplyResult.Rejected(op.entityId, "Unsupported entity type: " + op.entityType)
        }
    }

    private fun applyFolderOp(collection: BookmarkCollection, op: SyncOperation): ApplyResult {
        if (op.opType == SyncOpType.DELETE) {
            collection.deleteItem(op.entityId)
            return ApplyResult.Applied(op.entityId, SyncOpType.DELETE)
        }

        val payload = op.folderPayload ?: return ApplyResult.Rejected(op.entityId, "Missing folder payload")

        return when (op.opType) {
            SyncOpType.CREATE -> {
                val existing = collection.folder(op.entityId)
                if (existing != null) {
                    collection.rename(op.entityId, payload.title)
                    ApplyResult.Applied(op.entityId, SyncOpType.CREATE)
                } else {
                    val safeParent = resolveParent(collection, payload.parentId)
                    collection.addFolderWithId(
                        id = op.entityId,
                        title = payload.title,
                        parentId = safeParent,
                        createdAt = payload.createdAt,
                        modifiedAt = payload.modifiedAt
                    )
                    ApplyResult.Applied(op.entityId, SyncOpType.CREATE)
                }
            }

            SyncOpType.UPDATE_CONTENT -> {
                val existing = collection.folder(op.entityId)
                if (existing != null) {
                    collection.rename(op.entityId, payload.title)
                    ApplyResult.Applied(op.entityId, SyncOpType.UPDATE_CONTENT)
                } else {
                    val safeParent = resolveParent(collection, payload.parentId)
                    collection.addFolderWithId(
                        id = op.entityId,
                        title = payload.title,
                        parentId = safeParent,
                        createdAt = payload.createdAt,
                        modifiedAt = payload.modifiedAt
                    )
                    ApplyResult.Applied(op.entityId, SyncOpType.UPDATE_CONTENT)
                }
            }

            SyncOpType.MOVE_REORDER -> {
                val existing = collection.folder(op.entityId)
                if (existing == null) {
                    return ApplyResult.Rejected(op.entityId, "Folder not found for move")
                }
                val safeParent = resolveParent(collection, payload.parentId)
                if (safeParent == op.entityId || isDescendant(collection, op.entityId, safeParent)) {
                    return ApplyResult.Rejected(op.entityId, "Cycle detected: cannot move folder into its own descendant")
                }
                collection.moveItem(op.entityId, safeParent, collection.childCount(safeParent))
                ApplyResult.Applied(op.entityId, SyncOpType.MOVE_REORDER)
            }

            SyncOpType.DELETE -> {
                collection.deleteItem(op.entityId)
                ApplyResult.Applied(op.entityId, SyncOpType.DELETE)
            }

            SyncOpType.SNAPSHOT_BOOTSTRAP -> {
                ApplyResult.Applied(op.entityId, SyncOpType.SNAPSHOT_BOOTSTRAP)
            }
        }
    }

    private fun applyBookmarkOp(collection: BookmarkCollection, op: SyncOperation): ApplyResult {
        if (op.opType == SyncOpType.DELETE) {
            collection.deleteItem(op.entityId)
            return ApplyResult.Applied(op.entityId, SyncOpType.DELETE)
        }

        val payload = op.bookmarkPayload ?: return ApplyResult.Rejected(op.entityId, "Missing bookmark payload")

        if (payload.url.startsWith("javascript:", ignoreCase = true) ||
            payload.url.startsWith("data:", ignoreCase = true)) {
            return ApplyResult.Rejected(op.entityId, "Security violation: rejected unsafe URI scheme")
        }

        return when (op.opType) {
            SyncOpType.CREATE -> {
                val existing = collection.bookmark(op.entityId)
                if (existing != null) {
                    collection.rename(op.entityId, payload.title)
                    if (payload.url.isNotBlank()) collection.setUrl(op.entityId, payload.url)
                    ApplyResult.Applied(op.entityId, SyncOpType.CREATE)
                } else {
                    val safeParent = resolveParent(collection, payload.parentId)
                    collection.addBookmarkWithId(
                        id = op.entityId,
                        title = payload.title,
                        url = payload.url,
                        parentId = safeParent,
                        createdAt = payload.createdAt,
                        modifiedAt = payload.modifiedAt
                    )
                    ApplyResult.Applied(op.entityId, SyncOpType.CREATE)
                }
            }

            SyncOpType.UPDATE_CONTENT -> {
                val existing = collection.bookmark(op.entityId)
                if (existing != null) {
                    collection.rename(op.entityId, payload.title)
                    if (payload.url.isNotBlank()) collection.setUrl(op.entityId, payload.url)
                    ApplyResult.Applied(op.entityId, SyncOpType.UPDATE_CONTENT)
                } else {
                    val safeParent = resolveParent(collection, payload.parentId)
                    collection.addBookmarkWithId(
                        id = op.entityId,
                        title = payload.title,
                        url = payload.url,
                        parentId = safeParent,
                        createdAt = payload.createdAt,
                        modifiedAt = payload.modifiedAt
                    )
                    ApplyResult.Applied(op.entityId, SyncOpType.UPDATE_CONTENT)
                }
            }

            SyncOpType.MOVE_REORDER -> {
                val existing = collection.bookmark(op.entityId)
                if (existing == null) {
                    return ApplyResult.Rejected(op.entityId, "Bookmark not found for move")
                }
                val safeParent = resolveParent(collection, payload.parentId)
                collection.moveItem(op.entityId, safeParent, collection.childCount(safeParent))
                ApplyResult.Applied(op.entityId, SyncOpType.MOVE_REORDER)
            }

            SyncOpType.DELETE -> {
                collection.deleteItem(op.entityId)
                ApplyResult.Applied(op.entityId, SyncOpType.DELETE)
            }

            SyncOpType.SNAPSHOT_BOOTSTRAP -> {
                ApplyResult.Applied(op.entityId, SyncOpType.SNAPSHOT_BOOTSTRAP)
            }
        }
    }

    private fun resolveParent(collection: BookmarkCollection, parentId: String): String {
        if (parentId == ROOT_FOLDER_ID || parentId == "root" || parentId == "bookmarks_bar" || parentId == "other_bookmarks" || parentId == "mobile_bookmarks") {
            return ROOT_FOLDER_ID
        }
        return if (collection.folder(parentId) != null) {
            parentId
        } else {
            ROOT_FOLDER_ID
        }
    }

    private fun isDescendant(collection: BookmarkCollection, ancestorId: String, targetId: String): Boolean {
        var current: String? = targetId
        while (current != null && current != ROOT_FOLDER_ID) {
            if (current == ancestorId) return true
            current = collection.folder(current)?.parentId
        }
        return false
    }
}
