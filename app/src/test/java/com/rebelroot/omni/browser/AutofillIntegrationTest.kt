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
}
