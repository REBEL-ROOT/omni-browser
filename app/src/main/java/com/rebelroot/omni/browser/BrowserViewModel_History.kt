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
    
    saveHistory(context)
}

fun BrowserViewModel.deleteHistoryEntry(entry: HistoryEntry) {
    val context = appContext ?: return
    historyList.remove(entry)
    saveHistory(context)
}

fun BrowserViewModel.clearAllHistory() {
    val context = appContext ?: return
    historyList.clear()
    saveHistory(context)
}

