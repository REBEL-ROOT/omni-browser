package com.rebelroot.omni.sync.mozilla

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MozillaSyncManagerTest {

    private lateinit var syncManager: MozillaSyncManager
    private lateinit var collection: BookmarkCollection

    @Before
    fun setUp() {
        syncManager = MozillaSyncManager()
        collection = BookmarkCollection()
    }

    @Test
    fun testSyncWithoutAuthReturnsError() {
        val latch = CountDownLatch(1)
        var syncSuccess = true

        syncManager.syncNow(
            collection = collection,
            tabs = emptyList()
        ) { success ->
            syncSuccess = success
            latch.countDown()
        }

        latch.await(3, TimeUnit.SECONDS)
        assertFalse(syncSuccess)

        val state = syncManager.syncState.value
        assertTrue(state is MozSyncState.Error)
    }

    @Test
    fun testLoginBridgeDeduplication() {
        val bridge = MozillaLoginBridge()
        val localLogins = listOf(
            LoginEntry(
                guid = "l1",
                hostname = "https://example.com",
                username = "admin",
                password = "oldPassword1",
                timePasswordChanged = 1000L
            )
        )

        val remoteLogins = listOf(
            LoginEntry(
                guid = "l1_remote",
                hostname = "https://example.com",
                username = "admin",
                password = "newPassword2",
                timePasswordChanged = 2000L
            )
        )

        val merged = bridge.mergeLogins(localLogins, remoteLogins)
        assertEquals(1, merged.size)
        assertEquals("newPassword2", merged[0].password)
    }
}
