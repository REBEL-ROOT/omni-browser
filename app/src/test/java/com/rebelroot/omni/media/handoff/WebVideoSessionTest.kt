/*
 * Omni Browser — Web Video Session Tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.media.handoff

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebVideoSessionTest {

    @Test
    fun `test WebVideoSession creation and properties`() {
        val session = WebVideoSession(
            sessionId = "test_session_1",
            tabId = "tab_123",
            videoElementId = "omni_vid_1",
            sourceUri = "https://example.com/video.mp4",
            pageUrl = "https://example.com/watch",
            title = "Test Video",
            durationMs = 60000L,
            currentPositionMs = 15000L,
            isPaused = false,
            playbackRate = 1.5f,
            volume = 0.8f,
            muted = false,
            mimeType = "video/mp4",
            sourceType = MediaSourceType.DIRECT_MP4,
            videoWidth = 1920,
            videoHeight = 1080
        )

        assertEquals("test_session_1", session.sessionId)
        assertEquals("tab_123", session.tabId)
        assertEquals("omni_vid_1", session.videoElementId)
        assertEquals("https://example.com/video.mp4", session.sourceUri)
        assertEquals(60000L, session.durationMs)
        assertEquals(15000L, session.currentPositionMs)
        assertFalse(session.isPaused)
        assertEquals(1.5f, session.playbackRate, 0.001f)
        assertEquals(0.8f, session.volume, 0.001f)
        assertFalse(session.muted)
        assertTrue(session.isDownloadable)
        assertTrue(session.isNativeHandoffSupported)
        assertFalse(session.isLiveStream)
    }

    @Test
    fun `test clampPosition within bounds`() {
        val session = WebVideoSession(
            sessionId = "test_session_2",
            tabId = "tab_123",
            videoElementId = "omni_vid_2",
            sourceUri = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            durationMs = 50000L,
            currentPositionMs = 10000L
        )

        assertEquals(0L, session.clampPosition(-500L))
        assertEquals(25000L, session.clampPosition(25000L))
        assertEquals(50000L, session.clampPosition(60000L))
    }

    @Test
    fun `test updateNativeProgress updates state correctly`() {
        val session = WebVideoSession(
            sessionId = "test_session_3",
            tabId = "tab_123",
            videoElementId = "omni_vid_3",
            sourceUri = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            durationMs = 100000L,
            currentPositionMs = 5000L,
            isPaused = true
        )

        session.updateNativeProgress(
            positionMs = 45000L,
            isPlaying = true,
            rate = 1.25f,
            vol = 0.9f,
            isMuted = false
        )

        assertEquals(45000L, session.currentPositionMs)
        assertFalse(session.isPaused)
        assertEquals(1.25f, session.playbackRate, 0.001f)
        assertEquals(0.9f, session.volume, 0.001f)
        assertFalse(session.muted)
        assertEquals(WebVideoSessionState.NATIVE_PLAYING, session.state)

        session.updateNativeProgress(
            positionMs = 50000L,
            isPlaying = false
        )

        assertEquals(50000L, session.currentPositionMs)
        assertTrue(session.isPaused)
        assertEquals(WebVideoSessionState.NATIVE_PAUSED, session.state)
    }

    @Test
    fun `test JSON serialization and deserialization`() {
        val json = JSONObject().apply {
            put("sessionId", "s_999")
            put("tabId", "tab_abc")
            put("videoId", "vid_777")
            put("sourceUri", "https://example.com/stream.m3u8")
            put("pageUrl", "https://example.com/watch?v=1")
            put("title", "Live Stream")
            put("currentPositionMs", 34200L)
            put("durationMs", 120000L)
            put("isPaused", false)
            put("playbackRate", 1.0)
            put("volume", 1.0)
            put("muted", false)
            put("mimeType", "application/x-mpegURL")
            put("videoWidth", 1280)
            put("videoHeight", 720)
        }

        val session = WebVideoSession.fromJson(json)

        assertEquals("s_999", session.sessionId)
        assertEquals("tab_abc", session.tabId)
        assertEquals("vid_777", session.videoElementId)
        assertEquals("https://example.com/stream.m3u8", session.sourceUri)
        assertEquals(34200L, session.currentPositionMs)
        assertEquals(120000L, session.durationMs)
        assertFalse(session.isPaused)
        assertEquals(MediaSourceType.HLS, session.sourceType)

        val restoreJson = session.toRestoreJson()
        assertEquals("s_999", restoreJson.getString("sessionId"))
        assertEquals("vid_777", restoreJson.getString("videoId"))
        assertEquals("https://example.com/stream.m3u8", restoreJson.getString("sourceUri"))
        assertEquals(34200L, restoreJson.getLong("currentTimeMs"))
        assertTrue(restoreJson.getBoolean("isPlaying"))
    }

    @Test
    fun `test state machine transitions`() {
        val session = WebVideoSession(
            sessionId = "test_session_states",
            tabId = "tab_1",
            videoElementId = "vid_1",
            sourceUri = "https://example.com/video.mp4",
            pageUrl = "https://example.com"
        )

        assertEquals(WebVideoSessionState.SITE_PLAYING, session.state)

        session.state = WebVideoSessionState.HANDOFF_TO_NATIVE
        assertTrue(session.state.isTransitioning)

        session.state = WebVideoSessionState.NATIVE_PREPARING
        assertTrue(session.state.isNativeActive)

        session.state = WebVideoSessionState.NATIVE_PLAYING
        assertTrue(session.state.isNativeActive)

        session.state = WebVideoSessionState.HANDOFF_TO_SITE
        assertTrue(session.state.isTransitioning)

        session.state = WebVideoSessionState.SITE_RESTORING
        assertTrue(session.state.isSiteActive)

        session.state = WebVideoSessionState.RELEASED
        assertFalse(session.state.isNativeActive)
        assertFalse(session.state.isSiteActive)
    }
}
