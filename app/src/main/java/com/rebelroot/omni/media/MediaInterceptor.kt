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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaInterceptor {

    data class DetectedMedia(
        val url: String,
        val type: MediaType,
        val quality: String? = null,
        val isDrmProtected: Boolean = false,
        val sizeBytes: Long? = null
    )

    enum class MediaType { MP4, WEBM, HLS, DASH, AUDIO }

    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    /**
     * When false (default), media detected on YouTube / Google domains is ignored so the
     * native player and downloader stay ToS-compliant. When the user enables "Enable on YouTube"
     * in settings, this is flipped and those streams are captured like any other site.
     */
    var isYouTubeEnabled = false

    private fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("googlevideo.com")
    }

    private fun isTrackingOrStaticResource(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("ping.gif") ||
               lower.endsWith(".gif") || lower.contains(".gif?") ||
               lower.endsWith(".png") || lower.contains(".png?") ||
               lower.endsWith(".jpg") || lower.contains(".jpg?") ||
               lower.endsWith(".jpeg") || lower.contains(".jpeg?") ||
               lower.endsWith(".svg") || lower.contains(".svg?") ||
               lower.endsWith(".webp") || lower.contains(".webp?") ||
               lower.contains("analytics") || lower.contains("telemetry") ||
               lower.contains("pixel") || lower.contains("/ping")
    }

    /**
     * Called when the network interceptor detects a media asset request.
     */
    fun onMediaRequestDetected(url: String, headers: Map<String, String>? = null) {
        if (isTrackingOrStaticResource(url)) return
        if (isYouTubeUrl(url) && !isYouTubeEnabled) return
        val type = classifyUrl(url) ?: return
        val isDrm = url.contains("drm") || url.contains("widevine") || url.contains("license")
        
        if (type == MediaType.HLS) {
            fetchAndParseHlsQualities(url, isDrm)
        } else {
            val media = DetectedMedia(
                url = url,
                type = type,
                quality = extractQuality(url) ?: "Source HD",
                isDrmProtected = isDrm,
                sizeBytes = headers?.get("Content-Length")?.toLongOrNull()
            )
            addMedia(media)
            Log.i("MediaInterceptor", "🎥 Intercepted Media: ${media.type} | DRM: ${media.isDrmProtected} | URL: $url")
        }
    }

    /**
     * Aggressive capturing callback for MSE (Media Source Extensions) or Blob links.
     * Triggered by our injected WebExtension page script.
     */
    fun onAggressiveMediaGrabbed(url: String, mimeType: String) {
        if (isTrackingOrStaticResource(url)) return
        if (isYouTubeUrl(url) && !isYouTubeEnabled) return
        val type = when {
            mimeType.contains("video/mp4") -> MediaType.MP4
            mimeType.contains("video/webm") -> MediaType.WEBM
            mimeType.contains("application/x-mpegURL") || mimeType.contains("mpegurl") -> MediaType.HLS
            mimeType.contains("dash+xml") -> MediaType.DASH

            mimeType.contains("audio/") -> MediaType.AUDIO
            else -> MediaType.MP4
        }

        if (type == MediaType.HLS) {
            fetchAndParseHlsQualities(url, false)
        } else {
            val media = DetectedMedia(
                url = url,
                type = type,
                quality = "Source HD",
                isDrmProtected = false // MSE intercepted are typically unencrypted or local streams
            )
            addMedia(media)
            Log.i("MediaInterceptor", "⚡ Aggressively Captured Media: ${media.type} | URL: $url")
        }
    }

    private fun fetchAndParseHlsQualities(urlStr: String, isDrm: Boolean) {
        scope.launch {
            try {
                val connection = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                connection.connect()
                val manifestContent = connection.inputStream.bufferedReader().use { it.readText() }
                
                val parsedVariants = parseM3U8MasterPlaylist(urlStr, manifestContent)
                
                if (parsedVariants.isNotEmpty()) {
                    parsedVariants.forEach { variant ->
                        addMedia(DetectedMedia(
                            url = variant.first,
                            type = MediaType.HLS,
                            quality = variant.second,
                            isDrmProtected = isDrm
                        ))
                    }
                } else {
                    // Not a master playlist or parsing failed, add the original
                    addMedia(DetectedMedia(
                        url = urlStr,
                        type = MediaType.HLS,
                        quality = "Auto / Source",
                        isDrmProtected = isDrm
                    ))
                }
            } catch (e: Exception) {
                Log.e("MediaInterceptor", "Failed to fetch/parse HLS manifest", e)
                addMedia(DetectedMedia(
                    url = urlStr,
                    type = MediaType.HLS,
                    quality = "Auto / Source",
                    isDrmProtected = isDrm
                ))
            }
        }
    }

    /**
     * Parses an M3U8 Master Playlist and returns a list of Pair(URL, QualityName)
     */
    private fun parseM3U8MasterPlaylist(baseUrl: String, content: String): List<Pair<String, String>> {
        val variants = mutableListOf<Pair<String, String>>()
        val lines = content.lines()
        var currentQuality = ""
        
        val baseUri = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                // Try to extract resolution
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(trimmed)
                if (resMatch != null) {
                    val res = resMatch.groupValues[1]
                    val height = res.substringAfter("x").toIntOrNull() ?: 0
                    currentQuality = "${height}p"
                } else {
                    // Fallback to bandwidth
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
        
        // Remove duplicates by quality (keeping the highest bandwidth one if multiple exist, though for simplicity we just distinctBy)
        return variants.distinctBy { it.second }.sortedByDescending { 
            it.second.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
        }
    }

    private fun addMedia(media: DetectedMedia) {
        _detectedMedia.update { current ->
            if (current.any { it.url == media.url }) current
            else current + media
        }
    }

    fun clear() {
        _detectedMedia.value = emptyList()
    }

    private fun classifyUrl(url: String): MediaType? {
        val lower = url.lowercase()

        // Check for mime query param first (used by googlevideo.com / YouTube)
        val mimeFromQuery = try {
            android.net.Uri.parse(url).getQueryParameter("mime")?.let {
                java.net.URLDecoder.decode(it, "UTF-8").lowercase()
            }
        } catch (e: Exception) { null }

        if (mimeFromQuery != null) {
            return when {
                mimeFromQuery.contains("video/mp4") -> MediaType.MP4
                mimeFromQuery.contains("video/webm") -> MediaType.WEBM
                mimeFromQuery.contains("application/x-mpegurl") || mimeFromQuery.contains("mpegurl") -> MediaType.HLS
                mimeFromQuery.contains("dash+xml") -> MediaType.DASH
                mimeFromQuery.contains("audio/") -> MediaType.AUDIO
                else -> null
            }
        }

        return when {
            lower.endsWith(".m3u8") || lower.contains(".m3u8") || lower.contains("m3u8") -> MediaType.HLS
            lower.endsWith(".mpd") || lower.contains(".mpd") || lower.contains("/dash/") -> MediaType.DASH
            lower.endsWith(".mp4") || lower.contains(".mp4") -> MediaType.MP4
            lower.endsWith(".webm") || lower.contains(".webm") -> MediaType.WEBM
            lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".m4a") -> MediaType.AUDIO
            else -> null
        }
    }


    private fun extractQuality(url: String): String? {
        val regex = Regex("(\\d{3,4})p")
        return regex.find(url)?.groupValues?.get(1)?.let { "${it}p" }
    }
}
