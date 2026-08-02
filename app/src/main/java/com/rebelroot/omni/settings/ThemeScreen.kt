/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel

private data class ThemeAppIconPreset(val key: String, val label: String, val resId: Int, val colors: Pair<Color, Color>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.theme_settings_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
            // ── THEME MODE SECTION ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.appearance_theme), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme Mode: Light | Creamy | Dark | AMOLED
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = R.string.theme_mode), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val themeMode = when {
                            viewModel.isAmoledMode -> 3
                            viewModel.isDarkThemeEnabled -> 2
                            viewModel.isCreamyMode -> 1
                            else -> 0
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(
                                stringResource(id = R.string.theme_light),
                                stringResource(id = R.string.theme_creamy),
                                stringResource(id = R.string.theme_dark),
                                stringResource(id = R.string.theme_amoled)
                            )
                            options.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = {
                                        when (index) {
                                            0 -> {
                                                viewModel.saveDarkTheme(context, false)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                            1 -> {
                                                viewModel.saveDarkTheme(context, false)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, true)
                                            }
                                            2 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, false)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                            3 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, true)
                                                viewModel.saveCreamyMode(context, false)
                                            }
                                        }
                                    },
                                    selected = themeMode == index,
                                    icon = {}
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (themeMode == index) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // Accent Color selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(id = R.string.accent_color),
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val accentOptions = listOf(
                            "Ocean Blue" to Color(0xFF0A84FF),
                            "Crimson Red" to Color(0xFFFF3B5C),
                            "Emerald Green" to Color(0xFF00C853),
                            "Sunset Orange" to Color(0xFFFF6D00),
                            "Royal Purple" to Color(0xFF7C4DFF),
                            "Monochrome" to Color(0xFFAAAAAA)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            accentOptions.forEach { (name, color) ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (viewModel.selectedAccentTheme == name)
                                                Modifier.border(3.dp, textPrimaryColor.copy(alpha = 0.4f), CircleShape)
                                            else Modifier
                                        )
                                        .clickable { viewModel.saveAccentTheme(context, name) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (viewModel.selectedAccentTheme == name) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── WEBSITE DARK MODE ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = R.string.appearance_force_dark_websites), color = textPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(id = R.string.appearance_force_dark_websites_desc), color = textSecondaryColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = viewModel.forceDarkWebsites,
                        onCheckedChange = { viewModel.saveForceDarkWebsites(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                    )
                }
            }

            // ── APP ICON PRESETS ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(stringResource(id = R.string.app_icon), color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                val presets = listOf(
                    ThemeAppIconPreset("Light", stringResource(id = R.string.icon_preset_light), R.drawable.ic_omni_logo, Color.White to Color.Unspecified),
                    ThemeAppIconPreset("Dark", stringResource(id = R.string.icon_preset_dark), R.drawable.ic_omni_logo, Color(0xFF0D0D0F) to Color.Unspecified),
                    ThemeAppIconPreset("Aura Dark", stringResource(id = R.string.icon_preset_aura_dark), R.drawable.ic_omni_ring_dark, Color.Unspecified to Color.Unspecified),
                    ThemeAppIconPreset("Aura Light", stringResource(id = R.string.icon_preset_aura_light), R.drawable.ic_omni_ring_light, Color.Unspecified to Color.Unspecified)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (key, label, resId, colors) ->
                                val (bgCol, iconCol) = colors
                                val isSelected = (viewModel.appIconState == key || (viewModel.appIconState == "Default" && key == "Aura Light")) && viewModel.customIconPath == null
                                Card(
                                    onClick = {
                                        viewModel.saveAppIconState(context, key)
                                        viewModel.saveCustomIconPath(context, null)
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) accentColor else cardBorderColor
                                    ),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .then(
                                                    if (bgCol != Color.Unspecified)
                                                        Modifier.background(bgCol)
                                                    else Modifier
                                                )
                                                .border(
                                                    1.dp,
                                                    if (key == "Default") Color.LightGray.copy(alpha = 0.4f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(14.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bgCol == Color.Unspecified) {
                                                Image(
                                                    painter = painterResource(id = resId),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(60.dp)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = resId),
                                                    contentDescription = null,
                                                    tint = iconCol,
                                                    modifier = Modifier.size(38.dp)
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                label,
                                                color = textPrimaryColor,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    stringResource(id = R.string.app_icon_warning),
                    color = textSecondaryColor,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
