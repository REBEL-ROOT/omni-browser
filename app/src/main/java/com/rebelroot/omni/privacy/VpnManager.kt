package com.rebelroot.omni.privacy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnManager(private val context: Context) {

    companion object {
        private const val TAG = "VpnManager"
    }

    sealed class VpnState {
        object Disconnected : VpnState()
        object Connecting : VpnState()
        object Connected : VpnState()
        data class Error(val message: String) : VpnState()
    }

    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    fun connect(configContent: String) {
        _state.value = VpnState.Error("VPN service is not supported in this version.")
    }

    fun connectContaboVps(ipAddress: String, clientPrivateKey: String, serverPublicKey: String) {
        _state.value = VpnState.Error("VPN service is not supported in this version.")
    }

    fun disconnect() {
        _state.value = VpnState.Disconnected
    }
}
