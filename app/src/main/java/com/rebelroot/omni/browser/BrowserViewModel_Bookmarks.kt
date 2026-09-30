package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.bookmarks.storage.loadBookmarks as loadCanonicalBookmarks
import com.rebelroot.omni.bookmarks.storage.saveBookmarks as saveCanonicalBookmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG

/**
 * Serializes writes to the bookmark store so two rapid add/remove operations
 * cannot lose each other's changes (both would otherwise load the same
 * snapshot before either saves).
 */
private val bookmarkStoreMutex = Mutex()

/** Canonical v2 store file name (must match BookmarkStorage). */
private const val LEGACY_BOOKMARK_FILE = "browser_bookmarks.json"

/**
 * Bookmarks are persisted in the canonical v2 store (folders + items). The UI
 * only needs a flat list, so [BrowserViewModel.bookmarksList] is a derived view
 * of that store — every mutation below writes through the store and then
 * refreshes the list, keeping import/export/sync and the UI in sync.
 */
private fun BookmarkCollection.toUiEntries(): List<BookmarkEntry> =
    allBookmarks()
        .sortedByDescending { it.createdAt }
        .map { BookmarkEntry(title = it.title, url = it.url, timestamp = it.createdAt) }

/** Replaces the flat UI list from the canonical collection. Call on the main thread. */
private fun BrowserViewModel.applyCollectionToUi(collection: BookmarkCollection) {
    val entries = collection.toUiEntries()
    bookmarksList.clear()
    bookmarksList.addAll(entries)
}

/**
 * Loads bookmarks from the canonical store into the UI-facing flat list.
 * The legacy flat file is migrated by the storage layer on first run; any
 * entries it still holds that are missing from the canonical store (added by
 * older builds) are reconciled once, then the legacy file is retired.
 */
internal fun BrowserViewModel.loadBookmarks(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                val collection = loadCanonicalBookmarks(context)
                reconcileLegacy(context, collection)
                withContext(Dispatchers.Main) { applyCollectionToUi(collection) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bookmarks", e)
            }
        }
    }
}

/**
 * Re-reads the canonical store into the UI list. Needed after code paths that
 * write the store directly (bookmark import, sync).
 */
fun BrowserViewModel.refreshBookmarks(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val collection = loadCanonicalBookmarks(context)
            withContext(Dispatchers.Main) { applyCollectionToUi(collection) }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing bookmarks", e)
        }
    }
}

/**
 * Merges legacy-only entries into [collection], persists if anything was added,
 * then deletes the legacy file so it is never merged twice (which could
 * resurrect a bookmark the user since deleted).
 */
private fun reconcileLegacy(context: Context, collection: BookmarkCollection) {
    val legacyFile = File(context.filesDir, LEGACY_BOOKMARK_FILE)
    if (!legacyFile.exists()) return
    try {
        val existingUrls = collection.allBookmarks().map { it.url }.toMutableSet()
        val jsonArray = JSONArray(legacyFile.readText())
        var added = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val url = obj.optString("url", "")
            if (url.isBlank() || url in existingUrls) continue
            collection.addBookmark(
                title = obj.optString("title", url),
                url = url,
                parentId = ROOT_FOLDER_ID
            )
            existingUrls += url
            added++
        }
        if (added > 0) {
            saveCanonicalBookmarks(context, collection)
            Log.i(TAG, "Reconciled $added legacy bookmark(s) into the canonical store")
        }
        legacyFile.delete()
    } catch (e: Exception) {
        Log.e(TAG, "Error reconciling legacy bookmarks", e)
    }
}

/** Deletes every bookmark in [collection] whose URL matches [url]. */
private fun BookmarkCollection.deleteByUrl(url: String) {
    allBookmarks().filter { it.url == url }.forEach { deleteItem(it.id) }
}

fun BrowserViewModel.addToBookmarks(title: String, url: String) {
    val context = appContext ?: return
    if (url == "about:blank" || url.trim().isEmpty()) return

    // Optimistic UI update so bookmark state reflects instantly; the store
    // refresh below then reconciles ordering and persistence.
    bookmarksList.removeAll { it.url == url }
    bookmarksList.add(0, BookmarkEntry(title, url, System.currentTimeMillis()))

    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                val collection = loadCanonicalBookmarks(context)
                collection.deleteByUrl(url)
                collection.addBookmark(title = title, url = url, parentId = ROOT_FOLDER_ID)
                saveCanonicalBookmarks(context, collection)
                withContext(Dispatchers.Main) { applyCollectionToUi(collection) }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bookmark", e)
            }
        }
    }
}

fun BrowserViewModel.removeBookmark(url: String) {
    val context = appContext ?: return

    bookmarksList.removeAll { it.url == url }

    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                val collection = loadCanonicalBookmarks(context)
                collection.deleteByUrl(url)
                saveCanonicalBookmarks(context, collection)
                withContext(Dispatchers.Main) { applyCollectionToUi(collection) }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bookmark", e)
            }
        }
    }
}

fun BrowserViewModel.clearAllBookmarks() {
    val context = appContext ?: return
    bookmarksList.clear()
    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                saveCanonicalBookmarks(context, BookmarkCollection())
                // Drop any leftover legacy file so its entries are not reconciled back.
                File(context.filesDir, LEGACY_BOOKMARK_FILE).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing bookmarks", e)
            }
        }
    }
}

fun BrowserViewModel.isBookmarked(url: String): Boolean {
    return bookmarksList.any { it.url == url }
}