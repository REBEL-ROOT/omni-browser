/*
 * Omni Browser - Core Sync Pipeline
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.sync.core

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.browser.TabState
import com.rebelroot.omni.sync.model.Hlc
import com.rebelroot.omni.sync.model.HlcClock
import com.rebelroot.omni.sync.model.SyncOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Listener interface for sync adapters (e.g. Mozilla Sync Adapter, Omni Mesh Extension Adapter)
 * to receive local mutations and dispatch remote changes.
 */
interface SyncDataObserver {
    fun onBookmarkMutation(operation: SyncOperation) {}
    fun onTabsChanged(tabs: List<TabState>) {}
    fun onHistoryChanged() {}
}

/**
 * Central SyncBridge that anchors all local browser mutations and dispatches them
 * to active synchronization backends (Mozilla Firefox Sync and/or Omni Mesh LAN Extension).
 */
class SyncBridge private constructor(
    val deviceId: String = "omni_" + java.util.UUID.randomUUID().toString().take(8)
) {
    val clock = HlcClock(deviceId)

    private val _mutationEvents = MutableSharedFlow<SyncOperation>(extraBufferCapacity = 64)
    val mutationEvents: SharedFlow<SyncOperation> = _mutationEvents.asSharedFlow()

    private val observers = mutableListOf<SyncDataObserver>()
    private val scope = CoroutineScope(Dispatchers.Default)

    fun registerObserver(observer: SyncDataObserver) {
        synchronized(observers) {
            if (!observers.contains(observer)) {
                observers.add(observer)
            }
        }
    }

    fun unregisterObserver(observer: SyncDataObserver) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    /**
     * Called whenever a local bookmark or folder is created, modified, moved, or deleted.
     */
    fun recordBookmarkMutation(operation: SyncOperation) {
        scope.launch {
            _mutationEvents.emit(operation)
            synchronized(observers) {
                observers.forEach { it.onBookmarkMutation(operation) }
            }
        }
    }

    var tabBridge: com.rebelroot.omni.sync.mozilla.MozillaTabBridge? = null
    var localTabs: List<TabState> = emptyList()

    fun updateRemoteDeviceTabs(deviceId: String, deviceName: String, tabs: List<com.rebelroot.omni.sync.mozilla.TabInfo>) {
        tabBridge?.updateDirectRemoteTabs(deviceId, deviceName, tabs)
    }

    /**
     * Called whenever open tabs change (tab opened, closed, navigated).
     */
    fun recordTabsChanged(tabs: List<TabState>) {
        localTabs = tabs
        scope.launch {
            synchronized(observers) {
                observers.forEach { it.onTabsChanged(tabs) }
            }
        }
    }

    /**
     * Called whenever browsing history is updated.
     */
    fun recordHistoryChanged() {
        scope.launch {
            synchronized(observers) {
                observers.forEach { it.onHistoryChanged() }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SyncBridge? = null

        fun getInstance(): SyncBridge {
            return instance ?: synchronized(this) {
                instance ?: SyncBridge().also { instance = it }
            }
        }
    }
}
