/*
 * Omni Browser - Durable session state serialization format.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.session

import org.json.JSONObject

/**
 * Versioned on-disk representation of a single tab's recoverable state.
 *
 * This class is intentionally NOT tied to GeckoView internals — it stores
 * the raw [GeckoSession.SessionState] bytes alongside browser-level metadata
 * so that recovery can proceed even if Gecko changes its serialization format.
 */
data class OmniSessionState(
    /** Schema version for forward/backward compatibility. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Tab identifier (matches [TabState.id]). */
    val tabId: String,
    /** Serialized [GeckoSession.SessionState] bytes (opaque to Omni). */
    val sessionStateBytes: ByteArray,
    /** Browser-level metadata that does NOT depend on Gecko internals. */
    val metadata: TabMetadata,
    /** Unix timestamp (ms) when this state was written. */
    val timestamp: Long = System.currentTimeMillis()
) {
    data class TabMetadata(
        val title: String,
        val url: String,
        val isIncognito: Boolean,
        val lastActiveTime: Long,
        val canGoBack: Boolean,
        val canGoForward: Boolean
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromJson(json: JSONObject): OmniSessionState {
            val schema = json.optInt("schemaVersion", 1)
            val tabId = json.getString("tabId")
            val b64 = json.getString("sessionStateBytes")
            val sessionStateBytes = java.util.Base64.getDecoder().decode(b64)
            val metaObj = json.getJSONObject("metadata")
            val metadata = TabMetadata(
                title = metaObj.getString("title"),
                url = metaObj.getString("url"),
                isIncognito = metaObj.optBoolean("isIncognito", false),
                lastActiveTime = metaObj.optLong("lastActiveTime", 0L),
                canGoBack = metaObj.optBoolean("canGoBack", false),
                canGoForward = metaObj.optBoolean("canGoForward", false)
            )
            return OmniSessionState(
                schemaVersion = schema,
                tabId = tabId,
                sessionStateBytes = sessionStateBytes,
                metadata = metadata,
                timestamp = json.optLong("timestamp", 0L)
            )
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("tabId", tabId)
            put("sessionStateBytes", java.util.Base64.getEncoder().withoutPadding().encodeToString(sessionStateBytes))
            put("metadata", JSONObject().apply {
                put("title", metadata.title)
                put("url", metadata.url)
                put("isIncognito", metadata.isIncognito)
                put("lastActiveTime", metadata.lastActiveTime)
                put("canGoBack", metadata.canGoBack)
                put("canGoForward", metadata.canGoForward)
            })
            put("timestamp", timestamp)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OmniSessionState) return false
        return schemaVersion == other.schemaVersion &&
                tabId == other.tabId &&
                sessionStateBytes.contentEquals(other.sessionStateBytes) &&
                metadata == other.metadata &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + tabId.hashCode()
        result = 31 * result + sessionStateBytes.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
