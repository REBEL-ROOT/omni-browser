package com.rebelroot.omni.sync.transport.lan

import com.rebelroot.omni.sync.crypto.DeviceKeyManager
import com.rebelroot.omni.sync.crypto.TrustManager
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class LanTransportServer(
    var port: Int,
    private val keyManager: DeviceKeyManager,
    private val trustManager: TrustManager
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    val activeSessions = ConcurrentHashMap<String, LanTransportSession>()

    fun start(onSessionEstablished: ((LanTransportSession) -> Unit)? = null) {
        if (isRunning) return
        isRunning = true

        val sSocket = ServerSocket(port)
        serverSocket = sSocket
        port = sSocket.localPort // Bind to actual port if 0 was passed

        thread(isDaemon = true, name = "LanServer-Acceptor") {
            try {
                while (isRunning && !sSocket.isClosed) {
                    val clientSocket = sSocket.accept()
                    thread(isDaemon = true, name = "LanServer-Session") {
                        val session = LanTransportSession(clientSocket, keyManager, trustManager, isServer = true)
                        if (session.performHandshake()) {
                            val peerId = session.remoteDeviceId
                            if (peerId != null) {
                                activeSessions[peerId] = session
                                onSessionEstablished?.invoke(session)
                            }
                        } else {
                            session.close()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        activeSessions.values.forEach { it.close() }
        activeSessions.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}
