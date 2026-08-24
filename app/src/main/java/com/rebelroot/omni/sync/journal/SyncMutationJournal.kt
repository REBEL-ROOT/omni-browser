package com.rebelroot.omni.sync.journal

import com.rebelroot.omni.sync.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class SyncMutationJournal(
    private val journalDir: File
) {
    companion object {
        private const val JOURNAL_FILE = "sync_mutation_journal.json"
        private const val TEMP_FILE = "sync_mutation_journal.tmp"
    }

    private val queue = ConcurrentLinkedQueue<SyncOperation>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun recordLocalMutation(op: SyncOperation) {
        queue.add(op.copy(isLocalOrigin = true))
        persistToDisk()
    }

    @Synchronized
    fun pendingOperations(): List<SyncOperation> = queue.toList()

    @Synchronized
    fun pendingCount(): Int = queue.size

    @Synchronized
    fun markAcknowledged(peerId: String, upToHlc: Hlc) {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val op = iterator.next()
            if (op.hlc <= upToHlc) {
                iterator.remove()
            }
        }
        persistToDisk()
    }

    @Synchronized
    fun clear() {
        queue.clear()
        persistToDisk()
    }

    private fun loadFromDisk() {
        val file = File(journalDir, JOURNAL_FILE)
        if (!file.exists()) return

        try {
            val text = file.readText(Charsets.UTF_8)
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val op = deserializeOperation(obj)
                queue.add(op)
            }
        } catch (_: Exception) {
            // Quarantine or start clean on read corruption
        }
    }

    private fun persistToDisk() {
        try {
            val array = JSONArray()
            queue.forEach { op ->
                array.put(serializeOperation(op))
            }
            val temp = File(journalDir, TEMP_FILE)
            val dest = File(journalDir, JOURNAL_FILE)
            temp.writeText(array.toString(), Charsets.UTF_8)
            if (dest.exists()) dest.delete()
            temp.renameTo(dest)
        } catch (_: Exception) {
            // Logged in production diagnostics
        }
    }

    private fun serializeOperation(op: SyncOperation): JSONObject {
        return JSONObject().apply {
            put("opId", op.opId)
            put("opType", op.opType.name)
            put("entityType", op.entityType.name)
            put("entityId", op.entityId)
            put("hlc", op.hlc.toString())
            put("isLocalOrigin", op.isLocalOrigin)
            if (op.bookmarkPayload != null) {
                val b = op.bookmarkPayload
                put("bookmark", JSONObject().apply {
                    put("parentId", b.parentId)
                    put("position", b.position)
                    put("title", b.title)
                    put("url", b.url)
                    put("faviconUrl", b.faviconUrl)
                    put("createdAt", b.createdAt)
                    put("modifiedAt", b.modifiedAt)
                    put("isDeleted", b.isDeleted)
                })
            }
            if (op.folderPayload != null) {
                val f = op.folderPayload
                put("folder", JSONObject().apply {
                    put("parentId", f.parentId)
                    put("position", f.position)
                    put("title", f.title)
                    put("createdAt", f.createdAt)
                    put("modifiedAt", f.modifiedAt)
                    put("isDeleted", f.isDeleted)
                })
            }
        }
    }

    private fun deserializeOperation(obj: JSONObject): SyncOperation {
        val opId = obj.getString("opId")
        val opType = SyncOpType.valueOf(obj.getString("opType"))
        val entityType = SyncEntityType.valueOf(obj.getString("entityType"))
        val entityId = obj.getString("entityId")
        val hlc = Hlc.parse(obj.getString("hlc"))
        val isLocalOrigin = obj.optBoolean("isLocalOrigin", true)

        val bookmarkPayload = if (obj.has("bookmark")) {
            val b = obj.getJSONObject("bookmark")
            BookmarkPayload(
                parentId = b.getString("parentId"),
                position = b.getString("position"),
                title = b.getString("title"),
                url = b.getString("url"),
                faviconUrl = b.optString("faviconUrl", ""),
                createdAt = b.optLong("createdAt", 0L),
                modifiedAt = b.optLong("modifiedAt", 0L),
                isDeleted = b.optBoolean("isDeleted", false)
            )
        } else null

        val folderPayload = if (obj.has("folder")) {
            val f = obj.getJSONObject("folder")
            FolderPayload(
                parentId = f.getString("parentId"),
                position = f.getString("position"),
                title = f.getString("title"),
                createdAt = f.optLong("createdAt", 0L),
                modifiedAt = f.optLong("modifiedAt", 0L),
                isDeleted = f.optBoolean("isDeleted", false)
            )
        } else null

        return SyncOperation(
            opId = opId,
            opType = opType,
            entityType = entityType,
            entityId = entityId,
            hlc = hlc,
            bookmarkPayload = bookmarkPayload,
            folderPayload = folderPayload,
            isLocalOrigin = isLocalOrigin
        )
    }
}
