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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
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
    var isDark = viewModel.isDarkThemeEnabled

    // Set Native Sheet Open flag for extension security gating
    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.isNativeSheetOpen = true
        onDispose {
            viewModel.isNativeSheetOpen = false
        }
    }

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

    // Auto-save logic
    val handleSave: () -> Unit = {
        if (noteTitle.isNotBlank() || noteContent.text.isNotBlank()) {
            val currentNote = selectedNoteForEdit
            val finalTitle = if (noteTitle.isBlank()) context.getString(R.string.devnotes_untitled) else noteTitle
            if (currentNote == null) {
                viewModel.addDevNote(finalTitle, noteContent.text, noteType)
            } else {
                viewModel.updateDevNote(currentNote.id, finalTitle, noteContent.text, noteType)
            }
        }
        selectedNoteForEdit = null
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (isEditorOpen) {
                handleSave()
                isEditorOpen = false
            }
            onDismissRequest()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isEditorOpen) {
                // --- NOTE EDITOR (Full Screen Notepad style) ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Editor Top Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            handleSave()
                            isEditorOpen = false
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back & Auto-Save",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Category Badge Selector
                            Box {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = when (noteType) {
                                                "PASSWORD" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                                "KEY" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                "CODE" -> Color(0xFF06B6D4).copy(alpha = 0.2f)
                                                "URL" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                else -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { isTypeMenuExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = noteType,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (noteType) {
                                                "PASSWORD" -> Color(0xFFEF4444)
                                                "KEY" -> Color(0xFFF59E0B)
                                                "CODE" -> Color(0xFF06B6D4)
                                                "URL" -> Color(0xFF10B981)
                                                else -> Color(0xFF8B5CF6)
                                            }
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropDown,
                                            contentDescription = null,
                                            tint = if (isDark) Color.White else Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isTypeMenuExpanded,
                                    onDismissRequest = { isTypeMenuExpanded = false }
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

                            TextButton(onClick = {
                                handleSave()
                                isEditorOpen = false
                            }) {
                                Text(stringResource(id = R.string.devnotes_done), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Content Area (Visual Paper)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                    ) {
                        // Title Input
                        BasicTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (isDark) Color.White else Color.Black,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (noteTitle.isEmpty()) {
                                    Text(
                                        text = stringResource(id = R.string.devnotes_title_placeholder),
                                        color = Color.Gray,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )

                        HorizontalDivider(
                            color = if (isDark) Color(0xFF1E293B) else Color.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Body Input
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = noteContent,
                                onValueChange = { noteContent = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                    fontSize = 16.sp,
                                    fontFamily = if (noteType == "CODE") androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.SansSerif,
                                    lineHeight = 22.sp
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (noteContent.text.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.devnotes_body_placeholder),
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }

                    // Symbols helper row at bottom of editor
                    val symbols = listOf("{}", "[]", "()", "=>", ";", "\"", "'", "const", "let", "function", "&&", "||", "!")
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                label = { Text(stringResource(id = R.string.devnotes_paste)) },
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
                                    label = { Text(stringResource(id = R.string.devnotes_gen_password)) },
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
                                    label = { Text(stringResource(id = R.string.devnotes_gen_uuid)) },
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
                }
            } else {
                // --- NOTES DASHBOARD LIST (Full Screen UI) ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }

                        Text(
                            text = stringResource(id = R.string.devnotes_header),
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
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add note",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }



                    // Search Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF141D2D) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.devnotes_search_placeholder),
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = if (isDark) Color.White else Color.Black,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Filter Pills
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
                        "All" to stringResource(id = R.string.devnotes_filter_all, counts["All"] ?: 0),
                        "NOTE" to stringResource(id = R.string.devnotes_filter_notes, counts["NOTE"] ?: 0),
                        "CODE" to stringResource(id = R.string.devnotes_filter_codes, counts["CODE"] ?: 0),
                        "KEY" to stringResource(id = R.string.devnotes_filter_keys, counts["KEY"] ?: 0),
                        "PASSWORD" to stringResource(id = R.string.devnotes_filter_passwords, counts["PASSWORD"] ?: 0),
                        "URL" to stringResource(id = R.string.devnotes_filter_urls, counts["URL"] ?: 0)
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filterItems) { (tag, label) ->
                            val isSelected = selectedFilterTag == tag
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF141D2D) else Color.White),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else (if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                                    .clickable { selectedFilterTag = tag }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
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

                    // Notes List
                    val filteredNotes = allNotes.filter { note ->
                        (selectedFilterTag == "All" || note.type == selectedFilterTag) &&
                        (searchQuery.isBlank() || note.title.contains(searchQuery, ignoreCase = true) || note.content.contains(searchQuery, ignoreCase = true))
                    }

                    if (filteredNotes.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(id = R.string.devnotes_empty), color = Color.Gray, fontSize = 15.sp)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredNotes) { note ->
                                val isPassVisible = passwordVisibilityMap[note.id] ?: false
                                val cardColor = MaterialTheme.colorScheme.surface
                                val accentColor = when (note.type) {
                                    "PASSWORD" -> Color(0xFFEF4444)
                                    "KEY" -> Color(0xFFF59E0B)
                                    "CODE" -> Color(0xFF06B6D4)
                                    "URL" -> Color(0xFF10B981)
                                    else -> Color(0xFF8B5CF6)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedNoteForEdit = note
                                            noteTitle = note.title
                                            noteContent = androidx.compose.ui.text.input.TextFieldValue(note.content)
                                            noteType = note.type
                                            isEditorOpen = true
                                        },
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                        // Left Accent Colored Border
                                        Box(
                                            modifier = Modifier
                                                .width(5.dp)
                                                .fillMaxHeight()
                                                .background(accentColor)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = note.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (isDark) Color.White else Color.Black,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // Top Right Type Tag badge
                                                Box(
                                                    modifier = Modifier
                                                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = note.type,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = accentColor
                                                    )
                                                }
                                            }

                                            if (note.type == "PASSWORD") {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = if (isPassVisible) note.content else "••••••••",
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        fontSize = 14.sp,
                                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = { passwordVisibilityMap[note.id] = !isPassVisible },
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
                                                    fontSize = 13.sp,
                                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                                    fontFamily = if (note.type == "CODE") androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.SansSerif,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatNoteTimestamp(note.timestamp),
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("Copied Note", note.content)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, context.getString(R.string.devnotes_copied), Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ContentCopy,
                                                            contentDescription = "Copy",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteDevNote(note.id)
                                                            Toast.makeText(context, context.getString(R.string.devnotes_deleted), Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(16.dp)
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

                    // Capsule quick note bottom field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
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
                                        Toast.makeText(context, context.getString(R.string.devnotes_url_attached), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.devnotes_no_tab_attach), Toast.LENGTH_SHORT).show()
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
                                        Text(stringResource(id = R.string.devnotes_quick_placeholder), color = Color.Gray, fontSize = 14.sp)
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
                                            Toast.makeText(context, context.getString(R.string.devnotes_no_speech), Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, context.getString(R.string.devnotes_added), Toast.LENGTH_SHORT).show()
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
    var hideImages by remember { mutableStateOf(viewModel.siteStyleHideImages) }
    var grayscale by remember { mutableStateOf(viewModel.siteStyleGrayscale) }
    var warmFilter by remember { mutableStateOf(viewModel.siteStyleWarmFilter) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val presets = listOf(
        Triple("DEFAULT", R.string.site_style_preset_original, Color.Gray),
        Triple("DARK", R.string.site_style_preset_dark, Color(0xFF18181B)),
        Triple("SEPIA", R.string.site_style_preset_sepia, Color(0xFFF4ECD8)),
        Triple("OLED", R.string.site_style_preset_oled, Color(0xFF000000)),
        Triple("FOREST", R.string.site_style_preset_forest, Color(0xFF0F1C15))
    )

    val isDark = viewModel.isDarkThemeEnabled
    val sheetBg = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
    val cardBg = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else sheetBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.site_style_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textPrimary
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.resetSiteStyle()
                        fontSize = 100
                        themePreset = "DEFAULT"
                        lineSpacing = 1.4f
                        letterSpacing = 0f
                        fontFamily = "inherit"
                        appliedGlobally = false
                        hideImages = false
                        grayscale = false
                        warmFilter = false
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.site_style_reset_all),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }

            // SECTION 1: Color Presets & Font Family Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(id = R.string.site_style_theme_presets),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { (code, labelRes, color) ->
                                val isSelected = themePreset == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(
                                            color = if (code == "DEFAULT") {
                                                if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                            } else color,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            themePreset = code
                                            viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = context.getString(labelRes),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (code == "SEPIA" || (code == "DEFAULT" && !isDark)) Color(0xFF5F4B32) else Color.White
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Fonts
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(id = R.string.site_style_font_family),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("inherit", R.string.site_style_font_default, androidx.compose.ui.text.font.FontFamily.SansSerif),
                                Triple("sans-serif", R.string.site_style_font_sans, androidx.compose.ui.text.font.FontFamily.SansSerif),
                                Triple("serif", R.string.site_style_font_serif, androidx.compose.ui.text.font.FontFamily.Serif),
                                Triple("monospace", R.string.site_style_font_mono, androidx.compose.ui.text.font.FontFamily.Monospace)
                            ).forEach { (code, labelRes, ff) ->
                                val isSelected = fontFamily == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            fontFamily = code
                                            viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = labelRes),
                                        fontSize = 11.sp,
                                        fontFamily = ff,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: Typography & Spacing Sliders Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Slider 1: Font Size
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.FormatSize, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                Text(stringResource(id = R.string.site_style_font_size), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
                            }
                            Text("${fontSize}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = {
                                fontSize = it.toInt()
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            valueRange = 80f..200f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Slider 2: Line Spacing
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.FormatLineSpacing, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                Text(stringResource(id = R.string.site_style_line_spacing), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
                            }
                            Text(String.format("%.2fx", lineSpacing), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = lineSpacing,
                            onValueChange = {
                                lineSpacing = it
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            valueRange = 1.0f..2.5f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Slider 3: Letter Spacing
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.TextFields, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                Text(stringResource(id = R.string.site_style_letter_spacing), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
                            }
                            Text(String.format("%.2fpx", letterSpacing), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = letterSpacing,
                            onValueChange = {
                                letterSpacing = it
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            valueRange = -1.0f..4.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }
            }

            // SECTION 3: Content Filters & Helpers (Hide Images, Grayscale, Night Light)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: Hide Images
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(stringResource(id = R.string.site_style_hide_images), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text(stringResource(id = R.string.site_style_hide_images_desc), fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = hideImages,
                            onCheckedChange = {
                                hideImages = it
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Row 2: Grayscale Focus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BrightnessMedium,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(stringResource(id = R.string.site_style_grayscale), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text(stringResource(id = R.string.site_style_grayscale_desc), fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = grayscale,
                            onCheckedChange = {
                                grayscale = it
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Row 3: Blue Light Filter (Night Light)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Nightlight,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(stringResource(id = R.string.site_style_night_light), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text(stringResource(id = R.string.site_style_night_light_desc), fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = warmFilter,
                            onCheckedChange = {
                                warmFilter = it
                                viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                            },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Row 4: Scroll Buttons
                    var showScrollButtons by remember { mutableStateOf(viewModel.showScrollButtons) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(stringResource(id = R.string.site_style_scroll_buttons), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text(stringResource(id = R.string.site_style_scroll_buttons_desc), fontSize = 10.sp, color = textSecondary)
                            }
                        }
                        Switch(
                            checked = showScrollButtons,
                            onCheckedChange = {
                                showScrollButtons = it
                                viewModel.saveShowScrollButtons(context, it)
                            },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
            }

            // SECTION 4: Application Scope Card (Apply to all)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = R.string.site_style_apply_all), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                        Text(stringResource(id = R.string.site_style_apply_all_desc), fontSize = 11.sp, color = textSecondary)
                    }
                    Switch(
                        checked = appliedGlobally,
                        onCheckedChange = {
                            appliedGlobally = it
                            viewModel.updateSiteStyle(fontSize, themePreset, lineSpacing, letterSpacing, fontFamily, appliedGlobally, hideImages, grayscale, warmFilter)
                        },
                        modifier = Modifier.scale(0.85f)
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyReportSheet(
    onDismissRequest: () -> Unit,
    viewModel: BrowserViewModel
) {
    var showTrackersList by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Shield Icon + Privacy Report
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Protection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.privacy_report_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.privacy_report_intelligent_tracking),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Overview explanation banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Text(
                    text = stringResource(R.string.privacy_report_banner_desc),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Hero 4-Grid Stats Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stat 1: Trackers Blocked
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${viewModel.trackersBlockedCount}",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.privacy_report_trackers_prevented),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stat 2: Mobile Data Saved
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${(viewModel.trackersBlockedCount * 1.5).toInt()} MB",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.privacy_report_est_data_saved),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stat 3: Speed Boost
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "+35%",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.privacy_report_faster_page_load),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stat 4: Privacy Level
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "100%",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.privacy_report_on_device_privacy),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Section: Prevented Trackers List
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.privacy_report_most_frequent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showTrackersList = !showTrackersList }) {
                    Text(
                        text = if (showTrackersList) stringResource(R.string.privacy_report_hide) else stringResource(R.string.privacy_report_show),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showTrackersList) {
                val trackerDomains = listOf(
                    Triple("google-analytics.com", stringResource(R.string.privacy_cat_analytics_telemetry), "High"),
                    Triple("facebook.net", stringResource(R.string.privacy_cat_social_tracking), "High"),
                    Triple("doubleclick.net", stringResource(R.string.privacy_cat_ad_telemetry), "Medium"),
                    Triple("scorecardresearch.com", stringResource(R.string.privacy_cat_audience_measurement), "Medium"),
                    Triple("criteo.com", stringResource(R.string.privacy_cat_retargeting_tracker), "Low")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trackerDomains.forEach { (domain, category, _) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = domain,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = category,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = stringResource(R.string.privacy_report_blocked_status),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGrabberSheetContent(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isMangaMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf(setOf<String>()) }
    val isDark = viewModel.isDarkThemeEnabled
    val bg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7)
    val cardBg = if (viewModel.isAmoledMode) Color(0xFF111111) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    // Initial load when sheet opens
    LaunchedEffect(Unit) {
        viewModel.extractPageImages(context)
    }

    val initialStyle = remember { com.rebelroot.omni.ai.manga.MangaPreferences.loadTypographyStyle(context) }
    val initialLangs = remember { com.rebelroot.omni.ai.manga.MangaPreferences.loadLanguages(context) }
    var localImages by remember(viewModel.extractedImagesList) { mutableStateOf(viewModel.extractedImagesList) }
    var isFullscreenManga by remember { mutableStateOf(false) }
    var pendingDownloadType by remember { mutableStateOf<Boolean?>(null) } // null = hide, false = images, true = asPdf
    var isTranslateMangaEnabled by remember { mutableStateOf(false) }
    var selectedSourceLang by remember { mutableStateOf(initialLangs.first) }
    var selectedTargetLang by remember { mutableStateOf(initialLangs.second) }
    var typographyStyle by remember { mutableStateOf(initialStyle) }
    var showComposerSheet by remember { mutableStateOf(false) }
    val mangaPipeline = viewModel.translationManager.mangaPipeline

    if (showComposerSheet) {
        MangaTranslationComposerSheet(
            sourceLang = selectedSourceLang,
            targetLang = selectedTargetLang,
            typographyStyle = typographyStyle,
            onSourceLangChange = { selectedSourceLang = it },
            onTargetLangChange = { selectedTargetLang = it },
            onStyleChange = { typographyStyle = it },
            onDismissRequest = { showComposerSheet = false },
            isDark = isDark
        )
    }

    // Download destination prompt (Download Locally vs Save to Private Vault 🔒)
    pendingDownloadType?.let { asPdf ->
        ModalBottomSheet(
            onDismissRequest = { pendingDownloadType = null },
            containerColor = cardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (asPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (asPdf) stringResource(id = R.string.img_grab_dl_pdf_title) else stringResource(id = R.string.img_grab_dl_images_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = textColor
                        )
                        Text(
                            text = stringResource(id = R.string.img_grab_pages_ready, localImages.size),
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

                Button(
                    onClick = {
                        val isPdf = asPdf
                        pendingDownloadType = null
                        downloadMangaImagesAndPdf(context, localImages, viewModel.currentUrl, asPdf = isPdf, saveToLocker = false, downloadEngine = viewModel.streamDownloadEngine)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.img_grab_download_locally), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = {
                        val isPdf = asPdf
                        pendingDownloadType = null
                        downloadMangaImagesAndPdf(context, localImages, viewModel.currentUrl, asPdf = isPdf, saveToLocker = true, downloadEngine = viewModel.streamDownloadEngine)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.img_grab_save_vault), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (isFullscreenManga && localImages.isNotEmpty()) {
        MangaFullscreenViewer(
            images = localImages,
            pageUrl = viewModel.currentUrl,
            downloadEngine = viewModel.streamDownloadEngine,
            pipeline = mangaPipeline,
            initialTranslate = isTranslateMangaEnabled,
            sourceLanguage = selectedSourceLang.second,
            targetLanguage = selectedTargetLang.second,
            initialTypographyStyle = typographyStyle,
            onTypographyStyleChange = { typographyStyle = it },
            onSourceLangChange = { selectedSourceLang = it },
            onTargetLangChange = { selectedTargetLang = it },
            onExitFullscreen = { isFullscreenManga = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = bg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .fillMaxHeight(0.9f)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (isMangaMode) stringResource(id = R.string.img_grab_manga_mode) else stringResource(id = R.string.img_grab_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = stringResource(id = R.string.img_grab_pages_extracted, localImages.size),
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isMangaMode && localImages.isNotEmpty()) {
                        IconButton(onClick = { isFullscreenManga = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Mode Toggle (Manga vs Grid)
                    FilterChip(
                        selected = isMangaMode,
                        onClick = { isMangaMode = !isMangaMode },
                        label = { Text(if (isMangaMode) stringResource(id = R.string.img_grab_grid_view) else stringResource(id = R.string.img_grab_manga_view), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textColor)
                    }
                }
            }

            if (isMangaMode && localImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isTranslateMangaEnabled,
                            onClick = { isTranslateMangaEnabled = !isTranslateMangaEnabled },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isTranslateMangaEnabled) MaterialTheme.colorScheme.primary else textColor
                                )
                            },
                            label = {
                                Text(
                                    if (isTranslateMangaEnabled) "Live: ${selectedSourceLang.second.uppercase()}→${selectedTargetLang.second.uppercase()}" else "Translate Manga",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        )

                        IconButton(
                            onClick = { showComposerSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Translation Composer & Style",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isTranslateMangaEnabled) {
                        Text(
                            text = "Long-press to peek",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

            if (viewModel.isExtractingImages) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(id = R.string.img_grab_extracting), color = textColor, fontSize = 13.sp)
                    }
                }
            } else if (localImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ImageNotSupported, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(id = R.string.img_grab_no_images), color = textColor, fontSize = 14.sp)
                    }
                }
            } else if (isMangaMode) {
                // ── Manga / Webtoon Vertical Continuous View Mode ──
                Box(modifier = Modifier.fillMaxSize()) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp) // Zero gap for seamless manga reading!
                    ) {
                        items(localImages.size) { index ->
                            MangaContinuousPageItem(
                                imgUrl = localImages[index],
                                pageUrl = viewModel.currentUrl,
                                index = index,
                                isTranslateEnabled = isTranslateMangaEnabled,
                                sourceLang = selectedSourceLang.second,
                                targetLang = selectedTargetLang.second,
                                typographyStyle = typographyStyle,
                                pipeline = mangaPipeline,
                                isDark = isDark
                            )
                        }
                    }

                    // Floating Page Indicator Badge
                    val firstVisible = remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.img_grab_page_indicator, firstVisible.value, localImages.size),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                // ── Grid Gallery Mode (with Top-Right X Delete Badge) ──
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(localImages.size) { index ->
                            val imgUrl = localImages[index]
                            var retryKey by remember(imgUrl) { mutableIntStateOf(0) }
                            val request = remember(imgUrl, viewModel.currentUrl, retryKey) {
                                val referer = ImageGrabberUtils.resolveReferer(imgUrl, viewModel.currentUrl)
                                coil.request.ImageRequest.Builder(context)
                                    .data(imgUrl)
                                    .apply {
                                        if (referer.isNotEmpty()) addHeader("Referer", referer)
                                        addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                        addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                    }
                                    .crossfade(true)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build()
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cardBg)
                            ) {
                                coil.compose.SubcomposeAsyncImage(
                                    model = request,
                                    imageLoader = coil.Coil.imageLoader(context),
                                    contentDescription = "Image ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    loading = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { retryKey++ },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = "Retry",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Retry",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                )

                                // Top-Right (X) Remove Badge Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Red.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clickable {
                                            localImages = localImages - imgUrl
                                            viewModel.extractedImagesList = localImages
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove Image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar for Downloads & Manga View Switch
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = cardBg,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.img_grab_pages_remaining, localImages.size),
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pendingDownloadType = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(id = R.string.img_grab_images_count, localImages.size), fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        pendingDownloadType = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(id = R.string.img_grab_as_pdf), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageInspectorSheetContent(
    viewModel: BrowserViewModel,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val bg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7)
    val cardBg = if (viewModel.isAmoledMode) Color(0xFF111111) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    var selectedTab by remember { mutableStateOf(initialTab) }
    var jsInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.inspectCurrentPage(context)
    }

    val stats = viewModel.pageInspectorStats

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = bg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .fillMaxHeight(0.85f)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "DevTools (Inspector & Console)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textColor)
                }
            }

            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text(stringResource(id = R.string.inspector_tab_overview), modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text(stringResource(id = R.string.inspector_tab_elements), modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text(stringResource(id = R.string.inspector_tab_network, stats?.resources?.size ?: 0), modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                    Text(stringResource(id = R.string.inspector_tab_console), modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 3) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 4, onClick = { selectedTab == 4 }) {
                    Text(stringResource(id = R.string.inspector_tab_storage), modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 4) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
            }

            if (stats == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (selectedTab) {
                    0 -> DevToolsOverviewTab(stats, viewModel.currentUrl, isDark, cardBg, textColor)
                    1 -> DevToolsElementsTab(stats, viewModel, isDark, cardBg, textColor)
                    2 -> DevToolsNetworkTab(stats, isDark, cardBg, textColor)
                    3 -> DevToolsConsoleTab(viewModel, jsInput, onJsChange = { jsInput = it }, isDark, cardBg, textColor)
                    4 -> DevToolsStorageTab(stats, isDark, cardBg, textColor)
                }
            }
        }
    }
}

@Composable
private fun DevToolsOverviewTab(
    stats: BrowserViewModel.PageStats,
    currentUrl: String,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color
) {
    val isHttps = currentUrl.startsWith("https://")
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // URL & Security Card
            Surface(shape = RoundedCornerShape(12.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (isHttps) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, contentDescription = null, tint = if (isHttps) Color(0xFF34C759) else Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                        Text(if (isHttps) stringResource(id = R.string.inspector_https_secure) else stringResource(id = R.string.inspector_http_unsecure), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isHttps) Color(0xFF34C759) else Color(0xFFFF9500))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stats.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(currentUrl, fontSize = 11.sp, color = textColor.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            // Stat Cards Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = cardBg) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(id = R.string.inspector_stat_reading), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(stringResource(id = R.string.inspector_stat_min, stats.readTimeMinutes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(stringResource(id = R.string.inspector_stat_words, stats.wordCount), fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = cardBg) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(id = R.string.inspector_stat_elements), fontSize = 10.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                        Text(stringResource(id = R.string.inspector_stat_imgs, stats.imageCount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(stringResource(id = R.string.inspector_stat_links, stats.linkCount), fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = cardBg) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(id = R.string.inspector_stat_assets), fontSize = 10.sp, color = Color(0xFFAF52DE), fontWeight = FontWeight.Bold)
                        Text(stringResource(id = R.string.inspector_stat_scripts, stats.scriptCount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(stringResource(id = R.string.inspector_stat_styles, stats.cssCount), fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
            }
        }

        if (stats.metaTags.isNotEmpty()) {
            item {
                Text(stringResource(id = R.string.inspector_seo_meta, stats.metaTags.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
            items(stats.metaTags.size) { idx ->
                val meta = stats.metaTags[idx]
                Surface(shape = RoundedCornerShape(8.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(meta.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(meta.content, fontSize = 12.sp, color = textColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DevToolsElementsTab(
    stats: BrowserViewModel.PageStats,
    viewModel: BrowserViewModel,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color
) {
    if (stats.domNodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.inspector_no_dom), color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(stringResource(id = R.string.inspector_dom_header, stats.domNodes.size), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.7f))
            }
            items(stats.domNodes.size) { idx ->
                val node = stats.domNodes[idx]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text("<${node.tag}>", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (node.id.isNotEmpty()) {
                                Text("#${node.id}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34C759))
                            }
                            if (node.className.isNotEmpty()) {
                                Text(".${node.className.take(25)}", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (node.snippet.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("\"${node.snippet}\"", fontSize = 11.sp, color = textColor.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevToolsNetworkTab(
    stats: BrowserViewModel.PageStats,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color
) {
    val totalBytes = stats.resources.sumOf { it.sizeBytes }
    if (stats.resources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.inspector_no_network), color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(id = R.string.inspector_network_requests, stats.resources.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Text(stringResource(id = R.string.inspector_kb_transferred, totalBytes / 1024), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(stats.resources.size) { idx ->
                    val res = stats.resources[idx]
                    val fileName = try { android.net.Uri.parse(res.url).lastPathSegment ?: res.url } catch(_: Exception) { res.url }
                    val badgeColor = when(res.type.lowercase()) {
                        "script" -> Color(0xFFAF52DE)
                        "img", "image" -> Color(0xFF007AFF)
                        "fetch", "xmlhttprequest" -> Color(0xFF34C759)
                        "css" -> Color(0xFFFF9500)
                        else -> Color.Gray
                    }

                    Surface(shape = RoundedCornerShape(8.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(4.dp), color = badgeColor.copy(alpha = 0.15f)) {
                                    Text(res.type.take(6), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                                Text(fileName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${res.durationMs}ms", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevToolsConsoleTab(
    viewModel: BrowserViewModel,
    jsInput: String,
    onJsChange: (String) -> Unit,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(id = R.string.inspector_console_cmds), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.7f))
        
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('a').forEach(a => a.style.outline = '2px solid gold')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(stringResource(id = R.string.inspector_cmd_links), fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('*').forEach(e => e.style.outline = '1px solid red')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(stringResource(id = R.string.inspector_cmd_outline), fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('input[type=\"hidden\"]').forEach(i => i.type = 'text')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(stringResource(id = R.string.inspector_cmd_hidden), fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.body.contentEditable = (document.body.contentEditable !== 'true')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(stringResource(id = R.string.inspector_cmd_edit), fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = jsInput,
                onValueChange = onJsChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("eval('document.title')", fontSize = 12.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            )
            Button(
                onClick = {
                    if (jsInput.isNotBlank()) {
                        viewModel.executeConsoleJs(jsInput)
                    }
                }
            ) {
                Text(stringResource(id = R.string.action_run))
            }
        }

        val result = viewModel.consoleEvalResult
        val isError = viewModel.consoleEvalError
        if (result != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (isError) Color(0xFF3C1414) else if (isDark) Color(0xFF0F1B12) else Color(0xFFE8F5E9)
            ) {
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(if (isError) stringResource(id = R.string.inspector_console_error) else stringResource(id = R.string.inspector_console_output), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isError) Color(0xFFFF453A) else Color(0xFF34C759))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(result, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (isError) Color(0xFFFF453A) else if (isDark) Color(0xFF34C759) else Color(0xFF1B5E20))
                }
            }
        }
    }
}

@Composable
private fun DevToolsStorageTab(
    stats: BrowserViewModel.PageStats,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(id = R.string.inspector_cookies, stats.cookies.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
        if (stats.cookies.isEmpty()) {
            item {
                Text(stringResource(id = R.string.inspector_no_cookies), fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
            }
        } else {
            items(stats.cookies.size) { idx ->
                val item = stats.cookies[idx]
                Surface(shape = RoundedCornerShape(8.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(item.key, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(item.value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(id = R.string.inspector_localstorage, stats.localStorageItems.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
        if (stats.localStorageItems.isEmpty()) {
            item {
                Text(stringResource(id = R.string.inspector_no_localstorage), fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
            }
        } else {
            items(stats.localStorageItems.size) { idx ->
                val item = stats.localStorageItems[idx]
                Surface(shape = RoundedCornerShape(8.dp), color = cardBg, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(item.key, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFAF52DE))
                        Text(item.value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaContinuousPageItem(
    imgUrl: String,
    pageUrl: String,
    index: Int,
    isTranslateEnabled: Boolean,
    sourceLang: String,
    targetLang: String,
    typographyStyle: com.rebelroot.omni.ai.manga.MangaTypographyStyle = com.rebelroot.omni.ai.manga.MangaTypographyStyle(),
    pipeline: com.rebelroot.omni.ai.manga.MangaTranslationPipeline?,
    isDark: Boolean,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isTranslating by remember { mutableStateOf(false) }
    var translatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBitmapRef by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var retryKey by remember(imgUrl) { mutableIntStateOf(0) }

    val request = remember(imgUrl, pageUrl, retryKey) {
        val referer = ImageGrabberUtils.resolveReferer(imgUrl, pageUrl)
        coil.request.ImageRequest.Builder(context)
            .data(imgUrl)
            .apply {
                if (referer.isNotEmpty()) addHeader("Referer", referer)
                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            }
            .allowHardware(false)
            .crossfade(true)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    LaunchedEffect(isTranslateEnabled, imgUrl, sourceLang, targetLang, typographyStyle, retryKey) {
        if (isTranslateEnabled && pipeline != null) {
            isTranslating = true
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                val result = imageLoader.execute(request)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val bmp = drawable.bitmap
                    originalBitmapRef = bmp
                    val res = pipeline.translateImage(imgUrl, bmp, sourceLang, targetLang, style = typographyStyle)
                    translatedBitmap = res.translatedBitmap
                }
            } catch (e: Exception) {
                translatedBitmap = null
            } finally {
                isTranslating = false
            }
        } else {
            translatedBitmap = null
            isTranslating = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        val currentTranslated = translatedBitmap
        if (isTranslateEnabled && currentTranslated != null && !showOriginal) {
            Image(
                bitmap = currentTranslated.asImageBitmap(),
                contentDescription = "Translated Manga Page ${index + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { showOriginal = !showOriginal }
                    ),
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
            )
        } else {
            coil.compose.SubcomposeAsyncImage(
                model = request,
                imageLoader = coil.Coil.imageLoader(context),
                contentDescription = "Manga Page ${index + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { if (translatedBitmap != null) showOriginal = !showOriginal }
                    ),
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                            .clickable { retryKey++ },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry Page ${index + 1}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tap to retry Page ${index + 1}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }

        // Translation Badges & Quick Edit Action
        if (isTranslateEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Edit Dialogue Button
                if (translatedBitmap != null && !isTranslating) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.clickable { showEditorSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Dialogue",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Edit", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isTranslating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.White
                            )
                            Text("Translating...", color = Color.White, fontSize = 10.sp)
                        }
                    } else if (showOriginal) {
                        Text("Original (Hold to switch)", color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Translated", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showEditorSheet) {
            val cachedBlocks = remember(imgUrl, sourceLang, targetLang) {
                pipeline?.getCachedBlocks(imgUrl, sourceLang, targetLang) ?: emptyList()
            }
            MangaDialogueEditorSheet(
                pageIndex = index,
                imgUrl = imgUrl,
                originalBitmap = originalBitmapRef,
                currentBlocks = cachedBlocks,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                typographyStyle = typographyStyle,
                pipeline = pipeline,
                onSaveAndApply = { _, updatedBmp ->
                    translatedBitmap = updatedBmp
                    showEditorSheet = false
                },
                onDismissRequest = { showEditorSheet = false },
                isDark = isDark
            )
        }
    }
}

@Composable
private fun MangaFullscreenViewer(
    images: List<String>,
    pageUrl: String = "",
    downloadEngine: com.rebelroot.omni.media.StreamDownloadEngine? = null,
    pipeline: com.rebelroot.omni.ai.manga.MangaTranslationPipeline?,
    initialTranslate: Boolean = false,
    sourceLanguage: String = "ja",
    targetLanguage: String = "en",
    initialTypographyStyle: com.rebelroot.omni.ai.manga.MangaTypographyStyle = com.rebelroot.omni.ai.manga.MangaTypographyStyle(),
    onTypographyStyleChange: (com.rebelroot.omni.ai.manga.MangaTypographyStyle) -> Unit = {},
    onSourceLangChange: (Pair<String, String>) -> Unit = {},
    onTargetLangChange: (Pair<String, String>) -> Unit = {},
    onExitFullscreen: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var isTranslateEnabled by remember { mutableStateOf(initialTranslate) }
    var selectedSourceLang by remember { mutableStateOf("Japanese" to sourceLanguage) }
    var selectedTargetLang by remember { mutableStateOf("English" to targetLanguage) }
    var typographyStyle by remember { mutableStateOf(initialTypographyStyle) }
    var showComposerSheet by remember { mutableStateOf(false) }
    var editingPageIndex by remember { mutableStateOf<Int?>(null) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstVisible = remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showComposerSheet) {
        MangaTranslationComposerSheet(
            sourceLang = selectedSourceLang,
            targetLang = selectedTargetLang,
            typographyStyle = typographyStyle,
            onSourceLangChange = {
                selectedSourceLang = it
                onSourceLangChange(it)
            },
            onTargetLangChange = {
                selectedTargetLang = it
                onTargetLangChange(it)
            },
            onStyleChange = {
                typographyStyle = it
                onTypographyStyleChange(it)
            },
            onDismissRequest = { showComposerSheet = false },
            isDark = true
        )
    }

    editingPageIndex?.let { editIdx ->
        val currentImgUrl = images.getOrNull(editIdx) ?: ""
        val cachedBlocks = remember(currentImgUrl, selectedSourceLang.second, selectedTargetLang.second) {
            pipeline?.getCachedBlocks(currentImgUrl, selectedSourceLang.second, selectedTargetLang.second) ?: emptyList()
        }
        MangaDialogueEditorSheet(
            pageIndex = editIdx,
            imgUrl = currentImgUrl,
            originalBitmap = null,
            currentBlocks = cachedBlocks,
            sourceLanguage = selectedSourceLang.second,
            targetLanguage = selectedTargetLang.second,
            typographyStyle = typographyStyle,
            pipeline = pipeline,
            downloadEngine = downloadEngine,
            onSaveAndApply = { _, _ ->
                editingPageIndex = null
            },
            onDismissRequest = { editingPageIndex = null },
            isDark = true
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onExitFullscreen,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        ) {
            // Continuous Vertical Manga Scroll
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(images.size) { index ->
                    MangaContinuousPageItem(
                        imgUrl = images[index],
                        pageUrl = pageUrl,
                        index = index,
                        isTranslateEnabled = isTranslateEnabled,
                        sourceLang = selectedSourceLang.second,
                        targetLang = selectedTargetLang.second,
                        typographyStyle = typographyStyle,
                        pipeline = pipeline,
                        isDark = true,
                        onClick = { showControls = !showControls }
                    )
                }
            }

            // Top Control Bar Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Fullscreen Manga Reader",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Live Translation Toggle
                            IconButton(onClick = { isTranslateEnabled = !isTranslateEnabled }) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Translate Manga",
                                    tint = if (isTranslateEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Manual Dialogue Editor Button
                            if (isTranslateEnabled) {
                                IconButton(onClick = { editingPageIndex = (firstVisible.value - 1).coerceIn(0, images.size - 1) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.EditNote,
                                        contentDescription = "Edit Dialogue on Current Page",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Translation Composer Settings
                            IconButton(onClick = { showComposerSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Customize Manga Translation",
                                    tint = if (isTranslateEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(onClick = onExitFullscreen) {
                                Icon(
                                    imageVector = Icons.Rounded.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating HUD (Page X of Y)
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.85f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Page ${firstVisible.value} of ${images.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                downloadMangaImagesAndPdf(context, images, pageUrl = pageUrl, asPdf = false, downloadEngine = downloadEngine)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Download Images",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                downloadMangaImagesAndPdf(context, images, pageUrl = pageUrl, asPdf = true, downloadEngine = downloadEngine)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = "Download PDF",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Manga Translation Composer Bottom Sheet.
 * Lets users customize Source & Target languages, font scale, font typeface,
 * speech bubble background fill, and text colors in real time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaTranslationComposerSheet(
    sourceLang: Pair<String, String>,
    targetLang: Pair<String, String>,
    typographyStyle: com.rebelroot.omni.ai.manga.MangaTypographyStyle,
    onSourceLangChange: (Pair<String, String>) -> Unit,
    onTargetLangChange: (Pair<String, String>) -> Unit,
    onStyleChange: (com.rebelroot.omni.ai.manga.MangaTypographyStyle) -> Unit,
    onDismissRequest: () -> Unit,
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
) {
    val context = LocalContext.current
    var applyToAll by remember { mutableStateOf(com.rebelroot.omni.ai.manga.MangaPreferences.loadApplyToAll(context)) }

    val bg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    val handleSourceChange = { newSrc: Pair<String, String> ->
        onSourceLangChange(newSrc)
        if (applyToAll) {
            com.rebelroot.omni.ai.manga.MangaPreferences.saveLanguages(context, newSrc, targetLang)
        }
    }
    val handleTargetChange = { newTgt: Pair<String, String> ->
        onTargetLangChange(newTgt)
        if (applyToAll) {
            com.rebelroot.omni.ai.manga.MangaPreferences.saveLanguages(context, sourceLang, newTgt)
        }
    }
    val handleStyleChange = { newStyle: com.rebelroot.omni.ai.manga.MangaTypographyStyle ->
        onStyleChange(newStyle)
        if (applyToAll) {
            com.rebelroot.omni.ai.manga.MangaPreferences.saveTypographyStyle(context, newStyle)
        }
    }

    val sourceOptions = listOf(
        "Auto Detect" to "auto",
        "Japanese (日本語)" to "ja",
        "Chinese (中文)" to "zh",
        "Korean (한국어)" to "ko",
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de"
    )

    val targetOptions = listOf(
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Japanese" to "ja",
        "Chinese" to "zh",
        "Korean" to "ko",
        "Hindi" to "hi",
        "Portuguese" to "pt",
        "Russian" to "ru",
        "Arabic" to "ar",
        "Indonesian" to "id",
        "Vietnamese" to "vi"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = bg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Manga Translation Composer",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                TextButton(
                    onClick = {
                        val def = com.rebelroot.omni.ai.manga.MangaTypographyStyle()
                        handleStyleChange(def)
                    }
                ) {
                    Text("Reset", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Apply to All Translations Global Switch
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AllInclusive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Apply to all translations",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "Use these settings as global defaults for all pages & future manga",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
                            )
                        }
                    }
                    Switch(
                        checked = applyToAll,
                        onCheckedChange = { checked ->
                            applyToAll = checked
                            com.rebelroot.omni.ai.manga.MangaPreferences.saveApplyToAll(context, checked)
                            if (checked) {
                                com.rebelroot.omni.ai.manga.MangaPreferences.saveTypographyStyle(context, typographyStyle)
                                com.rebelroot.omni.ai.manga.MangaPreferences.saveLanguages(context, sourceLang, targetLang)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            HorizontalDivider(color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // 1. Language Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Source Language (OCR)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sourceOptions) { (name, code) ->
                        FilterChip(
                            selected = sourceLang.second == code,
                            onClick = { handleSourceChange(name to code) },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Target Language",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(targetOptions) { (name, code) ->
                        FilterChip(
                            selected = targetLang.second == code,
                            onClick = { handleTargetChange(name to code) },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // 2. Font Size Scaling
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Font Size Scale",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "${(typographyStyle.fontSizeScale * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val newScale = (typographyStyle.fontSizeScale - 0.15f).coerceAtLeast(0.60f)
                            handleStyleChange(typographyStyle.copy(fontSizeScale = newScale))
                        }
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease Font Size")
                    }

                    LazyRow(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(listOf("80%" to 0.80f, "100%" to 1.0f, "130%" to 1.30f, "160%" to 1.60f, "200%" to 2.00f, "250%" to 2.50f, "300%" to 3.00f)) { (lbl, scale) ->
                            FilterChip(
                                selected = (typographyStyle.fontSizeScale - scale).let { it > -0.06f && it < 0.06f },
                                onClick = { handleStyleChange(typographyStyle.copy(fontSizeScale = scale)) },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val newScale = (typographyStyle.fontSizeScale + 0.15f).coerceAtMost(3.00f)
                            handleStyleChange(typographyStyle.copy(fontSizeScale = newScale))
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase Font Size")
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // 3. Font Family
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Font Typeface",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Comic Bold" to "Comic",
                        "Sans-Serif" to "Sans",
                        "Classic Serif" to "Serif",
                        "Clean Mono" to "Clean"
                    ).forEach { (label, key) ->
                        FilterChip(
                            selected = typographyStyle.fontFamily == key,
                            onClick = { handleStyleChange(typographyStyle.copy(fontFamily = key)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // 4. Background & Text Colors
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Bubble Background",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Auto Snap" to "Auto",
                        "Pure White" to "White",
                        "Transparent" to "Transparent"
                    ).forEach { (label, key) ->
                        FilterChip(
                            selected = typographyStyle.bgFillMode == key,
                            onClick = { handleStyleChange(typographyStyle.copy(bgFillMode = key)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Text Color Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Auto Contrast" to "Auto",
                        "Pure Black" to "Black",
                        "White Outline" to "White"
                    ).forEach { (label, key) ->
                        FilterChip(
                            selected = typographyStyle.textColorMode == key,
                            onClick = { handleStyleChange(typographyStyle.copy(textColorMode = key)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply & Close", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Interactive Dialogue Editor Bottom Sheet.
 * Lets users review all detected speech balloons on a page, edit translated text,
 * re-translate specific bubbles, add missing bubbles, and save/download the edited page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDialogueEditorSheet(
    pageIndex: Int,
    imgUrl: String,
    originalBitmap: Bitmap?,
    currentBlocks: List<com.rebelroot.omni.ai.manga.MangaDialogueBlock>,
    sourceLanguage: String,
    targetLanguage: String,
    typographyStyle: com.rebelroot.omni.ai.manga.MangaTypographyStyle,
    pipeline: com.rebelroot.omni.ai.manga.MangaTranslationPipeline?,
    downloadEngine: com.rebelroot.omni.media.StreamDownloadEngine? = null,
    onSaveAndApply: (List<com.rebelroot.omni.ai.manga.MangaDialogueBlock>, Bitmap) -> Unit,
    onDismissRequest: () -> Unit,
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    var editedBlocks by remember(currentBlocks) { mutableStateOf(currentBlocks.toMutableList()) }
    var isSaving by remember { mutableStateOf(false) }
    var translatingIndex by remember { mutableStateOf<Int?>(null) }
    var activeBitmap by remember { mutableStateOf(originalBitmap) }

    // If originalBitmap was not passed down directly, load it via Coil
    LaunchedEffect(imgUrl) {
        if (activeBitmap == null) {
            try {
                val req = coil.request.ImageRequest.Builder(context)
                    .data(imgUrl)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(context).execute(req)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    activeBitmap = drawable.bitmap
                }
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = bg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Edit Page ${pageIndex + 1} Dialogue",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "${editedBlocks.size} speech bubbles detected",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textColor)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // Speech Bubbles List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (editedBlocks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No speech bubbles found on this page.\nTap below to add a custom dialogue block.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
                            )
                        }
                    }
                }

                items(editedBlocks.size) { idx ->
                    val block = editedBlocks[idx]
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = if (block.isVertical) "Vertical Bubble" else "Horizontal Block",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Re-Translate Button
                                    IconButton(
                                        onClick = {
                                            if (pipeline != null && block.rawText.isNotEmpty()) {
                                                scope.launch {
                                                    translatingIndex = idx
                                                    val retranslated = pipeline.retranslateText(block.rawText, sourceLanguage, targetLanguage)
                                                    val updatedList = editedBlocks.toMutableList()
                                                    updatedList[idx] = block.copy(translatedText = retranslated)
                                                    editedBlocks = updatedList
                                                    translatingIndex = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (translatingIndex == idx) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Translate,
                                                contentDescription = "Re-Translate",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Delete Bubble Button
                                    IconButton(
                                        onClick = {
                                            val updatedList = editedBlocks.toMutableList()
                                            updatedList.removeAt(idx)
                                            editedBlocks = updatedList
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Remove Bubble",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Original OCR Raw Text
                            if (block.rawText.isNotEmpty()) {
                                Text(
                                    text = "Original: ${block.rawText}",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF555555),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Editable Translation Field
                            OutlinedTextField(
                                value = block.translatedText,
                                onValueChange = { newText ->
                                    val updatedList = editedBlocks.toMutableList()
                                    updatedList[idx] = block.copy(translatedText = newText)
                                    editedBlocks = updatedList
                                },
                                label = { Text("Translated Dialogue", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(fontSize = 13.sp, color = textColor)
                            )
                        }
                    }
                }

                // Add Custom Bubble Button
                item {
                    OutlinedButton(
                        onClick = {
                            val newBlock = com.rebelroot.omni.ai.manga.MangaDialogueBlock(
                                id = "custom_${System.currentTimeMillis()}",
                                rawText = "",
                                translatedText = "",
                                boundingBox = com.rebelroot.omni.ai.manga.MangaRect(50f, 50f, 250f, 150f)
                            )
                            val updatedList = editedBlocks.toMutableList()
                            updatedList.add(newBlock)
                            editedBlocks = updatedList
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Custom Dialogue Bubble", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

            // Bottom Actions (Save & Apply + Download Image / PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download Image
                OutlinedButton(
                    onClick = {
                        val bmp = activeBitmap
                        if (bmp != null && pipeline != null) {
                            scope.launch {
                                val res = pipeline.applyCustomBlocks(imgUrl, bmp, sourceLanguage, targetLanguage, editedBlocks, typographyStyle)
                                saveOrDownloadSingleMangaPage(context, res.translatedBitmap, pageIndex, asPdf = false, saveToLocker = false, downloadEngine = downloadEngine)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save JPG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Download PDF
                OutlinedButton(
                    onClick = {
                        val bmp = activeBitmap
                        if (bmp != null && pipeline != null) {
                            scope.launch {
                                val res = pipeline.applyCustomBlocks(imgUrl, bmp, sourceLanguage, targetLanguage, editedBlocks, typographyStyle)
                                saveOrDownloadSingleMangaPage(context, res.translatedBitmap, pageIndex, asPdf = true, saveToLocker = false, downloadEngine = downloadEngine)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Save & Apply Changes
                Button(
                    onClick = {
                        val bmp = activeBitmap
                        if (bmp != null && pipeline != null) {
                            scope.launch {
                                isSaving = true
                                val res = pipeline.applyCustomBlocks(imgUrl, bmp, sourceLanguage, targetLanguage, editedBlocks, typographyStyle)
                                onSaveAndApply(editedBlocks, res.translatedBitmap)
                                isSaving = false
                                onDismissRequest()
                            }
                        } else {
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Saves or downloads a single rendered manga bitmap as JPEG image or PDF to Downloads / Vault.
 */
fun saveOrDownloadSingleMangaPage(
    context: android.content.Context,
    bitmap: Bitmap,
    pageIndex: Int,
    asPdf: Boolean = false,
    saveToLocker: Boolean = false,
    downloadEngine: com.rebelroot.omni.media.StreamDownloadEngine? = null
) {
    val appCtx = context.applicationContext
    val timeStamp = System.currentTimeMillis() / 1000
    val fileName = if (asPdf) "Manga_Page_${pageIndex + 1}_$timeStamp.pdf" else "Manga_Page_${pageIndex + 1}_$timeStamp.jpg"
    val mimeType = if (asPdf) "application/pdf" else "image/jpeg"
    val destText = if (saveToLocker) "Private Vault" else "Downloads"

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            if (saveToLocker) {
                val lockerManager = com.rebelroot.omni.tools.locker.PrivateLockerManager(appCtx)
                val tempFile = java.io.File(appCtx.cacheDir, fileName)
                java.io.FileOutputStream(tempFile).use { out ->
                    if (asPdf) {
                        val doc = android.graphics.pdf.PdfDocument()
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                        val page = doc.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        doc.finishPage(page)
                        doc.writeTo(out)
                        doc.close()
                    } else {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
                lockerManager.saveFileToLocker(tempFile, fileName, mimeType)
                if (tempFile.exists()) tempFile.delete()
            } else {
                val resolver = appCtx.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/OmniBrowser")
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (asPdf) {
                            val doc = android.graphics.pdf.PdfDocument()
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                            val page = doc.startPage(pageInfo)
                            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            doc.finishPage(page)
                            doc.writeTo(out)
                            doc.close()
                        } else {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    }
                }
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(appCtx, "Saved Page ${pageIndex + 1} to $destText!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(appCtx, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


fun downloadMangaImagesAndPdf(
    context: android.content.Context,
    urls: List<String>,
    pageUrl: String = "",
    asPdf: Boolean = false,
    saveToLocker: Boolean = false,
    downloadEngine: com.rebelroot.omni.media.StreamDownloadEngine? = null
) {
    if (urls.isEmpty()) return
    val appCtx = context.applicationContext
    val targetCount = urls.size
    val destText = if (saveToLocker) context.getString(R.string.download_destination_vault) else context.getString(R.string.downloads_title)
    val modeText = if (asPdf) context.getString(R.string.manga_mode_pdf, targetCount) else context.getString(R.string.manga_mode_images, targetCount)
    Toast.makeText(appCtx, context.getString(R.string.manga_toast_starting, modeText, destText), Toast.LENGTH_SHORT).show()

    val timeStamp = System.currentTimeMillis() / 1000
    val filename = if (asPdf) "Manga_$timeStamp.pdf" else "Manga_$timeStamp ($targetCount images)"

    val registered = downloadEngine?.registerExternalJob(
        filename = filename,
        url = pageUrl,
        saveToLocker = saveToLocker,
        isGeneric = true
    )
    val jobId = registered?.first

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val referer = try {
            if (pageUrl.isNotEmpty()) {
                val uri = android.net.Uri.parse(pageUrl)
                "${uri.scheme}://${uri.host}/"
            } else ""
        } catch (_: Exception) { "" }

        val folderName = "Manga_$timeStamp"
        val resolver = appCtx.contentResolver
        val loader = coil.Coil.imageLoader(appCtx)
        val lockerManager = if (saveToLocker) com.rebelroot.omni.tools.locker.PrivateLockerManager(appCtx) else null

        val pdfDocument = if (asPdf) android.graphics.pdf.PdfDocument() else null
        var successCount = 0
        var totalBytesDownloaded = 0L
        var firstSavedUri: android.net.Uri? = null
        var firstSavedFile: java.io.File? = null
        val downloadedCount = java.util.concurrent.atomic.AtomicInteger(0)

        // Download images in parallel (4 at a time) for maximum speed
        val semaphore = Semaphore(4)
        coroutineScope {
            val deferreds = urls.mapIndexed { index, url ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val reqReferer = if (referer.isNotEmpty()) referer else ImageGrabberUtils.resolveReferer(url, pageUrl)
                        val request = coil.request.ImageRequest.Builder(appCtx)
                            .data(url)
                            .apply {
                                if (reqReferer.isNotEmpty()) addHeader("Referer", reqReferer)
                                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            }
                            // Only disable hardware bitmaps for PNG — hardware bitmaps
                            // cannot be read by BitmapDrawable.bitmap for CPU access.
                            // JPEG pages use hardware-backed bitmaps where possible;
                            // Bitmap.compress() copies to software internally when needed.
                            .allowHardware(!url.contains(".png", ignoreCase = true))
                            .size(coil.size.Size.ORIGINAL) // Full original resolution — no downsampling
                            .build()

                        val result = loader.execute(request)
                        val drawable = result.drawable
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            val bitmap = drawable.bitmap

                            if (asPdf && pdfDocument != null) {
                                synchronized(pdfDocument) {
                                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                    pdfDocument.finishPage(page)
                                }
                                successCount++
                            } else {
                                val isPng = url.contains(".png", true)
                                val ext = if (isPng) ".png" else ".jpg"
                                val mimeType = if (isPng) "image/png" else "image/jpeg"
                                val fileName = "${folderName}_page_${index + 1}$ext"

                                if (saveToLocker && lockerManager != null) {
                                    val tempFile = java.io.File(appCtx.cacheDir, fileName)
                                    java.io.FileOutputStream(tempFile).use { out ->
                                        if (isPng) {
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                        } else {
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                                        }
                                    }
                                    totalBytesDownloaded += tempFile.length()
                                    if (firstSavedFile == null) firstSavedFile = tempFile
                                    lockerManager.saveFileToLocker(tempFile, fileName, mimeType)
                                    if (tempFile.exists()) tempFile.delete()
                                    successCount++
                                } else {
                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "page_${index + 1}$ext")
                                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/OmniBrowser/$folderName")
                                    }
                                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        if (firstSavedUri == null) firstSavedUri = uri
                                        resolver.openOutputStream(uri)?.use { out ->
                                            if (isPng) {
                                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                            } else {
                                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                                            }
                                        }
                                        successCount++
                                    }
                                }
                            }
                            // Recycle the decoded bitmap immediately after writing —
                            // each manga page can be several MB and there are up to 4
                            // concurrent downloads; holding them all would spike RSS.
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }

                        val done = downloadedCount.incrementAndGet()
                        val percent = (done * 100) / targetCount
                        if (jobId != null) {
                            downloadEngine?.updateExternalJobProgress(
                                jobId = jobId,
                                filename = filename,
                                progress = percent,
                                statusText = "$done of $targetCount pages downloaded",
                                bytesDownloaded = totalBytesDownloaded
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MangaDownload", "Error fetching page ${index + 1}: $url", e)
                        downloadedCount.incrementAndGet()
                    } finally {
                        semaphore.release()
                    }
                }
            }
            deferreds.awaitAll()
        }

        if (asPdf && pdfDocument != null) {
            try {
                val pdfFileName = "Manga_$timeStamp.pdf"
                if (saveToLocker && lockerManager != null) {
                    val tempPdfFile = java.io.File(appCtx.cacheDir, pdfFileName)
                    java.io.FileOutputStream(tempPdfFile).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()
                    val pdfBytes = tempPdfFile.length()
                    lockerManager.saveFileToLocker(tempPdfFile, pdfFileName, "application/pdf")
                    if (jobId != null) {
                        downloadEngine?.completeExternalJob(jobId, filename, tempPdfFile, pdfBytes, null)
                    }
                    if (tempPdfFile.exists()) tempPdfFile.delete()

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(appCtx, context.getString(R.string.manga_toast_pdf_vault, successCount), Toast.LENGTH_LONG).show()
                    }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val targetPdfFile = java.io.File(downloadsDir, "OmniBrowser/$pdfFileName")
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/OmniBrowser")
                    }
                    val pdfUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (pdfUri != null) {
                        resolver.openOutputStream(pdfUri)?.use { out ->
                            pdfDocument.writeTo(out)
                        }
                    }
                    pdfDocument.close()
                    if (jobId != null) {
                        downloadEngine?.completeExternalJob(jobId, filename, targetPdfFile, targetPdfFile.length(), pdfUri)
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(appCtx, context.getString(R.string.manga_toast_pdf_downloads, successCount, pdfFileName), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaDownload", "Error writing PDF", e)
                if (jobId != null) {
                    downloadEngine?.failExternalJob(jobId, filename, e.localizedMessage ?: "PDF Error")
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(appCtx, context.getString(R.string.manga_toast_pdf_failed, e.localizedMessage), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val folderFile = java.io.File(downloadsDir, "OmniBrowser/$folderName")
            if (jobId != null) {
                downloadEngine?.completeExternalJob(jobId, filename, folderFile, totalBytesDownloaded, firstSavedUri)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = if (saveToLocker) context.getString(R.string.manga_toast_images_vault, successCount) else context.getString(R.string.manga_toast_images_downloads, successCount, folderName)
                Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllInOneMenuSheet(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenHistory: () -> Unit,
    onBurnData: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowFeedbackDialog: () -> Unit = {},
    onShowCustomizationSheet: () -> Unit,
    onShowExtensions: () -> Unit,
    onShowPlayerSettings: () -> Unit,
    onShowSiteInfo: () -> Unit,
    onFindInPage: () -> Unit,
    onAddTabToNewGroup: () -> Unit,
    hasActiveUserExtensions: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val isAmoled = viewModel.isAmoledMode
    val showHomeScreen = viewModel.currentUrl.isEmpty() || viewModel.currentUrl.startsWith("file:///android_asset/home")
    
    // Firefox inspired but better!
    val sheetBg = if (isAmoled) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF9F9FB)
    val cardBg = if (isAmoled) Color(0xFF0C0C0E) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val secondaryText = if (isDark) Color(0xFFA0A0A5) else Color(0xFF8E8E93)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
    val isBookmarked = viewModel.isBookmarked(viewModel.currentUrl)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0xFF48484A) else Color(0xFFC7C7CC),
                width = 32.dp,
                height = 3.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            
            // --- Card 0: Tab Actions ---
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AllInOneGridItem(
                        icon = Icons.Rounded.Add,
                        label = stringResource(id = R.string.menu_new_tab),
                        tint = textColor,
                        onClick = { onDismissRequest(); onNewTab() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.VisibilityOff,
                        label = stringResource(id = R.string.menu_incognito),
                        tint = textColor,
                        onClick = { onDismissRequest(); onNewIncognitoTab() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.GridView,
                        label = stringResource(id = R.string.menu_group),
                        tint = textColor,
                        onClick = { onDismissRequest(); onAddTabToNewGroup() }
                    )
                    val isHideNavActive = viewModel.navBarHideTop || viewModel.navBarHideBottom
                    AllInOneGridItem(
                        icon = if (isHideNavActive) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                        label = stringResource(id = R.string.menu_hide_nav),
                        tint = if (isHideNavActive) MaterialTheme.colorScheme.primary else textColor,
                        onClick = {
                            val newHideState = !isHideNavActive
                            viewModel.saveNavBarHideTop(context, newHideState)
                            viewModel.saveNavBarHideBottom(context, newHideState)
                        }
                    )
                }
            }

            // --- Card 1: Page Actions ---
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    // Bookmark Page — only shown when a webpage is active
                    if (!showHomeScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                if (activeTab != null) {
                                    if (isBookmarked) {
                                        viewModel.removeBookmark(viewModel.currentUrl)
                                    } else {
                                        viewModel.addToBookmarks(activeTab.title ?: "Page", viewModel.currentUrl)
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isBookmarked) stringResource(id = R.string.menu_remove_bookmark) else stringResource(id = R.string.menu_bookmark_page),
                            fontSize = 14.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Add to Shortcuts
                    if (activeTab != null && activeTab.url != "about:blank" && viewModel.currentUrl != "about:blank") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissRequest()
                                    viewModel.addShortcut(activeTab.title ?: "Page", viewModel.currentUrl)
                                }
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add to Shortcuts",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(id = R.string.menu_add_to_shortcuts), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Find in Page
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissRequest(); onFindInPage() }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Find",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(id = R.string.menu_find_in_page), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Desktop Site
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissRequest(); viewModel.toggleDesktopMode(context) }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = "Desktop Site",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(id = R.string.menu_desktop_site), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .background(if (viewModel.isDesktopMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else dividerColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (viewModel.isDesktopMode) stringResource(id = R.string.on) else stringResource(id = R.string.off),
                                fontSize = 11.sp,
                                color = if (viewModel.isDesktopMode) MaterialTheme.colorScheme.primary else textColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))
                    } // end !showHomeScreen

                    // Extensions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                onShowExtensions()
                            }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = stringResource(id = R.string.ext_menu_cd),
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.menu_extensions), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(stringResource(id = R.string.menu_extensions_subtext), fontSize = 11.sp, color = secondaryText)
                        }
                        
                        // Badge for Extensions Count
                        if (hasActiveUserExtensions) {
                            Box(
                                modifier = Modifier
                                    .background(dividerColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Box(modifier = Modifier.size(7.dp).background(Color(0xFF8B5CF6), androidx.compose.foundation.shape.CircleShape))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // --- Card 2: Grid Menu (History, Bookmarks, Downloads, Burn Data) ---
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AllInOneGridItem(
                        icon = Icons.Rounded.History,
                        label = stringResource(id = R.string.history_title),
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenHistory() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Bookmark,
                        label = stringResource(id = R.string.bookmarks_title),
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenBookmarks() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Download,
                        label = stringResource(id = R.string.downloads_title),
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenDownloads() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Whatshot,
                        label = stringResource(id = R.string.menu_burn_data),
                        tint = Color(0xFFFF4444),
                        onClick = { onDismissRequest(); onBurnData() }
                    )
                }
            }

            // --- Card 3: Theme & Settings ---
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AllInOneGridItem(
                        icon = Icons.Rounded.PlayCircle,
                        label = stringResource(id = R.string.menu_player),
                        tint = textColor,
                        onClick = { onDismissRequest(); onShowPlayerSettings() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Palette,
                        label = stringResource(id = R.string.menu_theme),
                        tint = textColor,
                        onClick = { onDismissRequest(); onShowThemeSheet() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Settings,
                        label = stringResource(id = R.string.settings_title),
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenSettings() }
                    )
                    AllInOneGridItem(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        label = stringResource(id = R.string.menu_help),
                        tint = textColor,
                        onClick = { onDismissRequest(); onShowFeedbackDialog() }
                    )
                }
            }

            // --- Bottom Navigation Row (Back, Forward, Share, Refresh) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val canGoBack = activeTab?.canGoBack == true && !showHomeScreen
                val canGoForward = activeTab?.canGoForward == true

                AllInOneBottomAction(
                    icon = Icons.Rounded.ArrowBack,
                    label = stringResource(id = R.string.menu_back),
                    enabled = canGoBack,
                    onClick = { viewModel.goBack() }
                )
                AllInOneBottomAction(
                    icon = Icons.Rounded.ArrowForward,
                    label = stringResource(id = R.string.menu_forward),
                    enabled = canGoForward,
                    onClick = { viewModel.goForward() }
                )
                AllInOneBottomAction(
                    icon = Icons.Rounded.Share,
                    label = stringResource(id = R.string.menu_share),
                    enabled = activeTab != null && !showHomeScreen,
                    onClick = {
                        onDismissRequest()
                        val sendIntent: android.content.Intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, viewModel.currentUrl)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(shareIntent)
                    }
                )
                AllInOneBottomAction(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(id = R.string.menu_refresh),
                    enabled = activeTab != null && !showHomeScreen,
                    onClick = { viewModel.reload(); onDismissRequest() }
                )
            }
        }
    }
}

@Composable
fun AllInOneGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AllInOneBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.3f
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val isAmoled = viewModel.isAmoledMode
    val sheetBg = if (isAmoled) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF9F9FB)
    val cardBg = if (isAmoled) Color(0xFF0C0C0E) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val secondaryText = if (isDark) Color(0xFFA0A0A5) else Color(0xFF8E8E93)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val accentColor = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0xFF48484A) else Color(0xFFC7C7CC),
                width = 32.dp,
                height = 3.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Theme",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Theme Mode: Light | Dark | AMOLED
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme Mode", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val themeMode = when {
                            viewModel.isAmoledMode -> 3
                            viewModel.isDarkThemeEnabled -> 2
                            viewModel.isCreamyMode -> 1
                            else -> 0
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf("Light", "Creamy", "Dark", "AMOLED")
                            options.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = {
                                        when (index) {
                                            0 -> {
                                                viewModel.saveDarkTheme(context, false)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                            1 -> {
                                                viewModel.saveDarkTheme(context, false)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, true)
                                            }
                                            2 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                            3 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, true)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                        }
                                    },
                                    selected = themeMode == index,
                                    icon = {}
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (themeMode == index) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // 2. App Nav Scaler
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("App Nav Scaler", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${(viewModel.uiScale * 100).toInt()}%",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = viewModel.uiScale,
                            onValueChange = { newValue ->
                                val steppedValue = ((newValue / 0.05f) + 0.5f).toInt() * 0.05f
                                viewModel.saveUiScale(context, steppedValue.coerceIn(0.8f, 1.3f))
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else Color(0xFFE0E0E0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Smaller", color = secondaryText, fontSize = 11.sp)
                            Text("Default", color = secondaryText, fontSize = 11.sp)
                            Text("Larger", color = secondaryText, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // 3. Accent Color
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Accent Color",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val accentOptions = listOf(
                            "Ocean Blue" to Color(0xFF0A84FF),
                            "Crimson Red" to Color(0xFFFF3B5C),
                            "Emerald Green" to Color(0xFF00C853),
                            "Sunset Orange" to Color(0xFFFF6D00),
                            "Royal Purple" to Color(0xFF7C4DFF),
                            "Monochrome" to Color(0xFFAAAAAA)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            accentOptions.forEach { (name, color) ->
                                val isSelected = viewModel.selectedAccentTheme == name
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (isSelected)
                                                Modifier.border(3.dp, textColor.copy(alpha = 0.6f), CircleShape)
                                            else Modifier
                                        )
                                        .clickable { viewModel.saveAccentTheme(context, name) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
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
}

@Composable
fun HelpFeedbackDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val dialogBg = if (isDark && viewModel.isAmoledMode) Color(0xFF0C0D10) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondaryColor = if (isDark) Color(0xFFA0A0A5) else Color(0xFF8E8E93)
    val cardBorderColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val accentColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = dialogBg,
        title = {
            Text(
                text = "Help & Feedback",
                color = textPrimaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Send your feedback directly to the development team's Telegram bot. Thank you for helping us improve!",
                    color = textSecondaryColor,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = textSecondaryColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = cardBorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = textSecondaryColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = cardBorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rating", color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            IconButton(
                                onClick = { rating = i },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "$i Stars",
                                    tint = if (i <= rating) Color(0xFFFFD700) else textSecondaryColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Message / Suggestion") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = textSecondaryColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = cardBorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.sendFeedbackToTelegram(name, email, rating, comment) { success, error ->
                        isSubmitting = false
                        if (success) {
                            Toast.makeText(context, "Feedback sent successfully!", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        } else {
                            Toast.makeText(context, "Failed to send: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = comment.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Send Feedback")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = textSecondaryColor)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onShowSnifferSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val isAmoled = viewModel.isAmoledMode
    val sheetBg = if (isAmoled) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF9F9FB)
    val cardBg = if (isAmoled) Color(0xFF0C0C0E) else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val secondaryText = if (isDark) Color(0xFFA0A0A5) else Color(0xFF8E8E93)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val accentColor = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0xFF48484A) else Color(0xFFC7C7CC),
                width = 32.dp,
                height = 3.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        stringResource(id = R.string.menu_player_settings),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = secondaryText)
                }
            }

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // Enable Native Player
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Native Player", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Play web videos in native high-performance player", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isNativePlayerEnabled,
                            onCheckedChange = { viewModel.toggleNativePlayer(context) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Media Sniffer / Fetcher
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Media Sniffer / Fetcher", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Detect web page videos and display sniffer banner", fontSize = 11.sp, color = secondaryText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onShowSnifferSettings) {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = "Sniffer Settings",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Switch(
                                checked = viewModel.isMediaGrabberEnabled,
                                onCheckedChange = { viewModel.toggleMediaGrabber(context) },
                                colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                            )
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Auto-Play
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Start playback automatically when video opens", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerAutoPlayEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "autoplay", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Loop Playback
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Loop Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Repeat video playback automatically in a loop", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerLoopEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "loop", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Brightness Gestures
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Brightness Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Swipe vertically on left side to adjust brightness", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerBrightnessGestureEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "brightness_gesture", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Volume Gestures
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Volume Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Swipe vertically on right side to adjust volume", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerVolumeGestureEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "volume_gesture", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Resume Playback
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Resume Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Remember position and resume where you left off", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerResumePlaybackEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "resume", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Background Playback
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Continue audio playback when app is minimized", fontSize = 11.sp, color = secondaryText)
                        }
                        Switch(
                            checked = viewModel.isPlayerBackgroundPlaybackEnabled,
                            onCheckedChange = { viewModel.savePlayerSetting(context, "background", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Default Quality Limit
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Quality Limit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text("Maximum resolution to select automatically", fontSize = 11.sp, color = secondaryText)
                        }
                        var expandedQuality by remember { mutableStateOf(false) }
                        val qualities = listOf("Auto", "360p", "480p", "720p", "1080p")
                        Box {
                            TextButton(onClick = { expandedQuality = true }) {
                                Text(
                                    text = viewModel.playerDefaultQuality,
                                    color = accentColor,
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
            }
        }
    }
}

