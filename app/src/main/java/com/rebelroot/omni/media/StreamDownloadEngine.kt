package com.rebelroot.omni.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rebelroot.omni.tools.locker.PrivateLockerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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
import java.util.concurrent.ConcurrentHashMap

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

    private val runningJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "downloads_channel"
    private val nextNotificationId = AtomicInteger(1000)
    private val jobNotificationIds = ConcurrentHashMap<String, Int>()

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_CANCEL_DOWNLOAD") {
                val jobId = intent.getStringExtra("job_id")
                if (jobId != null) {
                    cancelDownload(jobId)
                }
            }
        }
    }

    init {
        createNotificationChannel()
        val filter = IntentFilter("ACTION_CANCEL_DOWNLOAD")
        ContextCompat.registerReceiver(
            context,
            cancelReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadDownloadHistory()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Shows download progress for videos and audio streams."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNotificationId(jobId: String): Int {
        return jobNotificationIds.getOrPut(jobId) { nextNotificationId.incrementAndGet() }
    }

    private fun updateNotification(jobId: String, title: String, content: String, progress: Int, isIndeterminate: Boolean = false) {
        val notificationId = getNotificationId(jobId)
        val cancelIntent = Intent("ACTION_CANCEL_DOWNLOAD").apply {
            putExtra("job_id", jobId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(progress in 0..99)
            .setAutoCancel(progress >= 100 || progress < 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingIntent)

        if (progress in 0..100) {
            builder.setProgress(100, progress, isIndeterminate)
        } else if (isIndeterminate) {
            builder.setProgress(100, 0, true)
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, builder.build())
        }
    }

    private fun showCompleteNotification(jobId: String, title: String, filename: String) {
        val notificationId = getNotificationId(jobId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(filename)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, builder.build())
        }
    }

    private fun showErrorNotification(jobId: String, title: String, errorMsg: String) {
        val notificationId = getNotificationId(jobId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(errorMsg)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, builder.build())
        }
    }

    fun cancelDownload(jobId: String) {
        runningJobs[jobId]?.cancel()
        runningJobs.remove(jobId)
        val job = _jobs.value.find { it.id == jobId }
        if (job != null) {
            (job.progress as? MutableStateFlow)?.value = DownloadProgress.Error("Download cancelled")
        }
        _jobs.update { list ->
            list.filter { it.id != jobId }
        }
        val notificationId = jobNotificationIds.remove(jobId)
        if (notificationId != null) {
            notificationManager.cancel(notificationId)
        }
        saveDownloadHistory()
    }

    suspend fun startDownload(
        url: String,
        suggestedName: String,
        type: MediaInterceptor.MediaType,
        saveToLocker: Boolean,
        referrerUrl: String? = null
    ): String {
        val jobId = UUID.randomUUID().toString()
        val extension = when (type) {
            MediaInterceptor.MediaType.AUDIO -> ".mp3"
            MediaInterceptor.MediaType.WEBM -> ".webm"
            else -> ".mp4"
        }
        val baseName = suggestedName.removeSuffix(".mp4").removeSuffix(".ts").removeSuffix(".mp3").removeSuffix(".webm")
        val filename = "$baseName$extension"
        val progressFlow = MutableStateFlow<DownloadProgress>(DownloadProgress.Downloading(0, 0L))

        val job = DownloadJob(
            id = jobId,
            filename = filename,
            url = url,
            saveToLocker = saveToLocker,
            progress = progressFlow
        )

        _jobs.update { it + job }
        saveDownloadHistory()

        // Start listening to the job progress flow to update notifications dynamically
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            var lastPercent = -2
            progressFlow.collect { progress ->
                when (progress) {
                    is DownloadProgress.Downloading -> {
                        val pct = progress.percent
                        val text = if (pct >= 0) "$pct% completed" else "Downloading..."
                        updateNotification(jobId, filename, text, pct)
                        if (pct != lastPercent) {
                            lastPercent = pct
                            saveDownloadHistory()
                        }
                    }
                    is DownloadProgress.Muxing -> {
                        updateNotification(jobId, filename, progress.message, -1, isIndeterminate = true)
                        saveDownloadHistory()
                    }
                    is DownloadProgress.Complete -> {
                        showCompleteNotification(jobId, filename, "Saved successfully")
                        jobNotificationIds.remove(jobId)
                        saveDownloadHistory()
                    }
                    is DownloadProgress.Error -> {
                        showErrorNotification(jobId, filename, progress.message)
                        jobNotificationIds.remove(jobId)
                        saveDownloadHistory()
                    }
                }
            }
        }

        // Launch asynchronous downloader in Coroutine Dispatcher context
        val jobCoroutine = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                if (type == MediaInterceptor.MediaType.HLS) {
                    downloadHLS(jobId, url, filename, saveToLocker, referrerUrl, progressFlow)
                } else {
                    downloadDirect(jobId, url, filename, saveToLocker, referrerUrl, progressFlow)
                }
            } catch (e: Exception) {
                Log.e("DownloadEngine", "Download failed for job $jobId", e)
                progressFlow.value = DownloadProgress.Error(e.message ?: "Unknown download error")
            } finally {
                runningJobs.remove(jobId)
            }
        }
        runningJobs[jobId] = jobCoroutine

        return jobId
    }

    private suspend fun downloadDirect(
        jobId: String,
        urlStr: String,
        filename: String,
        saveToLocker: Boolean,
        referrerUrl: String?,
        progressFlow: MutableStateFlow<DownloadProgress>
    ) = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "temp_downloads").apply { mkdirs() }
        val targetFile = File(targetDir, filename)
        if (targetFile.exists()) targetFile.delete()

        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            if (!referrerUrl.isNullOrEmpty()) {
                connection.setRequestProperty("Referer", referrerUrl)
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                progressFlow.value = DownloadProgress.Error("Server returned code ${connection.responseCode}")
                return@withContext
            }

            val totalLength = connection.contentLengthLong
            val input = BufferedInputStream(connection.inputStream, 8192)
            val output = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var count = 0
            var totalBytes = 0L

            while (isActive && input.read(buffer).also { count = it } != -1) {
                totalBytes += count
                output.write(buffer, 0, count)
                
                val percent = if (totalLength > 0) ((totalBytes * 100) / totalLength).toInt() else -1
                progressFlow.value = DownloadProgress.Downloading(percent, totalBytes)
            }

            output.flush()
            output.close()
            input.close()

            if (!isActive) {
                targetFile.delete()
                return@withContext
            }

            if (saveToLocker) {
                progressFlow.value = DownloadProgress.Muxing("Encrypting and moving to Private Locker...")
                val mimeType = if (filename.endsWith(".mp3")) "audio/mpeg" else "video/mp4"
                privateLockerManager.saveUriToLocker(Uri.fromFile(targetFile), filename, mimeType)
                targetFile.delete()
                val finalLockerFile = File(context.filesDir, "locker/$filename")
                progressFlow.value = DownloadProgress.Complete(finalLockerFile, totalBytes)
            } else {
                progressFlow.value = DownloadProgress.Muxing("Saving to public Downloads...")
                val savedFile = saveToPublicDownloads(targetFile, filename)
                progressFlow.value = DownloadProgress.Complete(savedFile, totalBytes)
            }
        } finally {
            if (progressFlow.value !is DownloadProgress.Complete && targetFile.exists()) {
                targetFile.delete()
            }
        }
    }

    private suspend fun downloadHLS(
        jobId: String,
        manifestUrl: String,
        filename: String,
        saveToLocker: Boolean,
        referrerUrl: String?,
        progressFlow: MutableStateFlow<DownloadProgress>
    ) = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "temp_downloads").apply { mkdirs() }
        val finalOutFile = File(targetDir, filename)
        if (finalOutFile.exists()) finalOutFile.delete()

        var success = false
        if (ffmpegBridge.isNativeActive()) {
            progressFlow.value = DownloadProgress.Muxing("Downloading and converting via FFmpeg...")
            val argsList = mutableListOf<String>()
            
            if (!referrerUrl.isNullOrEmpty()) {
                argsList.add("-headers")
                argsList.add("User-Agent: Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36\r\nReferer: $referrerUrl\r\n")
            }
            
            argsList.add("-i")
            argsList.add(manifestUrl)
            argsList.add("-c")
            argsList.add("copy")
            argsList.add(finalOutFile.absolutePath)
            
            val result = ffmpegBridge.execute(*argsList.toTypedArray())
            if (result == 0) {
                success = true
            } else {
                Log.e("DownloadEngine", "Native FFmpeg direct HLS download failed (Code $result). Falling back to segment downloader.")
            }
        }

        if (!success) {
            progressFlow.value = DownloadProgress.Muxing("Fetching HLS manifest...")
            
            var resolvedUrl = manifestUrl
            var m3u8Content = fetchTextUrl(resolvedUrl, referrerUrl)
            
            if (m3u8Content.isEmpty()) {
                progressFlow.value = DownloadProgress.Error("Empty manifest or network error")
                return@withContext
            }

            if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                progressFlow.value = DownloadProgress.Muxing("Resolving HLS Master Playlist...")
                val variants = parseM3U8MasterPlaylist(resolvedUrl, m3u8Content)
                if (variants.isNotEmpty()) {
                    resolvedUrl = variants.first().first
                    m3u8Content = fetchTextUrl(resolvedUrl, referrerUrl)
                    if (m3u8Content.isEmpty()) {
                        progressFlow.value = DownloadProgress.Error("Failed to fetch variant playlist")
                        return@withContext
                    }
                }
            }

            val segmentUrls = parseM3U8Segments(resolvedUrl, m3u8Content)
            if (segmentUrls.isEmpty()) {
                progressFlow.value = DownloadProgress.Error("No segment chunks found in manifest")
                return@withContext
            }

            val keyInfo = parseEncryptionKeyInfo(resolvedUrl, m3u8Content)
            val keyBytes = keyInfo?.let { fetchKeyBytes(it.uri, referrerUrl) }

            val tempDir = File(context.cacheDir, "hls_${UUID.randomUUID()}").apply { mkdirs() }
            
            try {
                val totalSegments = segmentUrls.size
                val downloadedCount = AtomicInteger(0)
                var totalBytes = 0L

                progressFlow.value = DownloadProgress.Downloading(0, 0L)

                val semaphore = Semaphore(5)
                val jobs = segmentUrls.mapIndexed { index, segUrl ->
                    this@withContext.launch {
                        semaphore.withPermit {
                            var attempt = 0
                            var segSuccess = false
                            var bytes = 0L
                            val segmentFile = File(tempDir, "seg-$index.ts")
                            while (attempt < 3 && !segSuccess && isActive) {
                                try {
                                    attempt++
                                    bytes = downloadSegmentFile(
                                        segUrl, 
                                        segmentFile, 
                                        keyInfo, 
                                        keyBytes, 
                                        index, 
                                        referrerUrl
                                    ) { !isActive }
                                    segSuccess = true
                                } catch (ce: kotlinx.coroutines.CancellationException) {
                                    throw ce
                                } catch (e: Exception) {
                                    Log.w("DownloadEngine", "Attempt $attempt failed for segment $index", e)
                                    if (attempt < 3 && isActive) {
                                        delay(1000)
                                    }
                                }
                            }
                            if (segSuccess) {
                                totalBytes += bytes
                                val downloaded = downloadedCount.incrementAndGet()
                                val percent = (downloaded * 100) / totalSegments
                                progressFlow.value = DownloadProgress.Downloading(percent, totalBytes)
                            } else if (isActive) {
                                throw Exception("Failed downloading segment $index after 3 attempts")
                            }
                        }
                    }
                }
                
                jobs.forEach { it.join() }

                if (!isActive) {
                    return@withContext
                }

                if (downloadedCount.get() < totalSegments) {
                    progressFlow.value = DownloadProgress.Error("Failed downloading HLS chunks ($downloadedCount/$totalSegments completed)")
                    return@withContext
                }

                progressFlow.value = DownloadProgress.Muxing("Stitching HLS segments...")
                
                val tempStitchedTs = File(tempDir, "temp_stitched.ts")
                if (tempStitchedTs.exists()) tempStitchedTs.delete()

                val segments = tempDir.listFiles { _, name -> name.endsWith(".ts") || name.contains("seg-") }
                    ?.sortedWith { f1, f2 ->
                        val num1 = f1.name.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val num2 = f2.name.filter { it.isDigit() }.toIntOrNull() ?: 0
                        num1.compareTo(num2)
                    } ?: emptyList()

                if (segments.isEmpty()) {
                    progressFlow.value = DownloadProgress.Error("No segments found to stitch")
                    return@withContext
                }

                FileOutputStream(tempStitchedTs).use { outputStream ->
                    segments.forEachIndexed { index, segment ->
                        if (!isActive) return@withContext
                        java.io.FileInputStream(segment).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        val percent = ((index + 1) * 100) / segments.size
                        progressFlow.value = DownloadProgress.Muxing("Stitching segments: $percent%")
                    }
                }

                if (!isActive) {
                    return@withContext
                }

                var finalSuccess = false
                if (ffmpegBridge.isNativeActive()) {
                    progressFlow.value = DownloadProgress.Muxing("Remuxing stitched segments to MP4...")
                    val result = ffmpegBridge.execute("-i", tempStitchedTs.absolutePath, "-c", "copy", finalOutFile.absolutePath)
                    if (result == 0) {
                        finalSuccess = true
                    }
                }

                if (!finalSuccess) {
                    if (!isActive) return@withContext
                    progressFlow.value = DownloadProgress.Muxing("Stitching & converting HLS to MP4...")
                    finalSuccess = remuxTsToMp4(tempStitchedTs, finalOutFile)
                }

                if (finalSuccess) {
                    success = true
                } else {
                    if (!isActive) return@withContext
                    progressFlow.value = DownloadProgress.Muxing("Remuxing failed. Saving as raw TS stream...")
                    val tsFilename = filename.removeSuffix(".mp4") + ".ts"
                    val finalOutTsFile = File(targetDir, tsFilename)
                    if (finalOutTsFile.exists()) finalOutTsFile.delete()
                    
                    try {
                        tempStitchedTs.copyTo(finalOutTsFile, overwrite = true)
                        
                        _jobs.update { list ->
                            list.map { if (it.id == jobId) it.copy(filename = tsFilename) else it }
                        }
                        saveDownloadHistory()
                        
                        if (saveToLocker) {
                            progressFlow.value = DownloadProgress.Muxing("Encrypting and moving to Private Locker...")
                            privateLockerManager.saveUriToLocker(Uri.fromFile(finalOutTsFile), tsFilename, "video/mp2t")
                            finalOutTsFile.delete()
                            val finalLockerFile = File(context.filesDir, "locker/$tsFilename")
                            progressFlow.value = DownloadProgress.Complete(finalLockerFile, finalLockerFile.length())
                        } else {
                            progressFlow.value = DownloadProgress.Muxing("Saving to public Downloads...")
                            val savedFile = saveToPublicDownloads(finalOutTsFile, tsFilename)
                            progressFlow.value = DownloadProgress.Complete(savedFile, savedFile.length())
                        }
                        return@withContext
                    } catch (e: Exception) {
                        Log.e("DownloadEngine", "Failed to copy fallback TS file", e)
                        progressFlow.value = DownloadProgress.Error("Stitching and MP4 conversion failed")
                        return@withContext
                    }
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }

        if (success) {
            if (saveToLocker) {
                progressFlow.value = DownloadProgress.Muxing("Encrypting and moving to Private Locker...")
                val mimeType = if (filename.endsWith(".mp3")) "audio/mpeg" else "video/mp4"
                privateLockerManager.saveUriToLocker(Uri.fromFile(finalOutFile), filename, mimeType)
                finalOutFile.delete()
                val finalLockerFile = File(context.filesDir, "locker/$filename")
                progressFlow.value = DownloadProgress.Complete(finalLockerFile, finalLockerFile.length())
            } else {
                progressFlow.value = DownloadProgress.Muxing("Saving to public Downloads...")
                val savedFile = saveToPublicDownloads(finalOutFile, filename)
                progressFlow.value = DownloadProgress.Complete(savedFile, savedFile.length())
            }
        } else {
            progressFlow.value = DownloadProgress.Error("Stitching and MP4 conversion failed")
        }
    }

    private fun fetchTextUrl(urlStr: String, referrerUrl: String? = null): String {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            if (!referrerUrl.isNullOrEmpty()) {
                connection.setRequestProperty("Referer", referrerUrl)
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
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

    private fun downloadSegmentFile(
        urlStr: String,
        file: File,
        keyInfo: EncryptionKeyInfo?,
        keyBytes: ByteArray?,
        segmentIndex: Int,
        referrerUrl: String?,
        isCancelled: () -> Boolean
    ): Long {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        if (!referrerUrl.isNullOrEmpty()) {
            connection.setRequestProperty("Referer", referrerUrl)
        }
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        connection.connect()
        
        val rawBytes = connection.inputStream.use { input ->
            input.readBytes()
        }
        
        if (isCancelled()) {
            throw kotlinx.coroutines.CancellationException("Download cancelled")
        }
        
        val decryptedBytes = if (keyInfo != null && keyBytes != null) {
            decryptAes128(rawBytes, keyBytes, keyInfo.iv ?: getSequenceIv(segmentIndex))
        } else {
            rawBytes
        }
        
        FileOutputStream(file).use { output ->
            output.write(decryptedBytes)
        }
        return decryptedBytes.size.toLong()
    }

    private fun saveToPublicDownloads(sourceFile: File, filename: String): File {
        val resolver = context.contentResolver
        val mimeType = when {
            filename.endsWith(".mp3") -> "audio/mpeg"
            filename.endsWith(".ts") -> "video/mp2t"
            else -> "video/mp4"
        }
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val targetUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (targetUri != null) {
            try {
                resolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                sourceFile.delete()
                return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
            } catch (e: Exception) {
                Log.e("DownloadEngine", "Failed to save via MediaStore, falling back to direct copy", e)
            }
        }
        
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val fallbackFile = File(fallbackDir, filename)
        try {
            sourceFile.copyTo(fallbackFile, overwrite = true)
            sourceFile.delete()
            return fallbackFile
        } catch (e: Exception) {
            Log.e("DownloadEngine", "Failed fallback file copy", e)
        }
        return sourceFile
    }

    private fun parseM3U8MasterPlaylist(baseUrl: String, content: String): List<Pair<String, String>> {
        val variants = mutableListOf<Pair<String, String>>()
        val lines = content.lines()
        var currentQuality = ""
        val baseUri = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(trimmed)
                if (resMatch != null) {
                    val res = resMatch.groupValues[1]
                    val height = res.substringAfter("x").toIntOrNull() ?: 0
                    currentQuality = "${height}p"
                } else {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(trimmed)
                    if (bwMatch != null) {
                        val kbps = (bwMatch.groupValues[1].toIntOrNull() ?: 0) / 1000
                        currentQuality = "${kbps}kbps"
                    } else {
                        currentQuality = "Unknown Quality"
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentQuality.isNotEmpty()) {
                val fullUrl = if (trimmed.startsWith("http")) trimmed else "$baseUri$trimmed"
                variants.add(fullUrl to currentQuality)
                currentQuality = ""
            }
        }
        
        return variants.distinctBy { it.second }.sortedByDescending { 
            it.second.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
        }
    }

    private fun remuxTsToMp4(inputFile: File, outputFile: File): Boolean {
        var extractor: android.media.MediaExtractor? = null
        var muxer: android.media.MediaMuxer? = null
        try {
            extractor = android.media.MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            
            muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val trackCount = extractor.trackCount
            val trackMap = HashMap<Int, Int>()
            
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstTrackId = muxer.addTrack(format)
                    trackMap[i] = dstTrackId
                }
            }
            
            if (trackMap.isEmpty()) {
                Log.e("DownloadEngine", "MediaMuxer error: No video or audio tracks found in stitched TS file.")
                return false
            }
            
            muxer.start()
            
            val maxBufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val lastTrackTimestamps = HashMap<Int, Long>()
            
            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                
                val dstTrackId = trackMap[trackIndex]
                if (dstTrackId != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        break
                    }
                    
                    val originalTime = extractor.sampleTime
                    val lastTime = lastTrackTimestamps[dstTrackId] ?: -1L
                    val adjustedTime = if (originalTime <= lastTime) {
                        lastTime + 1000L // Increment by 1ms to maintain strictly monotonic presentation timestamps
                    } else {
                        originalTime
                    }
                    lastTrackTimestamps[dstTrackId] = adjustedTime
                    
                    bufferInfo.presentationTimeUs = adjustedTime
                    bufferInfo.flags = extractor.sampleFlags
                    
                    muxer.writeSampleData(dstTrackId, buffer, bufferInfo)
                }
                extractor.advance()
            }
            
            muxer.stop()
            Log.i("DownloadEngine", "MediaMuxer successfully remuxed HLS TS stream into standard MP4.")
            return true
        } catch (e: Exception) {
            Log.e("DownloadEngine", "MediaMuxer remuxing failed", e)
            return false
        } finally {
            try {
                extractor?.release()
            } catch (e: Exception) {}
            try {
                muxer?.release()
            } catch (e: Exception) {}
        }
    }
    data class EncryptionKeyInfo(
        val method: String,
        val uri: String,
        val iv: ByteArray?
    )

    private fun parseEncryptionKeyInfo(manifestUrl: String, manifestContent: String): EncryptionKeyInfo? {
        val line = manifestContent.lines().firstOrNull { it.startsWith("#EXT-X-KEY") } ?: return null
        val methodMatch = Regex("METHOD=([^,]+)").find(line)
        val method = methodMatch?.groupValues?.get(1) ?: return null
        if (method != "AES-128") return null
        
        val uriMatch = Regex("URI=\"([^\"]+)\"").find(line)
        val rawUri = uriMatch?.groupValues?.get(1) ?: return null
        
        val baseUri = manifestUrl.substring(0, manifestUrl.lastIndexOf("/") + 1)
        val resolvedUri = if (rawUri.startsWith("http")) rawUri else "$baseUri$rawUri"
        
        val ivMatch = Regex("IV=0x([0-9a-fA-F]+)").find(line)
        val ivBytes = ivMatch?.groupValues?.get(1)?.let { ivStr ->
            val bytes = ByteArray(16)
            for (i in 0 until 16) {
                val index = i * 2
                if (index + 2 <= ivStr.length) {
                    bytes[i] = ivStr.substring(index, index + 2).toInt(16).toByte()
                }
            }
            bytes
        }
        
        return EncryptionKeyInfo(method, resolvedUri, ivBytes)
    }

    private fun fetchKeyBytes(keyUrl: String, referrerUrl: String?): ByteArray? {
        return try {
            val connection = URL(keyUrl).openConnection() as HttpURLConnection
            if (!referrerUrl.isNullOrEmpty()) {
                connection.setRequestProperty("Referer", referrerUrl)
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DownloadEngine", "Failed to fetch encryption key: $keyUrl", e)
            null
        }
    }

    private fun decryptAes128(bytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(bytes)
    }

    private fun getSequenceIv(sequenceNumber: Int): ByteArray {
        val iv = ByteArray(16)
        iv[12] = ((sequenceNumber shr 24) and 0xFF).toByte()
        iv[13] = ((sequenceNumber shr 16) and 0xFF).toByte()
        iv[14] = ((sequenceNumber shr 8) and 0xFF).toByte()
        iv[15] = (sequenceNumber and 0xFF).toByte()
        return iv
    }

    private fun saveDownloadHistory() {
        val file = File(context.filesDir, "download_history.json")
        try {
            val jsonArray = org.json.JSONArray()
            _jobs.value.forEach { job ->
                val progressVal = job.progress.value
                val obj = org.json.JSONObject().apply {
                    put("id", job.id)
                    put("filename", job.filename)
                    put("url", job.url)
                    put("saveToLocker", job.saveToLocker)
                    
                    when (progressVal) {
                        is DownloadProgress.Downloading -> {
                            put("status", "downloading")
                            put("percent", progressVal.percent)
                            put("bytes", progressVal.bytesDownloaded)
                        }
                        is DownloadProgress.Muxing -> {
                            put("status", "muxing")
                            put("message", progressVal.message)
                        }
                        is DownloadProgress.Complete -> {
                            put("status", "complete")
                            put("filePath", progressVal.file.absolutePath)
                            put("bytes", progressVal.sizeBytes)
                        }
                        is DownloadProgress.Error -> {
                            put("status", "error")
                            put("message", progressVal.message)
                        }
                    }
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("DownloadEngine", "Error saving download history", e)
        }
    }

    private fun loadDownloadHistory() {
        val file = File(context.filesDir, "download_history.json")
        if (!file.exists()) return
        try {
            val jsonStr = file.readText()
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<DownloadJob>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val filename = obj.getString("filename")
                val url = obj.getString("url")
                val saveToLocker = obj.getBoolean("saveToLocker")
                val status = obj.optString("status", "")
                
                val progressFlow = when (status) {
                    "complete" -> {
                        val filePath = obj.getString("filePath")
                        val bytes = obj.optLong("bytes", 0L)
                        MutableStateFlow<DownloadProgress>(DownloadProgress.Complete(File(filePath), bytes))
                    }
                    "error" -> {
                        val message = obj.optString("message", "Unknown error")
                        MutableStateFlow<DownloadProgress>(DownloadProgress.Error(message))
                    }
                    else -> {
                        MutableStateFlow<DownloadProgress>(DownloadProgress.Error("Interrupted"))
                    }
                }
                
                list.add(
                    DownloadJob(
                        id = id,
                        filename = filename,
                        url = url,
                        saveToLocker = saveToLocker,
                        progress = progressFlow
                    )
                )
            }
            _jobs.value = list
        } catch (e: Exception) {
            Log.e("DownloadEngine", "Error loading download history", e)
        }
    }
}
