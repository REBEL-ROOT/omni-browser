package com.rebelroot.omni.sync.coordinator

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.conflict.ConflictEngine
import com.rebelroot.omni.sync.crypto.*
import com.rebelroot.omni.sync.model.*
import com.rebelroot.omni.sync.storage.SyncStorage
import com.rebelroot.omni.sync.transport.lan.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.Socket

enum class SyncStatus {
    IDLE,
    SYNCING,
    CONNECTED,
    ERROR
}

enum class SyncBackend {
    OMNI_LAN,
    FIREFOX,
    BOTH
}

data class SyncUiState(
    val deviceId: String = "",
    val deviceName: String = "",
    val fingerprint: String = "",
    val trustedDevices: List<TrustedDevice> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val autoSyncEnabled: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val pendingOutboxCount: Int = 0,
    val statusMessage: String = "Ready",
    val syncBackend: SyncBackend = SyncBackend.OMNI_LAN,
    val fxSyncEnabled: Boolean = false,
    val fxAccountEmail: String? = null
)

class SyncCoordinator(
    private val baseDir: File,
    val collection: BookmarkCollection,
    val lanPort: Int = 0,
    private val broadcastPort: Int = 0,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val keyManager = DeviceKeyManager(baseDir)
    val trustManager = TrustManager(baseDir)
    val pairingEngine = PairingEngine(keyManager, trustManager)
    val clock = HlcClock(keyManager.deviceId)
    val adapter = BookmarkAdapter(clock)
    val storage = SyncStorage(baseDir, clock)
    val conflictEngine = ConflictEngine(adapter, storage)

    val lanServer = LanTransportServer(lanPort, keyManager, trustManager)
    val lanDiscovery = LanDiscoveryService(
        deviceId = keyManager.deviceId,
        deviceName = keyManager.deviceName,
        port = lanPort,
        publicKeyBase64 = keyManager.publicKeyBase64,
        broadcastPort = broadcastPort
    )

    private val _uiState = MutableStateFlow(
        SyncUiState(
            deviceId = keyManager.deviceId,
            deviceName = keyManager.deviceName,
            fingerprint = keyManager.fingerprint,
            trustedDevices = trustManager.allTrustedDevices(),
            pendingOutboxCount = storage.outboxCount()
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        startLanServices()
    }

    private fun startLanServices() {
        lanServer.start { session ->
            handleIncomingSession(session)
        }
        lanDiscovery.port = lanServer.port
        lanDiscovery.start { peer ->
            if (trustManager.isDeviceTrusted(peer.deviceId) && _uiState.value.autoSyncEnabled) {
                syncWithPeer(peer.hostAddress, peer.port)
            }
        }
    }

    fun onLocalBookmarkMutation(op: SyncOperation) {
        storage.recordLocalMutation(op)
        updateState()
        if (_uiState.value.autoSyncEnabled) {
            lanDiscovery.broadcastBeacon()
        }
    }

    fun syncNow() {
        scope.launch {
            _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.SYNCING, statusMessage = "Broadcasting sync beacon...")
            lanDiscovery.broadcastBeacon()
            val peers = lanDiscovery.getDiscoveredPeers().filter { trustManager.isDeviceTrusted(it.deviceId) }
            if (peers.isEmpty()) {
                _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.IDLE, statusMessage = "No trusted LAN peers active")
            } else {
                peers.forEach { syncWithPeer(it.hostAddress, it.port) }
            }
        }
    }

    fun syncWithPeer(host: String, port: Int) {
        scope.launch {
            try {
                _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.SYNCING, statusMessage = "Syncing with " + host + ":" + port)
                val socket = Socket(host, port)
                val session = LanTransportSession(socket, keyManager, trustManager, isServer = false)
                if (session.performHandshake()) {
                    val pending = storage.pendingOutboxOperations()
                    session.sendSyncOperations(pending)

                    val receivedEnv = session.receiveEncryptedEnvelope()
                    if (receivedEnv != null) {
                        val decrypted = session.decryptEnvelope(receivedEnv)
                        if (decrypted != null) {
                            _uiState.value = _uiState.value.copy(
                                lastSyncTimestamp = System.currentTimeMillis(),
                                syncStatus = SyncStatus.CONNECTED,
                                statusMessage = "Sync completed successfully"
                            )
                        }
                    }
                }
                session.close()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.ERROR, statusMessage = "Sync failed: " + e.message)
            }
        }
    }

    private fun handleIncomingSession(session: LanTransportSession) {
        scope.launch {
            try {
                val envelope = session.receiveEncryptedEnvelope()
                if (envelope != null) {
                    val bytes = session.decryptEnvelope(envelope)
                    if (bytes != null) {
                        _uiState.value = _uiState.value.copy(
                            lastSyncTimestamp = System.currentTimeMillis(),
                            syncStatus = SyncStatus.CONNECTED,
                            statusMessage = "Synced with " + session.remoteDeviceId
                        )
                    }
                }
                val pending = storage.pendingOutboxOperations()
                session.sendSyncOperations(pending)
            } catch (_: Exception) {}
        }
    }

    fun createPairingInvitation(): PairingInvitation = pairingEngine.createInvitation()

    fun processPairingInvitation(invitationJson: String): PairingResult {
        val result = pairingEngine.processIncomingInvitation(invitationJson)
        updateState()
        return result
    }

    fun revokeDevice(deviceId: String) {
        trustManager.revokeDevice(deviceId)
        updateState()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoSyncEnabled = enabled)
    }

    private fun updateState() {
        _uiState.value = _uiState.value.copy(
            trustedDevices = trustManager.allTrustedDevices(),
            pendingOutboxCount = storage.outboxCount()
        )
    }

    fun shutdown() {
        lanDiscovery.stop()
        lanServer.stop()
    }
}
