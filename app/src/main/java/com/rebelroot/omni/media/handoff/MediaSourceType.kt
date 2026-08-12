/*
 * Omni Browser — Media Source Type Classification
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Classification of media sources for the handoff system.
 * Distinguishes directly transferable sources from non-transferable
 * browser-internal sources (blob:, MSE, DRM).
 */

package com.rebelroot.omni.media.handoff

/**
 * Classification of a media source URI for handoff purposes.
 *
 * The handoff system uses this to decide whether a source can be transferred
 * from the website's HTML5 player to Omni's native ExoPlayer.
 */
enum class MediaSourceType {
    /** Direct MP4 file — fully transferable. */
    DIRECT_MP4,

    /** Direct WebM file — fully transferable. */
    DIRECT_WEBM,

    /** HLS manifest (.m3u8) — transferable, ExoPlayer handles it. */
    HLS,

    /** DASH manifest (.mpd) — transferable, ExoPlayer handles it. */
    DASH,

    /** YouTube stream — handled by existing YouTube extractor. */
    YOUTUBE,

    /** Blob URL (browser-internal MSE buffer) — non-transferable. */
    BLOB,

    /** Data URL (inline base64) — non-transferable. */
    DATA,

    /** DRM-protected stream (Widevine, PlayReady) — non-transferable. */
    DRM_PROTECTED,

    /** Unknown / unclassifiable — treated as unsupported pending validation. */
    UNKNOWN;

    /** True if this source type can be handed off to ExoPlayer. */
    val isSupported: Boolean
        get() = this in setOf(DIRECT_MP4, DIRECT_WEBM, HLS, DASH, YOUTUBE)
}
