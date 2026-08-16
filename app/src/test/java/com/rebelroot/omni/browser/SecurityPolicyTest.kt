/*
 * Omni Browser - Security Policy Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Tests for SecurityPolicy and OriginVerifier to prevent regression of
 * origin-spoofing, path-traversal, and intent-redirection vulnerabilities.
 */

package com.rebelroot.omni.browser

import org.junit.Test
import org.junit.Assert.*

class SecurityPolicyTest {

    // ── OriginVerifier.isExactOriginMatch ───────────────────────────────────

    @Test
    fun exactOriginMatch_accountsGoogleCom_matches() {
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.google.com/oauth", "accounts.google.com"))
    }

    @Test
    fun exactOriginMatch_wwwGoogleCom_doesNotMatchGoogleCom() {
        // Exact match should NOT match subdomains
        assertFalse(OriginVerifier.isExactOriginMatch("https://www.google.com", "google.com"))
    }

    @Test
    fun exactOriginMatch_evilGoogleCom_doesNotMatch() {
        // Critical: evilgoogle.com must NOT match google.com
        assertFalse(OriginVerifier.isExactOriginMatch("https://evilgoogle.com", "google.com"))
    }

    @Test
    fun exactOriginMatch_googleComEvilCom_doesNotMatch() {
        // google.com.evil.com must NOT match google.com
        assertFalse(OriginVerifier.isExactOriginMatch("https://google.com.evil.com", "google.com"))
    }

    @Test
    fun exactOriginMatch_userinfoSpoofing_doesNotMatch() {
        // user:pass@google.com@evil.com must NOT match google.com
        assertFalse(OriginVerifier.isExactOriginMatch("https://user:pass@google.com@evil.com/path", "google.com"))
    }

    @Test
    fun exactOriginMatch_idnHomograph_doesNotMatch() {
        // Cyrillic 'а' (U+0430) looks like Latin 'a' but is a different domain
        assertFalse(OriginVerifier.isExactOriginMatch("https://gооgle.com", "google.com"))
    }

    @Test
    fun exactOriginMatch_trailingDot_normalized() {
        // Trailing dot in FQDN should be normalized away
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.google.com.", "accounts.google.com"))
    }

    @Test
    fun exactOriginMatch_nullUri_returnsFalse() {
        assertFalse(OriginVerifier.isExactOriginMatch(null, "google.com"))
    }

    @Test
    fun exactOriginMatch_emptyDomain_returnsFalse() {
        assertFalse(OriginVerifier.isExactOriginMatch("https://google.com", ""))
    }

    // ── OriginVerifier.isSubdomainOf ────────────────────────────────────────

    @Test
    fun subdomainOf_wwwGoogleCom_matchesGoogleCom() {
        assertTrue(OriginVerifier.isSubdomainOf("https://www.google.com/search", "google.com"))
    }

    @Test
    fun subdomainOf_mailGoogleCom_matchesGoogleCom() {
        assertTrue(OriginVerifier.isSubdomainOf("https://mail.google.com", "google.com"))
    }

    @Test
    fun subdomainOf_googleCom_exactMatch() {
        // The domain itself should match
        assertTrue(OriginVerifier.isSubdomainOf("https://google.com", "google.com"))
    }

    @Test
    fun subdomainOf_evilGoogleCom_doesNotMatch() {
        // Critical: evilgoogle.com must NOT match google.com
        assertFalse(OriginVerifier.isSubdomainOf("https://evilgoogle.com", "google.com"))
    }

    @Test
    fun subdomainOf_googleComEvilCom_doesNotMatch() {
        // google.com.evil.com must NOT match google.com
        assertFalse(OriginVerifier.isSubdomainOf("https://google.com.evil.com", "google.com"))
    }

    @Test
    fun subdomainOf_notGoogleCom_doesNotMatch() {
        assertFalse(OriginVerifier.isSubdomainOf("https://not-google.com", "google.com"))
    }

    @Test
    fun subdomainOf_nullUri_returnsFalse() {
        assertFalse(OriginVerifier.isSubdomainOf(null, "google.com"))
    }

    @Test
    fun subdomainOf_emptyParent_returnsFalse() {
        assertFalse(OriginVerifier.isSubdomainOf("https://www.google.com", ""))
    }

    // ── SecurityPolicy.sanitizeFilename ───────────────────────────────────────

    @Test
    fun sanitizeFilename_normalFile_returnsUnchanged() {
        assertEquals("document.pdf", SecurityPolicy.sanitizeFilename("document.pdf"))
    }

    @Test
    fun sanitizeFilename_pathTraversal_stripped() {
        assertEquals("evil.apk", SecurityPolicy.sanitizeFilename("../../evil.apk"))
    }

    @Test
    fun sanitizeFilename_backslashTraversal_stripped() {
        assertEquals("evil.apk", SecurityPolicy.sanitizeFilename("..\\..\\evil.apk"))
    }

