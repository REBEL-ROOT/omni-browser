/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.media.handoff

import android.util.Log
import com.rebelroot.omni.media.MediaInterceptor

/**
 * Authoritative video source resolver for Omni Browser.
 *
 * Responsibilities:
 * 1. Resolves HTML5 <video> sources (including MSE `blob:` URLs) to exact, playable network streams.
 * 2. Scopes stream resolution to the originating tab, document, and video element.
 * 3. Filters out individual media segments (.ts, .m4s) and favors master/media playlists and progressive streams.
 * 4. Preserves stream-specific request context (cookies, referer, origin, request headers).
 * 5. Rejects unsupported, DRM-protected, or unresolvable blob sources safely without disrupting web playback.
 */
object WebVideoSourceResolver {

    private const val TAG = "WebVideoSourceResolver"

    sealed class ResolutionResult {
        data class Success(
            val resolvedUri: String,
            val mimeType: String,
            val sourceType: MediaSourceType,
            val cookies: String? = null,
            val headers: Map<String, String> = emptyMap(),
            val referrer: String? = null,
            val origin: String? = null
        ) : ResolutionResult()

        data class Unsupported(val reason: String) : ResolutionResult()
        data class UnresolvedBlob(val message: String) : ResolutionResult()
        data class NoMediaFound(val message: String) : ResolutionResult()
    }

    /**
     * Resolves the authoritative stream URL for a given video session.
     *
     * @param session The captured live video session from the webpage.
     * @param associatedStreams Stream URLs explicitly associated with this video element by JS.
     * @param tabDetectedMedia Sniffed media items strictly scoped to the originating Omni tab/page.
     */
    fun resolve(
        session: WebVideoSession,
        associatedStreams: List<String> = emptyList(),
        tabDetectedMedia: List<MediaInterceptor.DetectedMedia> = emptyList()
    ): ResolutionResult {
        val rawUri = session.sourceUri.trim()
        val rawMime = session.mimeType

        Log.d(TAG, "🔍 Resolving source for videoId=${session.videoElementId}, tabId=${session.tabId}, rawUri=$rawUri")

        // 1. Direct HTTP/HTTPS source on <video> element
        if (isDirectHttpPlayable(rawUri)) {
            val st = MediaSourceClassifier.classify(rawUri, rawMime)
            if (st.isSupported) {
                Log.i(TAG, "✅ Direct HTTP/HTTPS source resolved: $rawUri ($st)")
                return ResolutionResult.Success(
                    resolvedUri = rawUri,
                    mimeType = rawMime ?: defaultMimeFor(st),
                    sourceType = st,
                    cookies = session.cookies,
                    headers = session.headers,
                    referrer = session.referrer ?: session.pageUrl,
                    origin = session.origin
                )
            } else {
                Log.w(TAG, "⚠️ Direct source is unsupported format: $rawUri ($st)")
                return ResolutionResult.Unsupported("Format $st is not supported for native player")
            }
        }

        // 2. Explicitly associated streams captured for this specific video element
        val candidateFromAssociated = pickBestStream(associatedStreams)
        if (candidateFromAssociated != null) {
            val st = MediaSourceClassifier.classify(candidateFromAssociated, null)
            if (st.isSupported) {
                // Find matching metadata from tabDetectedMedia if available
                val matchedMeta = tabDetectedMedia.firstOrNull { it.url == candidateFromAssociated }
                Log.i(TAG, "✅ Video-associated stream resolved: $candidateFromAssociated ($st)")
                return ResolutionResult.Success(
                    resolvedUri = candidateFromAssociated,
                    mimeType = matchedMeta?.toPlaybackRequest()?.mimeType ?: defaultMimeFor(st),
                    sourceType = st,
                    cookies = matchedMeta?.cookies ?: session.cookies,
                    headers = matchedMeta?.headers ?: session.headers,
                    referrer = matchedMeta?.referrer ?: session.referrer ?: session.pageUrl,
                    origin = matchedMeta?.origin ?: session.origin
                )
            }
        }

        // 3. Tab-scoped detected media items (filtered strictly to valid playlists / progressive streams)
        val validTabStreams = tabDetectedMedia.filter { item ->
            isValidMediaStreamUrl(item.url) && !isMediaSegment(item.url)
        }

        val bestTabMedia = pickBestDetectedMedia(validTabStreams)
        if (bestTabMedia != null) {
            val st = MediaSourceClassifier.classify(bestTabMedia.url, null)
            if (st.isSupported) {
                Log.i(TAG, "✅ Tab-scoped detected stream resolved: ${bestTabMedia.url} ($st)")
                return ResolutionResult.Success(
                    resolvedUri = bestTabMedia.url,
                    mimeType = bestTabMedia.toPlaybackRequest().mimeType ?: defaultMimeFor(st),
                    sourceType = st,
                    cookies = bestTabMedia.cookies ?: session.cookies,
                    headers = bestTabMedia.headers.ifEmpty { session.headers },
                    referrer = bestTabMedia.referrer ?: session.referrer ?: session.pageUrl,
                    origin = bestTabMedia.origin ?: session.origin
                )
            }
        }

        // 4. If rawUri is a blob and no stream could be resolved
        if (rawUri.startsWith("blob:")) {
            Log.w(TAG, "❌ Blob source could not be resolved to an underlying stream: $rawUri")
            return ResolutionResult.UnresolvedBlob("Underlying media stream is still loading or unavailable for blob URL")
        }

        if (rawUri.isEmpty()) {
            return ResolutionResult.NoMediaFound("No video source URL provided")
        }

        val fallbackType = MediaSourceClassifier.classify(rawUri, rawMime)
        return if (fallbackType.isSupported) {
            ResolutionResult.Success(
                resolvedUri = rawUri,
                mimeType = rawMime ?: defaultMimeFor(fallbackType),
                sourceType = fallbackType,
                cookies = session.cookies,
                headers = session.headers,
                referrer = session.referrer ?: session.pageUrl,
                origin = session.origin
            )
        } else {
            ResolutionResult.Unsupported("Unsupported video source type: $fallbackType")
        }
    }

