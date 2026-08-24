package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.model.*
import com.rebelroot.omni.sync.storage.IngestResult
import com.rebelroot.omni.sync.storage.SyncStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun syncStorage_recoversFullStateAfterProcessRestart() {
        val dir = tempFolder.newFolder()
        val clock = HlcClock("dev_local_01")
        val storage1 = SyncStorage(dir, clock)

        val op = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_01",
            hlc = Hlc.initial("dev_local_01", 1000L),
            bookmarkPayload = BookmarkPayload(title = "Omni", url = "https://omnibrowser.app")
        )
        storage1.recordLocalMutation(op)
        assertEquals(1, storage1.outboxCount())

        // Recreate storage (restart)
        val storage2 = SyncStorage(dir, clock)
        assertEquals(1, storage2.outboxCount())
        val recovered = storage2.pendingOutboxOperations().first()
        assertEquals("bmk_01", recovered.entityId)
        assertEquals("Omni", recovered.bookmarkPayload?.title)
    }

    @Test
    fun syncStorage_deduplicatesIncomingOperationsViaInbox() {
        val dir = tempFolder.newFolder()
        val clock = HlcClock("dev_local_01")
        val storage = SyncStorage(dir, clock)

        val op = SyncOperation(
            opId = "op_remote_unique_123",
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_remote_01",
            hlc = Hlc.initial("dev_remote_01", 1000L),
            bookmarkPayload = BookmarkPayload(title = "Test", url = "https://test.com")
        )

        // First delivery: eligible
        val res1 = storage.checkIncomingEligibility(op)
        assertEquals(IngestResult.APPLIED, res1)
        storage.markIncomingApplied(op)

        // Second delivery (duplicate replay): rejected
        val res2 = storage.checkIncomingEligibility(op)
        assertEquals(IngestResult.DUPLICATE_IGNORED, res2)
    }

    @Test
    fun syncStorage_tombstonePreventsStaleResurrection() {
        val dir = tempFolder.newFolder()
        val clock = HlcClock("dev_local_01")
        val storage = SyncStorage(dir, clock)

        // Delete operation at HLC 2000
        val delOp = SyncOperation(
            opId = "op_del_01",
            opType = SyncOpType.DELETE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_victim_01",
            hlc = Hlc(2000L, 0, "dev_remote_01")
        )
        storage.markIncomingApplied(delOp)
        assertTrue(storage.isTombstoned("bmk_victim_01"))

        // Stale edit arrives from earlier time (HLC 1000)
        val staleEdit = SyncOperation(
            opId = "op_stale_edit_01",
            opType = SyncOpType.UPDATE_CONTENT,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_victim_01",
            hlc = Hlc(1000L, 0, "dev_peer_02"),
            bookmarkPayload = BookmarkPayload(title = "Old Edit")
        )
        val res = storage.checkIncomingEligibility(staleEdit)
        assertEquals(IngestResult.STALE_TOMBSTONE_IGNORED, res)
    }

    @Test
    fun syncStorage_multiPeerAcknowledgementPreservesOperationsUntilAllPeersAck() {
        val dir = tempFolder.newFolder()
        val clock = HlcClock("dev_local_01")
        val storage = SyncStorage(dir, clock)

        val op1 = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_01",
            hlc = Hlc(1000L, 0, "dev_local_01")
        )
        val op2 = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_02",
            hlc = Hlc(2000L, 0, "dev_local_01")
        )
        storage.recordLocalMutation(op1)
        storage.recordLocalMutation(op2)
        assertEquals(2, storage.outboxCount())

        val activePeers = setOf("peer_desktop_chrome", "peer_android_tablet")

        // Peer 1 acks op1
        storage.recordPeerAck("peer_desktop_chrome", Hlc(1000L, 0, "dev_local_01"), activePeers)
        // Both ops should still be in outbox because peer 2 has not acked yet
        assertEquals(2, storage.outboxCount())

        // Peer 2 acks op1
        storage.recordPeerAck("peer_android_tablet", Hlc(1000L, 0, "dev_local_01"), activePeers)
        // Now op1 is purged because all active peers acked it
        assertEquals(1, storage.outboxCount())
        assertEquals("bmk_02", storage.pendingOutboxOperations().first().entityId)
    }

    @Test
    fun syncStorage_quarantinesCorruptRecords() {
        val dir = tempFolder.newFolder()
        val clock = HlcClock("dev_local_01")
        val storage = SyncStorage(dir, clock)

        storage.quarantineInvalidRecord("rec_bad_01", "{malformed json...}", "JSON syntax error")
        assertEquals(1, storage.allQuarantined().size)
        assertEquals("rec_bad_01", storage.allQuarantined().first().recordId)
    }
}
