/*
 * Omni Browser - Central session recovery coordinator.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.session

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Central coordinator responsible for all session recovery.
 *
 * Uses orthogonal state dimensions instead of one giant state enum:
 * - **ProcessState**: ALIVE, RECREATED
 * - **GeckoState**: NOT_CREATED, OPEN, DEAD, OPENING, RESTORING, READY
 * - **VisibilityState**: VISIBLE, BACKGROUND
 * - **PersistenceState**: DIRTY, CHECKPOINTED
 * - **HandoffState**: NONE, EXTERNAL_APP
 *
 * All recovery decisions flow through this class. Recovery is single-threaded
 * on the Main dispatcher to prevent races between Activity, ViewModel, Gecko
 * callbacks, and persistence.
 */
class SessionRecoveryCoordinator(
    private val persistence: SessionStatePersistence
) {
    companion object {
        private const val TAG = "SessionRecovery"
        private const val RECOVERY_TIMEOUT_MS = 10_000L
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State dimensions
    // ─────────────────────────────────────────────────────────────────────────

    enum class ProcessState { ALIVE, RECREATED }
    enum class GeckoState { NOT_CREATED, OPEN, DEAD, OPENING, RESTORING, READY }
    enum class VisibilityState { VISIBLE, BACKGROUND }
    enum class PersistenceState { DIRTY, CHECKPOINTED }
    enum class HandoffState { NONE, EXTERNAL_APP }

    data class RecoveryState(
        val process: ProcessState = ProcessState.ALIVE,
        val gecko: GeckoState = GeckoState.NOT_CREATED,
        val visibility: VisibilityState = VisibilityState.VISIBLE,
        val persistence: PersistenceState = PersistenceState.DIRTY,
        val handoff: HandoffState = HandoffState.NONE
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Per-tab recovery tracking
    // ─────────────────────────────────────────────────────────────────────────

    data class TabRecoveryInfo(
        val tabId: String,
        var geckoState: GeckoState = GeckoState.NOT_CREATED,
        var generationId: Long = 0L,
        var isRecovering: AtomicBoolean = AtomicBoolean(false),
        var pendingRecoveryJob: Job? = null
    )

    private val tabRecoveryInfo = ConcurrentHashMap<String, TabRecoveryInfo>()

    /** Global generation counter for session instance IDs. */
    private val globalGenerationCounter = AtomicLong(0L)

    /** Current recovery state snapshot. */
    @Volatile
    var currentState = RecoveryState()
        private set

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun nextGenerationId(): Long = globalGenerationCounter.incrementAndGet()

    fun registerTab(tabId: String, initialGeneration: Long, geckoState: GeckoState = GeckoState.NOT_CREATED) {
        tabRecoveryInfo[tabId] = TabRecoveryInfo(
            tabId = tabId,
            generationId = initialGeneration,
            geckoState = geckoState
        )
    }

    fun unregisterTab(tabId: String) {
        tabRecoveryInfo.remove(tabId)
        persistence.removeDurableState(tabId)
    }

    fun updateTabGeneration(tabId: String, newGeneration: Long) {
        tabRecoveryInfo[tabId]?.generationId = newGeneration
    }

    fun updateTabGeckoState(tabId: String, state: GeckoState) {
        tabRecoveryInfo[tabId]?.geckoState = state
    }

    fun isStaleCallback(tabId: String, callbackGeneration: Long): Boolean {
        val info = tabRecoveryInfo[tabId] ?: return true
        return callbackGeneration != info.generationId
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle state transitions
    // ─────────────────────────────────────────────────────────────────────────

    fun onProcessRecreated() {
        currentState = currentState.copy(process = ProcessState.RECREATED)
        SessionRecoveryDiagnostics.logActivityCreated(savedInstanceStatePresent = true, processRecreated = true)
    }

    fun onActivityRecreated() {
        if (currentState.process != ProcessState.RECREATED) {
            currentState = currentState.copy(process = ProcessState.ALIVE)
        }
        SessionRecoveryDiagnostics.logActivityCreated(savedInstanceStatePresent = true, processRecreated = false)
    }

    fun onActivityVisible() {
        currentState = currentState.copy(visibility = VisibilityState.VISIBLE)
    }

    fun onActivityBackground() {
        currentState = currentState.copy(visibility = VisibilityState.BACKGROUND)
    }

    fun onExternalAppHandoffStarted() {
        currentState = currentState.copy(handoff = HandoffState.EXTERNAL_APP)
        SessionRecoveryDiagnostics.logActivityResume(isExternalAppHandoff = true)
    }

    fun onExternalAppHandoffEnded() {
        currentState = currentState.copy(handoff = HandoffState.NONE)
    }

    fun onCheckpointCompleted() {
        currentState = currentState.copy(persistence = PersistenceState.CHECKPOINTED)
    }

    fun onSessionDirtied() {
        currentState = currentState.copy(persistence = PersistenceState.DIRTY)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recovery orchestration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiate recovery for a tab whose GeckoSession has died (onKill) or
     * was found dead on resume.
     *
     * Recovery hierarchy:
     * 1. Latest in-memory SessionState (if same process)
     * 2. Latest durable SessionState (from disk)
     * 3. URL + metadata fallback
     */
    fun recoverTab(
        tabId: String,
        context: Context,
        runtime: GeckoRuntime,
        url: String,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        inMemoryState: GeckoSession.SessionState?,
        createSession: (GeckoSession) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val info = tabRecoveryInfo[tabId] ?: return
        if (info.isRecovering.getAndSet(true)) {
            Log.d(TAG, "Recovery already in progress for $tabId")
            return
        }

        info.pendingRecoveryJob?.cancel()
        info.geckoState = GeckoState.RESTORING

        SessionRecoveryDiagnostics.logRecoveryStarted(tabId, reason = "session_dead")

        info.pendingRecoveryJob = coordinatorScope.launch {
            try {
                withTimeout(RECOVERY_TIMEOUT_MS) {
                    val recovered = performRecovery(
                        tabId = tabId,
                        context = context,
                        runtime = runtime,
                        url = url,
                        isIncognito = isIncognito,
                        isDesktopMode = isDesktopMode,
                        inMemoryState = inMemoryState,
                        createSession = createSession
                    )
                    onComplete(recovered.first, recovered.second)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Recovery timeout for $tabId")
                SessionRecoveryDiagnostics.logRecoveryFailed(tabId, fallback = "timeout")
                onComplete(false, "timeout")
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed for $tabId", e)
                SessionRecoveryDiagnostics.logRecoveryFailed(tabId, fallback = "error: ${e.message}")
                onComplete(false, "error")
            } finally {
                info.isRecovering.set(false)
            }
        }
    }

    fun cancelRecovery(tabId: String) {
        val info = tabRecoveryInfo[tabId] ?: return
        info.pendingRecoveryJob?.cancel()
        info.isRecovering.set(false)
    }

    fun shutdown() {
        tabRecoveryInfo.values.forEach {
            it.pendingRecoveryJob?.cancel()
            it.isRecovering.set(false)
        }
        coordinatorScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private recovery implementation
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun performRecovery(
        tabId: String,
        context: Context,
        runtime: GeckoRuntime,
        url: String,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        inMemoryState: GeckoSession.SessionState?,
        createSession: (GeckoSession) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.Main) {
        val info = tabRecoveryInfo[tabId]!!

        // 1. Try in-memory SessionState (same process, session was hard-suspended)
        if (inMemoryState != null) {
            SessionRecoveryDiagnostics.logRecoveryStep(tabId, "in_memory_state")
            val newGen = nextGenerationId()
            val newSession = createReplacementSession(isIncognito, isDesktopMode, context)
            createSession(newSession)
            info.generationId = newGen
            info.geckoState = GeckoState.OPENING
            newSession.open(runtime)
            newSession.restoreState(inMemoryState)
            info.geckoState = GeckoState.READY
            SessionRecoveryDiagnostics.logRecoverySuccess(tabId, "in_memory_state")
            return@withContext Pair(true, "in_memory_state")
        }

        // 2. Try durable SessionState (survived process death)
        val durableState = withContext(Dispatchers.IO) {
            persistence.readDurableState(tabId)
        }
        if (durableState != null) {
            SessionRecoveryDiagnostics.logRecoveryStep(tabId, "durable_state")
            val newGen = nextGenerationId()
            val newSession = createReplacementSession(isIncognito, isDesktopMode, context)
            createSession(newSession)
            info.generationId = newGen
            info.geckoState = GeckoState.OPENING
            newSession.open(runtime)
            newSession.restoreState(durableState)
            info.geckoState = GeckoState.READY
            SessionRecoveryDiagnostics.logRecoverySuccess(tabId, "durable_state")
            return@withContext Pair(true, "durable_state")
        }

        // 3. Fallback to URL reload
        if (url != "about:blank" && url.isNotEmpty()) {
            SessionRecoveryDiagnostics.logRecoveryStep(tabId, "url_fallback")
            val newGen = nextGenerationId()
            val newSession = createReplacementSession(isIncognito, isDesktopMode, context)
            createSession(newSession)
            info.generationId = newGen
            info.geckoState = GeckoState.OPENING
            newSession.open(runtime)
            newSession.loadUri(url)
            info.geckoState = GeckoState.READY
            SessionRecoveryDiagnostics.logRecoverySuccess(tabId, "url_fallback")
            return@withContext Pair(true, "url_fallback")
        }

        // 4. Nothing to recover
        SessionRecoveryDiagnostics.logRecoveryFailed(tabId, fallback = "no_state")
        return@withContext Pair(false, "no_state")
    }

    private fun createReplacementSession(
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        context: Context
    ): GeckoSession {
        // JavaScript permission check would need the URL; for now allow default.
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognito)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .allowJavascript(true)
            .build()
        return GeckoSession(settings)
    }
}
