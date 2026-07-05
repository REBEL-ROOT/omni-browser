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

package com.rebelroot.omni.browser

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView
import com.rebelroot.omni.R
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.privacy.FireButton
import com.rebelroot.omni.tools.qrcode.BarcodeGenerator
import android.graphics.Bitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TabItem(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .height(32.dp)
            .widthIn(max = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    0.5.dp,
                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Tab",
                    modifier = Modifier.size(8.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreenContent(
    viewModel: BrowserViewModel,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenLocker: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenConsole: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.firstOrNull() ?: ""
                if (spokenText.isNotEmpty()) {
                    searchText = androidx.compose.ui.text.input.TextFieldValue(spokenText)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onNavigateTo(spokenText)
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center branding OMNI stylized logo Image
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                id = if (viewModel.isDarkThemeEnabled) {
                    com.rebelroot.omni.R.drawable.omni_home_logo
                } else {
                    com.rebelroot.omni.R.drawable.omni_home_logo_light
                }
            ),
            contentDescription = "Omni Browser Logo",
            modifier = Modifier
                .height(100.dp)
                .padding(horizontal = 16.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        // Flat Slate search pill
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            placeholder = { Text(stringResource(id = R.string.search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            leadingIcon = {
                var expanded by remember { mutableStateOf(false) }
                val currentEngine = viewModel.selectedSearchEngine
                
                Box {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { expanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val (char, color) = when (currentEngine) {
                            "DuckDuckGo" -> "D" to Color(0xFFDE5833)
                            "Brave" -> "B" to Color(0xFFFF1A1A)
                            "Bing" -> "b" to Color(0xFF00A4EF)
                            "Custom" -> "C" to Color(0xFF8E9AA8)
                            else -> "G" to MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = char,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                    ) {
                        val engines = listOf("Google", "DuckDuckGo", "Brave", "Bing", "Custom")
                        engines.forEach { engine ->
                            DropdownMenuItem(
                                text = { Text(engine, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                                onClick = {
                                    viewModel.saveSearchEngine(context, engine)
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    trailingIconColor = MaterialTheme.colorScheme.primary
                                ),
                                trailingIcon = {
                                    if (currentEngine == engine) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent("com.google.lens.intent.action.LENS_INPUT").apply {
                                    setPackage("com.google.android.googlequicksearchbox")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                        setClassName("com.google.ar.lens", "com.google.vr.apps.ornament.app.lens.LensLauncherActivity")
                                    }
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Google Lens is not installed on this device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Google Lens",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toString())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                }
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (searchText.text.isNotEmpty()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onNavigateTo(searchText.text)
                    }
                }
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E),
                unfocusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF1F3F4),
                unfocusedContainerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF1F3F4)
            )
        )

        // Dynamic Grid of Shortcuts — 5 per row, icon-only, no border boxes
        // Shows up to 15 items collapsed; "More" expands to show all.
        var showAddShortcutSheet by remember { mutableStateOf(false) }
        var shortcutsExpanded by remember { mutableStateOf(false) }
        val shortcuts = viewModel.shortcutsList

        val allItems = remember(shortcuts.toList()) {
            shortcuts.toList() + HomeShortcut(id = "add_shortcut_btn", title = "Add", url = "add")
        }
        // Items to display: collapse at 15 unless expanded (keep "Add" always last)
        val visibleItems = remember(allItems, shortcutsExpanded) {
            val realItems = allItems.dropLast(1) // all except Add btn
            val addBtn   = allItems.last()
            val showMore = realItems.size > 15 && !shortcutsExpanded
            val capped   = if (showMore) realItems.take(10) else realItems
            capped + addBtn
        }
        val hasMore = allItems.size - 1 > 15 && !shortcutsExpanded  // -1 for Add btn
        val columnsCount = 4
        val shortcutRows = remember(visibleItems) { visibleItems.chunked(columnsCount) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            shortcutRows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    rowItems.forEach { shortcut ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                shortcut.id == "add_shortcut_btn" -> {
                                    CompactShortcutItem(
                                        title = stringResource(id = R.string.add_title),
                                        icon = Icons.Rounded.Add,
                                        onClick = { showAddShortcutSheet = true }
                                    )
                                }
                                shortcut.isFeature -> {
                                    val (icon, isAccented, action) = when (shortcut.title) {
                                        "Downloads" -> Triple(Icons.Rounded.Download, true, onOpenDownloads)
                                        "History"   -> Triple(Icons.Rounded.History,  false, onOpenHistory)
                                        "Bookmarks" -> Triple(Icons.Rounded.Bookmark, false, onOpenBookmarks)
                                        "Incognito" -> Triple(
                                            if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            false,
                                            { viewModel.toggleIncognitoMode(context) }
                                        )
                                        else -> Triple(Icons.Rounded.Extension, false, {})
                                    }
                                    val displayTitle = when (shortcut.title) {
                                        "Downloads" -> stringResource(id = R.string.downloads_title)
                                        "History"   -> stringResource(id = R.string.history_title)
                                        "Bookmarks" -> stringResource(id = R.string.bookmarks_title)
                                        "Incognito" -> stringResource(id = R.string.incognito_title)
                                        else        -> shortcut.title
                                    }
                                    CompactShortcutItem(
                                        title = displayTitle,
                                        icon = icon,
                                        isAccented = isAccented,
                                        onClick = action
                                    )
                                }
                                else -> {
                                    var showDeleteDialog by remember { mutableStateOf(false) }
                                    if (showDeleteDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showDeleteDialog = false },
                                            title = { Text("Delete Shortcut?") },
                                            text = { Text("Remove shortcut to ${shortcut.title}?") },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    viewModel.deleteShortcut(shortcut)
                                                    showDeleteDialog = false
                                                }) { Text("Delete", color = Color(0xFFFF3B30)) }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                                            }
                                        )
                                    }
                                    CompactDynamicShortcutItem(
                                        title = shortcut.title,
                                        url = shortcut.url,
                                        onClick = { onNavigateTo(shortcut.url) },
                                        onLongClick = { if (!shortcut.isPermanent) showDeleteDialog = true }
                                    )
                                }
                            }
                        }
                    }
                    if (rowItems.size < columnsCount) {
                        repeat(columnsCount - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (hasMore || shortcutsExpanded) {
            TextButton(
                onClick = { shortcutsExpanded = !shortcutsExpanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (shortcutsExpanded) "Show less" else "More ›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Add to Home Bottom Sheet
        if (showAddShortcutSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddShortcutSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color.White
            ) {
                var nameInput by remember { mutableStateOf("") }
                var urlInput by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add to Home",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                        )
                        IconButton(onClick = { showAddShortcutSheet = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                            )
                        }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Shortcut Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Shortcut URL") },
                            placeholder = { Text("example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (urlInput.isNotEmpty()) {
                                        val title = nameInput.ifEmpty { urlInput }
                                        viewModel.addShortcut(title, urlInput)
                                        showAddShortcutSheet = false
                                    }
                                }
                            )
                        )
                        Button(
                            onClick = {
                                if (urlInput.isNotEmpty()) {
                                    val title = nameInput.ifEmpty { urlInput }
                                    viewModel.addShortcut(title, urlInput)
                                    showAddShortcutSheet = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add Custom Shortcut", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                    
                    Text(
                        text = "Popular websites",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                    )
                    
                    val popularSites = listOf(
                        Triple("Spotify", "https://spotify.com", Color(0xFF1DB954)),
                        Triple("Facebook", "https://facebook.com", Color(0xFF1877F2)),
                        Triple("Amazon", "https://amazon.com", Color(0xFFFF9900)),
                        Triple("Hulu", "https://hulu.com", Color(0xFF1CE783)),
                        Triple("Twitter", "https://twitter.com", Color(0xFF1DA1F2)),
                        Triple("eBay", "https://ebay.com", Color(0xFFE53238)),
                        Triple("Walmart", "https://walmart.com", Color(0xFF0071CE)),
                        Triple("Daily Mail", "https://dailymail.co.uk", Color(0xFF005689))
                    )
                    
                    val popularColumns = 4
                    val popularRows = remember(popularSites) { popularSites.chunked(popularColumns) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        popularRows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                rowItems.forEach { site ->
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PopularSiteItem(
                                            title = site.first,
                                            domain = site.second,
                                            bgColor = site.third,
                                            onClick = {
                                                viewModel.addShortcut(site.first, site.second)
                                                showAddShortcutSheet = false
                                            }
                                        )
                                    }
                                }
                                if (rowItems.size < popularColumns) {
                                    repeat(popularColumns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Discover Section Block (Dynamic News Feed)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.discover_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.refresh_title),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        viewModel.fetchNews(viewModel.selectedNewsCategory)
                    }
                )
            }

            // Category Pill Tabs
            val categories = listOf("Trending", "World", "Technology", "Sports", "Business", "Science", "Entertainment", "Health")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = viewModel.selectedNewsCategory == category
                    val displayCategory = when (category) {
                        "Trending" -> stringResource(id = R.string.cat_trending)
                        "World" -> stringResource(id = R.string.cat_world)
                        "Technology" -> stringResource(id = R.string.cat_technology)
                        "Sports" -> stringResource(id = R.string.cat_sports)
                        "Business" -> stringResource(id = R.string.cat_business)
                        "Science" -> stringResource(id = R.string.cat_science)
                        "Entertainment" -> stringResource(id = R.string.cat_entertainment)
                        "Health" -> stringResource(id = R.string.cat_health)
                        else -> category
                    }
                    Surface(
                        onClick = { viewModel.fetchNews(category) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayCategory,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (viewModel.isNewsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            } else if (viewModel.newsArticles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No articles found in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column {
                        viewModel.newsArticles.take(30).forEachIndexed { index, article ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateTo(article.link) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Thumbnail — always present (real image or source favicon)
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                                        .data(article.imageUrl)
                                        .size(120, 120)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                                    placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = article.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 18.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = article.source,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (article.pubDate.isNotEmpty()) {
                                            Text(
                                                text = "·",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = article.pubDate,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < minOf(viewModel.newsArticles.size, 30) - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                }
            }
        }
    }
}
}

// ── Compact shortcut item: icon-only pill, no visible border box ────────────
// Mirrors Chrome/Aloha new-tab style — icon floats on a soft tinted circle,
// label underneath, whole thing feels light and airy.
@Composable
fun CompactShortcutItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isAccented: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .width(56.dp)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isAccented)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isAccented) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactDynamicShortcutItem(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val domain = remember(url) {
        try { Uri.parse(url).host ?: url } catch (e: Exception) { url }
    }
    val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$domain"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.width(56.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .size(64, 64)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
                error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass)
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isAccented: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() },
            color = if (isAccented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (isAccented) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isAccented) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DiscoverRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ToolCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val accentColor = MaterialTheme.colorScheme.primary
    val iconContainerBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerBg)
                    .border(BorderStroke(0.5.dp, accentColor.copy(alpha = 0.35f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenLocker: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onPlayOnlineStream: (String, String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val coroutineScope = rememberCoroutineScope()
    var dragAmountAccumulated by remember { mutableStateOf(0f) }

    val showHomeScreen = viewModel.currentUrl == "about:blank" || viewModel.currentUrl.isEmpty()
    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }

    
    var showMenu by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)) }
    var isInputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // Video detection states
    val detectedMedia by viewModel.mediaInterceptor.detectedMedia.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var isAlohaBannerDismissed by remember { mutableStateOf(false) }
    var isScrollNavBarVisible by remember { mutableStateOf(true) }
    var isNavHideEnabled by remember { mutableStateOf(true) }
    val hasActiveUserExtensions = remember(viewModel.userExtensions.toList()) {
        viewModel.userExtensions.any { it.metaData.enabled }
    }
    LaunchedEffect(viewModel.currentUrl) {
        isAlohaBannerDismissed = false
        isScrollNavBarVisible = true
    }
    LaunchedEffect(isNavHideEnabled) {
        if (!isNavHideEnabled) {
            isScrollNavBarVisible = true
        }
    }
    var selectedMediaItem by remember { mutableStateOf<MediaInterceptor.DetectedMedia?>(null) }
    var showExtensionsSheet by remember { mutableStateOf(false) }

    // Fullscreen download overlay — hoisted outside the if-block so state survives
    // fullscreen entry/exit transitions and doesn't reset on every recomposition.
    var showFullscreenDownloadBtn by remember { mutableStateOf(true) }
    
    // Auto-Scroll and Player Settings states
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var isAutoScrollPaused by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableStateOf(1) }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }
    var isReaderSettingsExpanded by remember { mutableStateOf(true) }
    var isAutoScrollHUDExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel.isReaderModeActive) {
        if (viewModel.isReaderModeActive) {
            isReaderSettingsExpanded = true
        }
    }

    // Auto-Scroll Loop
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed, isAutoScrollPaused) {
        if (isAutoScrollActive && activeTab != null && !showHomeScreen) {
            val pixels = when (autoScrollSpeed) {
                1 -> 1
                2 -> 2
                3 -> 3
                4 -> 4
                5 -> 6
                else -> 1
            }
            val delayMs = when (autoScrollSpeed) {
                1 -> 50L
                2 -> 50L
                3 -> 40L
                4 -> 30L
                5 -> 20L
                else -> 50L
            }
            while (isAutoScrollActive && !isAutoScrollPaused) {
                activeTab.session.loadUri("javascript:(function(){ window.scrollBy(0, $pixels); })();")
                delay(delayMs)
            }
        }
    }

    // Auto-fade after 3 s while playing; reappear immediately on pause
    LaunchedEffect(showFullscreenDownloadBtn, viewModel.isVideoPlayingInPage) {
        if (showFullscreenDownloadBtn && viewModel.isVideoPlayingInPage) {
            kotlinx.coroutines.delay(3000)
            showFullscreenDownloadBtn = false
        }
    }
    LaunchedEffect(viewModel.isVideoPlayingInPage) {
        if (!viewModel.isVideoPlayingInPage) {
            showFullscreenDownloadBtn = true
        }
    }
    // Reset visibility whenever fullscreen is entered fresh
    LaunchedEffect(viewModel.isFullscreen) {
        if (viewModel.isFullscreen) showFullscreenDownloadBtn = true
    }

    // Offline Translation states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var translationSourceText by remember { mutableStateOf("") }
    var translationResultText by remember { mutableStateOf("") }
    var translationProgress by remember { mutableStateOf(false) }

    var showSourceLangMenu by remember { mutableStateOf(false) }
    var showTargetLangMenu by remember { mutableStateOf(false) }
    var showPageTargetLangMenu by remember { mutableStateOf(false) }

    var selectedSourceLang by remember { mutableStateOf("Spanish" to "es") }
    var selectedTargetLang by remember { mutableStateOf("English" to "en") }
    var selectedPageTargetLang by remember { mutableStateOf("English" to "en") }

    // Tab Switcher states
    var showTabGroupsSheet by remember { mutableStateOf(false) }
    
    // Developer Console state
    var showConsoleSheet by remember { mutableStateOf(false) }
    var showDevNotesSheet by remember { mutableStateOf(false) }
    var showSiteStyleCustomizerSheet by remember { mutableStateOf(false) }

    // Tools sheet state
    var showToolsSheet by remember { mutableStateOf(false) }
    var isHomeSearchFocused by remember { mutableStateOf(false) }

    val isKeyboardVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
            isHomeSearchFocused = false
            isInputFocused = false
        }
    }

    // QR Quick Tools states
    var showQrGeneratorDialog by remember { mutableStateOf(false) }
    var showQrScanResult by remember { mutableStateOf(false) }
    var qrGeneratorUrl by remember { mutableStateOf("") }

    // Feature Overview States
    var showQrOverviewDialog by remember { mutableStateOf(false) }
    var showPdfOverviewDialog by remember { mutableStateOf(false) }
    var showVideoOverviewDialog by remember { mutableStateOf(false) }
    var showExtensionsOverviewDialog by remember { mutableStateOf(false) }

    var pendingQrAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPdfAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingVideoAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExtensionsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var showEditPageOverviewDialog by remember { mutableStateOf(false) }
    var showConsoleOverviewDialog by remember { mutableStateOf(false) }

    var pendingEditPageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConsoleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDevNotesOverviewDialog by remember { mutableStateOf(false) }
    var pendingDevNotesAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        val request = viewModel.activeSystemPermissionRequest
        if (request != null) {
            if (allGranted) {
                request.onGranted()
            } else {
                request.onDenied()
            }
            viewModel.clearActiveSystemPermissionRequest()
        }
    }

    LaunchedEffect(viewModel.activeSystemPermissionRequest) {
        viewModel.activeSystemPermissionRequest?.let { request ->
            systemPermissionLauncher.launch(request.permissions ?: emptyArray())
        }
    }

    // ── File / Photo picker for web <input type="file"> ────────────────
    // Single-file picker (also handles camera/gallery via MIME type)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.deliverFilePickerResult(listOf(uri))
        } else {
            viewModel.cancelFilePrompt()
        }
    }

    // Multi-file picker
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.deliverFilePickerResult(uris)
        } else {
            viewModel.cancelFilePrompt()
        }
    }

    // Observe pendingFilePrompt and launch the right picker automatically
    LaunchedEffect(viewModel.pendingFilePrompt) {
        val pending = viewModel.pendingFilePrompt ?: return@LaunchedEffect
        // Build a MIME type string for the launcher. Fall back to "*/*" if none supplied.
        val mime = pending.mimeTypes
            ?.filter { it.isNotBlank() }
            ?.joinToString(",")
            ?.ifBlank { null }
            ?: "*/*"
        if (pending.allowMultiple) {
            multiFilePickerLauncher.launch(mime)
        } else {
            filePickerLauncher.launch(mime)
        }
    }

    LaunchedEffect(viewModel.currentUrl) {
        inputUrl = androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)
    }

    LaunchedEffect(viewModel.qrScanResults) {
        if (viewModel.qrScanResults.isNotEmpty()) {
            showQrScanResult = true
        }
    }

    LaunchedEffect(viewModel.qrScanError) {
        viewModel.qrScanError?.let { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            viewModel.clearQrScanResults()
        }
    }

    LaunchedEffect(viewModel.isFullscreen) {
        val activity = run {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            ctx as? Activity
        }
        activity?.let {
            FullscreenManager.setFullscreen(it, viewModel.isFullscreen)
        }
    }

    LaunchedEffect(viewModel, onPlayOnlineStream) {
        viewModel.onPlayVideoRequestReceived = { url, pageUrl ->
            onPlayOnlineStream(url, pageUrl)
        }
        viewModel.pendingVideoUrl?.let { url ->
            viewModel.pendingVideoUrl = null
            onPlayOnlineStream(url, url)
        }
    }


    // Reset home search focus state when we leave the home screen.
    // Also force the nav bar permanently visible when on the home screen.
    LaunchedEffect(showHomeScreen) {
        if (!showHomeScreen) {
            isHomeSearchFocused = false
        } else {
            isScrollNavBarVisible = true
        }
    }

    val currentShowHomeScreen by rememberUpdatedState(showHomeScreen)

    // Scroll delegate: registered once per tab session to drive nav bar hide/show.
    //
    // Why accumulated delta instead of raw diff:
    // Sites with sticky/fixed footer navs sometimes fire a burst of small scroll
    // events (position corrections, IntersectionObserver reflows, etc.) that reset
    // lastScrollY without the user actually scrolling up.  A single-diff check
    // interprets those corrections as "scrolled up → show nav" and the nav never
    // hides.  Accumulating deltas across events means a site-triggered micro-scroll
    // (+3 px) cannot cancel a user's deliberate downward scroll (+80 px).
    LaunchedEffect(activeTab?.session) {
        val session = activeTab?.session ?: return@LaunchedEffect
        var lastScrollY = 0
        var accumulatedDelta = 0  // running sum; reset when direction commits
        session.scrollDelegate = object : org.mozilla.geckoview.GeckoSession.ScrollDelegate {
            override fun onScrollChanged(sess: org.mozilla.geckoview.GeckoSession, scrollX: Int, scrollY: Int) {
                // ── Always pin the nav bar on the home screen ──────────────────
                if (currentShowHomeScreen) {
                    isScrollNavBarVisible = true
                    lastScrollY = scrollY
                    accumulatedDelta = 0
                    return
                }

                if (!isNavHideEnabled) {
                    isScrollNavBarVisible = true
                    lastScrollY = scrollY
                    accumulatedDelta = 0
                    return
                }


                // Always show nav when the page is scrolled back to the very top
                if (scrollY <= 0) {
                    isScrollNavBarVisible = true
                    lastScrollY = 0
                    accumulatedDelta = 0
                    return
                }

                val delta = scrollY - lastScrollY
                lastScrollY = scrollY

                // Ignore near-zero jitter (≤ 4 px) — these are typically layout/
                // reflow corrections fired by sites with sticky footer navs.
                if (delta > -5 && delta < 5) return

                accumulatedDelta += delta

                // Commit hide only after accumulating a meaningful downward scroll
                if (accumulatedDelta > 60) {
                    isScrollNavBarVisible = false
                    accumulatedDelta = 0
                // Commit show only after accumulating a meaningful upward scroll
                } else if (accumulatedDelta < -40) {
                    isScrollNavBarVisible = true
                    accumulatedDelta = 0
                }
            }
        }
    }

    // Add back gesture handler to handle system back clicks safely
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Only intercept back when the browser screen is actually in focus.
    // The video player screen has its own BackHandler that takes priority when it is
    // composed on top, so this handler is only active when the browser is the top destination.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (!showHomeScreen) {
            if (viewModel.canGoBack) {
                // Navigate the active tab's GeckoSession back safely
                try { viewModel.goBack() } catch (e: Exception) {
                    android.util.Log.w("BackHandler", "goBack() error, going home: ${e.message}")
                    viewModel.navigateHomeDirectly()
                }
            } else {
                // No history left – go to home screen without touching session
                viewModel.navigateHomeDirectly()
            }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                showExitConfirmDialog = true
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Tap again to exit Omni Browser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text("Exit Browser?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Text("Are you sure you want to exit Omni Browser?", fontSize = 14.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("No", color = if (viewModel.isDarkThemeEnabled) Color.Gray else Color.DarkGray)
                }
            },
            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1A26) else Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Uncaught exception crash recovery notification dialog
    val crashPrefs = remember { context.getSharedPreferences("omni_crash_prefs", android.content.Context.MODE_PRIVATE) }
    var crashMsg by remember { mutableStateOf(crashPrefs.getString("last_crash_msg", null)) }
    if (crashMsg != null) {
        AlertDialog(
            onDismissRequest = {
                crashPrefs.edit().remove("last_crash_msg").apply()
                crashMsg = null
            },
            title = {
                Text("Auto Recovery", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("Omni Browser recovered from an unexpected error: \n\n$crashMsg\n\nYou can continue browsing safely.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        crashPrefs.edit().remove("last_crash_msg").apply()
                        crashMsg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1A26) else Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !viewModel.isFullscreen && !showHomeScreen,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 350)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Column {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp
                    ) {
                        Column {
                            if (isTablet) {
                                // Tablet Tab Strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF1C1C1E) else Color(0xFFF1F3F4))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabletTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(tabletTabs, key = { it.id }) { tab ->
                                            val isActive = tab.id == viewModel.activeTabId
                                            val tabBg = if (isActive) {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF2C2C2E) else Color.White
                                            } else {
                                                Color.Transparent
                                            }
                                            val tabTextColor = if (isActive) {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White else Color(0xFF202124)
                                            } else {
                                                if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                                            }
                                            
                                            Row(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                    .background(tabBg)
                                                    .clickable { viewModel.selectTab(tab.id) }
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (tab.title.isNullOrBlank()) "New Tab" else tab.title,
                                                    color = tabTextColor,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                
                                                if (tabletTabs.size > 1 || viewModel.isIncognitoMode) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .clickable { viewModel.closeTab(tab.id, context) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Close Tab",
                                                            tint = tabTextColor.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    IconButton(
                                        onClick = { viewModel.createNewTab(context, "about:blank") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "New Tab",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0x1F000000))

                                // Tablet Toolbar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.goBack() },
                                        enabled = viewModel.canGoBack,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (viewModel.canGoBack) (if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)) else (if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.2f) else Color(0x1F000000)),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.goForward() },
                                        enabled = viewModel.canGoForward,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Forward",
                                            tint = if (viewModel.canGoForward) (if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)) else (if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.2f) else Color(0x1F000000)),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.reload() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Reload",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.loadUrl("about:blank") },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = "Home",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isInputFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = "Search icon",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )

                                            BasicTextField(
                                                value = if (inputUrl.text == "about:blank") androidx.compose.ui.text.input.TextFieldValue("") else inputUrl,
                                                onValueChange = { inputUrl = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged { isInputFocused = it.isFocused },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    imeAction = ImeAction.Go
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onGo = {
                                                        viewModel.loadUrl(inputUrl.text)
                                                        focusManager.clearFocus()
                                                        keyboardController?.hide()
                                                    }
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                            )

                                            if (inputUrl.text.isNotEmpty() && inputUrl.text != "about:blank") {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { inputUrl = androidx.compose.ui.text.input.TextFieldValue("") },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Clear",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }

                                            if (viewModel.currentUrl.isNotEmpty() && viewModel.currentUrl != "about:blank" && !isInputFocused) {
                                                val isBookmarked = viewModel.isBookmarked(viewModel.currentUrl)
                                                
                                                // Only show reader toggle button when reader mode is NOT active
                                                // (when active, the dedicated reader config bar at the bottom handles exit)
                                                if (!viewModel.isReaderModeActive) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clickable { viewModel.toggleReaderMode() },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                                            contentDescription = "Reader Mode",
                                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable {
                                                            if (isBookmarked) {
                                                                viewModel.removeBookmark(viewModel.currentUrl)
                                                            } else {
                                                                val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Page"
                                                                viewModel.addToBookmarks(activeTabTitle, viewModel.currentUrl)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        contentDescription = "Bookmark",
                                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isInputFocused) {
                                        TextButton(
                                            onClick = {
                                                inputUrl = androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                        }
                                    }

                                    IconButton(
                                        onClick = { showExtensionsSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            Icon(
                                                imageVector = Icons.Rounded.Extension,
                                                contentDescription = "Extensions",
                                                tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            if (hasActiveUserExtensions) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .offset(x = 1.dp, y = (-1).dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = CircleShape
                                                        )
                                                        .border(1.dp, if (viewModel.isDarkThemeEnabled) Color(0xFF0C1420) else Color.White, CircleShape)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { showToolsSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = BlackholeIcon,
                                            contentDescription = "Tools",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Menu,
                                            contentDescription = "Menu",
                                            tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                // Existing Phone Top Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(visible = !isInputFocused) {
                                        IconButton(
                                            onClick = { viewModel.loadUrl("about:blank") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(id = com.rebelroot.omni.R.drawable.ic_omni_logo),
                                                contentDescription = "Go Home",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isInputFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = "Search icon",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )

                                            BasicTextField(
                                                value = if (inputUrl.text == "about:blank") androidx.compose.ui.text.input.TextFieldValue("") else inputUrl,
                                                onValueChange = { inputUrl = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged { isInputFocused = it.isFocused },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    imeAction = ImeAction.Go
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onGo = {
                                                        viewModel.loadUrl(inputUrl.text)
                                                        focusManager.clearFocus()
                                                        keyboardController?.hide()
                                                    }
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                            )

                                            if (inputUrl.text.isNotEmpty() && inputUrl.text != "about:blank") {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { inputUrl = androidx.compose.ui.text.input.TextFieldValue("") },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Clear",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }

                                            // Bookmark and Reader Mode toggles inside the address bar (when not typing)
                                            if (viewModel.currentUrl.isNotEmpty() && viewModel.currentUrl != "about:blank" && !isInputFocused) {
                                                val isBookmarked = viewModel.isBookmarked(viewModel.currentUrl)
                                                
                                                // Only show reader toggle when NOT already in reader mode
                                                if (!viewModel.isReaderModeActive) {
                                                    // Reader Mode Toggler
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clickable { viewModel.toggleReaderMode() },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                                            contentDescription = "Reader Mode",
                                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                
                                                // Bookmark Star Toggler
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable {
                                                            if (isBookmarked) {
                                                                viewModel.removeBookmark(viewModel.currentUrl)
                                                            } else {
                                                                val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Page"
                                                                viewModel.addToBookmarks(activeTabTitle, viewModel.currentUrl)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        contentDescription = "Bookmark",
                                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = isInputFocused) {
                                        TextButton(
                                            onClick = {
                                                inputUrl = androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl)
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                        }
                                    }

                                    AnimatedVisibility(visible = !isInputFocused) {
                                        IconButton(
                                            onClick = { viewModel.reload() },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Reload",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = !isInputFocused) {
                                        IconButton(
                                            onClick = { showExtensionsSheet = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.TopEnd) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Extension,
                                                    contentDescription = "Extensions",
                                                    tint = MaterialTheme.colorScheme.onBackground
                                                )
                                                if (hasActiveUserExtensions) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .offset(x = 1.dp, y = (-1).dp)
                                                            .background(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = CircleShape
                                                            )
                                                            .border(1.dp, if (viewModel.isDarkThemeEnabled) Color(0xFF0C1420) else Color.White, CircleShape)
                                                    )
                                                 }
                                            }
                                        }
                                    }
                                }
                            }

                            if (viewModel.isLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }

                    val isRestrictedDomain = listOf("youtube.com", "youtu.be", "google.com", "googlevideo.com", "googleusercontent.com").any { viewModel.currentUrl.contains(it, ignoreCase = true) }
                    val nonDrmMedia = if (isRestrictedDomain) emptyList() else detectedMedia.filter { !it.isDrmProtected }
                    val showAlohaBanner = nonDrmMedia.isNotEmpty() && !isAlohaBannerDismissed && !showHomeScreen && !viewModel.isReaderModeActive

                    AnimatedVisibility(
                        visible = showAlohaBanner,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            color = Color(0xFF1B2234)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { isAlohaBannerDismissed = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (viewModel.isVideoPlayingInPage) {
                                    EqualizerIcon(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayCircle,
                                        contentDescription = "Video Detected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = if (viewModel.isVideoPlayingInPage) "Video is playing" else "Video playing detected",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        val firstMedia = nonDrmMedia.firstOrNull()
                                        if (firstMedia != null) {
                                            onPlayOnlineStream(firstMedia.url, viewModel.currentUrl)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play in Premium Player",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (!viewModel.hasSeenVideoOverview) {
                                            pendingVideoAction = { showDownloadSheet = true }
                                            showVideoOverviewDialog = true
                                        } else {
                                            showDownloadSheet = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download Options",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isTablet && !viewModel.isFullscreen && !isInputFocused && !isHomeSearchFocused && (showHomeScreen || isScrollNavBarVisible),
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 350)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                // Flat minimal bottom bar persisting exactly as requested in screenshots
                val isDark = viewModel.isDarkThemeEnabled
                val navBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                val navBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val navContent = if (isDark) Color.White else Color(0xFF1C1C1E)
                val navContentMuted = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF8E8E93)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .drawBehind {
                            drawLine(
                                color = navBorder,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    color = navBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragAmountAccumulated > 100f) {
                                            val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                            // Swiped right -> select previous tab
                                            val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                            if (currentIndex > 0) {
                                                viewModel.selectTab(currentModeTabs[currentIndex - 1].id)
                                            }
                                        } else if (dragAmountAccumulated < -100f) {
                                            // Swiped left -> select next tab
                                            val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                            val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                            if (currentIndex != -1 && currentIndex < currentModeTabs.size - 1) {
                                                viewModel.selectTab(currentModeTabs[currentIndex + 1].id)
                                            }
                                        }
                                        dragAmountAccumulated = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAmountAccumulated += dragAmount
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Back button
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.goBack() },
                                enabled = viewModel.canGoBack,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (viewModel.canGoBack) navContent else navContentMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Left-Center: Forward button
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.goForward() },
                                enabled = viewModel.canGoForward,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (viewModel.canGoForward) navContent else navContentMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Center: Tools
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showToolsSheet = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = BlackholeIcon,
                                    contentDescription = "Tools",
                                    tint = navContent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Right-Center: Tabs counter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTabGroupsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(1.dp, navContent, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                                    color = navContent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right: Menu
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Menu",
                                    tint = navContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->

    // ── Edge-style Grid Menu Bottom Sheet ──────────────────────────────
    if (showMenu) {
        val isDark = viewModel.isDarkThemeEnabled
        val inactiveIconBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF1F3F4)
        val inactiveIconTint = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
        val activeIconTint = MaterialTheme.colorScheme.primary
        val activeIconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFC7C7CC))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Row 1: Primary actions (colored, filled icons) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Bookmarks
                    MenuGridCell(
                        icon = Icons.Rounded.Bookmark,
                        label = "Bookmarks",
                        iconTint = activeIconTint,
                        iconBg = activeIconBg,
                        onClick = {
                            showMenu = false
                            val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Bookmark"
                            viewModel.addToHistory(activeTabTitle, viewModel.currentUrl)
                            Toast.makeText(context, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                        }
                    )
                    // History
                    MenuGridCell(
                        icon = Icons.Rounded.History,
                        label = "History",
                        iconTint = activeIconTint,
                        iconBg = activeIconBg,
                        onClick = { showMenu = false; onOpenHistory() }
                    )
                    // Downloads
                    MenuGridCell(
                        icon = Icons.Rounded.Download,
                        label = "Downloads",
                        iconTint = activeIconTint,
                        iconBg = activeIconBg,
                        onClick = { showMenu = false; onOpenDownloads() }
                    )
                    // Settings
                    MenuGridCell(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        iconTint = activeIconTint,
                        iconBg = activeIconBg,
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                }

                HorizontalDivider(
                    color = if (isDark) Color(0xFF23374A).copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // ── Row 2: Secondary actions ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Incognito (with toggle state)
                    MenuGridCell(
                        icon = if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        label = "Incognito",
                        iconTint = if (viewModel.isIncognitoMode) activeIconTint else inactiveIconTint,
                        iconBg = if (viewModel.isIncognitoMode) activeIconBg else inactiveIconBg,
                        onClick = { viewModel.toggleIncognitoMode(context) }
                    )
                    // Player Settings
                    MenuGridCell(
                        icon = Icons.Rounded.PlayCircle,
                        label = "Player\nSettings",
                        iconTint = inactiveIconTint,
                        iconBg = inactiveIconBg,
                        onClick = {
                            showMenu = false
                            showPlayerSettingsDialog = true
                        }
                    )
                    // Desktop Site
                    MenuGridCell(
                        icon = Icons.Rounded.Computer,
                        label = "Desktop\nSite",
                        iconTint = if (viewModel.isDesktopMode) activeIconTint else inactiveIconTint,
                        iconBg = if (viewModel.isDesktopMode) activeIconBg else inactiveIconBg,
                        onClick = { viewModel.toggleDesktopMode(context) }
                    )
                    // Nav Hide
                    MenuGridCell(
                        icon = Icons.Rounded.OpenInFull,
                        label = if (isNavHideEnabled) "Nav\nHide On" else "Nav\nHide Off",
                        iconTint = if (isNavHideEnabled) activeIconTint else inactiveIconTint,
                        iconBg = if (isNavHideEnabled) activeIconBg else inactiveIconBg,
                        onClick = {
                            showMenu = false
                            val nextEnabled = !isNavHideEnabled
                            isNavHideEnabled = nextEnabled
                            isScrollNavBarVisible = true
                            Toast.makeText(context, if (nextEnabled) "Nav hide enabled" else "Nav hide disabled", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Row 3: Power tools ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Reader Mode
                    MenuGridCell(
                        icon = Icons.Rounded.Book,
                        label = "Reader\nMode",
                        iconTint = inactiveIconTint,
                        iconBg = inactiveIconBg,
                        onClick = {
                            showMenu = false
                            try { viewModel.toggleReaderMode() } catch (e: Exception) {
                                Toast.makeText(context, "Reader Mode not supported on this page", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    // Auto-Scroll
                    MenuGridCell(
                        icon = Icons.Rounded.ArrowDownward,
                        label = "Auto-Scroll",
                        iconTint = if (isAutoScrollActive) activeIconTint else inactiveIconTint,
                        iconBg = if (isAutoScrollActive) activeIconBg else inactiveIconBg,
                        onClick = {
                            showMenu = false
                            if (showHomeScreen || activeTab == null) {
                                Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                            } else {
                                isAutoScrollActive = !isAutoScrollActive
                            }
                        }
                    )
                    // Burn Data
                    MenuGridCell(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Burn\nData",
                        iconTint = Color(0xFFFF4444),
                        iconBg = Color(0xFFFF4444).copy(alpha = 0.12f),
                        onClick = {
                            showMenu = false
                            coroutineScope.launch {
                                val runtime = viewModel.getGeckoRuntime(context)
                                FireButton(runtime, context).burn()
                                Toast.makeText(context, "🔥 Browsing data burned", Toast.LENGTH_SHORT).show()
                                viewModel.loadUrl("about:blank")
                            }
                        }
                    )
                    // Extensions
                    MenuGridCell(
                        icon = Icons.Rounded.Extension,
                        label = "Extensions",
                        iconTint = inactiveIconTint,
                        iconBg = inactiveIconBg,
                        onClick = {
                            showMenu = false
                            if (!viewModel.hasSeenExtensionsOverview) {
                                pendingExtensionsAction = { showExtensionsSheet = true }
                                showExtensionsOverviewDialog = true
                            } else {
                                showExtensionsSheet = true
                            }
                        }
                    )
                }
            }
        }
    }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (viewModel.isDarkThemeEnabled) Color(0xFF0B0B0C) else Color(0xFFFFFFFF))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Use a single, shared GeckoView component to avoid rendering lag, memory overhead, and multiple compositor threads
                if (activeTab != null && !showHomeScreen) {
                    DisposableEffect(Unit) {
                        onDispose {
                            viewModel.clearActiveGeckoView()
                        }
                    }

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            object : GeckoView(ctx) {
                                override fun onDetachedFromWindow() {
                                    releaseSession()
                                    super.onDetachedFromWindow()
                                }
                            }.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                val runtime = viewModel.getGeckoRuntime(ctx)
                                if (!activeTab.session.isOpen) {
                                    activeTab.session.open(runtime)
                                }
                                setSession(activeTab.session)
                                activeTab.session.setActive(true)
                                viewModel.setActiveGeckoView(this)
                                
                                // Scroll delegate is set via LaunchedEffect below; skip here to avoid duplicate assignment on recomposition
                            }
                        },
                        update = { geckoView ->
                            val runtime = viewModel.getGeckoRuntime(geckoView.context)
                            if (!activeTab.session.isOpen) {
                                activeTab.session.open(runtime)
                            }
                            if (geckoView.session != activeTab.session) {
                                geckoView.releaseSession()
                                geckoView.setSession(activeTab.session)
                            }
                            activeTab.session.setActive(true)
                            viewModel.setActiveGeckoView(geckoView)
                            
                            // Scroll delegate is managed via LaunchedEffect; no inline assignment needed
                        },
                        onRelease = { geckoView ->
                            geckoView.releaseSession()
                            viewModel.clearActiveGeckoView()
                        }
                    )

                    RainbowScanBorder(isScanning = viewModel.isQrScanning)
                    
                    activeTab.loadError?.let { errorMsg ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (viewModel.isDarkThemeEnabled) Color(0xFF0B0B0C) else Color(0xFFFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                colors = CardDefaults.cardColors(containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color.White),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF4444),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Unable to Load Page",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorMsg,
                                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.reload() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Key on tab ID only — NOT the full TabState data class.
                    // TabState.copy() is called on every title/url/canGoBack change,
                    // which would cause the old key to dispose (setActive(false)) and
                    // immediately re-enter (setActive(true)), creating a rapid
                    // deactivate→reactivate cycle that freezes the compositor,
                    // drops network packets, and stalls cookie/JS processing.
                    DisposableEffect(activeTab.id) {
                        onDispose {
                            // Only deactivate when we truly leave this tab
                            viewModel.tabs.find { it.id == activeTab.id }?.session?.setActive(false)
                        }
                    }

                    // Lifecycle-aware session management:
                    // GeckoView requires setActive(false) on pause and setActive(true)
                    // on resume to properly suspend/resume its internal compositor,
                    // network stack, and JS engine. Without this, returning from
                    // background causes pages to appear frozen until a user interaction
                    // triggers a Compose recomposition.
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    DisposableEffect(activeTab.id, lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            val currentSession = viewModel.tabs.find { it.id == activeTab.id }?.session
                            when (event) {
                                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                    currentSession?.setActive(true)
                                }
                                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                    currentSession?.setActive(false)
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                }

                if (showHomeScreen) {
                    HomeScreenContent(
                        viewModel = viewModel,
                        onOpenDownloads = onOpenDownloads,
                        onOpenHistory = onOpenHistory,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenLocker = onOpenLocker,
                        onOpenQrTools = {
                            if (!viewModel.hasSeenQrOverview) {
                                pendingQrAction = onOpenQrTools
                                showQrOverviewDialog = true
                            } else {
                                onOpenQrTools()
                            }
                        },
                        onOpenExtensions = {
                            if (!viewModel.hasSeenExtensionsOverview) {
                                pendingExtensionsAction = { showExtensionsSheet = true }
                                showExtensionsOverviewDialog = true
                            } else {
                                showExtensionsSheet = true
                            }
                        },
                        onOpenTranslator = {
                            translationSourceText = ""
                            translationResultText = ""
                            showTranslationDialog = true
                        },
                        onOpenConsole = {
                            if (!viewModel.hasSeenConsoleOverview) {
                                pendingConsoleAction = { showConsoleSheet = true }
                                showConsoleOverviewDialog = true
                            } else {
                                showConsoleSheet = true
                            }
                        },
                        onNavigateTo = { query ->
                            viewModel.loadUrl(query)
                        },
                        onFocusChanged = { isHomeSearchFocused = it }
                    )
                }

                // Auto-Scroll HUD Pill
                if (isAutoScrollActive && activeTab != null && !showHomeScreen && !viewModel.isReaderModeActive && isAutoScrollHUDExpanded) {
                    var showSpeedSlider by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFE3F2FD))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .combinedClickable(
                                onClick = {
                                    isAutoScrollPaused = !isAutoScrollPaused
                                },
                                onLongClick = {
                                    showSpeedSlider = !showSpeedSlider
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoScrollPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (isAutoScrollPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { isAutoScrollPaused = !isAutoScrollPaused }
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )
                                
                                Text(
                                    text = "Scroll ${autoScrollSpeed}x",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Collapse Auto Scroll",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            isAutoScrollHUDExpanded = false
                                        }
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )

                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close Auto Scroll",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            isAutoScrollActive = false
                                            isAutoScrollPaused = false
                                        }
                                )
                            }

                            if (showSpeedSlider) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.width(180.dp)
                                ) {
                                    Text("1x", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Slider(
                                        value = autoScrollSpeed.toFloat(),
                                        onValueChange = { autoScrollSpeed = it.toInt().coerceIn(1, 5) },
                                        valueRange = 1f..5f,
                                        steps = 3,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("5x", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                
                // Collapsed Auto-Scroll HUD Indicator
                if (isAutoScrollActive && activeTab != null && !showHomeScreen && !viewModel.isReaderModeActive && !isAutoScrollHUDExpanded) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp, end = 16.dp)
                            .size(36.dp)
                            .clickable { isAutoScrollHUDExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFE3F2FD)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAutoScrollPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "Expand Auto Scroll",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
                
                // Bottom edge touch detector to restore nav bars when hidden
                if (!isScrollNavBarVisible && !viewModel.isFullscreen && !showHomeScreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(20.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                isScrollNavBarVisible = true
                            }
                    )
                }
            }

            // ─── Reader Mode Configuration Bar ─────────────────────────────────────
            if (viewModel.isReaderModeActive && activeTab != null && !showHomeScreen) {
                if (isReaderSettingsExpanded) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = if (isTablet) 16.dp else 72.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFFAFAFA)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // ── Header Row ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Reader View",
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (viewModel.isTtsPlaying)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable {
                                            if (viewModel.isTtsPlaying) viewModel.stopTts()
                                            else viewModel.readAloudCurrentPage()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (viewModel.isTtsPlaying) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.RecordVoiceOver,
                                                contentDescription = "Read Aloud",
                                                tint = if (viewModel.isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (viewModel.isTtsPlaying) "Stop" else "Listen",
                                                color = if (viewModel.isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { isReaderSettingsExpanded = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Collapse Reader Controls",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { viewModel.toggleReaderMode() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Exit Reader Mode",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.decreaseReaderFontSize() }
                                    ) {
                                        Text(
                                            text = "A−",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    Text(
                                        text = "${viewModel.readerFontSize}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.increaseReaderFontSize() }
                                    ) {
                                        Text(
                                            text = "A+",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatLineSpacing,
                                        contentDescription = "Line spacing",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.decreaseReaderLineHeight() }
                                    ) {
                                        Text(
                                            text = "−",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    Text(
                                        text = String.format("%.1f", viewModel.readerLineHeight),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1A1A1A),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.increaseReaderLineHeight() }
                                    ) {
                                        Text(
                                            text = "+",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    listOf("Light", "Sepia", "Dark").forEach { theme ->
                                        val isSelected = viewModel.readerTheme == theme
                                        val (themeBg, themeFg) = when (theme) {
                                            "Sepia" -> Color(0xFFF4ECD8) to Color(0xFF5B4636)
                                            "Dark" -> Color(0xFF1E1E1E) to Color(0xFFE0E0E0)
                                            else -> Color.White to Color(0xFF1A1A1A)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(width = 46.dp, height = 26.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(themeBg)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.setReaderThemeMode(theme) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = theme,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else themeFg,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Font",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )
                                listOf("System", "Serif", "Sans", "Mono").forEach { family ->
                                    val isSelected = viewModel.readerFontFamily == (if (family == "Sans") "Sans-Serif" else if (family == "Mono") "Monospace" else family)
                                    val vmFamily = when (family) {
                                        "Sans" -> "Sans-Serif"
                                        "Mono" -> "Monospace"
                                        else -> family
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.updateReaderFontFamily(vmFamily) }
                                    ) {
                                        Text(
                                            text = family,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Width",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )
                                listOf("Narrow", "Medium", "Wide").forEach { w ->
                                    val isSelected = viewModel.readerWidth == w
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.updateReaderWidth(w) }
                                    ) {
                                        Text(
                                            text = w,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Space",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (viewModel.readerLetterSpacing != "Normal") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val next = when (viewModel.readerLetterSpacing) {
                                                "Normal" -> "Wide"
                                                "Wide" -> "Very Wide"
                                                else -> "Normal"
                                            }
                                            viewModel.updateReaderLetterSpacing(next)
                                        }
                                ) {
                                    Text(
                                        text = "Letter: ${viewModel.readerLetterSpacing}",
                                        fontSize = 10.sp,
                                        fontWeight = if (viewModel.readerLetterSpacing != "Normal") FontWeight.Bold else FontWeight.Normal,
                                        color = if (viewModel.readerLetterSpacing != "Normal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (viewModel.readerWordSpacing != "Normal") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val next = when (viewModel.readerWordSpacing) {
                                                "Normal" -> "Wide"
                                                "Wide" -> "Very Wide"
                                                else -> "Normal"
                                            }
                                            viewModel.updateReaderWordSpacing(next)
                                        }
                                ) {
                                    Text(
                                        text = "Word: ${viewModel.readerWordSpacing}",
                                        fontSize = 10.sp,
                                        fontWeight = if (viewModel.readerWordSpacing != "Normal") FontWeight.Bold else FontWeight.Normal,
                                        color = if (viewModel.readerWordSpacing != "Normal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Align",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )
                                listOf("Left", "Justify").forEach { align ->
                                    val isSelected = (align == "Justify" && viewModel.readerJustified) || (align == "Left" && !viewModel.readerJustified)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.toggleReaderJustified() }
                                    ) {
                                        Text(
                                            text = align,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = if (isTablet) 16.dp else 72.dp, end = 16.dp)
                            .size(36.dp)
                            .clickable { isReaderSettingsExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFE3F2FD)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = "Expand Reader Controls",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }


            // ─── Unified smart download button ─────────────────────────────────────
            // • Fullscreen: fades while playing, stays / reappears while paused or on tap
            val isRestrictedDomain = listOf("youtube.com", "youtu.be", "google.com", "googlevideo.com", "googleusercontent.com").any { viewModel.currentUrl.contains(it, ignoreCase = true) }
            val nonDrmMedia = if (isRestrictedDomain) emptyList() else detectedMedia.filter { !it.isDrmProtected }
            if (nonDrmMedia.isNotEmpty() && !showHomeScreen && !viewModel.isReaderModeActive) {
                if (viewModel.isFullscreen) {
                    // Fullscreen mode — overlay with auto-fade controls
                    // Transparent tap-catcher; restores controls on any tap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showFullscreenDownloadBtn = true }
                    ) {
                        // Top-left controls: Back + Exit Fullscreen
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .safeDrawingPadding()
                                .padding(start = 12.dp, top = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back to browser button
                                IconButton(
                                    onClick = { viewModel.goBack() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // Exit fullscreen button — tells GeckoView to exit fullscreen
                                IconButton(
                                    onClick = {
                                        activeTab?.session?.exitFullScreen()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Bottom-right controls: Play FAB + Download FAB
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 32.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                val firstMedia = nonDrmMedia.firstOrNull()
                                if (firstMedia != null) {
                                    FloatingActionButton(
                                        onClick = {
                                            onPlayOnlineStream(firstMedia.url, viewModel.currentUrl)
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play in Premium Player",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                FloatingActionButton(
                                    onClick = {
                                        if (!viewModel.hasSeenVideoOverview) {
                                            pendingVideoAction = { showDownloadSheet = true }
                                            showVideoOverviewDialog = true
                                        } else {
                                            showDownloadSheet = true
                                        }
                                    },
                                    containerColor = Color.Black.copy(alpha = 0.78f),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // ───────────────────────────────────────────────────────────────────────

            // Context Menu Bottom Sheet
            val activeContextMenu = viewModel.activeContextMenu
            if (activeContextMenu != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissContextMenu() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color.White,
                    contentColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0)) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        // Header
                        val titleText = when {
                            !activeContextMenu.srcUri.isNullOrEmpty() -> "Image Option"
                            !activeContextMenu.linkUri.isNullOrEmpty() -> "Link Option"
                            else -> "Page Option"
                        }
                        val subtitleText = activeContextMenu.srcUri ?: activeContextMenu.linkUri ?: ""
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = titleText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                            )
                            if (subtitleText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitleText,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFF0F0F0),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Options
                        if (!activeContextMenu.srcUri.isNullOrEmpty()) {
                            val imageUrl = activeContextMenu.srcUri
                            // Google Lens Search Option
                            ContextMenuItem(
                                icon = Icons.Rounded.CameraAlt,
                                title = "Search image with Google Lens",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val encodedUrl = android.net.Uri.encode(imageUrl)
                                    val lensUrl = "https://lens.google.com/uploadbyurl?url=$encodedUrl"
                                    viewModel.createNewTab(context, lensUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Google Lens App Intent
                            ContextMenuItem(
                                icon = Icons.Rounded.CameraAlt,
                                title = "Search with Google Lens App",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    try {
                                        val intent = android.content.Intent("com.google.lens.intent.action.LENS_INPUT").apply {
                                            setPackage("com.google.android.googlequicksearchbox")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Google Lens app not installed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Open in New Tab
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                title = "Open image in new tab",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    viewModel.createNewTab(context, imageUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Copy Image Link
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy image link",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Image Link", imageUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Image link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                        }

                        if (!activeContextMenu.linkUri.isNullOrEmpty()) {
                            val linkUrl = activeContextMenu.linkUri
                            // Open Link in New Tab
                            ContextMenuItem(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                title = "Open link in new tab",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    viewModel.createNewTab(context, linkUrl)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Copy Link Address
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy link address",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Link Address", linkUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link address copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            // Share Link
                            ContextMenuItem(
                                icon = Icons.Rounded.Share,
                                title = "Share link",
                                onClick = {
                                    viewModel.dismissContextMenu()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, linkUrl)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    val chooserIntent = Intent.createChooser(shareIntent, "Share link").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(chooserIntent)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                        }
                    }
                }
            }

            // Text Selection Bottom Sheet
            val activeTextSelection = viewModel.activeTextSelection
            if (activeTextSelection != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissTextSelection() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color.White,
                    contentColor = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0)) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        // Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Text Selection",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeTextSelection,
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.6f) else Color(0xFF606266)
                            )
                        }
                        
                        HorizontalDivider(
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF2A3C50) else Color(0xFFE0E0E0),
                            thickness = 1.dp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Options
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy Option
                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                title = "Copy",
                                onClick = {
                                    viewModel.copySelectedText(context)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )
                            
                            // Share Option
                            ContextMenuItem(
                                icon = Icons.Rounded.Share,
                                title = "Share",
                                onClick = {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, activeTextSelection)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share text"))
                                    viewModel.dismissTextSelection()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            // Speak Aloud Option
                            ContextMenuItem(
                                icon = Icons.Rounded.RecordVoiceOver,
                                title = "Speak Aloud",
                                onClick = {
                                    viewModel.speakSelectedText(context)
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                            
                            // Select All Option
                            ContextMenuItem(
                                icon = Icons.Rounded.SelectAll,
                                title = "Select All",
                                onClick = {
                                    viewModel.selectAllText()
                                },
                                isDark = viewModel.isDarkThemeEnabled
                            )

                        }
                    }
                }
            }

            // Bottom options sheet for video downloading
            if (showDownloadSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Download Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(nonDrmMedia) { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (item.type == com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Quality: ${item.quality ?: "Auto/Source"}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = item.type.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    onPlayOnlineStream(item.url, viewModel.currentUrl)
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = "Video-${System.currentTimeMillis()}",
                                                            type = item.type,
                                                            saveToLocker = false,
                                                            referrerUrl = viewModel.currentUrl
                                                        )
                                                        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = "Video-${System.currentTimeMillis()}",
                                                            type = item.type,
                                                            saveToLocker = true,
                                                            referrerUrl = viewModel.currentUrl
                                                        )
                                                        Toast.makeText(context, "Downloading to Locker...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Locker", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Offline translation dialog card overlay
            if (showTranslationDialog) {
                val languages = listOf(
                    "English" to "en",
                    "Spanish" to "es",
                    "French" to "fr",
                    "German" to "de",
                    "Chinese" to "zh",
                    "Hindi" to "hi",
                    "Arabic" to "ar",
                    "Russian" to "ru",
                    "Portuguese" to "pt",
                    "Japanese" to "ja",
                    "Italian" to "it",
                    "Turkish" to "tr",
                    "Korean" to "ko",
                    "Vietnamese" to "vi"
                )

                AlertDialog(
                    onDismissRequest = { showTranslationDialog = false; viewModel.translationManager.close() },
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White,
                    title = {
                        Text(
                            text = "🌐 Site & Text Translator",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // --- Whole Page Translation Card ---
                            val currentUrl = viewModel.currentUrl
                            val canTranslatePage = !showHomeScreen && activeTab != null && 
                                    (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Translate Webpage",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )
                                    
                                    if (canTranslatePage) {
                                        Text(
                                            text = "Translate the entire active webpage using Google Web Translation proxy.",
                                            fontSize = 11.sp,
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else Color(0xFF64748B)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Target Language:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                            )
                                            LanguageDropdownSelector(
                                                label = "Target",
                                                selectedLanguageName = selectedPageTargetLang.first,
                                                expanded = showPageTargetLangMenu,
                                                onExpandedChange = { showPageTargetLangMenu = it },
                                                languages = languages,
                                                onLanguageSelected = { selectedPageTargetLang = it }
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                                                    try {
                                                        val encodedUrl = java.net.URLEncoder.encode(currentUrl, "UTF-8")
                                                        val targetLang = selectedPageTargetLang.second
                                                        val translateUrl = "https://translate.google.com/translate?sl=auto&tl=$targetLang&u=$encodedUrl"
                                                        viewModel.loadUrl(translateUrl)
                                                        showTranslationDialog = false
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Translator", "Failed to encode URL", e)
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Translate Entire Page", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text(
                                            text = "Open any webpage to enable whole-site translation.",
                                            fontSize = 12.sp,
                                            color = Color.Red.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // --- Text Translation Card ---
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Translate Custom Text",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LanguageDropdownSelector(
                                            label = "Source",
                                            selectedLanguageName = selectedSourceLang.first,
                                            expanded = showSourceLangMenu,
                                            onExpandedChange = { showSourceLangMenu = it },
                                            languages = languages,
                                            onLanguageSelected = { selectedSourceLang = it }
                                        )
                                        Text(
                                            text = "➔",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                        LanguageDropdownSelector(
                                            label = "Target",
                                            selectedLanguageName = selectedTargetLang.first,
                                            expanded = showTargetLangMenu,
                                            onExpandedChange = { showTargetLangMenu = it },
                                            languages = languages,
                                            onLanguageSelected = { selectedTargetLang = it }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = translationSourceText,
                                        onValueChange = { translationSourceText = it },
                                        placeholder = { Text("Type text to translate...", color = Color.Gray) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black),
                                        modifier = Modifier.fillMaxWidth().height(90.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    if (translationProgress) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "Downloading offline model...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (translationResultText.isNotEmpty()) {
                                        Surface(
                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = translationResultText,
                                                fontSize = 14.sp,
                                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            translationProgress = true
                                            viewModel.translationManager.setupLanguage(
                                                selectedSourceLang.second,
                                                selectedTargetLang.second
                                            ) {
                                                coroutineScope.launch {
                                                    try {
                                                        translationResultText = viewModel.translationManager.translateText(translationSourceText)
                                                    } catch (e: Exception) {
                                                        translationResultText = "Translation failed: ${e.message}"
                                                    } finally {
                                                        translationProgress = false
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Translate Text", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showTranslationDialog = false
                                viewModel.translationManager.close()
                            }
                        ) {
                            Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // 2. Premium Grid Tab Windows Switcher Tray Bottom Sheet
            if (showTabGroupsSheet) {
                val currentModeTabs = remember(viewModel.tabs.toList(), viewModel.isIncognitoMode) {
                    viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                }
                ModalBottomSheet(
                    onDismissRequest = { showTabGroupsSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF070A0F) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.isIncognitoMode) "Incognito Tabs (${currentModeTabs.size})" else "Open Tabs (${currentModeTabs.size})",
                                color = if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            TextButton(
                                onClick = {
                                    showTabGroupsSheet = false
                                    viewModel.createNewTab(context, "about:blank")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Tab",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Premium Mode Toggle Capsule Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF1E2D3F) else MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val normalCount = viewModel.tabs.count { !it.isIncognito }
                            val privateCount = viewModel.tabs.count { it.isIncognito }

                            // Normal tab option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (!viewModel.isIncognitoMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        if (viewModel.isIncognitoMode) {
                                            viewModel.toggleIncognitoMode(context)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Normal ($normalCount)",
                                    color = if (!viewModel.isIncognitoMode) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Private tab option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (viewModel.isIncognitoMode) Color(0xFFFF3B5C) else Color.Transparent)
                                    .clickable {
                                        if (!viewModel.isIncognitoMode) {
                                            viewModel.toggleIncognitoMode(context)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Incognito ($privateCount)",
                                    color = if (viewModel.isIncognitoMode) Color.White else (if (viewModel.isDarkThemeEnabled || viewModel.isIncognitoMode) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        val tabChunks = remember(currentModeTabs) { currentModeTabs.chunked(2) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            items(tabChunks) { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (tab in chunk) {
                                        val isActive = tab.id == viewModel.activeTabId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (viewModel.isDarkThemeEnabled) Color(0xFF16222F) else MaterialTheme.colorScheme.surfaceVariant)
                                                .border(
                                                    BorderStroke(
                                                        if (isActive) 1.5.dp else 0.5.dp,
                                                        if (isActive) MaterialTheme.colorScheme.primary else (if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.selectTab(tab.id)
                                                    showTabGroupsSheet = false
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Header row of the tab window card
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Language,
                                                            contentDescription = null,
                                                            tint = if (isActive) MaterialTheme.colorScheme.primary else (if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") "New Tab" else tab.title,
                                                            color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Box(
                                                         modifier = Modifier
                                                             .size(24.dp)
                                                             .clip(CircleShape)
                                                             .background(if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
                                                             .clickable {
                                                                 viewModel.closeTab(tab.id, context)
                                                             },
                                                         contentAlignment = Alignment.Center
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Rounded.Close,
                                                             contentDescription = "Close Tab",
                                                             tint = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                             modifier = Modifier.size(12.dp)
                                                         )
                                                     }
                                                }

                                                // Webpage Preview Placeholder Box (Chrome / Opera style)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(84.dp)
                                                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = if (viewModel.isDarkThemeEnabled) {
                                                                    listOf(Color(0xFF1E2D3F), Color(0xFF0F1B26))
                                                                } else {
                                                                    listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                                                }
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    // Show snapshot / reference image using high-res favicon
                                                    if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                                        coil.compose.AsyncImage(
                                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                                .data("https://www.google.com/s2/favicons?domain=${tab.url}&sz=128")
                                                                .size(96, 96)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = "Site Thumbnail",
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Explore,
                                                            contentDescription = null,
                                                            tint = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    }

                                                    // Tiny URL overlay at the bottom of the card preview
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(6.dp),
                                                        contentAlignment = Alignment.BottomStart
                                                    ) {
                                                        Text(
                                                            text = if (tab.url == "about:blank") "about:blank" else tab.url,
                                                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (chunk.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Web Extensions Manager Bottom Sheet
            if (showConsoleSheet) {
                var jsInputText by remember { mutableStateOf("") }
                ModalBottomSheet(
                    onDismissRequest = { showConsoleSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ">_ Developer Console",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { viewModel.consoleLogs.clear() }) {
                                Text("Clear", color = Color(0xFFFF5555))
                            }
                        }

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(if (viewModel.isDarkThemeEnabled) Color(0xFF05080C) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .border(1.dp, if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.consoleLogs.toList(), key = { "${it.timestamp}_${it.message.hashCode()}" }) { log ->
                                val color = when (log.level) {
                                    "ERROR" -> Color(0xFFFF5555)
                                    "WARN" -> Color(0xFFFFB86C)
                                    "INFO" -> if (viewModel.isDarkThemeEnabled) Color(0xFF8BE9FD) else Color(0xFF0284C7)
                                    else -> if (viewModel.isDarkThemeEnabled) Color(0xFFF8F8F2) else Color(0xFF1E293B)
                                }
                                val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                val timeStr = formatter.format(java.util.Date(log.timestamp))
                                
                                Text(
                                    text = "[$timeStr] ${log.level}: ${log.message}",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = color,
                                    lineHeight = 14.sp
                                )
                                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = jsInputText,
                                onValueChange = { jsInputText = it },
                                placeholder = { Text("console.log('Hello');", fontSize = 14.sp) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else Color.LightGray
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSend = {
                                        if (jsInputText.isNotBlank()) {
                                            viewModel.pendingJsCommand = jsInputText
                                            jsInputText = ""
                                        }
                                    }
                                )
                            )
                            androidx.compose.material3.Button(
                                onClick = {
                                    if (jsInputText.isNotBlank()) {
                                        viewModel.pendingJsCommand = jsInputText
                                        jsInputText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Run", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 4. Web Extensions Manager Bottom Sheet
            if (showExtensionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showExtensionsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
                ) {
                    // Sync user extensions safely when sheet opens
                    LaunchedEffect(Unit) {
                        try {
                            viewModel.syncUserExtensions()
                        } catch (_: Exception) { /* ignore sync errors */ }
                    }

                    // Take a stable snapshot to avoid ConcurrentModificationException
                    val userExts = remember(viewModel.userExtensions.toList()) {
                        viewModel.userExtensions.toList()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Extensions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = {
                                    showExtensionsSheet = false
                                    viewModel.createNewTab(context, "https://addons.mozilla.org/en-US/android/")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "Browse Store",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Store", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))

                        // Extension Item 1: uBlock Origin
                        ExtensionItemCard(
                            icon = Icons.Rounded.Shield,
                            name = "uBlock Origin",
                            author = "Raymond Hill (gorhill)",
                            description = "An efficient wide-spectrum content blocker. Easy on CPU and memory.",
                            checked = viewModel.isAdblockerEnabled,
                            enabled = !viewModel.isAdblockerToggling,
                            onCheckedChange = { viewModel.toggleAdblock(context) }
                        )

                        // Extension Item 2: Universal Text Copy
                        ExtensionItemCard(
                            icon = Icons.Rounded.FileCopy,
                            name = "Universal Text Copy",
                            author = "Omni Browser Team",
                            description = "Bypass website restrictions to force-enable text selection and copying.",
                            checked = viewModel.isUniversalCopyEnabled,
                            enabled = !viewModel.isUniversalCopyToggling,
                            onCheckedChange = { viewModel.toggleUniversalCopy(context) }
                        )

                        // Extension Item 3: Media Grabber
                        ExtensionItemCard(
                            icon = Icons.Rounded.Download,
                            name = "Aggressive Media Grabber",
                            author = "Omni Browser Team",
                            description = "Sniff and capture offline dynamic HLS/DASH dynamic segments and streams.",
                            checked = viewModel.isMediaGrabberEnabled,
                            enabled = !viewModel.isMediaGrabberToggling,
                            onCheckedChange = { viewModel.toggleMediaGrabber(context) }
                        )

                        // Extension Item 4: AI Blocker
                        ExtensionItemCard(
                            icon = Icons.Rounded.Block,
                            name = "AI Blocker",
                            author = "Omni Browser Team",
                            description = "Completely block AI Overview summaries and assistant panels on search engines.",
                            checked = viewModel.isAiBlockerEnabled,
                            enabled = !viewModel.isAiBlockerToggling,
                            onCheckedChange = { viewModel.toggleAiBlocker(context) }
                        )

                        // User-installed Extensions Title and Cards
                        if (userExts.isNotEmpty()) {
                            HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))
                            Text(
                                text = "Installed Extensions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            for (ext in userExts) {
                                val isEnabled = ext.metaData.enabled
                                val optionsUrl = try { ext.metaData?.optionsPageUrl } catch (_: Exception) { null }
                                val extDisplayName = remember(ext.id) {
                                    val name = try { ext.metaData?.name } catch (_: Exception) { null }
                                    if (!name.isNullOrBlank()) name
                                    else ext.id.substringBefore("@").replace("-", " ")
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                                }
                                UserExtensionItemCard(
                                    extension = ext,
                                    checked = isEnabled,
                                    enabled = !viewModel.togglingUserExtensionIds.contains(ext.id),
                                    onCheckedChange = { viewModel.toggleUserExtension(ext, context) },
                                    onUninstall = { viewModel.uninstallUserExtension(ext, context) },
                                    onOptionsClick = if (!optionsUrl.isNullOrBlank()) {
                                        {
                                            showExtensionsSheet = false
                                            viewModel.loadUrl(optionsUrl)
                                        }
                                    } else null,
                                    onPopupClick = if (viewModel.extensionActions.containsKey(ext.id) || !ext.metaData?.optionsPageUrl.isNullOrBlank()) {
                                        {
                                            showExtensionsSheet = false
                                            val action = viewModel.extensionActions[ext.id]
                                            if (action != null) {
                                                action.click()
                                            } else {
                                                val optionsUrl = ext.metaData?.optionsPageUrl
                                                if (!optionsUrl.isNullOrBlank()) {
                                                    viewModel.loadUrl(optionsUrl)
                                                }
                                            }
                                        }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 4b. Extension Popup / Composer Sheet
            // Opens the extension's browser-action popup (moz-extension://…/popup.html)
            // so users can interact with it (e.g. change VPN server) without leaving the browser.
            if (viewModel.activeExtensionPopupSession != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissExtensionPopup() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = viewModel.activeExtensionPopupName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Extension Interaction",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.dismissExtensionPopup() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // The actual extension popup WebView
                        AndroidView(
                            factory = { ctx ->
                                org.mozilla.geckoview.GeckoView(ctx).apply {
                                    setSession(viewModel.activeExtensionPopupSession!!)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 5. Quick Tools Bottom Sheet
            if (showToolsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showToolsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Quick Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))

                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 4
                        ) {
                            val cardModifier = Modifier.width(82.dp)
                            val isDark = viewModel.isDarkThemeEnabled

                            ToolCard(title = "QR Scanner", icon = Icons.Rounded.QrCodeScanner, isDarkTheme = isDark, modifier = cardModifier) {
                                showToolsSheet = false
                                if (!viewModel.hasSeenQrOverview) {
                                    pendingQrAction = onOpenQrTools
                                    showQrOverviewDialog = true
                                } else {
                                    onOpenQrTools()
                                }
                            }
                            ToolCard(title = "Safe Locker", icon = Icons.Rounded.Lock, isDarkTheme = isDark, modifier = cardModifier) {
                                showToolsSheet = false
                                onOpenLocker()
                            }
                            ToolCard(title = "Translator", icon = Icons.Rounded.Translate, isDarkTheme = isDark, modifier = cardModifier) {
                                showToolsSheet = false
                                translationSourceText = ""
                                translationResultText = ""
                                showTranslationDialog = true
                            }
                            val isEditing = activeTab?.isEditModeEnabled ?: false
                            ToolCard(title = if (isEditing) "Stop Edit" else "Edit Page", icon = Icons.Rounded.Edit, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    if (!viewModel.hasSeenEditPageOverview) {
                                        pendingEditPageAction = { viewModel.toggleEditMode() }
                                        showEditPageOverviewDialog = true
                                    } else {
                                        viewModel.toggleEditMode()
                                    }
                                }
                            }
                            ToolCard(title = "Save PDF", icon = Icons.Rounded.Print, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    if (!viewModel.hasSeenPdfOverview) {
                                        pendingPdfAction = { viewModel.printCurrentPage(context) }
                                        showPdfOverviewDialog = true
                                    } else {
                                        viewModel.printCurrentPage(context)
                                    }
                                }
                            }
                            ToolCard(title = "Pin Web App", icon = Icons.AutoMirrored.Rounded.OpenInNew, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    viewModel.installWebAppShortcut(context, activeTab.title, activeTab.url)
                                }
                            }
                            ToolCard(title = "Auto-Scroll", icon = Icons.Rounded.ArrowDownward, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    isAutoScrollActive = !isAutoScrollActive
                                }
                            }
                            ToolCard(title = "QR Scan Page", icon = Icons.Rounded.CenterFocusWeak, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    if (!viewModel.hasSeenQrOverview) {
                                        pendingQrAction = { viewModel.scanPageForQrCodes() }
                                        showQrOverviewDialog = true
                                    } else {
                                        viewModel.scanPageForQrCodes()
                                    }
                                }
                            }
                            ToolCard(title = "QR Generator", icon = Icons.Rounded.QrCode2, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    if (!viewModel.hasSeenQrOverview) {
                                        pendingQrAction = {
                                            qrGeneratorUrl = activeTab.url
                                            showQrGeneratorDialog = true
                                        }
                                        showQrOverviewDialog = true
                                    } else {
                                        qrGeneratorUrl = activeTab.url
                                        showQrGeneratorDialog = true
                                    }
                                }
                            }

                            ToolCard(title = "Console Log", icon = Icons.Rounded.Terminal, isDarkTheme = isDark, modifier = cardModifier) {
                                showToolsSheet = false
                                if (!viewModel.hasSeenConsoleOverview) {
                                    pendingConsoleAction = { showConsoleSheet = true }
                                    showConsoleOverviewDialog = true
                                } else {
                                    showConsoleSheet = true
                                }
                            }
                            ToolCard(title = "Dev Notes", icon = Icons.Rounded.Description, isDarkTheme = isDark, modifier = cardModifier) {
                                showToolsSheet = false
                                if (!viewModel.hasSeenDevNotesOverview) {
                                    pendingDevNotesAction = { showDevNotesSheet = true }
                                    showDevNotesOverviewDialog = true
                                } else {
                                    showDevNotesSheet = true
                                }
                            }
                            ToolCard(title = "Site Style", icon = Icons.Rounded.Palette, isDarkTheme = isDark, modifier = cardModifier) {
                                if (showHomeScreen || activeTab == null) {
                                    Toast.makeText(context, "Open a webpage first to use this tool", Toast.LENGTH_SHORT).show()
                                } else {
                                    showToolsSheet = false
                                    showSiteStyleCustomizerSheet = true
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            
            // 6. Dev Notes & Vault Bottom Sheet
            if (showDevNotesSheet) {
                DevNotesSheetContent(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    onDismissRequest = { showDevNotesSheet = false }
                )
            }

            if (showSiteStyleCustomizerSheet) {
                SiteStyleCustomizerSheetContent(
                    viewModel = viewModel,
                    onDismissRequest = { showSiteStyleCustomizerSheet = false }
                )
            }

            // QR Overview Dialog
            if (showQrOverviewDialog) {
                FeatureOverviewDialog(
                    title = "QR Scanner & Generator",
                    subtitle = "Fully Local & Secure",
                    description = "Omni Browser integrates local QR code utilities powered completely offline by Google ML Kit.\n\n• QR Scan Page: Automatically detects and decodes any QR codes visible on your current tab's screen.\n• QR Generator: Instantly converts your active webpage URL into a QR code for quick sharing. You can also customize the text/URL in real-time.",
                    icon = Icons.Rounded.QrCodeScanner,
                    accentColor = Color(0xFF00D2C4), // Cyan/Teal
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showQrOverviewDialog = false
                        viewModel.saveQrOverviewSeen(context, true)
                        pendingQrAction?.invoke()
                        pendingQrAction = null
                    }
                )
            }

            // PDF Overview Dialog
            if (showPdfOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Web Page to PDF",
                    subtitle = "Clean offline saving",
                    description = "Convert any webpage or web article into a clean, read-optimized PDF document locally on your device.\n\nGreat for saving news, documentation, or study materials for offline access without external trackers.",
                    icon = Icons.Rounded.Print,
                    accentColor = Color(0xFF30D158), // Emerald Green
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showPdfOverviewDialog = false
                        viewModel.savePdfOverviewSeen(context, true)
                        pendingPdfAction?.invoke()
                        pendingPdfAction = null
                    }
                )
            }

            // Video Player & Downloader Dialog
            if (showVideoOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Media Player & Downloader",
                    subtitle = "Disclaimer & Rules",
                    description = "Stream video feeds through our hardware-accelerated Media3 player with gesture controls and multi-threaded parallel downloads.\n\n⚠️ Piracy Disclaimer: Omni Browser does not host, index, or endorse the download of copyrighted content. Downloads are only permitted for personal, non-commercial use of public or freely available media.\n\n🚫 YouTube/Google Restriction: In compliance with terms of service, video detection and downloading are strictly disabled on YouTube and all other Google services.",
                    icon = Icons.Rounded.Download,
                    accentColor = Color(0xFFFF6D00), // Sunset Orange
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showVideoOverviewDialog = false
                        viewModel.saveVideoOverviewSeen(context, true)
                        pendingVideoAction?.invoke()
                        pendingVideoAction = null
                    }
                )
            }

            // Extensions Overview Dialog
            if (showExtensionsOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Firefox Desktop Extensions",
                    subtitle = "Ad-blockers & Developer tools",
                    description = "Omni Browser supports desktop Firefox extensions. You can install tools like uBlock Origin directly from the extensions panel to block ads and trackers.\n\nAll extension processing runs entirely local to your device.",
                    icon = Icons.Rounded.Extension,
                    accentColor = Color(0xFFFF3B5C), // Crimson Red
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showExtensionsOverviewDialog = false
                        viewModel.saveExtensionsOverviewSeen(context, true)
                        pendingExtensionsAction?.invoke()
                        pendingExtensionsAction = null
                    }
                )
            }
            
            // Edit Page Overview Dialog
            if (showEditPageOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Web Page Editor",
                    subtitle = "Live DOM Manipulation",
                    description = "Omni Browser allows you to edit the text and elements of any webpage in real-time.\n\nSimply tap elements on the screen to modify text, delete components, or change layout. Tap the tool again to save your edits or exit edit mode.",
                    icon = Icons.Rounded.Edit,
                    accentColor = Color(0xFF5E5CE6), // Royal Purple
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showEditPageOverviewDialog = false
                        viewModel.saveEditPageOverviewSeen(context, true)
                        pendingEditPageAction?.invoke()
                        pendingEditPageAction = null
                    }
                )
            }

            // Console Log Overview Dialog
            if (showConsoleOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Developer Console",
                    subtitle = "JS Logs & Diagnostics",
                    description = "Inspect console messages, warnings, and errors printed by the active webpage.\n\nGreat for debugging scripts, tracking network requests, or analyzing webpage behavior in real-time.",
                    icon = Icons.Rounded.Terminal,
                    accentColor = Color(0xFFF1C40F), // Glow Yellow/Amber
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showConsoleOverviewDialog = false
                        viewModel.saveConsoleOverviewSeen(context, true)
                        pendingConsoleAction?.invoke()
                        pendingConsoleAction = null
                    }
                )
            }

            // Dev Notes Overview Dialog
            if (showDevNotesOverviewDialog) {
                FeatureOverviewDialog(
                    title = "Dev Notes & Vault",
                    subtitle = "Secure Offline Credentials & Snippets",
                    description = "Store passwords, API keys, code snippets, and urls 100% offline securely inside the app sandbox.\n\nIncludes an helper keyboard row for quick symbols insertion (`{}`, `[]`, `=>`, `;`), paste/copy clipboard operators, and direct URL navigation with automated password copying.",
                    icon = Icons.Rounded.Description,
                    accentColor = Color(0xFF9B59B6), // Purple
                    isDarkTheme = viewModel.isDarkThemeEnabled,
                    onDismiss = {
                        showDevNotesOverviewDialog = false
                        viewModel.saveDevNotesOverviewSeen(context, true)
                        pendingDevNotesAction?.invoke()
                        pendingDevNotesAction = null
                    }
                )
            }
            
            // Site permission prompt overlay
            viewModel.activePermissionPrompt?.let { prompt ->
                PermissionPromptDialog(prompt = prompt, isDarkThemeEnabled = viewModel.isDarkThemeEnabled)
            }

            // Site WebRTC media permission prompt overlay
            viewModel.activeMediaPermissionPrompt?.let { prompt ->
                MediaPermissionPromptDialog(prompt = prompt, isDarkThemeEnabled = viewModel.isDarkThemeEnabled)
            }
            
            // Native Player Settings dialog
            if (showPlayerSettingsDialog) {
                PlayerSettingsDialog(
                    viewModel = viewModel,
                    onDismissRequest = { showPlayerSettingsDialog = false }
                )
            }

            // QR Scan Result Composer Overlay
            if (showQrScanResult) {
                QrScanResultComposer(
                    results = viewModel.qrScanResults,
                    onOpenUrl = { url ->
                        viewModel.loadUrl(url)
                    },
                    onDismiss = {
                        showQrScanResult = false
                        viewModel.clearQrScanResults()
                    },
                    isDarkTheme = viewModel.isDarkThemeEnabled
                )
            }

            // QR Generator Dialog Bottom Sheet
            if (showQrGeneratorDialog) {
                QrGeneratorDialog(
                    initialUrl = qrGeneratorUrl,
                    onDismissRequest = { showQrGeneratorDialog = false },
                    isDarkTheme = viewModel.isDarkThemeEnabled
                )
            }

            // ── Generic file download destination dialog ───────────────────────────
            viewModel.pendingGenericDownload?.let { pending ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.pendingGenericDownload = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val ext = pending.filename.substringAfterLast('.').lowercase()
                        val (fileIcon, fileColor) = when {
                            ext == "pdf" -> Icons.Rounded.PictureAsPdf to Color(0xFFE53935)
                            ext == "apk" -> Icons.Rounded.Android to Color(0xFF43A047)
                            ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip to Color(0xFFFF8F00)
                            ext in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac") -> Icons.Rounded.MusicNote to Color(0xFF8E24AA)
                            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Rounded.Image to Color(0xFF039BE5)
                            ext in setOf("doc", "docx", "txt", "rtf") -> Icons.Rounded.Description to Color(0xFF1E88E5)
                            ext in setOf("xls", "xlsx", "csv") -> Icons.Rounded.TableChart to Color(0xFF43A047)
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(fileColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(fileIcon, contentDescription = null, tint = fileColor, modifier = Modifier.size(26.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download File",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pending.filename,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Button(
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = false) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Download Locally", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.startGenericDownload(pending, saveToLocker = true) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Save to Private Vault 🔒", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        TextButton(
                            onClick = { viewModel.pendingGenericDownload = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun ExtensionItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    author: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon box with brush/gradient
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Name & Author
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!enabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SYNCING",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "by $author",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Switch
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }

            // Description
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun UserExtensionItemCard(
    extension: org.mozilla.geckoview.WebExtension,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onOptionsClick: (() -> Unit)? = null,
    onPopupClick: (() -> Unit)? = null
) {
    val extId = try { extension.id ?: "unknown-extension" } catch (e: Exception) { "unknown-extension" }
    val displayName = remember(extId) {
        val name = try { extension.metaData?.name } catch (e: Exception) { null }
        if (!name.isNullOrBlank()) name
        else extId.substringBefore("@").replace("-", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
    val description = remember(extId) {
        try { extension.metaData?.description } catch (e: Exception) { null }
    }
    val version = remember(extId) {
        try { extension.metaData?.version } catch (e: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Icon, Title & Details, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Extension puzzle icon with soft gradient background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title & ID/Version details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!enabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SYNCING",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!version.isNullOrBlank()) {
                            Text(
                                text = "v$version",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = extId,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Switch
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }

            // Middle: Description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            // Bottom Row: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open Popup button (VPN control panel / interactive interface)
                    if (onPopupClick != null) {
                        Button(
                            onClick = onPopupClick,
                            enabled = enabled && checked, // Only clickable if enabled & checked/active!
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Settings/Options Page button
                    if (onOptionsClick != null) {
                        IconButton(
                            onClick = onOptionsClick,
                            enabled = enabled,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Uninstall / Delete Button
                IconButton(
                    onClick = onUninstall,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Uninstall",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionPromptDialog(
    prompt: com.rebelroot.omni.browser.ContentPermissionPrompt,
    isDarkThemeEnabled: Boolean
) {
    val icon = when (prompt.permissionType) {
        1 -> Icons.Rounded.LocationOn // PERMISSION_GEOLOCATION
        3 -> Icons.Rounded.CameraAlt  // PERMISSION_CAMERA
        4 -> Icons.Rounded.Mic        // PERMISSION_MICROPHONE
        5 -> Icons.Rounded.VpnKey     // PERMISSION_MEDIA_KEY_SYSTEM
        8 -> Icons.Rounded.Storage    // PERMISSION_STORAGE_ACCESS
        else -> Icons.Rounded.Info
    }
    
    val title = when (prompt.permissionType) {
        1 -> "Location Access"
        3 -> "Camera Access"
        4 -> "Microphone Access"
        5 -> "DRM Media Access"
        8 -> "Storage Access"
        else -> "Permission Request"
    }

    val description = when (prompt.permissionType) {
        1 -> "wants to access your physical location to provide localized services and content."
        3 -> "wants to use your camera for video streaming or recording."
        4 -> "wants to use your microphone for voice recording or calling."
        5 -> "wants to verify your device DRM keys for secure high-definition playback."
        8 -> "wants to read/write storage for managing site offline cache or local files."
        else -> "is requesting access to permissions."
    }

    val textPrimary = if (isDarkThemeEnabled) Color.White else Color(0xFF202124)
    val textSecondary = if (isDarkThemeEnabled) Color(0xFF8E9AA8) else Color(0xFF606266)
    val cardColor = if (isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
    val borderCol = if (isDarkThemeEnabled) Color(0xFF16222F) else Color(0x1F000000)

    AlertDialog(
        onDismissRequest = { prompt.onDeny() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = prompt.siteUri,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { prompt.onAllow() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Allow", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { prompt.onDeny() },
                border = BorderStroke(0.5.dp, borderCol),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Deny", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = cardColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(BorderStroke(0.5.dp, borderCol), RoundedCornerShape(16.dp))
    )
}

@Composable
fun MediaPermissionPromptDialog(
    prompt: com.rebelroot.omni.browser.MediaPermissionPrompt,
    isDarkThemeEnabled: Boolean
) {
    val title = when {
        prompt.hasVideo && prompt.hasAudio -> "Camera & Microphone"
        prompt.hasVideo -> "Camera Access"
        else -> "Microphone Access"
    }

    val description = when {
        prompt.hasVideo && prompt.hasAudio -> "wants to use your camera and microphone for video capture or calling."
        prompt.hasVideo -> "wants to use your camera for video capture or streaming."
        else -> "wants to use your microphone for voice recording or calling."
    }

    val icon = when {
        prompt.hasVideo && prompt.hasAudio -> Icons.Rounded.Videocam
        prompt.hasVideo -> Icons.Rounded.CameraAlt
        else -> Icons.Rounded.Mic
    }

    val textPrimary = if (isDarkThemeEnabled) Color.White else Color(0xFF202124)
    val textSecondary = if (isDarkThemeEnabled) Color(0xFF8E9AA8) else Color(0xFF606266)
    val cardColor = if (isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
    val borderCol = if (isDarkThemeEnabled) Color(0xFF16222F) else Color(0x1F000000)

    AlertDialog(
        onDismissRequest = { prompt.onDeny() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = prompt.siteUri,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { prompt.onAllow(null, null) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Allow", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { prompt.onDeny() },
                border = BorderStroke(0.5.dp, borderCol),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Deny", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = cardColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(BorderStroke(0.5.dp, borderCol), RoundedCornerShape(16.dp))
    )
}

@Composable
fun MenuGridCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xFF8E9AA8),
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DynamicShortcutItem(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val domain = remember(url) {
        try {
            val uri = Uri.parse(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
    val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$domain"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(faviconUrl)
                        .size(64, 64)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass)
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PopularSiteItem(
    title: String,
    domain: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier.size(60.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick() },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val cleanDomain = domain.substringAfter("https://").substringBefore("/")
                    val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$cleanDomain"
                    
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(faviconUrl)
                            .size(64, 64)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EqualizerIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val animHeights = (0..3).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 350 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "height_$index"
        )
    }

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        animHeights.forEach { animHeight ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight(animHeight.value)
                    .background(color, shape = RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
fun LanguageDropdownSelector(
    label: String,
    selectedLanguageName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    languages: List<Pair<String, String>>,
    onLanguageSelected: (Pair<String, String>) -> Unit
) {
    Box(modifier = Modifier.width(135.dp)) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF23374A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedLanguageName,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 240.dp).background(Color(0xFF16222F))
        ) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.first, color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        onLanguageSelected(lang)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Native Player Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Switch 1: Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Native Player", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Play supported web videos in high-performance native media view", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isNativePlayerEnabled,
                        onCheckedChange = { viewModel.toggleNativePlayer(context) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 2: Auto-Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Start playback automatically when video opens", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerAutoPlayEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "autoplay", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 3: Loop Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Loop Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Repeat video playback in a loop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerLoopEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "loop", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 4: Brightness Gesture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Brightness Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Swipe vertically on the left half to adjust brightness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerBrightnessGestureEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "brightness_gesture", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 5: Volume Gesture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Volume Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Swipe vertically on the right half to adjust volume", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerVolumeGestureEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "volume_gesture", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 6: Resume Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resume Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Remember video position and resume where you left off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerResumePlaybackEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "resume", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 7: Background Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Continue playing audio when app is minimized", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerBackgroundPlaybackEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "background", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Quality Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Quality Limit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Maximum resolution to select automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var expandedQuality by remember { mutableStateOf(false) }
                    val qualities = listOf("Auto", "360p", "480p", "720p", "1080p")
                    Box {
                        TextButton(onClick = { expandedQuality = true }) {
                            Text(
                                text = viewModel.playerDefaultQuality,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        DropdownMenu(
                            expanded = expandedQuality,
                            onDismissRequest = { expandedQuality = false }
                        ) {
                            qualities.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q) },
                                    onClick = {
                                        viewModel.savePlayerSetting(context, "quality", q)
                                        expandedQuality = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
    )
}

val BlackholeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Blackhole",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Outer swirl 1
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 1.2f)
            curveTo(18f, 1.2f, 22.8f, 6f, 22.8f, 12f)
            curveTo(22.8f, 15.6f, 20.4f, 19.2f, 16.8f, 19.2f)
            curveTo(13.2f, 21.6f, 8.4f, 18f, 8.4f, 14.4f)
        }
        // Outer swirl 2
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 22.8f)
            curveTo(6f, 22.8f, 1.2f, 18f, 1.2f, 12f)
            curveTo(1.2f, 8.4f, 3.6f, 4.8f, 7.2f, 4.8f)
            curveTo(10.8f, 2.4f, 15.6f, 6f, 15.6f, 9.6f)
        }
        // Singularity center
        path(
            fill = SolidColor(Color.White)
        ) {
            moveTo(12f, 9f)
            curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
            curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
            curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
            curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
            close()
        }
    }.build()
}

@Composable
fun RainbowScanBorder(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val baseColors = listOf(
        Color(0xFFFF3366), // Hot pink
        Color(0xFFFF9933), // Orange
        Color(0xFFFFCC33), // Yellow
        Color(0xFF33CC66), // Green
        Color(0xFF3399FF), // Blue
        Color(0xFF9933FF), // Purple
        Color(0xFFFF3366)  // Hot pink
    )

    val shiftedColors = remember(offsetFraction) {
        val size = baseColors.size - 1
        val shift = (offsetFraction * size).toInt()
        val fraction = (offsetFraction * size) - shift
        
        List(baseColors.size) { i ->
            val index1 = (i + shift) % size
            val index2 = (index1 + 1) % size
            lerpColor(baseColors[index1], baseColors[index2], fraction)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 4.dp,
                brush = Brush.sweepGradient(colors = shiftedColors),
                shape = RoundedCornerShape(0.dp)
            )
            .alpha(pulseAlpha)
    )
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + fraction * (stop.red - start.red),
        green = start.green + fraction * (stop.green - start.green),
        blue = start.blue + fraction * (stop.blue - start.blue),
        alpha = start.alpha + fraction * (stop.alpha - start.alpha)
    )
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap, displayName: String): Boolean {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/OmniBrowser")
    }

    val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    var success = false
    if (targetUri != null) {
        try {
            resolver.openOutputStream(targetUri)?.use { output ->
                success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } catch (e: java.lang.Exception) {
            Log.e("BrowserScreen", "Failed to save bitmap: ${e.localizedMessage}")
        }
    }
    return success
}

@Composable
fun QrScanResultComposer(
    results: List<String>,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    if (results.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }
    val currentResult = results.getOrNull(currentIndex) ?: ""
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF0D1620).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) Color(0xFF23374A).copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 70.dp) // clear bottom bar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDarkTheme) Color(0xFF1E2E3D) else Color(0xFFF0F4F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "QR Code Detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (results.size > 1) {
                                Text(
                                    text = "Found ${results.size} codes (${currentIndex + 1}/${results.size})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (results.size > 1) {
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex - 1 + results.size) % results.size
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex + 1) % results.size
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Result Content Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) Color(0xFF16222F) else Color(0xFFF5F7FA),
                    border = BorderStroke(0.5.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentResult,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Scanned Text", currentResult)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentResult)
                            }
                            val chooser = Intent.createChooser(intent, "Share Link").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 12.sp)
                    }

                    val isUrl = currentResult.startsWith("http://") || currentResult.startsWith("https://")
                    Button(
                        onClick = {
                            if (isUrl) {
                                onOpenUrl(currentResult)
                            } else {
                                onOpenUrl("https://www.google.com/search?q=${Uri.encode(currentResult)}")
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isUrl) Icons.Rounded.OpenInBrowser else Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrl) "Open Link" else "Search",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorDialog(
    initialUrl: String,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    val context = LocalContext.current

    // Generate QR bitmap reactively
    val qrBitmap = remember(urlText) {
        if (urlText.isNotEmpty()) {
            BarcodeGenerator.generateQRCode(
                text = urlText,
                size = 512,
                foreground = 0xFF000000.toInt(), // Black QR
                background = 0xFFFFFFFF.toInt()  // White background
            )
        } else {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDarkTheme) Color(0xFF0D1620) else Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Share via QR Code",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            // QR Preview Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray.copy(alpha = 0.5f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(220.dp)
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Enter URL to generate",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // URL input field
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("URL / Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Share
                OutlinedButton(
                    onClick = {
                        qrBitmap?.let { bitmap ->
                            try {
                                val cacheFile = File(context.cacheDir, "omni_shared_qr.png")
                                FileOutputStream(cacheFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(intent, "Share QR Code").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(chooser)
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray),
                    enabled = qrBitmap != null
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }

                // Save
                Button(
                    onClick = {
                        qrBitmap?.let { bitmap ->
                            try {
                                val saved = saveBitmapToGallery(context, bitmap, "Omni_QR_${System.currentTimeMillis()}.png")
                                if (saved) {
                                    Toast.makeText(context, "QR saved to gallery", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save QR", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = qrBitmap != null
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureOverviewDialog(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF1E2D3D) else accentColor.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF0C1420) else Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(24.dp, shape = RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Feature Icon with glowing background circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description / Info Detail
                Text(
                    text = description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action button: Got It
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Got it",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            fontSize = 15.sp,
            color = if (isDark) Color.White else Color(0xFF202124)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevNotesSheetContent(
    viewModel: BrowserViewModel,
    activeTab: TabState?,
    onDismissRequest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isEditorOpen by remember { mutableStateOf(false) }
    var selectedNoteForEdit by remember { mutableStateOf<BrowserViewModel.DevNote?>(null) }
    
    // Editor Form States
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var noteType by remember { mutableStateOf("NOTE") } 
    var isTypeMenuExpanded by remember { mutableStateOf(false) }

    // Toggle states for password visibility in the list
    val passwordVisibilityMap = remember { mutableStateMapOf<String, Boolean>() }
    
    // Search & filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTag by remember { mutableStateOf("All") }
    var quickNoteText by remember { mutableStateOf("") }

    // Voice input recognizer
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.firstOrNull() ?: ""
                if (spokenText.isNotEmpty()) {
                    quickNoteText = spokenText
                }
            }
        }
    )

    val isDark = viewModel.isDarkThemeEnabled

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF0B0F19) else Color(0xFFF3F4F6)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isEditorOpen) {
                // --- NOTE EDITOR SCREEN (Detailed Editor) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedNoteForEdit == null) "New Dev Note" else "Edit Note",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { isEditorOpen = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF1E293B) else Color.LightGray.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDark) Color(0xFF1E293B) else Color.LightGray
                        )
                    )

                    // Type Selector Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isTypeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Type: $noteType",
                                    color = if (isDark) Color.White else Color.Black,
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = isTypeMenuExpanded,
                            onDismissRequest = { isTypeMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            listOf("NOTE", "CODE", "KEY", "PASSWORD", "URL").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        noteType = type
                                        isTypeMenuExpanded = false
                                        if (type == "URL" && activeTab != null && noteTitle.isEmpty() && noteContent.text.isEmpty()) {
                                            noteTitle = activeTab.title.take(30)
                                            noteContent = androidx.compose.ui.text.input.TextFieldValue(activeTab.url)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Content Field
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDark) Color(0xFF1E293B) else Color.LightGray
                        )
                    )

                    // Helper Row
                    val symbols = listOf("{}", "[]", "()", "=>", ";", "\"", "'", "const", "let", "function", "&&", "||", "!")
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        val start = noteContent.selection.start
                                        val end = noteContent.selection.end
                                        val newText = noteContent.text.substring(0, start) + text + noteContent.text.substring(end)
                                        noteContent = androidx.compose.ui.text.input.TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(start + text.length)
                                        )
                                    }
                                },
                                label = { Text("Paste Clip") },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        if (noteType == "PASSWORD") {
                            item {
                                AssistChip(
                                    onClick = {
                                        val generatedPass = generateRandomPassword()
                                        val start = noteContent.selection.start
                                        val end = noteContent.selection.end
                                        val newText = noteContent.text.substring(0, start) + generatedPass + noteContent.text.substring(end)
                                        noteContent = androidx.compose.ui.text.input.TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(start + generatedPass.length)
                                        )
                                    },
                                    label = { Text("Gen Password") },
                                    leadingIcon = { Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                        if (noteType == "KEY") {
                            item {
                                AssistChip(
                                    onClick = {
                                        val generatedKey = generateRandomKey()
                                        val start = noteContent.selection.start
                                        val end = noteContent.selection.end
                                        val newText = noteContent.text.substring(0, start) + generatedKey + noteContent.text.substring(end)
                                        noteContent = androidx.compose.ui.text.input.TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(start + generatedKey.length)
                                        )
                                    },
                                    label = { Text("Gen UUID") },
                                    leadingIcon = { Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                        items(symbols) { sym ->
                            AssistChip(
                                onClick = {
                                    val start = noteContent.selection.start
                                    val end = noteContent.selection.end
                                    val newText = noteContent.text.substring(0, start) + sym + noteContent.text.substring(end)
                                    noteContent = androidx.compose.ui.text.input.TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(start + sym.length)
                                    )
                                },
                                label = { Text(sym) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (noteTitle.isNotBlank() && noteContent.text.isNotBlank()) {
                                val currentNote = selectedNoteForEdit
                                if (currentNote == null) {
                                    viewModel.addDevNote(noteTitle, noteContent.text, noteType)
                                } else {
                                    viewModel.updateDevNote(currentNote.id, noteTitle, noteContent.text, noteType)
                                }
                                isEditorOpen = false
                            } else {
                                Toast.makeText(context, "Title and Content cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Note")
                    }
                }
            } else {
                // --- NEW DEV NOTES UI DASHBOARD ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(36.dp))
                    Text(
                        text = "All notes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (isDark) Color.White else Color.Black
                    )
                    IconButton(
                        onClick = {
                            selectedNoteForEdit = null
                            noteTitle = ""
                            noteContent = androidx.compose.ui.text.input.TextFieldValue("")
                            noteType = "NOTE"
                            isEditorOpen = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add New Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Search Bar Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search notes...", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF141D2D) else Color.White,
                            unfocusedContainerColor = if (isDark) Color(0xFF141D2D) else Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }

                // Filter Tag Pills
                val allNotes = viewModel.devNotes.toList()
                val counts = mapOf(
                    "All" to allNotes.size,
                    "NOTE" to allNotes.count { it.type == "NOTE" },
                    "CODE" to allNotes.count { it.type == "CODE" },
                    "KEY" to allNotes.count { it.type == "KEY" },
                    "PASSWORD" to allNotes.count { it.type == "PASSWORD" },
                    "URL" to allNotes.count { it.type == "URL" }
                )

                val filterItems = listOf(
                    "All" to "All ${counts["All"]}",
                    "NOTE" to "#notes ${counts["NOTE"]}",
                    "CODE" to "#codes ${counts["CODE"]}",
                    "KEY" to "#keys ${counts["KEY"]}",
                    "PASSWORD" to "#passwords ${counts["PASSWORD"]}",
                    "URL" to "#urls ${counts["URL"]}"
                )

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterItems) { (tag, label) ->
                        val isSelected = selectedFilterTag == tag
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF141D2D) else Color.White),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else (if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedFilterTag = tag }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
                            )
                        }
                    }
                }

                // Info banner explaining what Dev Notes does
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Dev Notes is an offline-first developer vault for saving code snippets, keys, passwords, and links securely.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }
                }

                // Filter & Search Result Notes
                val filteredNotes = allNotes.filter { note ->
                    (selectedFilterTag == "All" || note.type == selectedFilterTag) &&
                    (searchQuery.isBlank() || note.title.contains(searchQuery, ignoreCase = true) || note.content.contains(searchQuery, ignoreCase = true))
                }

                if (filteredNotes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No matching notes found", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    val chunkedNotes = filteredNotes.chunked(2)

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chunkedNotes) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (note in rowItems) {
                                    val isPassVisible = passwordVisibilityMap[note.id] ?: false
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(
                                                color = if (isDark) Color(0xFF141D2D) else Color.White,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isDark) Color(0xFF1E293B) else Color.LightGray.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                selectedNoteForEdit = note
                                                noteTitle = note.title
                                                noteContent = androidx.compose.ui.text.input.TextFieldValue(note.content)
                                                noteType = note.type
                                                isEditorOpen = true
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = note.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isDark) Color.White else Color.Black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (note.type == "PASSWORD") {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = if (isPassVisible) note.content else "••••••••",
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        fontSize = 13.sp,
                                                        color = if (isDark) Color.LightGray else Color.DarkGray,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            passwordVisibilityMap[note.id] = !isPassVisible
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPassVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = note.content,
                                                    fontSize = 12.sp,
                                                    color = if (isDark) Color.LightGray.copy(alpha = 0.8f) else Color.DarkGray,
                                                    fontFamily = if (note.type == "CODE") androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.SansSerif,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "#" + note.type.lowercase(java.util.Locale.ROOT),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (note.type) {
                                                        "PASSWORD" -> Color(0xFFEF4444)
                                                        "KEY" -> Color(0xFFF59E0B)
                                                        "CODE" -> Color(0xFF06B6D4)
                                                        "URL" -> Color(0xFF10B981)
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                                
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("Copied Note", note.content)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Copied content", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ContentCopy,
                                                            contentDescription = "Copy",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteDevNote(note.id)
                                                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = formatNoteTimestamp(note.timestamp),
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom capsule quick notes input field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isDark) Color(0xFF141D2D) else Color.White,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (activeTab != null && activeTab.url != "about:blank") {
                                    val textToAppend = "${activeTab.title}: ${activeTab.url}"
                                    quickNoteText = if (quickNoteText.isEmpty()) textToAppend else "$quickNoteText $textToAppend"
                                    Toast.makeText(context, "Attached URL from active tab", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No active webpage tab to attach", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = "Attach Web URL",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        BasicTextField(
                            value = quickNoteText,
                            onValueChange = { quickNoteText = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (isDark) Color.White else Color.Black,
                                fontSize = 14.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (quickNoteText.isEmpty()) {
                                    Text("Create quick note", color = Color.Gray, fontSize = 14.sp)
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        if (quickNoteText.isEmpty()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        }
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Speech recognizer not available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Voice Input",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    val isSendEnabled = quickNoteText.isNotBlank()
                    IconButton(
                        onClick = {
                            if (isSendEnabled) {
                                val parsed = parseQuickNote(quickNoteText)
                                viewModel.addDevNote(parsed.title, parsed.content, parsed.type)
                                quickNoteText = ""
                                Toast.makeText(context, "Note added", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSendEnabled) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF141D2D) else Color.White),
                            disabledContainerColor = if (isDark) Color(0xFF141D2D) else Color.White
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .border(
                                width = 1.dp,
                                color = if (isSendEnabled) Color.Transparent else (if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f)),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save Note",
                            tint = if (isSendEnabled) Color.White else Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteStyleCustomizerSheetContent(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    var fontSize by remember { mutableStateOf(viewModel.siteStyleFontSize) }
    var themePreset by remember { mutableStateOf(viewModel.siteStyleTheme) }
    var lineSpacing by remember { mutableStateOf(viewModel.siteStyleLineSpacing) }
    var letterSpacing by remember { mutableStateOf(viewModel.siteStyleLetterSpacing) }
    var fontFamily by remember { mutableStateOf(viewModel.siteStyleFontFamily) }
    var appliedGlobally by remember { mutableStateOf(viewModel.siteStyleAppliedGlobally) }

    val presets = listOf(
        "DEFAULT" to ("Original" to Color.Gray),
        "DARK" to ("Dark Blue" to Color(0xFF0B131E)),
        "SEPIA" to ("Sepia" to Color(0xFFF4ECD8)),
        "OLED" to ("OLED Black" to Color(0xFF000000)),
        "FOREST" to ("Forest" to Color(0xFFE6F0E6))
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0D1620) else Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Site Style Customizer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = {
                        viewModel.resetSiteStyle()
                        fontSize = 100
                        themePreset = "DEFAULT"
                        lineSpacing = 1.4f
                        letterSpacing = 0f
                        fontFamily = "inherit"
                        appliedGlobally = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color Theme Presets", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (code, labelInfo) ->
                        val (label, color) = labelInfo
                        val isSelected = themePreset == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    color = if (code == "DEFAULT") {
                                        if (viewModel.isDarkThemeEnabled) Color(0xFF1C2C3E) else Color(0xFFF1F5F9)
                                    } else color,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    themePreset = code
                                    viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (code == "SEPIA" || code == "FOREST" || (code == "DEFAULT" && !viewModel.isDarkThemeEnabled)) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Font Size", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
                    Text("${fontSize}%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.Slider(
                    value = fontSize.toFloat(),
                    onValueChange = {
                        fontSize = it.toInt()
                        viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                    },
                    valueRange = 80f..200f
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Line Spacing", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
                    Text(String.format("%.2fx", lineSpacing), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.Slider(
                    value = lineSpacing,
                    onValueChange = {
                        lineSpacing = it
                        viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                    },
                    valueRange = 1.0f..2.5f
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Letter Spacing", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
                    Text(String.format("%.2fpx", letterSpacing), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.Slider(
                    value = letterSpacing,
                    onValueChange = {
                        letterSpacing = it
                        viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                    },
                    valueRange = -1.0f..4.0f
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Font Style", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (viewModel.isDarkThemeEnabled) Color.LightGray else Color.DarkGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "inherit" to "Default",
                        "sans-serif" to "Sans-Serif",
                        "serif" to "Serif",
                        "monospace" to "Monospace"
                    ).forEach { (code, label) ->
                        val isSelected = fontFamily == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    fontFamily = code
                                    viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = when (code) {
                                    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                    "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                    else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (viewModel.isDarkThemeEnabled) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Apply to all sites", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black)
                    Text("Save preferences to automatically load on every site.", fontSize = 11.sp, color = Color.Gray)
                }
                androidx.compose.material3.Switch(
                    checked = appliedGlobally,
                    onCheckedChange = {
                        appliedGlobally = it
                        viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun formatNoteTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
    val date = java.util.Date(timestamp)
    val now = java.util.Calendar.getInstance()
    val noteCal = java.util.Calendar.getInstance().apply { time = date }
    
    return when {
        now.get(java.util.Calendar.YEAR) == noteCal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == noteCal.get(java.util.Calendar.DAY_OF_YEAR) -> {
            "Today ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date)}"
        }
        now.get(java.util.Calendar.YEAR) == noteCal.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) - noteCal.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date)}"
        }
        else -> {
            sdf.format(date)
        }
    }
}

fun parseQuickNote(text: String): BrowserViewModel.DevNote {
    val trimmed = text.trim()
    val lower = trimmed.lowercase(java.util.Locale.ROOT)
    val type = when {
        trimmed.contains("http://") || trimmed.contains("https://") || trimmed.contains("www.") -> "URL"
        trimmed.contains("{") && trimmed.contains("}") -> "CODE"
        trimmed.contains("function ") || trimmed.contains("val ") || trimmed.contains("var ") ||
                trimmed.contains("import ") || trimmed.contains("class ") || trimmed.contains("fun ") ||
                trimmed.contains("public ") || trimmed.contains("private ") || trimmed.contains("return ") ||
                trimmed.contains("const ") || trimmed.contains("let ") || trimmed.contains("def ") -> "CODE"
        lower.contains("password") || lower.contains("pwd") || lower.contains("passwd") || lower.contains("credentials") -> "PASSWORD"
        lower.contains("api_key") || lower.contains("apikey") || lower.contains("ssh-") || lower.contains("ghp_") ||
                lower.contains("token") || lower.contains("secret") || lower.contains("key") -> "KEY"
        else -> "NOTE"
    }

    var title = "Quick Note"
    var content = trimmed

    if (trimmed.contains(": ")) {
        val parts = trimmed.split(": ", limit = 2)
        if (parts[0].length in 2..60) {
            title = parts[0].trim()
            content = parts[1].trim()
        }
    } else {
        val words = trimmed.split(Regex("\\s+"))
        if (words.isNotEmpty()) {
            val preview = words.take(4).joinToString(" ")
            title = if (preview.length > 35) preview.take(35) + "..." else preview
        }
    }

    return BrowserViewModel.DevNote(
        title = title,
        content = content,
        type = type
    )
}

fun generateRandomPassword(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
    return (1..16).map { chars.random() }.joinToString("")
}

fun generateRandomKey(): String {
    return java.util.UUID.randomUUID().toString()
}


