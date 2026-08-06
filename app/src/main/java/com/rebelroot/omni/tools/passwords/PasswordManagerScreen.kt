package com.rebelroot.omni.tools.passwords

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.attachPasswordVault
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ─── Root screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(
    browserViewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val masterPasswordManager = remember(context) { MasterPasswordManager(context) }
    var masterKeyBytes by remember { mutableStateOf<ByteArray?>(null) }

    DisposableEffect(masterKeyBytes) {
        onDispose { masterKeyBytes?.fill(0) }
    }

    AnimatedContent(
        targetState = masterKeyBytes != null,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "password-manager-gate"
    ) { unlocked ->
        if (!unlocked) {
            MasterPasswordScreen(
                masterPasswordManager = masterPasswordManager,
                modifier = modifier.fillMaxSize(),
                onUnlockSuccess = { keyBytes ->
                    masterKeyBytes?.fill(0)
                    masterKeyBytes = keyBytes.copyOf()
                    browserViewModel.attachPasswordVault(context, keyBytes)
                }
            )
        } else {
            val vaultManager = remember(masterKeyBytes) {
                PasswordVaultManager(context, masterKeyBytes!!.copyOf())
            }
            DisposableEffect(vaultManager) {
                onDispose { vaultManager.close() }
            }

            VaultScreen(
                modifier = modifier,
                context = context,
                vaultManager = vaultManager
            )
        }
    }
}

