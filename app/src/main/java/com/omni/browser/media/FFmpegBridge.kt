package com.omni.browser.media

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
    fun execute(vararg args: String): Int {
        isNativeLoaded = loader.isInstalled()
        if (isNativeLoaded) {
            return try {
                executeNative(*args)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("FFmpegBridge", "JNI execute failed, falling back to Kotlin stitching...", e)
                fallbackStitch(args)
            }
        } else {
            Log.i("FFmpegBridge", "Native FFmpeg simulated. Executing Kotlin segment stitcher...")
            return fallbackStitch(args)
        }
    }

    private fun fallbackStitch(args: Array<out String>): Int {
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
                segments.forEach { segment ->
                    FileInputStream(segment).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
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
