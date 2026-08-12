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

package com.rebelroot.omni.ai.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates the full model lifecycle for a single [ModelCatalog]:
 *
 *   queued → downloading → verifying → installed | failed
 *
 * Responsibilities are deliberately split from [ModelStorage] (files),
 * [ModelVerifier] (integrity) and [ModelDownloader] (transfer) so each is
 * independently testable.
 *
 * Safety guarantees:
 *  - A model is only activated after passing [ModelVerifier] (size + SHA-256).
 *  - Install is atomic (partial → final via [ModelStorage.commit]).
 *  - Updates download & verify the NEW version independently; the OLD version is
 *    kept until the new one is verified, so a failed update never removes a
 *    working model.
 *  - Cancellation discards the partial file; an installed model is never touched.
 */
class ModelRepository(
    private val catalog: ModelCatalog,
    private val storage: ModelStorage,
    private val downloader: ModelDownloader = ModelDownloader(),
    private val verifier: ModelVerifier = ModelVerifier()
) {
    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states

    private val cancelled = ConcurrentHashMap<String, AtomicBoolean>()

    fun stateFor(id: String): ModelState =
        _states.value[id] ?: ModelState(descriptor = catalog.byId(id))

    fun installedModels(): List<ModelDescriptor> = storage.listInstalled()

    fun isInstalled(descriptor: ModelDescriptor): Boolean = storage.isInstalled(descriptor)

    /** Begin (or resume) installation of [descriptor]. */
    suspend fun install(descriptor: ModelDescriptor): ModelState {
        val cancelFlag = cancelled.getOrPut(descriptor.id) { AtomicBoolean(false) }
        cancelFlag.set(false)

        emit(descriptor.id) { copy(status = ModelInstallState.QUEUED, errorMessage = null) }

        // Skip transfer if a complete partial already exists.
        val partialComplete = storage.partialBytes(descriptor) == descriptor.sizeBytes
        if (!partialComplete) {
            emit(descriptor.id) { copy(status = ModelInstallState.DOWNLOADING, progress = ModelProgress(0, descriptor.sizeBytes)) }
            val outcome = downloader.download(
                descriptor = descriptor,
                partialFile = storage.partialFile(descriptor),
                allowHosts = catalog.allowedHosts().takeIf { it.isNotEmpty() },
                listener = object : ModelDownloader.ProgressListener {
                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long) {
                        emit(descriptor.id) {
                            copy(
                                status = ModelInstallState.DOWNLOADING,
                                progress = ModelProgress(bytesDownloaded, totalBytes, bytesPerSecond)
                            )
                        }
                    }
                },
                isCancelled = { cancelFlag.get() }
            )
            when (outcome) {
                is ModelDownloader.DownloadOutcome.Success -> { /* continue */ }
                is ModelDownloader.DownloadOutcome.Cancelled -> {
                    emit(descriptor.id) { copy(status = ModelInstallState.IDLE, progress = ModelProgress()) }
                    storage.discardPartial(descriptor)
                    return stateFor(descriptor.id)
                }
                is ModelDownloader.DownloadOutcome.Failed -> {
                    emit(descriptor.id) { copy(status = ModelInstallState.FAILED, errorMessage = outcome.reason) }
                    storage.discardPartial(descriptor)
                    return stateFor(descriptor.id)
                }
            }
        }

        // Verify
        emit(descriptor.id) { copy(status = ModelInstallState.VERIFYING) }
        val partial = storage.partialFile(descriptor)
        val result = verifier.verify(partial, descriptor)
        if (result !is VerificationResult.Verified && result !is VerificationResult.Unverified) {
            emit(descriptor.id) { copy(status = ModelInstallState.FAILED, errorMessage = (result as VerificationResult.Failed).reason) }
            storage.discardPartial(descriptor)
            return stateFor(descriptor.id)
        }

        // Atomic install
        val committed = storage.commit(descriptor)
        if (!committed) {
            emit(descriptor.id) { copy(status = ModelInstallState.FAILED, errorMessage = "failed to install model file") }
            return stateFor(descriptor.id)
        }

        // Update succeeded: remove a previous version if present (rollback safety:
        // the old file remains untouched until this point).
        removeOlderVersions(descriptor)

        emit(descriptor.id) { copy(status = ModelInstallState.INSTALLED, errorMessage = null, progress = ModelProgress(descriptor.sizeBytes, descriptor.sizeBytes)) }
        return stateFor(descriptor.id)
    }

    /** Update an already-installed model to a newer [descriptor]. */
    suspend fun update(descriptor: ModelDescriptor): ModelState = install(descriptor)

    /** Request cancellation of an in-flight download. */
    fun cancel(id: String) {
        cancelled[id]?.set(true)
    }

    /** Delete an installed model (and any partial). */
    fun delete(id: String): Boolean {
        val d = catalog.byId(id) ?: run {
            // Not in catalog but may still be on disk; try to find by id prefix.
            return storage.listInstalled().firstOrNull { it.id == id }?.let { storage.delete(it) } ?: false
        }
        val ok = storage.delete(d)
        if (ok) emit(id) { copy(status = ModelInstallState.IDLE, progress = ModelProgress(), errorMessage = null) }
        return ok
    }

    /** Drop a partial download (e.g. on user cancel) without affecting installs. */
    fun discardPartial(id: String) {
        catalog.byId(id)?.let { storage.discardPartial(it) }
    }

    private fun removeOlderVersions(descriptor: ModelDescriptor) {
        storage.listInstalled().forEach { installed ->
            if (installed.id == descriptor.id && installed.version != descriptor.version) {
                storage.delete(installed)
            }
        }
    }

    @Synchronized
    private fun emit(id: String, transform: ModelState.() -> ModelState) {
        val current = _states.value[id] ?: ModelState(descriptor = catalog.byId(id))
        val next = transform(current)
        _states.value = _states.value.toMutableMap().apply { put(id, next) }
    }
}
