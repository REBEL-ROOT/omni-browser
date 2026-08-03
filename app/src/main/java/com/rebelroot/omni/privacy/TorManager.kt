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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress

sealed class TorState {
    object Disconnected : TorState()
    object Connecting : TorState()
    data class Bootstrap(val percent: Int) : TorState()
    object Connected : TorState()
    data class Error(val message: String) : TorState()
}

class TorManager(private val context: Context) {

    companion object {
        private const val TAG = "TorManager"
        const val DEFAULT_SOCKS_PORT = 9050
        const val BRIDGE_SOCKS_PORT = 9052
        const val CONTROL_PORT = 9051

        const val ORBOT_PACKAGE = "org.torproject.android"
        const val ORBOT_START_ACTION = "org.torproject.android.intent.action.START"
        const val ORBOT_STOP_ACTION = "org.torproject.android.intent.action.STOP"

        /** Maximum time to wait for Orbot to become reachable after launch intent. */
        private const val ORBOT_WAIT_TIMEOUT_MS = 30_000L
        /** Polling interval while waiting for Orbot. */
        private const val ORBOT_POLL_INTERVAL_MS = 1_500L
    }

    private val _state = MutableStateFlow<TorState>(TorState.Disconnected)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var testJob: Job? = null
    private var customSocksHost: String? = null
    private var customSocksPort: Int = DEFAULT_SOCKS_PORT

    fun setCustomProxy(host: String, port: Int) {
        customSocksHost = host
        customSocksPort = port
    }

    fun clearCustomProxy() {
        customSocksHost = null
        customSocksPort = DEFAULT_SOCKS_PORT
    }

    fun startTor(port: Int = DEFAULT_SOCKS_PORT) {
        if (testJob?.isActive == true) return

        _state.value = TorState.Connecting
        testJob = scope.launch {
            try {
                val targetHost = customSocksHost ?: "127.0.0.1"
                val targetPort = customSocksHost?.let { customSocksPort } ?: port

                if (customSocksHost == null) {
                    val orbotOk = tryStartOrbot()
                    if (!orbotOk) {
                        _state.value = TorState.Error("Orbot not installed or failed to start. Install Orbot from F-Droid or Play Store, or configure a custom SOCKS5 proxy.")
                        return@launch
                    }
                    // Poll until Orbot's SOCKS port is actually reachable (up to 30s)
                    val reachable = pollUntilReachable(targetHost, targetPort)
                    if (!reachable) {
                        _state.value = TorState.Error("Orbot launched but SOCKS proxy not reachable after ${ORBOT_WAIT_TIMEOUT_MS / 1000}s. Open Orbot and ensure it shows 'Connected'.")
                        return@launch
                    }
                } else {
                    // Custom proxy: single test
                    val ok = testSocksProxy(targetHost, targetPort)
                    if (!ok) {
                        _state.value = TorState.Error("Custom SOCKS5 proxy unreachable at $targetHost:$targetPort")
                        return@launch
                    }
                }

                _state.value = TorState.Connected
            } catch (e: Exception) {
                Log.e(TAG, "Tor connection failed", e)
                _state.value = TorState.Error(e.message ?: "Unknown Tor error")
            } finally {
                testJob = null
            }
        }
    }

