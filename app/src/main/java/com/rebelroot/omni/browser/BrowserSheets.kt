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
    val sheetBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
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
