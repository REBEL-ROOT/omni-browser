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

package com.rebelroot.omni.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebelroot.omni.ai.models.ModelInstallState
import com.rebelroot.omni.ai.models.ModelPlatform
import com.rebelroot.omni.ai.models.ModelState
import com.rebelroot.omni.ai.translation.TranslationMode
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.dataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Settings → Offline AI: model download/management and translation-mode policy.
 *
 * Models are downloaded from the app-controlled catalog, verified (size +
 * SHA-256 when pinned) and atomically installed — nothing is bundled in the APK.
 */
@Composable
fun OfflineAiSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = remember { ModelPlatform.get(context) }
    val repoStates by platform.repository.states.collectAsState()
    val isDark = viewModel.isDarkThemeEnabled
    val textPrimary = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    val textSecondary = if (isDark) androidx.compose.ui.graphics.Color(0xFF8E8E93) else androidx.compose.ui.graphics.Color(0xFF8E8E93)

    var translationMode by remember { mutableStateOf(viewModel.translationManager.getMode()) }

    // Restore persisted mode on open.
    LaunchedEffect(Unit) {
        val saved = context.dataStore.data.firstOrNull()
        val restored = saved?.let { TranslationMode.fromPreference(it[TRANSLATION_MODE_KEY]) }
        if (restored != null) {
            viewModel.translationManager.setMode(restored)
            translationMode = restored
        }
    }

    fun persistMode(mode: TranslationMode) {
        viewModel.translationManager.setMode(mode)
        translationMode = mode
        scope.launch {
            context.dataStore.edit { it[TRANSLATION_MODE_KEY] = when (mode) {
                TranslationMode.OFFLINE_ONLY -> "offline_only"
                TranslationMode.ONLINE_ONLY -> "online_only"
                TranslationMode.ASK -> "ask"
            } }
        }
    }

    BackHandler { onNavigateBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Offline AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("On-device translation & captions — no cloud", color = textSecondary, fontSize = 12.sp)
            }
        }

        // ── Translation policy ───────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Translation Mode", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("OFFLINE_ONLY never contacts a translation service.", color = textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        TranslationMode.OFFLINE_ONLY to "Offline",
                        TranslationMode.ASK to "Ask",
                        TranslationMode.ONLINE_ONLY to "Online"
                    ).forEach { (mode, label) ->
                        val selected = translationMode == mode
                        Surface(
                            onClick = { persistMode(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    label,
                                    color = if (selected) MaterialTheme.colorScheme.primary else textPrimary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Installed models / storage ───────────────────────────────────────
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Installed Models", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(formatBytes(platform.installedBytes()), color = textSecondary, fontSize = 12.sp)
                }
                Text("Models are downloaded, verified and stored only on this device.", color = textSecondary, fontSize = 12.sp)

                val models = platform.catalog.all()
                if (models.isEmpty()) {
                    EmptyModelsState(
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
                models.forEach { descriptor ->
                    val state: ModelState = repoStates[descriptor.id] ?: ModelState(descriptor = descriptor)
                    val installed = state.isInstalled || platform.repository.isInstalled(descriptor)
                    ModelRow(
                        name = descriptor.name,
                        detail = buildString {
                            append(descriptor.sourceProject)
                            descriptor.sourceLanguage?.let { append(" • ").append(it.uppercase()) }
                            descriptor.targetLanguage?.let { append(" → ").append(it.uppercase()) }
                            if (descriptor.sizeBytes > 0) append(" • ").append(formatBytes(descriptor.sizeBytes))
                            if (!descriptor.isChecksumPinned) append(" • unverified size")
                        },
                        state = state,
                        installed = installed,
                        onDownload = {
                            scope.launch {
                                platform.repository.install(descriptor)
                                // Wire the platform and make a freshly downloaded
                                // translation model available to the translator.
                                viewModel.translationManager.attachPlatform(platform)
                            }
                        },
                        onDelete = {
                            platform.repository.delete(descriptor.id)
                            viewModel.translationManager.attachPlatform(platform)
                            Toast.makeText(context, "Model deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ModelRow(
    name: String,
    detail: String,
    state: ModelState,
    installed: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color(0xFF141416)
    val textPrimary = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1C1C1E)
    val textSecondary = if (isDark) androidx.compose.ui.graphics.Color(0xFF8E8E93) else androidx.compose.ui.graphics.Color(0xFF8E8E93)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(detail, color = textSecondary, fontSize = 11.sp)
                }
                when {
                    installed -> {
                        Text("Installed", color = androidx.compose.ui.graphics.Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    state.status == ModelInstallState.DOWNLOADING || state.status == ModelInstallState.VERIFYING -> {
                        Text("${state.progress.bytesDownloaded} / ${state.progress.totalBytes}", color = textSecondary, fontSize = 12.sp)
                    }
                    state.status == ModelInstallState.FAILED -> {
                        Text("Failed", color = androidx.compose.ui.graphics.Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.status == ModelInstallState.DOWNLOADING || state.status == ModelInstallState.VERIFYING) {
                LinearProgressIndicator(
                    progress = { if (state.progress.isIndeterminate) 0.4f else state.progress.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.status == ModelInstallState.FAILED && state.errorMessage != null) {
                Text(state.errorMessage, color = androidx.compose.ui.graphics.Color(0xFFFF3B30), fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!installed && state.status != ModelInstallState.DOWNLOADING && state.status != ModelInstallState.VERIFYING) {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (installed) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyModelsState(
    isDark: Boolean,
    textPrimary: androidx.compose.ui.graphics.Color,
    textSecondary: androidx.compose.ui.graphics.Color
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = "No models available yet",
            color = textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Text(
            text = "AI models for on-device translation and captions are downloaded from the catalog. Check back later for available models.",
            color = textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}

private val TRANSLATION_MODE_KEY = stringPreferencesKey("translation_mode")
