/*
 * Omni Browser — Web Video Session Model
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Authoritative single media session model representing a webpage HTML5 <video>
 * element under Omni control and its handoff to/from the native Media3/ExoPlayer.
 */

package com.rebelroot.omni.media.handoff

import org.json.JSONObject

/**
 * State machine for the single authoritative video session.
 */
enum class WebVideoSessionState {
    /** Playing in the webpage HTML5 <video> element. */
    SITE_PLAYING,

    /** Paused in the webpage HTML5 <video> element. */
    SITE_PAUSED,

    /** User initiated handoff to native player; capturing web state & pausing webpage. */
    HANDOFF_TO_NATIVE,

    /** Native ExoPlayer is initializing/buffering with captured state. */
    NATIVE_PREPARING,

    /** Native ExoPlayer is actively playing. Webpage video is paused. */
    NATIVE_PLAYING,

    /** Native ExoPlayer is paused. Webpage video remains paused. */
    NATIVE_PAUSED,

    /** User minimized/exited native player; capturing native state & preparing web seek. */
    HANDOFF_TO_SITE,

    /** Webpage video is seeking and restoring play/pause/rate/mute state. */
    SITE_RESTORING,

    /** Session completed and released cleanly. */
    RELEASED,

    /** Native preparation or handoff failed; fallback to webpage playback. */
    FAILED;

    val isNativeActive: Boolean
        get() = this in setOf(NATIVE_PREPARING, NATIVE_PLAYING, NATIVE_PAUSED)

    val isSiteActive: Boolean
        get() = this in setOf(SITE_PLAYING, SITE_PAUSED, SITE_RESTORING)

    val isTransitioning: Boolean
        get() = this in setOf(HANDOFF_TO_NATIVE, HANDOFF_TO_SITE)
}

/**
 * Authoritative model representing the single logical video session.
 *
 * Only one renderer is active at a time:
 * - When in site mode, HTML5 <video> renders and ExoPlayer does not exist.
 * - When in native mode, ExoPlayer renders and HTML5 <video> is paused.
 */
