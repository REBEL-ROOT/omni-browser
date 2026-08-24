package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.tab.DeviceTabSnapshot
import com.rebelroot.omni.sync.tab.SyncedTab
import com.rebelroot.omni.sync.tab.TabSyncAdapter
import org.junit.Assert.*
import org.junit.Test

class TabSyncAdapterTest {

    @Test
    fun filterPortableTabs_strictlyExcludesIncognitoTabs() {
        val adapter = TabSyncAdapter("dev_android_01", "Pixel 8 Pro")

        val rawTabs = listOf(
            "tab_1" to mapOf("url" to "https://github.com", "title" to "GitHub", "isIncognito" to false),
            "tab_2" to mapOf("url" to "https://private-bank.com", "title" to "Secret Bank", "isIncognito" to true),
            "tab_3" to mapOf("url" to "about:blank", "title" to "New Tab", "isIncognito" to false),
            "tab_4" to mapOf("url" to "https://kotlinlang.org", "title" to "Kotlin", "isIncognito" to false)
        )

        val portable = adapter.filterPortableTabs(rawTabs)
        assertEquals("Only non-incognito non-blank tabs should be synced", 2, portable.size)
        assertEquals("https://github.com", portable[0].url)
        assertEquals("https://kotlinlang.org", portable[1].url)
    }

    @Test
    fun remoteTabsPartitioning_preservesPerDeviceTabLists() {
        val adapter = TabSyncAdapter("dev_android_01", "Pixel 8 Pro")

        val desktopSnapshot = DeviceTabSnapshot(
            deviceId = "dev_chrome_desktop",
            deviceName = "MacBook Pro",
            tabs = listOf(
                SyncedTab("tab_c1", "dev_chrome_desktop", "https://news.ycombinator.com", "Hacker News"),
                SyncedTab("tab_c2", "dev_chrome_desktop", "https://reddit.com", "Reddit")
            )
        )

        adapter.updateRemoteDeviceTabs(desktopSnapshot)

        val remoteTabs = adapter.getRemoteTabsForDevice("dev_chrome_desktop")
        assertEquals(2, remoteTabs.size)
        assertEquals("Hacker News", remoteTabs[0].title)

        adapter.removeDeviceTabs("dev_chrome_desktop")
        assertTrue(adapter.getRemoteTabsForDevice("dev_chrome_desktop").isEmpty())
    }
}
