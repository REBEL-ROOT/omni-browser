/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.browser.tabs

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
