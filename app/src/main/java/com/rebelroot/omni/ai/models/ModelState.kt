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

/**
 * Lifecycle state of a single model's install operation.
 *
 * Matches the required progress states:
 *   idle → queued → downloading → (paused) → verifying → installed | failed
 */
enum class ModelInstallState {
    IDLE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLED,
    FAILED
}

/** Byte-level download progress (also used for verification phase). */
data class ModelProgress(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)

    val isIndeterminate: Boolean get() = totalBytes <= 0L
}

/** Observable state for one model in the repository. */
data class ModelState(
    val descriptor: ModelDescriptor? = null,
    val status: ModelInstallState = ModelInstallState.IDLE,
    val progress: ModelProgress = ModelProgress(),
    val errorMessage: String? = null
) {
    val isInstalled: Boolean get() = status == ModelInstallState.INSTALLED
    val isActive: Boolean get() = status in setOf(
        ModelInstallState.QUEUED,
        ModelInstallState.DOWNLOADING,
        ModelInstallState.PAUSED,
        ModelInstallState.VERIFYING
    )
}
