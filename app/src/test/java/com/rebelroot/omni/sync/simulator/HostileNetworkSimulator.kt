package com.rebelroot.omni.sync.simulator

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.conflict.ConflictEngine
import com.rebelroot.omni.sync.model.HlcClock
import com.rebelroot.omni.sync.model.SyncOperation
import com.rebelroot.omni.sync.storage.SyncStorage
import java.io.File
import java.util.Random

class SimulatedPeer(
    val deviceId: String,
    val baseDir: File,
    val clockSkewMs: Long = 0L
) {
    var physicalTime: Long = 1724330400000L + clockSkewMs
    val clock = HlcClock(deviceId) { physicalTime }
    val collection = BookmarkCollection { physicalTime }
    val adapter = BookmarkAdapter(clock)
    val storage = SyncStorage(baseDir, clock)
    val conflictEngine = ConflictEngine(adapter, storage)

    fun tick(advanceMs: Long = 10L) {
        physicalTime += advanceMs
    }

    fun restart(): SimulatedPeer {
        val reloaded = SimulatedPeer(deviceId, baseDir, clockSkewMs)
        reloaded.physicalTime = this.physicalTime
        return reloaded
    }
}

data class SimulatedPacket(
    val packetId: Long,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val operation: SyncOperation,
    val deliverAtTick: Long
)

class HostileNetworkSimulator(
    val peers: List<SimulatedPeer>,
    val seed: Long = 42L,
    val dropRate: Double = 0.15,
    val duplicateRate: Double = 0.20,
    val maxDelayTicks: Int = 10
) {
    private val random = Random(seed)
    private val packetQueue = mutableListOf<SimulatedPacket>()
    private var currentTick: Long = 0L
    private var packetCounter: Long = 0L

    fun broadcast(sender: SimulatedPeer, op: SyncOperation) {
        sender.storage.recordLocalMutation(op)
        sender.adapter.applyRemoteOperation(sender.collection, op)
        peers.filter { it.deviceId != sender.deviceId }.forEach { recipient ->
            schedulePacket(sender.deviceId, recipient.deviceId, op)
        }
    }

    private fun schedulePacket(from: String, to: String, op: SyncOperation) {
        if (random.nextDouble() < dropRate) {
            return
        }

        val delay = random.nextInt(maxDelayTicks + 1).toLong()
        val deliverTick = currentTick + delay
        packetQueue.add(SimulatedPacket(++packetCounter, from, to, op, deliverTick))

        if (random.nextDouble() < duplicateRate) {
            val dupDelay = delay + random.nextInt(3) + 1
            packetQueue.add(SimulatedPacket(++packetCounter, from, to, op, currentTick + dupDelay))
        }
    }

    fun step(): Int {
        currentTick++
        peers.forEach { it.tick(10L) }

        val deliverable = packetQueue.filter { it.deliverAtTick <= currentTick }
        if (deliverable.isEmpty()) return 0

        val shuffled = deliverable.shuffled(random)
        shuffled.forEach { packet ->
            val recipient = peers.find { it.deviceId == packet.recipientDeviceId }
            recipient?.conflictEngine?.processIncomingOperation(recipient.collection, packet.operation)
            packetQueue.remove(packet)
        }

        return shuffled.size
    }

    fun flushAll() {
        while (packetQueue.isNotEmpty()) {
            step()
        }
    }

    fun syncAllDirectly() {
        peers.forEach { sender ->
            val ops = sender.adapter.exportToOperations(sender.collection)
            peers.filter { it.deviceId != sender.deviceId }.forEach { recipient ->
                ops.forEach { op ->
                    recipient.conflictEngine.processIncomingOperation(recipient.collection, op)
                }
            }
        }
    }
}
