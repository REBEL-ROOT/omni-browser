package com.rebelroot.omni.sync.tab

import com.rebelroot.omni.browser.TabState

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

/**
 * In-memory store of open tabs reported by paired desktop devices over Omni
 * Sync (LAN / desktop extension). Feeds the "Remote Tabs" viewer.
 */
class RemoteTabsBridge {

    private val _remoteTabsFlow = MutableStateFlow<List<RemoteDeviceTabs>>(emptyList())
    val remoteTabsFlow: StateFlow<List<RemoteDeviceTabs>> = _remoteTabsFlow.asStateFlow()

    private val remoteTabsByDevice = mutableMapOf<String, RemoteDeviceTabs>()

    /**
     * Converts Omni [TabState] instances to shareable [TabInfo] records.
     * Strictly excludes incognito tabs and internal pages.
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

    fun updateDirectRemoteTabs(deviceId: String, deviceName: String, tabs: List<TabInfo>) {
        synchronized(remoteTabsByDevice) {
            remoteTabsByDevice[deviceId] = RemoteDeviceTabs(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceType = "desktop",
                lastModified = System.currentTimeMillis(),
                tabs = tabs
            )
            _remoteTabsFlow.value = remoteTabsByDevice.values.toList()
        }
    }

    /** Stores tabs received from a remote paired device. */
    fun updateRemoteDeviceTabs(remoteDevice: RemoteDeviceTabs) {
        remoteTabsByDevice[remoteDevice.deviceId] = remoteDevice
        _remoteTabsFlow.value = remoteTabsByDevice.values.toList()
    }

    /** Returns all synced remote tabs grouped by device. */
    fun getAllRemoteDeviceTabs(): List<RemoteDeviceTabs> {
        return remoteTabsByDevice.values.toList()
    }

    fun clearRemoteTabs() {
        remoteTabsByDevice.clear()
        _remoteTabsFlow.value = emptyList()
    }
}