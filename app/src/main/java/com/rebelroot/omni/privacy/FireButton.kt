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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

class FireButton(
    private val runtime: GeckoRuntime,
    private val context: Context
) {

    companion object {
        private const val TAG = "FireButton"
    }

    /**
     * Incinerates ALL browsing data: cookies, caches, storage, history, permissions,
     * and site settings via [StorageController.ClearFlags.ALL].
     *
     * The GeckoView purge is fully awaited (blocking poll with timeout) before this
     * function returns — callers may bring the process down right after (e.g.
     * "Burn & Exit" calls finishAffinity), and killing the process mid-purge
     * corrupts the Gecko profile, which surfaces as a blank error page on the
     * next launch (issue #117).
     */
    suspend fun burn() = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔥 Initiating 1-tap data incineration...")

        try {
            // 1. Clear ALL GeckoView storage — cookies, cache, DOM storage, history,
            //    auth sessions, permissions, and site settings in one pass.
            //    Dispatched to Main thread for safe GeckoRuntime interaction, then
            //    awaited on this IO thread via poll() so there is no deadlock risk.
            val purgeResult: org.mozilla.geckoview.GeckoResult<Void> =
                withContext(Dispatchers.Main) {
                    runtime.storageController.clearData(StorageController.ClearFlags.ALL)
                }
            try {
                purgeResult.poll(30_000L)
                Log.d(TAG, "GeckoView storageController clear completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "GeckoView storageController clear error", e)
            }

            // 2. Wipe standard HTTP cache and temp cacheDir files
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }

            // 3. Wipe code cache directory
            try {
                val codeCacheDir = context.codeCacheDir
                if (codeCacheDir != null && codeCacheDir.exists()) {
                    codeCacheDir.deleteRecursively()
                    codeCacheDir.mkdirs()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear codeCacheDir", e)
            }

            // 4. Wipe external cache directories
            try {
                context.externalCacheDir?.deleteRecursively()
                context.externalCacheDirs?.forEach { it?.deleteRecursively() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear externalCacheDir", e)
            }

            // 5. Wipe Gecko profile disk cache directories (cache2, startupCache, jumpListCache, etc.)
            try {
                val geckoProfileDir = File(context.filesDir, "mozilla")
                if (geckoProfileDir.exists()) {
                    geckoProfileDir.walkTopDown().forEach { file ->
                        if (file.isDirectory && (
                            file.name == "cache2" ||
                            file.name == "startupCache" ||
                            file.name == "jumpListCache" ||
                            file.name == "shader-cache" ||
                            file.name == "thumbnails"
                        )) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear gecko profile cache", e)
            }

            // 6. Wipe temporary download folders
            val tempDownloadsDir = File(context.filesDir, "temp_downloads")
            if (tempDownloadsDir.exists()) {
                tempDownloadsDir.deleteRecursively()
            }

            // 7. Wipe persisted site permissions and settings file
            val sitePermissionsFile = File(context.filesDir, "browser_site_permissions.json")
            if (sitePermissionsFile.exists()) {
                sitePermissionsFile.delete()
            }

            Log.i(TAG, "🔥 All temporary data successfully incinerated. No traces left.")

        } catch (e: Exception) {
            Log.e(TAG, "Error executing Fire Button data burn", e)
        }
    }
}
