/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import coil.compose.AsyncImage
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenDownloads: () -> Unit = {}
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF111111) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    var showDefaultDownloaderDialog by remember { mutableStateOf(false) }
    var showConcurrentLimitDialog by remember { mutableStateOf(false) }
    var showExtensionPolicyDialog by remember { mutableStateOf(false) }

    val externalApps = remember(context) {
        viewModel.getAvailableExternalDownloaders(context)
    }

    val currentDownloaderLabel = remember(viewModel.defaultDownloader, externalApps) {
        when {
            viewModel.defaultDownloader == "internal" -> context.getString(R.string.downloader_internal)
            viewModel.defaultDownloader == "system" -> context.getString(R.string.downloader_system)
            viewModel.defaultDownloader == "external_chooser" -> context.getString(R.string.downloader_external_chooser)
            viewModel.defaultDownloader.startsWith("package:") -> {
                val pkg = viewModel.defaultDownloader.substringAfter("package:")
                val app = externalApps.firstOrNull { it.packageName == pkg }
                app?.name ?: pkg
            }
            else -> context.getString(R.string.downloader_internal)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.download_settings_title),
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = textPrimaryColor
                        )
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── DEFAULT DOWNLOAD MANAGER ─────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.default_downloader_title).uppercase(),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDefaultDownloaderDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(id = R.string.default_downloader_title),
                                color = textPrimaryColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                currentDownloaderLabel,
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Change",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── DOWNLOAD PREFERENCES ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.download_preferences_section),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    // Ask before downloading
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAskBeforeDownload(context) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.QuestionAnswer, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.ask_before_download_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.ask_before_download_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = viewModel.askBeforeDownload,
                            onCheckedChange = { viewModel.toggleAskBeforeDownload(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Download over Wi-Fi only
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDownloadWifiOnly(context) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.download_wifi_only_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.download_wifi_only_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = viewModel.downloadWifiOnly,
                            onCheckedChange = { viewModel.toggleDownloadWifiOnly(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Max concurrent downloads
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showConcurrentLimitDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.max_concurrent_downloads_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (viewModel.maxConcurrentDownloads >= 99) "Unlimited downloads" else "${viewModel.maxConcurrentDownloads} tasks at once",
                                color = textSecondaryColor,
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Extension downloads policy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExtensionPolicyDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.extension_download_policy_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val policyLabel = when (viewModel.extensionDownloadPolicy) {
                                BrowserViewModel.ExtensionDownloadPolicy.ASK_EVERY_TIME -> stringResource(R.string.extension_download_policy_ask)
                                BrowserViewModel.ExtensionDownloadPolicy.ALLOW_TRUSTED -> stringResource(R.string.extension_download_policy_allow)
                                BrowserViewModel.ExtensionDownloadPolicy.NEVER_ALLOW -> stringResource(R.string.extension_download_policy_never)
                            }
                            Text(policyLabel, color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }
                }
            }

            // ── NOTIFICATIONS & SOUNDS ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.download_notifications_section),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDownloadNotificationsEnabled(context) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.download_notifications_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.download_notifications_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = viewModel.downloadNotificationsEnabled,
                            onCheckedChange = { viewModel.toggleDownloadNotificationsEnabled(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDownloadSoundEnabled(context) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.download_sound_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.download_sound_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = viewModel.downloadSoundEnabled,
                            onCheckedChange = { viewModel.toggleDownloadSoundEnabled(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDownloadVibrateEnabled(context) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Vibration, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.download_vibrate_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.download_vibrate_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = viewModel.downloadVibrateEnabled,
                            onCheckedChange = { viewModel.toggleDownloadVibrateEnabled(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }
                }
            }

            // ── STORAGE & QUICK ACCESS ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.download_storage_section),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.download_location_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("/storage/emulated/0/Download", color = textSecondaryColor, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDownloads() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DownloadForOffline, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.view_downloads_manager_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.view_downloads_manager_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }
                }
            }
        }
    }

    // ── DEFAULT DOWNLOADER SELECTION DIALOG ──────────────────────────────
    if (showDefaultDownloaderDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultDownloaderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = accentColor)
                    Text(stringResource(id = R.string.default_downloader_title), color = textPrimaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardColor,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(id = R.string.default_downloader_desc),
                        color = textSecondaryColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Option 1: Omni Downloader (Built-in)
                    DownloaderOptionRow(
                        title = stringResource(id = R.string.downloader_internal),
                        subtitle = stringResource(id = R.string.downloader_internal_desc),
                        icon = Icons.Rounded.Bolt,
                        isSelected = viewModel.defaultDownloader == "internal",
                        accentColor = accentColor,
                        textPrimary = textPrimaryColor,
                        textSecondary = textSecondaryColor,
                        cardBorder = cardBorderColor,
                        onClick = {
                            viewModel.setDefaultDownloader(context, "internal")
                            showDefaultDownloaderDialog = false
                        }
                    )

                    // Option 2: Android System DownloadManager
                    DownloaderOptionRow(
                        title = stringResource(id = R.string.downloader_system),
                        subtitle = stringResource(id = R.string.downloader_system_desc),
                        icon = Icons.Rounded.Android,
                        isSelected = viewModel.defaultDownloader == "system",
                        accentColor = accentColor,
                        textPrimary = textPrimaryColor,
                        textSecondary = textSecondaryColor,
                        cardBorder = cardBorderColor,
                        onClick = {
                            viewModel.setDefaultDownloader(context, "system")
                            showDefaultDownloaderDialog = false
                        }
                    )

                    // Option 3: External Downloader (Always Ask Chooser)
                    DownloaderOptionRow(
                        title = stringResource(id = R.string.downloader_external_chooser),
                        subtitle = stringResource(id = R.string.downloader_external_chooser_desc),
                        icon = Icons.Rounded.CallMade,
                        isSelected = viewModel.defaultDownloader == "external_chooser",
                        accentColor = accentColor,
                        textPrimary = textPrimaryColor,
                        textSecondary = textSecondaryColor,
                        cardBorder = cardBorderColor,
                        onClick = {
                            viewModel.setDefaultDownloader(context, "external_chooser")
                            showDefaultDownloaderDialog = false
                        }
                    )

                    // Option 4+: Detected External Downloader Apps
                    if (externalApps.isNotEmpty()) {
                        Text(
                            "INSTALLED EXTERNAL DOWNLOADERS",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                        externalApps.forEach { app ->
                            val optionKey = "package:${app.packageName}"
                            DownloaderOptionRow(
                                title = app.name,
                                subtitle = stringResource(id = R.string.downloader_external_app_desc, app.name),
                                drawableIcon = app.icon,
                                fallbackIcon = Icons.Rounded.Launch,
                                isSelected = viewModel.defaultDownloader == optionKey,
                                accentColor = accentColor,
                                textPrimary = textPrimaryColor,
                                textSecondary = textSecondaryColor,
                                cardBorder = cardBorderColor,
                                onClick = {
                                    viewModel.setDefaultDownloader(context, optionKey)
                                    showDefaultDownloaderDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultDownloaderDialog = false }) {
                    Text("Close", color = accentColor)
                }
            }
        )
    }

    // ── CONCURRENT DOWNLOADS LIMIT DIALOG ──────────────────────────────
    if (showConcurrentLimitDialog) {
        val limits = listOf(1, 2, 3, 5, 8, 99)
        AlertDialog(
            onDismissRequest = { showConcurrentLimitDialog = false },
            title = { Text(stringResource(id = R.string.max_concurrent_downloads_title), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
            containerColor = cardColor,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    limits.forEach { limit ->
                        val label = if (limit >= 99) "Unlimited parallel downloads" else "$limit active downloads at once"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setMaxConcurrentDownloads(context, limit)
                                    showConcurrentLimitDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = textPrimaryColor, fontSize = 14.sp)
                            RadioButton(
                                selected = (viewModel.maxConcurrentDownloads == limit || (limit == 99 && viewModel.maxConcurrentDownloads >= 99)),
                                onClick = {
                                    viewModel.setMaxConcurrentDownloads(context, limit)
                                    showConcurrentLimitDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConcurrentLimitDialog = false }) {
                    Text("Close", color = accentColor)
                }
            }
        )
    }

    // ── EXTENSION DOWNLOAD POLICY DIALOG ──────────────────────────────
    if (showExtensionPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showExtensionPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Extension, contentDescription = null, tint = accentColor)
                    Text(stringResource(id = R.string.extension_download_policy_title), color = textPrimaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardColor,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    listOf(
                        Triple(BrowserViewModel.ExtensionDownloadPolicy.ASK_EVERY_TIME, stringResource(R.string.extension_download_policy_ask), "Prompt before each extension-initiated download"),
                        Triple(BrowserViewModel.ExtensionDownloadPolicy.ALLOW_TRUSTED, stringResource(R.string.extension_download_policy_allow), "Automatically download from installed extensions without prompting"),
                        Triple(BrowserViewModel.ExtensionDownloadPolicy.NEVER_ALLOW, stringResource(R.string.extension_download_policy_never), "Block all downloads initiated by extensions")
                    ).forEach { (policy, label, desc) ->
                        Surface(
                            onClick = {
                                viewModel.setExtensionDownloadPolicy(policy, context)
                                showExtensionPolicyDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (viewModel.extensionDownloadPolicy == policy) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (viewModel.extensionDownloadPolicy == policy) accentColor else cardBorderColor.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, color = if (viewModel.extensionDownloadPolicy == policy) accentColor else textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(desc, color = textSecondaryColor, fontSize = 11.sp)
                                }
                                RadioButton(
                                    selected = (viewModel.extensionDownloadPolicy == policy),
                                    onClick = {
                                        viewModel.setExtensionDownloadPolicy(policy, context)
                                        showExtensionPolicyDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExtensionPolicyDialog = false }) {
                    Text(stringResource(R.string.cancel_text), color = accentColor)
                }
            }
        )
    }
}

@Composable
private fun DownloaderOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    drawableIcon: android.graphics.drawable.Drawable? = null,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Download,
    isSelected: Boolean,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBorder: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(1.dp, if (isSelected) accentColor else cardBorder.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (drawableIcon != null) {
                AsyncImage(
                    model = drawableIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = icon ?: fallbackIcon,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (isSelected) accentColor else textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(subtitle, color = textSecondary, fontSize = 11.sp)
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
            )
        }
    }
}
