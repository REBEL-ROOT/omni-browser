package com.omni.browser.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.omni.browser.tools.locker.PrivateLockerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class StreamDownloadEngine(
    private val context: Context,
    private val ffmpegBridge: FFmpegBridge,
    private val privateLockerManager: PrivateLockerManager
) {

    sealed class DownloadProgress {
        data class Downloading(val percent: Int, val bytesDownloaded: Long) : DownloadProgress()
        data class Muxing(val message: String) : DownloadProgress()
        data class Complete(val file: File, val sizeBytes: Long) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }

    data class DownloadJob(
        val id: String,
        val filename: String,
        val url: String,
        val saveToLocker: Boolean,
        val progress: StateFlow<DownloadProgress>
    )

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    suspend fun startDownload(
        url: String,
        suggestedName: String,
        type: MediaInterceptor.MediaType,
        saveToLocker: Boolean
    ): String {
        val jobId = UUID.randomUUID().toString()
        val extension = when (type) {
            MediaInterceptor.MediaType.AUDIO -> ".mp3"
            MediaInterceptor.MediaType.WEBM -> ".webm"
            else -> ".mp4"
        }
        val filename = if (suggestedName.endsWith(extension)) suggestedName else "$suggestedName$extension"
        val progressFlow = MutableStateFlow<DownloadProgress>(DownloadProgress.Downloading(0, 0L))

        val job = DownloadJob(
            id = jobId,
            filename = filename,
            url = url,
            saveToLocker = saveToLocker,
            progress = progressFlow
        )

        _jobs.update { it + job }

        // Launch asynchronous downloader in Coroutine Dispatcher context
        kotlinx.coroutines.GlobalScope.also {
            // Background dispatch to avoid blocking
            kotlin.runCatching {
                val dispatcher = Dispatchers.IO
                kotlinx.coroutines.launch(dispatcher) {
                    try {
                        if (type == MediaInterceptor.MediaType.HLS) {
                            downloadHLS(url, filename, saveToLocker, progressFlow)
                        } else {
                            downloadDirect(url, filename, saveToLocker, progressFlow)
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadEngine", "Download failed for job $jobId", e)
                        progressFlow.value = DownloadProgress.Error(e.message ?: "Unknown download error")
                    }
                }
            }
        }

        return jobId
    }

    private suspend fun downloadDirect(
        urlStr: String,
        filename: String,
        saveToLocker: Boolean,
        progressFlow: MutableStateFlow<DownloadProgress>
    ) = withContext(Dispatchers.IO) {
        val targetDir = if (saveToLocker) {
            File(context.filesDir, "temp_downloads").apply { mkdirs() }
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        val targetFile = File(targetDir, filename)
        if (targetFile.exists()) targetFile.delete()

        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            progressFlow.value = DownloadProgress.Error("Server returned code ${connection.responseCode}")
            return@withContext
        }

        val totalLength = connection.contentLengthLong
        val input = BufferedInputStream(connection.inputStream, 8192)
        val output = FileOutputStream(targetFile)

        val buffer = ByteArray(8192)
        var count: Int
        var totalBytes = 0L

        while (input.read(buffer).also { count = it } != -1) {
            totalBytes += count
            output.write(buffer, 0, count)
            
            val percent = if (totalLength > 0) ((totalBytes * 100) / totalLength).toInt() else -1
            progressFlow.value = DownloadProgress.Downloading(percent, totalBytes)
        }

        output.flush()
        output.close()
        input.close()

        if (saveToLocker) {
            progressFlow.value = DownloadProgress.Muxing("Encrypting and moving to Private Locker...")
            val mimeType = if (filename.endsWith(".mp3")) "audio/mpeg" else "video/mp4"
            privateLockerManager.saveUriToLocker(Uri.fromFile(targetFile), filename, mimeType)
            targetFile.delete()
            val finalLockerFile = File(context.filesDir, "locker/$filename") // Simulated final target path
            progressFlow.value = DownloadProgress.Complete(finalLockerFile, totalBytes)
        } else {
            progressFlow.value = DownloadProgress.Complete(targetFile, totalBytes)
        }
    }

    private suspend fun downloadHLS(
        manifestUrl: String,
        filename: String,
        saveToLocker: Boolean,
        progressFlow: MutableStateFlow<DownloadProgress>
    ) = withContext(Dispatchers.IO) {
        progressFlow.value = DownloadProgress.Muxing("Fetching HLS manifest...")
        
        // 1. Fetch Manifest
        val m3u8Content = fetchTextUrl(manifestUrl)
        if (m3u8Content.isEmpty()) {
            progressFlow.value = DownloadProgress.Error("Empty manifest or network error")
            return@withContext
        }

        // 2. Parse Segment Links
        val segmentUrls = parseM3U8Segments(manifestUrl, m3u8Content)
        if (segmentUrls.isEmpty()) {
            progressFlow.value = DownloadProgress.Error("No segment chunks found in manifest")
            return@withContext
        }

        // 3. Create Sandbox directory
        val tempDir = File(context.cacheDir, "hls_${UUID.randomUUID()}").apply { mkdirs() }
        
        val totalSegments = segmentUrls.size
        val downloadedCount = AtomicInteger(0)
        var totalBytes = 0L

        progressFlow.value = DownloadProgress.Downloading(0, 0L)

        // 4. Download Segments concurrently (limit to 5 streams to optimize battery/resource usage)
        val semaphore = Semaphore(5)
        val jobs = segmentUrls.mapIndexed { index, segUrl ->
            kotlinx.coroutines.launch(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val segmentFile = File(tempDir, "seg-$index.ts")
                        val bytes = downloadSegmentFile(segUrl, segmentFile)
                        totalBytes += bytes
                        val downloaded = downloadedCount.incrementAndGet()
                        val percent = (downloaded * 100) / totalSegments
                        progressFlow.value = DownloadProgress.Downloading(percent, totalBytes)
                    } catch (e: Exception) {
                        Log.e("DownloadEngine", "Failed segment index $index", e)
                    }
                }
            }
        }
        
        // Wait for all chunk completions
        jobs.forEach { it.join() }

        if (downloadedCount.get() < totalSegments) {
            progressFlow.value = DownloadProgress.Error("Failed downloading HLS chunks ($downloadedCount/$totalSegments completed)")
            tempDir.deleteRecursively()
            return@withContext
        }

        // 5. Stitch / Mux segments via FFmpeg NDK Bridge
        progressFlow.value = DownloadProgress.Muxing("Remuxing segments into premium MP4 container...")
        
        val targetDir = if (saveToLocker) {
            File(context.filesDir, "temp_downloads").apply { mkdirs() }
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        val finalOutFile = File(targetDir, filename)
        if (finalOutFile.exists()) finalOutFile.delete()

        // Call JNI or Kotlin fallback stitching method.
        // We pass the temp folder index path as input which FFmpegBridge fallback parses!
        val result = ffmpegBridge.execute("-i", tempDir.absolutePath, "-c", "copy", finalOutFile.absolutePath)

        tempDir.deleteRecursively()

        if (result == 0) {
            if (saveToLocker) {
                progressFlow.value = DownloadProgress.Muxing("Encrypting and moving to Private Locker...")
                val mimeType = if (filename.endsWith(".mp3")) "audio/mpeg" else "video/mp4"
                privateLockerManager.saveUriToLocker(Uri.fromFile(finalOutFile), filename, mimeType)
                finalOutFile.delete()
                val finalLockerFile = File(context.filesDir, "locker/$filename")
                progressFlow.value = DownloadProgress.Complete(finalLockerFile, finalLockerFile.length())
            } else {
                progressFlow.value = DownloadProgress.Complete(finalOutFile, finalOutFile.length())
            }
        } else {
            progressFlow.value = DownloadProgress.Error("FFmpeg stitching error (Code $result)")
        }
    }

    private fun fetchTextUrl(urlStr: String): String {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connect()
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseM3U8Segments(baseManifestUrl: String, content: String): List<String> {
        val list = mutableListOf<String>()
        val baseUri = baseManifestUrl.substring(0, baseManifestUrl.lastIndexOf("/") + 1)
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val fullUrl = if (trimmed.startsWith("http")) {
                    trimmed
                } else {
                    "$baseUri$trimmed"
                }
                list.add(fullUrl)
            }
        }
        return list
    }

    private fun downloadSegmentFile(urlStr: String, file: File): Long {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        var size = 0L
        connection.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(4096)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    size += read
                }
            }
        }
        return size
    }
}
