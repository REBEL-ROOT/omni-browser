/*
 * Omni Browser — Media Source Classifier
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Classifies media source URIs into [MediaSourceType] based on URL patterns,
 * MIME type hints, and DRM signals. Used by the handoff system to decide
 * whether a source can be transferred from the website player to ExoPlayer.
 *
 * Phase 4.
 */

package com.rebelroot.omni.media.handoff

/**
 * Classifies media URIs to determine handoff support.
 *
 * This is a pure function — no Android dependencies — so it is unit-testable
 * on the JVM.
 */
object MediaSourceClassifier {

    /**
     * Classifies [uri] (and optional [mimeType] hint) into a [MediaSourceType].
     *
     * @param uri the media source URI (may be currentSrc, src, or resolved manifest)
     * @param mimeType optional MIME type from the video element or HTTP headers
     * @return the classified source type
     */
    fun classify(uri: String, mimeType: String? = null): MediaSourceType {
        val lower = uri.lowercase()

        // ── Non-transferable schemes (must be checked first) ────────────────

        if (lower.startsWith("blob:")) {
            return MediaSourceType.BLOB
        }
        if (lower.startsWith("data:")) {
            return MediaSourceType.DATA
        }

        // ── DRM signals ─────────────────────────────────────────────────────

        if (hasDrmSignals(lower, mimeType)) {
            return MediaSourceType.DRM_PROTECTED
        }

        // ── YouTube ─────────────────────────────────────────────────────────

        if (isYouTubeUrl(lower)) {
            return MediaSourceType.YOUTUBE
        }

        // ── Manifest-based streaming ────────────────────────────────────────

        if (lower.contains(".m3u8") || lower.contains("/hls/") ||
            mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            mimeType.equals("application/x-mpegurl", ignoreCase = true)
        ) {
            return MediaSourceType.HLS
        }

        if (lower.contains(".mpd") || lower.contains("/dash/") ||
            mimeType.equals("application/dash+xml", ignoreCase = true)
        ) {
            return MediaSourceType.DASH
        }

        // ── Direct file formats ─────────────────────────────────────────────

        if (lower.endsWith(".mp4") || lower.endsWith(".m4v") ||
            lower.contains(".mp4?") || lower.contains(".m4v?") ||
            mimeType.equals("video/mp4", ignoreCase = true)
        ) {
            return MediaSourceType.DIRECT_MP4
        }

        if (lower.endsWith(".webm") || lower.contains(".webm?") ||
            mimeType.equals("video/webm", ignoreCase = true)
        ) {
            return MediaSourceType.DIRECT_WEBM
        }

        // ── Fallback ────────────────────────────────────────────────────────

        return MediaSourceType.UNKNOWN
    }

    /**
     * Returns true if [type] can be handed off to ExoPlayer.
     */
    fun isSupported(type: MediaSourceType): Boolean = type.isSupported

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun isYouTubeUrl(lower: String): Boolean {
        return lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            lower.contains("googlevideo.com")
    }

    private fun hasDrmSignals(lowerUri: String, mimeType: String?): Boolean {
        // URL-based DRM signals (weak but useful heuristics)
        val urlSignals = listOf(
            "widevine", "playready", "fairplay", "clearkey",
            "drm", "license", "wvlicense", "prlicense"
        )
        if (urlSignals.any { lowerUri.contains(it) }) return true

        // MIME type DRM signals
        val drmMimeTypes = listOf(
            "application/dash+xml", // DASH may or may not be DRM — we treat it as
            // transferrable above, but if combined with DRM signals it's blocked
        )
        // We already classified DASH separately; DRM is detected by URL signals first.

        return false
    }
}
