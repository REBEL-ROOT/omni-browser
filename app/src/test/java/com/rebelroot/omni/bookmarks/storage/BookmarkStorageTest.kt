/*
 * Omni Browser - Bookmark Storage Unit Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 02 gate: atomic writes, versioned format, legacy migration,
 * corruption recovery, round-trip fidelity.
 */

package com.rebelroot.omni.bookmarks.storage

import com.rebelroot.omni.bookmarks.model.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.io.File

class BookmarkStorageTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDir("omni_bookmark_test_")
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Round-trip
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun roundTrip_emptyCollection_producesEmptyCollection() {
        val original = BookmarkCollection()
        saveBookmarksToDir(tempDir, original)

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(0, loaded.bookmarkCount())
        assertEquals(0, loaded.folderCount())
    }

    @Test
    fun roundTrip_bookmarksAndFolders_preserved() {
        val original = BookmarkCollection()
        original.addBookmark("Google", "https://google.com")
        original.addFolder("Work")
        val docs = original.addFolder("Docs", parentId = original.allFolders().first().id)
        original.addBookmark("Sheet", "https://sheet.example", parentId = docs.id)

        saveBookmarksToDir(tempDir, original)
        val loaded = loadBookmarksFromDir(tempDir)

        assertEquals(2, loaded.bookmarkCount())
        assertEquals(2, loaded.folderCount())
        assertEquals("Work", loaded.allFolders().first { it.title == "Work" }.title)
        val sheet = loaded.allBookmarks().first { it.title == "Sheet" }
        assertEquals(docs.id, sheet.parentId)
    }

    @Test
    fun roundTrip_positionsPreserved() {
        val original = BookmarkCollection()
        original.addBookmark("A", "https://a.example")
        original.addBookmark("B", "https://b.example")
        original.addFolder("F")
        original.addBookmark("C", "https://c.example")

        saveBookmarksToDir(tempDir, original)
        val loaded = loadBookmarksFromDir(tempDir)

        val ids = loaded.childIds(ROOT_FOLDER_ID)
        assertEquals(4, ids.size)
        assertEquals(listOf(0L, 1L, 2L, 3L), ids.map { loaded.bookmark(it)?.position ?: loaded.folder(it)!!.position })
    }

    @Test
    fun roundTrip_timestampsPreserved() {
        val original = BookmarkCollection { 1_234_567_890L }
        original.addBookmark("T", "https://t.example")

        saveBookmarksToDir(tempDir, original)
        val loaded = loadBookmarksFromDir(tempDir)

        val bm = loaded.allBookmarks().first()
        assertEquals(1_234_567_890L, bm.createdAt)
        assertEquals(1_234_567_890L, bm.modifiedAt)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Legacy migration
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun legacyMigration_flatBookmarks_migratedToRootLevel() {
        // Write legacy format.
        val legacyJson = """[
            {"title":"Google","url":"https://google.com","timestamp":1000},
            {"title":"GitHub","url":"https://github.com","timestamp":2000}
        ]""".trimIndent()
        File(tempDir, "browser_bookmarks.json").writeText(legacyJson)

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(2, loaded.bookmarkCount())
        assertEquals(0, loaded.folderCount())
        assertTrue(loaded.allBookmarks().any { it.title == "Google" && it.url == "https://google.com" })
        assertTrue(loaded.allBookmarks().any { it.title == "GitHub" && it.url == "https://github.com" })
    }

    @Test
    fun legacyMigration_densePositionsAssigned() {
        val legacyJson = """[
            {"title":"A","url":"https://a.example","timestamp":1000},
            {"title":"B","url":"https://b.example","timestamp":2000}
        ]""".trimIndent()
        File(tempDir, "browser_bookmarks.json").writeText(legacyJson)

        val loaded = loadBookmarksFromDir(tempDir)
        val positions = loaded.allBookmarks().map { it.position }
        assertEquals(listOf(0L, 1L), positions.sorted())
    }

    @Test
    fun legacyMigration_v2FileCreatedAfterMigration() {
        File(tempDir, "browser_bookmarks.json").writeText("""[{"title":"X","url":"https://x.example"}]""")

        loadBookmarksFromDir(tempDir)
        assertTrue(File(tempDir, "browser_bookmarks_v2.json").exists())
    }

    @Test
    fun legacyMigration_v2TakesPrecedenceOverLegacy() {
        // Legacy has 1 bookmark, v2 has 2.
        File(tempDir, "browser_bookmarks.json").writeText("""[{"title":"Legacy","url":"https://legacy.example"}]""")
        val original = BookmarkCollection()
        original.addBookmark("V2", "https://v2.example")
        original.addBookmark("V2-2", "https://v2-2.example")
        saveBookmarksToDir(tempDir, original)

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(2, loaded.bookmarkCount())
        assertTrue(loaded.allBookmarks().any { it.title == "V2" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Corruption recovery
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun corruptV2File_returnsEmptyCollection() {
        File(tempDir, "browser_bookmarks_v2.json").writeText("not json at all {[")

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(0, loaded.bookmarkCount())
    }

    @Test
    fun corruptV2File_doesNotCrash() {
        File(tempDir, "browser_bookmarks_v2.json").writeText("{\"schema_version\":2,\"bookmarks\":[{\"id\":\"x\"}]}")

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(0, loaded.bookmarkCount())
    }

    @Test
    fun missingFile_returnsEmptyCollection() {
        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(0, loaded.bookmarkCount())
        assertEquals(0, loaded.folderCount())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Atomic write safety
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun atomicWrite_tempFileRemovedAfterSuccess() {
        val collection = BookmarkCollection()
        collection.addBookmark("A", "https://a.example")

        saveBookmarksToDir(tempDir, collection)
        assertFalse(File(tempDir, "browser_bookmarks_v2.json.tmp").exists())
    }

    @Test
    fun atomicWrite_existingDataPreservedOnOverwrite() {
        val first = BookmarkCollection()
        first.addBookmark("First", "https://first.example")
        saveBookmarksToDir(tempDir, first)

        val second = BookmarkCollection()
        second.addBookmark("Second", "https://second.example")
        second.addFolder("Folder")
        saveBookmarksToDir(tempDir, second)

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(1, loaded.bookmarkCount())
        assertEquals(1, loaded.folderCount())
        assertTrue(loaded.allBookmarks().any { it.title == "Second" })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Schema version
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun schemaVersion_writtenToFile() {
        val collection = BookmarkCollection()
        collection.addBookmark("A", "https://a.example")

        saveBookmarksToDir(tempDir, collection)
        val text = File(tempDir, "browser_bookmarks_v2.json").readText()
        assertTrue(text.contains("\"schema_version\":2"))
    }

    @Test
    fun unknownSchemaVersion_treatedAsEmpty() {
        File(tempDir, "browser_bookmarks_v2.json").writeText("""{"schema_version":99,"bookmarks":[],"folders":[]}""")

        val loaded = loadBookmarksFromDir(tempDir)
        assertEquals(0, loaded.bookmarkCount())
    }
}
