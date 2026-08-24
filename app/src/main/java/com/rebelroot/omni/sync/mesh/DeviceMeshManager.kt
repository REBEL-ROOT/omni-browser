package com.rebelroot.omni.sync.mesh

import com.rebelroot.omni.sync.crypto.TrustManager
import com.rebelroot.omni.sync.crypto.TrustedDevice
import java.util.concurrent.ConcurrentHashMap

class DeviceMeshManager(
    val localDeviceId: String,
    val trustManager: TrustManager
) {
    private val checkpoints = ConcurrentHashMap<String, Long>() // deviceId -> lastAckedHlcPhysical

    fun updatePeerCheckpoint(deviceId: String, hlcPhysical: Long) {
        val current = checkpoints[deviceId] ?: 0L
        if (hlcPhysical > current) {
            checkpoints[deviceId] = hlcPhysical
        }
    }

    fun getPeerCheckpoint(deviceId: String): Long = checkpoints[deviceId] ?: 0L

    fun processRevocationEvent(revokedDeviceId: String) {
        trustManager.revokeDevice(revokedDeviceId)
        checkpoints.remove(revokedDeviceId)
    }

    fun isDeviceAuthorized(deviceId: String): Boolean = trustManager.isDeviceTrusted(deviceId)
}
