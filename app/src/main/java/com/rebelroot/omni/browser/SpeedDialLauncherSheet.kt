package com.rebelroot.omni.browser

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

data class ShortcutLauncherItem(
    val id: String,
    val title: String,
    val url: String,
    val isBookmark: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialLauncherSheet(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val allItems = remember(viewModel.shortcutsList.toList(), viewModel.bookmarksList.toList()) {
        val shortcutsMapped = viewModel.shortcutsList
            .filter { !it.isFeature && it.url != "add" }
            .map { ShortcutLauncherItem(id = it.id, title = it.title, url = it.url, isBookmark = false) }

        val bookmarksMapped = viewModel.bookmarksList
            .map { ShortcutLauncherItem(id = "bm_${it.url.hashCode()}", title = it.title, url = it.url, isBookmark = true) }

        (shortcutsMapped + bookmarksMapped).distinctBy { it.url }
    }

    val filteredItems = remember(allItems, searchQuery, selectedLetter) {
        allItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.url.contains(searchQuery, ignoreCase = true)

            val firstChar = item.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val matchesLetter = when (selectedLetter) {
                "ALL" -> true
                "#" -> !firstChar.isLetter()
                else -> firstChar.toString() == selectedLetter
            }

            matchesSearch && matchesLetter
        }.sortedBy { it.title.lowercase() }
    }

    val alphabetList = remember { listOf("ALL", "#") + ('A'..'Z').map { it.toString() } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = bgColor,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SPEED DIAL",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Access all your web pages & shortcuts in one place",
                        fontSize = 11.5.sp,
                        color = textSecondary
                    )
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Shortcut",
                        tint = accentColor
                    )
                }
            }

            // Real-Time Search Bar with Search Icon
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Enter URL or Search Shortcuts...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // A-Z Alphabet Filter Strip (Fast Find)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(alphabetList) { letter ->
                    val isSelected = selectedLetter == letter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor else cardColor)
                            .clickable { selectedLetter = letter }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textPrimary
                        )
                    }
                }
            }

            // Grid View of Shortcuts & Bookmarks (4 columns matching App Launcher)
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No shortcuts found for '$selectedLetter'",
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        SpeedDialGridTile(
                            item = item,
                            onClick = {
                                onDismissRequest()
                                onOpenUrl(item.url)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSpeedDialShortcutDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, url ->
                viewModel.addShortcut(title, url)
                Toast.makeText(context, "Added to Speed Dial", Toast.LENGTH_SHORT).show()
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddSpeedDialShortcutDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Speed Dial Shortcut", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Shortcut Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Google", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web Address (URL)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. google.com", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                        val finalTitle = title.ifBlank { formattedUrl }
                        onAdd(finalTitle, formattedUrl)
                    }
                },
                enabled = url.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Shortcut")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class SiteBrandStyle(
    val bgGradient: List<Color>,
    val badgeText: String,
    val iconRes: Int? = null
)

