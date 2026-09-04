package com.rebelroot.omni.sync.mozilla

import org.junit.Assert.*
import org.junit.Test

class MozillaSyncClientTest {

    @Test
    fun testBsoRecordDataHolder() {
        val bso = BsoRecord(
            id = "bmk_test123",
            modified = 1724500000.5,
            payload = "{\"id\":\"bmk_test123\",\"title\":\"Test\"}",
            sortindex = 10,
            ttl = 3600
        )

        assertEquals("bmk_test123", bso.id)
        assertEquals(1724500000.5, bso.modified, 0.001)
        assertEquals(10, bso.sortindex)
        assertEquals(3600, bso.ttl)
    }

    @Test
    fun testTokenServerResponseDataHolder() {
        val resp = TokenServerResponse(
            id = "usr_12345",
            key = "secret_key_abc",
            apiEndpoint = "https://sync-1-5.sync.services.mozilla.com/1.5/usr_12345",
            durationSeconds = 7200,
            hashAlgorithm = "sha256"
        )

        assertEquals("usr_12345", resp.id)
        assertEquals("secret_key_abc", resp.key)
        assertEquals("https://sync-1-5.sync.services.mozilla.com/1.5/usr_12345", resp.apiEndpoint)
        assertEquals(7200L, resp.durationSeconds)
        assertEquals("sha256", resp.hashAlgorithm)
    }

    @Test
    fun testGenerateHawkHeader() {
        val client = MozillaSyncClient()
        val url = java.net.URL("https://token.services.mozilla.com/1.0/sync/1.5")
        val hawkHeader = client.generateHawkHeader(
            id = "test_user_id",
            key = "secret_key_12345",
            method = "GET",
            url = url
        )

        assertTrue(hawkHeader.startsWith("Hawk id=\"test_user_id\""))
        assertTrue(hawkHeader.contains("ts="))
        assertTrue(hawkHeader.contains("nonce="))
        assertTrue(hawkHeader.contains("mac="))
    }
}
