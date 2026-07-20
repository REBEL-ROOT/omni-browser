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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import coil.compose.rememberAsyncImagePainter
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
 import androidx.compose.foundation.gestures.rememberTransformableState
 import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


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
            .clip(RoundedCornerShape(20.dp))
            .border(
                BorderStroke(
                    0.5.dp,
                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                RoundedCornerShape(20.dp)
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
                modifier = Modifier.size(16.dp)
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
    onFocusChanged: (Boolean) -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    showCustomizationSheet: Boolean,
    onShowCustomizationSheetChange: (Boolean) -> Unit,
    onShowTabGroups: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    LaunchedEffect(searchText.text) {
        viewModel.fetchSearchSuggestions(searchText.text)
    }
    var showAddShortcutSheet by remember { mutableStateOf(false) }
    var shortcutsExpanded by remember { mutableStateOf(false) }
    var selectedShortcutForMenu by remember { mutableStateOf<HomeShortcut?>(null) }
    var showShortcutOptionsSheet by remember { mutableStateOf(false) }
    var showEditShortcutDialog by remember { mutableStateOf(false) }
    var showDeleteShortcutDialog by remember { mutableStateOf(false) }

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

    var showHomeMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewModel.browserWallpaperUri != null && !viewModel.isIncognitoMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                coil.compose.AsyncImage(
                    model = viewModel.browserWallpaperUri,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = viewModel.wallpaperScale
                            scaleY = viewModel.wallpaperScale
                            translationX = viewModel.wallpaperOffsetX
                            translationY = viewModel.wallpaperOffsetY
                        }
                        .then(
                            if (viewModel.wallpaperBlur > 0f) {
                                Modifier.blur(viewModel.wallpaperBlur.dp).graphicsLayer()
                            } else {
                                Modifier
                            }
                        )
                )
            }
            val dimAlpha = if (viewModel.wallpaperDim >= 0f) {
                viewModel.wallpaperDim
            } else {
                if (viewModel.isDarkThemeEnabled) 0.5f else 0.65f
            }
            val overlayColor = if (viewModel.isDarkThemeEnabled) Color.Black.copy(alpha = dimAlpha) else Color.White.copy(alpha = dimAlpha)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (viewModel.isIncognitoMode) {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF161320), Color(0xFF0C0A10))
                        )
                    } else if (viewModel.browserWallpaperUri != null) {
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent)
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
                        )
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
        if (!viewModel.isIncognitoMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tabs counter icon — only shown when All-in-One navbar is enabled
                if (viewModel.chromeNavBarEnabled) {
                    IconButton(
                        onClick = onShowTabGroups,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.5.dp, if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E), RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box {
                IconButton(
                    onClick = { showHomeMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Menu",
                        tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E),
                        modifier = Modifier.size(20.dp)
                    )
                }

                ChromeMenuDropdown(
                    expanded = showHomeMenu,
                    onDismissRequest = { showHomeMenu = false },
                    viewModel = viewModel,
                    onNewTab = {
                        viewModel.createNewTab(context, "about:blank")
                    },
                    onNewIncognitoTab = {
                        if (!viewModel.isIncognitoMode) {
                            viewModel.toggleIncognitoMode(context)
                        }
                        viewModel.createNewTab(context, "about:blank")
                    },
                    onOpenHistory = onOpenHistory,
                    onBurnData = {
                        coroutineScope.launch {
                            val runtime = viewModel.getGeckoRuntime(context)
                            FireButton(runtime, context).burn()
                            viewModel.burnAllData(context)
                            Toast.makeText(context, "🔥 All history and tabs burned", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenDownloads = onOpenDownloads,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    onShowCustomizationSheet = { onShowCustomizationSheetChange(true) },
                    onShowExtensions = {},
                    onShowPlayerSettings = {},
                    onShowSiteInfo = {}
                )
                } // end Box (3-dot + dropdown)
            } // end Row
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (viewModel.isIncognitoMode) {
            // Incognito Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282335)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = "Incognito Mode",
                        tint = Color(0xFFCBB2FF),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    text = "You've gone incognito",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Your browsing history, search history, and cookies won't be saved. Files downloaded and bookmarks created will still be kept.",
                    color = Color(0xFF9186A8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        } else if (viewModel.showHomeLogo) {
            // Center branding OMNI stylized logo Image or Custom Cropped Image
            if (viewModel.customIconPath != null) {
                coil.compose.AsyncImage(
                    model = java.io.File(viewModel.customIconPath!!),
                    contentDescription = "Omni Browser Custom Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
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
            }
        }

        val contentModifier = Modifier.fillMaxWidth()

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
                            .background(if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface)
                            
                    ) {
                        val engines = listOf("Google", "Yahoo", "Yandex", "DuckDuckGo", "Brave", "Bing", "Ecosia", "Startpage", "Qwant", "Custom")
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

        if (searchText.text.isNotEmpty() && viewModel.searchSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF1F3F4),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    viewModel.searchSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchText = androidx.compose.ui.text.input.TextFieldValue(suggestion, androidx.compose.ui.text.TextRange(suggestion.length))
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onNavigateTo(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = suggestion,
                                color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E),
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    searchText = androidx.compose.ui.text.input.TextFieldValue(suggestion, androidx.compose.ui.text.TextRange(suggestion.length))
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.TrendingFlat,
                                    contentDescription = "Refine search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = -135f }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.showHomeShortcuts && (searchText.text.isEmpty() || viewModel.searchSuggestions.isEmpty())) {
            // Dynamic Grid of Shortcuts — 5 per row, icon-only, no border boxes
            // Shows up to 15 items collapsed; "More" expands to show all.
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
                                        CompactDynamicShortcutItem(
                                            title = shortcut.title,
                                            url = shortcut.url,
                                            onClick = { onNavigateTo(shortcut.url) },
                                            onLongClick = {
                                                if (!shortcut.isPermanent) {
                                                    selectedShortcutForMenu = shortcut
                                                    showShortcutOptionsSheet = true
                                                }
                                            }
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
        }
        }

        // Shortcut long-press Options Sheet
        if (showShortcutOptionsSheet && selectedShortcutForMenu != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showShortcutOptionsSheet = false
                    selectedShortcutForMenu = null
                },
                containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = selectedShortcutForMenu!!.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShortcutOptionsSheet = false
                                showEditShortcutDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Shortcut",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Edit Shortcut",
                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color.Black,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShortcutOptionsSheet = false
                                showDeleteShortcutDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Remove Shortcut",
                            tint = Color.Red
                        )
                        Text(
                            text = "Remove Shortcut",
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Edit Shortcut Dialog
        if (showEditShortcutDialog && selectedShortcutForMenu != null) {
            var editName by remember { mutableStateOf(selectedShortcutForMenu!!.title) }
            var editUrl by remember { mutableStateOf(selectedShortcutForMenu!!.url) }
            AlertDialog(
                onDismissRequest = {
                    showEditShortcutDialog = false
                    selectedShortcutForMenu = null
                },
                title = { Text("Edit Shortcut") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = editUrl,
                            onValueChange = { editUrl = it },
                            label = { Text("URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (editName.isNotBlank() && editUrl.isNotBlank()) {
                            viewModel.editShortcut(selectedShortcutForMenu!!, editName, editUrl)
                        }
                        showEditShortcutDialog = false
                        selectedShortcutForMenu = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showEditShortcutDialog = false
                        selectedShortcutForMenu = null
                    }) { Text("Cancel") }
                }
            )
        }

        // Delete Shortcut Confirmation Dialog
        if (showDeleteShortcutDialog && selectedShortcutForMenu != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteShortcutDialog = false
                    selectedShortcutForMenu = null
                },
                title = { Text("Delete Shortcut?") },
                text = { Text("Remove shortcut to ${selectedShortcutForMenu!!.title}?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteShortcut(selectedShortcutForMenu!!)
                        showDeleteShortcutDialog = false
                        selectedShortcutForMenu = null
                    }) { Text("Delete", color = Color(0xFFFF3B30)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteShortcutDialog = false
                        selectedShortcutForMenu = null
                    }) { Text("Cancel") }
                }
            )
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
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Shortcut Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Shortcut URL") },
                            placeholder = { Text("example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
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
                            shape = RoundedCornerShape(24.dp)
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
        if (viewModel.showDiscoverFeed && !viewModel.isIncognitoMode) {
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
                            shape = RoundedCornerShape(32.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
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
                        shape = RoundedCornerShape(32.dp),
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
                                            .clip(RoundedCornerShape(20.dp)),
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

}

    if (showCustomizationSheet) {
        ModalBottomSheet(
            onDismissRequest = { onShowCustomizationSheetChange(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (viewModel.isDarkThemeEnabled) Color(0xFF3A3A3C) else Color(0xFFC7C7CC))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Customize Home Screen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E)
                )

                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

                // Toggle 1: Show Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Logo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "Display stylized brand logo at the top",
                            fontSize = 12.sp,
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                        )
                    }
                    Switch(
                        checked = viewModel.showHomeLogo,
                        onCheckedChange = { viewModel.saveShowHomeLogo(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                // Toggle 2: Show Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Shortcuts",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "Show quick access site links and standard shortcuts",
                            fontSize = 12.sp,
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                        )
                    }
                    Switch(
                        checked = viewModel.showHomeShortcuts,
                        onCheckedChange = { viewModel.saveShowHomeShortcuts(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                // Toggle 3: Show Discover Feed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Discover Feed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "Display news articles feed on home screen",
                            fontSize = 12.sp,
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                        )
                    }
                    Switch(
                        checked = viewModel.showDiscoverFeed,
                        onCheckedChange = { viewModel.saveShowDiscoverFeed(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
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
                .size(48.dp)
                .clip(RoundedCornerShape(32.dp))
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
                modifier = Modifier.size(24.dp)
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
                .size(48.dp)
                .clip(RoundedCornerShape(32.dp))
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
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(32.dp)),
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
                .size(64.dp)
                .clip(RoundedCornerShape(24.dp))
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
                .clip(RoundedCornerShape(20.dp))
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
            .clip(RoundedCornerShape(24.dp))
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconContainerBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
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
                .size(64.dp)
                .clip(RoundedCornerShape(24.dp))
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
                        .clip(RoundedCornerShape(32.dp)),
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
            modifier = Modifier.size(64.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(24.dp))
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(20.dp)
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
