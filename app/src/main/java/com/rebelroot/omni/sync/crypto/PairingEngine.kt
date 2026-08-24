package com.rebelroot.omni.sync.crypto

import org.json.JSONObject
import java.util.Base64

data class PairingInvitation(
    val version: Int = 1,
    val deviceId: String,
    val deviceName: String,
    val publicKeyBase64: String,
    val nonceBase64: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("deviceId", deviceId)
        put("deviceName", deviceName)
        put("publicKey", publicKeyBase64)
        put("nonce", nonceBase64)
        put("timestamp", timestamp)
    }.toString()

    companion object {
        fun fromJson(jsonStr: String): PairingInvitation {
            val obj = JSONObject(jsonStr)
            val pubKey = if (obj.has("publicKey")) obj.getString("publicKey") else obj.getString("publicKeyBase64")
            val nonce = if (obj.has("nonce")) obj.getString("nonce") else if (obj.has("nonceBase64")) obj.getString("nonceBase64") else ""
            val ver = if (obj.has("version")) obj.getInt("version") else 1
            val ts = if (obj.has("timestamp")) obj.getLong("timestamp") else System.currentTimeMillis()
            val name = if (obj.has("deviceName")) obj.getString("deviceName") else "Omni Device"
            return PairingInvitation(
                version = ver,
                deviceId = obj.getString("deviceId"),
                deviceName = name,
                publicKeyBase64 = pubKey,
                nonceBase64 = nonce,
                timestamp = ts
            )
        }
    }
}

sealed class PairingResult {
    data class Success(val trustedDevice: TrustedDevice, val sasCode: String) : PairingResult()
    data class Failed(val reason: String) : PairingResult()
}

class PairingEngine(
    private val keyManager: DeviceKeyManager,
    private val trustManager: TrustManager
) {
    companion object {
        const val PAIRING_EXPIRY_MS = 5 * 60 * 1000L
    }

    fun createInvitation(): PairingInvitation {
        val nonce = CryptoEngine.generateRandomNonce(16)
        return PairingInvitation(
            deviceId = keyManager.deviceId,
            deviceName = keyManager.deviceName,
            publicKeyBase64 = keyManager.publicKeyBase64,
            nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        )
    }

    fun processIncomingInvitation(
        invitationJson: String,
        now: Long = System.currentTimeMillis()
    ): PairingResult {
        try {
            val inv = PairingInvitation.fromJson(invitationJson)

            if (now - inv.timestamp > PAIRING_EXPIRY_MS) {
                return PairingResult.Failed("Pairing invitation expired")
            }

            if (inv.deviceId == keyManager.deviceId) {
                return PairingResult.Failed("Cannot pair device with itself")
            }

            val myPubKey = keyManager.keyPair.public.encoded
            val peerPubKey = Base64.getDecoder().decode(inv.publicKeyBase64)
            val nonce = Base64.getDecoder().decode(inv.nonceBase64)
            val sas = CryptoEngine.deriveSasCode(myPubKey, peerPubKey, nonce)

            val trusted = TrustedDevice(
                deviceId = inv.deviceId,
                deviceName = inv.deviceName,
                publicKeyBase64 = inv.publicKeyBase64,
                pairedAt = now
            )
            trustManager.addTrustedDevice(trusted)

            return PairingResult.Success(trusted, sas)
        } catch (e: Exception) {
            return PairingResult.Failed("Invalid invitation payload: " + e.message)
        }
    }
}
