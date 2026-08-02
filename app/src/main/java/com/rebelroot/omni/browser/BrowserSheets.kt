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
            val finalTitle = if (noteTitle.isBlank()) "Untitled Note" else noteTitle
            if (currentNote == null) {
                viewModel.addDevNote(finalTitle, noteContent.text, noteType)
            } else {
                viewModel.updateDevNote(currentNote.id, finalTitle, noteContent.text, noteType)
            }
        }
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
                                Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                        text = "Title...",
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
                                            text = "Write your notes here...",
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
                                label = { Text("Paste") },
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
                                    label = { Text("Generate Password") },
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
                            text = "Notepad & Vault",
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
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search your notes & vault...", color = Color.Gray, fontSize = 14.sp) },
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
                                .fillMaxWidth()
                                .height(46.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isDark) Color(0xFF1F2937) else Color.LightGray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
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
                        "All" to "All (${counts["All"]})",
                        "NOTE" to "Notes (${counts["NOTE"]})",
                        "CODE" to "Codes (${counts["CODE"]})",
                        "KEY" to "Keys (${counts["KEY"]})",
                        "PASSWORD" to "Passwords (${counts["PASSWORD"]})",
                        "URL" to "URLs (${counts["URL"]})"
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
                            Text("No vault items found", color = Color.Gray, fontSize = 15.sp)
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
                                    Row(modifier = Modifier.fillMaxWidth()) {
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
                                                            Toast.makeText(context, "Copied content", Toast.LENGTH_SHORT).show()
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
                                                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
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
                                        Text("Create quick note...", color = Color.Gray, fontSize = 14.sp)
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
                                    Toast.makeText(context, "Added to vault", Toast.LENGTH_SHORT).show()
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

    val presets = listOf(
        "DEFAULT" to ("Original" to Color.Gray),
        "DARK" to ("Dark Blue" to Color(0xFF0B131E)),
        "SEPIA" to ("Sepia" to Color(0xFFF4ECD8)),
        "OLED" to ("OLED Black" to Color(0xFF000000)),
        "FOREST" to ("Forest" to Color(0xFFE6F0E6))
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
                        text = "Customize Site Style",
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
                        text = "Reset All",
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
                            text = "Theme Presets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { (code, labelInfo) ->
                                val (label, color) = labelInfo
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
                                        text = label.split(" ").firstOrNull() ?: label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (code == "SEPIA" || code == "FOREST" || (code == "DEFAULT" && !isDark)) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    // Fonts
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Font Family",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "inherit" to "Default",
                                "sans-serif" to "Sans",
                                "serif" to "Serif",
                                "monospace" to "Mono"
                            ).forEach { (code, label) ->
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
                                        text = label,
                                        fontSize = 11.sp,
                                        fontFamily = when (code) {
                                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                        },
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
                                Text("Font Size", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
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
                                Text("Line Spacing", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
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
                                Text("Letter Spacing", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textPrimary)
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
                                Text("Hide Images", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text("Do not load images for data-saving", fontSize = 10.sp, color = textSecondary)
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
                                Text("Grayscale Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text("Desaturate colors for comfortable reading", fontSize = 10.sp, color = textSecondary)
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
                                Text("Night Light", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text("Apply warm amber tint for eye care", fontSize = 10.sp, color = textSecondary)
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
                    val context = androidx.compose.ui.platform.LocalContext.current
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
                                Text("Scroll Buttons", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                Text("Show buttons to scroll quickly to top or bottom", fontSize = 10.sp, color = textSecondary)
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
                        Text("Apply to all sites", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                        Text("Automatically load styles on every site.", fontSize = 11.sp, color = textSecondary)
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
                            text = "Privacy Report",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Intelligent Tracking Prevention",
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
                    text = "Omni Smart Tracking Protection prevents trackers from profiling your web activity and collecting data across websites.",
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
                            text = "Trackers Prevented",
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
                            text = "Est. Data Saved",
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
                            text = "Faster Page Load",
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
                            text = "On-Device Privacy",
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
                    text = "Most Frequent Trackers Blocked",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showTrackersList = !showTrackersList }) {
                    Text(
                        text = if (showTrackersList) "Hide" else "Show",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showTrackersList) {
                val trackerDomains = listOf(
                    Triple("google-analytics.com", "Analytics & Telemetry", "High"),
                    Triple("facebook.net", "Social Tracking & Pixel", "High"),
                    Triple("doubleclick.net", "Ad Network Telemetry", "Medium"),
                    Triple("scorecardresearch.com", "Audience Measurement", "Medium"),
                    Triple("criteo.com", "Retargeting Ad Tracker", "Low")
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
                                        text = "Blocked",
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

    var localImages by remember(viewModel.extractedImagesList) { mutableStateOf(viewModel.extractedImagesList) }
    var isFullscreenManga by remember { mutableStateOf(false) }
    var pendingDownloadType by remember { mutableStateOf<Boolean?>(null) } // null = hide, false = images, true = asPdf

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
                            text = if (asPdf) "Download Manga PDF" else "Download Manga Images",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = textColor
                        )
                        Text(
                            text = "${localImages.size} pages ready for download",
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
                    Text("Download Locally", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                    Text("Save to Private Vault 🔒", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (isFullscreenManga && localImages.isNotEmpty()) {
        MangaFullscreenViewer(
            images = localImages,
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
                            text = if (isMangaMode) "Manga Reader Mode" else "Image Grabber",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "${localImages.size} pages extracted",
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
                        label = { Text(if (isMangaMode) "🖼️ Grid View" else "📖 Manga View", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textColor)
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
                        Text("Extracting full-resolution images...", color = textColor, fontSize = 13.sp)
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
                        Text("No images available", color = textColor, fontSize = 14.sp)
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
                            val imgUrl = localImages[index]
                            val request = remember(imgUrl, viewModel.currentUrl) {
                                val referer = try {
                                    val uri = android.net.Uri.parse(viewModel.currentUrl)
                                    "${uri.scheme}://${uri.host}/"
                                } catch (_: Exception) {
                                    viewModel.currentUrl
                                }
                                coil.request.ImageRequest.Builder(context)
                                    .data(imgUrl)
                                    .addHeader("Referer", referer)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                    .crossfade(true)
                                    .build()
                            }
                            coil.compose.AsyncImage(
                                model = request,
                                contentDescription = "Manga Page ${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
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
                            text = "Page ${firstVisible.value} / ${localImages.size}",
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
                            val request = remember(imgUrl, viewModel.currentUrl) {
                                val referer = try {
                                    val uri = android.net.Uri.parse(viewModel.currentUrl)
                                    "${uri.scheme}://${uri.host}/"
                                } catch (_: Exception) {
                                    viewModel.currentUrl
                                }
                                coil.request.ImageRequest.Builder(context)
                                    .data(imgUrl)
                                    .addHeader("Referer", referer)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                    .crossfade(true)
                                    .build()
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cardBg)
                            ) {
                                coil.compose.AsyncImage(
                                    model = request,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
                                text = "${localImages.size} pages remaining",
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
                                    Text("Images (${localImages.size})", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        pendingDownloadType = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("As PDF", fontSize = 12.sp)
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
    onDismissRequest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val bg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7)
    val cardBg = if (viewModel.isAmoledMode) Color(0xFF111111) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    var selectedTab by remember { mutableStateOf(0) }
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
                        text = "DevTools Inspector",
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
                    Text("📊 Overview", modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("🌐 Elements", modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("⚡ Network (${stats?.resources?.size ?: 0})", modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                    Text("💻 Console", modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 3) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
                }
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) {
                    Text("🔒 Storage", modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), fontSize = 12.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 4) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.7f))
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
                        Text(if (isHttps) "HTTPS Secure Connection" else "HTTP Unsecured Connection", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isHttps) Color(0xFF34C759) else Color(0xFFFF9500))
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
                        Text("Reading", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("${stats.readTimeMinutes} min", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("${stats.wordCount} words", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = cardBg) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Elements", fontSize = 10.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                        Text("${stats.imageCount} imgs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("${stats.linkCount} links", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = cardBg) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Assets", fontSize = 10.sp, color = Color(0xFFAF52DE), fontWeight = FontWeight.Bold)
                        Text("${stats.scriptCount} scripts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("${stats.cssCount} styles", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
            }
        }

        if (stats.metaTags.isNotEmpty()) {
            item {
                Text("SEO & Meta Tags (${stats.metaTags.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
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
            Text("No DOM nodes extracted", color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Page DOM Structure Inspector (${stats.domNodes.size} key elements)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.7f))
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
            Text("No network activity captured yet", color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${stats.resources.size} Network Requests", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Text("${totalBytes / 1024} KB transferred", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
        Text("Quick DevTools Commands", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.7f))
        
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('a').forEach(a => a.style.outline = '2px solid gold')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("🔗 Highlight Links", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('*').forEach(e => e.style.outline = '1px solid red')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("🔳 Outline Layout", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.querySelectorAll('input[type=\"hidden\"]').forEach(i => i.type = 'text')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("👁️ Show Hidden Inputs", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.executeConsoleJs("document.body.contentEditable = (document.body.contentEditable !== 'true')") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("✏️ Toggle Edit Page", fontSize = 11.sp)
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
                Text("Run")
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
                    Text(if (isError) "Console Error" else "Console Output", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isError) Color(0xFFFF453A) else Color(0xFF34C759))
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
            Text("Cookies (${stats.cookies.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
        if (stats.cookies.isEmpty()) {
            item {
                Text("No cookies set for this domain", fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
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
            Text("LocalStorage (${stats.localStorageItems.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
        if (stats.localStorageItems.isEmpty()) {
            item {
                Text("No LocalStorage items found", fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
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

@Composable
private fun MangaFullscreenViewer(
    images: List<String>,
    onExitFullscreen: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstVisible = remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                    val imgUrl = images[index]
                    val request = remember(imgUrl) {
                        val referer = try {
                            val uri = android.net.Uri.parse(images.firstOrNull() ?: "")
                            "${uri.scheme}://${uri.host}/"
                        } catch (_: Exception) {
                            ""
                        }
                        coil.request.ImageRequest.Builder(context)
                            .data(imgUrl)
                            .addHeader("Referer", referer)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.AsyncImage(
                        model = request,
                        contentDescription = "Manga Page ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
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
                                downloadMangaImagesAndPdf(context, images, asPdf = false)
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
                                downloadMangaImagesAndPdf(context, images, asPdf = true)
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
    val destText = if (saveToLocker) "Private Vault 🔒" else "Downloads"
    val modeText = if (asPdf) "PDF document" else "$targetCount images"
    Toast.makeText(appCtx, "⏳ Starting download ($modeText to $destText)...", Toast.LENGTH_SHORT).show()

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
        val loader = coil.ImageLoader(appCtx)
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
                        val request = coil.request.ImageRequest.Builder(appCtx)
                            .data(url)
                            .apply {
                                if (referer.isNotEmpty()) addHeader("Referer", referer)
                                addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            }
                            .allowHardware(false)
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
                        Toast.makeText(appCtx, "🔒 Saved Manga PDF ($successCount pages) to Private Vault", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(appCtx, "📄 Saved Manga PDF ($successCount pages) to Downloads/OmniBrowser/$pdfFileName", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MangaDownload", "Error writing PDF", e)
                if (jobId != null) {
                    downloadEngine?.failExternalJob(jobId, filename, e.localizedMessage ?: "PDF Error")
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(appCtx, "Failed to compile PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val folderFile = java.io.File(downloadsDir, "OmniBrowser/$folderName")
            if (jobId != null) {
                downloadEngine?.completeExternalJob(jobId, filename, folderFile, totalBytesDownloaded, firstSavedUri)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = if (saveToLocker) "🔒 Saved $successCount images to Private Vault" else "✅ Downloaded $successCount images to Downloads/OmniBrowser/$folderName"
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
                        label = "New Tab",
                        tint = textColor,
                        onClick = { onDismissRequest(); onNewTab() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.VisibilityOff,
                        label = "Incognito",
                        tint = textColor,
                        onClick = { onDismissRequest(); onNewIncognitoTab() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.GridView,
                        label = "Group",
                        tint = textColor,
                        onClick = { onDismissRequest(); onAddTabToNewGroup() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Devices,
                        label = "Recent",
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenHistory() }
                    )
                }
            }

            // --- Card 1: Page Actions ---
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    // Bookmark Page
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                if (activeTab != null && !showHomeScreen) {
                                    if (isBookmarked) {
                                        viewModel.removeBookmark(viewModel.currentUrl)
                                    } else {
                                        viewModel.addToBookmarks(activeTab.title ?: "Page", viewModel.currentUrl)
                                    }
                                } else {
                                    Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
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
                        Text("Bookmark page", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Add to Shortcuts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                if (activeTab != null && !showHomeScreen) {
                                    viewModel.addShortcut(activeTab.title ?: "Page", viewModel.currentUrl)
                                    Toast.makeText(context, "Added to shortcuts", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
                                }
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
                        Text("Add to shortcuts", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Find in Page
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                if (activeTab != null && !showHomeScreen) {
                                    onFindInPage()
                                } else {
                                    Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                        Text("Find in page", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

                    // Desktop Site
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissRequest()
                                if (activeTab != null && !showHomeScreen) {
                                    viewModel.toggleDesktopMode(context)
                                } else {
                                    Toast.makeText(context, "Open a webpage first", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                        Text("Desktop site", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        
                        // Badge for On/Off
                        Box(
                            modifier = Modifier
                                .background(if (viewModel.isDesktopMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else dividerColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (viewModel.isDesktopMode) "On" else "Off",
                                fontSize = 11.sp,
                                color = if (viewModel.isDesktopMode) MaterialTheme.colorScheme.primary else textColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))

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
                            contentDescription = "Extensions",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extensions", fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text("Adblock, scripts & more", fontSize = 11.sp, color = secondaryText)
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
                        label = "History",
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenHistory() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Bookmark,
                        label = "Bookmarks",
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenBookmarks() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Download,
                        label = "Downloads",
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenDownloads() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Whatshot,
                        label = "Burn Data",
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
                        label = "Player",
                        tint = textColor,
                        onClick = { onDismissRequest(); onShowPlayerSettings() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Palette,
                        label = "Theme",
                        tint = textColor,
                        onClick = { onDismissRequest(); onShowThemeSheet() }
                    )
                    AllInOneGridItem(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        tint = textColor,
                        onClick = { onDismissRequest(); onOpenSettings() }
                    )
                    AllInOneGridItem(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        label = "Help",
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
                val canGoBack = activeTab?.canGoBack == true
                val canGoForward = activeTab?.canGoForward == true

                AllInOneBottomAction(
                    icon = Icons.Rounded.ArrowBack,
                    label = "Back",
                    enabled = canGoBack,
                    onClick = { viewModel.goBack() }
                )
                AllInOneBottomAction(
                    icon = Icons.Rounded.ArrowForward,
                    label = "Forward",
                    enabled = canGoForward,
                    onClick = { viewModel.goForward() }
                )
                AllInOneBottomAction(
                    icon = Icons.Rounded.Share,
                    label = "Share",
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
                    label = "Refresh",
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

