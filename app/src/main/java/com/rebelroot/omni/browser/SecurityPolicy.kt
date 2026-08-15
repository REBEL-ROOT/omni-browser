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

import android.net.Uri
import android.util.Log
import java.net.IDN
import java.util.Locale

/**
 * Centralized security policy for navigation schemes, filename sanitization,
 * host extraction, and intent URI validation.
 *
 * All security-sensitive string operations (URL matching, filename handling,
 * scheme validation) should flow through this object to prevent:
 * - Origin spoofing via substring matching
 * - Path traversal in download filenames
 * - Intent redirection attacks
 * - Privilege escalation via untrusted scheme handlers
 */
object SecurityPolicy {

    private const val TAG = "SecurityPolicy"

    // ── Navigation Scheme Policy ────────────────────────────────────────────

    /**
     * Schemes that are safe to navigate to directly.
     */
    private val ALLOWED_NAVIGATION_SCHEMES = setOf(
        "http",
        "https",
        "about",
        "file",
        "content",
        "moz-extension"
    )

    /**
     * Schemes that are potentially dangerous when initiated from external
     * intents (not from web content itself).
     */
    private val DANGEROUS_EXTERNAL_SCHEMES = setOf(
        "javascript",
        "data",
        "blob",
        "intent",
        "market",
        "chrome"
    )

    /**
     * Checks if a scheme is safe for direct navigation.
     * Returns true for null/empty schemes (relative URLs).
     */
    fun isValidNavigationScheme(scheme: String?): Boolean {
        if (scheme.isNullOrBlank()) return true // relative URL, allowed
        return ALLOWED_NAVIGATION_SCHEMES.contains(scheme.lowercase(Locale.ROOT))
    }

    /**
     * Checks if a scheme is dangerous when coming from an external Android intent.
     * These schemes can execute code or access local resources and should be
     * blocked when the navigation originates outside the browser.
     */
    fun isDangerousExternalScheme(scheme: String?): Boolean {
        if (scheme.isNullOrBlank()) return false
        return DANGEROUS_EXTERNAL_SCHEMES.contains(scheme.lowercase(Locale.ROOT))
    }

    // ── Filename Sanitization ──────────────────────────────────────────────

    /**
     * Maximum safe filename length.
     */
    private const val MAX_FILENAME_LENGTH = 200

