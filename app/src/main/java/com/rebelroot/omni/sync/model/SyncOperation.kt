package com.rebelroot.omni.sync.model

import java.util.UUID

enum class SyncOpType {
    CREATE,
    UPDATE_CONTENT,
    MOVE_REORDER,
    DELETE,
    SNAPSHOT_BOOTSTRAP
}

enum class SyncEntityType {
    BOOKMARK,
    FOLDER,
    TAB,
    HISTORY,
    SETTING
}

data class Hlc(
    val physicalTime: Long,
    val counter: Int,
    val deviceId: String
) : Comparable<Hlc> {

    override fun compareTo(other: Hlc): Int {
        if (this.physicalTime != other.physicalTime) {
            return this.physicalTime.compareTo(other.physicalTime)
        }
        if (this.counter != other.counter) {
            return this.counter.compareTo(other.counter)
        }
        return this.deviceId.compareTo(other.deviceId)
    }

    override fun toString(): String = "$physicalTime:$counter:$deviceId"

    companion object {
        fun parse(str: String): Hlc {
            val parts = str.split(":")
            require(parts.size == 3) { "Invalid HLC string format: $str" }
            return Hlc(
                physicalTime = parts[0].toLong(),
                counter = parts[1].toInt(),
                deviceId = parts[2]
            )
        }

        fun initial(deviceId: String, physicalTime: Long = System.currentTimeMillis()): Hlc =
            Hlc(physicalTime, 0, deviceId)
    }
}

class HlcClock(
    val deviceId: String,
    private val physicalClock: () -> Long = System::currentTimeMillis
) {
    private var lastHlc = Hlc.initial(deviceId, physicalClock())

    @Synchronized
    fun now(): Hlc {
        val nowPhysical = physicalClock()
        val nextHlc = if (nowPhysical > lastHlc.physicalTime) {
            Hlc(nowPhysical, 0, deviceId)
        } else {
            Hlc(lastHlc.physicalTime, lastHlc.counter + 1, deviceId)
        }
        lastHlc = nextHlc
        return nextHlc
    }

    @Synchronized
    fun update(remoteHlc: Hlc): Hlc {
        val nowPhysical = physicalClock()
        val maxPhysical = maxOf(nowPhysical, lastHlc.physicalTime, remoteHlc.physicalTime)
        val nextCounter = when {
            maxPhysical == lastHlc.physicalTime && maxPhysical == remoteHlc.physicalTime ->
                maxOf(lastHlc.counter, remoteHlc.counter) + 1
            maxPhysical == lastHlc.physicalTime -> lastHlc.counter + 1
            maxPhysical == remoteHlc.physicalTime -> remoteHlc.counter + 1
            else -> 0
        }
        val nextHlc = Hlc(maxPhysical, nextCounter, deviceId)
        lastHlc = nextHlc
        return nextHlc
    }
}

data class BookmarkPayload(
    val parentId: String = "root",
    val position: String = "a0",
    val title: String = "",
    val url: String = "",
    val faviconUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class FolderPayload(
    val parentId: String = "root",
    val position: String = "a0",
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class SyncOperation(
    val opId: String = UUID.randomUUID().toString(),
    val opType: SyncOpType,
    val entityType: SyncEntityType,
    val entityId: String,
    val hlc: Hlc,
    val bookmarkPayload: BookmarkPayload? = null,
    val folderPayload: FolderPayload? = null,
    val isLocalOrigin: Boolean = true
)
