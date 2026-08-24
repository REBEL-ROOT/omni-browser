package com.rebelroot.omni.sync.transport.p2p

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class SignalingType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    RELAY_MSG
}

data class SignalingPacket(
    val type: SignalingType,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val encryptedPayloadBase64: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("type", type.name)
            put("senderDeviceId", senderDeviceId)
            put("targetDeviceId", targetDeviceId)
            put("encryptedPayloadBase64", encryptedPayloadBase64)
            put("timestamp", timestamp)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SignalingPacket {
            val obj = JSONObject(jsonStr)
            return SignalingPacket(
                type = SignalingType.valueOf(obj.getString("type")),
                senderDeviceId = obj.getString("senderDeviceId"),
                targetDeviceId = obj.getString("targetDeviceId"),
                encryptedPayloadBase64 = obj.getString("encryptedPayloadBase64"),
                timestamp = obj.getLong("timestamp")
            )
        }
    }
}

class P2PTransportManager(
    val localDeviceId: String
) {
    private val activePeers = ConcurrentHashMap<String, Boolean>()

    fun handleIncomingSignaling(packet: SignalingPacket): Boolean {
        if (packet.targetDeviceId != localDeviceId) return false
        activePeers[packet.senderDeviceId] = true
        return true
    }

    fun isPeerConnected(deviceId: String): Boolean = activePeers[deviceId] ?: false

    fun disconnectPeer(deviceId: String) {
        activePeers.remove(deviceId)
    }
}