    private fun isDirectHttpPlayable(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (isMediaSegment(url)) return false
        val lower = url.lowercase()
        return lower.contains(".mp4") ||
               lower.contains(".m3u8") ||
               lower.contains(".mpd") ||
               lower.contains(".webm") ||
               lower.contains(".mkv") ||
               lower.contains("googlevideo.com/videoplayback")
    }

    /**
     * Checks if a URL is an individual media segment or fragment rather than a playable manifest.
     */
    fun isMediaSegment(url: String): Boolean {
        val lower = url.lowercase()
        val path = try {
            val qIdx = lower.indexOf('?')
            if (qIdx != -1) lower.substring(0, qIdx) else lower
        } catch (_: Exception) { lower }

        if (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".aac") || path.endsWith(".key")) {
            return true
        }

        if (lower.contains("/segment/") || lower.contains("/segment-") || lower.contains("/fragment/") ||
            lower.contains("/range/") || lower.contains("seg-") || lower.contains("frag-")) {
            // Only consider it a segment if it does NOT also contain .m3u8 or .mpd
            if (!lower.contains(".m3u8") && !lower.contains(".mpd")) {
                return true
            }
        }

        return false
    }

    fun isValidMediaStreamUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val lower = url.lowercase()
        if (lower.contains("analytics") || lower.contains("telemetry") || lower.contains("doubleclick")) {
            return false
        }
        return true
    }

    private fun pickBestStream(urls: List<String>): String? {
        val validUrls = urls.filter { isValidMediaStreamUrl(it) && !isMediaSegment(it) }
        if (validUrls.isEmpty()) return null

        // Priority 1: HLS manifests (.m3u8)
        val hls = validUrls.firstOrNull { it.lowercase().contains(".m3u8") || it.lowercase().contains("/hls/") }
        if (hls != null) return hls

        // Priority 2: DASH manifests (.mpd)
        val dash = validUrls.firstOrNull { it.lowercase().contains(".mpd") || it.lowercase().contains("/dash/") }
        if (dash != null) return dash

        // Priority 3: Googlevideo streams
        val gvideo = validUrls.firstOrNull { it.lowercase().contains("googlevideo.com/videoplayback") }
        if (gvideo != null) return gvideo

        // Priority 4: Direct progressive MP4 / WebM
        val mp4 = validUrls.firstOrNull { it.lowercase().contains(".mp4") }
        if (mp4 != null) return mp4

        val webm = validUrls.firstOrNull { it.lowercase().contains(".webm") }
        if (webm != null) return webm

        return validUrls.firstOrNull()
    }

    private fun pickBestDetectedMedia(items: List<MediaInterceptor.DetectedMedia>): MediaInterceptor.DetectedMedia? {
        if (items.isEmpty()) return null

        // Priority 1: HLS
        val hls = items.firstOrNull { it.type == MediaInterceptor.MediaType.HLS }
        if (hls != null) return hls

        // Priority 2: DASH
        val dash = items.firstOrNull { it.type == MediaInterceptor.MediaType.DASH }
        if (dash != null) return dash

        // Priority 3: MP4
        val mp4 = items.firstOrNull { it.type == MediaInterceptor.MediaType.MP4 }
        if (mp4 != null) return mp4

        // Priority 4: WebM
        val webm = items.firstOrNull { it.type == MediaInterceptor.MediaType.WEBM }
        if (webm != null) return webm

        return items.firstOrNull()
    }

    private fun defaultMimeFor(sourceType: MediaSourceType): String {
        return when (sourceType) {
            MediaSourceType.HLS -> "application/x-mpegURL"
            MediaSourceType.DASH -> "application/dash+xml"
            MediaSourceType.DIRECT_MP4 -> "video/mp4"
            MediaSourceType.DIRECT_WEBM -> "video/webm"
            else -> "video/mp4"
        }
    }
}
