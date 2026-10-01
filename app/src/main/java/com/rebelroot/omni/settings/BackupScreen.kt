/*
 * Omni Browser - Backup & Restore screen
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Lets the user pick which categories to include in an export (settings,
 * bookmarks, history, passwords, notes), then save or share a JSON file.
 * Import inspects a file and lets the user choose which sections to restore.
 */

package com.rebelroot.omni.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.backup.BackupEngine
import com.rebelroot.omni.backup.BackupInspection
import com.rebelroot.omni.backup.BackupSection
import com.rebelroot.omni.browser.BackupImportResult
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onRestored: (tabsRestored: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = Color(0xFFFFA726)

    var selected by remember {
        mutableStateOf(BackupSection.entries.filter { it.defaultEnabled }.toSet())
    }
    var showExportSheet by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingInspection by remember { mutableStateOf<BackupInspection?>(null) }
    var importSelection by remember { mutableStateOf<Set<BackupSection>>(emptySet()) }

    BackHandler { onNavigateBack() }

    fun saveJsonToUri(uri: Uri) {
        val sections = selected
        if (sections.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.backup_none_selected), Toast.LENGTH_SHORT).show()
            return
        }
        isBusy = true
        scope.launch {
            val result = runCatching {
                val json = BackupEngine.build(context, viewModel, sections)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Cannot open destination file")
                }
            }
            isBusy = false
            Toast.makeText(
                context,
                result.fold(
                    { context.getString(R.string.backup_export_success) },
                    { context.getString(R.string.backup_export_failed, it.message ?: "") }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun shareJson() {
        val sections = selected
        if (sections.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.backup_none_selected), Toast.LENGTH_SHORT).show()
            return
        }
        isBusy = true
        scope.launch {
            runCatching {
                val json = BackupEngine.build(context, viewModel, sections)
                val name = "omni-browser-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
                val file = File(context.cacheDir, name).apply { writeText(json, Charsets.UTF_8) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "com.rebelroot.omni.fileprovider", file
                )
                withContext(Dispatchers.Main) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.backup_share_subject))
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(send, context.getString(R.string.export_share))
                    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_export_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
            isBusy = false
        }
    }

    // Save-to-device (SAF). CreateDocument(mime) is avoided on pre-Q OEM ROMs.
    val saveLauncher = rememberLauncherForActivityResult(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            ActivityResultContracts.CreateDocument("application/json")
        else
            ActivityResultContracts.CreateDocument()
    ) { uri -> if (uri != null) saveJsonToUri(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }
                }.getOrNull()
                val inspection = text?.let { BackupEngine.inspect(it) }
                if (text == null || inspection == null || inspection.available.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.backup_empty_file), Toast.LENGTH_LONG).show()
                } else {
                    pendingImport = text
                    pendingInspection = inspection
                    importSelection = inspection.available.toSet()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(id = R.string.backup_title), fontWeight = FontWeight.Bold, color = textPrimaryColor)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f)))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Export ───────────────────────────────────────────────────────
            Text(
                stringResource(id = R.string.backup_export_section),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(id = R.string.backup_export_desc),
                color = textSecondaryColor,
                fontSize = 12.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardColor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, cardBorderColor)
            ) {
                Column {
                    BackupSection.entries.forEachIndexed { index, section ->
                        if (index > 0) {
                            HorizontalDivider(color = cardBorderColor.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
                        }
                        SectionToggleRow(
                            section = section,
                            checked = section in selected,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            warningColor = warningColor,
                            onToggle = { on ->
                                selected = if (on) selected + section else selected - section
                            }
                        )
                    }
                }
            }

            Button(
                onClick = { showExportSheet = true },
                enabled = selected.isNotEmpty() && !isBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(id = R.string.backup_export_button))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Import ───────────────────────────────────────────────────────
            Text(
                stringResource(id = R.string.backup_import_section),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(id = R.string.backup_import_desc),
                color = textSecondaryColor,
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(id = R.string.backup_import_button))
            }
        }
    }

    // Export destination sheet
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(id = R.string.backup_export_button),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textPrimaryColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DestinationRow(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(id = R.string.export_save_to_device),
                    subtitle = stringResource(id = R.string.backup_save_to_device_desc),
                    onClick = {
                        showExportSheet = false
                        val name = "omni-browser-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
                        saveLauncher.launch(name)
                    }
                )
                DestinationRow(
                    icon = Icons.Rounded.Share,
                    title = stringResource(id = R.string.export_share),
                    subtitle = stringResource(id = R.string.backup_share_desc),
                    onClick = { showExportSheet = false; shareJson() }
                )
            }
        }
    }

    // Import confirm sheet with per-section checkboxes
    val inspection = pendingInspection
    if (inspection != null && pendingImport != null) {
        ModalBottomSheet(
            onDismissRequest = { pendingImport = null; pendingInspection = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(id = R.string.backup_import_preview_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textPrimaryColor
                )
                Text(stringResource(id = R.string.backup_import_contains), color = textSecondaryColor, fontSize = 12.sp)
                inspection.available.forEach { section ->
                    val count = inspection.countFor(section)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                importSelection = if (section in importSelection) importSelection - section else importSelection + section
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = section in importSelection,
                            onCheckedChange = { on ->
                                importSelection = if (on) importSelection + section else importSelection - section
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sectionLabel(section), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimaryColor)
                            Text(
                                stringResource(id = R.string.backup_item_count, count),
                                fontSize = 12.sp,
                                color = textSecondaryColor
                            )
                        }
                        if (section.sensitive) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = warningColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (importSelection.any { it.sensitive }) {
                    Text(
                        stringResource(id = R.string.backup_passwords_warning),
                        color = warningColor,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pendingImport = null; pendingInspection = null },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(id = R.string.import_cancel)) }
                    Button(
                        onClick = {
                            val text = pendingImport!!
                            val sections = importSelection
                            pendingImport = null
                            pendingInspection = null
                            if (sections.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.backup_none_selected), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isBusy = true
                            scope.launch {
                                when (val r = BackupEngine.restore(context, viewModel, text, sections)) {
                                    is BackupImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            isBusy = false
                                            val skippedSuffix = if (r.skipped > 0)
                                                " (" + context.getString(R.string.settings_backup_skipped, r.skipped) + ")" else ""
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_restore_success, r.restored, skippedSuffix),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onRestored(BackupSection.TABS in sections)
                                        }
                                    }
                                    else -> {
                                        withContext(Dispatchers.Main) {
                                            isBusy = false
                                            Toast.makeText(context, context.getString(R.string.backup_restore_failed), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = importSelection.isNotEmpty() && !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(id = R.string.backup_restore_button)) }
                }
            }
        }
    }
}

