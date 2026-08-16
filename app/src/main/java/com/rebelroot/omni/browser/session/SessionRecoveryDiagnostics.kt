/*
 * Omni Browser - Debug-only session recovery diagnostics.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.session

import android.os.Build
import android.util.Log

/**
 * Structured, debug-only diagnostics for session lifecycle, recovery, and persistence.
 *
 * **All logging is disabled in release builds.**
 *
 * Never logs:
 * - cookies
 * - passwords
 * - tokens
 * - form contents
 * - authentication secrets
 * - private page content
 */
object SessionRecoveryDiagnostics {

    private const val TAG_LIFECYCLE = "[OmniLifecycle]"
    private const val TAG_SESSION = "[OmniSession]"
    private const val TAG_RECOVERY = "[OmniRecovery]"
    private const val TAG_PERSISTENCE = "[OmniPersistence]"
    private const val TAG_STARTUP = "[OmniStartup]"

    private val isDebug: Boolean by lazy {
        try {
            Build.TYPE == "userdebug" || Build.TYPE == "eng"
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun logActivityCreated(savedInstanceStatePresent: Boolean, processRecreated: Boolean) {
        if (!isDebug) return
        val state = if (processRecreated) "PROCESS_RECREATED" else if (savedInstanceStatePresent) "ACTIVITY_RECREATED" else "FRESH"
        Log.d(TAG_LIFECYCLE, "Activity.onCreate | state=$state")
    }

    fun logActivitySaveInstanceState(activeTabId: String?) {
        if (!isDebug) return
        Log.d(TAG_LIFECYCLE, "Activity.onSaveInstanceState | activeTab=$activeTabId")
    }

    fun logActivityResume(isExternalAppHandoff: Boolean) {
        if (!isDebug) return
        Log.d(TAG_LIFECYCLE, "Activity.onResume | handoff=$isExternalAppHandoff")
    }

    fun logActivityPause() {
        if (!isDebug) return
        Log.d(TAG_LIFECYCLE, "Activity.onPause")
    }

    fun logActivityStop() {
        if (!isDebug) return
        Log.d(TAG_LIFECYCLE, "Activity.onStop")
    }

    // -------------------------------------------------------------------------
    // Session
    // -------------------------------------------------------------------------

    fun logSessionCreated(tabId: String, generationId: Long, isLazy: Boolean) {
        if (!isDebug) return
        Log.d(TAG_SESSION, "Created | tab=$tabId gen=$generationId lazy=$isLazy")
    }

    fun logSessionOpened(tabId: String, generationId: Long) {
        if (!isDebug) return
        Log.d(TAG_SESSION, "Opened | tab=$tabId gen=$generationId")
    }

    fun logSessionClosed(tabId: String, generationId: Long, reason: String) {
        if (!isDebug) return
        Log.d(TAG_SESSION, "Closed | tab=$tabId gen=$generationId reason=$reason")
    }

    fun logSessionKilled(tabId: String, generationId: Long) {
        if (!isDebug) return
        Log.d(TAG_SESSION, "onKill | tab=$tabId gen=$generationId")
    }

    fun logSessionStateChanged(tabId: String, generationId: Long, accepted: Boolean) {
        if (!isDebug) return
        if (!accepted) {
            Log.d(TAG_SESSION, "StateChange REJECTED (stale) | tab=$tabId gen=$generationId")
        }
    }

    fun logSessionPrioritySet(tabId: String, priority: String) {
        if (!isDebug) return
        Log.d(TAG_SESSION, "Priority | tab=$tabId priority=$priority")
    }

    // -------------------------------------------------------------------------
    // Recovery
    // -------------------------------------------------------------------------

    fun logRecoveryStarted(tabId: String, reason: String) {
        if (!isDebug) return
        Log.i(TAG_RECOVERY, "Started | tab=$tabId reason=$reason")
    }

    fun logRecoveryStep(tabId: String, step: String) {
        if (!isDebug) return
        Log.d(TAG_RECOVERY, "Step | tab=$tabId step=$step")
    }

    fun logRecoverySuccess(tabId: String, method: String) {
        if (!isDebug) return
        Log.i(TAG_RECOVERY, "Success | tab=$tabId method=$method")
    }

    fun logRecoveryFailed(tabId: String, fallback: String) {
        if (!isDebug) return
        Log.w(TAG_RECOVERY, "Failed | tab=$tabId fallback=$fallback")
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    fun logPersistRequested(tabId: String, debounced: Boolean) {
        if (!isDebug) return
        Log.d(TAG_PERSISTENCE, "Request | tab=$tabId debounced=$debounced")
    }

    fun logPersistCompleted(tabId: String) {
        if (!isDebug) return
        Log.d(TAG_PERSISTENCE, "Completed | tab=$tabId")
    }

    fun logPersistFailed(tabId: String, error: String) {
        if (!isDebug) return
        Log.w(TAG_PERSISTENCE, "Failed | tab=$tabId error=$error")
    }

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------

    fun logStartupPhase(phase: String, durationMs: Long? = null) {
        if (!isDebug) return
        val dur = durationMs?.let { " | ${it}ms" } ?: ""
        Log.d(TAG_STARTUP, "Phase | $phase$dur")
    }

    fun logTabRestoreDecision(tabId: String, method: String) {
        if (!isDebug) return
        Log.d(TAG_STARTUP, "TabRestore | tab=$tabId method=$method")
    }
}
