package com.rebelroot.omni.sync.mozilla

import com.rebelroot.omni.browser.TabState
import org.json.JSONArray
import org.json.JSONObject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TabInfo(
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val lastAccessed: Long = System.currentTimeMillis()
)

data class RemoteDeviceTabs(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "desktop",
    val lastModified: Long = System.currentTimeMillis(),
    val tabs: List<TabInfo>
)

class MozillaTabBridge {

    private val _remoteTabsFlow = MutableStateFlow<List<RemoteDeviceTabs>>(emptyList())
    val remoteTabsFlow: StateFlow<List<RemoteDeviceTabs>> = _remoteTabsFlow.asStateFlow()

    private val remoteTabsByDevice = mutableMapOf<String, RemoteDeviceTabs>()

    /**
     * Converts Omni [TabState] instances to [TabInfo] records for uploading to Firefox Sync.
     * Strictly excludes incognito tabs and internal blank pages.
     */
    fun exportTabs(tabs: List<TabState>): List<TabInfo> {
        return tabs
            .filter { !it.isIncognito && it.url.isNotBlank() && it.url != "about:blank" && !it.url.startsWith("omni://") }
            .map {
                TabInfo(
                    title = it.title.takeIf { t -> t.isNotBlank() } ?: it.url,
                    url = it.url,
                    iconUrl = null,
                    lastAccessed = System.currentTimeMillis()
                )
            }
    }

    /**
     * Converts local open tabs into a BSO record for the local device.
     */
    fun exportToBsoRecord(deviceId: String, deviceName: String, tabs: List<TabState>): BsoRecord {
        val tabInfos = exportTabs(tabs)
        val payload = JSONObject().apply {
            put("id", deviceId)
            put("clientName", deviceName)
            val tabsArray = JSONArray()
            tabInfos.forEach { t ->
                tabsArray.put(JSONObject().apply {
                    put("title", t.title)
                    put("urlHistory", JSONArray().put(t.url))
                    if (t.iconUrl != null) put("icon", t.iconUrl)
                    put("lastUsed", t.lastAccessed / 1000)
                })
            }
            put("tabs", tabsArray)
        }

        return BsoRecord(
            id = deviceId,
            modified = System.currentTimeMillis() / 1000.0,
            payload = payload.toString()
        )
    }

    /**
     * Parses remote tab BSO records received from Mozilla Sync.
     */
    fun parseBsoRecords(bsoList: List<BsoRecord>, localDeviceId: String): List<RemoteDeviceTabs> {
        val list = mutableListOf<RemoteDeviceTabs>()
        for (bso in bsoList) {
            // Ignore own device's tabs record
            if (bso.id == localDeviceId) continue

            try {
                val json = JSONObject(bso.payload)
                if (json.optBoolean("deleted", false)) continue

                val deviceName = json.optString("clientName", "Remote Firefox Device")
                val tabsArray = json.optJSONArray("tabs") ?: JSONArray()
                val parsedTabs = mutableListOf<TabInfo>()

                for (i in 0 until tabsArray.length()) {
                    val tabObj = tabsArray.getJSONObject(i)
                    val urlHist = tabObj.optJSONArray("urlHistory")
                    val url = if (urlHist != null && urlHist.length() > 0) {
                        urlHist.getString(0)
                    } else {
                        tabObj.optString("url", "")
                    }
                    if (url.isBlank() || url == "about:blank") continue

                    val title = tabObj.optString("title", url)
                    val icon = tabObj.optString("icon", "").takeIf { it.isNotBlank() }
                    val lastUsedSec = tabObj.optLong("lastUsed", (bso.modified).toLong())
                    parsedTabs.add(
                        TabInfo(
                            title = title,
                            url = url,
                            iconUrl = icon,
                            lastAccessed = lastUsedSec * 1000L
                        )
                    )
                }

                if (parsedTabs.isNotEmpty()) {
                    val dev = RemoteDeviceTabs(
                        deviceId = bso.id,
                        deviceName = deviceName,
                        lastModified = (bso.modified * 1000.0).toLong(),
                        tabs = parsedTabs
                    )
                    list.add(dev)
                    updateRemoteDeviceTabs(dev)
                }
            } catch (e: Exception) {
                // Ignore malformed device record
            }
        }
        return list
    }

    /**
     * Stores synced tabs received from a remote Firefox/Omni device.
     */
    fun updateRemoteDeviceTabs(remoteDevice: RemoteDeviceTabs) {
        remoteTabsByDevice[remoteDevice.deviceId] = remoteDevice
        _remoteTabsFlow.value = remoteTabsByDevice.values.toList()
    }

    /**
     * Returns all synced remote tabs grouped by device.
     */
    fun getAllRemoteDeviceTabs(): List<RemoteDeviceTabs> {
        return remoteTabsByDevice.values.toList()
    }

    fun clearRemoteTabs() {
        remoteTabsByDevice.clear()
        _remoteTabsFlow.value = emptyList()
    }
}
