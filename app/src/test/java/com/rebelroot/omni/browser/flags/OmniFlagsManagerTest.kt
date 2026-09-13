/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser.flags

import org.junit.Assert.*
import org.junit.Test

class OmniFlagsManagerTest {

    @Test
    fun testAllFlagsRegistryIntegrity() {
        val flags = OmniFlagsRegistry.ALL_FLAGS
        assertTrue("Flags registry should contain flags", flags.size >= 20)

        val ids = mutableSetOf<String>()
        for (flag in flags) {
            assertTrue("Flag ID must not be blank", flag.id.isNotBlank())
            assertTrue("Duplicate flag ID found: ${flag.id}", ids.add(flag.id))
            assertTrue("Flag title must not be blank for ${flag.id}", flag.title.isNotBlank())
            assertTrue("Flag description must not be blank for ${flag.id}", flag.description.isNotBlank())
            assertTrue("Flag tag must start with '#' for ${flag.id}", flag.tag.startsWith("#"))
            assertTrue("Flag enginePrefs must not be empty for ${flag.id}", flag.enginePrefs.isNotEmpty())

            // Verify all pref keys are valid dotted strings
            for ((key, value) in flag.enginePrefs) {
                assertTrue("Pref key must contain dot: $key", key.contains("."))
                assertNotNull("Pref value must not be null for $key", value)
            }
        }
    }

    @Test
    fun testFlagOriginAndCategories() {
        val chromeFlags = OmniFlagsRegistry.ALL_FLAGS.filter { it.origin == FlagOrigin.CHROME }
        val braveFlags = OmniFlagsRegistry.ALL_FLAGS.filter { it.origin == FlagOrigin.BRAVE }

        assertTrue("Should have Chrome-inspired flags", chromeFlags.isNotEmpty())
        assertTrue("Should have Brave-inspired flags", braveFlags.isNotEmpty())

        val parallelFlag = OmniFlagsRegistry.getFlagById("chrome_parallel_downloading")
        assertNotNull(parallelFlag)
        assertEquals("#enable-parallel-downloading", parallelFlag?.tag)
        assertEquals(FlagCategory.PERFORMANCE, parallelFlag?.category)
        assertEquals(10, parallelFlag?.enginePrefs?.get("network.http.max-persistent-connections-per-server"))

        val gpcFlag = OmniFlagsRegistry.getFlagById("brave_shields_gpc")
        assertNotNull(gpcFlag)
        assertEquals(FlagCategory.BRAVE_SHIELDS, gpcFlag?.category)
        assertEquals(true, gpcFlag?.enginePrefs?.get("privacy.globalprivacycontrol.enabled"))
    }

    @Test
    fun testFlagsStateEncodingAndDecoding() {
        val defaults = OmniFlagsRegistry.getDefaultStateMap()
        assertNotNull(defaults)
        assertTrue(defaults.containsKey("chrome_parallel_downloading"))

        // Modify one flag
        val modified = defaults.toMutableMap()
        modified["chrome_parallel_downloading"] = false
        modified["chrome_webgpu"] = true

        val encoded = OmniFlagsManager.encodeFlagsState(modified)
        assertTrue(encoded.contains("\"chrome_parallel_downloading\":false"))
        assertTrue(encoded.contains("\"chrome_webgpu\":true"))

        val decoded = OmniFlagsManager.decodeFlagsState(encoded)
        assertEquals(false, decoded["chrome_parallel_downloading"])
        assertEquals(true, decoded["chrome_webgpu"])

        // Test fallback on empty/null string
        val fallback = OmniFlagsManager.decodeFlagsState(null)
        assertEquals(defaults["chrome_parallel_downloading"], fallback["chrome_parallel_downloading"])
    }

    @Test
    fun testCustomPrefsEncodingAndDecoding() {
        val customPrefs = listOf(
            CustomPref("image.animation_mode", PrefType.STRING, "none"),
            CustomPref("browser.cache.memory.enable", PrefType.BOOLEAN, "true"),
            CustomPref("network.http.max-connections", PrefType.INT, "900")
        )

        val encoded = OmniFlagsManager.encodeCustomPrefs(customPrefs)
        val decoded = OmniFlagsManager.decodeCustomPrefs(encoded)

        assertEquals(3, decoded.size)
        assertEquals("image.animation_mode", decoded[0].key)
        assertEquals(PrefType.STRING, decoded[0].type)
        assertEquals("none", decoded[0].value)

        assertEquals("browser.cache.memory.enable", decoded[1].key)
        assertEquals(PrefType.BOOLEAN, decoded[1].type)
        assertEquals("true", decoded[1].value)

        assertEquals("network.http.max-connections", decoded[2].key)
        assertEquals(PrefType.INT, decoded[2].type)
        assertEquals("900", decoded[2].value)

        // Test empty
        val emptyDecoded = OmniFlagsManager.decodeCustomPrefs("")
        assertTrue(emptyDecoded.isEmpty())
    }

    @Test
    fun testGetComputedPrefsAndYamlFormatting() {
        val stateMap = mapOf(
            "chrome_parallel_downloading" to true,
            "chrome_webgpu" to false
        )
        val custom = listOf(
            CustomPref("custom.test.enabled", PrefType.BOOLEAN, "true"),
            CustomPref("custom.test.count", PrefType.INT, "42"),
            CustomPref("custom.test.name", PrefType.STRING, "OmniTest")
        )

        val computed = OmniFlagsManager.getComputedPrefs(stateMap, custom)
        assertEquals(10, computed["network.http.max-persistent-connections-per-server"])
        assertNull(computed["gfx.webgpu.force-enabled"]) // WebGPU was disabled
        assertEquals(true, computed["custom.test.enabled"])
        assertEquals(42, computed["custom.test.count"])
        assertEquals("OmniTest", computed["custom.test.name"])

        val yaml = OmniFlagsManager.formatPrefsToYaml(computed)
        assertTrue(yaml.contains("  network.http.max-persistent-connections-per-server: 10\n"))
        assertTrue(yaml.contains("  custom.test.enabled: true\n"))
        assertTrue(yaml.contains("  custom.test.count: 42\n"))
        assertTrue(yaml.contains("  custom.test.name: \"OmniTest\"\n"))
    }

    @Test
    fun testUrlAliasesForFlags() {
        val testUrls = listOf(
            "chrome://flags",
            "chrome:flags",
            "brave://flags",
            "brave:flags",
            "about:flags",
            "about:config",
            "omni:config",
            "omni://config",
            "omni:flags",
            "omni://flags",
            "  CHROME://FLAGS  "
        )

        val validSet = setOf(
            "omni:config", "omni://config",
            "about:config", "about:flags",
            "chrome://flags", "chrome:flags",
            "brave://flags", "brave:flags",
            "omni:flags", "omni://flags"
        )

        for (url in testUrls) {
            val clean = url.trim().lowercase()
            assertTrue("Expected $url to be recognized as config/flags URL", clean in validSet)
        }

        assertFalse("https://google.com should not be a config URL", "https://google.com" in validSet)
        assertFalse("chrome://settings should not be in flags set", "chrome://settings" in validSet)
    }
}
