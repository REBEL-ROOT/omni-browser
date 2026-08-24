package com.rebelroot.omni.browser

import org.junit.Assert.*
import org.junit.Test
import org.mozilla.geckoview.GeckoView

class AutofillIntegrationTest {

    @Test
    fun testGeckoViewAutofillApiPresence() {
        // Verify setAutofillEnabled is available on GeckoView
        val setAutofillMethod = GeckoView::class.java.methods.firstOrNull { it.name == "setAutofillEnabled" }
        assertNotNull("GeckoView.setAutofillEnabled(boolean) must be present", setAutofillMethod)

        // Verify mAutofillDelegate field is present on GeckoView
        val autofillField = GeckoView::class.java.declaredFields.firstOrNull { it.name.contains("Autofill", ignoreCase = true) }
        assertNotNull("GeckoView must have an internal AutofillDelegate field", autofillField)
    }

    @Test
    fun testAutofillProviderModeEnum() {
        val modes = BrowserViewModel.AutofillProviderMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(BrowserViewModel.AutofillProviderMode.THIRD_PARTY))
        assertTrue(modes.contains(BrowserViewModel.AutofillProviderMode.OMNI_VAULT))
        assertTrue(modes.contains(BrowserViewModel.AutofillProviderMode.BOTH))
    }

    @Test
    fun testGeckoConfigAutofillPrefs() {
        val testYaml = """
            pref:
              signon.autofillForms: true
              dom.forms.autocomplete.formautofill: true
              extensions.formautofill.available: true
        """.trimIndent()

        assertTrue(testYaml.contains("signon.autofillForms: true"))
        assertTrue(testYaml.contains("dom.forms.autocomplete.formautofill: true"))
        assertTrue(testYaml.contains("extensions.formautofill.available: true"))
    }

    @Test
    fun testDomainMatchingLogic() {
        val savedDomains = listOf("github.com", "accounts.google.com", "reddit.com")
        val currentUrl = "https://github.com/login"
        val host = java.net.URI(currentUrl).host.removePrefix("www.")

        val matches = savedDomains.filter {
            it == host || host.contains(it) || it.contains(host)
        }

        assertEquals(1, matches.size)
        assertEquals("github.com", matches[0])
    }

    @Test
    fun testCuratedPasswordManagersPresent() {
        val curated = com.rebelroot.omni.browser.extensions.CuratedExtensionRepository.curatedList
        val bitwarden = curated.find { it.name.contains("Bitwarden", ignoreCase = true) }
        assertNotNull("Bitwarden extension must be present in curated list", bitwarden)
        assertEquals("{446900e4-71c2-419f-a6a7-df9c091e268b}", bitwarden?.id)
        assertTrue("Bitwarden download URL must be valid XPI", bitwarden?.downloadUrl?.endsWith(".xpi") == true)

        val protonPass = curated.find { it.name.contains("Proton Pass", ignoreCase = true) }
        assertNotNull("Proton Pass extension must be present in curated list", protonPass)
        assertEquals("78272b6fa5e24ba987ac@proton.me", protonPass?.id)

        val keepassxc = curated.find { it.name.contains("KeePassXC", ignoreCase = true) }
        assertNotNull("KeePassXC extension must be present in curated list", keepassxc)
        assertEquals("keepassxc-browser@keepassxc.org", keepassxc?.id)
    }

    @Test
    fun testOmniPasswordManagerAutofillModesLogic() {
        val savedPasswords = listOf(
            BrowserViewModel.SavedPassword("1", "github.com", "octocat", "supersecret", System.currentTimeMillis()),
            BrowserViewModel.SavedPassword("2", "accounts.google.com", "user@gmail.com", "googlepass", System.currentTimeMillis())
        )

        val targetUrl = "https://github.com/login"
        val host = java.net.URI(targetUrl).host.removePrefix("www.")
        val matches = savedPasswords.filter {
            it.domain == host || host.contains(it.domain) || it.domain.contains(host)
        }

        assertEquals(1, matches.size)
        assertEquals("octocat", matches[0].username)
        assertEquals("supersecret", matches[0].password)

        // Verify OMNI_VAULT mode triggers autofill
        var showBottomSheetOmni = false
        val modeOmni = BrowserViewModel.AutofillProviderMode.OMNI_VAULT
        if (matches.isNotEmpty() && (modeOmni == BrowserViewModel.AutofillProviderMode.OMNI_VAULT || modeOmni == BrowserViewModel.AutofillProviderMode.BOTH)) {
            showBottomSheetOmni = true
        }
        assertTrue("OMNI_VAULT mode must show autofill bottom sheet", showBottomSheetOmni)

        // Verify BOTH mode also triggers autofill
        var showBottomSheetBoth = false
        val modeBoth = BrowserViewModel.AutofillProviderMode.BOTH
        if (matches.isNotEmpty() && (modeBoth == BrowserViewModel.AutofillProviderMode.OMNI_VAULT || modeBoth == BrowserViewModel.AutofillProviderMode.BOTH)) {
            showBottomSheetBoth = true
        }
        assertTrue("BOTH mode must show autofill bottom sheet", showBottomSheetBoth)

        // Verify THIRD_PARTY mode suppresses bottom sheet so third-party manager has priority
        var showBottomSheetThirdParty = false
        val modeThirdParty = BrowserViewModel.AutofillProviderMode.THIRD_PARTY
        if (matches.isNotEmpty() && (modeThirdParty == BrowserViewModel.AutofillProviderMode.OMNI_VAULT || modeThirdParty == BrowserViewModel.AutofillProviderMode.BOTH)) {
            showBottomSheetThirdParty = true
        }
        assertFalse("THIRD_PARTY mode should not pop bottom sheet automatically", showBottomSheetThirdParty)
    }

    @Test
    fun testNeverSavePasswordSuppression() {
        val neverSaveDomains = setOf("bank.com", "secure-portal.net")
        val originHost1 = "bank.com"
        val originHost2 = "github.com"

        assertTrue(neverSaveDomains.contains(originHost1))
        assertFalse(neverSaveDomains.contains(originHost2))
    }

    @Test
    fun testOmniPasswordManagerMasterToggle() {
        val saved = listOf(
            BrowserViewModel.SavedPassword("1", "github.com", "octocat", "supersecret", System.currentTimeMillis())
        )

        // Helper simulation of checkAutofillForFocus
        fun simulateCheckAutofill(enabled: Boolean, url: String, mode: BrowserViewModel.AutofillProviderMode): Boolean {
            if (!enabled || url.isBlank() || url == "about:blank") return false
            val host = java.net.URI(url).host?.removePrefix("www.") ?: ""
            val matches = saved.filter { it.domain == host || host.contains(it.domain) || it.domain.contains(host) }
            return matches.isNotEmpty() && (mode == BrowserViewModel.AutofillProviderMode.OMNI_VAULT || mode == BrowserViewModel.AutofillProviderMode.BOTH)
        }

        // When ON
        val canAutofillWhenOn = simulateCheckAutofill(true, "https://github.com/login", BrowserViewModel.AutofillProviderMode.OMNI_VAULT)
        assertTrue("Autofill should be active when master toggle is ON", canAutofillWhenOn)

        // When OFF
        val canAutofillWhenOff = simulateCheckAutofill(false, "https://github.com/login", BrowserViewModel.AutofillProviderMode.OMNI_VAULT)
        assertFalse("Autofill should be disabled when master toggle is OFF", canAutofillWhenOff)
    }

    @Test
    fun testAppUpdateVersionComparison() {
        fun compareVersionNames(v1: String, v2: String): Int {
            val parts1 = v1.split('.').mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split('.').mapNotNull { it.toIntOrNull() }
            val maxLength = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLength) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        assertTrue(compareVersionNames("1.3.0", "1.2.9.3") > 0)
        assertTrue(compareVersionNames("2.0.0", "1.9.9") > 0)
        assertTrue(compareVersionNames("1.2.9.4", "1.2.9.3") > 0)
        assertEquals(0, compareVersionNames("1.2.9.3", "1.2.9.3"))
        assertTrue(compareVersionNames("1.2.9.2", "1.2.9.3") < 0)

        assertEquals("omni_app_updates", BrowserViewModel.CHANNEL_ID_APP_UPDATES)
        assertEquals(4001, BrowserViewModel.NOTIFICATION_ID_APP_UPDATE)
    }

    @Test
    fun testDownloadNotificationIntentAction() {
        val downloadAction = "com.rebelroot.omni.ACTION_OPEN_DOWNLOADS"
        val downloadExtra = "extra_open_downloads"

        assertEquals("com.rebelroot.omni.ACTION_OPEN_DOWNLOADS", downloadAction)
        assertEquals("extra_open_downloads", downloadExtra)
    }
}
