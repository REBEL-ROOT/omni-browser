package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.BookmarkNode
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.bookmarks.storage.loadBookmarks as loadCanonicalBookmarks
import com.rebelroot.omni.bookmarks.storage.saveBookmarks as saveCanonicalBookmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG

/**
 * Serializes writes to the bookmark store so two rapid add/remove operations
 * cannot lose each other's changes (both would otherwise load the same
 * snapshot before either saves). Shared with the sync writers via
 * [com.rebelroot.omni.bookmarks.storage.BookmarkStoreLock].
 */
private val bookmarkStoreMutex = com.rebelroot.omni.bookmarks.storage.BookmarkStoreLock.mutex

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

/** Replaces the flat UI list and folder tree from the canonical collection. Call on the main thread. */
private fun BrowserViewModel.applyCollectionToUi(collection: BookmarkCollection) {
    val entries = collection.toUiEntries()
    bookmarksList.clear()
    bookmarksList.addAll(entries)
    bookmarksTree = collection.buildTree()
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
                collection.allBookmarks().filter { it.url == url }.forEach {
                    BookmarkSyncEmitter.emitDelete(it.id, isFolder = false)
                }
                collection.deleteByUrl(url)
                val created = collection.addBookmark(title = title, url = url, parentId = ROOT_FOLDER_ID)
                BookmarkSyncEmitter.emitCreate(collection, created.id)
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
                collection.allBookmarks().filter { it.url == url }.forEach {
                    BookmarkSyncEmitter.emitDelete(it.id, isFolder = false)
                }
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
    // Republish both projections so the manager (which renders the folder tree)
    // and the flat list (speed dial / star) reflect the clear immediately.
    applyCollectionToUi(BookmarkCollection())
    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                val current = loadCanonicalBookmarks(context)
                current.allFolders().forEach { BookmarkSyncEmitter.emitDelete(it.id, isFolder = true) }
                current.allBookmarks().forEach { BookmarkSyncEmitter.emitDelete(it.id, isFolder = false) }
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

// ── Bookmark manager operations (folder tree, rename, move, delete) ──────────

/**
 * Loads the canonical store, applies [transform], saves it, then republishes
 * the UI list and tree. Serialized through [bookmarkStoreMutex] so it cannot
 * race a concurrent add/remove.
 */
private fun BrowserViewModel.persistBookmarks(
    context: Context,
    transform: (BookmarkCollection) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        bookmarkStoreMutex.withLock {
            try {
                val collection = loadCanonicalBookmarks(context)
                transform(collection)
                saveCanonicalBookmarks(context, collection)
                withContext(Dispatchers.Main) { applyCollectionToUi(collection) }
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting bookmark change", e)
            }
        }
    }
}

/** Creates a folder inside [parentId]; a blank name falls back to a default. */
fun BrowserViewModel.createBookmarkFolder(title: String, parentId: String = ROOT_FOLDER_ID) {
    val context = appContext ?: return
    val name = title.trim().ifBlank { "New folder" }
    persistBookmarks(context) { collection ->
        val folder = collection.addFolder(name, parentId = parentId)
        BookmarkSyncEmitter.emitCreate(collection, folder.id)
    }
}

/** Adds a bookmark to [parentId] (used by the manager's "new bookmark" action). */
fun BrowserViewModel.addBookmarkToFolder(title: String, url: String, parentId: String = ROOT_FOLDER_ID) {
    val context = appContext ?: return
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty() || cleanUrl == "about:blank") return
    val name = title.trim().ifBlank { cleanUrl }
    persistBookmarks(context) { collection ->
        val bookmark = collection.addBookmark(name, cleanUrl, parentId = parentId)
        BookmarkSyncEmitter.emitCreate(collection, bookmark.id)
    }
}

/** Renames a bookmark or folder. */
fun BrowserViewModel.renameBookmarkItem(id: String, title: String) {
    val context = appContext ?: return
    persistBookmarks(context) { collection ->
        if (collection.rename(id, title.trim())) BookmarkSyncEmitter.emitUpdate(collection, id)
    }
}

/** Updates a bookmark's URL. */
fun BrowserViewModel.updateBookmarkUrl(id: String, url: String) {
    val context = appContext ?: return
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) return
    persistBookmarks(context) { collection ->
        if (collection.setUrl(id, cleanUrl)) BookmarkSyncEmitter.emitUpdate(collection, id)
    }
}

/** Deletes a bookmark, or a folder together with everything inside it. */
fun BrowserViewModel.deleteBookmarkItem(id: String) {
    val context = appContext ?: return
    persistBookmarks(context) { collection ->
        val isFolder = collection.folder(id) != null
        if (collection.deleteItem(id)) BookmarkSyncEmitter.emitDelete(id, isFolder)
    }
}

/** Moves a bookmark or folder into [newParentId], appended after its siblings. */
fun BrowserViewModel.moveBookmarkItem(id: String, newParentId: String) {
    val context = appContext ?: return
    persistBookmarks(context) { collection ->
        val target = collection.childCount(newParentId)
        if (collection.moveItem(id, newParentId, target)) BookmarkSyncEmitter.emitMove(collection, id)
    }
}