/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    
    val context = LocalContext.current
    var showIconDialog by remember { mutableStateOf(false) }
    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (isDarkMode) Color(0xFF0B0B0C) else Color(0xFFF2F3F5)
    val cardColor = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val textPrimaryColor = if (isDarkMode) Color.White else Color(0xFF1C1C1E)
    val textSecondaryColor = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val dividerColor = if (isDarkMode) Color(0xFF2C2C2E).copy(alpha = 0.5f) else Color(0xFFE5E5EA)

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
            // Navigation Visibility Toggles — FIRST (most important nav setting)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Navigation", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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

            // Force Websites to Use Dark Theme
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
                        Text("Force Websites to Use Dark Theme", color = textPrimaryColor, fontSize = 16.sp)
                        Text("Requires app restart", color = textSecondaryColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = viewModel.forceDarkWebsites,
                        onCheckedChange = { viewModel.saveForceDarkWebsites(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
            }

            // App Icon selection
            val coroutineScope = rememberCoroutineScope()
            val customIconPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Copy to internal storage so path stays valid
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val destFile = File(context.filesDir, "custom_app_icon.png")
                            inputStream?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                viewModel.saveCustomIconPath(context, destFile.absolutePath)
                                viewModel.saveAppIconState(context, "Custom")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppearanceScreen", "Failed to copy custom icon: ${e.message}")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("App Icon", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Surface(
                        color = Color(0xFFFF9500).copy(alpha = 0.15f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "⚠ Experimental",
                            color = Color(0xFFFF9500),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 2×2 preset icon grid
                // 2×2 preset icon grid using identity logo (ic_omni_logo)
                val presets = listOf(
                    Triple("Default", com.rebelroot.omni.R.drawable.ic_omni_logo, Color.White to Color.Unspecified),
                    Triple("Dark",    com.rebelroot.omni.R.drawable.ic_omni_logo, Color(0xFF0D0D0F) to Color.White),
                    Triple("Blue",    com.rebelroot.omni.R.drawable.ic_omni_logo, Color(0xFF0A84FF) to Color.White),
                    Triple("Gold",    com.rebelroot.omni.R.drawable.ic_omni_logo, Color(0xFFFFB800) to Color(0xFF1C1C1E))
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

                // Custom icon card
                val isCustomSelected = viewModel.customIconPath != null
                Card(
                    onClick = { customIconPickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = if (isCustomSelected) 2.5.dp else 1.dp,
                        color = if (isCustomSelected) accentColor else cardBorderColor
                    ),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Preview or placeholder
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFF0F0F0)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.customIconPath != null) {
                                val file = File(viewModel.customIconPath!!)
                                if (file.exists()) {
                                    androidx.compose.foundation.Image(
                                        bitmap = remember(viewModel.customIconPath) {
                                            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                            bmp.asImageBitmap()
                                        },
                                        contentDescription = "Custom icon",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(28.dp))
                                }
                            } else {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(28.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Custom Icon",
                                color = textPrimaryColor,
                                fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Text(
                                if (isCustomSelected) "Tap to change image" else "Choose from gallery",
                                color = textSecondaryColor,
                                fontSize = 12.sp
                            )
                        }

                        // Clear button (only when custom is active)
                        if (isCustomSelected) {
                            IconButton(
                                onClick = {
                                    viewModel.saveCustomIconPath(context, null)
                                    viewModel.saveAppIconState(context, "Default")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove custom icon",
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textSecondaryColor
                            )
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
                }
            }
        }
    }
}
