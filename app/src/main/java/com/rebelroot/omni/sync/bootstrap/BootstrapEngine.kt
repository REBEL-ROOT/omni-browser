package com.rebelroot.omni.sync.bootstrap

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.model.*
import org.json.JSONArray
import org.json.JSONObject

data class StateSnapshot(
    val version: Int = 1,
    val snapshotHlc: Hlc,
    val folders: List<SyncOperation>,
    val bookmarks: List<SyncOperation>
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("version", version)
            put("snapshotHlc", snapshotHlc.toString())
            put("folders", JSONArray().apply {
                folders.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.entityId)
                        put("parentId", f.folderPayload?.parentId ?: "root")
                        put("position", f.folderPayload?.position ?: "a0")
                        put("title", f.folderPayload?.title ?: "")
                    })
                }
            })
            put("bookmarks", JSONArray().apply {
                bookmarks.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.entityId)
                        put("parentId", b.bookmarkPayload?.parentId ?: "root")
                        put("position", b.bookmarkPayload?.position ?: "a0")
                        put("title", b.bookmarkPayload?.title ?: "")
                        put("url", b.bookmarkPayload?.url ?: "")
                    })
                }
            })
        }.toString()
    }
}

class BootstrapEngine(
    private val clock: HlcClock,
    private val adapter: BookmarkAdapter
) {
    fun generateSnapshot(collection: BookmarkCollection): StateSnapshot {
        val currentHlc = clock.now()
        val allOps = adapter.exportToOperations(collection)
        val folders = allOps.filter { it.entityType == SyncEntityType.FOLDER }
        val bookmarks = allOps.filter { it.entityType == SyncEntityType.BOOKMARK }
        return StateSnapshot(1, currentHlc, folders, bookmarks)
    }

    fun applySnapshot(collection: BookmarkCollection, snapshot: StateSnapshot) {
        clock.update(snapshot.snapshotHlc)
        // Apply folders first
        snapshot.folders.forEach { f ->
            adapter.applyRemoteOperation(collection, f)
        }
        // Apply bookmarks
        snapshot.bookmarks.forEach { b ->
            adapter.applyRemoteOperation(collection, b)
        }
    }
}
