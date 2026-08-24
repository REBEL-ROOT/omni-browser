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
    fun testIsRedirectUrl() {
        val manager = FxAccountManager.getInstance()

        assertTrue(manager.isRedirectUrl("https://accounts.firefox.com/oauth/success/a2270f727f45f648?code=123"))
        assertTrue(manager.isRedirectUrl("omni://fxa-auth?code=123"))
        assertFalse(manager.isRedirectUrl("https://accounts.firefox.com/authorization"))
        assertFalse(manager.isRedirectUrl("https://google.com"))
    }
}
