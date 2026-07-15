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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    engine: StreamDownloadEngine,
    onNavigateBack: () -> Unit,
    onPlayVideo: (File) -> Unit
) {
    val jobs by engine.jobs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(id = com.rebelroot.omni.R.string.downloads_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(id = com.rebelroot.omni.R.string.downloads_empty),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(jobs) { job ->
                        DownloadItemCard(
                            job = job,
                            onPlayVideo = onPlayVideo,
                            onOpenFile = { file, openUri ->
                                try {
                                    val ext = file.extension.lowercase()
                                    val mime = when (ext) {
                                        "apk" -> "application/vnd.android.package-archive"
                                        "pdf" -> "application/pdf"
                                        "zip" -> "application/zip"
                                        "rar" -> "application/x-rar-compressed"
                                        "7z" -> "application/x-7z-compressed"
                                        "tar" -> "application/x-tar"
                                        "gz" -> "application/gzip"
                                        "tgz" -> "application/gzip"
                                        "bin" -> "application/octet-stream"
                                        "exe" -> "application/octet-stream"
                                        "epub" -> "application/epub+zip"
                                        "doc" -> "application/msword"
                                        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        "xls" -> "application/vnd.ms-excel"
                                        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        "ppt" -> "application/vnd.ms-powerpoint"
                                        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                        "csv" -> "text/csv"
                                        "txt" -> "text/plain"
                                        "rtf" -> "application/rtf"
                                        "xml" -> "application/xml"
                                        "json" -> "application/json"
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
                                        "flv" -> "video/x-flv"
                                        "jpg", "jpeg" -> "image/jpeg"
                                        "png" -> "image/png"
                                        "gif" -> "image/gif"
                                        "webp" -> "image/webp"
                                        "bmp" -> "image/bmp"
                                        "svg" -> "image/svg+xml"
                                        "ico" -> "image/x-icon"
                                        else -> android.webkit.MimeTypeMap.getSingleton()
                                            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                                    }
                                    // Use MediaStore URI directly (Android 10+), or FileProvider for older
                                    val uri = openUri ?: FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { engine.cancelDownload(job.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    job: StreamDownloadEngine.DownloadJob,
    onPlayVideo: (java.io.File) -> Unit,
    onOpenFile: (java.io.File, android.net.Uri?) -> Unit,
    onDelete: () -> Unit
) {
    val progressState by job.progress.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                RoundedCornerShape(12.dp)
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        job.saveToLocker -> Icons.Rounded.Lock
                        job.isGeneric -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        else -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = "Type",
                    tint = when {
                        job.saveToLocker -> MaterialTheme.colorScheme.primary
                        job.isGeneric -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = job.filename,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = job.url,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val progress = progressState) {
                is StreamDownloadEngine.DownloadProgress.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (progress.percent >= 0) "Downloading: ${progress.percent}%" else "Downloading...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatBytes(progress.bytesDownloaded),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (progress.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is StreamDownloadEngine.DownloadProgress.Muxing -> {
                    Text(
                        text = progress.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                is StreamDownloadEngine.DownloadProgress.Complete -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Complete",
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatBytes(progress.sizeBytes),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        // Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Play button for local video downloads
                            if (!job.saveToLocker && !job.isGeneric &&
                                (job.filename.endsWith(".mp4") || job.filename.endsWith(".webm"))) {
                                Button(
                                    onClick = { onPlayVideo(progress.file) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(androidx.compose.ui.res.stringResource(id = com.rebelroot.omni.R.string.play_text), fontSize = 11.sp)
                                }
                            }
                            // Open button for generic local downloads
                            if (!job.saveToLocker && job.isGeneric) {
                                Button(
                                    onClick = { onOpenFile(progress.file, progress.openUri) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Open", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                is StreamDownloadEngine.DownloadProgress.Error -> {
                    Text(
                        text = "Error: ${progress.message}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
