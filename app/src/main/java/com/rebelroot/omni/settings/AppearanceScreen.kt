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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
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
                title = { Text("Appearance", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
            // Force Websites to Use Dark Theme (Moved to top)
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
                        Text("Force Websites to Use Dark Theme", color = textPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Requires app restart", color = textSecondaryColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = viewModel.forceDarkWebsites,
                        onCheckedChange = { viewModel.saveForceDarkWebsites(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                    )
                }
            }
            // ── THEME SECTION ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme Mode: Light | Dark | AMOLED
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme Mode", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val themeMode = when {
                            viewModel.isAmoledMode -> 2
                            viewModel.isDarkThemeEnabled -> 1
                            else -> 0
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf("Light", "Dark", "AMOLED")
                            val icons = listOf(
                                Icons.Rounded.LightMode,
                                Icons.Rounded.DarkMode,
                                Icons.Rounded.Brightness1
                            )
                            options.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = {
                                        when (index) {
                                            0 -> {
                                                viewModel.saveDarkTheme(context, false)
                                                viewModel.saveAmoledMode(context, false)
                                            }
                                            1 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, false)
                                            }
                                            2 -> {
                                                viewModel.saveDarkTheme(context, true)
                                                viewModel.saveAmoledMode(context, true)
                                            }
                                        }
                                    },
                                    selected = themeMode == index,
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = themeMode == index) {
                                            Icon(imageVector = icons[index], contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                                        }
                                    }
                                ) {
                                    Text(label, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // UI Scale Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UI Scale", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("Smaller", color = textSecondaryColor, fontSize = 11.sp)
                            Text("Default", color = textSecondaryColor, fontSize = 11.sp)
                            Text("Larger", color = textSecondaryColor, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = dividerColor)



                    // Accent Color selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Accent Color",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (false) {
                            Text(
                                "Wallpaper colors active — accent selection overridden",
                                color = textSecondaryColor,
                                fontSize = 11.sp
                            )
                        } else {
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
            }

            // Navigation Visibility Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Navigation", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
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
                            Text("All-in-One Menu Bar", color = textPrimaryColor, fontSize = 16.sp)
                            Text("Chrome-style single top bar (hides bottom nav)", color = textSecondaryColor, fontSize = 12.sp)
                        }
                        Switch(
                            checked = viewModel.chromeNavBarEnabled,
                            onCheckedChange = { viewModel.saveChromeNavBarEnabled(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    // All-in-One preview strip — shown when the toggle is ON
                    AnimatedVisibility(visible = viewModel.chromeNavBarEnabled) {
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
                                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(16.dp))
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
                        Text("Hide Upper Navigation Bar", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                        Text("Hide Bottom Navigation Bar", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.navBarHideBottom,
                            onCheckedChange = { viewModel.saveNavBarHideBottom(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                }
            }

            // Address Bar position
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Address Bar", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
                        Text("Top", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                        Text("Bottom", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                        Text("Split", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (viewModel.addressBarPosition == "Split") {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                }
            }

            // Theme Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
                            .clickable { viewModel.saveDarkTheme(context, false) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Light Theme", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (!viewModel.isDarkThemeEnabled) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.saveDarkTheme(context, true) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Theme", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (viewModel.isDarkThemeEnabled) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor)
                        }
                    }
                }
            }



            // App Icon selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("App Icon", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                val presets = listOf(
                    Triple("Default", com.rebelroot.omni.R.drawable.ic_omni_logo, Color.White to Color.Unspecified),
                    Triple("Dark",    com.rebelroot.omni.R.drawable.ic_omni_logo, Color(0xFF0D0D0F) to Color.Unspecified)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { (label, resId, colors) ->
                                val (bgCol, iconCol) = colors
                                val isSelected = viewModel.appIconState == label && viewModel.customIconPath == null
                                Card(
                                    onClick = {
                                        viewModel.saveAppIconState(context, label)
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
                                                .background(bgCol)
                                                .border(
                                                    1.dp,
                                                    if (label == "Default") Color.LightGray.copy(alpha = 0.4f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(14.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = resId),
                                                contentDescription = null,
                                                tint = iconCol,
                                                modifier = Modifier.size(38.dp)
                                            )
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
                            // Fill last row if odd number
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Info note
                Text(
                    "⚠️  Changing the app icon may briefly remove it from your home screen. It reappears in a few seconds.",
                    color = textSecondaryColor,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }


            // New Tab Page Customization Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New Tab Page", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
                        Text("Show Logo", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                        Text("Show Shortcuts", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                        Text("Show Discover Feed", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.showDiscoverFeed,
                            onCheckedChange = { viewModel.saveShowDiscoverFeed(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWallpapers() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Wallpaper", color = textPrimaryColor, fontSize = 16.sp)
                                Surface(
                                    color = Color(0xFFFF9500).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Experimental",
                                        color = Color(0xFFFF9500),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Browser background, dynamic wallpaper blur/dim", color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textSecondaryColor
                        )
                    }
                }
            }
        }
    }
}
