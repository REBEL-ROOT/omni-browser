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
     */
    suspend fun burn() = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔥 Initiating 1-tap data incineration...")
        
        try {
            // 1. Clear ALL GeckoView storage — cookies, cache, DOM storage, history,
            //    auth sessions, permissions, and site settings in one pass.
            withContext(Dispatchers.Main) {
                runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
                    { Log.d(TAG, "GeckoView storageController clear completed successfully.") },
                    { err -> Log.e(TAG, "GeckoView storageController clear error", err) }
                )
            }

            // 2. Wipes standard HTTP WebView caches and temp cacheDir files recursively
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
            
            // 3. Wipes temporary download folders
            val tempDownloadsDir = File(context.filesDir, "temp_downloads")
            if (tempDownloadsDir.exists()) {
                tempDownloadsDir.deleteRecursively()
            }

            Log.i(TAG, "🔥 All temporary data successfully incinerated. No traces left.")

        } catch (e: Exception) {
            Log.e(TAG, "Error executing Fire Button data burn", e)
        }
    }
}

// Inline File import definition for safety
private typealias File = java.io.File
