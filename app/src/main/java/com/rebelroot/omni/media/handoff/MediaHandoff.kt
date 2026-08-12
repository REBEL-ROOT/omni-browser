/*
 * Omni Browser — Media Handoff Model
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Immutable snapshot of a website HTML5 <video> element's live playback state,
 * captured at the moment the user requests native player takeover.
 *
 * The handoff is consumed exactly once (see [BrowserViewModel.consumePendingHandoff])
 * to prevent stale state from leaking between tabs or sequential handoffs.
 */

package com.rebelroot.omni.media.handoff

/**
 * A structured media-handoff object capturing the live state of a website's
 * HTML5 `<video>` element at the moment of handoff request.
 *
 * @param handoffId unique ID for this handoff (UUID), consumed once
 * @param tabId the browser tab/session that originated the handoff
 * @param sourceUri the resolved media source URI (currentSrc or best manifest)
 * @param pageUrl the page URL (used as referrer)
 * @param title document title or video-specific title
 * @param currentPositionMs live currentTime of the video element, in milliseconds
 * @param durationMs live duration of the video element, in milliseconds; null if Infinity/NaN/live
 * @param isPaused whether the video element was paused at capture time
 * @param playbackRate the video element's playbackRate (default 1.0f)
 * @param volume the video element's volume, 0.0..1.0
 * @param muted whether the video element was muted
 * @param mimeType optional MIME type hint from the video element
 * @param sourceType classification of the source (direct, HLS, blob, DRM, etc.)
 * @param capturedAt System.currentTimeMillis() when the state was captured
 * @param videoWidth intrinsic video width in pixels
 * @param videoHeight intrinsic video height in pixels
 */
data class MediaHandoff(
    val handoffId: String,
    val tabId: String,
    val sourceUri: String,
    val pageUrl: String,
    val title: String?,
    val currentPositionMs: Long,
    val durationMs: Long?,
    val isPaused: Boolean,
    val playbackRate: Float,
    val volume: Float,
    val muted: Boolean,
    val mimeType: String?,
    val sourceType: MediaSourceType,
    val capturedAt: Long,
    val videoWidth: Int,
    val videoHeight: Int
) {
    /** True if this handoff represents a live stream (duration unknown or Infinity). */
    val isLiveStream: Boolean
        get() = durationMs == null || durationMs <= 0L

    /** Playback position as seconds (for logging / display). */
    val currentPositionSeconds: Float
        get() = currentPositionMs / 1000f

    /** Duration as seconds, or null for live streams. */
    val durationSeconds: Float?
        get() = durationMs?.let { it / 1000f }
}
