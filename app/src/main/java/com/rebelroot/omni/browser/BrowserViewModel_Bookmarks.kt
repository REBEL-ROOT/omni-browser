package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG

internal fun BrowserViewModel.loadBookmarks(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(context.filesDir, "browser_bookmarks.json")
        if (!file.exists()) return@launch
        try {
            val jsonArray = JSONArray(file.readText())
            val temp = mutableListOf<BookmarkEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                temp.add(BookmarkEntry(
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
            withContext(Dispatchers.Main) {
                bookmarksList.clear()
                bookmarksList.addAll(temp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bookmarks", e)
        }
    }
}

internal fun BrowserViewModel.saveBookmarks(context: Context) {
    val bookmarksSnapshot = bookmarksList.toList()
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(context.filesDir, "browser_bookmarks.json")
        try {
            val jsonArray = JSONArray()
            bookmarksSnapshot.forEach { entry ->
                jsonArray.put(JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("timestamp", entry.timestamp)
                })
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bookmarks", e)
        }
    }
}

fun BrowserViewModel.addToBookmarks(title: String, url: String) {
    val context = appContext ?: return
    if (url == "about:blank" || url.trim().isEmpty()) return
    
    bookmarksList.removeAll { it.url == url }
    bookmarksList.add(0, BookmarkEntry(title, url, System.currentTimeMillis()))
    saveBookmarks(context)
}

fun BrowserViewModel.removeBookmark(url: String) {
    val context = appContext ?: return
    bookmarksList.removeAll { it.url == url }
    saveBookmarks(context)
}

fun BrowserViewModel.clearAllBookmarks() {
    val context = appContext ?: return
    bookmarksList.clear()
    saveBookmarks(context)
}

fun BrowserViewModel.isBookmarked(url: String): Boolean {
    return bookmarksList.any { it.url == url }
}
