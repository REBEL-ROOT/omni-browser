/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenWallpapers: () -> Unit = {}
) {
    BackHandler {
        onNavigateBack()
    }
    
    val context = LocalContext.current
    var showIconDialog by remember { mutableStateOf(false) }
    val isDarkMode = viewModel.isDarkThemeEnabled
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
                title = { Text(stringResource(id = R.string.preferences_layout_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
            // Address Bar position (Moved to top)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.appearance_address_bar), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
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
                            .clickable { viewModel.saveAddressBarPosition(context, "Top") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.appearance_top), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (viewModel.addressBarPosition == "Top") {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.saveAddressBarPosition(context, "Bottom") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.appearance_bottom), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (viewModel.addressBarPosition == "Bottom") {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.saveAddressBarPosition(context, "Split") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.appearance_split), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (viewModel.addressBarPosition == "Split") {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                }
            }

            // Navigation Visibility Toggles (Moved to top)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.appearance_navigation), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    val isAllInOneEnabled = viewModel.addressBarPosition == "Top" || viewModel.addressBarPosition == "Bottom"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .alpha(if (isAllInOneEnabled) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.appearance_all_in_one), color = textPrimaryColor, fontSize = 16.sp)
                            Text(stringResource(id = R.string.appearance_all_in_one_desc), color = textSecondaryColor, fontSize = 12.sp)
                        }
                        Switch(
                            checked = viewModel.chromeNavBarEnabled && isAllInOneEnabled,
                            onCheckedChange = { viewModel.saveChromeNavBarEnabled(context, it) },
                            enabled = isAllInOneEnabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    // All-in-One preview strip — shown when the toggle is ON
                    AnimatedVisibility(visible = viewModel.chromeNavBarEnabled && isAllInOneEnabled) {
                        Column {
                            HorizontalDivider(color = dividerColor)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "Preview",
                                    color = textSecondaryColor,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 20.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFF0F0F0))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.sweepGradient(
                                                    listOf(Color(0xFF7B2FBE), Color(0xFF4A90E2), Color(0xFF7B2FBE))
                                                )
                                            )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFFFFFFF))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(10.dp))
                                        Text("https://www.rebelroot.", color = textPrimaryColor, fontSize = 10.sp, maxLines = 1)
                                    }
                                    Icon(imageVector = Icons.Rounded.Build, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(16.dp))
                                    Icon(imageVector = Icons.Rounded.Extension, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(16.dp))
                                    Box(
                                        modifier = Modifier.size(20.dp).border(1.5.dp, textSecondaryColor, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("4", color = textPrimaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.appearance_hide_upper_nav), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.navBarHideTop,
                            onCheckedChange = { viewModel.saveNavBarHideTop(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.appearance_hide_bottom_nav), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.navBarHideBottom,
                            onCheckedChange = { viewModel.saveNavBarHideBottom(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.hide_refresh_indicator), color = textPrimaryColor, fontSize = 16.sp)
                            Text(stringResource(id = R.string.hide_refresh_indicator_desc), color = textSecondaryColor, fontSize = 12.sp)
                        }
                        Switch(
                            checked = viewModel.hideRefreshIndicator,
                            onCheckedChange = { viewModel.saveHideRefreshIndicator(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                }
            }

            // ── UI SCALERS & LAYOUT ───────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.ui_scalers_layout), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // App Nav Scaler
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.app_nav_scaler), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${(viewModel.uiScale * 100).toInt()}%",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = viewModel.uiScale,
                            onValueChange = { newValue ->
                                val steppedValue = ((newValue / 0.05f) + 0.5f).toInt() * 0.05f
                                viewModel.saveUiScale(context, steppedValue.coerceIn(0.8f, 1.3f))
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else Color(0xFFE0E0E0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.scale_smaller), color = textSecondaryColor, fontSize = 11.sp)
                            Text(stringResource(id = R.string.scale_default), color = textSecondaryColor, fontSize = 11.sp)
                            Text(stringResource(id = R.string.scale_larger), color = textSecondaryColor, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // Home Screen UI Scaler
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.home_screen_ui_scale), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(viewModel.homeUiScale * 100).toInt()}%", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = viewModel.homeUiScale,
                            onValueChange = { newValue ->
                                val steppedValue = ((newValue / 0.05f) + 0.5f).toInt() * 0.05f
                                viewModel.saveHomeUiScale(context, steppedValue.coerceIn(0.8f, 1.3f))
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = dividerColor)

                    // Bottom Nav Scaler
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.bottom_nav_scale), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(viewModel.bottomNavScale * 100).toInt()}%", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = viewModel.bottomNavScale,
                            onValueChange = { newValue ->
                                val steppedValue = ((newValue / 0.05f) + 0.5f).toInt() * 0.05f
                                viewModel.saveBottomNavScale(context, steppedValue.coerceIn(0.8f, 1.3f))
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── HOME SHORTCUT TILE STYLE ─────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.home_shortcuts), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(id = R.string.shortcut_tile_style), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val styleOptions = listOf(
                            "Circle" to stringResource(id = R.string.style_circle),
                            "Squircle" to stringResource(id = R.string.style_squircle),
                            "Square" to stringResource(id = R.string.style_square),
                            "Glass" to stringResource(id = R.string.style_glass)
                        )
                        styleOptions.forEach { (style, label) ->
                            val isSelected = viewModel.shortcutTileStyle == style
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) accentColor
                                        else if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                                    )
                                    .clickable {
                                        viewModel.saveShortcutTileStyle(context, style)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else textPrimaryColor
                                )
                            }
                        }
                    }
                }
            }

            // New Tab Page Customization Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.new_tab_page), color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
                        Text(stringResource(id = R.string.show_logo), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.showHomeLogo,
                            onCheckedChange = { viewModel.saveShowHomeLogo(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.show_shortcuts), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.showHomeShortcuts,
                            onCheckedChange = { viewModel.saveShowHomeShortcuts(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.discover_feed_title), color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.showDiscoverFeed,
                            onCheckedChange = { viewModel.saveShowDiscoverFeed(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                }
            }
        }
    }
}