@Composable
private fun SectionToggleRow(
    section: BackupSection,
    checked: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    warningColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sectionLabel(section), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimaryColor)
            val desc = sectionDescription(section)
            if (desc != null) {
                Text(desc, fontSize = 12.sp, color = textSecondaryColor)
            }
        }
        if (section.sensitive) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = warningColor, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun DestinationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun sectionLabel(section: BackupSection): String = when (section) {
    BackupSection.SETTINGS -> stringResource(id = R.string.backup_section_settings)
    BackupSection.BOOKMARKS -> stringResource(id = R.string.backup_section_bookmarks)
    BackupSection.SPEED_DIAL -> stringResource(id = R.string.backup_section_speed_dial)
    BackupSection.HISTORY -> stringResource(id = R.string.backup_section_history)
    BackupSection.TABS -> stringResource(id = R.string.backup_section_tabs)
    BackupSection.PASSWORDS -> stringResource(id = R.string.backup_section_passwords)
    BackupSection.NOTES -> stringResource(id = R.string.backup_section_notes)
}

@Composable
private fun sectionDescription(section: BackupSection): String? = when (section) {
    BackupSection.SETTINGS -> stringResource(id = R.string.backup_section_settings_desc)
    BackupSection.SPEED_DIAL -> stringResource(id = R.string.backup_section_speed_dial_desc)
    BackupSection.TABS -> stringResource(id = R.string.backup_section_tabs_desc)
    BackupSection.PASSWORDS -> stringResource(id = R.string.backup_section_passwords_desc)
    else -> null
}