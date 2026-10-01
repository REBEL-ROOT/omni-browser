package com.rebelroot.omni.browser

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Where a speed-dial tile came from — decides how it is deleted and ranked. */
enum class LauncherSource { SHORTCUT, BOOKMARK, HISTORY }

/** Top-level filters shown as chips across the top of the speed dial. */
enum class LauncherCategory(val label: String) {
    ALL("All"),
    OPTIMAL("Optimal"),
    BOOKMARKS("Bookmarks"),
    SHORTCUTS("Shortcuts"),
    RECENTS("Recents")
}

data class ShortcutLauncherItem(
    val id: String,
    val title: String,
    val url: String,
    val source: LauncherSource,
    val timestamp: Long = 0L
)

/** Normalized host for site matching: lowercase, `www.` stripped, null if unusable. */
private fun hostOf(url: String): String? =
    runCatching { java.net.URI(url).host?.lowercase()?.removePrefix("www.") }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

/**
 * True when two hosts belong to the same site. Handles parent/child hosts so a
 * page on `docs.github.com` still matches a bookmark saved on `github.com`.
 */
private fun isSameSite(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return a == b || a.endsWith(".$b") || b.endsWith(".$a")
}

/**
 * Key used to collapse duplicate entries across sources. Ignores scheme, a
 * leading `www.` and a trailing slash so `http://www.x.com/` and `https://x.com`
 * are treated as the same destination.
 */
private fun dedupeKey(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return runCatching {
        val uri = java.net.URI(trimmed)
        val host = uri.host?.lowercase()?.removePrefix("www.")
        if (host.isNullOrBlank()) trimmed.lowercase()
        else host + (uri.path ?: "") + (uri.query?.let { "?$it" } ?: "")
    }.getOrDefault(trimmed.lowercase())
}

/** Common search-engine hosts, including ccTLD variants (google.co.uk, yahoo.co.jp). */
private val SEARCH_ENGINE_SUFFIXES = listOf(
    "bing.com", "duckduckgo.com", "baidu.com", "ecosia.org", "startpage.com",
    "qwant.com", "mojeek.com", "brave.com", "ask.com", "aol.com", "naver.com",
    "seznam.cz", "sogou.com", "yandex.com", "yandex.ru", "yandex.com.tr"
)
private val GOOGLE_HOST = Regex("^google\\.[a-z]{2,3}(\\.[a-z]{2})?$")
private val YAHOO_HOST = Regex("^yahoo\\.[a-z]{2,3}(\\.[a-z]{2})?$")

/**
 * True for a search results page. There we show "All" — the query URL carries no
 * site of its own, so site-scoped "Optimal" suggestions would be meaningless.
 */