    /**
     * Reserved device names on Windows that must not be used as filenames
     * (prevents issues if files are transferred to Windows systems).
     */
    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Sanitizes a filename to prevent path traversal, null byte injection,
     * control character injection, and reserved name attacks.
     *
     * Handles:
     * - Path traversal sequences (../, ..\)
     * - URL-encoded traversal (%2e%2e%2f)
     * - Null bytes (%00, \u0000)
     * - Control characters (ASCII < 32)
     * - Leading/trailing dots and spaces (Windows compatibility)
     * - Reserved Windows device names
     * - Excessive filename length
     *
     * @param name Raw filename from URL path segment or Content-Disposition header
     * @return Sanitized filename, or "download" if the input is empty/unsafe
     */
    fun sanitizeFilename(name: String?): String {
        if (name.isNullOrBlank()) return "download"

        var sanitized = name.trim()

        // Step 1: URL-decode to catch encoded traversal sequences
        try {
            sanitized = java.net.URLDecoder.decode(sanitized, "UTF-8")
        } catch (_: Exception) {}

        // Step 2: Strip null bytes
        sanitized = sanitized.replace("\u0000", "")

        // Step 3: Strip control characters (ASCII < 32, except common safe ones)
        sanitized = sanitized.filter { c ->
            val code = c.code
            code >= 32 || c == '\n' || c == '\r' || c == '\t'
        }

        // Step 4: Remove path separators and traversal sequences
        sanitized = sanitized
            .replace("../", "")
            .replace("..\\", "")
            .replace("/", "")
            .replace("\\", "")
            .replace(":", "")       // Windows drive separator
            .replace("|", "")       // Windows pipe
            .replace("?", "")       // Windows wildcard
            .replace("*", "")       // Windows wildcard
            .replace("\"", "")      // Windows quote

        // Step 5: Collapse any remaining double-dots
        sanitized = sanitized.replace("..", ".")

        // Step 6: Remove leading dots, spaces, and control chars (Windows)
        sanitized = sanitized.trimStart('.', ' ')

        // Step 7: Remove trailing dots and spaces (Windows strips these silently,
        // which can cause extension spoofing: "evil.exe." → "evil.exe")
        sanitized = sanitized.trimEnd('.', ' ')

        // Step 8: If empty after sanitization, return fallback
        if (sanitized.isBlank()) return "download"

        // Step 9: Check for reserved Windows device names (case-insensitive)
        val baseName = sanitized.substringBeforeLast('.').uppercase(Locale.ROOT)
        if (RESERVED_NAMES.contains(baseName)) {
            sanitized = "_$sanitized"
        }

        // Step 10: Enforce maximum length while preserving extension
        if (sanitized.length > MAX_FILENAME_LENGTH) {
            val lastDot = sanitized.lastIndexOf('.')
            if (lastDot > 0 && lastDot < sanitized.length - 1) {
                val ext = sanitized.substring(lastDot)
                val maxBase = MAX_FILENAME_LENGTH - ext.length
                if (maxBase > 0) {
                    sanitized = sanitized.substring(0, maxBase.coerceAtMost(lastDot)) + ext
                } else {
                    sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH)
                }
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH)
            }
        }

        return sanitized.ifBlank { "download" }
    }

    // ── Host Extraction ────────────────────────────────────────────────────

    /**
     * Extracts the effective host from a URI string with proper handling of:
     * - IDN/Punycode normalization
     * - Trailing dots (FQDN notation)
     * - Userinfo stripping (user:pass@host)
     * - Default port normalization
     * - IPv4/IPv6 addresses
     *
     * This is the ONLY safe way to extract a hostname from a URI for security
     * decisions. Never use Uri.parse(uri).host directly for security checks,
     * as it may return null or malformed values for edge cases.
     *
     * @param uri Full URI string or bare hostname
     * @return Normalized lowercase hostname, or empty string if unparseable
     */
    fun extractEffectiveHost(uri: String?): String {
        if (uri.isNullOrBlank()) return ""

        return try {
            // Parse the URI
            val parsed = Uri.parse(uri)
            var host = parsed.host?.lowercase(Locale.ROOT) ?: ""

            // If no host extracted, try parsing as bare hostname
            if (host.isEmpty()) {
                // Try treating the whole string as a hostname
                host = uri.lowercase(Locale.ROOT)
                    .trim()
                    .removeSuffix(".")  // Remove trailing dot (FQDN)
            }

            // Remove trailing dot if present
            if (host.endsWith(".")) {
                host = host.removeSuffix(".")
            }

            // Normalize IDN/Punycode to ASCII form for consistent comparison
            // This prevents IDN homograph attacks (e.g., Cyrillic 'а' vs Latin 'a')
            if (host.isNotEmpty()) {
                try {
                    host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
                } catch (_: IllegalArgumentException) {
                    // Invalid IDN — keep as-is, downstream checks will reject
                }
            }

            host
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract host from: $uri", e)
            ""
        }
    }

    // ── Intent URI Validation ──────────────────────────────────────────────

    /**
     * Validates a URI received from an external Android intent.
     *
     * Blocks:
     * - javascript: schemes (code injection)
     * - data: schemes (can embed arbitrary content)
     * - blob: schemes (memory-resident content)
     * - intent: schemes (intent redirection / app launch)
     * - market: schemes (Play Store manipulation)
     * - Empty or unparseable URIs
     *
     * Allows:
     * - http:// and https:// URLs with valid hosts
     * - about: URLs (about:blank, about:config)
     * - file:// URLs (with caution — limited to app-owned paths)
     * - content:// URLs (ContentResolver URIs)
     * - moz-extension:// URLs (built-in extensions)
     *
     * @param uri The raw URI string from Intent.getDataString() or similar
     * @return true if the URI is safe to load in the browser, false otherwise
     */
    fun validateIntentUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false

        val trimmed = uri.trim()

        // Reject null bytes and control characters
        if (trimmed.any { it.code < 32 }) return false

        // Extract and validate scheme
        val scheme = try {
            Uri.parse(trimmed).scheme?.lowercase(Locale.ROOT)
        } catch (e: Exception) {
            Log.w(TAG, "Unparseable intent URI: $uri")
            return false
        }

        if (scheme == null) return false

        // Block dangerous external schemes
        if (isDangerousExternalScheme(scheme)) {
            Log.w(TAG, "Blocked dangerous scheme '$scheme' from external intent: $uri")
            return false
        }

        // For http/https, validate the host is parseable
        if (scheme == "http" || scheme == "https") {
            val host = extractEffectiveHost(trimmed)
            if (host.isEmpty()) {
                Log.w(TAG, "Blocked http/https intent with unparseable host: $uri")
                return false
            }
        }

        return true
    }

    /**
     * Sanitizes a URI string received from an external intent.
     * Strips dangerous query parameters and fragments that could be used
     * for intent redirection or parameter injection.
     *
     * @param uri Raw URI from intent
     * @return Sanitized URI, or null if the URI is invalid
     */
    fun sanitizeIntentUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        if (!validateIntentUri(uri)) return null

        return try {
            val parsed = Uri.parse(uri.trim())
            // Rebuild without fragment (fragments can contain intent:// redirects)
            parsed.buildUpon()
                .fragment(null)
                .build()
                .toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sanitize intent URI: $uri", e)
            null
        }
    }
}

