package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.crypto.DeviceKeyManager
import com.rebelroot.omni.sync.crypto.TrustManager
import com.rebelroot.omni.sync.crypto.TrustedDevice
import com.rebelroot.omni.sync.model.*
import com.rebelroot.omni.sync.transport.lan.LanFrame
import com.rebelroot.omni.sync.transport.lan.LanFrameType
import com.rebelroot.omni.sync.transport.lan.LanTransportServer
import com.rebelroot.omni.sync.transport.lan.LanTransportSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LanTransportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun lanFrame_roundtrip() {
        val frame = LanFrame(LanFrameType.HANDSHAKE_INIT, "{\"deviceId\":\"dev_123\"}")
        val out = ByteArrayOutputStream()
        LanFrame.writeToStream(frame, out)

        val bytes = out.toByteArray()
        val inp = ByteArrayInputStream(bytes)
        val readFrame = LanFrame.readFromStream(inp)

        assertEquals(LanFrameType.HANDSHAKE_INIT, readFrame.frameType)
        assertEquals("{\"deviceId\":\"dev_123\"}", readFrame.payloadJson)
    }

    @Test
    fun lanTransport_authenticatedClientServerHandshake_andEncryptedSync() {
        val aliceDir = tempFolder.newFolder("alice_lan")
        val bobDir = tempFolder.newFolder("bob_lan")

        val aliceKeys = DeviceKeyManager(aliceDir)
        val bobKeys = DeviceKeyManager(bobDir)

        val aliceTrust = TrustManager(aliceDir)
        val bobTrust = TrustManager(bobDir)

        // Establish mutual trust
        aliceTrust.addTrustedDevice(
            TrustedDevice(
                deviceId = bobKeys.deviceId,
                deviceName = bobKeys.deviceName,
                publicKeyBase64 = bobKeys.publicKeyBase64
            )
        )
        bobTrust.addTrustedDevice(
            TrustedDevice(
                deviceId = aliceKeys.deviceId,
                deviceName = aliceKeys.deviceName,
                publicKeyBase64 = aliceKeys.publicKeyBase64
            )
        )

        // Start Alice (Server) on random available local port
        val port = 19345
        val server = LanTransportServer(port, aliceKeys, aliceTrust)
        val serverSessionLatch = CountDownLatch(1)
        var serverSession: LanTransportSession? = null

        server.start { session ->
            serverSession = session
            serverSessionLatch.countDown()
        }

        // Start Bob (Client) connecting to Alice
        val clientSocket = Socket("127.0.0.1", port)
        val clientSession = LanTransportSession(clientSocket, bobKeys, bobTrust, isServer = false)

        val clientHandshakeSuccess = clientSession.performHandshake()
        assertTrue("Client handshake must succeed", clientHandshakeSuccess)

        val serverHandshakeSuccess = serverSessionLatch.await(5, TimeUnit.SECONDS)
        assertTrue("Server handshake callback must trigger", serverHandshakeSuccess)
        assertEquals(bobKeys.deviceId, serverSession?.remoteDeviceId)

        // Bob sends an encrypted bookmark operation to Alice
        val op = SyncOperation(
            opId = "op_lan_01",
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_lan_test",
            hlc = Hlc.initial(bobKeys.deviceId, 1000L),
            bookmarkPayload = BookmarkPayload(title = "LAN Bookmark", url = "https://lan.sync.org")
        )
        clientSession.sendSyncOperations(listOf(op))

        // Alice receives and decrypts the envelope
        val receivedEnvelope = serverSession?.receiveEncryptedEnvelope()
        assertNotNull("Server must receive encrypted envelope", receivedEnvelope)

        val decryptedBytes = serverSession?.decryptEnvelope(receivedEnvelope!!)
        assertNotNull("Decrypted bytes must not be null", decryptedBytes)
        val decryptedJson = String(decryptedBytes!!)
        assertTrue("Decrypted payload must contain bookmark info", decryptedJson.contains("LAN Bookmark"))

        // Cleanup
        clientSession.close()
        server.stop()
    }

    @Test
    fun lanTransport_rejectsUntrustedPeer() {
        val aliceDir = tempFolder.newFolder("alice_untrusted")
        val eveDir = tempFolder.newFolder("eve_untrusted")

        val aliceKeys = DeviceKeyManager(aliceDir)
        val eveKeys = DeviceKeyManager(eveDir)

        val aliceTrust = TrustManager(aliceDir) // Eve is NOT in Alice trust list!
        val eveTrust = TrustManager(eveDir)

        val port = 19346
        val server = LanTransportServer(port, aliceKeys, aliceTrust)
        server.start()

        val eveSocket = Socket("127.0.0.1", port)
        val eveSession = LanTransportSession(eveSocket, eveKeys, eveTrust, isServer = false)

        // Handshake should fail or be rejected because Alice does not trust Eve
        val handshakeResult = eveSession.performHandshake()
        assertFalse("Handshake with untrusted peer must be rejected", handshakeResult)

        eveSession.close()
        server.stop()
    }
}
