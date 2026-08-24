package com.rebelroot.omni.sync.mozilla

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MozillaHistoryBridgeTest {

    private lateinit var historyBridge: MozillaHistoryBridge

    @Before
    fun setUp() {
        historyBridge = MozillaHistoryBridge()
    }

    @Test
    fun testDeduplicateHistoryWithinTimeWindow() {
        val now = 1724500000000L
        val entries = listOf(
            MozHistoryEntry(guid = "1", url = "https://example.com", title = "Example", visitTimestamp = now),
            MozHistoryEntry(guid = "2", url = "https://example.com", title = "Example", visitTimestamp = now + 10_000L), // 10s apart -> duplicate
            MozHistoryEntry(guid = "3", url = "https://example.com", title = "Example", visitTimestamp = now + 120_000L), // 2 min apart -> distinct
            MozHistoryEntry(guid = "4", url = "https://other.com", title = "Other", visitTimestamp = now)
        )

        val deduplicated = historyBridge.deduplicateHistory(entries)
        assertEquals(3, deduplicated.size)
    }

    @Test
    fun testExportAndParseBsoRecords() {
        val now = 1724500000000L
        val entries = listOf(
            MozHistoryEntry(guid = "hist_1", url = "https://example.com", title = "Example", visitTimestamp = now)
        )

        val bsoList = historyBridge.exportToBsoRecords(entries)
        assertEquals(1, bsoList.size)

        val parsed = historyBridge.parseBsoRecords(bsoList)
        assertEquals(1, parsed.size)
        assertEquals("https://example.com", parsed[0].url)
        assertEquals("Example", parsed[0].title)
    }
}
