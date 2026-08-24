package com.rebelroot.omni.sync.history

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SyncedHistoryVisit(
    val visitId: String,
    val url: String,
    val title: String,
    val visitTime: Long,
    val visitCount: Int = 1,
    val deviceId: String
)

class HistorySyncAdapter(
    private val localDeviceId: String,
    var isHistorySyncEnabled: Boolean = false
) {
    companion object {
        val RETENTION_DAYS_MS = TimeUnit.DAYS.toMillis(90)
        private val TRACKING_PARAMS = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "msclkid", "mc_eid", "_hsenc", "_hsmi"
        )

        fun sanitizeUrl(rawUrl: String): String {
            return try {
                val uri = URI(rawUrl)
                val query = uri.query ?: return rawUrl
                val cleanParams = query.split("&")
                    .filterNot { param ->
                        val key = param.substringBefore("=").lowercase()
                        TRACKING_PARAMS.contains(key)
                    }
                val newQuery = if (cleanParams.isEmpty()) null else cleanParams.joinToString("&")
                URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
            } catch (_: Exception) {
                rawUrl
            }
        }
    }

    private val localVisits = ConcurrentHashMap<String, SyncedHistoryVisit>()

    fun recordVisit(url: String, title: String, isIncognito: Boolean, visitTime: Long = System.currentTimeMillis()): SyncedHistoryVisit? {
        if (!isHistorySyncEnabled || isIncognito) return null
        val cleanUrl = sanitizeUrl(url)
        if (cleanUrl.isBlank() || cleanUrl.startsWith("about:") || cleanUrl.startsWith("chrome://")) return null

        val visitId = "hist_${cleanUrl.hashCode()}_${visitTime}"
        val visit = SyncedHistoryVisit(
            visitId = visitId,
            url = cleanUrl,
            title = title.ifBlank { cleanUrl },
            visitTime = visitTime,
            visitCount = 1,
            deviceId = localDeviceId
        )
        localVisits[visitId] = visit
        return visit
    }

    fun pruneExpiredVisits(currentTime: Long = System.currentTimeMillis()): Int {
        val cutoff = currentTime - RETENTION_DAYS_MS
        var pruned = 0
        localVisits.entries.removeIf { (_, visit) ->
            if (visit.visitTime < cutoff) {
                pruned++
                true
            } else {
                false
            }
        }
        return pruned
    }

    fun clearAllHistory() {
        localVisits.clear()
    }

    fun visitCount(): Int = localVisits.size
}
