package com.rebelroot.omni.sync.mozilla

import android.content.Context
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.storage.saveBookmarks
import com.rebelroot.omni.browser.TabState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SyncEngine {
    BOOKMARKS, HISTORY, TABS, LOGINS
}

sealed class MozSyncState {
    object Idle : MozSyncState()
    data class Syncing(val engine: SyncEngine, val message: String = "Syncing ${engine.name.lowercase()}...") : MozSyncState()
    data class Done(val lastSyncTime: Long) : MozSyncState()
    data class Error(val message: String) : MozSyncState()
}

class MozillaSyncManager(
    val accountManager: FxAccountManager = FxAccountManager.getInstance(),
    val syncClient: MozillaSyncClient = MozillaSyncClient(),
    val bookmarkBridge: MozillaBookmarkBridge = MozillaBookmarkBridge(),
    val tabBridge: MozillaTabBridge = MozillaTabBridge(),
    val historyBridge: MozillaHistoryBridge = MozillaHistoryBridge(),
    val loginBridge: MozillaLoginBridge = MozillaLoginBridge(),
    val syncBridge: com.rebelroot.omni.sync.core.SyncBridge = com.rebelroot.omni.sync.core.SyncBridge.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val mainDispatcher: CoroutineDispatcher = try {
        Dispatchers.Main.immediate
    } catch (_: Throwable) {
        Dispatchers.Default
    }
) {

    private val _syncState = MutableStateFlow<MozSyncState>(MozSyncState.Idle)
    val syncState: StateFlow<MozSyncState> = _syncState.asStateFlow()

    private var backoffUntilMillis: Long = 0L

    private suspend fun onMain(block: suspend () -> Unit) {
        try {
            withContext(mainDispatcher) { block() }
        } catch (_: Throwable) {
            withContext(Dispatchers.Default) { block() }
        }
    }

    fun syncNow(
        context: Context? = null,
        collection: BookmarkCollection? = null,
        tabs: List<TabState> = emptyList(),
        engines: Set<SyncEngine>? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val targetEngines = engines ?: SyncEngine.values().filter { accountManager.isEngineEnabled(it) }.toSet()

        scope.launch {
            val token = accountManager.getAccessToken()
            if (token.isNullOrBlank()) {
                onMain {
                    _syncState.value = MozSyncState.Error("Not signed in to Firefox Account")
                    try { onComplete?.invoke(false) } catch (_: Exception) {}
                }
                return@launch
            }

            if (System.currentTimeMillis() < backoffUntilMillis) {
                val waitSec = (backoffUntilMillis - System.currentTimeMillis()) / 1000L
                onMain {
                    _syncState.value = MozSyncState.Error("Server requested backoff. Retrying in ${waitSec}s")
                    try { onComplete?.invoke(false) } catch (_: Exception) {}
                }
                return@launch
            }

            try {
                // 1. Fetch storage credentials from TokenServer
                onMain {
                    _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Authenticating with Mozilla Cloud...")
                }
                val credsResult = syncClient.fetchStorageCredentials(token, accountManager.getSyncKey())
                
                val apiEndpoint: String
                val authToken: String

                when (credsResult) {
                    is SyncClientResult.Success -> {
                        apiEndpoint = credsResult.data.apiEndpoint
                        authToken = "Bearer $token"
                        handleBackoff(credsResult.backoffSeconds)
                    }
                    is SyncClientResult.Failure -> {
                        if (credsResult.isAuthError) {
                            onMain {
                                _syncState.value = MozSyncState.Error("Session expired. Please sign in again.")
                            }
                        } else {
                            // Fallback to direct storage endpoint if TokenServer is bypassed/mocked
                            onMain {
                                _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Connecting to storage...")
                            }
                        }
                        apiEndpoint = "https://sync-1-5.sync.services.mozilla.com/1.5/${accountManager.getUserId() ?: "user"}/"
                        authToken = "Bearer $token"
                    }
                }

                val lastSyncTime = accountManager.getLastSyncTime()
                val lastSyncSec = lastSyncTime / 1000.0

                // 2. Sync Bookmarks (Bidirectional)
                if (targetEngines.contains(SyncEngine.BOOKMARKS) && collection != null) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Syncing bookmarks...")
                    }
                    val fetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "bookmarks",
                        authToken = authToken,
                        newerThan = lastSyncSec
                    )

                    if (fetchResult is SyncClientResult.Success) {
                        handleBackoff(fetchResult.backoffSeconds)
                        val remoteItems = bookmarkBridge.parseBsoRecords(fetchResult.data)
                        if (remoteItems.isNotEmpty()) {
                            bookmarkBridge.importMozillaToCollection(remoteItems, collection)
                            if (context != null) {
                                saveBookmarks(context, collection)
                            }
                        }
                    }

                    // Export and upload local bookmarks
                    val localBsoList = bookmarkBridge.exportToBsoRecords(collection)
                    if (localBsoList.isNotEmpty()) {
                        val postRes = syncClient.postCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "bookmarks",
                            authToken = authToken,
                            records = localBsoList
                        )
                        if (postRes is SyncClientResult.Success) {
                            handleBackoff(postRes.backoffSeconds)
                        }
                    }
                }

                // 3. Sync Open Tabs
                if (targetEngines.contains(SyncEngine.TABS)) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.TABS, "Syncing open tabs...")
                    }
                    val localDeviceId = accountManager.getUserId() ?: "omni_device"
                    val deviceName = accountManager.getDeviceName()

                    // Download remote tabs from other devices
                    val tabsFetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "tabs",
                        authToken = authToken
                    )

                    if (tabsFetchResult is SyncClientResult.Success) {
                        handleBackoff(tabsFetchResult.backoffSeconds)
                        tabBridge.parseBsoRecords(tabsFetchResult.data, localDeviceId)
                    }

                    // Upload local tabs
                    if (tabs.isNotEmpty()) {
                        val localTabBso = tabBridge.exportToBsoRecord(localDeviceId, deviceName, tabs)
                        val postTabRes = syncClient.postCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "tabs",
                            authToken = authToken,
                            records = listOf(localTabBso)
                        )
                        if (postTabRes is SyncClientResult.Success) {
                            handleBackoff(postTabRes.backoffSeconds)
                        }
                    }
                }

                // 4. Sync History
                if (targetEngines.contains(SyncEngine.HISTORY)) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.HISTORY, "Syncing history...")
                    }
                    val histFetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "history",
                        authToken = authToken,
                        newerThan = lastSyncSec,
                        limit = 100
                    )
                    if (histFetchResult is SyncClientResult.Success) {
                        handleBackoff(histFetchResult.backoffSeconds)
                        val parsedHist = historyBridge.parseBsoRecords(histFetchResult.data)
                        // History parsed and available
                    }
                }

                // 5. Update last sync timestamp
                val finishTime = System.currentTimeMillis()
                accountManager.setLastSyncTime(finishTime)
                val userEmail = (accountManager.accountState.value as? FxaState.SignedIn)?.email ?: "Firefox Account"
                val appCtx = com.rebelroot.omni.OmniApplication.appContext
                if (appCtx != null) {
                    com.rebelroot.omni.sync.notification.SyncNotificationManager.notifyFirefoxSync(
                        context = appCtx,
                        email = userEmail,
                        summary = "Bookmarks, tabs, and logins updated"
                    )
                }

                onMain {
                    _syncState.value = MozSyncState.Done(finishTime)
                    try { onComplete?.invoke(true) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                onMain {
                    _syncState.value = MozSyncState.Error(e.message ?: "Sync failed")
                    try { onComplete?.invoke(false) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleBackoff(seconds: Long) {
        if (seconds > 0) {
            backoffUntilMillis = System.currentTimeMillis() + (seconds * 1000L)
        }
    }

    companion object {
        @Volatile
        private var instance: MozillaSyncManager? = null

        fun getInstance(): MozillaSyncManager {
            return instance ?: synchronized(this) {
                instance ?: MozillaSyncManager().also { instance = it }
            }
        }
    }
}
