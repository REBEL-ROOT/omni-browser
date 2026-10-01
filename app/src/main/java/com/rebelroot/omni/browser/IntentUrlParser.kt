/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.browser

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Locale

/**
 * Robust and security-hardened parser for incoming Android intents (ACTION_SEND,
 * ACTION_VIEW, ACTION_WEB_SEARCH, ACTION_PROCESS_TEXT).
 *
 * Handles:
 * - Direct HTTP/HTTPS URLs
 * - URLs embedded inside conversational or descriptive text (e.g. from share sheets)
 * - Trailing punctuation cleaning (e.g. "https://example.com/.")
 * - Balanced parenthesis preservation (e.g. "https://en.wikipedia.org/wiki/Rust_(programming_language)")
 * - www.* domain detection
 * - Bare domain detection (e.g. "github.com")
 * - Web search query fallback for shared plain text
 * - Security blocking of dangerous schemes (javascript:, data:, file:, content:, intent:, etc.)
 * - Rejection of control characters and null bytes
 */
object IntentUrlParser {

    private const val TAG = "IntentUrlParser"

    private val HTTP_URL_REGEX = Regex(
        """https?://[^\s<>"{}|\\^`]+""",
        RegexOption.IGNORE_CASE
    )

    private val WWW_URL_REGEX = Regex(
        """\bwww\.[^\s<>"{}|\\^`]+""",
        RegexOption.IGNORE_CASE
    )

    private val DISALLOWED_EXTERNAL_SCHEMES = setOf(
        "javascript",
        "data",
        "blob",
        "intent",
        "market",
        "chrome",
        "file",
        "content",
        "moz-extension",
        "jar"
    )

    /**
     * Extracts a safe URL or search query from an incoming Android Intent.
     *
     * @param intent The incoming intent from onCreate or onNewIntent.
     * @param context Optional context for converting ClipData to text.
     * @return Sanitized URL or search query, or null if input is empty or invalid.
     */
    fun parseIntent(intent: Intent?, context: Context? = null): String? {
        if (intent == null) return null
        val action = intent.action

        val rawText: String? = when (action) {
            Intent.ACTION_SEND -> {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.let { item ->
                        context?.let { ctx -> item.coerceToText(ctx)?.toString() } ?: item.text?.toString()
                    }
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.dataString
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            }
            Intent.ACTION_WEB_SEARCH -> {
                intent.getStringExtra(SearchManager.QUERY)
                    ?: intent.getStringExtra("query")
                    ?: intent.dataString
            }
            else -> {
                intent.dataString
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        }

        val fallbackToQuery = (action == Intent.ACTION_SEND ||
                action == Intent.ACTION_WEB_SEARCH ||
                action == Intent.ACTION_PROCESS_TEXT)

        return extractUrlOrQuery(rawText, fallbackToQuery)
    }

    /**
     * Extracts an HTTP/HTTPS URL, a www/domain link, or a sanitized search query
     * from arbitrary input text. Returns null if the text is empty, contains control
     * characters, or targets a dangerous scheme.
     *
     * @param rawText Text received from an external share sheet or intent.
     * @param fallbackToQuery If true, non-URL text will be preserved as a search query.
     * @return Sanitized URL or search query, or null.
     */
    fun extractUrlOrQuery(rawText: String?, fallbackToQuery: Boolean = true): String? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()

        // Reject null bytes and control characters (security hardening)
        if (trimmed.any { it.code < 32 }) {
            Log.w(TAG, "🛡️ Blocked intent input containing control characters")
            return null
        }

        // 1. Check for disallowed schemes at the start of input
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx > 0) {
            val potentialScheme = trimmed.substring(0, colonIdx).lowercase(Locale.ROOT).trim()
            if (isDisallowedScheme(potentialScheme)) {
                Log.w(TAG, "🛡️ Blocked disallowed scheme '$potentialScheme' in intent text: $trimmed")
                return null
            }
        }

        // 2. Search for explicit http:// or https:// URL within the text
        val httpMatch = HTTP_URL_REGEX.find(trimmed)
        if (httpMatch != null) {
            val candidate = cleanTrailingPunctuation(httpMatch.value)
            if (SecurityPolicy.validateIntentUri(candidate)) {
                return candidate
            } else {
                Log.w(TAG, "🛡️ Security policy rejected candidate HTTP URL: $candidate")
                return null
            }
        }

        // 3. Search for www. domain within the text
        val wwwMatch = WWW_URL_REGEX.find(trimmed)
        if (wwwMatch != null) {
            val candidate = "https://" + cleanTrailingPunctuation(wwwMatch.value)
            if (SecurityPolicy.validateIntentUri(candidate)) {
                return candidate
            } else {
                Log.w(TAG, "🛡️ Security policy rejected candidate www URL: $candidate")
                return null
            }
        }

        // 4. Safe direct internal schemes (e.g. about:blank, about:home)
        if (colonIdx > 0) {
            val scheme = trimmed.substring(0, colonIdx).lowercase(Locale.ROOT).trim()
            if (scheme == "about") {
                return if (SecurityPolicy.validateIntentUri(trimmed)) trimmed else null
            }
        }

        // 5. Bare domain without scheme (e.g. "example.com" or "example.com/path")
        if (trimmed.contains('.') && !trimmed.contains(' ') && !trimmed.contains(':')) {
            val candidate = "https://$trimmed"
            if (SecurityPolicy.validateIntentUri(candidate)) {
                return candidate
            }
        }

        // 6. Plain text search query fallback
        if (fallbackToQuery) {
            return trimmed
        }

        return null
    }

    /**
     * Cleans trailing punctuation that may be attached to URLs when shared
     * from conversational or formatted text (e.g., "Check out https://example.com!").
     * Preserves closing parentheses/brackets if the URL itself opened them
     * (e.g., "https://en.wikipedia.org/wiki/Rust_(programming_language)").
     */
    fun cleanTrailingPunctuation(rawUrl: String): String {
        var url = rawUrl
        while (url.isNotEmpty()) {
            val last = url.last()
            when {
                last == '.' || last == ',' || last == ';' || last == '!' || last == '?' || last == '\'' || last == '"' -> {
                    url = url.dropLast(1)
                }
                last == ')' && url.count { it == ')' } > url.count { it == '(' } -> {
                    url = url.dropLast(1)
                }
                last == ']' && url.count { it == ']' } > url.count { it == '[' } -> {
                    url = url.dropLast(1)
                }
                else -> break
            }
        }
        return url
    }

    private fun isDisallowedScheme(scheme: String): Boolean {
        if (SecurityPolicy.isDangerousExternalScheme(scheme)) return true
        return DISALLOWED_EXTERNAL_SCHEMES.contains(scheme.lowercase(Locale.ROOT))
    }
}
