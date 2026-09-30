/*
 * Omni Browser - Password CSV tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.tools.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvTest {

    @Test
    fun `parse skips the header, derives domains and reads four columns`() {
        val csv = """
            name,url,username,password
            Example,https://www.example.com/login,alice@example.com,s3cret
            GitHub,https://github.com,octocat,hunter2
        """.trimIndent()

        val result = PasswordCsv.parse(csv)

        assertEquals(2, result.entries.size)
        assertEquals(0, result.duplicates)
        assertEquals("example.com", result.entries[0].domain)
        assertEquals("alice@example.com", result.entries[0].username)
        assertEquals("s3cret", result.entries[0].password)
        assertEquals("Example", result.entries[0].label)
        assertEquals("github.com", result.entries[1].domain)
    }

    @Test
    fun `parse counts duplicates against existing keys and skips blank rows`() {
        val csv = "name,url,username,password\n" +
            "Example,https://example.com,bob,pw\n" +
            "\n" +
            "NoUser,https://nouser.com,,pw\n"

        val existing = setOf("example.com" to "bob")
        val result = PasswordCsv.parse(csv, existing)

        assertEquals(0, result.entries.size)
        assertEquals(1, result.duplicates)
        assertEquals(1, result.skipped) // empty username
    }

    @Test
    fun `parseLine handles quoted fields containing commas and escaped quotes`() {
        val cols = PasswordCsv.parseLine("\"Acme, Inc.\",https://acme.com,user,\"pa\"\"ss\"")
        assertEquals(listOf("Acme, Inc.", "https://acme.com", "user", "pa\"ss"), cols)
    }

    @Test
    fun `build output round-trips back through parse`() {
        val entries = listOf(
            PasswordEntry(domain = "example.com", username = "a@b.c", password = "p,w\"d", label = "Example, Inc.")
        )

        val csv = PasswordCsv.build(entries)
        assertTrue(csv.startsWith(PasswordCsv.HEADER))

        val parsed = PasswordCsv.parse(csv)
        assertEquals(1, parsed.entries.size)
        assertEquals("example.com", parsed.entries[0].domain)
        assertEquals("a@b.c", parsed.entries[0].username)
        assertEquals("p,w\"d", parsed.entries[0].password)
        assertEquals("Example, Inc.", parsed.entries[0].label)
    }
}