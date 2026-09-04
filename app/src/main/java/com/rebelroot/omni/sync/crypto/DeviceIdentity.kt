package com.rebelroot.omni.sync.crypto

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TrustedDevice(
    val deviceId: String,
    val deviceName: String,
    val publicKeyBase64: String,
    val lanHost: String? = null,
    val lanPort: Int? = null,
    val pairedAt: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val revokedAt: Long? = null
)

class DeviceKeyManager(
    private val keyDir: File
) {
    companion object {
        private const val IDENTITY_FILE = "sync_device_identity.json"
    }

    val deviceId: String
    val deviceName: String
    val keyPair: KeyPair

    init {
        val file = File(keyDir, IDENTITY_FILE)
        if (file.exists()) {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            deviceId = json.optString("deviceId", UUID.randomUUID().toString())
            deviceName = json.optString("deviceName", "Omni Device")
            if (json.has("privateKey") && json.has("publicKey")) {
                val privBytes = Base64.getDecoder().decode(json.getString("privateKey"))
                val pubBytes = Base64.getDecoder().decode(json.getString("publicKey"))
                val kf = KeyFactory.getInstance("EC")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                keyPair = KeyPair(pubKey, privKey)
            } else {
                keyPair = CryptoEngine.generateKeyPair()
                saveIdentity(file)
            }
        } else {
            deviceId = UUID.randomUUID().toString()
            deviceName = "Omni Device"
            keyPair = CryptoEngine.generateKeyPair()
            saveIdentity(file)
        }
    }

    val publicKeyBase64: String
        get() = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    val fingerprint: String
        get() = CryptoEngine.sha256Hex(keyPair.public.encoded)

    private fun saveIdentity(file: File) {
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("publicKey", Base64.getEncoder().encodeToString(keyPair.public.encoded))
            put("privateKey", Base64.getEncoder().encodeToString(keyPair.private.encoded))
        }
        file.writeText(json.toString(), Charsets.UTF_8)
    }
}

class TrustManager(
    private val keyDir: File
) {
    companion object {
        private const val TRUST_FILE = "sync_trusted_devices.json"
    }

    private val trustedDevices = ConcurrentHashMap<String, TrustedDevice>()

    init {
        loadTrustedDevices()
    }

    fun addTrustedDevice(device: TrustedDevice) {
        trustedDevices[device.deviceId] = device
        saveTrustedDevices()
    }

    fun revokeDevice(deviceId: String) {
        val existing = trustedDevices[deviceId]
        if (existing != null) {
            trustedDevices[deviceId] = existing.copy(isRevoked = true, revokedAt = System.currentTimeMillis())
            saveTrustedDevices()
        }
    }

    fun isDeviceTrusted(deviceId: String): Boolean {
        val dev = trustedDevices[deviceId] ?: return false
        return !dev.isRevoked
    }

    fun getDevice(deviceId: String): TrustedDevice? = trustedDevices[deviceId]

    fun allTrustedDevices(): List<TrustedDevice> = trustedDevices.values.filter { !it.isRevoked }

    private fun loadTrustedDevices() {
        val file = File(keyDir, TRUST_FILE)
        if (!file.exists()) return

        val arr = JSONArray(file.readText(Charsets.UTF_8))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val dev = TrustedDevice(
                deviceId = obj.getString("deviceId"),
                deviceName = obj.getString("deviceName"),
                publicKeyBase64 = obj.getString("publicKey"),
                lanHost = if (obj.has("lanHost")) obj.getString("lanHost") else null,
                lanPort = if (obj.has("lanPort")) obj.getInt("lanPort") else null,
                pairedAt = obj.optLong("pairedAt", System.currentTimeMillis()),
                isRevoked = obj.optBoolean("isRevoked", false),
                revokedAt = if (obj.has("revokedAt")) obj.getLong("revokedAt") else null
            )
            trustedDevices[dev.deviceId] = dev
        }
    }

    private fun saveTrustedDevices() {
        val file = File(keyDir, TRUST_FILE)
        val arr = JSONArray()
        for (dev in trustedDevices.values) {
            val obj = JSONObject().apply {
                put("deviceId", dev.deviceId)
                put("deviceName", dev.deviceName)
                put("publicKey", dev.publicKeyBase64)
                dev.lanHost?.let { put("lanHost", it) }
                dev.lanPort?.let { put("lanPort", it) }
                put("pairedAt", dev.pairedAt)
                put("isRevoked", dev.isRevoked)
                dev.revokedAt?.let { put("revokedAt", it) }
            }
            arr.put(obj)
        }
        file.writeText(arr.toString(2), Charsets.UTF_8)
    }
}
