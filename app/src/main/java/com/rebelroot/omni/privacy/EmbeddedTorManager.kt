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
import android.util.Log
import io.matthewnelson.kmp.tor.resource.noexec.tor.ResourceLoaderTorNoExec
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent as KmpTorEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.regex.Pattern

/**
 * Manages an embedded Tor daemon (via kmp-tor 2.x) so the browser can route
 * through Tor without requiring the external Orbot app.
 *
 * The public surface mirrors [TorManager] so both backends are interchangeable
 * from the UI and [BrowserViewModel].
 */
class EmbeddedTorManager(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddedTorManager"

        /** Dedicated SOCKS port for the embedded Tor daemon. Using 9150 (the
         *  Tor Browser convention) avoids any collision with Orbot's 9050. */
        const val EMBEDDED_SOCKS_PORT = 9150

        private val BOOTSTRAP_PROGRESS = Pattern.compile("BOOTSTRAP PROGRESS=(\\d+)")
    }

    private val _state = MutableStateFlow<TorState>(TorState.Disconnected)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val environment: TorRuntime.Environment by lazy {
        TorRuntime.Environment.Builder(
            workDirectory = File(context.filesDir, "torservice"),
            cacheDirectory = File(context.cacheDir, "torservice"),
            loader = ResourceLoaderTorNoExec::getOrCreate,
        )
    }

    private val runtime: TorRuntime by lazy {
        TorRuntime.Builder(environment) {
            val executor = OnEvent.Executor.Immediate

            // Log any runtime-level errors but do not flip state to Error here
            // (fatal failures are handled via the enqueue callback).
            observerStatic(RuntimeEvent.ERROR, executor) { data ->
                Log.e(TAG, "Tor runtime error: $data", data)
            }

            val parseProgress: (String) -> Unit = { data ->
                val matcher = BOOTSTRAP_PROGRESS.matcher(data)
                if (matcher.find()) {
                    val percent = matcher.group(1)?.toIntOrNull()
                    if (percent != null) {
                        _state.value = if (percent >= 100) {
                            TorState.Connected
                        } else {
                            TorState.Bootstrap(percent)
                        }
                    }
                }
            }

            // Parse Tor's STATUS_CLIENT and NOTICE events to emit real bootstrap progress.
            observerStatic(KmpTorEvent.STATUS_CLIENT, executor, parseProgress)
            observerStatic(KmpTorEvent.NOTICE, executor, parseProgress)

            config { _ ->
                TorOption.__SocksPort.configure {
                    port(EMBEDDED_SOCKS_PORT.toPortEphemeral())
                    reassignable(false)
                }
            }

            required(KmpTorEvent.STATUS_CLIENT)
            required(KmpTorEvent.NOTICE)
        }
    }

    fun startTor() {
        if (_state.value is TorState.Connecting || _state.value is TorState.Connected) return

        _state.value = TorState.Connecting

        try {
            runtime.enqueue(
                action = Action.StartDaemon,
                onFailure = { t ->
                    Log.e(TAG, "Failed to start Tor daemon", t)
                    _state.value = TorState.Error(t.message ?: "Failed to start Tor daemon")
                },
                onSuccess = {
                    // Bootstrap progress comes through STATUS_CLIENT observer.
                    // The observer flips state to Connected once PROGRESS=100.
                    Log.i(TAG, "Tor daemon start enqueued")
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting Tor daemon", e)
            _state.value = TorState.Error(e.message ?: "Unexpected error starting Tor")
        }
    }

    fun stopTor() {
        try {
            runtime.enqueue(
                action = Action.StopDaemon,
                onFailure = { t ->
                    Log.e(TAG, "Failed to stop Tor daemon", t)
                },
                onSuccess = {
                    _state.value = TorState.Disconnected
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error stopping Tor daemon", e)
            _state.value = TorState.Disconnected
        }
    }

    fun requestNewCircuit() {
        try {
            runtime.enqueue(
                cmd = TorCmd.Signal.NewNym,
                onFailure = { t ->
                    Log.e(TAG, "Failed to request new circuit", t)
                },
                onSuccess = {
                    Log.i(TAG, "New circuit requested")
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error requesting new circuit", e)
        }
    }

    fun isConnected(): Boolean {
        return _state.value is TorState.Connected
    }

    /** Cancels the background coroutine scope. Call from ViewModel.onCleared(). */
    fun shutdown() {
        scope.cancel()
    }
}
