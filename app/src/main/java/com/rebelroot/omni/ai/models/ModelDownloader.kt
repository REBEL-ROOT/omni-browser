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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resumable, cancellable, retryable model downloader.
 *
 * Uses HTTP `Range` to resume an interrupted download from the bytes already on
 * disk (in the `.partial` file). Downloads are application-owned: the URL comes
 * exclusively from the application-controlled [ModelCatalog] and, optionally, an
 * [allowHosts] set — webpage JavaScript can never supply or influence it.
 *
 * Pure JVM (HttpURLConnection) so it is unit-testable against a local server.
 */
class ModelDownloader(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRetries: Int = 5,
    /** Enforced in production (default). Tests may relax it to use a local HTTP server. */
    private val requireHttps: Boolean = true
) {

    interface ProgressListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long)
    }

    sealed class DownloadOutcome {
        object Success : DownloadOutcome()
        data class Failed(val reason: String, val retryable: Boolean) : DownloadOutcome()
        object Cancelled : DownloadOutcome()
    }

    class DownloadCancelled : Exception("download cancelled")

    /**
     * Download [descriptor]'s file, resuming into [partialFile] when possible.
     *
     * @param allowHosts If non-null, the resolved host of [ModelDescriptor.downloadUrl]
     *   must be a member; otherwise the download is refused. This guarantees model
     *   requests never reach an arbitrary (e.g. webpage-supplied) host.
     * @param listener Progress reported on the IO dispatcher.
     * @param isCancelled Polled between chunks; return true to abort cleanly.
     */
    suspend fun download(
        descriptor: ModelDescriptor,
        partialFile: File,
        allowHosts: Set<String>? = null,
        listener: ProgressListener? = null,
        isCancelled: () -> Boolean = { false }
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val url = try {
            URL(descriptor.downloadUrl)
        } catch (e: Exception) {
            return@withContext DownloadOutcome.Failed("invalid URL: ${descriptor.downloadUrl}", false)
        }

        if (requireHttps && url.protocol != "https") {
            return@withContext DownloadOutcome.Failed("only HTTPS model URLs are allowed", false)
        }
        if (allowHosts != null && !allowHosts.contains(url.host.lowercase())) {
            return@withContext DownloadOutcome.Failed(
                "model host '${url.host}' is not in the allow-list", false
            )
        }

        var lastError: String? = null
        repeat(maxRetries + 1) { attempt ->
            if (isCancelled()) return@withContext DownloadOutcome.Cancelled
            try {
                doDownloadChunked(url, descriptor, partialFile, listener, isCancelled)
                return@withContext DownloadOutcome.Success
            } catch (e: DownloadCancelled) {
                return@withContext DownloadOutcome.Cancelled
            } catch (e: IOException) {
                lastError = e.message ?: "io error"
                if (attempt < maxRetries) delay(minOf(2000L * (attempt + 1), 10_000L))
            }
        }
        DownloadOutcome.Failed(lastError ?: "download failed", true)
    }

    private fun doDownloadChunked(
        url: URL,
        descriptor: ModelDescriptor,
        partialFile: File,
        listener: ProgressListener?,
        isCancelled: () -> Boolean
    ) {
        val existing = if (partialFile.isFile) partialFile.length() else 0L
        val total = descriptor.sizeBytes

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", "OmniBrowserOfflineAI/1.0")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (existing > 0 && existing < total) {
            connection.setRequestProperty("Range", "bytes=$existing-")
        }

        val responseCode = connection.responseCode
        val raf = RandomAccessFile(partialFile, "rw")

        try {
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    // Server ignored Range (or fresh download). Restart from zero.
                    if (existing > 0) {
                        raf.seek(0)
                        raf.setLength(0)
                    }
                }
                HttpURLConnection.HTTP_PARTIAL -> {
                    // Resuming: server confirms it will send the remaining bytes.
                    raf.seek(existing)
                }
                else -> {
                    val err = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    throw IOException("HTTP $responseCode: $err")
                }
            }

            val contentLength = connection.contentLengthLong.let { if (it <= 0) total else it }
            val stream = connection.inputStream
            val buf = ByteArray(64 * 1024)
            var downloaded = if (responseCode == HttpURLConnection.HTTP_PARTIAL) existing else 0L
            var lastReport = System.currentTimeMillis()
            var lastBytes = downloaded

            while (true) {
                if (isCancelled()) throw DownloadCancelled()
                val read = stream.read(buf)
                if (read == -1) break
                raf.write(buf, 0, read)
                downloaded += read
                val now = System.currentTimeMillis()
                if (now - lastReport >= 250 || read == -1) {
                    val elapsedMs = (now - lastReport).coerceAtLeast(1)
                    val rate = ((downloaded - lastBytes) * 1000 / elapsedMs).coerceAtLeast(0)
                    listener?.onProgress(downloaded, contentLength, rate)
                    lastReport = now
                    lastBytes = downloaded
                }
            }
            raf.fd.sync()
            if (downloaded != total && total > 0) {
                // Length mismatch vs catalog: surface for verification to catch.
                // We still let the verifier reject mismatched sizes.
            }
        } finally {
            runCatching { raf.close() }
            runCatching { connection.disconnect() }
        }
    }
}
