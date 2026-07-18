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

package com.rebelroot.omni.tools.locker

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrivateLockerScreen(
    activity: FragmentActivity,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    
    // Instantiate managers
    val authManager = remember { LockerAuthManager(activity) }
    val lockerManager = remember { PrivateLockerManager(context) }
    val pinManager = remember { PinManager(context) }
    
    // States
    var isAuthenticated by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    
    val secureFiles by lockerManager.getSecureFiles().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var successCount = 0
                uris.forEach { uri ->
                    try {
                        val (name, _) = getFileNameAndSize(context, uri)
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        lockerManager.saveUriToLocker(uri, name, mimeType)
                        successCount++
                    } catch (e: java.lang.Exception) {
                        Log.e("PrivateLockerScreen", "Failed to import file: $uri", e)
                    }
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (successCount > 0) {
                        Toast.makeText(context, context.getString(R.string.locker_import_success, successCount), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.locker_import_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun getFileCategory(file: LockerFile): String {
        val mime = file.mimeType.lowercase()
        val name = file.displayName.lowercase()
        return when {
            mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> "images"
            mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".avi") || name.endsWith(".3gp") -> "videos"
            mime.equals("application/pdf") || mime.contains("msword") || mime.contains("officedocument") || name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx") -> "docs"
            mime.equals("application/epub+zip") || name.endsWith(".epub") -> "epub"
            mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".xml") -> "txt"
            else -> "others"
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                lockerManager.clearDecryptedCache()
            }
        }
    }

    // Format size helper
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.getDefault(), "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    // Format date helper
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Decrypt and open secure file using standard Android Intent
    fun openSecureFile(fileRecord: LockerFile) {
        try {
            val decryptedFile = lockerManager.decryptToCacheFile(fileRecord.id, fileRecord.displayName)
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                decryptedFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, fileRecord.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.locker_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.safe_locker_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isAuthenticated) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background layout with blur effect to enhance locked premium visual feel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
                    .background(MaterialTheme.colorScheme.background)
            )

            AnimatedContent(
                targetState = isAuthenticated,
                label = "auth_anim"
            ) { authenticated ->
                if (!authenticated) {
                    LockerLockScreen(
                        pinManager = pinManager,
                        authManager = authManager,
                        onUnlockSuccess = {
                            isAuthenticated = true
                        }
                    )
                } else {
                    // UNLOCKED FILE VIEWER GRID / LIST STATE
                    if (selectedCategory == null) {
                        // Root View: Folder Grid
                        val categories = listOf(
                            Triple("images", stringResource(R.string.locker_cat_images), Icons.Rounded.Image),
                            Triple("videos", stringResource(R.string.locker_cat_videos), Icons.Rounded.Movie),
                            Triple("docs", stringResource(R.string.locker_cat_docs), Icons.Rounded.Description),
                            Triple("epub", stringResource(R.string.locker_cat_epub), Icons.AutoMirrored.Rounded.MenuBook),
                            Triple("txt", stringResource(R.string.locker_cat_txt), Icons.AutoMirrored.Rounded.Article),
                            Triple("others", stringResource(R.string.locker_cat_others), Icons.Rounded.Folder)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.locker_folders_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            items(categories.chunked(2)) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { item ->
                                        val key = item.first
                                        val label = item.second
                                        val icon = item.third
                                        val count = secureFiles.count { getFileCategory(it) == key }
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(130.dp)
                                                .clickable { selectedCategory = key },
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                            shadowElevation = 2.dp
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Icon with gradient pill background
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.locker_items_count, count),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size < 2) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Folder Detail View
                        val categoryKey = selectedCategory!!
                        val filteredFiles = secureFiles.filter { getFileCategory(it) == categoryKey }
                        val categoryLabel = when (categoryKey) {
                            "images" -> stringResource(R.string.locker_cat_images)
                            "videos" -> stringResource(R.string.locker_cat_videos)
                            "docs" -> stringResource(R.string.locker_cat_docs)
                            "epub" -> stringResource(R.string.locker_cat_epub)
                            "txt" -> stringResource(R.string.locker_cat_txt)
                            else -> stringResource(R.string.locker_cat_others)
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Folder Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedCategory = null }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.back_desc),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.locker_folder_detail_title, categoryLabel),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = stringResource(R.string.locker_items_count, filteredFiles.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            if (filteredFiles.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.locker_empty),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredFiles) { file ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp)),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = file.displayName,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Row {
                                                        Text(
                                                            text = formatFileSize(file.sizeBytes),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            text = formatDate(file.createdAt),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }

                                                // Actions
                                                Row {
                                                    IconButton(onClick = { openSecureFile(file) }) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PlayArrow,
                                                            contentDescription = "Open file",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                lockerManager.deleteFile(file.id)
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Delete,
                                                            contentDescription = "Delete file",
                                                            tint = MaterialTheme.colorScheme.error
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
            }
        }

    }
}

