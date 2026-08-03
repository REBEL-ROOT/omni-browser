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

package com.rebelroot.omni.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rebelroot.omni.R
import java.io.File

private enum class DownloadCategory(@StringRes val labelRes: Int) {
    ALL(R.string.download_category_all),
    VIDEOS(R.string.download_category_videos),
    AUDIO(R.string.download_category_audio),
    IMAGES(R.string.download_category_images),
    DOCUMENTS(R.string.download_category_documents),
    APKS(R.string.download_category_apks),
    OTHER(R.string.download_category_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    engine: StreamDownloadEngine,
    onNavigateBack: () -> Unit,
    onPlayVideo: (File) -> Unit
) {
    val jobs by engine.jobs.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(DownloadCategory.ALL) }

    // Dialog States
    var renameJob by remember { mutableStateOf<StreamDownloadEngine.DownloadJob?>(null) }
    var renameText by remember { mutableStateOf("") }

    var deleteJob by remember { mutableStateOf<StreamDownloadEngine.DownloadJob?>(null) }
    var deleteFromDiskChecked by remember { mutableStateOf(true) }

    // Calculate Storage Statistics
    val deviceStorageInfo = remember { getDeviceStorageInfo(context) }
    val totalDownloadedBytes = remember(jobs) {
        jobs.sumOf { job ->
            val p = job.progress.value
            if (p is StreamDownloadEngine.DownloadProgress.Complete) p.sizeBytes else 0L
        }
    }

