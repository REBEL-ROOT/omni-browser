package com.rebelroot.omni.sync

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.bootstrap.BootstrapEngine
import com.rebelroot.omni.sync.crypto.DeviceKeyManager
import com.rebelroot.omni.sync.crypto.TrustManager
import com.rebelroot.omni.sync.crypto.TrustedDevice
import com.rebelroot.omni.sync.mesh.DeviceMeshManager
import com.rebelroot.omni.sync.model.HlcClock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BootstrapAndMeshTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun bootstrapSnapshot_fastForwardsNewDevice() {
        val aliceClock = HlcClock("dev_alice")
        val aliceAdapter = BookmarkAdapter(aliceClock)
        val aliceEngine = BootstrapEngine(aliceClock, aliceAdapter)

        val aliceCollection = BookmarkCollection()
        val fld = aliceCollection.addFolder("Work")
        aliceCollection.addBookmark("Omni Docs", "https://docs.omnibrowser.app", fld.id)
        aliceCollection.addBookmark("RebelRoot", "https://rebelroot.com")

        // Alice generates complete snapshot
        val snapshot = aliceEngine.generateSnapshot(aliceCollection)
        assertEquals(1, snapshot.folders.size)
        assertEquals(2, snapshot.bookmarks.size)

        // Bob (fresh device) applies snapshot
        val bobClock = HlcClock("dev_bob")
        val bobAdapter = BookmarkAdapter(bobClock)
        val bobEngine = BootstrapEngine(bobClock, bobAdapter)
        val bobCollection = BookmarkCollection()

        bobEngine.applySnapshot(bobCollection, snapshot)

        assertEquals("Bob must have 1 folder", 1, bobCollection.allFolders().size)
        assertEquals("Bob must have 2 bookmarks", 2, bobCollection.allBookmarks().size)
        assertEquals("Work", bobCollection.allFolders()[0].title)
    }

    @Test
    fun meshManager_handlesCheckpointsAndRevocation() {
        val testDir = tempFolder.newFolder("mesh_test")
        val trust = TrustManager(testDir)
        val mesh = DeviceMeshManager("dev_local", trust)

        trust.addTrustedDevice(TrustedDevice("dev_peer_01", "Peer 01", "pubKey123"))
        assertTrue(mesh.isDeviceAuthorized("dev_peer_01"))

        mesh.updatePeerCheckpoint("dev_peer_01", 1000L)
        assertEquals(1000L, mesh.getPeerCheckpoint("dev_peer_01"))

        // Revocation
        mesh.processRevocationEvent("dev_peer_01")
        assertFalse("Revoked device must not be authorized", mesh.isDeviceAuthorized("dev_peer_01"))
        assertEquals(0L, mesh.getPeerCheckpoint("dev_peer_01"))
    }
}
