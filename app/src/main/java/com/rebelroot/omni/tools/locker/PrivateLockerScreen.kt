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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
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
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var isPinSetupMode by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .hazeChild(state = hazeState)
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_desc))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "🔒 " + stringResource(R.string.safe_locker_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        floatingActionButton = {
            if (isAuthenticated) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
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
                    // LOCKED STATE
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp)
                                .border(
                                    BorderStroke(
                                        width = 0.5.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            )
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.locker_vault_locked),
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.locker_vault_locked),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.locker_auth_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                
                                Button(
                                    onClick = {
                                        authManager.authenticate(
                                            onSuccess = {
                                                isAuthenticated = true
                                                authError = null
                                            },
                                            onError = { error ->
                                                authError = error
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.locker_unlock_vault))
                                }

                                authError?.let { error ->
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (error.contains("not configured") && !pinManager.isPinSet()) {
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(onClick = {
                                            isPinSetupMode = true
                                            pinInput = ""
                                            pinError = null
                                            showPinDialog = true
                                        }) {
                                            Text(stringResource(R.string.locker_setup_pin))
                                        }
                                    }
                                }

                                if (pinManager.isPinSet()) {
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            isPinSetupMode = false
                                            pinInput = ""
                                            pinError = null
                                            showPinDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.locker_unlock_pin))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // UNLOCKED FILE VIEWER GRID / LIST STATE
                    if (selectedCategory == null) {
                        // Root View: Folder Grid
                        val categories = listOf(
                            Triple("images", stringResource(R.string.locker_cat_images), Icons.Rounded.Image),
                            Triple("videos", stringResource(R.string.locker_cat_videos), Icons.Rounded.Movie),
                            Triple("docs", stringResource(R.string.locker_cat_docs), Icons.Rounded.Description),
                            Triple("epub", stringResource(R.string.locker_cat_epub), Icons.Rounded.MenuBook),
                            Triple("txt", stringResource(R.string.locker_cat_txt), Icons.Rounded.Article),
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
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    pair.forEach { item ->
                                        val key = item.first
                                        val label = item.second
                                        val icon = item.third
                                        val count = secureFiles.count { getFileCategory(it) == key }
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(120.dp)
                                                .clickable { selectedCategory = key }
                                                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), RoundedCornerShape(16.dp)),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.locker_items_count, count),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text(if (isPinSetupMode) stringResource(R.string.locker_setup_pin_title) else stringResource(R.string.locker_enter_pin_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it.take(8) }, // Max 8 chars
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            label = { Text("PIN") }
                        )
                        pinError?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (pinInput.length < 4) {
                            pinError = context.getString(R.string.locker_pin_length_error)
                            return@Button
                        }
                        if (isPinSetupMode) {
                            pinManager.setPin(pinInput)
                            showPinDialog = false
                            isAuthenticated = true
                        } else {
                            if (pinManager.verifyPin(pinInput)) {
                                showPinDialog = false
                                isAuthenticated = true
                            } else {
                                pinError = context.getString(R.string.locker_pin_incorrect)
                                pinInput = ""
                            }
                        }
                    }) {
                        Text(if (isPinSetupMode) stringResource(R.string.locker_save_unlock) else stringResource(R.string.locker_unlock_vault))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text(stringResource(R.string.cancel_text))
                    }
                }
            )
        }
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
