package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.journal.SyncMutationJournal
import com.rebelroot.omni.sync.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncMutationJournalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun journal_recordsAndPersistsOperationsAcrossRestarts() {
        val dir = tempFolder.newFolder()
        val journal1 = SyncMutationJournal(dir)

        val op = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_01",
            hlc = Hlc.initial("dev_01", 1000L),
            bookmarkPayload = BookmarkPayload(title = "Test", url = "https://test.com")
        )
        journal1.recordLocalMutation(op)
        assertEquals(1, journal1.pendingCount())

        // Recreate journal from disk (simulating process restart)
        val journal2 = SyncMutationJournal(dir)
        assertEquals(1, journal2.pendingCount())
        val recoveredOp = journal2.pendingOperations().first()
        assertEquals("bmk_01", recoveredOp.entityId)
        assertEquals("Test", recoveredOp.bookmarkPayload?.title)
    }

    @Test
    fun journal_acknowledgementPurgesOlderOperations() {
        val dir = tempFolder.newFolder()
        val journal = SyncMutationJournal(dir)

        val op1 = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_01",
            hlc = Hlc(1000L, 0, "dev_01")
        )
        val op2 = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_02",
            hlc = Hlc(2000L, 0, "dev_01")
        )
        journal.recordLocalMutation(op1)
        journal.recordLocalMutation(op2)
        assertEquals(2, journal.pendingCount())

        journal.markAcknowledged("peer_01", Hlc(1000L, 0, "dev_01"))
        assertEquals(1, journal.pendingCount())
        assertEquals("bmk_02", journal.pendingOperations().first().entityId)
    }
}
