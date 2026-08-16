/*
 * Omni Browser - Session recovery unit tests.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.session

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryTest {

    // ─────────────────────────────────────────────────────────────────────────
    // OmniSessionState serialization (versioned format round-trip)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun sessionStateJsonRoundTrip_preservesAllFields() {
        val metadata = OmniSessionState.TabMetadata(
            title = "Example Page",
            url = "https://example.com/article",
            isIncognito = false,
            lastActiveTime = 123456789L,
            canGoBack = true,
            canGoForward = false
        )
        val original = OmniSessionState(
            tabId = "tab-123",
            sessionStateBytes = "fake-serialized-state-bytes".toByteArray(Charsets.UTF_8),
            metadata = metadata,
            timestamp = 999L
        )

        val json = original.toJson()
        val restored = OmniSessionState.fromJson(json)

        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.tabId, restored.tabId)
        assertArrayEquals(original.sessionStateBytes, restored.sessionStateBytes)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(metadata.title, restored.metadata.title)
        assertEquals(metadata.url, restored.metadata.url)
        assertEquals(metadata.isIncognito, restored.metadata.isIncognito)
        assertEquals(metadata.lastActiveTime, restored.metadata.lastActiveTime)
        assertEquals(metadata.canGoBack, restored.metadata.canGoBack)
        assertEquals(metadata.canGoForward, restored.metadata.canGoForward)
    }

    @Test
    fun sessionStateJson_roundTrip_withIncognitoFlag() {
        val metadata = OmniSessionState.TabMetadata(
            title = "Private",
            url = "https://private.example.com",
            isIncognito = true,
            lastActiveTime = 555L,
            canGoBack = false,
            canGoForward = false
        )
        val original = OmniSessionState(
            tabId = "incognito-tab",
            sessionStateBytes = "private-bytes".toByteArray(Charsets.UTF_8),
            metadata = metadata
        )
        val restored = OmniSessionState.fromJson(original.toJson())
        assertTrue(restored.metadata.isIncognito)
        assertEquals("private-bytes", String(restored.sessionStateBytes, Charsets.UTF_8))
    }

    @Test
    fun sessionStateJson_base64Encoding_isValid() {
        val metadata = OmniSessionState.TabMetadata(
            title = "T", url = "U", isIncognito = false,
            lastActiveTime = 1L, canGoBack = false, canGoForward = false
        )
        val state = OmniSessionState(
            tabId = "x",
            sessionStateBytes = byteArrayOf(0, 1, 2, 3, -1, -2, 127),
            metadata = metadata
        )
        val json = state.toJson()
        val b64 = json.getString("sessionStateBytes")
        // Decode must not throw and must match original bytes.
        val decoded = java.util.Base64.getDecoder().decode(b64)
        assertArrayEquals(state.sessionStateBytes, decoded)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recovery coordinator: generation management + stale callback protection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun coordinator_nextGenerationId_isMonotonic() {
        val persistence = SessionStatePersistence(tempDir())
        // SessionStatePersistence.shutdown() cancels the scope; call it to be safe.
        persistence.shutdown()
        val coordinator = SessionRecoveryCoordinator(persistence)

        val g1 = coordinator.nextGenerationId()
        val g2 = coordinator.nextGenerationId()
        val g3 = coordinator.nextGenerationId()
        assertTrue(g2 > g1)
        assertTrue(g3 > g2)
        coordinator.shutdown()
    }

    @Test
    fun coordinator_isStaleCallback_detectsStaleGeneration() {
        val persistence = SessionStatePersistence(tempDir())
        persistence.shutdown()
        val coordinator = SessionRecoveryCoordinator(persistence)

        coordinator.registerTab("tab-a", initialGeneration = 5L)
        // Current generation is 5. A callback claiming generation 5 is valid.
        assertFalse(coordinator.isStaleCallback("tab-a", 5L))
        // A callback claiming generation 4 (old) is stale.
        assertTrue(coordinator.isStaleCallback("tab-a", 4L))
        // A callback claiming generation 6 (future — impossible) is also rejected.
        assertTrue(coordinator.isStaleCallback("tab-a", 6L))

        // Update generation to 7 → old callback (5) is now stale.
        coordinator.updateTabGeneration("tab-a", 7L)
        assertTrue(coordinator.isStaleCallback("tab-a", 5L))
        assertFalse(coordinator.isStaleCallback("tab-a", 7L))

        coordinator.shutdown()
    }

    @Test
    fun coordinator_registerAndUnregister_tabLifecycle() {
        val persistence = SessionStatePersistence(tempDir())
        persistence.shutdown()
        val coordinator = SessionRecoveryCoordinator(persistence)

        coordinator.registerTab("tab-x", 1L)
        assertFalse(coordinator.isStaleCallback("tab-x", 1L))

        coordinator.unregisterTab("tab-x")
        // After unregister, any callback for that tab is treated as stale.
        assertTrue(coordinator.isStaleCallback("tab-x", 1L))

        coordinator.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle state transitions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun coordinator_lifecycleTransitions_areConsistent() {
        val persistence = SessionStatePersistence(tempDir())
        persistence.shutdown()
        val coordinator = SessionRecoveryCoordinator(persistence)

        // Process recreated → visibility transitions → handoff
        coordinator.onProcessRecreated()
        assertEquals(SessionRecoveryCoordinator.ProcessState.RECREATED, coordinator.currentState.process)

        coordinator.onActivityVisible()
        assertEquals(SessionRecoveryCoordinator.VisibilityState.VISIBLE, coordinator.currentState.visibility)

        coordinator.onActivityBackground()
        assertEquals(SessionRecoveryCoordinator.VisibilityState.BACKGROUND, coordinator.currentState.visibility)

        coordinator.onExternalAppHandoffStarted()
        assertEquals(SessionRecoveryCoordinator.HandoffState.EXTERNAL_APP, coordinator.currentState.handoff)

        coordinator.onExternalAppHandoffEnded()
        assertEquals(SessionRecoveryCoordinator.HandoffState.NONE, coordinator.currentState.handoff)

        coordinator.onCheckpointCompleted()
        assertEquals(SessionRecoveryCoordinator.PersistenceState.CHECKPOINTED, coordinator.currentState.persistence)

        coordinator.shutdown()
    }

    private fun assertArrayEquals(a: ByteArray, b: ByteArray) {
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals("Byte at index $i differs", a[i], b[i])
        }
    }

    private fun tempDir(): java.io.File {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "omni-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SessionStatePersistence: corruption resilience (no Gecko needed)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun persistence_readAllDurableStates_handlesCorruptFileGracefully() {
        val dir = tempDir()
        val file = java.io.File(dir, "browser_session_states.json")
        // Write a corrupt (non-JSON) file.
        file.writeText("{ this is not valid json <<<< >>>>")
        val persistence = SessionStatePersistence(dir)
        // Must not throw; must return empty map.
        val result = persistence.readAllDurableStates()
        assertTrue(result.isEmpty())
        persistence.shutdown()
    }

    @Test
    fun persistence_readAllDurableStates_returnsEmptyWhenFileMissing() {
        val dir = tempDir()
        val persistence = SessionStatePersistence(dir)
        val result = persistence.readAllDurableStates()
        assertTrue(result.isEmpty())
        persistence.shutdown()
    }

    @Test
    fun persistence_removeDurableState_doesNotThrowOnMissingFile() {
        val dir = tempDir()
        val persistence = SessionStatePersistence(dir)
        // Removing a non-existent tab must not throw.
        persistence.removeDurableState("never-existed")
        persistence.shutdown()
    }
}
