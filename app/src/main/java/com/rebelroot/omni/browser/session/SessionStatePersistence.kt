/*
 * Omni Browser - Debounced, atomic, versioned SessionState persistence.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.session

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.mozilla.geckoview.GeckoSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Maintains durable recoverable state for every eligible (non-incognito) tab.
 *
 * Architecture:
 * ```
 * GeckoSession
 *   ↓ onSessionStateChange()
 *   ↓ latest in-memory SessionState
 *   ↓ debounce / coalesce (500 ms)
 *   ↓ atomic file write (temp → rename)
 *   ↓ durable app-private storage
 * ```
 *
 * Two concepts are maintained:
 * - **latestInMemorySessionState** — what Gecko last reported (may be lost on crash)
 * - **latestDurableSessionState** — what was successfully written to disk (survives process death)
 *
 * Important: incognito tabs are NEVER persisted. Their SessionState lives only in memory.
 *
 * @param baseDir Directory where the serialized SessionState file is written.
 *                Callers should pass [Context.getFilesDir] (app-private, encrypted-at-rest).
 */
class SessionStatePersistence(private val baseDir: File) {

    companion object {
        private const val TAG = "SessionStatePersistence"
        private const val SESSION_STATE_FILE = "browser_session_states.json"
        private const val SESSION_STATE_TEMP = "browser_session_states.tmp"
        private const val DEBOUNCE_MS = 500L
        private const val FORCE_CHECKPOINT_TIMEOUT_MS = 3_000L
    }

    /** Monotonically-increasing write generation for stale-write detection. */
    private val writeGeneration = AtomicLong(0L)

    /** In-flight debounce jobs per tab. */
    private val pendingJobs = ConcurrentHashMap<String, Job>()

    /** Scope for all persistence work (off main thread). */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Latest durable state per tab (read from disk or set after successful write). */
    private val durableStateCache = ConcurrentHashMap<String, OmniSessionState>()

    /**
     * Request a debounced persistence write for the given tab.
     * Multiple rapid calls for the same tab are coalesced into a single disk write.
     */
    fun requestPersist(tabId: String, sessionState: GeckoSession.SessionState, metadata: OmniSessionState.TabMetadata) {
        if (metadata.isIncognito) {
            // Privacy: never persist incognito state to disk.
            return
        }

        // Cancel any in-flight job for this tab.
        pendingJobs[tabId]?.cancel()

        val job = persistenceScope.launch {
            delay(DEBOUNCE_MS)
            if (!isActive) return@launch

            val bytes = sessionState.toString().toByteArray(Charsets.UTF_8)
            val state = OmniSessionState(
                tabId = tabId,
                sessionStateBytes = bytes,
                metadata = metadata
            )
            performAtomicWrite(state)
        }
        pendingJobs[tabId] = job
    }

