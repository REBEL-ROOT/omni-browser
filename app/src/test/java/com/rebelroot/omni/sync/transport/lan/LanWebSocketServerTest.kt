package com.rebelroot.omni.sync.transport.lan

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.conflict.ConflictEngine
import com.rebelroot.omni.sync.core.SyncBridge
import com.rebelroot.omni.sync.crypto.DeviceKeyManager
import com.rebelroot.omni.sync.crypto.TrustManager
import com.rebelroot.omni.sync.model.HlcClock
import com.rebelroot.omni.sync.model.SyncEntityType
import com.rebelroot.omni.sync.model.SyncOpType
import com.rebelroot.omni.sync.model.SyncOperation
import com.rebelroot.omni.sync.storage.SyncStorage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanWebSocketServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var keyManager: DeviceKeyManager
    private lateinit var trustManager: TrustManager
    private lateinit var clock: HlcClock
    private lateinit var adapter: BookmarkAdapter
    private lateinit var storage: SyncStorage
    private lateinit var conflictEngine: ConflictEngine
    private lateinit var collection: BookmarkCollection
    private lateinit var server: LanWebSocketServer

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("sync_test")
        keyManager = DeviceKeyManager(rootDir)
        trustManager = TrustManager(rootDir)
        clock = HlcClock(keyManager.deviceId)
        adapter = BookmarkAdapter(clock)
        storage = SyncStorage(rootDir, clock)
        conflictEngine = ConflictEngine(adapter, storage)
        collection = BookmarkCollection()

        server = LanWebSocketServer(
            port = 0,
            keyManager = keyManager,
            trustManager = trustManager,
            storage = storage,
            conflictEngine = conflictEngine,
            collection = collection,
            syncBridge = SyncBridge.getInstance()
        )
    }

    @Test
    fun testProcessSyncExchangePayload() {
        val incomingOp = JSONObject().apply {
            put("opId", "op_remote_test_1")
            put("opType", "CREATE")
            put("entityType", "BOOKMARK")
            put("entityId", "bmk_remote_1")
            put("hlc", clock.now().toString())
            put("bookmarkPayload", JSONObject().apply {
                put("parentId", "root")
                put("position", "a0")
                put("title", "Remote Bookmark")
                put("url", "https://example.com/remote")
                put("createdAt", System.currentTimeMillis())
                put("modifiedAt", System.currentTimeMillis())
                put("isDeleted", false)
            })
        }

        val requestPayload = JSONObject().apply {
            put("action", "SYNC_EXCHANGE")
            put("deviceId", "dev_remote_desktop")
            put("operations", JSONArray().apply { put(incomingOp) })
        }.toString()

        val responseJsonStr = server.processSyncExchangePayload(requestPayload)
        val responseJson = JSONObject(responseJsonStr)

        assertEquals("success", responseJson.getString("status"))
        assertEquals(1, responseJson.getInt("appliedCount"))
        assertTrue(responseJson.has("remoteOperations"))
    }

    @Test
    fun testGetLocalIpAddress() {
        val ip = LanWebSocketServer.getLocalIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotEmpty())
    }
}
