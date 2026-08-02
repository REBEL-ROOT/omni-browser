/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.adblock.AdBlockProvider
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF111111) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val adBlockManager = viewModel.adBlockManager
    val providers by adBlockManager.providers.collectAsState()
    val isSyncing by adBlockManager.isSyncing.collectAsState()
    var isMasterEnabled by remember { mutableStateOf(adBlockManager.isMasterEnabled) }

    var showAddCustomDialog by remember { mutableStateOf(false) }
    var customNameInput by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.adblock_screen_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = textPrimaryColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { adBlockManager.syncAllProviders() },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accentColor)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Sync All", tint = accentColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f)))
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── MASTER TOGGLE & STATS HEADER ─────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardColor,
                border = BorderStroke(0.5.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(stringResource(id = R.string.adblock_master_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimaryColor)
                                Text(if (isMasterEnabled) stringResource(id = R.string.adblock_active) else stringResource(id = R.string.adblock_disabled), fontSize = 12.sp, color = textSecondaryColor)
                            }
                        }

                        Switch(
                            checked = isMasterEnabled,
                            onCheckedChange = { enabled ->
                                isMasterEnabled = enabled
                                adBlockManager.isMasterEnabled = enabled
                                viewModel.updateRuntimeContentBlocking(context)
                            }
                        )
                    }

                    HorizontalDivider(color = dividerColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${adBlockManager.totalBlockedCount} ${stringResource(id = R.string.adblock_total_blocked)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                            Text(stringResource(id = R.string.adblock_total_blocked_desc), fontSize = 11.sp, color = textSecondaryColor)
                        }

                        TextButton(onClick = { adBlockManager.clearBlockedStats() }) {
                            Text(stringResource(id = R.string.adblock_clear_stats), fontSize = 11.sp, color = textSecondaryColor)
                        }
                    }
                }
            }

            // ── PRESET FILTER LIST PROVIDERS ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.adblock_builtin_providers), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    val presets = providers.filter { it.isPreset }
                    presets.forEachIndexed { index, provider ->
                        ProviderItemRow(
                            provider = provider,
                            onToggle = { enabled ->
                                adBlockManager.toggleProvider(provider.id, enabled)
                            },
                            onSync = {
                                adBlockManager.syncProvider(provider)
                            },
                            onDelete = null,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            accentColor = accentColor
                        )
                        if (index < presets.size - 1) {
                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // ── CUSTOM FILTER PROVIDERS SECTION ──────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(id = R.string.adblock_custom_providers), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    TextButton(onClick = { showAddCustomDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.adblock_add_custom_url), fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }

                val customList = providers.filter { !it.isPreset }
                if (customList.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        border = BorderStroke(0.5.dp, cardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.FilterList, contentDescription = null, tint = textSecondaryColor.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                            Text(stringResource(id = R.string.adblock_no_custom), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimaryColor)
                            Text(stringResource(id = R.string.adblock_no_custom_desc), fontSize = 12.sp, color = textSecondaryColor)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardColor)
                            .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                    ) {
                        customList.forEachIndexed { index, provider ->
                            ProviderItemRow(
                                provider = provider,
                                onToggle = { enabled ->
                                    adBlockManager.toggleProvider(provider.id, enabled)
                                },
                                onSync = {
                                    adBlockManager.syncProvider(provider)
                                },
                                onDelete = {
                                    adBlockManager.removeProvider(provider.id)
                                },
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor,
                                accentColor = accentColor
                            )
                            if (index < customList.size - 1) {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Custom Provider Dialog
    if (showAddCustomDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("Add Custom Filter Provider", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter a blocklist URL (EasyList or Hosts format):", fontSize = 12.sp, color = textSecondaryColor)
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text("Provider Name (e.g. My Blocklist)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = { Text("Filter List URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customUrlInput.isNotBlank()) {
                            adBlockManager.addCustomProvider(
                                name = customNameInput.ifBlank { "Custom Filter" },
                                url = customUrlInput
                            )
                            customNameInput = ""
                            customUrlInput = ""
                            showAddCustomDialog = false
                        }
                    }
                ) {
                    Text("Add Provider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProviderItemRow(
    provider: AdBlockProvider,
    onToggle: (Boolean) -> Unit,
    onSync: () -> Unit,
    onDelete: (() -> Unit)?,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val dateStr = if (provider.lastUpdated > 0) {
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(provider.lastUpdated))
            } else "Not synced"
            Text(
                text = "${provider.ruleCount} rules • $dateStr",
                fontSize = 11.sp,
                color = textSecondaryColor
            )
            Text(
                text = provider.url,
                fontSize = 10.sp,
                color = textSecondaryColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onSync) {
                Icon(Icons.Rounded.Sync, contentDescription = "Sync", tint = accentColor, modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            Switch(
                checked = provider.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}
