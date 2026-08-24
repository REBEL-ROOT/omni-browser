package com.rebelroot.omni.sync.transport.lan

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class DiscoveredPeer(
    val deviceId: String,
    val deviceName: String,
    val hostAddress: String,
    val port: Int,
    val publicKeyBase64: String,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

class LanDiscoveryService(
    val deviceId: String,
    val deviceName: String,
    var port: Int,
    val publicKeyBase64: String,
    private val broadcastPort: Int = 18235
) {
    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredPeer>()
    private var isRunning = false
    private var broadcastSocket: DatagramSocket? = null

    fun start(onPeerDiscovered: ((DiscoveredPeer) -> Unit)? = null) {
        if (isRunning || broadcastPort <= 0) return
        isRunning = true

        thread(isDaemon = true, name = "LanDiscovery-Receiver") {
            try {
                val socket = DatagramSocket(broadcastPort)
                broadcastSocket = socket
                val buffer = ByteArray(4096)
                while (isRunning && !socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    try {
                        val json = JSONObject(text)
                        val peerId = json.getString("deviceId")
                        if (peerId != deviceId) {
                            val peer = DiscoveredPeer(
                                deviceId = peerId,
                                deviceName = json.getString("deviceName"),
                                hostAddress = packet.address.hostAddress ?: "127.0.0.1",
                                port = json.getInt("port"),
                                publicKeyBase64 = json.getString("publicKey"),
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            discoveredPeers[peerId] = peer
                            onPeerDiscovered?.invoke(peer)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun broadcastBeacon() {
        if (!isRunning || broadcastPort <= 0) return
        thread(isDaemon = true) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val beaconJson = JSONObject().apply {
                    put("service", "_omni-sync._tcp")
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("port", port)
                    put("publicKey", publicKeyBase64)
                }.toString()
                val bytes = beaconJson.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(
                    bytes, bytes.size,
                    InetAddress.getByName("255.255.255.255"),
                    broadcastPort
                )
                socket.send(packet)
                socket.close()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try {
            broadcastSocket?.close()
        } catch (_: Exception) {}
    }

    fun getDiscoveredPeers(): List<DiscoveredPeer> = discoveredPeers.values.toList()
}
