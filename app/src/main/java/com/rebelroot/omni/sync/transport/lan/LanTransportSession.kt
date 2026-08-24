package com.rebelroot.omni.sync.transport.lan

import com.rebelroot.omni.sync.crypto.*
import com.rebelroot.omni.sync.model.SyncOperation
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import javax.crypto.SecretKey

class LanTransportSession(
    private val socket: Socket,
    private val keyManager: DeviceKeyManager,
    private val trustManager: TrustManager,
    private val isServer: Boolean
) {
    private val inputStream: InputStream = socket.getInputStream()
    private val outputStream: OutputStream = socket.getOutputStream()

    var remoteDeviceId: String? = null
    private var sessionSecretKey: SecretKey? = null
    private var sequenceCounter = 0L

    fun performHandshake(): Boolean {
        return try {
            if (isServer) {
                val initFrame = LanFrame.readFromStream(inputStream)
                if (initFrame.frameType != LanFrameType.HANDSHAKE_INIT) return false
                val initObj = JSONObject(initFrame.payloadJson)
                val peerDeviceId = initObj.getString("deviceId")
                val peerPubKeyBase64 = initObj.getString("publicKey")

                if (!trustManager.isDeviceTrusted(peerDeviceId)) return false

                val peerPubKey = CryptoEngine.parsePublicKeyBase64(peerPubKeyBase64)
                val secret = CryptoEngine.deriveSharedSecret(keyManager.keyPair.private, peerPubKey)
                sessionSecretKey = secret
                remoteDeviceId = peerDeviceId

                val respObj = JSONObject().apply {
                    put("deviceId", keyManager.deviceId)
                    put("publicKey", keyManager.publicKeyBase64)
                }
                LanFrame.writeToStream(LanFrame(LanFrameType.HANDSHAKE_RESP, respObj.toString()), outputStream)
                true
            } else {
                val initObj = JSONObject().apply {
                    put("deviceId", keyManager.deviceId)
                    put("publicKey", keyManager.publicKeyBase64)
                }
                LanFrame.writeToStream(LanFrame(LanFrameType.HANDSHAKE_INIT, initObj.toString()), outputStream)

                val respFrame = LanFrame.readFromStream(inputStream)
                if (respFrame.frameType != LanFrameType.HANDSHAKE_RESP) return false
                val respObj = JSONObject(respFrame.payloadJson)
                val peerDeviceId = respObj.getString("deviceId")
                val peerPubKeyBase64 = respObj.getString("publicKey")

                if (!trustManager.isDeviceTrusted(peerDeviceId)) return false

                val peerPubKey = CryptoEngine.parsePublicKeyBase64(peerPubKeyBase64)
                val secret = CryptoEngine.deriveSharedSecret(keyManager.keyPair.private, peerPubKey)
                sessionSecretKey = secret
                remoteDeviceId = peerDeviceId
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun sendSyncOperations(operations: List<SyncOperation>) {
        val secret = sessionSecretKey ?: return
        val opsArray = JSONArray()
        operations.forEach { op ->
            opsArray.put(JSONObject().apply {
                put("opId", op.opId)
                put("opType", op.opType.name)
                put("entityType", op.entityType.name)
                put("entityId", op.entityId)
                put("hlc", op.hlc.toString())
                if (op.bookmarkPayload != null) {
                    val b = op.bookmarkPayload
                    put("bookmark", JSONObject().apply {
                        put("parentId", b.parentId)
                        put("position", b.position)
                        put("title", b.title)
                        put("url", b.url)
                    })
                }
            })
        }
        val rawJson = opsArray.toString().toByteArray(Charsets.UTF_8)
        val seq = ++sequenceCounter
        val envelope = CryptoEngine.encryptPayload(rawJson, secret, seq, keyManager.deviceId)

        val envJson = JSONObject().apply {
            put("senderDeviceId", envelope.senderDeviceId)
            put("sequenceNumber", envelope.sequenceNumber)
            put("iv", envelope.ivBase64)
            put("ciphertext", envelope.ciphertextBase64)
            put("timestamp", envelope.timestamp)
        }.toString()

        LanFrame.writeToStream(LanFrame(LanFrameType.ENCRYPTED_SYNC_MSG, envJson), outputStream)
    }

    fun receiveEncryptedEnvelope(): EncryptedEnvelope? {
        return try {
            val frame = LanFrame.readFromStream(inputStream)
            if (frame.frameType != LanFrameType.ENCRYPTED_SYNC_MSG) return null
            val obj = JSONObject(frame.payloadJson)
            EncryptedEnvelope(
                senderDeviceId = obj.getString("senderDeviceId"),
                sequenceNumber = obj.getLong("sequenceNumber"),
                ivBase64 = obj.getString("iv"),
                ciphertextBase64 = obj.getString("ciphertext"),
                timestamp = obj.getLong("timestamp")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decryptEnvelope(envelope: EncryptedEnvelope): ByteArray? {
        val secret = sessionSecretKey ?: return null
        return try {
            CryptoEngine.decryptPayload(envelope, secret)
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