data class WebVideoSession(
    val sessionId: String,
    val tabId: String,
    val videoElementId: String,
    val sourceUri: String,
    val pageUrl: String,
    val title: String? = null,
    val durationMs: Long? = null,
    var currentPositionMs: Long = 0L,
    var isPaused: Boolean = false,
    var playbackRate: Float = 1.0f,
    var volume: Float = 1.0f,
    var muted: Boolean = false,
    val mimeType: String? = null,
    val sourceType: MediaSourceType = MediaSourceType.UNKNOWN,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val poster: String? = null,
    val cookies: String? = null,
    val referrer: String? = null,
    val origin: String? = null,
    val headers: Map<String, String> = emptyMap(),
    var state: WebVideoSessionState = if (isPaused) WebVideoSessionState.SITE_PAUSED else WebVideoSessionState.SITE_PLAYING,
    val capturedAt: Long = System.currentTimeMillis(),
    var lastUpdatedMs: Long = System.currentTimeMillis()
) {
    val isLiveStream: Boolean
        get() = durationMs == null || durationMs <= 0L

    val isDownloadable: Boolean
        get() = sourceType.isSupported &&
            sourceType != MediaSourceType.BLOB &&
            sourceType != MediaSourceType.DATA &&
            sourceType != MediaSourceType.DRM_PROTECTED &&
            (sourceUri.startsWith("http://") || sourceUri.startsWith("https://"))

    val isNativeHandoffSupported: Boolean
        get() = sourceType.isSupported

    /**
     * Clamps a position safely within [0, durationMs].
     */
    fun clampPosition(posMs: Long): Long {
        val dur = durationMs
        return when {
            posMs < 0L -> 0L
            dur != null && dur > 0L && posMs > dur -> dur
            else -> posMs
        }
    }

    /**
     * Updates playback state during native playback.
     */
    fun updateNativeProgress(
        positionMs: Long,
        isPlaying: Boolean,
        rate: Float = playbackRate,
        vol: Float = volume,
        isMuted: Boolean = muted
    ) {
        currentPositionMs = clampPosition(positionMs)
        isPaused = !isPlaying
        playbackRate = rate.coerceIn(0.25f, 4.0f)
        volume = vol.coerceIn(0.0f, 1.0f)
        muted = isMuted
        state = if (isPlaying) WebVideoSessionState.NATIVE_PLAYING else WebVideoSessionState.NATIVE_PAUSED
        lastUpdatedMs = System.currentTimeMillis()
    }

    /**
     * Converts to MediaHandoff for backward compatibility.
     */
    fun toMediaHandoff(): MediaHandoff {
        return MediaHandoff(
            handoffId = sessionId,
            tabId = tabId,
            videoElementId = videoElementId,
            sourceUri = sourceUri,
            pageUrl = pageUrl,
            title = title,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isPaused = isPaused,
            playbackRate = playbackRate,
            volume = volume,
            muted = muted,
            mimeType = mimeType,
            sourceType = sourceType,
            capturedAt = capturedAt,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            poster = poster,
            cookies = cookies,
            referrer = referrer,
            origin = origin,
            headers = headers
        )
    }

    /**
     * Generates the JSON payload to restore the webpage video state on minimize/exit.
     */
    fun toRestoreJson(): JSONObject {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("videoId", videoElementId)
            put("sourceUri", sourceUri)
            put("currentTimeMs", currentPositionMs)
            put("isPlaying", !isPaused)
            put("playbackRate", playbackRate.toDouble())
            put("volume", volume.toDouble())
            put("muted", muted)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WebVideoSession {
            val sessionId = json.optString("sessionId", json.optString("handoffId", ""))
            val tabId = json.optString("tabId", "")
            val videoElementId = json.optString("videoId", json.optString("videoElementId", ""))
            val sourceUri = json.optString("sourceUri", json.optString("url", ""))
            val pageUrl = json.optString("pageUrl", "")
            val title = json.optString("title", "").takeIf { it.isNotEmpty() }
            val currentPositionMs = json.optLong("currentPositionMs", json.optLong("currentTimeMs", 0L))
            val durationMs = if (json.has("durationMs") && !json.isNull("durationMs")) {
                json.optLong("durationMs", -1L).takeIf { it >= 0 }
            } else null
            val isPaused = json.optBoolean("isPaused", !json.optBoolean("isPlaying", true))
            val playbackRate = json.optDouble("playbackRate", 1.0).toFloat()
            val volume = json.optDouble("volume", 1.0).toFloat()
            val muted = json.optBoolean("muted", false)
            val mimeType = json.optString("mimeType", "").takeIf { it.isNotEmpty() }
            val capturedAt = json.optLong("capturedAt", System.currentTimeMillis())
            val videoWidth = json.optInt("videoWidth", 0)
            val videoHeight = json.optInt("videoHeight", 0)
            val poster = json.optString("poster", "").takeIf { it.isNotEmpty() }
            val cookies = json.optString("cookies", "").takeIf { it.isNotEmpty() }
            val referrer = json.optString("referrer", "").takeIf { it.isNotEmpty() }
            val origin = json.optString("origin", "").takeIf { it.isNotEmpty() }

            val sourceType = MediaSourceClassifier.classify(sourceUri, mimeType)

            return WebVideoSession(
                sessionId = sessionId,
                tabId = tabId,
                videoElementId = videoElementId,
                sourceUri = sourceUri,
                pageUrl = pageUrl,
                title = title,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                isPaused = isPaused,
                playbackRate = playbackRate,
                volume = volume,
                muted = muted,
                mimeType = mimeType,
                sourceType = sourceType,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                poster = poster,
                cookies = cookies,
                referrer = referrer,
                origin = origin,
                headers = emptyMap(),
                state = if (isPaused) WebVideoSessionState.SITE_PAUSED else WebVideoSessionState.SITE_PLAYING,
                capturedAt = capturedAt,
                lastUpdatedMs = capturedAt
            )
        }
    }
}