// ─── Vault screen (unlocked) ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScreen(
    modifier: Modifier,
    context: Context,
    vaultManager: PasswordVaultManager
) {
    val scope = rememberCoroutineScope()

    // Search
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    // Entry list
    val entriesFlow = remember(vaultManager, searchQuery) {
        if (searchQuery.isBlank()) vaultManager.getAllPasswords()
        else vaultManager.searchPasswordsFlow(searchQuery.trim())
    }
    val entries by entriesFlow.collectAsState(initial = emptyList())

    // Sheet / dialog state
    var showAddSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PasswordEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<PasswordEntry?>(null) }

    // Overflow menu
    var overflowExpanded by remember { mutableStateOf(false) }

    // Import state
    var importPreviewState by remember { mutableStateOf<ImportPreviewState?>(null) }

    // Export warning
    var showExportWarning by remember { mutableStateOf(false) }

    // CSV import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val preview = parseCsvImport(context, uri, vaultManager)
            withContext(Dispatchers.Main) { importPreviewState = preview }
        }
    }

    // ── Sheets & dialogs ──────────────────────────────────────────────────────

    if (showAddSheet) {
        PasswordEntrySheet(
            existing = null,
            onDismiss = { showAddSheet = false },
            onSave = { entry ->
                scope.launch(Dispatchers.IO) { vaultManager.addPassword(entry) }
            }
        )
    }

    editTarget?.let { entry ->
        PasswordEntrySheet(
            existing = entry,
            onDismiss = { editTarget = null },
            onSave = { updated ->
                scope.launch(Dispatchers.IO) { vaultManager.updatePassword(updated) }
            }
        )
    }

    deleteTarget?.let { entry ->
        DeletePasswordDialog(
            entry = entry,
            onConfirm = {
                scope.launch(Dispatchers.IO) { vaultManager.deletePassword(entry.id) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    importPreviewState?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    vaultManager.importAll(preview.toImport)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.pm_import_success, preview.toImport.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                importPreviewState = null
            },
            onDismiss = { importPreviewState = null }
        )
    }

    if (showExportWarning) {
        ExportWarningDialog(
            onConfirm = {
                showExportWarning = false
                scope.launch(Dispatchers.IO) { exportPasswordsCsv(context, vaultManager) }
            },
            onDismiss = { showExportWarning = false }
        )
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pm_title)) },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.pm_search_cd))
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.pm_more_options_cd))
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pm_import_csv)) },
                                onClick = {
                                    overflowExpanded = false
                                    importLauncher.launch("text/csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pm_export_csv)) },
                                onClick = {
                                    overflowExpanded = false
                                    showExportWarning = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pm_add_password_cd))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Animated search bar
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text(stringResource(R.string.pm_search_label)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            if (entries.isEmpty()) {
                EmptyVaultState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        PasswordEntryCard(
                            entry = entry,
                            onEdit = { editTarget = entry },
                            onDelete = { deleteTarget = entry },
                            onCopyUsername = {
                                copyToClipboard(context, context.getString(R.string.pm_detail_username), entry.username)
                            },
                            onCopyPassword = {
                                copyToClipboard(context, context.getString(R.string.pm_detail_password), entry.password)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Entry card ───────────────────────────────────────────────────────────────

@Composable
private fun PasswordEntryCard(
    entry: PasswordEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Domain favicon placeholder + domain text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = entry.label.ifBlank { entry.domain },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (entry.label.isNotBlank()) {
                            Text(
                                text = entry.domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopyUsername) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.pm_copy_username_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onCopyPassword) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.pm_copy_password_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) stringResource(R.string.pm_collapse_cd) else stringResource(R.string.pm_expand_cd)
                        )
                    }
                }
            }

            // Collapsed summary
            AnimatedVisibility(visible = !expanded) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Expanded detail
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailRow(label = stringResource(R.string.pm_detail_username), value = entry.username)
                    DetailRow(label = stringResource(R.string.pm_detail_password), value = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")
                    if (entry.notes.isNotBlank()) {
                        DetailRow(label = stringResource(R.string.pm_detail_notes), value = entry.notes)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.pm_edit))
                        }
                        TextButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.pm_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultState(modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = stringResource(R.string.pm_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.pm_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Clipboard helper ─────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

// ─── CSV Import ───────────────────────────────────────────────────────────────

data class ImportPreviewState(
    val total: Int,
    val duplicates: Int,
    val toImport: List<PasswordEntry>
)

private suspend fun parseCsvImport(
    context: Context,
    uri: Uri,
    vaultManager: PasswordVaultManager
): ImportPreviewState {
    val existing = vaultManager.exportAll()
    val existingKeys = existing.map {
        it.domain.lowercase().trim() to it.username.lowercase().trim()
    }.toSet()

    val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
        ?: return ImportPreviewState(0, 0, emptyList())

    val parsed = mutableListOf<PasswordEntry>()
    var duplicates = 0

    // Detect header row — Google: "name,url,username,password"
    // Chrome uses same columns. Skip any row where username column looks like "username".
    for ((index, line) in lines.withIndex()) {
        if (line.isBlank()) continue
        val cols = parseCsvLine(line)
        if (cols.size < 4) continue

        // Skip header
        if (index == 0 && cols[2].lowercase() in listOf("username", "user name", "login")) continue

        val name = cols.getOrElse(0) { "" }.trim()
        val url = cols.getOrElse(1) { "" }.trim()
        val username = cols.getOrElse(2) { "" }.trim()
        val password = cols.getOrElse(3) { "" }.trim()

        if (username.isBlank() || password.isBlank()) continue

        // Derive domain from URL
        val domain = runCatching {
            val host = java.net.URI(url).host ?: url
            host.removePrefix("www.")
        }.getOrElse { url.removePrefix("https://").removePrefix("http://").substringBefore("/") }
            .ifBlank { name }

        val key = domain.lowercase() to username.lowercase()
        if (key in existingKeys) {
            duplicates++
            continue
        }

        val now = System.currentTimeMillis()
        parsed.add(
            PasswordEntry(
                id = UUID.randomUUID().toString(),
                label = name,
                domain = domain,
                username = username,
                password = password,
                notes = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    return ImportPreviewState(
        total = parsed.size + duplicates,
        duplicates = duplicates,
        toImport = parsed
    )
}

/**
 * Minimal RFC 4180-compliant CSV line parser that handles quoted fields with commas inside.
 */
private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val current = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && !inQuotes -> inQuotes = true
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"'); i++ // escaped quote
            }
            c == '"' && inQuotes -> inQuotes = false
            c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
            else -> current.append(c)
        }
        i++
    }
    result.add(current.toString())
    return result
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreviewState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val foundStr = if (preview.total == 1)
        stringResource(R.string.pm_import_found, preview.total)
    else
        stringResource(R.string.pm_import_found_plural, preview.total)
    val skipStr = if (preview.duplicates == 1)
        " " + stringResource(R.string.pm_import_skip, preview.duplicates)
    else if (preview.duplicates > 1)
        " " + stringResource(R.string.pm_import_skip_plural, preview.duplicates)
    else ""
    val confirmStr = stringResource(R.string.pm_import_confirm, preview.toImport.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pm_import_title)) },
        text = { Text("$foundStr.$skipStr $confirmStr") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = preview.toImport.isNotEmpty()
            ) {
                Text(stringResource(R.string.pm_import_btn, preview.toImport.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_text)) }
        }
    )
}

// ─── CSV Export ───────────────────────────────────────────────────────────────

@Composable
private fun ExportWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pm_export_title)) },
        text = { Text(stringResource(R.string.pm_export_warning)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.pm_export_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_text)) }
        }
    )
}

private suspend fun exportPasswordsCsv(context: Context, vaultManager: PasswordVaultManager) {
    try {
        val entries = vaultManager.exportAll()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(exportDir, "passwords_export_$timestamp.csv")

        file.bufferedWriter().use { writer ->
            writer.write("name,url,username,password\n")
            for (entry in entries) {
                writer.write(
                    "${csvEscape(entry.label.ifBlank { entry.domain })}," +
                        "${csvEscape(entry.domain)}," +
                        "${csvEscape(entry.username)}," +
                        "${csvEscape(entry.password)}\n"
                )
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.pm_export_csv)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Grant read permission to all resolvers
        val pm = context.packageManager
        val resolvers = pm.queryIntentActivities(
            shareIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (info in resolvers) {
            try {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        context.startActivity(chooser)

        // Best-effort delete after a short delay — user will have had time to pick a target
        kotlinx.coroutines.delay(30_000)
        file.delete()
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.pm_export_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }
}

private fun csvEscape(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}
