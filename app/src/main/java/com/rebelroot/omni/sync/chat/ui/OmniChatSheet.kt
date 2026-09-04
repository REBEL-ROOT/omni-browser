package com.rebelroot.omni.sync.chat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rebelroot.omni.sync.chat.ChatMessage
import com.rebelroot.omni.sync.chat.ChatRepository
import com.rebelroot.omni.sync.chat.MessageType
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniChatSheet(
    activeTabTitle: String = "",
    activeTabUrl: String = "",
    onOpenUrl: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatRepo = remember { ChatRepository.getInstance() }
    val messages by chatRepo.messages.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            chatRepo.sendFileMessage(context, uri, fileName, mimeType)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111827),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF4B5563))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Top Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Omni Beam Live Drop",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connected LAN Peer • Online",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                if (activeTabUrl.isNotBlank() && activeTabUrl != "about:blank") {
                    IconButton(
                        onClick = {
                            chatRepo.sendTabLink(activeTabTitle, activeTabUrl)
                            Toast.makeText(context, "Beamed current tab to connected desktop", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Tab",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1F2937))

            // Messages LazyColumn
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No messages yet",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE5E7EB),
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Send messages, photos, videos, PDFs, or current tabs instantly between your phone and laptop over Wi-Fi.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                color = Color(0xFF1F2937),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Attach File",
                            tint = Color(0xFF9CA3AF)
                        )
                    }

                    IconButton(
                        onClick = { filePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "Attach Photo",
                            tint = Color(0xFF9CA3AF)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("Type a message or drop file...", color = Color(0xFF6B7280), fontSize = 14.sp)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF111827),
                            unfocusedContainerColor = Color(0xFF111827),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF374151)
                        ),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                chatRepo.sendTextMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) Color(0xFF2563EB) else Color(0xFF374151)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
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

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val isMe = message.isFromMe
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) Color(0xFF1D4ED8) else Color(0xFF374151)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (!isMe) {
                    Text(
                        text = message.senderName,
                        color = Color(0xFF93C5FD),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                when (message.type) {
                    MessageType.TEXT -> {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    MessageType.IMAGE -> {
                        if (message.fileUriOrBase64 != null) {
                            val imgFile = File(message.fileUriOrBase64)
                            if (imgFile.exists()) {
                                AsyncImage(
                                    model = imgFile,
                                    contentDescription = message.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            openFileExternally(context, imgFile)
                                        }
                                )
                            }
                        }
                        if (!message.fileName.isNullOrBlank()) {
                            Text(
                                text = message.fileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    MessageType.VIDEO, MessageType.DOCUMENT, MessageType.AUDIO -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (message.fileUriOrBase64 != null) {
                                        openFileExternally(context, File(message.fileUriOrBase64))
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = when (message.type) {
                                    MessageType.VIDEO -> Icons.Rounded.VideoFile
                                    MessageType.AUDIO -> Icons.Rounded.AudioFile
                                    else -> Icons.Rounded.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: "File",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(message.fileSizeBytes),
                                    color = Color(0xFFD1D5DB),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    MessageType.TAB_LINK -> {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    message.tabUrl?.let { onOpenUrl(it) }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.tabTitle ?: "Web Link",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = message.tabUrl ?: "",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = "Delivered",
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx)
        }
    }
    return name ?: uri.lastPathSegment
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

private fun openFileExternally(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Saved in Downloads/OmniDrop folder", Toast.LENGTH_SHORT).show()
    }
}
