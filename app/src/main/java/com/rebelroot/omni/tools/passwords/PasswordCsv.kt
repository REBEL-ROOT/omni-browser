/*
 * Omni Browser - Password CSV import/export
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Pure (Android-free) parsing and building of the "name,url,username,password"
 * CSV format used by Chrome, Edge and Firefox password exports. Shared by the
 * Password Manager screen and the Omni Sync LAN endpoint so both use exactly one
 * implementation.
 */

package com.rebelroot.omni.tools.passwords

import java.util.UUID

/** Outcome of parsing a passwords CSV. */
data class PasswordCsvParseResult(
    val entries: List<PasswordEntry>,
    val duplicates: Int,
    val skipped: Int
) {
    val total: Int get() = entries.size + duplicates
}

object PasswordCsv {
    const val HEADER: String = "name,url,username,password"
    private val HEADER_USERNAME_COLUMNS = setOf("username", "user name", "login")

    /**
     * Parses [csvText]. Rows whose (domain, username) already exist in
     * [existingKeys] are reported as duplicates and dropped.
     */
    fun parse(
        csvText: String,
        existingKeys: Set<Pair<String, String>> = emptySet()
    ): PasswordCsvParseResult {
        val entries = mutableListOf<PasswordEntry>()
        var duplicates = 0
        var skipped = 0

        csvText.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEachIndexed

            val cols = parseLine(line)
            if (cols.size < 4) {
                skipped++
                return@forEachIndexed
            }

            // Header row (Chrome/Edge/Firefox all use name,url,username,password).
            if (index == 0 && cols[2].trim().lowercase() in HEADER_USERNAME_COLUMNS) {
                return@forEachIndexed
            }

            val name = cols.getOrElse(0) { "" }.trim()
            val url = cols.getOrElse(1) { "" }.trim()
            val username = cols.getOrElse(2) { "" }.trim()
            val password = cols.getOrElse(3) { "" }.trim()

            if (username.isBlank() || password.isBlank()) {
                skipped++
                return@forEachIndexed
            }

            val domain = deriveDomain(url, name)
            if ((domain.lowercase() to username.lowercase()) in existingKeys) {
                duplicates++
                return@forEachIndexed
            }

            val now = System.currentTimeMillis()
            entries.add(
                PasswordEntry(
                    id = UUID.randomUUID().toString(),
                    label = name,
                    domain = domain,
                    username = username,
                    password = password,
                    notes = "",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        return PasswordCsvParseResult(entries, duplicates, skipped)
    }

    /** Builds CSV text (including the header row) for [entries]. */
    fun build(entries: List<PasswordEntry>): String = buildString {
        append(HEADER).append('\n')
        for (entry in entries) {
            append(escape(entry.label.ifBlank { entry.domain })).append(',')
            append(escape(entry.domain)).append(',')
            append(escape(entry.username)).append(',')
            append(escape(entry.password)).append('\n')
        }
    }

    /** Minimal RFC 4180 line parser that handles quoted fields containing commas. */
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++ // escaped quote
                }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> {
                    result.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun deriveDomain(url: String, fallback: String): String =
        runCatching {
            val host = java.net.URI(url).host ?: url
            host.removePrefix("www.")
        }.getOrElse {
            url.removePrefix("https://").removePrefix("http://").substringBefore("/")
        }.ifBlank { fallback }
}