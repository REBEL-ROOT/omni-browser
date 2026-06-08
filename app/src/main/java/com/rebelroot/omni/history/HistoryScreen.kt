package com.rebelroot.omni.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter history based on search query
    val filteredHistory = viewModel.historyList.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
    }

    // Dynamic grouping by Today / Yesterday / Older
    val now = System.currentTimeMillis()
    val millisecondsInDay = 24 * 60 * 60 * 1000L
    
    val groupedHistory = remember(filteredHistory) {
        filteredHistory.groupBy { entry ->
            val diff = now - entry.timestamp
            when {
                diff < millisecondsInDay -> "TODAY"
                diff < 2 * millisecondsInDay -> "YESTERDAY"
                else -> "OLDER"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearAllHistory() }) {
                        Text("Clear all", color = Color(0xFF0088FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070A0F)
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, Color(0xFF16222F).copy(alpha = 0.2f))
                )
            )
        },
        bottomBar = {
            // Flat minimal bottom bar persisting exactly as requested in screenshots
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = Color(0xFF0D1620),
                border = BorderStroke(0.5.dp, Color(0xFF16222F).copy(alpha = 0.5f))
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
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.tabs.size.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = Color.White,
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
                .background(Color(0xFF070A0F)) // Obsidian black background
        ) {
            // Interactive Slate Search box with filter icon on the right
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search history", color = Color(0xFF8E9AA8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF8E9AA8)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { /* filter action */ }) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Filter",
                            tint = Color(0xFF8E9AA8)
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF0088FF),
                    unfocusedBorderColor = Color(0xFF16222F),
                    focusedContainerColor = Color(0xFF16222F),
                    unfocusedContainerColor = Color(0xFF16222F)
                )
            )

            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history records found",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8E9AA8)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Loop through groups in order: TODAY, YESTERDAY, OLDER
                    listOf("TODAY", "YESTERDAY", "OLDER").forEach { category ->
                        val itemsInCategory = groupedHistory[category] ?: emptyList()
                        if (itemsInCategory.isNotEmpty()) {
                            item(key = category) {
                                Text(
                                    text = category,
                                    color = Color(0xFF8E9AA8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
                                )
                            }
                            
                            items(itemsInCategory, key = { it.timestamp }) { entry ->
                                HistoryRowItem(
                                    entry = entry,
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
}

@Composable
fun HistoryRowItem(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = Color(0xFF16222F),
        border = BorderStroke(0.5.dp, Color(0xFF23374A))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF243647)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = Color(0xFF8E9AA8),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = entry.url,
                    fontSize = 11.sp,
                    color = Color(0xFF8E9AA8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF243647).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete entry",
                    tint = Color(0xFF8E9AA8),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
