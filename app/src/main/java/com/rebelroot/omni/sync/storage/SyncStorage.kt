package com.rebelroot.omni.sync.storage

import com.rebelroot.omni.sync.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class TombstoneRecord(
    val entityId: String,
    val entityType: SyncEntityType,
    val deletedAtHlc: Hlc,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuarantinedRecord(
    val recordId: String,
    val rawPayload: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class IngestResult {
    APPLIED,
    DUPLICATE_IGNORED,
    STALE_TOMBSTONE_IGNORED,
    STALE_UPDATE_IGNORED,
    QUARANTINED,
    FAILED
}

class SyncStorage(
    private val storageDir: File,
    private val clock: HlcClock
) {
    companion object {
        private const val STORAGE_FILE = "sync_state_v1.json"
        private const val TEMP_FILE = "sync_state_v1.tmp"
        const val CURRENT_SCHEMA_VERSION = 1
        const val TOMBSTONE_TTL_MS = 30L * 24 * 3600 * 1000L // 30 days
        const val MAX_INBOX_SIZE = 5000
        const val MAX_OUTBOX_SIZE = 5000
    }

    private val outbox = ConcurrentLinkedQueue<SyncOperation>()
    private val inboxProcessedIds = ConcurrentHashMap<String, Long>()
    private val peerCheckpoints = ConcurrentHashMap<String, Hlc>()
    private val tombstones = ConcurrentHashMap<String, TombstoneRecord>()
    private val entityHlcs = ConcurrentHashMap<String, Hlc>()
    private val quarantined = ConcurrentLinkedQueue<QuarantinedRecord>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun recordLocalMutation(op: SyncOperation) {
        entityHlcs[op.entityId] = op.hlc
        if (op.opType == SyncOpType.DELETE) {
            tombstones[op.entityId] = TombstoneRecord(
                entityId = op.entityId,
                entityType = op.entityType,
                deletedAtHlc = op.hlc
            )
        }
        outbox.add(op.copy(isLocalOrigin = true))
        if (outbox.size > MAX_OUTBOX_SIZE) {
            compactOutbox()
        }
        persistToDisk()
    }

    @Synchronized
    fun pendingOutboxOperations(): List<SyncOperation> = outbox.toList()

    @Synchronized
    fun outboxCount(): Int = outbox.size

    @Synchronized
    fun recordPeerAck(peerDeviceId: String, ackedHlc: Hlc, activePeerIds: Set<String>) {
        peerCheckpoints[peerDeviceId] = ackedHlc
        if (activePeerIds.isNotEmpty() && activePeerIds.all { peerCheckpoints.containsKey(it) }) {
            val minAckedHlc = activePeerIds.mapNotNull { peerCheckpoints[it] }.minOrNull()
            if (minAckedHlc != null) {
                val iter = outbox.iterator()
                while (iter.hasNext()) {
                    val op = iter.next()
                    if (op.hlc <= minAckedHlc) {
                        iter.remove()
                    }
                }
            }
        }
        persistToDisk()
    }

    @Synchronized
    fun checkIncomingEligibility(op: SyncOperation): IngestResult {
        if (inboxProcessedIds.containsKey(op.opId)) {
            return IngestResult.DUPLICATE_IGNORED
        }

        val tombstone = tombstones[op.entityId]
        if (tombstone != null && op.hlc <= tombstone.deletedAtHlc) {
            return IngestResult.STALE_TOMBSTONE_IGNORED
        }

        val currentHlc = entityHlcs[op.entityId]
        if (currentHlc != null && op.hlc < currentHlc) {
            return IngestResult.STALE_UPDATE_IGNORED
        }

        return IngestResult.APPLIED
    }

    @Synchronized
    fun markIncomingApplied(op: SyncOperation) {
        inboxProcessedIds[op.opId] = System.currentTimeMillis()
        entityHlcs[op.entityId] = op.hlc
        if (op.opType == SyncOpType.DELETE) {
            tombstones[op.entityId] = TombstoneRecord(
                entityId = op.entityId,
                entityType = op.entityType,
                deletedAtHlc = op.hlc
            )
        }
        if (inboxProcessedIds.size > MAX_INBOX_SIZE) {
            compactInbox()
        }
        persistToDisk()
    }

    @Synchronized
    fun getEntityHlc(entityId: String): Hlc? = entityHlcs[entityId]

    @Synchronized
    fun setEntityHlc(entityId: String, hlc: Hlc) {
        entityHlcs[entityId] = hlc
    }

    @Synchronized
    fun quarantineInvalidRecord(recordId: String, rawPayload: String, reason: String) {
        quarantined.add(QuarantinedRecord(recordId, rawPayload, reason))
        persistToDisk()
    }

    @Synchronized
    fun compact(now: Long = System.currentTimeMillis()) {
        val tombstoneIter = tombstones.entries.iterator()
        while (tombstoneIter.hasNext()) {
            val entry = tombstoneIter.next()
            if (now - entry.value.timestamp > TOMBSTONE_TTL_MS) {
                tombstoneIter.remove()
            }
        }
        compactInbox()
        compactOutbox()
        persistToDisk()
    }

    private fun compactInbox() {
        if (inboxProcessedIds.size <= MAX_INBOX_SIZE) return
        val sorted = inboxProcessedIds.entries.sortedBy { it.value }
        val toRemove = sorted.take(inboxProcessedIds.size - MAX_INBOX_SIZE)
        toRemove.forEach { inboxProcessedIds.remove(it.key) }
    }

    private fun compactOutbox() {
        while (outbox.size > MAX_OUTBOX_SIZE) {
            outbox.poll()
        }
    }

    fun isTombstoned(entityId: String): Boolean = tombstones.containsKey(entityId)
    fun getTombstone(entityId: String): TombstoneRecord? = tombstones[entityId]
    fun allTombstones(): List<TombstoneRecord> = tombstones.values.toList()
    fun allQuarantined(): List<QuarantinedRecord> = quarantined.toList()
    fun peerCheckpoint(peerId: String): Hlc? = peerCheckpoints[peerId]

    private fun loadFromDisk() {
        val file = File(storageDir, STORAGE_FILE)
        if (!file.exists()) return

        try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val schemaVersion = json.optInt("schemaVersion", 1)
            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                quarantineInvalidRecord("schema_future", json.toString(), "Unsupported schemaVersion: " + schemaVersion)
                return
            }

            val outboxArray = json.optJSONArray("outbox") ?: JSONArray()
            for (i in 0 until outboxArray.length()) {
                val opObj = outboxArray.getJSONObject(i)
                outbox.add(deserializeOperation(opObj))
            }

            val inboxArray = json.optJSONArray("inbox") ?: JSONArray()
            for (i in 0 until inboxArray.length()) {
                val item = inboxArray.getJSONObject(i)
                inboxProcessedIds[item.getString("opId")] = item.getLong("processedAt")
            }

            val peerObj = json.optJSONObject("peerCheckpoints") ?: JSONObject()
            peerObj.keys().forEach { peerId ->
                peerCheckpoints[peerId] = Hlc.parse(peerObj.getString(peerId))
            }

            val tombstoneArray = json.optJSONArray("tombstones") ?: JSONArray()
            for (i in 0 until tombstoneArray.length()) {
                val tObj = tombstoneArray.getJSONObject(i)
                val entityId = tObj.getString("entityId")
                tombstones[entityId] = TombstoneRecord(
                    entityId = entityId,
                    entityType = SyncEntityType.valueOf(tObj.getString("entityType")),
                    deletedAtHlc = Hlc.parse(tObj.getString("deletedAtHlc")),
                    timestamp = tObj.optLong("timestamp", System.currentTimeMillis())
                )
            }

            val hlcsObj = json.optJSONObject("entityHlcs") ?: JSONObject()
            hlcsObj.keys().forEach { entityId ->
                entityHlcs[entityId] = Hlc.parse(hlcsObj.getString(entityId))
            }

            val quarantinedArray = json.optJSONArray("quarantined") ?: JSONArray()
            for (i in 0 until quarantinedArray.length()) {
                val qObj = quarantinedArray.getJSONObject(i)
                quarantined.add(
                    QuarantinedRecord(
                        recordId = qObj.getString("recordId"),
                        rawPayload = qObj.getString("rawPayload"),
                        reason = qObj.getString("reason"),
                        timestamp = qObj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            quarantineInvalidRecord("corrupted_state_file", "", "Failed to read sync storage: " + e.message)
        }
    }

    private fun persistToDisk() {
        try {
            val json = JSONObject().apply {
                put("schemaVersion", CURRENT_SCHEMA_VERSION)

                val outboxArray = JSONArray()
                outbox.forEach { outboxArray.put(serializeOperation(it)) }
                put("outbox", outboxArray)

                val inboxArray = JSONArray()
                inboxProcessedIds.forEach { (opId, processedAt) ->
                    inboxArray.put(JSONObject().apply {
                        put("opId", opId)
                        put("processedAt", processedAt)
                    })
                }
                put("inbox", inboxArray)

                val peerObj = JSONObject()
                peerCheckpoints.forEach { (peerId, hlc) ->
                    peerObj.put(peerId, hlc.toString())
                }
                put("peerCheckpoints", peerObj)

                val tombstoneArray = JSONArray()
                tombstones.values.forEach { t ->
                    tombstoneArray.put(JSONObject().apply {
                        put("entityId", t.entityId)
                        put("entityType", t.entityType.name)
                        put("deletedAtHlc", t.deletedAtHlc.toString())
                        put("timestamp", t.timestamp)
                    })
                }
                put("tombstones", tombstoneArray)

                val hlcsObj = JSONObject()
                entityHlcs.forEach { (entityId, hlc) ->
                    hlcsObj.put(entityId, hlc.toString())
                }
                put("entityHlcs", hlcsObj)

                val quarantinedArray = JSONArray()
                quarantined.forEach { q ->
                    quarantinedArray.put(JSONObject().apply {
                        put("recordId", q.recordId)
                        put("rawPayload", q.rawPayload)
                        put("reason", q.reason)
                        put("timestamp", q.timestamp)
                    })
                }
                put("quarantined", quarantinedArray)
            }

            val temp = File(storageDir, TEMP_FILE)
            val dest = File(storageDir, STORAGE_FILE)
            temp.writeText(json.toString(), Charsets.UTF_8)
            if (dest.exists()) dest.delete()
            temp.renameTo(dest)
        } catch (_: Exception) {
            // Handled
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
