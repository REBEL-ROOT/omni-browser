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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background worker that drives a model install through [ModelRepository].
 *
 * Using WorkManager guarantees the download is not bound to a Compose screen's
 * lifecycle: leaving Settings, rotating, or backgrounding the app will not cancel
 * it. The actual transfer/verify/atomic-install logic lives in [ModelRepository]
 * (pure, unit-tested); this worker only bridges WorkManager to it.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val platform = ModelPlatform.get(applicationContext)
        val descriptor = platform.catalog.byId(modelId) ?: return Result.failure()

        val state = platform.repository.install(descriptor)
        return if (state.status == ModelInstallState.INSTALLED) {
            Result.success()
        } else {
            // Retry transient failures a bounded number of times.
            if (state.status == ModelInstallState.FAILED && shouldRetry(state.errorMessage)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun shouldRetry(message: String?): Boolean {
        // Network/timeout style failures are retryable; integrity failures are not.
        return message != null && !message.contains("SHA-256", ignoreCase = true) &&
            !message.contains("size mismatch", ignoreCase = true) &&
            !message.contains("allow-list", ignoreCase = true)
    }

    companion object {
        const val KEY_MODEL_ID = "modelId"
    }
}
