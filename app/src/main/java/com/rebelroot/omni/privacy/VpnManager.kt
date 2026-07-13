/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
