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
            var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    tempImageUri = uri
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Icon", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Default Preset Icon Card
                    Card(
                        onClick = {
                            viewModel.saveAppIconState(context, "Default")
                            viewModel.saveCustomIconPath(context, null) // Clear custom icon
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (viewModel.appIconState == "Default" && viewModel.customIconPath == null) accentColor else cardBorderColor
                        ),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    tint = Color(0xFF2C2C2E),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text("Default", color = textPrimaryColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }

                    // Dark Preset Icon Card
                    Card(
                        onClick = {
                            viewModel.saveAppIconState(context, "Dark")
                            viewModel.saveCustomIconPath(context, null) // Clear custom icon
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (viewModel.appIconState == "Dark" && viewModel.customIconPath == null) accentColor else cardBorderColor
                        ),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1C1E))
                                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text("Dark", color = textPrimaryColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }

                    // Custom Gallery Icon Card
                    Card(
                        onClick = {
                            galleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (viewModel.customIconPath != null) accentColor else cardBorderColor
                        ),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (viewModel.customIconPath != null) {
                                coil.compose.AsyncImage(
                                    model = File(viewModel.customIconPath!!),
                                    contentDescription = "Custom Icon Preview",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddAPhoto,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(if (viewModel.customIconPath != null) "Customized" else "Choose Custom", color = textPrimaryColor, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Real-time crop and zoom editor dialog overlay
            if (tempImageUri != null) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val coroutineScope = rememberCoroutineScope()
                
                AlertDialog(
                    onDismissRequest = { tempImageUri = null },
                    title = { Text("Crop & Position Icon", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Pinch to zoom, drag to position. The icon will crop to the circle boundary.",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondaryColor
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            offset = Offset(
                                                x = offset.x + pan.x,
                                                y = offset.y + pan.y
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = tempImageUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
                                    contentScale = ContentScale.Fit
                                )
                                
                                // Circular preview highlight ring
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .border(2.dp, Color.White, CircleShape)
                                        .background(Color.Transparent)
                                )
                            }
                            
                            Button(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cardBorderColor)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = textPrimaryColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset Position", color = textPrimaryColor)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val uriToCrop = tempImageUri
                                if (uriToCrop != null) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(uriToCrop)
                                            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                            inputStream?.close()
                                            if (originalBitmap != null) {
                                                val targetSize = 512
                                                val croppedBitmap = android.graphics.Bitmap.createBitmap(targetSize, targetSize, android.graphics.Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(croppedBitmap)
                                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                                                
                                                val matrix = android.graphics.Matrix()
                                                val initScale = targetSize.toFloat() / Math.min(originalBitmap.width, originalBitmap.height)
                                                matrix.postScale(initScale, initScale)
                                                
                                                val scaledWidth = originalBitmap.width * initScale
                                                val scaledHeight = originalBitmap.height * initScale
                                                
                                                val dx = (targetSize - scaledWidth) / 2f
                                                val dy = (targetSize - scaledHeight) / 2f
                                                matrix.postTranslate(dx, dy)
                                                
                                                val displayRatio = targetSize.toFloat() / 220f
                                                matrix.postScale(scale, scale, targetSize / 2f, targetSize / 2f)
                                                matrix.postTranslate(offset.x * displayRatio, offset.y * displayRatio)
                                                
                                                canvas.drawBitmap(originalBitmap, matrix, paint)
                                                
                                                val outFile = File(context.filesDir, "custom_app_icon.png")
                                                val outStream = java.io.FileOutputStream(outFile)
                                                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                                                outStream.flush()
                                                outStream.close()
                                                
                                                withContext(Dispatchers.Main) {
                                                    viewModel.saveCustomIconPath(context, outFile.absolutePath)
                                                    viewModel.saveAppIconState(context, "Custom")
                                                    tempImageUri = null
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("CropIcon", "Cropping failed", e)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Apply Icon")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { tempImageUri = null }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = cardColor
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
