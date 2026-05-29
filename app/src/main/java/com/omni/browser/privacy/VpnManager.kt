package com.omni.browser.privacy

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.net.InetAddress

class VpnManager(private val context: Context) {

    companion object {
        private const val TAG = "VpnManager"
        private const val TUNNEL_NAME = "omni_wg_tunnel"
    }

    sealed class VpnState {
        object Disconnected : VpnState()
        object Connecting : VpnState()
        object Connected : VpnState()
        data class Error(val message: String) : VpnState()
    }

    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private var backend: Backend? = null

    init {
        try {
            backend = GoBackend(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GoBackend", e)
        }
    }

    /**
     * Start the VPN tunnel using a standard raw config string (e.g. parsed from a file)
     */
    fun connect(configContent: String) {
        val wgBackend = backend ?: run {
            _state.value = VpnState.Error("VPN service backend not available on this device.")
            return
        }

        _state.value = VpnState.Connecting
        try {
            val inputStream = configContent.byteInputStream()
            val config = Config.parse(inputStream)
            
            wgBackend.setState(
                object : Tunnel {
                    override fun getName() = TUNNEL_NAME
                    override fun onStateChange(state: Tunnel.State) {
                        Log.i(TAG, "WireGuard Tunnel State changed: $state")
                        when (state) {
                            Tunnel.State.UP -> _state.value = VpnState.Connected
                            Tunnel.State.DOWN -> _state.value = VpnState.Disconnected
                            Tunnel.State.TOGGLE -> {}
                        }
                    }
                },
                Tunnel.State.UP,
                config
            )
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard connect failed", e)
            _state.value = VpnState.Error(e.message ?: "WireGuard connection failed")
        }
    }

    /**
     * Pre-configured connection pointing to their high-performance Contabo VPS
     */
    fun connectContaboVps(ipAddress: String, clientPrivateKey: String, serverPublicKey: String) {
        val configStr = """
            [Interface]
            PrivateKey = $clientPrivateKey
            Address = 10.0.0.2/24
            DNS = 1.1.1.1
            
            [Peer]
            PublicKey = $serverPublicKey
            Endpoint = $ipAddress:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        connect(configStr)
    }

    fun disconnect() {
        val wgBackend = backend ?: return
        try {
            wgBackend.setState(
                object : Tunnel {
                    override fun getName() = TUNNEL_NAME
                    override fun onStateChange(state: Tunnel.State) {}
                },
                Tunnel.State.DOWN,
                null
            )
            _state.value = VpnState.Disconnected
            Log.i(TAG, "WireGuard VPN disconnected.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN tunnel", e)
        }
    }
}
