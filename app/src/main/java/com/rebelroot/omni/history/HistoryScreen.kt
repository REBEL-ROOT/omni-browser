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
    
    val isDarkMode = viewModel.isDarkThemeEnabled
    
    val bgColor = if (isDarkMode) Color(0xFF070A0F) else Color(0xFFF8F9FA)
    val cardColor = if (isDarkMode) Color(0xFF16222F) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF23374A) else Color(0x1F000000)
    val textPrimaryColor = if (isDarkMode) Color.White else Color(0xFF202124)
    val textSecondaryColor = if (isDarkMode) Color(0xFF8E9AA8) else Color(0xFF606266)
    val dividerColor = if (isDarkMode) Color(0xFF23374A).copy(alpha = 0.5f) else Color(0x1F000000)
    
    val navBgColor = if (isDarkMode) Color(0xFF0D1620) else Color(0xFFFFFFFF)
    val navBorderColor = if (isDarkMode) Color(0xFF16222F).copy(alpha = 0.5f) else Color(0x1F000000)
    val navContentColor = if (isDarkMode) Color.White else Color(0xFF202124)
    val navContentMutedColor = if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color(0xFF202124).copy(alpha = 0.2f)
    val inputBgColor = if (isDarkMode) Color(0xFF16222F) else Color(0xFFF2F3F5)
    val inputBorderColor = if (isDarkMode) Color(0xFF16222F) else Color(0x1F000000)

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
                title = { Text("History", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimaryColor
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearAllHistory() }) {
                        Text("Clear all", color = Color(0xFF0088FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f))
                )
            )
        },
        bottomBar = {
            // Flat minimal bottom bar persisting exactly as requested in screenshots
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = navBgColor,
                border = BorderStroke(0.5.dp, navBorderColor)
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
                            tint = navContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = navContentMutedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = navContentMutedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, navContentColor, RoundedCornerShape(4.dp))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.tabs.size.toString(),
                            color = navContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = navContentColor,
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
                .background(bgColor) // Obsidian black background
        ) {
            // Interactive Slate Search box with filter icon on the right
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search history", color = textSecondaryColor) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = textSecondaryColor
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { /* filter action */ }) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Filter",
                            tint = textSecondaryColor
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimaryColor,
                    unfocusedTextColor = textPrimaryColor,
                    focusedBorderColor = Color(0xFF0088FF),
                    unfocusedBorderColor = inputBorderColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
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
                        color = textSecondaryColor
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
                                    color = textSecondaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
                                )
                            }
                            
                            items(itemsInCategory, key = { it.timestamp }) { entry ->
                                HistoryRowItem(
                                    entry = entry,
                                    isDarkMode = isDarkMode,
                                    textPrimaryColor = textPrimaryColor,
                                    textSecondaryColor = textSecondaryColor,
                                    cardColor = cardColor,
                                    cardBorderColor = cardBorderColor,
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
    isDarkMode: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = cardColor,
        border = BorderStroke(0.5.dp, cardBorderColor)
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
                    .background(if (isDarkMode) Color(0xFF243647) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = textSecondaryColor,
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
                    color = textPrimaryColor
                )
                Text(
                    text = entry.url,
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (isDarkMode) Color(0xFF243647) else Color(0xFFE2E8F0)).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete entry",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
