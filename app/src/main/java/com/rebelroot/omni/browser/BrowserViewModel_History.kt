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

internal fun BrowserViewModel.loadHistory(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(context.filesDir, "browser_history.json")
        if (!file.exists()) return@launch
        try {
            val jsonStr = file.readText()
            val jsonArray = JSONArray(jsonStr)
            val temp = mutableListOf<HistoryEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                temp.add(
                    HistoryEntry(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            temp.sortByDescending { it.timestamp }
            withContext(Dispatchers.Main) {
                historyList.clear()
                historyList.addAll(temp)
                visitedUrlsCache.clear()
                temp.forEach { entry ->
                    markUrlVisitedInMemory(entry.url)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
        }
    }
}

internal fun BrowserViewModel.saveHistory(context: Context) {
    val historySnapshot = historyList.toList()
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(context.filesDir, "browser_history.json")
        try {
            val jsonArray = JSONArray()
            historySnapshot.forEach { entry ->
                val obj = JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("timestamp", entry.timestamp)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history", e)
        }
    }
}

fun BrowserViewModel.addToHistory(title: String, url: String) {
    val context = appContext ?: return
    if (url == "about:blank" || url.trim().isEmpty()) return
    
    // Prevent duplicate spam
    historyList.removeAll { it.url == url }
    historyList.add(0, HistoryEntry(title, url, System.currentTimeMillis()))
    
    // Cap history at 500 items for memory safety
    if (historyList.size > 500) {
        historyList.removeAt(historyList.lastIndex)
    }
    
    markUrlVisitedInMemory(url)
    saveHistory(context)
}

fun BrowserViewModel.deleteHistoryEntry(entry: HistoryEntry) {
    val context = appContext ?: return
    historyList.remove(entry)
    rebuildVisitedUrlsCache()
    saveHistory(context)
}

fun BrowserViewModel.clearAllHistory() {
    val context = appContext ?: return
    historyList.clear()
    visitedUrlsCache.clear()
    tabs.forEach { it.session?.purgeHistory() }
    saveHistory(context)
}

fun BrowserViewModel.clearHistorySince(cutoffTime: Long) {
    val context = appContext ?: return
    historyList.removeAll { it.timestamp >= cutoffTime }
    rebuildVisitedUrlsCache()
    tabs.forEach { it.session?.purgeHistory() }
    saveHistory(context)
}

/**
 * Return up to [limit] history entries whose URL host matches the given [domain].
 * Results are sorted by timestamp descending (most recent first).
 * Domain comparison is normalized: lowercase, www. stripped.
 */
fun BrowserViewModel.getHistoryForDomain(domain: String, limit: Int = 20): List<HistoryEntry> {
    val normalizedDomain = domain.lowercase().removePrefix("www.")
    return historyList.filter { entry ->
        val entryHost = try {
            java.net.URI(entry.url).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
        entryHost == normalizedDomain || entryHost.endsWith(".$normalizedDomain")
    }.sortedByDescending { it.timestamp }.take(limit)
}

/**
 * Remove all history entries whose URL host matches the given [domain].
 */
fun BrowserViewModel.clearHistoryForDomain(domain: String) {
    val context = appContext ?: return
    val normalizedDomain = domain.lowercase().removePrefix("www.")
    historyList.removeAll { entry ->
        val entryHost = try {
            java.net.URI(entry.url).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
        entryHost == normalizedDomain || entryHost.endsWith(".$normalizedDomain")
    }
    rebuildVisitedUrlsCache()
    tabs.forEach { it.session?.purgeHistory() }
    saveHistory(context)
}

/**
 * Record a URL into the fast in-memory visited cache with normalized variants
 * (trailing slash variations and base URLs without hash fragments).
 */
fun BrowserViewModel.markUrlVisitedInMemory(url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed == "about:blank") return
    visitedUrlsCache.add(trimmed)
    
    if (trimmed.endsWith('/')) {
        visitedUrlsCache.add(trimmed.dropLast(1))
    } else {
        visitedUrlsCache.add("$trimmed/")
    }
    
    val hashIdx = trimmed.indexOf('#')
    if (hashIdx != -1) {
        val withoutHash = trimmed.substring(0, hashIdx)
        if (withoutHash.isNotEmpty()) {
            visitedUrlsCache.add(withoutHash)
            if (withoutHash.endsWith('/')) {
                visitedUrlsCache.add(withoutHash.dropLast(1))
            } else {
                visitedUrlsCache.add("$withoutHash/")
            }
        }
    }
}

/**
 * Rebuild the fast in-memory cache of visited URLs from current historyList.
 */
fun BrowserViewModel.rebuildVisitedUrlsCache() {
    visitedUrlsCache.clear()
    historyList.forEach { entry ->
        markUrlVisitedInMemory(entry.url)
    }
}

/**
 * Determine in O(1) time whether a URL has been previously visited.
 * Checks the URL, hash fragment variations, and trailing-slash variants.
 */
fun BrowserViewModel.isUrlVisited(url: String): Boolean {
    if (url.isBlank() || url == "about:blank") return false
    val trimmed = url.trim()
    if (visitedUrlsCache.contains(trimmed)) return true
    
    val hashIdx = trimmed.indexOf('#')
    if (hashIdx != -1) {
        val withoutHash = trimmed.substring(0, hashIdx)
        if (withoutHash.isNotEmpty()) {
            if (visitedUrlsCache.contains(withoutHash)) return true
            val altHashSlash = if (withoutHash.endsWith('/')) withoutHash.dropLast(1) else "$withoutHash/"
            if (visitedUrlsCache.contains(altHashSlash)) return true
        }
    }
    
    val altSlash = if (trimmed.endsWith('/')) trimmed.dropLast(1) else "$trimmed/"
    return visitedUrlsCache.contains(altSlash)
}