    @Test
    fun sanitizeFilename_urlEncodedTraversal_stripped() {
        assertEquals("evil.apk", SecurityPolicy.sanitizeFilename("%2e%2e%2f%2e%2e%2fevil.apk"))
    }

    @Test
    fun sanitizeFilename_nullBytes_stripped() {
        assertEquals("file.txt", SecurityPolicy.sanitizeFilename("file\u0000.txt"))
    }

    @Test
    fun sanitizeFilename_controlChars_stripped() {
        assertEquals("file.txt", SecurityPolicy.sanitizeFilename("file\u0001\u0002.txt"))
    }

    @Test
    fun sanitizeFilename_reservedWindowsName_prefixed() {
        // CON, PRN, AUX, NUL are reserved Windows device names
        assertEquals("_CON.pdf", SecurityPolicy.sanitizeFilename("CON.pdf"))
        assertEquals("_PRN.exe", SecurityPolicy.sanitizeFilename("PRN.exe"))
        assertEquals("_AUX.zip", SecurityPolicy.sanitizeFilename("AUX.zip"))
    }

    @Test
    fun sanitizeFilename_leadingDotsTrimmed() {
        assertEquals("file.txt", SecurityPolicy.sanitizeFilename("...file.txt"))
    }

    @Test
    fun sanitizeFilename_trailingDotsTrimmed() {
        assertEquals("file.txt", SecurityPolicy.sanitizeFilename("file.txt..."))
    }

    @Test
    fun sanitizeFilename_excessiveLength_truncated() {
        val longName = "a".repeat(500) + ".pdf"
        val result = SecurityPolicy.sanitizeFilename(longName)
        assertTrue("Sanitized filename should be <= 200 chars", result.length <= 200)
        assertTrue("Sanitized filename should preserve extension", result.endsWith(".pdf"))
    }

    @Test
    fun sanitizeFilename_emptyInput_returnsDefault() {
        assertEquals("download", SecurityPolicy.sanitizeFilename(""))
    }

    @Test
    fun sanitizeFilename_nullInput_returnsDefault() {
        assertEquals("download", SecurityPolicy.sanitizeFilename(null))
    }

    @Test
    fun sanitizeFilename_onlyUnsafeChars_returnsDefault() {
        assertEquals("download", SecurityPolicy.sanitizeFilename("../../../"))
    }

    // ── SecurityPolicy.isValidNavigationScheme ───────────────────────────────

    @Test
    fun isValidNavigationScheme_http_allowed() {
        assertTrue(SecurityPolicy.isValidNavigationScheme("http"))
    }

    @Test
    fun isValidNavigationScheme_https_allowed() {
        assertTrue(SecurityPolicy.isValidNavigationScheme("https"))
    }

    @Test
    fun isValidNavigationScheme_about_allowed() {
        assertTrue(SecurityPolicy.isValidNavigationScheme("about"))
    }

    @Test
    fun isValidNavigationScheme_javascript_rejected() {
        assertFalse(SecurityPolicy.isValidNavigationScheme("javascript"))
    }

    @Test
    fun isValidNavigationScheme_data_rejected() {
        assertFalse(SecurityPolicy.isValidNavigationScheme("data"))
    }

    @Test
    fun isValidNavigationScheme_file_allowed() {
        assertTrue(SecurityPolicy.isValidNavigationScheme("file"))
    }

    @Test
    fun isValidNavigationScheme_intent_rejected() {
        assertFalse(SecurityPolicy.isValidNavigationScheme("intent"))
    }

    @Test
    fun isValidNavigationScheme_blob_rejected() {
        assertFalse(SecurityPolicy.isValidNavigationScheme("blob"))
    }

    @Test
    fun isValidNavigationScheme_null_returnsTrue() {
        // null scheme means relative URL, which is allowed
        assertTrue(SecurityPolicy.isValidNavigationScheme(null))
    }

    // ── SecurityPolicy.validateIntentUri ──────────────────────────────────────

    @Test
    fun validateIntentUri_httpsGoogleCom_allowed() {
        assertTrue(SecurityPolicy.validateIntentUri("https://www.google.com"))
    }

