package com.rebelroot.omni.sync

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.coordinator.SyncCoordinator
import com.rebelroot.omni.sync.crypto.PairingResult
import com.rebelroot.omni.sync.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun syncCoordinator_pairingAndMutationPipeline() {
        val aliceDir = tempFolder.newFolder("alice_coord")
        val bobDir = tempFolder.newFolder("bob_coord")

        val aliceCollection = BookmarkCollection()
        val bobCollection = BookmarkCollection()

        val aliceCoordinator = SyncCoordinator(aliceDir, aliceCollection)
        val bobCoordinator = SyncCoordinator(bobDir, bobCollection)

        val aliceInvitation = aliceCoordinator.createPairingInvitation()
        val pairingResult = bobCoordinator.processPairingInvitation(aliceInvitation.toJson())

        assertTrue("Pairing result must be success", pairingResult is PairingResult.Success)
        val success = pairingResult as PairingResult.Success
        assertEquals(aliceCoordinator.keyManager.deviceId, success.trustedDevice.deviceId)
        assertEquals(6, success.sasCode.length)

        val bmk = aliceCollection.addBookmark("Omni Browser", "https://omnibrowser.app")
        val op = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = bmk.id,
            hlc = aliceCoordinator.clock.now(),
            bookmarkPayload = BookmarkPayload(title = bmk.title, url = bmk.url)
        )
        aliceCoordinator.onLocalBookmarkMutation(op)

        assertEquals(1, aliceCoordinator.storage.outboxCount())

        aliceCoordinator.shutdown()
        bobCoordinator.shutdown()
    }
}
