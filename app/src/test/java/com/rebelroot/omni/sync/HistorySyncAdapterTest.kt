package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.history.HistorySyncAdapter
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HistorySyncAdapterTest {

    @Test
    fun historySync_strictlyOptIn_andExcludesIncognito() {
        val adapter = HistorySyncAdapter("dev_android_01", isHistorySyncEnabled = false)

        // When disabled, no visit is recorded
        val v1 = adapter.recordVisit("https://example.com", "Example", isIncognito = false)
        assertNull("Disabled history sync must return null", v1)

        // Enable sync
        adapter.isHistorySyncEnabled = true

        // Incognito visit must still be rejected
        val v2 = adapter.recordVisit("https://secret.com", "Secret", isIncognito = true)
        assertNull("Incognito visit must return null", v2)

        // Standard non-incognito visit recorded
        val v3 = adapter.recordVisit("https://example.com", "Example", isIncognito = false)
        assertNotNull(v3)
        assertEquals("https://example.com", v3?.url)
    }

    @Test
    fun sanitizeUrl_stripsTrackingParameters() {
        val dirtyUrl = "https://shop.com/product?id=42&utm_source=twitter&utm_medium=cpc&fbclid=IwAR123"
        val cleanUrl = HistorySyncAdapter.sanitizeUrl(dirtyUrl)
        assertEquals("https://shop.com/product?id=42", cleanUrl)
    }

    @Test
    fun pruneExpiredVisits_enforces90DayRetention() {
        val adapter = HistorySyncAdapter("dev_android_01", isHistorySyncEnabled = true)
        val now = System.currentTimeMillis()
        val expiredTime = now - TimeUnit.DAYS.toMillis(95) // 95 days old

        adapter.recordVisit("https://old.com", "Old", isIncognito = false, visitTime = expiredTime)
        adapter.recordVisit("https://recent.com", "Recent", isIncognito = false, visitTime = now)

        assertEquals(2, adapter.visitCount())

        val pruned = adapter.pruneExpiredVisits(now)
        assertEquals(1, pruned)
        assertEquals(1, adapter.visitCount())
    }
}
