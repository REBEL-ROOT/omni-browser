package com.rebelroot.omni.sync.mozilla

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MozillaTabBridgeTest {

    private lateinit var tabBridge: MozillaTabBridge

    @Before
    fun setUp() {
        tabBridge = MozillaTabBridge()
    }

    @Test
    fun testParseRemoteDeviceTabsBso() {
        val payload = JSONObject().apply {
            put("id", "device_macbook_pro")
            put("clientName", "Firefox on MacBook Pro")
            val tabsArr = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("title", "GitHub - REBEL-ROOT")
                    put("urlHistory", org.json.JSONArray().put("https://github.com/REBEL-ROOT"))
                    put("lastUsed", 1724500000)
                })
                put(JSONObject().apply {
                    put("title", "Android Developers")
                    put("urlHistory", org.json.JSONArray().put("https://developer.android.com"))
                    put("lastUsed", 1724500100)
                })
            }
            put("tabs", tabsArr)
        }

        val bso = BsoRecord(
            id = "device_macbook_pro",
            modified = 1724500100.0,
            payload = payload.toString()
        )

        val parsedDevices = tabBridge.parseBsoRecords(listOf(bso), localDeviceId = "local_phone_id")
        assertEquals(1, parsedDevices.size)
        assertEquals("Firefox on MacBook Pro", parsedDevices[0].deviceName)
        assertEquals(2, parsedDevices[0].tabs.size)
        assertEquals("GitHub - REBEL-ROOT", parsedDevices[0].tabs[0].title)
        assertEquals("https://github.com/REBEL-ROOT", parsedDevices[0].tabs[0].url)

        val allRemote = tabBridge.getAllRemoteDeviceTabs()
        assertEquals(1, allRemote.size)
    }

    @Test
    fun testIgnoreOwnDeviceTabs() {
        val payload = JSONObject().apply {
            put("id", "my_own_device_id")
            put("clientName", "Omni Browser")
            put("tabs", org.json.JSONArray())
        }

        val bso = BsoRecord(
            id = "my_own_device_id",
            modified = 1724500000.0,
            payload = payload.toString()
        )

        val parsed = tabBridge.parseBsoRecords(listOf(bso), localDeviceId = "my_own_device_id")
        assertTrue(parsed.isEmpty())
    }
}
