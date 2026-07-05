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

package com.rebelroot.omni.media

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FFmpegBridge(private val loader: FFmpegLoader) {

    private var isNativeLoaded = false

    init {
        isNativeLoaded = loader.isInstalled()
    }

    /**
     * Executes an FFmpeg command.
     * If native binaries are loaded, delegates to JNI.
     * Otherwise, falls back to a Kotlin HLS stitcher for MPEG-TS streams.
     */
    fun isNativeActive(): Boolean {
        return loader.isJniLoaded()
    }

    fun execute(vararg args: String, onProgress: ((Int) -> Unit)? = null): Int {
        isNativeLoaded = loader.isInstalled()
        if (isNativeLoaded && isNativeActive()) {
            return try {
                executeNative(*args)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("FFmpegBridge", "JNI execute failed, falling back to Kotlin stitching...", e)
                fallbackStitch(args, onProgress)
            }
        } else {
            Log.i("FFmpegBridge", "Native FFmpeg simulated. Executing Kotlin segment stitcher...")
            return fallbackStitch(args, onProgress)
        }
    }

    private fun fallbackStitch(args: Array<out String>, onProgress: ((Int) -> Unit)? = null): Int {
        try {
            // Find input and output flags
            var inputPath = ""
            var outputPath = ""
            for (i in args.indices) {
                if (args[i] == "-i" && i + 1 < args.size) {
                    inputPath = args[i + 1]
                }
            }
            outputPath = args.last()

            if (inputPath.isEmpty() || outputPath.isEmpty()) {
                Log.e("FFmpegBridge", "Invalid arguments for fallback stitcher.")
                return -1
            }

            val inputDir = File(inputPath)
            if (!inputDir.exists() || !inputDir.isDirectory) {
                // If the input path is a direct URL or manifest file, we can't easily stitch locally without downloading segments.
                // The stream downloader will download segments to a temp folder and pass the temp folder index as "-i"
                Log.e("FFmpegBridge", "Fallback stitcher requires local directory containing segment files.")
                return -1
            }

            val segments = inputDir.listFiles { _, name -> name.endsWith(".ts") || name.contains("seg-") }
                ?.sortedWith { f1, f2 ->
                    // Sort segments numerically
                    val num1 = f1.name.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val num2 = f2.name.filter { it.isDigit() }.toIntOrNull() ?: 0
                    num1.compareTo(num2)
                } ?: emptyList()

            if (segments.isEmpty()) {
                Log.e("FFmpegBridge", "No segments found to stitch.")
                return -1
            }

            val outFile = File(outputPath)
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) outFile.delete()

            Log.i("FFmpegBridge", "Stitching ${segments.size} HLS fragments into: ${outFile.name}")
            FileOutputStream(outFile).use { outputStream ->
                segments.forEachIndexed { index, segment ->
                    FileInputStream(segment).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    onProgress?.invoke(((index + 1) * 100) / segments.size)
                }
            }

            Log.i("FFmpegBridge", "Stitch complete: ${outFile.length()} bytes written.")
            return 0

        } catch (e: Exception) {
            Log.e("FFmpegBridge", "Stitching failed", e)
            return -1
        }
    }

    fun cancel() {
        if (isNativeLoaded) {
            try {
                cancelNative()
            } catch (e: UnsatisfiedLinkError) {
                Log.e("FFmpegBridge", "JNI cancel call failed")
            }
        }
    }

    private external fun executeNative(vararg args: String): Int
    private external fun cancelNative(): Unit
}
