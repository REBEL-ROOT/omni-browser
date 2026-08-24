package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.transport.p2p.P2PTransportManager
import com.rebelroot.omni.sync.transport.p2p.SignalingPacket
import com.rebelroot.omni.sync.transport.p2p.SignalingType
import org.junit.Assert.*
import org.junit.Test

class P2PTransportTest {

    @Test
    fun signalingPacket_serializationAndRouting() {
        val manager = P2PTransportManager("dev_alice")

        val packet = SignalingPacket(
            type = SignalingType.OFFER,
            senderDeviceId = "dev_bob",
            targetDeviceId = "dev_alice",
            encryptedPayloadBase64 = "ZXhhbXBsZV9zZHA="
        )

        val json = packet.toJson()
        val parsed = SignalingPacket.fromJson(json)

        assertEquals(SignalingType.OFFER, parsed.type)
        assertEquals("dev_bob", parsed.senderDeviceId)
        assertEquals("dev_alice", parsed.targetDeviceId)

        val handled = manager.handleIncomingSignaling(parsed)
        assertTrue("Packet addressed to local device must be handled", handled)
        assertTrue("Peer must be marked active", manager.isPeerConnected("dev_bob"))
    }
}