private fun isSearchEngine(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    if (GOOGLE_HOST.matches(host) || YAHOO_HOST.matches(host)) return true
    if (host.contains("searx")) return true
    return SEARCH_ENGINE_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

/**
 * Where the sheet should land when it opens: site-scoped "Optimal" when browsing
 * a real site that has related saved pages, otherwise "All".
 */
private fun defaultCategoryFor(url: String, hasOptimalResults: Boolean): LauncherCategory {
    val host = hostOf(url)
    return if (!host.isNullOrBlank() && !isSearchEngine(host) && hasOptimalResults) {
        LauncherCategory.OPTIMAL
    } else {
        LauncherCategory.ALL
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Delete confirmation state
    var itemToDelete by remember { mutableStateOf<ShortcutLauncherItem?>(null) }

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(76.dp) else GridCells.Fixed(5)

    // ── Sources ──────────────────────────────────────────────────────────────
    val shortcutItems = remember(viewModel.shortcutsList.toList()) {
        viewModel.shortcutsList
            .filter { !it.isFeature && it.url != "add" }
            .distinctBy { dedupeKey(it.url) }
            .map { ShortcutLauncherItem(it.id, it.title, it.url, LauncherSource.SHORTCUT) }
    }

    val bookmarkItems = remember(viewModel.bookmarksList.toList()) {
        viewModel.bookmarksList
            .distinctBy { dedupeKey(it.url) }
            .map { ShortcutLauncherItem("bm_${it.url.hashCode()}", it.title, it.url, LauncherSource.BOOKMARK) }
    }

    val recentItems = remember(viewModel.historyList.toList()) {
        viewModel.historyList
            .distinctBy { dedupeKey(it.url) }
            .take(150)
            .map { ShortcutLauncherItem("hist_${it.url.hashCode()}", it.title, it.url, LauncherSource.HISTORY, it.timestamp) }
    }

    // "All" = saved shortcuts first, then bookmarks, then anything else you visited.
    // Collapses any destination that appears in more than one source.
    val allItems = remember(shortcutItems, bookmarkItems, recentItems) {
        (shortcutItems + bookmarkItems + recentItems).distinctBy { dedupeKey(it.url) }
    }

    // "Optimal" = whatever is already saved/visited on the site you are on right
    // now, so you can jump straight to a related page without searching.
    val currentHost = remember(viewModel.currentUrl) { hostOf(viewModel.currentUrl) }
    val optimalItems = remember(allItems, currentHost) {
        val related = allItems
            .filter { isSameSite(hostOf(it.url), currentHost) }
            .distinctBy { dedupeKey(it.url) }
        related.filter { it.source != LauncherSource.HISTORY }.sortedBy { it.title.lowercase() } +
            related.filter { it.source == LauncherSource.HISTORY }.sortedByDescending { it.timestamp }
    }

    // Open on the tab that matches where the user is: "Optimal" for a site with
    // related saved pages, "All" on search results / blank pages. Evaluated once
    // per sheet open so the user's own tab choice is never overridden.
    var selectedCategory by remember {
        mutableStateOf(defaultCategoryFor(viewModel.currentUrl, optimalItems.isNotEmpty()))
    }

    val categoryItems = when (selectedCategory) {
        LauncherCategory.ALL -> allItems
        LauncherCategory.OPTIMAL -> optimalItems
        LauncherCategory.BOOKMARKS -> bookmarkItems
        LauncherCategory.SHORTCUTS -> shortcutItems
        LauncherCategory.RECENTS -> recentItems
    }

    val alphabetList = remember { listOf("ALL", "#") + ('A'..'Z').map { it.toString() } }

    // Recents are time-ordered, so the A–Z strip would fight that ordering.
    val showAlphabetStrip = selectedCategory != LauncherCategory.RECENTS
    val sortByName = selectedCategory in setOf(
        LauncherCategory.ALL, LauncherCategory.BOOKMARKS, LauncherCategory.SHORTCUTS
    )

    val filteredItems = remember(categoryItems, searchQuery, selectedLetter, sortByName) {
        val matched = categoryItems.filter { item ->
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
        }
        if (sortByName) matched.sortedBy { it.title.lowercase() } else matched
    }

    val emptyMessage = when {
        searchQuery.isNotBlank() -> "No results for \"$searchQuery\""
        selectedCategory == LauncherCategory.OPTIMAL && currentHost.isNullOrBlank() ->
            "Open a web page to see its saved pages here"
        selectedCategory == LauncherCategory.OPTIMAL ->
            "No saved pages for $currentHost yet"
        selectedLetter != "ALL" -> "No shortcuts found for '$selectedLetter'"
        else -> "Nothing here yet"
    }

    // Delete confirmation dialog
    if (itemToDelete != null) {
        val item = itemToDelete!!
        val isHistory = item.source == LauncherSource.HISTORY
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = if (isHistory) "Remove from Recents" else "Delete from Speed Dial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (isHistory)
                        "Remove \"${item.title}\" from your browsing recents?"
                    else
                        "Are you sure you want to remove \"${item.title}\" from Speed Dial?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (item.source) {
                            LauncherSource.BOOKMARK -> viewModel.removeBookmark(item.url)
                            LauncherSource.SHORTCUT -> viewModel.shortcutsList
                                .find { it.id == item.id }
                                ?.let { viewModel.deleteShortcut(it) }
                            LauncherSource.HISTORY -> viewModel.historyList
                                .find { it.url == item.url }
                                ?.let { viewModel.deleteHistoryEntry(it) }
                        }
                        Toast.makeText(
                            context,
                            if (isHistory) "Removed from Recents" else "Removed from Speed Dial",
                            Toast.LENGTH_SHORT
                        ).show()
                        itemToDelete = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isHistory) "Remove" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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

            // Category chips: All · Optimal · Bookmarks · Shortcuts · Recents
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LauncherCategory.entries) { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor else cardColor)
                            .clickable {
                                selectedCategory = category
                                selectedLetter = "ALL"
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textPrimary
                        )
                    }
                }
            }

            // A-Z Alphabet Filter Strip (Fast Find)
            if (showAlphabetStrip) {
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
            }

            // Grid View of Shortcuts & Bookmarks (5 columns matching Home Launcher)
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
                            text = emptyMessage,
                            fontSize = 14.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = gridColumns,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            },
                            onLongClick = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val currentTabTitle = remember(viewModel.activeTabId) {
            viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: ""
        }
        AddSpeedDialShortcutDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, url ->
                viewModel.addShortcut(title, url)
                Toast.makeText(context, "Added to Speed Dial", Toast.LENGTH_SHORT).show()
                showAddDialog = false
            },
            initialUrl = viewModel.currentUrl,
            initialTitle = currentTabTitle
        )
    }
}

@Composable
fun AddSpeedDialShortcutDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    initialUrl: String = "",
    initialTitle: String = ""
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialGridTile(
    item: ShortcutLauncherItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
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
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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
                            .size(30.dp)
                            .clip(CircleShape),
                        onError = { imageLoadFailed = true }
                    )
                }
                else -> {
                    Text(
                        text = brandStyle.badgeText,
                        fontSize = if (brandStyle.badgeText.length > 2) 11.sp else 15.sp,
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