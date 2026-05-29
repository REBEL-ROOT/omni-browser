package com.omni.browser.media

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    /**
     * Called when the network interceptor detects a media asset request.
     */
    fun onMediaRequestDetected(url: String, headers: Map<String, String>? = null) {
        val type = classifyUrl(url) ?: return
        val isDrm = url.contains("drm") || url.contains("widevine") || url.contains("license")
        
        val media = DetectedMedia(
            url = url,
            type = type,
            quality = extractQuality(url),
            isDrmProtected = isDrm,
            sizeBytes = headers?.get("Content-Length")?.toLongOrNull()
        )

        _detectedMedia.update { current ->
            if (current.any { it.url == url }) current
            else current + media
        }
        
        Log.i("MediaInterceptor", "🎥 Intercepted Media: ${media.type} | DRM: ${media.isDrmProtected} | URL: $url")
    }

    /**
     * Aggressive capturing callback for MSE (Media Source Extensions) or Blob links.
     * Triggered by our injected WebExtension page script.
     */
    fun onAggressiveMediaGrabbed(url: String, mimeType: String) {
        val type = when {
            mimeType.contains("video/mp4") -> MediaType.MP4
            mimeType.contains("video/webm") -> MediaType.WEBM
            mimeType.contains("application/x-mpegURL") || mimeType.contains("mpegurl") -> MediaType.HLS
            mimeType.contains("dash+xml") -> MediaType.DASH
            mimeType.contains("audio/") -> MediaType.AUDIO
            else -> MediaType.MP4
        }

        val media = DetectedMedia(
            url = url,
            type = type,
            quality = "Source HD",
            isDrmProtected = false // MSE intercepted are typically unencrypted or local streams
        )

        _detectedMedia.update { current ->
            if (current.any { it.url == url }) current
            else current + media
        }

        Log.i("MediaInterceptor", "⚡ Aggressively Captured Media: ${media.type} | URL: $url")
    }

    fun clear() {
        _detectedMedia.value = emptyList()
    }

    private fun classifyUrl(url: String): MediaType? {
        val lower = url.lowercase()
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
