/*
 * Omni Browser — Media Source Classifier Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 14: Unit tests for MediaSourceClassifier.
 */

package com.rebelroot.omni.media.handoff

import org.junit.Assert.*
import org.junit.Test

class MediaSourceClassifierTest {

    @Test
    fun `classify direct MP4`() {
        assertEquals(MediaSourceType.DIRECT_MP4, MediaSourceClassifier.classify("https://example.com/video.mp4"))
        assertEquals(MediaSourceType.DIRECT_MP4, MediaSourceClassifier.classify("https://example.com/video.m4v"))
        assertEquals(MediaSourceType.DIRECT_MP4, MediaSourceClassifier.classify("https://example.com/video.mp4?token=abc"))
    }

    @Test
    fun `classify direct WebM`() {
        assertEquals(MediaSourceType.DIRECT_WEBM, MediaSourceClassifier.classify("https://example.com/video.webm"))
        assertEquals(MediaSourceType.DIRECT_WEBM, MediaSourceClassifier.classify("https://example.com/video.webm?token=abc"))
    }

    @Test
    fun `classify HLS`() {
        assertEquals(MediaSourceType.HLS, MediaSourceClassifier.classify("https://example.com/playlist.m3u8"))
        assertEquals(MediaSourceType.HLS, MediaSourceClassifier.classify("https://example.com/hls/master.m3u8"))
    }

    @Test
    fun `classify DASH`() {
        assertEquals(MediaSourceType.DASH, MediaSourceClassifier.classify("https://example.com/manifest.mpd"))
        assertEquals(MediaSourceType.DASH, MediaSourceClassifier.classify("https://example.com/dash/stream.mpd"))
    }

    @Test
    fun `classify YouTube`() {
        assertEquals(MediaSourceType.YOUTUBE, MediaSourceClassifier.classify("https://www.youtube.com/watch?v=abc123"))
        assertEquals(MediaSourceType.YOUTUBE, MediaSourceClassifier.classify("https://youtu.be/abc123"))
        assertEquals(MediaSourceType.YOUTUBE, MediaSourceClassifier.classify("https://googlevideo.com/videoplayback?id=abc"))
    }

    @Test
    fun `classify blob URL`() {
        assertEquals(MediaSourceType.BLOB, MediaSourceClassifier.classify("blob:https://example.com/abc-123"))
    }

    @Test
    fun `classify data URL`() {
        assertEquals(MediaSourceType.DATA, MediaSourceClassifier.classify("data:text/html,<video>"))
    }

    @Test
    fun `classify DRM signals`() {
        assertEquals(MediaSourceType.DRM_PROTECTED, MediaSourceClassifier.classify("https://example.com/video.mp4?widevine=true"))
        assertEquals(MediaSourceType.DRM_PROTECTED, MediaSourceClassifier.classify("https://example.com/video.mp4?license=widevine"))
    }

    @Test
    fun `classify unknown`() {
        assertEquals(MediaSourceType.UNKNOWN, MediaSourceClassifier.classify("https://example.com/video.unknown"))
    }

    @Test
    fun `supported types`() {
        assertTrue(MediaSourceType.DIRECT_MP4.isSupported)
        assertTrue(MediaSourceType.DIRECT_WEBM.isSupported)
        assertTrue(MediaSourceType.HLS.isSupported)
        assertTrue(MediaSourceType.DASH.isSupported)
        assertTrue(MediaSourceType.YOUTUBE.isSupported)
        assertFalse(MediaSourceType.BLOB.isSupported)
        assertFalse(MediaSourceType.DATA.isSupported)
        assertFalse(MediaSourceType.DRM_PROTECTED.isSupported)
        assertFalse(MediaSourceType.UNKNOWN.isSupported)
    }

    @Test
    fun `classify with mimeType hint`() {
        assertEquals(MediaSourceType.HLS, MediaSourceClassifier.classify("https://example.com/stream", "application/vnd.apple.mpegurl"))
        assertEquals(MediaSourceType.DASH, MediaSourceClassifier.classify("https://example.com/stream", "application/dash+xml"))
        assertEquals(MediaSourceType.DIRECT_MP4, MediaSourceClassifier.classify("https://example.com/stream", "video/mp4"))
    }
}
