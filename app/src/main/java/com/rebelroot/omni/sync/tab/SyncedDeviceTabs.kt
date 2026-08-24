package com.rebelroot.omni.sync.tab

import com.rebelroot.omni.sync.model.Hlc
import com.rebelroot.omni.sync.model.SyncOperation
import com.rebelroot.omni.sync.model.SyncOpType
import com.rebelroot.omni.sync.model.SyncEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SyncedTab(
    val tabId: String,
    val deviceId: String,
    val url: String,
    val title: String,
    val faviconUrl: String = "",
    val lastActiveTime: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

data class DeviceTabSnapshot(
    val deviceId: String,
    val deviceName: String,
    val tabs: List<SyncedTab>,
    val updatedAt: Long = System.currentTimeMillis()
)

class TabSyncAdapter(
    private val localDeviceId: String,
    private val localDeviceName: String
) {
    private val remoteDeviceTabs = ConcurrentHashMap<String, DeviceTabSnapshot>()

    fun filterPortableTabs(
        allTabs: List<Pair<String, Map<String, Any>>> // tabId -> metadata (url, title, isIncognito, lastActive)
    ): List<SyncedTab> {
        return allTabs.filter { (_, meta) ->
            val isIncognito = meta["isIncognito"] as? Boolean ?: false
            val url = meta["url"] as? String ?: ""
            !isIncognito && url.isNotBlank() && !url.startsWith("about:")
        }.map { (tabId, meta) ->
            SyncedTab(
                tabId = tabId,
                deviceId = localDeviceId,
                url = meta["url"] as? String ?: "",
                title = meta["title"] as? String ?: "Untitled",
                faviconUrl = meta["faviconUrl"] as? String ?: "",
                lastActiveTime = meta["lastActiveTime"] as? Long ?: System.currentTimeMillis(),
                isPinned = meta["isPinned"] as? Boolean ?: false
            )
        }
    }

    fun updateRemoteDeviceTabs(snapshot: DeviceTabSnapshot) {
        remoteDeviceTabs[snapshot.deviceId] = snapshot
    }

    fun getRemoteTabsForDevice(deviceId: String): List<SyncedTab> {
        return remoteDeviceTabs[deviceId]?.tabs ?: emptyList()
    }

    fun getAllRemoteDeviceSnapshots(): List<DeviceTabSnapshot> {
        return remoteDeviceTabs.values.toList()
    }

    fun removeDeviceTabs(deviceId: String) {
        remoteDeviceTabs.remove(deviceId)
    }
}
