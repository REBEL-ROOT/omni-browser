/*
 * Omni Browser - Local bookmark mutations → Omni Sync outbox
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Bridges the browser's bookmark model to the sync model so the phone actually
 * sends its own bookmark/folder changes to a paired desktop. Without this the
 * sync outbox was never populated, so `/api/sync/exchange` always returned an
 * empty `remoteOperations` and the desktop received nothing from the phone.
 *
 * Parent mapping: the phone's root maps to the desktop browser's existing
 * "Mobile Bookmarks" container (both Chromium and Firefox have one) instead of
 * inventing a synthetic root. User-created subfolders are still emitted as
 * folder operations — omitting them would flatten the tree into one list.
 */

package com.rebelroot.omni.browser

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.sync.model.BookmarkPayload
import com.rebelroot.omni.sync.model.FolderPayload
import com.rebelroot.omni.sync.model.FractionalIndex
import com.rebelroot.omni.sync.model.HlcClock
import com.rebelroot.omni.sync.model.SyncEntityType
import com.rebelroot.omni.sync.model.SyncOpType
import com.rebelroot.omni.sync.model.SyncOperation

internal object BookmarkSyncEmitter {

    /** Sync id of the desktop browser's existing "Mobile Bookmarks" root. */
    private const val MOBILE_ROOT = "mobile_bookmarks"

    /** Maps a local parent id into the desktop's sync id space. */
    fun parentIdFor(localParentId: String): String =
        if (localParentId == ROOT_FOLDER_ID) MOBILE_ROOT else localParentId

    private fun coordinator() = com.rebelroot.omni.SyncCoordinatorHolder.coordinator

    private fun clock(): HlcClock =
        coordinator()?.clock ?: HlcClock("omni_phone")

    private fun enqueue(op: SyncOperation?) {
        if (op == null) return
        // Queued only once the coordinator (and its persisted outbox) exists.
        coordinator()?.onLocalBookmarkMutation(op)
    }

    fun emitCreate(collection: BookmarkCollection, id: String) = enqueue(createOp(collection, id))
    fun emitUpdate(collection: BookmarkCollection, id: String) = enqueue(updateOp(collection, id))
    fun emitMove(collection: BookmarkCollection, id: String) = enqueue(moveOp(collection, id))

    fun emitDelete(id: String, isFolder: Boolean) {
        enqueue(
            SyncOperation(
                opType = SyncOpType.DELETE,
                entityType = if (isFolder) SyncEntityType.FOLDER else SyncEntityType.BOOKMARK,
                entityId = id,
                hlc = clock().now()
            )
        )
    }

    /** Emits CREATE for every folder and bookmark (used after import / restore). */
    fun emitSnapshot(collection: BookmarkCollection) {
        snapshotOps(collection).forEach { enqueue(it) }
    }

    // ── Builders ────────────────────────────────────────────────────────────

    private fun indexOf(collection: BookmarkCollection, parentId: String, id: String): Long =
        collection.childIds(parentId).indexOf(id).toLong().coerceAtLeast(0L)

    /** Visible for tests: builds the CREATE op for a single item. */
    internal fun createOp(collection: BookmarkCollection, id: String): SyncOperation? {
        val now = clock().now()

        collection.folder(id)?.let { folder ->
            return SyncOperation(
                opType = SyncOpType.CREATE,
                entityType = SyncEntityType.FOLDER,
                entityId = id,
                hlc = now,
                folderPayload = FolderPayload(
                    parentId = parentIdFor(folder.parentId),
                    position = FractionalIndex.fromDensePosition(indexOf(collection, folder.parentId, id)),
                    title = folder.title,
                    createdAt = folder.createdAt,
                    modifiedAt = folder.modifiedAt
                )
            )
        }

        val bookmark = collection.bookmark(id) ?: return null
        return SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = id,
            hlc = now,
            bookmarkPayload = BookmarkPayload(
                parentId = parentIdFor(bookmark.parentId),
                position = FractionalIndex.fromDensePosition(indexOf(collection, bookmark.parentId, id)),
                title = bookmark.title,
                url = bookmark.url,
                createdAt = bookmark.createdAt,
                modifiedAt = bookmark.modifiedAt
            )
        )
    }

    private fun updateOp(collection: BookmarkCollection, id: String): SyncOperation? {
        val now = clock().now()

        collection.folder(id)?.let { folder ->
            return SyncOperation(
                opType = SyncOpType.UPDATE_CONTENT,
                entityType = SyncEntityType.FOLDER,
                entityId = id,
                hlc = now,
                folderPayload = FolderPayload(
                    parentId = parentIdFor(folder.parentId),
                    position = FractionalIndex.fromDensePosition(indexOf(collection, folder.parentId, id)),
                    title = folder.title,
                    createdAt = folder.createdAt,
                    modifiedAt = folder.modifiedAt
                )
            )
        }

        val bookmark = collection.bookmark(id) ?: return null
        return SyncOperation(
            opType = SyncOpType.UPDATE_CONTENT,
            entityType = SyncEntityType.BOOKMARK,
            entityId = id,
            hlc = now,
            bookmarkPayload = BookmarkPayload(
                parentId = parentIdFor(bookmark.parentId),
                position = FractionalIndex.fromDensePosition(indexOf(collection, bookmark.parentId, id)),
                title = bookmark.title,
                url = bookmark.url,
                createdAt = bookmark.createdAt,
                modifiedAt = bookmark.modifiedAt
            )
        )
    }

    private fun moveOp(collection: BookmarkCollection, id: String): SyncOperation? {
        val now = clock().now()

        collection.folder(id)?.let { folder ->
            return SyncOperation(
                opType = SyncOpType.MOVE_REORDER,
                entityType = SyncEntityType.FOLDER,
                entityId = id,
                hlc = now,
                folderPayload = FolderPayload(
                    parentId = parentIdFor(folder.parentId),
                    position = FractionalIndex.fromDensePosition(indexOf(collection, folder.parentId, id)),
                    title = folder.title,
                    createdAt = folder.createdAt,
                    modifiedAt = folder.modifiedAt
                )
            )
        }

        val bookmark = collection.bookmark(id) ?: return null
        return SyncOperation(
            opType = SyncOpType.MOVE_REORDER,
            entityType = SyncEntityType.BOOKMARK,
            entityId = id,
            hlc = now,
            bookmarkPayload = BookmarkPayload(
                parentId = parentIdFor(bookmark.parentId),
                position = FractionalIndex.fromDensePosition(indexOf(collection, bookmark.parentId, id)),
                title = bookmark.title,
                url = bookmark.url,
                createdAt = bookmark.createdAt,
                modifiedAt = bookmark.modifiedAt
            )
        )
    }

    /**
     * Folders ordered parent-before-child, then bookmarks, so the desktop can
     * create parents before their children. Visible for tests.
     */
    internal fun snapshotOps(collection: BookmarkCollection): List<SyncOperation> {
        val ordered: List<String> = depthFirstFolderIds(collection, ROOT_FOLDER_ID)
        val ids = ordered + collection.allBookmarks().map { it.id }
        return ids.mapNotNull { createOp(collection, it) }
    }

    private fun depthFirstFolderIds(collection: BookmarkCollection, parentId: String): List<String> {
        val out = mutableListOf<String>()
        collection.folderChildren(parentId).forEach { child ->
            out.add(child.id)
            out.addAll(depthFirstFolderIds(collection, child.id))
        }
        return out
    }
}