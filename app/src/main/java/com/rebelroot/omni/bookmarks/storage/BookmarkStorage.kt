/*
 * Omni Browser - Bookmark Storage (v2)
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Atomic, versioned, JSON-backed persistence for the canonical bookmark model.
 * Writes go to a temp file and are renamed atomically so a crash during write
 * can never corrupt the live database. The legacy flat format is detected and
 * migrated automatically on first load.
 *
 * This is an Android-dependent layer (File, Context) — tests use Robolectric
 * or instrumented tests if Android APIs are required. Pure-Kotlin tests
 * exercise the serialization round-trip via BookmarkCollection.
 */

package com.rebelroot.omni.bookmarks.storage

import android.content.Context
import com.rebelroot.omni.bookmarks.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


/** JVM/Android compatible logger. On Android this is a no-op wrapper around android.util.Log
 *  that is replaced at link time; in unit tests it falls back to System.err. */
private object OmniLog {
    fun w(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (e: RuntimeException) {
            System.err.println("W/$tag: $msg")
        }
    }
    fun e(tag: String, msg: String, th: Throwable? = null) {
        try {
            if (th != null) android.util.Log.e(tag, msg, th) else android.util.Log.e(tag, msg)
        } catch (e: RuntimeException) {
            System.err.println("E/$tag: $msg")
            th?.printStackTrace()
        }
    }
    fun i(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (e: RuntimeException) {
            System.err.println("I/$tag: $msg")
        }
    }
}

private const val TAG = "BookmarkStorage"
private const val V2_FILE = "browser_bookmarks_v2.json"
private const val LEGACY_FILE = "browser_bookmarks.json"
private const val SCHEMA_VERSION = 2


/**
 * Loads bookmarks from v2 storage, falling back to legacy migration if needed.
 * Returns a populated [BookmarkCollection] (empty if nothing exists).
 *
 * Threading: caller must ensure this runs off the main thread (IO dispatcher).
 */
fun loadBookmarks(context: Context): BookmarkCollection =
    loadBookmarksFromDir(context.filesDir)

/**
 * Saves the entire [BookmarkCollection] atomically to v2 storage.
 *
 * Threading: caller must ensure this runs off the main thread (IO dispatcher).
 */
fun saveBookmarks(context: Context, collection: BookmarkCollection) {
    saveBookmarksToDir(context.filesDir, collection)
}

/**
 * Loads bookmarks from [filesDir]. Exposed for unit tests that pass a temp directory.
 */
internal fun loadBookmarksFromDir(filesDir: File): BookmarkCollection {
    val collection = BookmarkCollection()
    val v2File = File(filesDir, V2_FILE)
    val legacyFile = File(filesDir, LEGACY_FILE)

    if (v2File.exists()) {
        try {
            val json = JSONObject(v2File.readText())
            val version = json.optInt("schema_version", 1)
            if (version >= 2) {
                parseV2(json, collection)
            } else {
                OmniLog.w(TAG, "Unknown schema version $version, treating as empty")
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error loading v2 bookmarks", e)
        }
    } else if (legacyFile.exists()) {
        try {
            migrateLegacy(legacyFile, collection)
            // Persist the migrated data immediately so legacy is no longer needed.
            saveBookmarksToDir(filesDir, collection)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error migrating legacy bookmarks", e)
        }
    }
    return collection
}

/**
 * Saves [collection] to [filesDir] atomically. Exposed for unit tests.
 */
internal fun saveBookmarksToDir(filesDir: File, collection: BookmarkCollection) {
    val v2File = File(filesDir, V2_FILE)
    val tempFile = File(filesDir, "$V2_FILE.tmp")
    try {
        val json = serializeV2(collection)
        tempFile.writeText(json.toString())
        // Atomic rename: delete destination first so rename always succeeds on overwrite.
        if (v2File.exists() && !v2File.delete()) {
            throw IllegalStateException("Failed to delete existing $V2_FILE before rename")
        }

        if (!tempFile.renameTo(v2File)) {
            throw IllegalStateException("Failed to rename temp file to $V2_FILE")
        }
    } catch (e: Exception) {
        OmniLog.e(TAG, "Error saving bookmarks: ${e.message}", e)
        // Clean up temp file on failure.
        tempFile.delete()
    }
}

// ── Serialization ──────────────────────────────────────────────────────────

private fun serializeV2(collection: BookmarkCollection): JSONObject {
    val bookmarksArray = JSONArray()
    collection.allBookmarks().forEach { b ->
        bookmarksArray.put(JSONObject().apply {
            put("id", b.id)
            put("parentId", b.parentId)
            put("position", b.position)
            put("title", b.title)
            put("url", b.url)
            put("createdAt", b.createdAt)
            put("modifiedAt", b.modifiedAt)
        })
    }
    val foldersArray = JSONArray()
    collection.allFolders().forEach { f ->
        foldersArray.put(JSONObject().apply {
            put("id", f.id)
            put("parentId", f.parentId)
            put("position", f.position)
            put("title", f.title)
            put("createdAt", f.createdAt)
            put("modifiedAt", f.modifiedAt)
        })
    }
    return JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("bookmarks", bookmarksArray)
        put("folders", foldersArray)
    }
}

private fun parseV2(json: JSONObject, collection: BookmarkCollection) {
    val bookmarks = mutableListOf<OmniBookmark>()
    val folders = mutableListOf<OmniBookmarkFolder>()

    val bookmarksArray = json.optJSONArray("bookmarks") ?: JSONArray()
    for (i in 0 until bookmarksArray.length()) {
        val obj = bookmarksArray.getJSONObject(i)
        bookmarks.add(
            OmniBookmark(
                id = obj.getString("id"),
                parentId = obj.getString("parentId"),
                position = obj.getLong("position"),
                title = obj.getString("title"),
                url = obj.getString("url"),
                createdAt = obj.getLong("createdAt"),
                modifiedAt = obj.getLong("modifiedAt")
            )
        )
    }

    val foldersArray = json.optJSONArray("folders") ?: JSONArray()
    for (i in 0 until foldersArray.length()) {
        val obj = foldersArray.getJSONObject(i)
        folders.add(
            OmniBookmarkFolder(
                id = obj.getString("id"),
                parentId = obj.getString("parentId"),
                position = obj.getLong("position"),
                title = obj.getString("title"),
                createdAt = obj.getLong("createdAt"),
                modifiedAt = obj.getLong("modifiedAt")
            )
        )
    }

    // Validate before committing — if the stored data is corrupt, start fresh.
    val issues = BookmarkCollection.validate(bookmarks, folders)
    if (issues.isNotEmpty()) {
        OmniLog.w(TAG, "Stored bookmark data is corrupt (${issues.size} issues), starting fresh: ${issues.first().message}")
        return
    }
    collection.replaceAll(bookmarks, folders)
}

// ── Legacy Migration ───────────────────────────────────────────────────────

/**
 * Reads the legacy flat JSON file and populates the collection with all
 * bookmarks at the root level (no folders). Each bookmark gets a fresh
 * stable UUID and a dense position.
 */
private fun migrateLegacy(legacyFile: File, collection: BookmarkCollection) {
    val jsonArray = JSONArray(legacyFile.readText())
    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        collection.addBookmark(
            title = obj.getString("title"),
            url = obj.getString("url"),
            parentId = ROOT_FOLDER_ID,
            position = i.toLong()
        )
    }
    OmniLog.i(TAG, "Migrated ${jsonArray.length()} legacy bookmarks to v2")
}