enum class LockSetupStep {
    CREATE,
    CONFIRM,
    UNLOCK
}

@Composable
fun LockerLockScreen(
    pinManager: PinManager,
    authManager: LockerAuthManager,
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var setupStep by remember { mutableStateOf(if (pinManager.isPinSet()) LockSetupStep.UNLOCK else LockSetupStep.CREATE) }
    var pinInput by remember { mutableStateOf("") }
    var tempPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canBiometric = remember { authManager.canAuthenticateWithBiometrics() }

    fun triggerBiometrics() {
        if (canBiometric && setupStep == LockSetupStep.UNLOCK) {
            authManager.authenticate(
                onSuccess = {
                    onUnlockSuccess()
                },
                onError = { err ->
                    if (!err.contains("cancel", ignoreCase = true) && !err.contains("negative button", ignoreCase = true)) {
                        errorMessage = err
                    }
                }
            )
        }
    }

    LaunchedEffect(setupStep) {
        if (setupStep == LockSetupStep.UNLOCK) {
            triggerBiometrics()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = when (setupStep) {
                    LockSetupStep.CREATE -> "Choose Private Locker PIN"
                    LockSetupStep.CONFIRM -> "Confirm Private Locker PIN"
                    LockSetupStep.UNLOCK -> "Enter Private Locker PIN"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = when (setupStep) {
                    LockSetupStep.CREATE -> "Set up a 4-digit PIN for your vault"
                    LockSetupStep.CONFIRM -> "Re-enter your 4-digit PIN to confirm"
                    LockSetupStep.UNLOCK -> "Enter your 4-digit PIN to unlock files"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            .border(
                                BorderStroke(
                                    1.dp, 
                                    if (isFilled) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )

            for (row in keys) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable {
                                    if (pinInput.length < 4) {
                                        errorMessage = null
                                        pinInput += key
                                        if (pinInput.length == 4) {
                                            when (setupStep) {
                                                LockSetupStep.CREATE -> {
                                                    tempPin = pinInput
                                                    pinInput = ""
                                                    setupStep = LockSetupStep.CONFIRM
                                                }
                                                LockSetupStep.CONFIRM -> {
                                                    if (pinInput == tempPin) {
                                                        pinManager.setPin(pinInput)
                                                        onUnlockSuccess()
                                                    } else {
                                                        errorMessage = "PINs do not match. Try again."
                                                        pinInput = ""
                                                        setupStep = LockSetupStep.CREATE
                                                    }
                                                }
                                                LockSetupStep.UNLOCK -> {
                                                    if (pinManager.verifyPin(pinInput)) {
                                                        onUnlockSuccess()
                                                    } else {
                                                        errorMessage = "Incorrect PIN."
                                                        pinInput = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canBiometric && setupStep == LockSetupStep.UNLOCK) {
                    IconButton(
                        onClick = { triggerBiometrics() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(64.dp))
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable {
                            if (pinInput.length < 4) {
                                errorMessage = null
                                pinInput += "0"
                                if (pinInput.length == 4) {
                                    when (setupStep) {
                                        LockSetupStep.CREATE -> {
                                            tempPin = pinInput
                                            pinInput = ""
                                            setupStep = LockSetupStep.CONFIRM
                                        }
                                        LockSetupStep.CONFIRM -> {
                                            if (pinInput == tempPin) {
                                                pinManager.setPin(pinInput)
                                                onUnlockSuccess()
                                            } else {
                                                errorMessage = "PINs do not match. Try again."
                                                pinInput = ""
                                                setupStep = LockSetupStep.CREATE
                                            }
                                        }
                                        LockSetupStep.UNLOCK -> {
                                            if (pinManager.verifyPin(pinInput)) {
                                                onUnlockSuccess()
                                            } else {
                                                errorMessage = "Incorrect PIN."
                                                pinInput = ""
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        if (pinInput.isNotEmpty()) {
                            errorMessage = null
                            pinInput = pinInput.dropLast(1)
                        }
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    val resolvedName = cursor.getString(nameIndex)
                    if (!resolvedName.isNullOrBlank()) {
                        name = resolvedName
                    }
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("PrivateLockerScreen", "Failed to resolve file name/size", e)
    }
    return Pair(name, size)
}