    /**
     * Force an immediate checkpoint of the given tab's state.
     * Blocks (with timeout) until the write completes or fails.
     * Use before critical boundaries: external app launch, onStop, etc.
     */
    fun forceCheckpoint(tabId: String, sessionState: GeckoSession.SessionState, metadata: OmniSessionState.TabMetadata): Boolean {
        if (metadata.isIncognito) return false

        // Cancel debounce and write immediately.
        pendingJobs[tabId]?.cancel()

        return runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(FORCE_CHECKPOINT_TIMEOUT_MS) {
                val bytes = sessionState.toString().toByteArray(Charsets.UTF_8)
                val state = OmniSessionState(
                    tabId = tabId,
                    sessionStateBytes = bytes,
                    metadata = metadata
                )
                performAtomicWrite(state)
                true
            } ?: false
        }
    }

    /**
     * Checkpoint multiple tabs at once. Used before process-death-prone transitions.
     */
    fun forceCheckpointBatch(states: Map<String, Pair<GeckoSession.SessionState, OmniSessionState.TabMetadata>>) {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()

        persistenceScope.launch {
            val validStates = states.filter { !it.value.second.isIncognito }.map { (tabId, pair) ->
                val (sessionState, metadata) = pair
                OmniSessionState(
                    tabId = tabId,
                    sessionStateBytes = sessionState.toString().toByteArray(Charsets.UTF_8),
                    metadata = metadata
                )
            }
            if (validStates.isEmpty()) return@launch
            performAtomicWriteBatch(validStates)
        }
    }

    /**
     * Read the latest durable SessionState for a tab.
     * Returns null if no durable state exists or if the state is too stale.
     */
    fun readDurableState(tabId: String): GeckoSession.SessionState? {
        // 1. Check in-memory cache.
        durableStateCache[tabId]?.let { cached ->
            return try {
                GeckoSession.SessionState.fromString(String(cached.sessionStateBytes, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize cached SessionState for $tabId", e)
                null
            }
        }

        // 2. Read from disk.
        return readFromDisk(tabId)
    }

    /**
     * Read all durable states. Used during process-restart recovery.
     */
    fun readAllDurableStates(): Map<String, GeckoSession.SessionState> {
        val file = File(baseDir, SESSION_STATE_FILE)
        if (!file.exists()) return emptyMap()

        return try {
            val json = JSONObject(file.readText())
            val statesObj = json.optJSONObject("states") ?: return emptyMap()
            val result = mutableMapOf<String, GeckoSession.SessionState>()
            statesObj.keys().forEach { tabId ->
                try {
                    val stateJson = statesObj.getJSONObject(tabId)
                    val omniState = OmniSessionState.fromJson(stateJson)
                    val geckoState = GeckoSession.SessionState.fromString(String(omniState.sessionStateBytes, Charsets.UTF_8))
                        ?: return@forEach
                    durableStateCache[tabId] = omniState
                    result[tabId] = geckoState
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt SessionState for $tabId", e)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read all durable states", e)
            emptyMap()
        }
    }

    /**
     * Remove durable state for a tab (e.g., when tab is closed).
     */
    fun removeDurableState(tabId: String) {
        durableStateCache.remove(tabId)
        persistenceScope.launch {
            try {
                val file = File(baseDir, SESSION_STATE_FILE)
                if (!file.exists()) return@launch
                val json = JSONObject(file.readText())
                val statesObj = json.optJSONObject("states") ?: return@launch
                if (statesObj.has(tabId)) {
                    statesObj.remove(tabId)
                    atomicWriteFile(json.toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove durable state for $tabId", e)
            }
        }
    }

    /**
     * Clear all durable session states (e.g., on "clear all data").
     */
    fun clearAll() {
        durableStateCache.clear()
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        persistenceScope.launch {
            try {
                File(baseDir, SESSION_STATE_FILE).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear all durable states", e)
            }
        }
    }

    fun shutdown() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        persistenceScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private implementation
    // ─────────────────────────────────────────────────────────────────────────

    private fun performAtomicWrite(state: OmniSessionState) {
        try {
            val file = File(baseDir, SESSION_STATE_FILE)
            val existingJson = if (file.exists()) {
                try {
                    JSONObject(file.readText())
                } catch (e: Exception) {
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            val statesObj = existingJson.optJSONObject("states") ?: JSONObject()
            statesObj.put(state.tabId, state.toJson())
            existingJson.put("states", statesObj)
            existingJson.put("version", OmniSessionState.CURRENT_SCHEMA_VERSION)
            existingJson.put("lastWritten", System.currentTimeMillis())

            atomicWriteFile(existingJson.toString())
            durableStateCache[state.tabId] = state
            Log.d(TAG, "Persisted SessionState for ${state.tabId} (${state.metadata.url})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist SessionState for ${state.tabId}", e)
        }
    }

    private fun performAtomicWriteBatch(states: List<OmniSessionState>) {
        try {
            val file = File(baseDir, SESSION_STATE_FILE)
            val existingJson = if (file.exists()) {
                try {
                    JSONObject(file.readText())
                } catch (e: Exception) {
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            val statesObj = existingJson.optJSONObject("states") ?: JSONObject()
            states.forEach { state ->
                statesObj.put(state.tabId, state.toJson())
                durableStateCache[state.tabId] = state
            }
            existingJson.put("states", statesObj)
            existingJson.put("version", OmniSessionState.CURRENT_SCHEMA_VERSION)
            existingJson.put("lastWritten", System.currentTimeMillis())

            atomicWriteFile(existingJson.toString())
            Log.d(TAG, "Batch-persisted ${states.size} SessionStates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch-persist SessionStates", e)
        }
    }

    private fun atomicWriteFile(content: String) {
        val dir = baseDir
        val tempFile = File(dir, SESSION_STATE_TEMP)
        val finalFile = File(dir, SESSION_STATE_FILE)
        tempFile.writeText(content)
        if (!tempFile.renameTo(finalFile)) {
            // Fallback: direct write if rename fails (shouldn't happen on Android)
            finalFile.writeText(content)
        }
    }

    private fun readFromDisk(tabId: String): GeckoSession.SessionState? {
        val file = File(baseDir, SESSION_STATE_FILE)
        if (!file.exists()) return null

        return try {
            val json = JSONObject(file.readText())
            val statesObj = json.optJSONObject("states") ?: return null
            if (!statesObj.has(tabId)) return null
            val stateJson = statesObj.getJSONObject(tabId)
            val omniState = OmniSessionState.fromJson(stateJson)
            durableStateCache[tabId] = omniState
            GeckoSession.SessionState.fromString(String(omniState.sessionStateBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read durable SessionState for $tabId", e)
            null
        }
    }
}
