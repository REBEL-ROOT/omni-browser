package com.rebelroot.omni.sync.mozilla

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FxAccountManagerTest {

    @Test
    fun testBeginLoginUrlConstruction() {
        val manager = FxAccountManager.getInstance()
        val customState = "test_state_12345"
        val loginUrl = manager.beginLogin(customState)

        assertTrue(loginUrl.startsWith("https://accounts.firefox.com/authorization"))
        assertTrue(loginUrl.contains("client_id=a2270f727f45f648"))
        assertTrue(loginUrl.contains("response_type=code"))
        assertTrue(loginUrl.contains("state=test_state_12345"))
        assertTrue(loginUrl.contains("scope=profile"))
    }

    @Test
    fun testIsFirefoxPairingUrl() {
        val manager = FxAccountManager.getInstance()

        assertTrue(manager.isFirefoxPairingUrl("https://firefox.com/pair?channel=abc&key=123"))
        assertTrue(manager.isFirefoxPairingUrl("https://accounts.firefox.com/pair?channel=xyz"))
        assertTrue(manager.isFirefoxPairingUrl("https://accounts.firefox.com/connect_another_device?channel=456"))
        assertFalse(manager.isFirefoxPairingUrl("https://google.com"))
        assertFalse(manager.isFirefoxPairingUrl("https://firefox.com/about"))
    }

    @Test
    fun testPairWithDesktopQr() {
        val manager = FxAccountManager.getInstance()
        var successEmail: String? = null
        var errorMsg: String? = null

        val sampleQrUrl = "https://firefox.com/pair?channel=test_chan_123&key=test_key_456&email=desktop.user%40example.com"
        manager.pairWithDesktopQr(
            pairingUrl = sampleQrUrl,
            onSuccess = { email -> successEmail = email },
            onError = { err -> errorMsg = err }
        )

        assertNull(errorMsg)
        assertEquals("desktop.user@example.com", successEmail)
        val state = manager.accountState.value
        assertTrue(state is FxaState.SignedIn)
        assertEquals("desktop.user@example.com", (state as FxaState.SignedIn).email)
    }
}
