/*
 * Omni Browser - IntentUrlParser Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Tests for URL and query extraction from incoming intents (ACTION_SEND,
 * ACTION_VIEW, ACTION_WEB_SEARCH, ACTION_PROCESS_TEXT).
 */

package com.rebelroot.omni.browser

import org.junit.Test
import org.junit.Assert.*

class IntentUrlParserTest {

    @Test
    fun directHttpsUrl_isExtractedDirectly() {
        val result = IntentUrlParser.extractUrlOrQuery("https://github.com/REBEL-ROOT/omni-browser")
        assertEquals("https://github.com/REBEL-ROOT/omni-browser", result)
    }

    @Test
    fun directHttpUrl_isExtractedDirectly() {
        val result = IntentUrlParser.extractUrlOrQuery("http://example.com/page?query=true")
        assertEquals("http://example.com/page?query=true", result)
    }

    @Test
    fun urlEmbeddedInSentence_isExtracted() {
        val text = "Check out this repo https://github.com/REBEL-ROOT/omni-browser for the code"
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://github.com/REBEL-ROOT/omni-browser", result)
    }

    @Test
    fun urlWithTrailingPeriod_stripsPeriod() {
        val text = "Visit https://example.com."
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://example.com", result)
    }

    @Test
    fun urlWithTrailingComma_stripsComma() {
        val text = "Go to https://example.com, and read."
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://example.com", result)
    }

    @Test
    fun urlWithTrailingExclamation_stripsExclamation() {
        val text = "Amazing website https://example.com/cool!"
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://example.com/cool", result)
    }

    @Test
    fun urlWithBalancedParentheses_preservesParentheses() {
        val url = "https://en.wikipedia.org/wiki/Rust_(programming_language)"
        val result = IntentUrlParser.extractUrlOrQuery(url)
        assertEquals("https://en.wikipedia.org/wiki/Rust_(programming_language)", result)
    }

    @Test
    fun urlEnclosedInParentheses_stripsWrappingParenthesis() {
        val text = "See (https://example.com/path)"
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://example.com/path", result)
    }

    @Test
    fun urlEnclosedInQuotes_stripsQuotes() {
        val text = "\"https://example.com\""
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://example.com", result)
    }

    @Test
    fun wwwDomainWithoutScheme_prependsHttps() {
        val text = "www.google.com"
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://www.google.com", result)
    }

    @Test
    fun bareDomain_prependsHttps() {
        val text = "github.com/REBEL-ROOT"
        val result = IntentUrlParser.extractUrlOrQuery(text)
        assertEquals("https://github.com/REBEL-ROOT", result)
    }

    @Test
    fun plainTextQuery_returnsQueryWhenFallbackAllowed() {
        val query = "how to make pasta"
        val result = IntentUrlParser.extractUrlOrQuery(query, fallbackToQuery = true)
        assertEquals("how to make pasta", result)
    }

    @Test
    fun plainTextQuery_returnsNullWhenFallbackDisallowed() {
        val query = "how to make pasta"
        val result = IntentUrlParser.extractUrlOrQuery(query, fallbackToQuery = false)
        assertNull(result)
    }

    @Test
    fun nullOrBlankInput_returnsNull() {
        assertNull(IntentUrlParser.extractUrlOrQuery(null))
        assertNull(IntentUrlParser.extractUrlOrQuery(""))
        assertNull(IntentUrlParser.extractUrlOrQuery("   \n\t  "))
    }

    @Test
    fun controlCharacters_areRejected() {
        assertNull(IntentUrlParser.extractUrlOrQuery("https://example.com\u0000"))
        assertNull(IntentUrlParser.extractUrlOrQuery("https://example.com\u0007bad"))
    }

    @Test
    fun dangerousSchemes_areBlocked() {
        assertNull("javascript: must be blocked", IntentUrlParser.extractUrlOrQuery("javascript:alert(1)"))
        assertNull("data: must be blocked", IntentUrlParser.extractUrlOrQuery("data:text/html,<h1>evil</h1>"))
        assertNull("file: must be blocked", IntentUrlParser.extractUrlOrQuery("file:///etc/passwd"))
        assertNull("content: must be blocked", IntentUrlParser.extractUrlOrQuery("content://sensitive/path"))
        assertNull("intent: must be blocked", IntentUrlParser.extractUrlOrQuery("intent://scan/#Intent;end"))
        assertNull("moz-extension: must be blocked", IntentUrlParser.extractUrlOrQuery("moz-extension://internal-uuid/page.html"))
        assertNull("blob: must be blocked", IntentUrlParser.extractUrlOrQuery("blob:https://example.com/uuid"))
    }

    @Test
    fun safeInternalAboutScheme_isAllowed() {
        val result = IntentUrlParser.extractUrlOrQuery("about:blank")
        assertEquals("about:blank", result)
    }

    @Test
    fun cleanTrailingPunctuation_handlesMultiplePunctuationMarks() {
        val cleaned = IntentUrlParser.cleanTrailingPunctuation("https://example.com/test!?...")
        assertEquals("https://example.com/test", cleaned)
    }
}
