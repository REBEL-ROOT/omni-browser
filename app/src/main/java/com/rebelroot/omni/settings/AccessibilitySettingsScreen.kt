/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
fun AccessibilitySettingsScreen(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.accessibility_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
            // Text Scaling Card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.acc_text_and_zoom), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(id = R.string.acc_text_scaling), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.acc_text_scaling_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Text(
                            text = "${(viewModel.accessibilityTextScale * 100).toInt()}%",
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = viewModel.accessibilityTextScale,
                        onValueChange = { viewModel.saveAccessibilityTextScale(context, it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Other settings Card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.acc_page_accessibility), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    // Force Enable Zoom Switch
                    SettingsSwitchRow(
                        icon = Icons.Rounded.ZoomIn,
                        title = stringResource(id = R.string.acc_force_zoom),
                        subtitle = stringResource(id = R.string.acc_force_zoom_desc),
                        checked = viewModel.accessibilityForceZoom,
                        onCheckedChange = { viewModel.saveAccessibilityForceZoom(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // High Contrast Mode Switch
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(id = R.string.acc_high_contrast),
                        subtitle = stringResource(id = R.string.acc_high_contrast_desc),
                        checked = viewModel.accessibilityHighContrast,
                        onCheckedChange = { viewModel.saveAccessibilityHighContrast(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                }
            }

            // System Accessibility Link Card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.acc_system_preferences), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        title = stringResource(id = R.string.acc_system_accessibility),
                        subtitle = stringResource(id = R.string.acc_system_accessibility_desc),
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}
