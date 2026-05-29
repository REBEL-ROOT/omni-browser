package com.omni.browser.browser.tabs

import android.net.Uri

data class BrowserTab(
    val id: String,
    val title: String,
    val url: String
)

data class TabGroup(
    val name: String,
    val icon: String,
    val tabs: List<BrowserTab>
)

class SmartTabManager {
    private val categoryRules = mapOf(
        "Social" to listOf("facebook.com", "twitter.com", "instagram.com", "reddit.com", "x.com"),
        "Shopping" to listOf("amazon.com", "ebay.com", "flipkart.com", "aliexpress.com", "shopify.com"),
        "Video" to listOf("youtube.com", "vimeo.com", "twitch.tv", "netflix.com"),
        "News" to listOf("bbc.com", "cnn.com", "reuters.com", "news.google.com"),
        "Dev" to listOf("github.com", "stackoverflow.com", "developer.android.com", "kotlinlang.org"),
        "Work" to listOf("docs.google.com", "sheets.google.com", "notion.so", "slack.com")
    )

    fun categorize(tabs: List<BrowserTab>): List<TabGroup> {
        val grouped = mutableMapOf<String, MutableList<BrowserTab>>()
        val ungrouped = mutableListOf<BrowserTab>()

        tabs.forEach { tab ->
            val host = try {
                Uri.parse(tab.url).host ?: ""
            } catch (e: Exception) {
                ""
            }

            val category = categoryRules.entries.find { (_, domains) ->
                domains.any { host.contains(it) }
            }?.key

            if (category != null) {
                grouped.getOrPut(category) { mutableListOf() }.add(tab)
            } else {
                ungrouped.add(tab)
            }
        }

        return grouped.map { (name, tabs) ->
            TabGroup(name = name, icon = getCategoryIcon(name), tabs = tabs)
        } + if (ungrouped.isNotEmpty()) {
            listOf(TabGroup(name = "Other", icon = "🌐", tabs = ungrouped))
        } else emptyList()
    }

    private fun getCategoryIcon(category: String) = when (category) {
        "Social" -> "💬"
        "Shopping" -> "🛒"
        "Video" -> "🎬"
        "News" -> "📰"
        "Dev" -> "💻"
        "Work" -> "💼"
        else -> "🌐"
    }
}
