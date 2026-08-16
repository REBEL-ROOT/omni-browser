/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.history

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rebelroot.omni.browser.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    BackHandler { onNavigateBack() }

    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isDarkMode = viewModel.isDarkThemeEnabled
    val bgColor = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val now = System.currentTimeMillis()
    val filteredHistory = viewModel.historyList.filter { entry ->
        val matchesSearch = searchQuery.isBlank() ||
                entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.url.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.TODAY -> isToday(entry.timestamp, now)
            HistoryFilter.YESTERDAY -> isYesterday(entry.timestamp, now)
            HistoryFilter.LAST_7_DAYS -> isWithinDays(entry.timestamp, now, 7)
            HistoryFilter.LAST_30_DAYS -> isWithinDays(entry.timestamp, now, 30)
            HistoryFilter.OLDER -> !isWithinDays(entry.timestamp, now, 30)
        }
        matchesSearch && matchesFilter
    }

    // Group by time buckets
    val grouped = remember(filteredHistory, selectedFilter) {
        if (selectedFilter == HistoryFilter.ALL) {
            groupByTimeBuckets(filteredHistory, now)
        } else {
            // When a specific filter is selected, show all items in one group
            mapOf(selectedFilter.label to filteredHistory)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    if (viewModel.historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                "Clear all",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, textPrimary, RoundedCornerShape(4.dp))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.tabs.size.toString(),
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(bgColor)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search history", color = textSecondary) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = textSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = textSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )

            // Filter chips
            FilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (filteredHistory.isEmpty()) {
                EmptyHistoryState(
                    isSearch = searchQuery.isNotEmpty(),
                    textSecondary = textSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    grouped.entries.forEach { (sectionLabel, entries) ->
                        if (entries.isNotEmpty()) {
                            item(key = "header_$sectionLabel") {
                                SectionHeader(label = sectionLabel)
                            }

                            items(entries, key = { "${it.timestamp}_${it.url}" }) { entry ->
                                HistoryListItem(
                                    entry = entry,
                                    now = now,
                                    isDarkMode = isDarkMode,
                                    onClick = { onOpenUrl(entry.url) },
                                    onDelete = { viewModel.deleteHistoryEntry(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear all confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear browsing history?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all history entries. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = HistoryFilter.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        filter.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = if (isSelected) null else FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier.height(34.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryListItem(
    entry: HistoryEntry,
    now: Long,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val domain = remember(entry.url) {
        try {
            java.net.URL(entry.url).host.removePrefix("www.")
        } catch (_: Exception) {
            entry.url.take(40)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Favicon placeholder / site icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val faviconUrl = remember(entry.url) {
                try {
                    val host = java.net.URL(entry.url).host
                    "https://www.google.com/s2/favicons?domain=$host&sz=64"
                } catch (_: Exception) { null }
            }
            if (faviconUrl != null) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Title + URL + time
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = entry.title.ifBlank { domain },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textPrimary
            )
            Text(
                text = domain,
                fontSize = 12.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatHistoryTime(entry.timestamp, now),
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.7f)
            )
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyHistoryState(
    isSearch: Boolean,
    textSecondary: Color
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = if (isSearch) "No matching history found" else "No browsing history",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )
            if (isSearch) {
                Text(
                    text = "Try a different search term",
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "Pages you visit will appear here",
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Filtering helpers ────────────────────────────────────────────────────────

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    OLDER("Older")
}

private fun isToday(timestamp: Long, now: Long): Boolean {
    val calNow = Calendar.getInstance().apply { timeInMillis = now }
    val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
    return calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(timestamp: Long, now: Long): Boolean {
    val calNow = Calendar.getInstance().apply { timeInMillis = now }
    val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
    calNow.add(Calendar.DAY_OF_YEAR, -1)
    return calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
}

private fun isWithinDays(timestamp: Long, now: Long, days: Int): Boolean {
    return (now - timestamp) < days * 24L * 60 * 60 * 1000
}

private fun groupByTimeBuckets(entries: List<HistoryEntry>, now: Long): Map<String, List<HistoryEntry>> {
    val result = linkedMapOf<String, MutableList<HistoryEntry>>()
    val todayList = mutableListOf<HistoryEntry>()
    val yesterdayList = mutableListOf<HistoryEntry>()
    val last7List = mutableListOf<HistoryEntry>()
    val last30List = mutableListOf<HistoryEntry>()
    val olderList = mutableListOf<HistoryEntry>()

    entries.forEach { entry ->
        when {
            isToday(entry.timestamp, now) -> todayList.add(entry)
            isYesterday(entry.timestamp, now) -> yesterdayList.add(entry)
            isWithinDays(entry.timestamp, now, 7) -> last7List.add(entry)
            isWithinDays(entry.timestamp, now, 30) -> last30List.add(entry)
            else -> olderList.add(entry)
        }
    }

    if (todayList.isNotEmpty()) result["Today"] = todayList
    if (yesterdayList.isNotEmpty()) result["Yesterday"] = yesterdayList
    if (last7List.isNotEmpty()) result["Last 7 days"] = last7List
    if (last30List.isNotEmpty()) result["Last 30 days"] = last30List
    if (olderList.isNotEmpty()) result["Older"] = olderList

    return result
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

private fun formatHistoryTime(timestamp: Long, now: Long): String {
    return when {
        isToday(timestamp, now) -> timeFormat.format(Date(timestamp))
        isYesterday(timestamp, now) -> "Yesterday • ${timeFormat.format(Date(timestamp))}"
        isWithinDays(timestamp, now, 7) -> dayFormat.format(Date(timestamp))
        else -> dateFormat.format(Date(timestamp))
    }
}
