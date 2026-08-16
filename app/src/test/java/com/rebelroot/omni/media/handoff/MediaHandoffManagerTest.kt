/*
 * Omni Browser — Media Handoff Manager Tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.media.handoff

import org.junit.Assert.*
import org.junit.Test

class MediaHandoffManagerTest {

    @Test
    fun `test createHandoffId format`() {
        val id1 = MediaHandoffManager.createHandoffId()
        val id2 = MediaHandoffManager.createHandoffId()

        assertTrue(id1.startsWith("h_"))
        assertTrue(id2.startsWith("h_"))
        assertNotEquals(id1, id2)
    }

    @Test
    fun `test staleness detection`() {
        val freshHandoff = MediaHandoff(
            handoffId = "h_1",
            tabId = "tab_1",
            sourceUri = "https://example.com/v.mp4",
            pageUrl = "https://example.com",
            currentPositionMs = 5000L,
            durationMs = 60000L,
            isPaused = false,
            sourceType = MediaSourceType.DIRECT_MP4,
            capturedAt = System.currentTimeMillis()
        )
        assertFalse(MediaHandoffManager.isHandoffStale(freshHandoff))

        val staleHandoff = freshHandoff.copy(capturedAt = System.currentTimeMillis() - 40000L)
        assertTrue(MediaHandoffManager.isHandoffStale(staleHandoff, maxAgeMs = 30000L))
    }

    @Test
    fun `test tab matching`() {
        val handoff = MediaHandoff(
            handoffId = "h_1",
            tabId = "tab_1",
            sourceUri = "https://example.com/v.mp4",
            pageUrl = "https://example.com",
            currentPositionMs = 5000L,
            durationMs = 60000L,
            isPaused = false,
            sourceType = MediaSourceType.DIRECT_MP4
        )

        assertTrue(MediaHandoffManager.matchesCurrentTab(handoff, "tab_1"))
        assertFalse(MediaHandoffManager.matchesCurrentTab(handoff, "tab_2"))
        assertTrue(MediaHandoffManager.matchesCurrentTab(handoff, ""))
    }

    @Test
    fun `test validateForConsumption`() {
        val validHandoff = MediaHandoff(
            handoffId = "h_1",
            tabId = "tab_1",
            sourceUri = "https://example.com/v.mp4",
            pageUrl = "https://example.com",
            currentPositionMs = 5000L,
            durationMs = 60000L,
            isPaused = false,
            sourceType = MediaSourceType.DIRECT_MP4,
            capturedAt = System.currentTimeMillis()
        )

        assertNull(MediaHandoffManager.validateForConsumption(validHandoff, "tab_1", "https://example.com/v.mp4"))

        val staleError = MediaHandoffManager.validateForConsumption(
            validHandoff.copy(capturedAt = System.currentTimeMillis() - 60000L),
            "tab_1",
            "https://example.com/v.mp4"
        )
        assertNotNull(staleError)
        assertTrue(staleError!!.contains("stale"))

        val tabMismatchError = MediaHandoffManager.validateForConsumption(validHandoff, "tab_2", "https://example.com/v.mp4")
        assertNotNull(tabMismatchError)
        assertTrue(tabMismatchError!!.contains("mismatch"))

        val uriMismatchError = MediaHandoffManager.validateForConsumption(validHandoff, "tab_1", "https://example.com/other.mp4")
        assertNotNull(uriMismatchError)
        assertTrue(uriMismatchError!!.contains("mismatch"))

        val unsupportedError = MediaHandoffManager.validateForConsumption(
            validHandoff.copy(sourceType = MediaSourceType.BLOB),
            "tab_1",
            "https://example.com/v.mp4"
        )
        assertNotNull(unsupportedError)
        assertTrue(unsupportedError!!.contains("Unsupported"))
    }
}