fun getSiteBrandStyle(url: String, title: String): SiteBrandStyle {
    val domain = runCatching { java.net.URI(url).host?.lowercase() ?: url.lowercase() }.getOrDefault(url.lowercase())
    val name = title.lowercase()

    return when {
        domain.contains("1337x") || name.contains("1337x") ->
            SiteBrandStyle(listOf(Color(0xFFD32F2F), Color(0xFFB71C1C)), badgeText = "1337x", iconRes = com.rebelroot.omni.R.drawable.ic_logo_1337x)
        domain.contains("piratebay") || name.contains("pirate bay") ->
            SiteBrandStyle(listOf(Color(0xFF00695C), Color(0xFF004D40)), badgeText = "TPB", iconRes = com.rebelroot.omni.R.drawable.ic_logo_piratebay)
        domain.contains("yts") || name.contains("yts") ->
            SiteBrandStyle(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)), badgeText = "YTS", iconRes = com.rebelroot.omni.R.drawable.ic_logo_yts)
        domain.contains("torrentgalaxy") || name.contains("torrentgalaxy") ->
            SiteBrandStyle(listOf(Color(0xFF6A1B9A), Color(0xFF4A148C)), badgeText = "TGx", iconRes = com.rebelroot.omni.R.drawable.ic_logo_torrentgalaxy)
        domain.contains("eztv") || name.contains("eztv") ->
            SiteBrandStyle(listOf(Color(0xFF1565C0), Color(0xFF0D47A1)), badgeText = "EZ", iconRes = com.rebelroot.omni.R.drawable.ic_logo_eztv)
        domain.contains("fitgirl") || name.contains("fitgirl") ->
            SiteBrandStyle(listOf(Color(0xFFC2185B), Color(0xFF880E4F)), badgeText = "FG", iconRes = com.rebelroot.omni.R.drawable.ic_logo_fitgirl)
        domain.contains("limetorrents") || name.contains("limetorrents") ->
            SiteBrandStyle(listOf(Color(0xFF558B2F), Color(0xFF33691E)), badgeText = "LIME", iconRes = com.rebelroot.omni.R.drawable.ic_logo_limetorrents)
        domain.contains("nyaa") || name.contains("nyaa") ->
            SiteBrandStyle(listOf(Color(0xFF0288D1), Color(0xFF01579B)), badgeText = "NYAA", iconRes = com.rebelroot.omni.R.drawable.ic_logo_nyaa)
        domain.contains("rutracker") || name.contains("rutracker") ->
            SiteBrandStyle(listOf(Color(0xFFE65100), Color(0xFFBF360C)), badgeText = "RU", iconRes = com.rebelroot.omni.R.drawable.ic_logo_rutracker)
        domain.contains("academictorrents") || name.contains("academic") ->
            SiteBrandStyle(listOf(Color(0xFF283593), Color(0xFF1A237E)), badgeText = "ACAD", iconRes = com.rebelroot.omni.R.drawable.ic_logo_academictorrents)
        domain.contains("rebelroot") ->
            SiteBrandStyle(listOf(Color(0xFF00ACC1), Color(0xFF006064)), badgeText = "RR")
        domain.contains("twitter") || domain.contains("x.com") ->
            SiteBrandStyle(listOf(Color(0xFF1DA1F2), Color(0xFF0C7ABF)), badgeText = "X")
        domain.contains("spotify") ->
            SiteBrandStyle(listOf(Color(0xFF1DB954), Color(0xFF128C3E)), badgeText = "♫")
        domain.contains("amazon") ->
            SiteBrandStyle(listOf(Color(0xFFFF9900), Color(0xFFE67E00)), badgeText = "amz")
        domain.contains("pinterest") ->
            SiteBrandStyle(listOf(Color(0xFFE60023), Color(0xFFAD001A)), badgeText = "P")
        else -> {
            val hash = Math.abs(url.hashCode())
            val palette = listOf(
                listOf(Color(0xFF1E88E5), Color(0xFF1565C0)),
                listOf(Color(0xFFE53935), Color(0xFFC62828)),
                listOf(Color(0xFF43A047), Color(0xFF2E7D32)),
                listOf(Color(0xFFFB8C00), Color(0xFFEF6C00)),
                listOf(Color(0xFF8E24AA), Color(0xFF6A1B9A)),
                listOf(Color(0xFF00ACC1), Color(0xFF00838F))
            )
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
            SiteBrandStyle(palette[hash % palette.size], badgeText = initial)
        }
    }
}

@Composable
fun SpeedDialGridTile(
    item: ShortcutLauncherItem,
    onClick: () -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val brandStyle = remember(item.url, item.title) { getSiteBrandStyle(item.url, item.title) }

    val host = remember(item.url) {
        runCatching { java.net.URI(item.url).host }.getOrNull()
    }
    val faviconUrl = remember(host) {
        if (!host.isNullOrBlank()) "https://icons.duckduckgo.com/ip3/$host.ico" else null
    }

    var imageLoadFailed by remember(item.url) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(brandStyle.bgGradient)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                brandStyle.iconRes != null -> {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = brandStyle.iconRes),
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                !imageLoadFailed && faviconUrl != null -> {
                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
                        onError = { imageLoadFailed = true }
                    )
                }
                else -> {
                    Text(
                        text = brandStyle.badgeText,
                        fontSize = if (brandStyle.badgeText.length > 2) 12.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
