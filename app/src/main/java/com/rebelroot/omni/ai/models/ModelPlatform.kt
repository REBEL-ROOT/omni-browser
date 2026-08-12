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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

/**
 * Application-level entry point for the shared model platform.
 *
 * Owns the [ModelCatalog] (loaded from the bundled, application-controlled
 * `assets/ai/models_catalog.json`), the [ModelStorage] (application-private),
 * and the [ModelRepository] that orchestrates downloads/verification/install.
 *
 * Model downloads are scheduled through [WorkManager] so they survive the user
 * leaving Settings or rotating the device. The worker delegates to [repository].
 *
 * Privacy: model requests never carry webpage cookies/headers/origin — they use
 * only the catalog URL + the app's own HTTPS client.
 */
class ModelPlatform private constructor(private val appContext: Context) {

    private val storageRoot = File(appContext.getDir("ai_models", Context.MODE_PRIVATE), "v1")
    val storage: ModelStorage = ModelStorage(storageRoot)
    val catalog: ModelCatalog = ModelCatalog.parse(loadCatalogJson())
    val repository: ModelRepository = ModelRepository(catalog, storage)

    /** Total bytes used by all installed models. */
    fun installedBytes(): Long = storage.totalInstalledBytes()

    /**
     * Enqueue a background download/install. Idempotent per model id: re-enqueuing
     * replaces any in-flight work for the same id rather than duplicating it.
     */
    fun enqueueDownload(modelId: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(Data.Builder().putString(ModelDownloadWorker.KEY_MODEL_ID, modelId).build())
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "model-download:$modelId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun loadCatalogJson(): String {
        return try {
            appContext.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // No bundled catalog: start with an empty one rather than crashing.
            "{\"models\":[]}"
        }
    }

    companion object {
        private const val CATALOG_ASSET_PATH = "ai/models_catalog.json"

        @Volatile private var instance: ModelPlatform? = null

        /** Get the process-wide singleton, initialised against the app context. */
        fun get(context: Context): ModelPlatform =
            instance ?: synchronized(this) {
                instance ?: ModelPlatform(context.applicationContext).also { instance = it }
            }
    }
}
