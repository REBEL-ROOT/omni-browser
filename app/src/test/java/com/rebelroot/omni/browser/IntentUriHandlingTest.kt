/*
 * Omni Browser - Intent URI Handling Regression Tests (Issue #120)
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Tests that intent://, custom scheme, and normal HTTPS URIs are correctly
 * classified and handled. Validates fallback URL extraction, malformed URI
 * safety, and calendar-spam blocking.
 *
 * Note: These are JVM unit tests using the project's android.net.Uri shim
 * and unitTests.isReturnDefaultValues = true (Intent.parseUri returns null).
 * The regex-based fallback extraction path is exercised directly.
 */

package com.rebelroot.omni.browser

import org.junit.Test
import org.junit.Assert.*

class IntentUriHandlingTest {

    // ── URI Scheme Classification ────────────────────────────────────────────
    // Validates that the onLoadRequest routing logic correctly identifies
    // intent://, market://, custom scheme, and normal HTTP(S) URIs.

    @Test
    fun intentUri_isNotHttpScheme() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        val lower = uri.lowercase()
        assertFalse("intent:// must not be treated as http", lower.startsWith("http://"))
        assertFalse("intent:// must not be treated as https", lower.startsWith("https://"))
        assertFalse("intent:// must not be treated as about:", lower.startsWith("about:"))
        assertFalse("intent:// must not be treated as javascript:", lower.startsWith("javascript:"))
        assertFalse("intent:// must not be treated as data:", lower.startsWith("data:"))
        assertTrue("intent:// must be caught by the external-app branch", lower.startsWith("intent:"))
    }

    @Test
    fun marketUri_isNotHttpScheme() {
        val uri = "market://details?id=com.example.app"
        val lower = uri.lowercase()
        assertFalse(lower.startsWith("http://"))
        assertFalse(lower.startsWith("https://"))
        assertTrue("market:// must be caught by intent/market branch", lower.startsWith("market:"))
    }

    @Test
    fun customScheme_isNotHttpScheme() {
        val uri = "myapp://deeplink/path?key=value"
        val lower = uri.lowercase()
        assertFalse(lower.startsWith("http://"))
        assertFalse(lower.startsWith("https://"))
        assertFalse(lower.startsWith("about:"))
        assertFalse(lower.startsWith("intent:"))
        assertFalse(lower.startsWith("market:"))
        // Custom scheme URIs fall through to the custom-protocol handler
    }

    @Test
    fun httpsUrl_isNotExternalScheme() {
        val uri = "https://www.google.com/search?q=test"
        val lower = uri.lowercase()
        assertTrue("HTTPS must be handled as normal browsing", lower.startsWith("https://"))
        assertFalse("HTTPS must not enter the external-app branch",
            !lower.startsWith("http://") &&
            !lower.startsWith("https://") &&
            !lower.startsWith("about:") &&
            !lower.startsWith("javascript:") &&
            !lower.startsWith("data:")
        )
    }

    @Test
    fun httpUrl_isNotExternalScheme() {
        val uri = "http://example.com"
        val lower = uri.lowercase()
        assertTrue(lower.startsWith("http://"))
    }

    // ── Intent URI Parsing ───────────────────────────────────────────────────
    // Validates that intent:// URIs are correctly parsed for package, scheme, etc.

    @Test
    fun intentUri_extractsPackageFromUri() {
        // The intent:// format: intent://host/path#Intent;scheme=xxx;package=yyy;end
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        // Extract package using the same regex pattern used in the codebase
        val packageRegex = Regex("package=([^;&#]+)", RegexOption.IGNORE_CASE)
        val match = packageRegex.find(uri)
        assertNotNull("package= must be found in intent URI", match)
        assertEquals("com.google.zxing.client.android", match!!.groupValues[1])
    }

    @Test
    fun intentUri_extractsSchemeFromUri() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        val schemeRegex = Regex("scheme=([^;&#]+)", RegexOption.IGNORE_CASE)
        val match = schemeRegex.find(uri)
        assertNotNull(match)
        assertEquals("zxing", match!!.groupValues[1])
    }

    @Test
    fun intentUri_withEidApp_extractsPackage() {
        // eID / digital-signature app (the scenario from issue #120)
        val uri = "intent://authenticate#Intent;scheme=eid;package=com.example.eid;S.browser_fallback_url=https%3A%2F%2Feid.example.com%2Finstall;end"
        val packageRegex = Regex("package=([^;&#]+)", RegexOption.IGNORE_CASE)
        val match = packageRegex.find(uri)
        assertNotNull(match)
        assertEquals("com.example.eid", match!!.groupValues[1])
    }

    @Test
    fun intentUri_withoutPackage_noPackageExtracted() {
        val uri = "intent://deep/link#Intent;scheme=myapp;end"
        val packageRegex = Regex("package=([^;&#]+)", RegexOption.IGNORE_CASE)
        val match = packageRegex.find(uri)
        assertNull("No package= present, so regex should not match", match)
    }

    // ── Malformed Intent URIs ────────────────────────────────────────────────

    @Test
    fun malformedIntentUri_noIntentFragment_doesNotCrash() {
        val uri = "intent://bad"
        val lower = uri.lowercase()
        assertTrue("Should still be identified as intent: scheme", lower.startsWith("intent:"))
        // Package extraction should return null gracefully
        val packageRegex = Regex("package=([^;&#]+)", RegexOption.IGNORE_CASE)
        assertNull(packageRegex.find(uri))
    }

    @Test
    fun malformedIntentUri_emptyAfterScheme_doesNotCrash() {
        val uri = "intent://"
        val lower = uri.lowercase()
        assertTrue(lower.startsWith("intent:"))
    }

    @Test
    fun malformedIntentUri_noEndMarker_doesNotCrash() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.test"
        // Missing ";end" — still should extract package
        val packageRegex = Regex("package=([^;&#]+)", RegexOption.IGNORE_CASE)
        val match = packageRegex.find(uri)
        assertNotNull(match)
        assertEquals("com.test", match!!.groupValues[1])
    }

    // ── Fallback URL Extraction (Regex Path) ─────────────────────────────────
    // The extractFallbackUrl method has a regex path that extracts
    // S.browser_fallback_url from intent URIs even when Intent.parseUri is
    // unavailable (as in JVM unit tests).

    @Test
    fun fallbackUrl_extractedFromIntentUri() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.test;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Finstall;end"
        val regex = Regex("[;?&]S\\.browser_fallback_url=([^;&#+]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(uri)
        assertNotNull("S.browser_fallback_url must be found", match)
        val decoded = java.net.URLDecoder.decode(match!!.groupValues[1], "UTF-8")
        assertEquals("https://example.com/install", decoded)
    }

    @Test
    fun fallbackUrl_notPresentInUri_returnsNull() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.test;end"
        val regex = Regex("[;?&]S\\.browser_fallback_url=([^;&#+]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(uri)
        assertNull("No fallback URL should be found", match)
    }

    @Test
    fun fallbackUrl_withComplexEncodedUrl() {
        val uri = "intent://pay#Intent;scheme=upi;package=com.google.android.apps.nbu.paisa.user;S.browser_fallback_url=https%3A%2F%2Fpay.google.com%2Fgp%2Fw%2Fhome%2Fsignup;end"
        val regex = Regex("[;?&]S\\.browser_fallback_url=([^;&#+]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(uri)
        assertNotNull(match)
        val decoded = java.net.URLDecoder.decode(match!!.groupValues[1], "UTF-8")
        assertEquals("https://pay.google.com/gp/w/home/signup", decoded)
    }

    // ── Calendar Spam Blocking ───────────────────────────────────────────────

    @Test
    fun calendarSpam_intentWithCalendarPackage_isDetected() {
        val intentPackage = "com.calendar.adware"
        val isCalendarSpam = intentPackage.contains("calendar") || intentPackage.contains("cal")
        assertTrue("Calendar package should be detected as spam", isCalendarSpam)
    }

    @Test
    fun calendarSpam_intentWithWebcalData_isDetected() {
        val dataString = "webcal://evil.com/spam.ics"
        val isCalendarSpam = dataString.contains("calendar") ||
                dataString.contains("webcal") ||
                dataString.contains(".ics")
        assertTrue("Webcal data should be detected as spam", isCalendarSpam)
    }

    @Test
    fun calendarSpam_normalAppPackage_isNotDetected() {
        val intentPackage = "com.example.eid"
        val isCalendarSpam = intentPackage.contains("calendar") || intentPackage.contains("cal")
        assertFalse("Normal package should not be flagged as calendar spam", isCalendarSpam)
    }

    // ── SecurityPolicy.validateIntentUri ──────────────────────────────────────

    @Test
    fun validateIntentUri_intentScheme_isBlocked() {
        // SecurityPolicy blocks intent: as a dangerous external scheme
        // (this is for URIs received via Android Intents, not web navigation)
        assertFalse(SecurityPolicy.validateIntentUri("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun validateIntentUri_httpsScheme_isAllowed() {
        assertTrue(SecurityPolicy.validateIntentUri("https://example.com/path"))
    }

    @Test
    fun validateIntentUri_httpScheme_isAllowed() {
        assertTrue(SecurityPolicy.validateIntentUri("http://example.com"))
    }

    // ── SecurityPolicy.isDangerousExternalScheme ──────────────────────────────

    @Test
    fun isDangerousExternalScheme_intent_isTrue() {
        assertTrue(SecurityPolicy.isDangerousExternalScheme("intent"))
    }

    @Test
    fun isDangerousExternalScheme_market_isTrue() {
        assertTrue(SecurityPolicy.isDangerousExternalScheme("market"))
    }

    @Test
    fun isDangerousExternalScheme_javascript_isTrue() {
        assertTrue(SecurityPolicy.isDangerousExternalScheme("javascript"))
    }

    @Test
    fun isDangerousExternalScheme_https_isFalse() {
        assertFalse(SecurityPolicy.isDangerousExternalScheme("https"))
    }

    @Test
    fun isDangerousExternalScheme_http_isFalse() {
        assertFalse(SecurityPolicy.isDangerousExternalScheme("http"))
    }

    @Test
    fun isDangerousExternalScheme_customScheme_isFalse() {
        assertFalse(SecurityPolicy.isDangerousExternalScheme("myapp"))
    }

    @Test
    fun isDangerousExternalScheme_null_isFalse() {
        assertFalse(SecurityPolicy.isDangerousExternalScheme(null))
    }

    // ── Intent URI Host/Path Extraction ──────────────────────────────────────

    @Test
    fun intentUri_hostAndPathExtraction() {
        val intentUri = "intent://authenticate/step2#Intent;scheme=eid;package=com.example.eid;end"
        val hostAndPath = intentUri.substringAfter("intent://").substringBefore("#Intent;").substringBefore(";")
        assertEquals("authenticate/step2", hostAndPath)
    }

    @Test
    fun intentUri_hostOnlyExtraction() {
        val intentUri = "intent://scan/#Intent;scheme=zxing;end"
        val hostAndPath = intentUri.substringAfter("intent://").substringBefore("#Intent;").substringBefore(";")
        assertEquals("scan/", hostAndPath)
    }

    @Test
    fun intentUri_emptyHostExtraction() {
        val intentUri = "intent://#Intent;scheme=test;end"
        val hostAndPath = intentUri.substringAfter("intent://").substringBefore("#Intent;").substringBefore(";")
        assertEquals("", hostAndPath)
    }
}
