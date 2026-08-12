/*
 * Omni Browser — Media Handoff Manager
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Manages the lifecycle of a media handoff: creation, validation,
 * staleness detection, and consume-once semantics.
 *
 * Phase 5-7: Handoff lifecycle management.
 */

package com.rebelroot.omni.media.handoff

import java.util.UUID

/**
 * Manages handoff lifecycle and validation.
 *
 * This is a stateless utility class. The actual [pendingHandoff] mutable state
 * lives in [BrowserViewModel] so it survives configuration changes and is
 * scoped to the browser session.
 */
object MediaHandoffManager {

    /** Maximum age of a handoff before it is considered stale (30 seconds). */
    const val DEFAULT_MAX_AGE_MS: Long = 30_000L

    /**
     * Generates a new unique handoff ID.
     */
    fun createHandoffId(): String = UUID.randomUUID().toString()

    /**
     * Returns true if the handoff is older than [maxAgeMs].
     *
     * A stale handoff should be discarded rather than consumed, because the
     * user may have seeked, paused, or navigated since capture.
     */
    fun isHandoffStale(handoff: MediaHandoff, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean {
        return (System.currentTimeMillis() - handoff.capturedAt) > maxAgeMs
    }

    /**
     * Returns true if the handoff originated from the given [tabId].
     */
    fun matchesCurrentTab(handoff: MediaHandoff, currentTabId: String): Boolean {
        return handoff.tabId == currentTabId
    }

    /**
     * Validates a handoff before consumption.
     *
     * @return null if valid, or a reason string if invalid
     */
    fun validateForConsumption(
        handoff: MediaHandoff,
        currentTabId: String,
        expectedSourceUri: String,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): String? {
        if (isHandoffStale(handoff, maxAgeMs)) {
            return "Handoff is stale (captured ${System.currentTimeMillis() - handoff.capturedAt}ms ago)"
        }
        if (!matchesCurrentTab(handoff, currentTabId)) {
            return "Handoff tab mismatch (expected $currentTabId, got ${handoff.tabId})"
        }
        if (handoff.sourceUri != expectedSourceUri) {
            return "Handoff source URI mismatch (expected $expectedSourceUri, got ${handoff.sourceUri})"
        }
        if (!MediaSourceClassifier.isSupported(handoff.sourceType)) {
            return "Unsupported source type: ${handoff.sourceType}"
        }
        return null
    }
}
