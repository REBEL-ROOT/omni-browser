package com.rebelroot.omni.sync.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteTabsBridgeTest {

    private lateinit var tabBridge: RemoteTabsBridge

    @Before
    fun setUp() {
        tabBridge = RemoteTabsBridge()
    }

    @Test
    fun `updateDirectRemoteTabs stores tabs grouped by device`() {
        tabBridge.updateDirectRemoteTabs(
            deviceId = "device_macbook_pro",
            deviceName = "Desktop Firefox",
            tabs = listOf(
                TabInfo(title = "GitHub - REBEL-ROOT", url = "https://github.com/REBEL-ROOT"),
                TabInfo(title = "Android Developers", url = "https://developer.android.com")
            )
        )

        val devices = tabBridge.getAllRemoteDeviceTabs()
        assertEquals(1, devices.size)
        assertEquals("Desktop Firefox", devices[0].deviceName)
        assertEquals(2, devices[0].tabs.size)
        assertEquals("https://github.com/REBEL-ROOT", devices[0].tabs[0].url)
        assertEquals(devices, tabBridge.remoteTabsFlow.value)
    }

    @Test
    fun `clearRemoteTabs empties the flow`() {
        tabBridge.updateRemoteDeviceTabs(
            RemoteDeviceTabs(deviceId = "d1", deviceName = "Desktop", tabs = listOf(TabInfo("t", "https://t.com")))
        )
        assertTrue(tabBridge.remoteTabsFlow.value.isNotEmpty())

        tabBridge.clearRemoteTabs()
        assertTrue(tabBridge.remoteTabsFlow.value.isEmpty())
        assertTrue(tabBridge.getAllRemoteDeviceTabs().isEmpty())
    }
}