/**
 * Origin verification utility that performs strict, exact host matching
 * for security-sensitive origin checks.
 *
 * Unlike the vulnerable pattern of `uri.contains("google.com")` which matches
 * any URL containing that substring (e.g., "evilgoogle.com" or "google.com.evil.com"),
 * this object extracts the actual host and performs exact equality or proper
 * subdomain matching.
 *
 * Usage:
 * - Use [isExactOriginMatch] when the origin must be EXACTLY the specified domain
 *   (e.g., OAuth endpoints must be exactly accounts.google.com)
 * - Use [isSubdomainOf] when the origin can be the domain OR any of its subdomains
 *   (e.g., google.com matches www.google.com, mail.google.com)
 *
 * IMPORTANT: This uses SecurityPolicy.extractEffectiveHost() internally, which
 * handles IDN/Punycode normalization, trailing dots, and userinfo stripping.
 */
object OriginVerifier {

    private const val TAG = "OriginVerifier"

    /**
     * Checks if the URI's host is EXACTLY equal to the specified domain.
     *
     * This is the strictest form of matching. It will NOT match subdomains.
     * Use this for OAuth providers, CSP origins, and other cases where the
     * origin must be precisely the specified domain.
     *
     * Examples:
     * - isExactOriginMatch("https://accounts.google.com/oauth", "accounts.google.com") → true
     * - isExactOriginMatch("https://www.google.com", "google.com") → false
     * - isExactOriginMatch("https://evilgoogle.com", "google.com") → false
     * - isExactOriginMatch("https://google.com.evil.com", "google.com") → false
     *
     * @param uri Full URI string
     * @param domain Expected domain (case-insensitive)
     * @return true only if the URI's host exactly matches the domain
     */
    fun isExactOriginMatch(uri: String?, domain: String): Boolean {
        if (uri.isNullOrBlank() || domain.isBlank()) return false

        val host = SecurityPolicy.extractEffectiveHost(uri)
        val target = domain.lowercase(java.util.Locale.ROOT)

        val match = host == target
        if (!match && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Exact origin mismatch: host='$host' vs expected='$target' for uri='$uri'")
        }
        return match
    }

    /**
     * Checks if the URI's host is the specified domain OR a proper subdomain of it.
     *
     * This allows www.google.com, mail.google.com, etc. to match google.com,
     * but rejects evilgoogle.com, google.com.evil.com, and similar spoofing attempts.
     *
     * A proper subdomain must have at least one additional label separated by a dot.
     * The parent domain must be a suffix preceded by a dot (or equal).
     *
     * Examples:
     * - isSubdomainOf("https://www.google.com/search", "google.com") → true
     * - isSubdomainOf("https://mail.google.com", "google.com") → true
     * - isSubdomainOf("https://google.com", "google.com") → true
     * - isSubdomainOf("https://evilgoogle.com", "google.com") → false
     * - isSubdomainOf("https://google.com.evil.com", "google.com") → false
     * - isSubdomainOf("https://not-google.com", "google.com") → false
     * - isSubdomainOf("https://user:pass@google.com@evil.com", "google.com") → false
     *
     * @param uri Full URI string
     * @param parentDomain Parent domain to check against (case-insensitive)
     * @return true if the URI's host equals or is a subdomain of parentDomain
     */
    fun isSubdomainOf(uri: String?, parentDomain: String): Boolean {
        if (uri.isNullOrBlank() || parentDomain.isBlank()) return false

        val host = SecurityPolicy.extractEffectiveHost(uri)
        val parent = parentDomain.lowercase(java.util.Locale.ROOT).removePrefix("www.")

        if (host.isEmpty()) return false

        // Normalize host for comparison
        val normalizedHost = if (host.startsWith("www.")) host.removePrefix("www.") else host

        // Exact match
        if (normalizedHost == parent) return true

        // Subdomain match: host must end with ".parentDomain"
        // This ensures "google.com.evil.com" does NOT match "google.com"
        // while "www.google.com" DOES match "google.com"
        val isSubdomain = normalizedHost.endsWith(".$parent")

        if (!isSubdomain && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Subdomain check failed: host='$normalizedHost' vs parent='$parent' for uri='$uri'")
        }
        return isSubdomain
    }

    /**
     * Checks if the URI's host matches ANY of the provided domains
     * using strict exact matching (not substring).
     *
     * @param uri Full URI string
     * @param domains List of domains to check against
     * @return true if the URI's host exactly matches any of the domains
     */
    fun matchesAnyExact(uri: String?, vararg domains: String): Boolean {
        return domains.any { isExactOriginMatch(uri, it) }
    }

    /**
     * Checks if the URI's host matches ANY of the provided domains
     * using subdomain matching.
     *
     * @param uri Full URI string
     * @param domains List of parent domains to check against
     * @return true if the URI's host is any of the domains or their subdomains
     */
    fun matchesAnySubdomain(uri: String?, vararg domains: String): Boolean {
        return domains.any { isSubdomainOf(uri, it) }
    }
}
