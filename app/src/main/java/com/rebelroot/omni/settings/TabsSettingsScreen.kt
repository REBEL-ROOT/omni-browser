/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
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

    var showAutoCloseDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.tabs_settings_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.tabs_management), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    // Layout selection
                    val currentLayoutLabel = if (viewModel.tabLayoutMode == "Grid") stringResource(id = R.string.tab_layout_grid) else stringResource(id = R.string.tab_layout_list)
                    SettingsRow(
                        icon = Icons.Rounded.GridView,
                        title = stringResource(id = R.string.tab_layout_title),
                        subtitle = currentLayoutLabel,
                        onClick = { showLayoutDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Open tabs in background
                    SettingsSwitchRow(
                        icon = Icons.Rounded.TabUnselected,
                        title = stringResource(id = R.string.open_tabs_in_background),
                        subtitle = stringResource(id = R.string.open_tabs_in_background_desc),
                        checked = viewModel.openTabsInBackground,
                        onCheckedChange = { viewModel.saveOpenTabsInBackground(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Auto-close threshold selection
                    val autoCloseSubtitle = when (viewModel.autoCloseTabsDays) {
                        1 -> stringResource(id = R.string.auto_close_1_day)
                        7 -> stringResource(id = R.string.auto_close_1_week)
                        30 -> stringResource(id = R.string.auto_close_1_month)
                        else -> stringResource(id = R.string.auto_close_never)
                    }
                    SettingsRow(
                        icon = Icons.Rounded.AccessTime,
                        title = stringResource(id = R.string.auto_close_tabs),
                        subtitle = autoCloseSubtitle,
                        onClick = { showAutoCloseDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Confirm before exit
                    SettingsSwitchRow(
                        icon = Icons.Rounded.ExitToApp,
                        title = stringResource(id = R.string.confirm_exit_title),
                        subtitle = stringResource(id = R.string.confirm_exit_desc),
                        checked = viewModel.confirmExit,
                        onCheckedChange = { viewModel.saveConfirmExit(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                }
            }
        }
    }

    // Tab Layout Selection Dialog
    if (showLayoutDialog) {
        val layouts = listOf(
            "Grid" to stringResource(id = R.string.tab_layout_grid),
            "List" to stringResource(id = R.string.tab_layout_list)
        )
        AlertDialog(
            onDismissRequest = { showLayoutDialog = false },
            title = { Text(stringResource(id = R.string.tab_layout_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    layouts.forEach { (modeKey, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.saveTabLayoutMode(context, modeKey)
                                    showLayoutDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.tabLayoutMode == modeKey,
                                onClick = {
                                    viewModel.saveTabLayoutMode(context, modeKey)
                                    showLayoutDialog = false
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Auto-Close Selector Dialog
    if (showAutoCloseDialog) {
        val options = listOf(
            0 to stringResource(id = R.string.auto_close_never),
            1 to stringResource(id = R.string.auto_close_1_day),
            7 to stringResource(id = R.string.auto_close_1_week),
            30 to stringResource(id = R.string.auto_close_1_month)
        )
        AlertDialog(
            onDismissRequest = { showAutoCloseDialog = false },
            title = { Text(stringResource(id = R.string.auto_close_tabs), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.saveAutoCloseTabsDays(context, value)
                                    showAutoCloseDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.autoCloseTabsDays == value,
                                onClick = {
                                    viewModel.saveAutoCloseTabsDays(context, value)
                                    showAutoCloseDialog = false
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
