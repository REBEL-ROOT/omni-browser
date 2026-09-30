/*
 * Omni Browser - Backup inspection unit tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Verifies section detection for both the current schema (v2) and the legacy
 * settings-only schema (v1), plus rejection of foreign files.
 */

package com.rebelroot.omni.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupInspectTest {

    @Test
    fun `detects all v2 sections with counts`() {
        val json = """
            {
              "app": "OmniBrowser",
              "schema_version": 2,
              "sections": ["settings","bookmarks","history","tabs"],
              "settings": {
                "datastore": {"omni_settings": [{"key":"a","type":"bool","value":true}]},
                "shared_prefs": {"omni_prefs": {"selected_language": {"type":"string","value":"en"}}}
              },
              "bookmarks": {"format":"netscape_html","count":3,"html":"<DL><p></DL><p>"},
              "history": [{"title":"A","url":"https://a.example","timestamp":1}],
              "tabs": {"browser_tabs.json":"[]","browser_tab_groups.json":"[]"}
            }
        """.trimIndent()

        val inspection = BackupEngine.inspect(json)
        requireNotNull(inspection)
        assertEquals(2, inspection.schemaVersion)
        assertTrue(BackupSection.SETTINGS in inspection.available)
        assertTrue(BackupSection.BOOKMARKS in inspection.available)
        assertTrue(BackupSection.HISTORY in inspection.available)
        assertTrue(BackupSection.TABS in inspection.available)
        assertEquals(3, inspection.countFor(BackupSection.BOOKMARKS))
        assertEquals(1, inspection.countFor(BackupSection.HISTORY))
        assertEquals(2, inspection.countFor(BackupSection.TABS))
    }

    @Test
    fun `detects legacy v1 settings-only file`() {
        val json = """
            {
              "app": "OmniBrowser",
              "schema_version": 1,
              "datastore": {"omni_settings": [{"key":"dark_theme_enabled","type":"bool","value":false}]},
              "shared_prefs": {"omni_prefs": {"selected_language": {"type":"string","value":"en"}}}
            }
        """.trimIndent()

        val inspection = BackupEngine.inspect(json)
        requireNotNull(inspection)
        assertEquals(1, inspection.schemaVersion)
        assertEquals(listOf(BackupSection.SETTINGS), inspection.available)
    }

    @Test
    fun `rejects foreign app tag`() {
        assertNull(BackupEngine.inspect("""{"app":"OtherBrowser","schema_version":2}"""))
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(BackupEngine.inspect("not json at all"))
    }
}