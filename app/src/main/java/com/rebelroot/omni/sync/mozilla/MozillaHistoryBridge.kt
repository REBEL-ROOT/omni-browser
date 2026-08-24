package com.rebelroot.omni.sync.mozilla

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MozHistoryEntry(
    val guid: String,
    val url: String,
    val title: String,
    val visitTimestamp: Long = System.currentTimeMillis(),
    val visitType: Int = 1
)

class MozillaHistoryBridge {

    /**
     * Converts local history entries to BSO records for Mozilla Sync.
     */
    fun exportToBsoRecords(entries: List<MozHistoryEntry>): List<BsoRecord> {
        val cleanEntries = entries.map { entry ->
            entry.copy(url = stripTrackingParameters(entry.url))
        }
        val deduplicated = deduplicateHistory(cleanEntries)

        return deduplicated.map { entry ->
            val payload = JSONObject().apply {
                put("id", entry.guid)
                put("histUri", entry.url)
                put("title", entry.title)
                val visits = JSONArray().apply {
                    put(JSONObject().apply {
                        put("date", entry.visitTimestamp * 1000L) // Microseconds in Mozilla Sync
                        put("type", entry.visitType)
                    })
                }
                put("visits", visits)
            }
            BsoRecord(
                id = entry.guid,
                modified = entry.visitTimestamp / 1000.0,
                payload = payload.toString()
            )
        }
    }

    /**
     * Parses remote history BSOs received from Mozilla Sync.
     */
    fun parseBsoRecords(bsoList: List<BsoRecord>): List<MozHistoryEntry> {
        val list = mutableListOf<MozHistoryEntry>()
        for (bso in bsoList) {
            try {
                val json = JSONObject(bso.payload)
                if (json.optBoolean("deleted", false)) continue

                val url = json.optString("histUri", json.optString("url", ""))
                if (url.isBlank() || url == "about:blank") continue

                val title = json.optString("title", url)
                var visitTimestamp = (bso.modified * 1000.0).toLong()

                val visitsArr = json.optJSONArray("visits")
                if (visitsArr != null && visitsArr.length() > 0) {
                    val firstVisit = visitsArr.getJSONObject(0)
                    val dateMicros = firstVisit.optLong("date", 0L)
                    if (dateMicros > 0L) {
                        visitTimestamp = dateMicros / 1000L
                    }
                }

                list.add(
                    MozHistoryEntry(
                        guid = bso.id,
                        url = stripTrackingParameters(url),
                        title = title,
                        visitTimestamp = visitTimestamp
                    )
                )
            } catch (e: Exception) {
                // Ignore malformed record
            }
        }
        return deduplicateHistory(list)
    }

    /**
     * Deduplicates history entries by URL and timestamp within a 60-second window.
     */
    fun deduplicateHistory(entries: List<MozHistoryEntry>): List<MozHistoryEntry> {
        val sorted = entries.sortedByDescending { it.visitTimestamp }
        val result = mutableListOf<MozHistoryEntry>()

        for (entry in sorted) {
            val isDuplicate = result.any { existing ->
                existing.url == entry.url && Math.abs(existing.visitTimestamp - entry.visitTimestamp) < 60_000L
            }
            if (!isDuplicate) {
                result.add(entry)
            }
        }
        return result
    }

    /**
     * Strips common query tracking parameters for privacy (pure Kotlin, JVM compatible).
     */
    fun stripTrackingParameters(url: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx == -1) return url

        val base = url.substring(0, qIdx)
        val query = url.substring(qIdx + 1)
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "msclkid", "mc_eid", "dclid", "_ga", "_gl"
        )

        val pairs = query.split('&')
        val filtered = pairs.filter { pair ->
            val key = pair.substringBefore('=').lowercase()
            key !in trackingParams && key.isNotBlank()
        }

        return if (filtered.isEmpty()) base else "$base?${filtered.joinToString("&")}"
    }
}