    // Filter jobs by category & search query
    val filteredJobs = remember(jobs, selectedCategory, searchQuery) {
        jobs.filter { job ->
            val matchesCategory = when (selectedCategory) {
                DownloadCategory.ALL -> true
                else -> getCategoryForFilename(job.filename) == selectedCategory
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                job.filename.contains(searchQuery, ignoreCase = true) || job.url.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.downloads_search_placeholder), fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(
                                text = stringResource(id = R.string.downloads_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (deviceStorageInfo.second > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.downloads_storage_usage,
                                        formatBytes(totalDownloadedBytes),
                                        formatBytes(deviceStorageInfo.second)
                                    ),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearching) stringResource(R.string.downloads_close_search) else stringResource(R.string.downloads_search)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Category Filter Chips Row
            ScrollableTabRow(
                selectedTabIndex = DownloadCategory.values().indexOf(selectedCategory),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
                indicator = {}
            ) {
                DownloadCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = stringResource(cat.labelRes),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            if (filteredJobs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) stringResource(R.string.downloads_no_matching) else stringResource(id = R.string.downloads_empty),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredJobs, key = { it.id }) { job ->
                        DownloadListItem(
                            job = job,
                            onPlayVideo = onPlayVideo,
                            onOpenFile = { file, openUri ->
                                openDownloadedFile(context, file, openUri, onPlayVideo)
                            },
                            onShareFile = { file, openUri ->
                                shareDownloadedFile(context, file, openUri)
                            },
                            onRenameClick = {
                                renameJob = job
                                renameText = job.filename.substringBeforeLast('.')
                            },
                            onDeleteClick = {
                                deleteJob = job
                                deleteFromDiskChecked = true
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                            modifier = Modifier.padding(start = 68.dp)
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    renameJob?.let { job ->
        AlertDialog(
            onDismissRequest = { renameJob = null },
            title = { Text(stringResource(R.string.download_rename_file), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.download_filename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renameJob
                        if (target != null && renameText.isNotBlank()) {
                            val success = engine.renameDownload(target.id, renameText)
                            if (!success) {
                                Toast.makeText(context, context.getString(R.string.download_rename_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                        renameJob = null
                    }
                ) {
                    Text(stringResource(R.string.download_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameJob = null }) {
                    Text(stringResource(R.string.cancel_text))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteJob?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteJob = null },
            title = { Text(stringResource(R.string.download_delete_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.download_delete_confirm, job.filename))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFromDiskChecked = !deleteFromDiskChecked }
                    ) {
                        Checkbox(
                            checked = deleteFromDiskChecked,
                            onCheckedChange = { deleteFromDiskChecked = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.download_delete_from_storage), fontSize = 13.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteJob
                        if (target != null) {
                            engine.deleteDownload(target.id, deleteFileFromDisk = deleteFromDiskChecked)
                        }
                        deleteJob = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.download_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteJob = null }) {
                    Text(stringResource(R.string.cancel_text))
                }
            }
        )
    }
}

@Composable
private fun DownloadListItem(
    job: StreamDownloadEngine.DownloadJob,
    onPlayVideo: (File) -> Unit,
    onOpenFile: (File, Uri?) -> Unit,
    onShareFile: (File, Uri?) -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progressState by job.progress.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            val p = progressState
            if (p is StreamDownloadEngine.DownloadProgress.Complete) {
                onOpenFile(p.file, p.openUri)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(getCategoryBackgroundColor(job.filename, job.saveToLocker)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(job.filename, job.saveToLocker),
                    contentDescription = null,
                    tint = getCategoryIconTint(job.filename, job.saveToLocker),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Main Details Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = job.filename,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                when (val progress = progressState) {
                    is StreamDownloadEngine.DownloadProgress.Complete -> {
                        Text(
                            text = formatBytes(progress.sizeBytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    is StreamDownloadEngine.DownloadProgress.Downloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (progress.percent >= 0) {
                                    stringResource(R.string.downloads_downloading_percent, progress.percent)
                                } else {
                                    stringResource(R.string.downloads_downloading)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${formatBytes(progress.bytesDownloaded)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.percent >= 0) progress.percent / 100f else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is StreamDownloadEngine.DownloadProgress.Muxing -> {
                        Text(
                            text = progress.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    is StreamDownloadEngine.DownloadProgress.Error -> {
                        Text(
                            text = stringResource(R.string.downloads_failed_prefix, progress.message),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3-Dot Options Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.download_more_options),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Share
                    if (progressState is StreamDownloadEngine.DownloadProgress.Complete && !job.saveToLocker) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_share)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val comp = progressState as StreamDownloadEngine.DownloadProgress.Complete
                                onShareFile(comp.file, comp.openUri)
                            }
                        )
                    }
                    // Rename
                    if (progressState is StreamDownloadEngine.DownloadProgress.Complete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                    }
                    // Delete
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

private fun getCategoryForFilename(filename: String): DownloadCategory {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m3u8" -> DownloadCategory.VIDEOS
        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus" -> DownloadCategory.AUDIO
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> DownloadCategory.IMAGES
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf" -> DownloadCategory.DOCUMENTS
        "apk" -> DownloadCategory.APKS
        else -> DownloadCategory.OTHER
    }
}

private fun getCategoryIcon(filename: String, isLocker: Boolean): ImageVector {
    if (isLocker) return Icons.Rounded.Lock
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts" -> Icons.Rounded.PlayArrow
        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus" -> Icons.Rounded.MusicNote
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Rounded.Image
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> Icons.AutoMirrored.Rounded.InsertDriveFile
        "apk" -> Icons.Rounded.Android
        "zip", "rar", "7z", "tar", "gz" -> Icons.Rounded.FolderZip
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun getCategoryBackgroundColor(filename: String, isLocker: Boolean): Color {
    if (isLocker) return Color(0xFF7C4DFF).copy(alpha = 0.15f)
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts" -> Color(0xFFE53935).copy(alpha = 0.15f)
        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus" -> Color(0xFF00ACC1).copy(alpha = 0.15f)
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Color(0xFFFFB300).copy(alpha = 0.15f)
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> Color(0xFF1E88E5).copy(alpha = 0.15f)
        "apk" -> Color(0xFF43A047).copy(alpha = 0.15f)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8E24AA).copy(alpha = 0.15f)
        else -> Color(0xFF757575).copy(alpha = 0.15f)
    }
}

private fun getCategoryIconTint(filename: String, isLocker: Boolean): Color {
    if (isLocker) return Color(0xFF7C4DFF)
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "flv", "ts" -> Color(0xFFE53935)
        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus" -> Color(0xFF00ACC1)
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Color(0xFFFFB300)
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> Color(0xFF1E88E5)
        "apk" -> Color(0xFF43A047)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8E24AA)
        else -> Color(0xFF757575)
    }
}

private fun openDownloadedFile(
    context: Context,
    file: File,
    openUri: Uri?,
    onPlayVideo: (File) -> Unit
) {
    val ext = file.extension.lowercase()
    if (ext == "mp4" || ext == "webm" || ext == "mkv") {
        onPlayVideo(file)
        return
    }

    val uri = openUri ?: try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        Uri.fromFile(file)
    }

    val mime = getMimeType(ext)

    // Special handling for APK files: launch Android Package Installer directly
    if (ext == "apk" || mime == "application/vnd.android.package-archive") {
        try {
            val apkIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(apkIntent)
            return
        } catch (e: Exception) {
            // Fallthrough to standard chooser
        }
    }

    fun launchIntentWithPermissions(targetIntent: Intent, title: String): Boolean {
        return try {
            val chooser = Intent.createChooser(targetIntent, title).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = context.packageManager
            val resInfoList = pm.queryIntentActivities(targetIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
            }

            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    // 1. Primary: Explicit MIME intent
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (launchIntentWithPermissions(viewIntent, context.getString(R.string.download_open_with))) return

    // 2. Secondary: Wildcard */* intent
    val wildcardIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (launchIntentWithPermissions(wildcardIntent, context.getString(R.string.download_open_with))) return

    // 3. Tertiary: Share ACTION_SEND intent
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = if (mime != "application/octet-stream") mime else "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (launchIntentWithPermissions(shareIntent, context.getString(R.string.download_open_file_using))) return

    // 4. Ultimate Fallback: Launch System Files App / Storage Access Framework directly
    try {
        val filesAppIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            setPackage("com.google.android.apps.nbu.files")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(filesAppIntent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.download_open_failed, file.name), Toast.LENGTH_SHORT).show()
    }
}

private fun shareDownloadedFile(context: Context, file: File, openUri: Uri?) {
    try {
        val ext = file.extension.lowercase()
        val mime = getMimeType(ext)
        val uri = openUri ?: try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (mime != "application/octet-stream") mime else "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.download_share_file_via)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pm = context.packageManager
        val resInfoList = pm.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            try {
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
        }

        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.download_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}

private fun getDeviceStorageInfo(context: Context): Pair<Long, Long> {
    return try {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes
        Pair(usedBytes, totalBytes)
    } catch (e: Exception) {
        Pair(0L, 0L)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