    fun stopTor() {
        testJob?.cancel()
        testJob = null
        _state.value = TorState.Disconnected
        // Ask Orbot to stop its Tor service so it doesn't linger in the
        // background. The stop action is a broadcast handled by Orbot's
        // StartTorReceiver; if Orbot isn't running this is a harmless no-op.
        try {
            val stopIntent = Intent(ORBOT_STOP_ACTION).apply {
                `package` = ORBOT_PACKAGE
            }
            context.sendBroadcast(stopIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Orbot stop broadcast", e)
        }
    }

    fun isConnected(): Boolean {
        return _state.value is TorState.Connected
    }

    /** Cancels background coroutines. Call from ViewModel.onCleared(). */
    fun shutdown() {
        testJob?.cancel()
        testJob = null
        scope.cancel()
    }

    /**
     * Opens Orbot's UI so the user can manually tap "New Identity".
     *
     * Orbot does not expose a broadcast intent for NEWNYM, and the Tor
     * control port (9051) requires cookie authentication that we cannot
     * obtain without root or a shared UID. So the best we can do is
     * foreground Orbot and let the user trigger the circuit rotation
     * themselves. This is a no-op when using a custom/remote SOCKS proxy
     * (no control channel exists).
     */
    fun requestNewCircuit() {
        if (customSocksHost == null) {
            try {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(ORBOT_PACKAGE)
                if (intent != null) {
                    // TorManager is built with the application context, so any
                    // activity started from it MUST carry FLAG_ACTIVITY_NEW_TASK
                    // or Android throws AndroidRuntimeException (crash).
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Orbot for new circuit", e)
            }
        }
    }

    private fun tryStartOrbot(): Boolean {
        return try {
            val pm = context.packageManager
            // Use the launch intent as the install probe. getPackageInfo(pkg, 0)
            // is deprecated on API 33+ and throws NameNotFoundException on some
            // OEMs; a null launch intent reliably means Orbot is not installed
            // (or exposes no launcher activity).
            val launchIntent = pm.getLaunchIntentForPackage(ORBOT_PACKAGE)
            if (launchIntent == null) return false

            // Orbot's StartTorReceiver is a BroadcastReceiver, so the start
            // action must be sent as a broadcast — NOT via startActivity.
            val startIntent = Intent(ORBOT_START_ACTION).apply {
                `package` = ORBOT_PACKAGE
            }
            val resolved = pm.queryBroadcastReceivers(startIntent, 0)
            if (resolved.isNotEmpty()) {
                context.sendBroadcast(startIntent)
            } else {
                // Fallback: open Orbot's UI so the user can start it manually.
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Orbot", e)
            false
        }
    }

    /**
     * Polls the SOCKS endpoint until it responds or the timeout elapses.
     * Emits Connecting state throughout — we do NOT synthesize a fake
     * bootstrap percentage from the poll counter. Real Tor bootstrap progress
     * would require reading Orbot's control port (9051), which is not
     * exposed by default. The UI shows an honest indeterminate "Waiting…"
     * state instead of an invented number.
     */
    private suspend fun pollUntilReachable(host: String, port: Int): Boolean {
        val deadline = System.currentTimeMillis() + ORBOT_WAIT_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            _state.value = TorState.Connecting

            if (testSocksProxy(host, port)) {
                return true
            }
            delay(ORBOT_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun testSocksProxy(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
                socket.soTimeout = 2500
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                val nmethods: Byte = 1
                out.write(byteArrayOf(0x05, nmethods, 0x00))
                out.flush()

                val header = ByteArray(2)
                var read = 0
                while (read < 2) {
                    val r = inp.read(header, read, 2 - read)
                    if (r == -1) throw IOException("SOCKS proxy closed")
                    read += r
                }

                val ver = header[0].toInt() and 0xFF
                val method = header[1].toInt() and 0xFF
                if (ver != 5 || method == 0xFF) {
                    throw IOException("SOCKS5 unsupported or auth required")
                }

                val addr = byteArrayOf(0, 0, 0, 0, 0)
                val portBytes = byteArrayOf(0, 0)
                val req = byteArrayOf(0x05, 0x01, 0x00, 0x01) + addr + portBytes
                out.write(req)
                out.flush()

                val resp = ByteArray(10)
                read = 0
                while (read < 10) {
                    val r = inp.read(resp, read, 10 - read)
                    if (r == -1) throw IOException("SOCKS proxy closed")
                    read += r
                }

                val rep = resp[1].toInt() and 0xFF
                if (rep != 0x00) throw IOException("SOCKS5 request failed: $rep")
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
