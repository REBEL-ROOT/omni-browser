/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.tabs.SmartTabManager
import com.rebelroot.omni.browser.tabs.BrowserTab

fun BrowserViewModel.autoGroupTabs(context: Context) {
    val currentModeTabs = if (isIncognitoMode) tabs.filter { it.isIncognito } else tabs.filter { !it.isIncognito }
    val groupedTabIds = tabGroups.flatMap { it.tabIds }.toSet()
    val ungroupedTabs = currentModeTabs.filter { it.id !in groupedTabIds && it.url != "about:blank" && it.url.isNotEmpty() }

    if (ungroupedTabs.size < 2) return

    val smartTabManager = SmartTabManager()
    val browserTabs = ungroupedTabs.map { BrowserTab(it.id, it.title, it.url) }
    val categoryGroups = smartTabManager.categorize(browserTabs)

    var groupsCreated = 0
    var totalGroupedTabs = 0

    val groupColors = listOf(
        0xFF4285F4L, // Google Blue
        0xFF34A853L, // Google Green
        0xFFEA4335L, // Google Red
        0xFFFBBC05L, // Google Yellow
        0xFF9C27B0L, // Purple
        0xFFFF6D00L, // Orange
        0xFF00BCD4L, // Cyan
        0xFFE91E63L  // Pink
    )

    categoryGroups.forEachIndexed { idx, catGroup ->
        if (catGroup.tabs.size >= 2) {
            val title = catGroup.name
            val color = groupColors[idx % groupColors.size]
            val tabIds = catGroup.tabs.map { it.id }
            val group = TabGroup(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                color = color,
                tabIds = tabIds
            )
            tabGroups.add(group)
            groupsCreated++
            totalGroupedTabs += tabIds.size
        }
    }

    if (groupsCreated > 0) {
        saveTabGroups()
        Toast.makeText(
            context,
            context.getString(R.string.tab_group_auto_grouped_toast, totalGroupedTabs, groupsCreated),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun BrowserViewModel.ungroupTabs(groupId: String) {
    val idx = tabGroups.indexOfFirst { it.id == groupId }
    if (idx != -1) {
        tabGroups.removeAt(idx)
        saveTabGroups()
    }
}

fun BrowserViewModel.closeGroupAndTabs(groupId: String, context: Context) {
    val group = tabGroups.find { it.id == groupId } ?: return
    val idsToClose = group.tabIds.toList()
    idsToClose.forEach { closeTab(it, context) }
    tabGroups.removeAll { it.id == groupId }
    saveTabGroups()
}

fun BrowserViewModel.saveGroupToBookmarks(groupId: String, context: Context) {
    val group = tabGroups.find { it.id == groupId } ?: return
    val groupTabs = tabs.filter { it.id in group.tabIds }
    var addedCount = 0
    groupTabs.forEach { tab ->
        if (tab.url.isNotBlank() && tab.url != "about:blank") {
            addToBookmarks(tab.title.ifBlank { tab.url }, tab.url)
            addedCount++
        }
    }
    if (addedCount > 0) {
        Toast.makeText(context, context.getString(R.string.tab_group_saved_bookmarks_toast), Toast.LENGTH_SHORT).show()
    }
}

fun BrowserViewModel.shareGroupLinks(groupId: String, context: Context) {
    val group = tabGroups.find { it.id == groupId } ?: return
    val groupTabs = tabs.filter { it.id in group.tabIds }
    val urls = groupTabs.mapNotNull { it.url.takeIf { u -> u.isNotBlank() && u != "about:blank" } }
    if (urls.isEmpty()) return

    val shareText = "📁 ${group.title}:\n" + urls.joinToString("\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Group Links", shareText)
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, context.getString(R.string.tab_group_copied_links_toast), Toast.LENGTH_SHORT).show()
}
