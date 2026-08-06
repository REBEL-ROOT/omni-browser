/*
 * Omni Browser - YouTube Video stream extractor
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.media

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object YouTubeExtractor {
    private const val TAG = "YouTubeExtractor"

    data class YouTubeStream(
        val url: String,
        val mimeType: String,
        val quality: String,
        val isAudio: Boolean,
        val isVideoOnly: Boolean = false,
        val sizeBytes: Long? = null
    )

    data class ExtractionResult(
        val title: String,
        val streams: List<YouTubeStream>
    )

    private data class ClientConfig(
        val name: String,
        val version: String,
        val userAgent: String,
        val buildPayloadContext: (JSONObject) -> Unit
    )

    // A list of fallback clients in priority order to bypass regional/device/sign-in requirements.
    private val CLIENT_CONFIGS = listOf(
        ClientConfig(
            name = "ANDROID",
            version = "19.30.36",
            userAgent = "com.google.android.youtube/19.30.36 (Linux; U; Android 11) gzip",
            buildPayloadContext = { clientObj ->
                clientObj.put("clientName", "ANDROID")
                clientObj.put("clientVersion", "19.30.36")
                clientObj.put("androidSdkVersion", 30)
                clientObj.put("hl", "en")
                clientObj.put("gl", "US")
                clientObj.put("utcOffsetMinutes", 0)
            }
        ),
        ClientConfig(
            name = "ANDROID_VR",
            version = "1.50.29",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.50.29 (Linux; U; Android 12) gzip",
            buildPayloadContext = { clientObj ->
                clientObj.put("clientName", "ANDROID_VR")
                clientObj.put("clientVersion", "1.50.29")
                clientObj.put("deviceMake", "Oculus")
                clientObj.put("deviceModel", "Quest 3")
                clientObj.put("androidSdkVersion", 32)
                clientObj.put("hl", "en")
                clientObj.put("gl", "US")
                clientObj.put("utcOffsetMinutes", 0)
            }
        ),
        ClientConfig(
            name = "TVHTML5",
            version = "7.20250312.16.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            buildPayloadContext = { clientObj ->
                clientObj.put("clientName", "TVHTML5")
                clientObj.put("clientVersion", "7.20250312.16.00")
                clientObj.put("hl", "en")
                clientObj.put("gl", "US")
                clientObj.put("utcOffsetMinutes", 0)
            }
        )
    )

    /**
     * Extracts the 11-character video ID from any valid YouTube URL (supporting www, mobile m., shorts, embeds, and redirects).
     */
    fun extractVideoId(url: String): String? {
        val cleanUrl = url.trim()
        
        // Handle youtu.be/ID
        if (cleanUrl.contains("youtu.be/")) {
            val parts = cleanUrl.split("youtu.be/")
            if (parts.size > 1) {
                return parts[1].split("?")[0].split("/")[0].take(11)
            }
        }
        // Handle shorts/ID
        if (cleanUrl.contains("/shorts/")) {
            val parts = cleanUrl.split("/shorts/")
            if (parts.size > 1) {
                return parts[1].split("?")[0].split("/")[0].take(11)
            }
        }
        // Handle embed/ID
        if (cleanUrl.contains("/embed/")) {
            val parts = cleanUrl.split("/embed/")
            if (parts.size > 1) {
                return parts[1].split("?")[0].split("/")[0].take(11)
            }
        }
        // Handle v/ID
        if (cleanUrl.contains("/v/")) {
            val parts = cleanUrl.split("/v/")
            if (parts.size > 1) {
                return parts[1].split("?")[0].split("/")[0].take(11)
            }
        }
        // Handle query parameter v=
        try {
            val uri = Uri.parse(cleanUrl)
            val vParam = uri.getQueryParameter("v")
            if (!vParam.isNullOrEmpty()) {
                return vParam.take(11)
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Intercepts and extracts streaming URLs for a YouTube video using the InnerTube API.
     * Iterates over a list of client configurations in sequence until one succeeds.
     */
    suspend fun extractStreams(videoUrl: String): ExtractionResult? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoUrl) ?: return@withContext null
        Log.i(TAG, "Extracting streams for video ID: $videoId")

        for (config in CLIENT_CONFIGS) {
            Log.i(TAG, "Attempting extraction using client: ${config.name}")
            val result = tryExtractWithClient(videoId, config)
            if (result != null && result.streams.isNotEmpty()) {
                Log.i(TAG, "Successfully extracted ${result.streams.size} streams using client ${config.name}")
                return@withContext result
            }
        }

        Log.e(TAG, "All extraction client configurations failed for video ID: $videoId")
        return@withContext null
    }

    private fun tryExtractWithClient(videoId: String, config: ClientConfig): ExtractionResult? {
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", config.userAgent)

            val jsonPayload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        config.buildPayloadContext(this)
                    })
                })
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "Client ${config.name} request failed with HTTP $responseCode")
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val jsonResponse = JSONObject(response)
            
            // Check for playability errors
            val playabilityStatus = jsonResponse.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != null && status != "OK") {
                val reason = playabilityStatus.optString("reason", "Video is not playable")
                Log.w(TAG, "Client ${config.name} reported playability status: $status - $reason")
                return null
            }

            val videoDetails = jsonResponse.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: "YouTube Video"

            val streamingData = jsonResponse.optJSONObject("streamingData") ?: return null
            val streams = mutableListOf<YouTubeStream>()

            // 1. Multiplexed formats (video + audio in one stream, e.g. 360p, 720p)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.getJSONObject(i)
                    val streamUrl = format.optString("url")
                    if (streamUrl.isNotEmpty()) {
                        val mimeType = format.optString("mimeType")
                        val quality = format.optString("qualityLabel", "Format $i")
                        val contentLength = format.optLong("contentLength", -1)
                        streams.add(YouTubeStream(
                            url = streamUrl,
                            mimeType = mimeType,
                            quality = quality,
                            isAudio = false,
                            isVideoOnly = false,
                            sizeBytes = if (contentLength > 0) contentLength else null
                        ))
                    }
                }
            }

            // 2. Adaptive formats (separate video-only and audio-only streams)
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val streamUrl = format.optString("url")
                    if (streamUrl.isNotEmpty()) {
                        val mimeType = format.optString("mimeType")
                        val isAudio = mimeType.startsWith("audio/")
                        val quality = if (isAudio) {
                            val audioQuality = format.optString("audioQuality", "AUDIO_QUALITY_MEDIUM")
                                .removePrefix("AUDIO_QUALITY_")
                            val bitrate = format.optInt("bitrate", 0) / 1000
                            if (bitrate > 0) "${bitrate}kbps ($audioQuality)" else audioQuality
                        } else {
                            format.optString("qualityLabel", "Adaptive $i")
                        }
                        val contentLength = format.optLong("contentLength", -1)
                        streams.add(YouTubeStream(
                            url = streamUrl,
                            mimeType = mimeType,
                            quality = quality,
                            isAudio = isAudio,
                            isVideoOnly = !isAudio,
                            sizeBytes = if (contentLength > 0) contentLength else null
                        ))
                    }
                }
            }

            return ExtractionResult(title, streams)
        } catch (e: Exception) {
            Log.w(TAG, "Client ${config.name} extraction failed with exception", e)
            return null
        }
    }
}
