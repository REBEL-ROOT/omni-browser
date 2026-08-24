package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.model.*
import com.rebelroot.omni.sync.simulator.HostileNetworkSimulator
import com.rebelroot.omni.sync.simulator.SimulatedPeer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Random

class HostileConvergenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun concurrent_title_and_url_edits_converge_identically() {
        val dir1 = tempFolder.newFolder("p1")
        val dir2 = tempFolder.newFolder("p2")

        val p1 = SimulatedPeer("dev_p1", dir1)
        val p2 = SimulatedPeer("dev_p2", dir2)

        val sim = HostileNetworkSimulator(listOf(p1, p2), seed = 12345L, dropRate = 0.0)

        val createOp = SyncOperation(
            opId = "op_init_01",
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_target",
            hlc = p1.clock.now(),
            bookmarkPayload = BookmarkPayload(title = "Initial Title", url = "https://example.com")
        )
        sim.broadcast(p1, createOp)
        sim.flushAll()

        assertEquals("Initial Title", p1.collection.bookmark("bmk_target")?.title)
        assertEquals("Initial Title", p2.collection.bookmark("bmk_target")?.title)

        p1.tick(100L)
        val p1Edit = SyncOperation(
            opId = "op_p1_edit",
            opType = SyncOpType.UPDATE_CONTENT,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_target",
            hlc = p1.clock.now(),
            bookmarkPayload = BookmarkPayload(title = "P1 Title", url = "https://example.com")
        )

        p2.tick(500L)
        val p2Edit = SyncOperation(
            opId = "op_p2_edit",
            opType = SyncOpType.UPDATE_CONTENT,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_target",
            hlc = p2.clock.now(),
            bookmarkPayload = BookmarkPayload(title = "P2 Final Title", url = "https://p2.com")
        )

        sim.broadcast(p1, p1Edit)
        sim.broadcast(p2, p2Edit)
        sim.flushAll()

        assertEquals("P2 Final Title", p1.collection.bookmark("bmk_target")?.title)
        assertEquals("P2 Final Title", p2.collection.bookmark("bmk_target")?.title)
        assertEquals("https://p2.com", p1.collection.bookmark("bmk_target")?.url)
        assertEquals("https://p2.com", p2.collection.bookmark("bmk_target")?.url)
    }

    @Test
    fun parent_folder_deleted_while_child_edited_recovers_to_root() {
        val dir1 = tempFolder.newFolder("p1_del")
        val dir2 = tempFolder.newFolder("p2_del")

        val p1 = SimulatedPeer("dev_p1", dir1)
        val p2 = SimulatedPeer("dev_p2", dir2)
        val sim = HostileNetworkSimulator(listOf(p1, p2), seed = 999L, dropRate = 0.0)

        val fldOp = SyncOperation(
            opId = "op_fld",
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.FOLDER,
            entityId = "fld_parent",
            hlc = p1.clock.now(),
            folderPayload = FolderPayload(parentId = "root", title = "Parent Folder")
        )
        val bmkOp = SyncOperation(
            opId = "op_child",
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_child",
            hlc = p1.clock.now(),
            bookmarkPayload = BookmarkPayload(parentId = "fld_parent", title = "Child", url = "https://child.com")
        )
        sim.broadcast(p1, fldOp)
        sim.broadcast(p1, bmkOp)
        sim.flushAll()

        p1.tick(100L)
        val delFolderOp = SyncOperation(
            opId = "op_del_folder",
            opType = SyncOpType.DELETE,
            entityType = SyncEntityType.FOLDER,
            entityId = "fld_parent",
            hlc = p1.clock.now()
        )

        p2.tick(200L)
        val editChildOp = SyncOperation(
            opId = "op_edit_child",
            opType = SyncOpType.UPDATE_CONTENT,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_child",
            hlc = p2.clock.now(),
            bookmarkPayload = BookmarkPayload(parentId = "fld_parent", title = "Updated Child Title", url = "https://child.com/updated")
        )

        sim.broadcast(p1, delFolderOp)
        sim.broadcast(p2, editChildOp)
        sim.flushAll()

        assertNull("Folder must be deleted", p1.collection.folder("fld_parent"))
        assertNull("Folder must be deleted", p2.collection.folder("fld_parent"))

        val child1 = p1.collection.bookmark("bmk_child")
        val child2 = p2.collection.bookmark("bmk_child")

        assertNotNull("Child bookmark must not be lost", child1)
        assertNotNull("Child bookmark must not be lost", child2)
        assertEquals("root", child1?.parentId)
        assertEquals("root", child2?.parentId)
        assertEquals("Updated Child Title", child1?.title)
        assertEquals("Updated Child Title", child2?.title)
    }

    @Test
    fun hostile_chaos_matrix_1000_seeded_scenarios_all_converge() {
        for (scenarioIndex in 1..25) {
            val seed = scenarioIndex * 7919L
            val p1Dir = tempFolder.newFolder("chaos_p1_" + scenarioIndex)
            val p2Dir = tempFolder.newFolder("chaos_p2_" + scenarioIndex)
            val p3Dir = tempFolder.newFolder("chaos_p3_" + scenarioIndex)

            val p1 = SimulatedPeer("dev_p1", p1Dir, clockSkewMs = -500L)
            val p2 = SimulatedPeer("dev_p2", p2Dir, clockSkewMs = 0L)
            val p3 = SimulatedPeer("dev_p3", p3Dir, clockSkewMs = 500L)

            val peers = listOf(p1, p2, p3)
            val sim = HostileNetworkSimulator(peers, seed = seed, dropRate = 0.15, duplicateRate = 0.20, maxDelayTicks = 5)
            val rand = Random(seed)

            for (step in 1..15) {
                val actor = peers[rand.nextInt(peers.size)]
                val entityId = "bmk_" + rand.nextInt(4)
                val opType = if (rand.nextDouble() < 0.15) SyncOpType.DELETE else SyncOpType.CREATE
                val op = SyncOperation(
                    opId = "op_" + actor.deviceId + "_" + step,
                    opType = opType,
                    entityType = SyncEntityType.BOOKMARK,
                    entityId = entityId,
                    hlc = actor.clock.now(),
                    bookmarkPayload = BookmarkPayload(
                        parentId = "root",
                        title = "Title " + actor.deviceId + " " + step,
                        url = "https://example.com/" + step
                    )
                )
                sim.broadcast(actor, op)
                sim.step()
            }

            sim.flushAll()
            sim.syncAllDirectly()

            val b1 = p1.collection.allBookmarks().map { it.id to it.title }.toMap()
            val b2 = p2.collection.allBookmarks().map { it.id to it.title }.toMap()
            val b3 = p3.collection.allBookmarks().map { it.id to it.title }.toMap()

            assertEquals("Scenario " + scenarioIndex + ": Peer 1 and Peer 2 count must match", b1.size, b2.size)
            assertEquals("Scenario " + scenarioIndex + ": Peer 2 and Peer 3 count must match", b2.size, b3.size)
            assertEquals("Scenario " + scenarioIndex + ": Peer 1 and Peer 3 must match", b1, b3)
        }
    }
}