    @Test
    fun validateIntentUri_javascriptBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri("javascript:alert('xss')"))
    }

    @Test
    fun validateIntentUri_dataUriBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun validateIntentUri_intentSchemeBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri("intent://evil.com#Intent;scheme=https;package=com.evil;end"))
    }

    @Test
    fun validateIntentUri_aboutBlank_allowed() {
        assertTrue(SecurityPolicy.validateIntentUri("about:blank"))
    }

    @Test
    fun validateIntentUri_mozExtension_allowed() {
        assertTrue(SecurityPolicy.validateIntentUri("moz-extension://1234-5678/page.html"))
    }

    @Test
    fun validateIntentUri_nullBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri(null))
    }

    @Test
    fun validateIntentUri_controlCharsBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri("https://evil.com\u0000.jpg"))
    }

    @Test
    fun validateIntentUri_emptyBlocked() {
        assertFalse(SecurityPolicy.validateIntentUri(""))
    }

    // ── SecurityPolicy.extractEffectiveHost ───────────────────────────────────

    @Test
    fun extractEffectiveHost_httpsUrl_returnsHost() {
        assertEquals("google.com", SecurityPolicy.extractEffectiveHost("https://google.com/path"))
    }

    @Test
    fun extractEffectiveHost_wwwPrefix_normalized() {
        assertEquals("www.google.com", SecurityPolicy.extractEffectiveHost("https://www.google.com"))
    }

    @Test
    fun extractEffectiveHost_userinfoStripped() {
        assertEquals("google.com", SecurityPolicy.extractEffectiveHost("https://user:pass@google.com"))
    }

    @Test
    fun extractEffectiveHost_trailingDot_removed() {
        assertEquals("google.com", SecurityPolicy.extractEffectiveHost("https://google.com."))
    }

    @Test
    fun extractEffectiveHost_null_returnsEmpty() {
        assertEquals("", SecurityPolicy.extractEffectiveHost(null))
    }

    @Test
    fun extractEffectiveHost_empty_returnsEmpty() {
        assertEquals("", SecurityPolicy.extractEffectiveHost(""))
    }

    // ── OriginVerifier.matchesAnyExact ────────────────────────────────────────

    @Test
    fun matchesAnyExact_accountsGoogleCom_matches() {
        assertTrue(OriginVerifier.matchesAnyExact("https://accounts.google.com", "accounts.google.com", "appleid.apple.com"))
    }

    @Test
    fun matchesAnyExact_noMatch_returnsFalse() {
        assertFalse(OriginVerifier.matchesAnyExact("https://evil.com", "accounts.google.com", "appleid.apple.com"))
    }

    // ── OriginVerifier.matchesAnySubdomain ──────────────────────────────────

    @Test
    fun matchesAnySubdomain_youtubeMatches() {
        assertTrue(OriginVerifier.matchesAnySubdomain("https://www.youtube.com/watch", "youtube.com", "google.com"))
    }

    @Test
    fun matchesAnySubdomain_noMatch_returnsFalse() {
        assertFalse(OriginVerifier.matchesAnySubdomain("https://evil.com", "youtube.com", "google.com"))
    }

    // ── Authentication Pipeline Tests (Issue #85) ──────────────────────────

    @Test
    fun exactOriginMatch_googleAuthOrigins() {
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.google.com/ServiceLogin?service=youtube", "accounts.google.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.google.com/v3/signin/identifier", "accounts.google.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.youtube.com/accounts/SetSID", "accounts.youtube.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://apis.google.com/js/api.js", "apis.google.com"))
    }

    @Test
    fun exactOriginMatch_oauthProviders() {
        assertTrue(OriginVerifier.isExactOriginMatch("https://appleid.apple.com/auth/authorize", "appleid.apple.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://login.microsoftonline.com/common/oauth2/v2.0/authorize", "login.microsoftonline.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://github.com/login/oauth/authorize", "github.com"))
    }

    @Test
    fun exactOriginMatch_subdomainSpoofingRejected() {
        assertFalse(OriginVerifier.isExactOriginMatch("https://accounts.google.com.attacker.com", "accounts.google.com"))
        assertFalse(OriginVerifier.isExactOriginMatch("https://attacker-accounts.google.com", "accounts.google.com"))
    }

    // ── Incognito Video Playback & Media Isolation (Issue #94) ─────────────

    @Test
    fun isSubdomainOf_videoStreamingDomains() {
        assertTrue(OriginVerifier.isSubdomainOf("https://www.youtube.com/watch?v=123", "youtube.com"))
        assertTrue(OriginVerifier.isSubdomainOf("https://m.youtube.com/watch?v=123", "youtube.com"))
        assertTrue(OriginVerifier.isSubdomainOf("https://youtu.be/123", "youtu.be"))
        assertTrue(OriginVerifier.isSubdomainOf("https://vimeo.com/123", "vimeo.com"))
        assertTrue(OriginVerifier.isSubdomainOf("https://player.vimeo.com/video/123", "vimeo.com"))
        assertTrue(OriginVerifier.isSubdomainOf("https://video.twimg.com/ext_tw_video/123", "twimg.com"))
        assertFalse(OriginVerifier.isSubdomainOf("https://notyoutube.com", "youtube.com"))
        assertFalse(OriginVerifier.isSubdomainOf("https://evil-youtube.com", "youtube.com"))
    }

    @Test
    fun isExactOriginMatch_mediaAndDrmOrigins() {
        assertTrue(OriginVerifier.isExactOriginMatch("https://accounts.youtube.com/accounts/SetSID", "accounts.youtube.com"))
        assertTrue(OriginVerifier.isExactOriginMatch("https://www.youtube.com/watch", "www.youtube.com"))
        assertFalse(OriginVerifier.isExactOriginMatch("https://youtube.com.attacker.com", "youtube.com"))
    }
}
