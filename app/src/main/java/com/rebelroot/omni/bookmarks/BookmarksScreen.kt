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

package com.rebelroot.omni.bookmarks

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.bookmarks.importexport.bookmarkExportFileName
import com.rebelroot.omni.bookmarks.importexport.exportBookmarksToFile
import com.rebelroot.omni.bookmarks.importexport.exportBookmarksToUri
import com.rebelroot.omni.bookmarks.importexport.prepareImportPreview
import com.rebelroot.omni.bookmarks.model.BookmarkNode
import com.rebelroot.omni.bookmarks.model.BookmarkWithPath
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import com.rebelroot.omni.bookmarks.model.collectBookmarkPaths
import com.rebelroot.omni.bookmarks.model.descendantFolderIds
import com.rebelroot.omni.bookmarks.model.findFolder
import com.rebelroot.omni.bookmarks.model.flattenFolders
import com.rebelroot.omni.bookmarks.model.pathToFolder
import com.rebelroot.omni.browser.*

/**
 * Bookmark manager.
 *
 * Mode 1 — browsing: shows the contents of [ROOT_FOLDER_ID] or a nested folder,
 * with a breadcrumb to walk back up; folders are opened in place and bookmarks
 * open in the browser.
 *
 * Mode 2 — searching: a flat, cross-folder result list (like Chrome/Firefox),
 * each bookmark annotated with the folder it lives in.
 *
 * Every item has a long-press/overflow menu for open, edit/rename, copy link,
 * move to folder and delete — operations the underlying canonical store already
 * supports but which previously had no UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenImportPreview: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // File picker for importing bookmarks (Netscape HTML from any browser).
    // A broad MIME set is used because file providers label browser export
    // files as text/html, text/plain or application/octet-stream.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.prepareImportPreview(
                context = context,
                uri = uri,
                onResult = { result ->
                    result.onSuccess {
                        onOpenImportPreview()
                    }.onFailure { e ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_error_toast, e.message ?: "Unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    var showExportSheet by remember { mutableStateOf(false) }

    // Save-to-device export via the Storage Access Framework, so the user
    // picks a permanent location instead of only being able to share.
    val exportCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBookmarksToUri(context, uri) { result ->
                result.onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_saved_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { e ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_error_toast, e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Share export: writes the file to cache and offers it to other apps.
    val shareExport: () -> Unit = {
        viewModel.exportBookmarksToFile(context) { result ->
            result.onSuccess { uri ->
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                // Always add NEW_TASK flag so the chooser can launch from any context
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_error_toast, e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    context.getString(R.string.export_error_toast, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Manager state ───────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    var currentFolderId by remember { mutableStateOf(ROOT_FOLDER_ID) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewBookmarkDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<BookmarkNode?>(null) }
    var editTarget by remember { mutableStateOf<BookmarkNode.Item?>(null) }
    var renameTarget by remember { mutableStateOf<BookmarkNode.Folder?>(null) }
    var moveTarget by remember { mutableStateOf<BookmarkNode?>(null) }
    var deleteTarget by remember { mutableStateOf<BookmarkNode?>(null) }

    val tree = viewModel.bookmarksTree
    val isSearching = searchQuery.isNotBlank()

    // Resolve the folder being viewed; fall back to the root if it was deleted.
    val currentFolder = remember(tree, currentFolderId) {
        tree?.findFolder(currentFolderId) ?: tree
    }
    LaunchedEffect(tree, currentFolderId) {
        if (tree != null && currentFolderId != ROOT_FOLDER_ID && tree.findFolder(currentFolderId) == null) {
            currentFolderId = ROOT_FOLDER_ID
        }
    }

    val breadcrumb = remember(tree, currentFolder?.id) {
        val root = tree ?: return@remember emptyList()
        root.pathToFolder(currentFolder?.id ?: ROOT_FOLDER_ID)
    }
    val atRoot = currentFolder == null || currentFolder.id == ROOT_FOLDER_ID
    val activeFolderId = currentFolder?.id ?: ROOT_FOLDER_ID

    val navigateUp: () -> Unit = {
        when {
            isSearching -> searchQuery = ""
            !atRoot -> currentFolderId = currentFolder!!.parentId.ifBlank { ROOT_FOLDER_ID }
            else -> onNavigateBack()
        }
    }
    BackHandler { navigateUp() }

    // Cross-folder search results (bookmarks) plus matching folders.
    val bookmarkResults = remember(tree, searchQuery) {
        if (tree == null || searchQuery.isBlank()) emptyList()
        else tree.collectBookmarkPaths().filter {
            it.item.title.contains(searchQuery, true) || it.item.url.contains(searchQuery, true)
        }
    }
    val folderResults = remember(tree, searchQuery) {
        if (tree == null || searchQuery.isBlank()) emptyList()
        else tree.flattenFolders().filter {
            it.folder.id != ROOT_FOLDER_ID && it.folder.title.contains(searchQuery, true)
        }
    }

    val isDarkMode = viewModel.isDarkThemeEnabled

    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    val inputBgColor = MaterialTheme.colorScheme.surfaceVariant
    val inputBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val label = if (atRoot) {
                        stringResource(id = R.string.bookmarks_title)
                    } else {
                        currentFolder!!.title.ifBlank { stringResource(id = R.string.bookmarks_move_untitled) }
                    }
                    Text(label, fontWeight = FontWeight.Bold, color = textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimaryColor
                        )
                    }
                },
                actions = {
                    // Create a folder inside the currently viewed folder.
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = stringResource(id = R.string.bookmarks_cd_new_folder),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Import button
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf("text/html", "text/plain", "application/octet-stream")
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = stringResource(id = R.string.bookmarks_import),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Export button — opens a sheet to save locally or share
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = stringResource(id = R.string.bookmarks_export),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(id = R.string.bookmarks_cd_more),
                                tint = textPrimaryColor
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.bookmarks_new_bookmark)) },
                                leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showNewBookmarkDialog = true
                                }
                            )
                            if (viewModel.bookmarksList.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(id = R.string.bookmarks_clear_all),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showClearConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f))
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = com.rebelroot.omni.ui.adaptive.rememberAdaptiveUiMetrics().screenContentMaxWidth)
                    .padding(paddingValues)
                    .background(bgColor)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 8.dp),
                    placeholder = { Text(stringResource(id = R.string.bookmarks_search_placeholder), color = textSecondaryColor) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = textSecondaryColor
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = textSecondaryColor)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = inputBorderColor,
                        focusedContainerColor = inputBgColor,
                        unfocusedContainerColor = inputBgColor
                    )
                )

                // Breadcrumb — only when the user has drilled into a folder.
                if (!isSearching && breadcrumb.size > 1) {
                    BreadcrumbRow(
                        breadcrumb = breadcrumb,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        onNavigate = { currentFolderId = it }
                    )
                }

                if (isSearching) {
                    SearchResults(
                        folderResults = folderResults,
                        bookmarkResults = bookmarkResults,
                        isDarkMode = isDarkMode,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        cardColor = cardColor,
                        cardBorderColor = cardBorderColor,
                        onOpenFolder = {
                            searchQuery = ""
                            currentFolderId = it
                        },
                        onOpenUrl = onOpenUrl,
                        onItemMenu = { actionTarget = it }
                    )
                } else {
                    FolderContents(
                        folder = currentFolder,
                        isDarkMode = isDarkMode,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        cardColor = cardColor,
                        cardBorderColor = cardBorderColor,
                        onOpenFolder = { currentFolderId = it },
                        onOpenUrl = onOpenUrl,
                        onItemLongPress = { actionTarget = it }
                    )
                }
            }
        }
    }

    // ── Item actions sheet ──────────────────────────────────────────────────
    actionTarget?.let { target ->
        ItemActionsSheet(
            target = target,
            onDismiss = { actionTarget = null },
            onOpenUrl = onOpenUrl,
            onOpenFolder = {
                searchQuery = ""
                currentFolderId = it
            },
            onEdit = { editTarget = it; actionTarget = null },
            onRename = { renameTarget = it; actionTarget = null },
            onMove = { moveTarget = it; actionTarget = null },
            onDelete = { deleteTarget = it; actionTarget = null },
            onCopyLink = { url ->
                clipboard.setText(AnnotatedString(url))
                Toast.makeText(context, context.getString(R.string.bookmarks_link_copied), Toast.LENGTH_SHORT).show()
                actionTarget = null
            }
        )
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────
    if (showNewFolderDialog) {
        TextInputDialog(
            title = stringResource(R.string.bookmarks_new_folder),
            label = stringResource(R.string.bookmarks_new_folder_hint),
            confirmLabel = stringResource(R.string.bookmarks_create),
            onConfirm = {
                viewModel.createBookmarkFolder(it, activeFolderId)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    if (showNewBookmarkDialog) {
        BookmarkEditorDialog(
            dialogTitle = stringResource(R.string.bookmarks_new_bookmark),
            confirmLabel = stringResource(R.string.bookmarks_create),
            initialName = viewModel.activeTab?.title.orEmpty(),
            initialUrl = viewModel.currentUrl.takeIf { it != "about:blank" }.orEmpty(),
            onConfirm = { name, url ->
                viewModel.addBookmarkToFolder(name, url, activeFolderId)
                showNewBookmarkDialog = false
            },
            onDismiss = { showNewBookmarkDialog = false }
        )
    }

    editTarget?.let { item ->
        BookmarkEditorDialog(
            dialogTitle = stringResource(R.string.bookmarks_edit_title),
            confirmLabel = stringResource(R.string.bookmarks_save),
            initialName = item.title,
            initialUrl = item.url,
            onConfirm = { name, url ->
                viewModel.renameBookmarkItem(item.id, name)
                viewModel.updateBookmarkUrl(item.id, url)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    renameTarget?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.bookmarks_rename_folder_title),
            label = stringResource(R.string.bookmarks_name_label),
            initialValue = folder.title,
            confirmLabel = stringResource(R.string.bookmarks_save),
            onConfirm = {
                viewModel.renameBookmarkItem(folder.id, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    if (moveTarget != null && tree != null) {
        val target = moveTarget!!
        MoveToFolderDialog(
            tree = tree,
            movingId = target.id,
            currentParentId = target.parentId,
            onPick = { parentId ->
                viewModel.moveBookmarkItem(target.id, parentId)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }

    deleteTarget?.let { target ->
        val isFolder = target is BookmarkNode.Folder
        val name = target.title.ifBlank {
            if (isFolder) stringResource(R.string.bookmarks_move_untitled) else target.let { (it as BookmarkNode.Item).url }
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    stringResource(
                        if (isFolder) R.string.bookmarks_delete_folder_title else R.string.bookmarks_delete_bookmark_title
                    ),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isFolder) R.string.bookmarks_delete_folder_message else R.string.bookmarks_delete_bookmark_message,
                        name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBookmarkItem(target.id)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.bookmarks_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.bookmarks_cancel))
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.bookmarks_clear_all), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.bookmarks_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllBookmarks()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.bookmarks_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.bookmarks_cancel))
                }
            }
        )
    }

    if (showExportSheet) {
        ExportOptionsSheet(
            onDismiss = { showExportSheet = false },
            onSaveToDevice = {
                showExportSheet = false
                exportCreateLauncher.launch(bookmarkExportFileName())
            },
            onShare = {
                showExportSheet = false
                shareExport()
            }
        )
    }
}

// ── Body sections ───────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbRow(
    breadcrumb: List<BookmarkNode.Folder>,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumb.forEachIndexed { index, folder ->
            val isLast = index == breadcrumb.lastIndex
            val label = if (folder.id == ROOT_FOLDER_ID) {
                stringResource(R.string.bookmarks_root_label)
            } else {
                folder.title.ifBlank { stringResource(R.string.bookmarks_move_untitled) }
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isLast) textPrimaryColor else textSecondaryColor,
                maxLines = 1,
                modifier = if (isLast) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigate(folder.id) }
                        .padding(horizontal = 2.dp)
                }
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = textSecondaryColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FolderContents(
    folder: BookmarkNode.Folder?,
    isDarkMode: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    onOpenFolder: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onItemLongPress: (BookmarkNode) -> Unit
) {
    val children = folder?.children.orEmpty()
    if (children.isEmpty()) {
        EmptyState(text = stringResource(R.string.bookmarks_folder_empty), textSecondaryColor = textSecondaryColor)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        items(children, key = { it.id }) { node ->
            when (node) {
                is BookmarkNode.Folder -> FolderRowItem(
                    folder = node,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    cardColor = cardColor,
                    cardBorderColor = cardBorderColor,
                    onClick = { onOpenFolder(node.id) },
                    onLongClick = { onItemLongPress(node) },
                    onMenu = { onItemLongPress(node) }
                )
                is BookmarkNode.Item -> BookmarkRowItem(
                    item = node,
                    isDarkMode = isDarkMode,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    cardColor = cardColor,
                    cardBorderColor = cardBorderColor,
                    onClick = { onOpenUrl(node.url) },
                    onLongClick = { onItemLongPress(node) },
                    onMenu = { onItemLongPress(node) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SearchResults(
    folderResults: List<com.rebelroot.omni.bookmarks.model.FolderAtDepth>,
    bookmarkResults: List<BookmarkWithPath>,
    isDarkMode: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    onOpenFolder: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onItemMenu: (BookmarkNode) -> Unit
) {
    if (folderResults.isEmpty() && bookmarkResults.isEmpty()) {
        EmptyState(text = stringResource(R.string.bookmarks_empty), textSecondaryColor = textSecondaryColor)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        items(folderResults, key = { "f_${it.folder.id}" }) { entry ->
            FolderRowItem(
                folder = entry.folder,
                textPrimaryColor = textPrimaryColor,
                textSecondaryColor = textSecondaryColor,
                cardColor = cardColor,
                cardBorderColor = cardBorderColor,
                onClick = { onOpenFolder(entry.folder.id) },
                onLongClick = { onItemMenu(entry.folder) },
                onMenu = { onItemMenu(entry.folder) }
            )
        }
        items(bookmarkResults, key = { "b_${it.item.id}" }) { entry ->
            BookmarkRowItem(
                item = entry.item,
                isDarkMode = isDarkMode,
                textPrimaryColor = textPrimaryColor,
                textSecondaryColor = textSecondaryColor,
                cardColor = cardColor,
                cardBorderColor = cardBorderColor,
                subtitleOverride = entry.folderPath
                    .joinToString(" / ") { it.title.ifBlank { "Untitled" } }
                    .takeIf { it.isNotBlank() },
                onClick = { onOpenUrl(entry.item.url) },
                onLongClick = { onItemMenu(entry.item) },
                onMenu = { onItemMenu(entry.item) }
            )
        }
    }
}

@Composable
private fun ColumnScope.EmptyState(text: String, textSecondaryColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textSecondaryColor
        )
    }
}

// ── Item actions ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemActionsSheet(
    target: BookmarkNode,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onEdit: (BookmarkNode.Item) -> Unit,
    onRename: (BookmarkNode.Folder) -> Unit,
    onMove: (BookmarkNode) -> Unit,
    onDelete: (BookmarkNode) -> Unit,
    onCopyLink: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = target.title.ifBlank { stringResource(R.string.bookmarks_move_untitled) },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            when (target) {
                is BookmarkNode.Item -> {
                    SheetActionRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.bookmarks_menu_open)) { onOpenUrl(target.url) }
                    SheetActionRow(Icons.Rounded.Edit, stringResource(R.string.bookmarks_menu_edit)) { onEdit(target) }
                    SheetActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.bookmarks_menu_copy_link)) { onCopyLink(target.url) }
                    SheetActionRow(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.bookmarks_menu_move)) { onMove(target) }
                    SheetActionRow(Icons.Rounded.Delete, stringResource(R.string.bookmarks_menu_delete), destructive = true) { onDelete(target) }
                }
                is BookmarkNode.Folder -> {
                    SheetActionRow(Icons.Rounded.FolderOpen, stringResource(R.string.bookmarks_menu_open)) { onOpenFolder(target.id) }
                    SheetActionRow(Icons.Rounded.Edit, stringResource(R.string.bookmarks_menu_rename)) { onRename(target) }
                    SheetActionRow(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.bookmarks_menu_move)) { onMove(target) }
                    SheetActionRow(Icons.Rounded.Delete, stringResource(R.string.bookmarks_menu_delete), destructive = true) { onDelete(target) }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = ""
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmarks_cancel))
            }
        }
    )
}

@Composable
private fun BookmarkEditorDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialName: String,
    initialUrl: String,
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.bookmarks_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.bookmarks_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = url.isNotBlank()
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmarks_cancel))
            }
        }
    )
}

@Composable
private fun MoveToFolderDialog(
    tree: BookmarkNode.Folder,
    movingId: String,
    currentParentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // A folder cannot be moved into itself or its own subtree.
    val movingFolder = tree.findFolder(movingId)
    val excluded = if (movingFolder != null) movingFolder.descendantFolderIds() + movingId else emptySet()
    val destinations = remember(tree, movingId) { tree.flattenFolders(exclude = excluded) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_move_title), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(destinations, key = { it.folder.id }) { entry ->
                    val isRoot = entry.folder.id == ROOT_FOLDER_ID
                    val isCurrent = entry.folder.id == currentParentId
                    val label = if (isRoot) {
                        stringResource(R.string.bookmarks_root_label)
                    } else {
                        entry.folder.title.ifBlank { stringResource(R.string.bookmarks_move_untitled) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isCurrent) { onPick(entry.folder.id) }
                            .padding(start = (12 + entry.depth * 16).dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isRoot) Icons.Rounded.Bookmarks else Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Text(
                                text = stringResource(R.string.bookmarks_move_current),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmarks_cancel))
            }
        }
    )
}

/**
 * Bottom sheet offering the two export destinations: save a permanent copy to
 * the device via the Storage Access Framework, or send the file to another app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionsSheet(
    onDismiss: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(id = R.string.export_options_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ExportOptionRow(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(id = R.string.export_save_to_device),
                subtitle = stringResource(id = R.string.export_save_to_device_desc),
                onClick = onSaveToDevice
            )
            ExportOptionRow(
                icon = Icons.Rounded.Share,
                title = stringResource(id = R.string.export_share),
                subtitle = stringResource(id = R.string.export_share_desc),
                onClick = onShare
            )
        }
    }
}

@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRowItem(
    folder: BookmarkNode.Folder,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title.ifBlank { stringResource(R.string.bookmarks_move_untitled) },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textPrimaryColor
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.bookmarks_item_count,
                        folder.children.size,
                        folder.children.size
                    ),
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.bookmarks_cd_item_menu),
                    tint = textSecondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkRowItem(
    item: BookmarkNode.Item,
    isDarkMode: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    subtitleOverride: String? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { item.url },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textPrimaryColor
                )
                Text(
                    text = subtitleOverride ?: item.url,
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onMenu,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background((if (isDarkMode) Color(0xFF243647) else Color(0xFFE2E8F0)).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.bookmarks_cd_item_menu),
                    tint = textSecondaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}