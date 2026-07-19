/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rebelroot.omni.browser.BrowserViewModel

val PRESET_WALLPAPERS = listOf(
    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=600&auto=format&fit=crop", // Abstract Liquid
    "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=600&auto=format&fit=crop", // Dark Geometry
    "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?q=80&w=600&auto=format&fit=crop", // Gradient
    "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?q=80&w=600&auto=format&fit=crop", // Abstract Fluid
    "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?q=80&w=600&auto=format&fit=crop", // Color Smoke
    "https://images.unsplash.com/photo-1604871000636-074fa5117945?q=80&w=600&auto=format&fit=crop", // Fluid Art
    "https://images.unsplash.com/photo-1563089145-599997674d42?q=80&w=600&auto=format&fit=crop", // Neon
    "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=600&auto=format&fit=crop"  // Abstract Paint
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var editingWallpaperUri by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
            } catch (e: Exception) {
                // Fallback for non-document providers
            }
            editingWallpaperUri = it.toString()
        }
    }

    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedWallpaper = viewModel.browserWallpaperUri

    if (editingWallpaperUri != null) {
        WallpaperEditorView(
            uri = editingWallpaperUri!!,
            viewModel = viewModel,
            onDismiss = { editingWallpaperUri = null },
            onApply = { cropUri, scale, offsetX, offsetY, dim, blur ->
                viewModel.saveBrowserWallpaperUri(context, cropUri)
                viewModel.saveWallpaperCrop(context, scale, offsetX, offsetY)
                viewModel.saveWallpaperDim(context, dim)
                viewModel.saveWallpaperBlur(context, blur)
                editingWallpaperUri = null
            }
        )
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallpapers", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
        ) {
            // Header Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = cardColor,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .clickable { launcher.launch("image/*") }
                ) {
                    Text(
                        text = "My photos",
                        color = textPrimaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                
                
                Surface(
                    color = cardColor,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .clickable { /* Future: Open Favorites */ }
                ) {
                    Text(
                        text = "Favorites",
                        color = textPrimaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // Toggle for daily auto-rotation
            Surface(
                color = cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Change wallpaper daily", color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = viewModel.changeWallpaperDaily,
                        onCheckedChange = { viewModel.saveChangeWallpaperDaily(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
            }

            // Wallpaper custom dim, blur, and blur casing settings (visible only when wallpaper is active)
            if (selectedWallpaper != null) {
                Surface(
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Wallpaper Customization",
                            color = textPrimaryColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 1. Wallpaper Blur Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Blur effect", color = textPrimaryColor, fontSize = 14.sp)
                                Text("${viewModel.wallpaperBlur.toInt()} dp", color = textSecondaryColor, fontSize = 14.sp)
                            }
                            Slider(
                                value = viewModel.wallpaperBlur,
                                onValueChange = { viewModel.saveWallpaperBlur(context, it) },
                                valueRange = 0f..25f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.5f))

                        // 2. Wallpaper Dim (Opacity Scrim) Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val currentDimVal = if (viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else (if (isDarkMode) 0.5f else 0.65f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dim opacity (overlay)", color = textPrimaryColor, fontSize = 14.sp)
                                Text("${(currentDimVal * 100).toInt()}%", color = textSecondaryColor, fontSize = 14.sp)
                            }
                            Slider(
                                value = currentDimVal,
                                onValueChange = { viewModel.saveWallpaperDim(context, it) },
                                valueRange = 0f..0.9f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                    }
                }
            }

            // Grid options
            Text(
                "Standard",
                color = textPrimaryColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Option to disable wallpaper
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardColor)
                            .border(
                                2.dp,
                                if (selectedWallpaper == null) accentColor else cardBorderColor,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.saveBrowserWallpaperUri(context, null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Wallpaper", color = textSecondaryColor)
                        if (selectedWallpaper == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Active custom wallpaper option
                if (selectedWallpaper != null && !PRESET_WALLPAPERS.contains(selectedWallpaper)) {
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                                .border(
                                    2.dp,
                                    accentColor,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { editingWallpaperUri = selectedWallpaper }
                        ) {
                            AsyncImage(
                                model = selectedWallpaper,
                                contentDescription = "Custom Wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Preset Wallpapers
                items(PRESET_WALLPAPERS) { uri ->
                    val isSelected = selectedWallpaper == uri
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                            .border(
                                2.dp,
                                if (isSelected) accentColor else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.saveBrowserWallpaperUri(context, uri) }
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Wallpaper Option",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditorView(
    uri: String,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onApply: (String, Float, Float, Float, Float, Float) -> Unit
) {
    val isDarkMode = viewModel.isDarkThemeEnabled
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    val isEditingCurrent = uri == viewModel.browserWallpaperUri
    var tempScale by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperScale else 1.0f) }
    var tempOffsetX by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperOffsetX else 0f) }
    var tempOffsetY by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperOffsetY else 0f) }
    var tempDim by remember { mutableStateOf(if (isEditingCurrent && viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else 0.4f) }
    var tempBlur by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperBlur else 0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Wallpaper", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = textPrimaryColor)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onApply(uri, tempScale, tempOffsetX, tempOffsetY, tempDim, tempBlur) }
                    ) {
                        Text("Apply", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(9f / 16f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(4.dp, cardBorderColor),
                shadowElevation = 12.dp,
                color = Color.Black
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = tempScale
                                scaleY = tempScale
                                translationX = tempOffsetX
                                translationY = tempOffsetY
                            }
                            .then(
                                if (tempBlur > 0f) {
                                    Modifier.blur(tempBlur.dp).graphicsLayer()
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = tempDim))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    tempOffsetX += dragAmount.x
                                    tempOffsetY += dragAmount.y
                                }
                            }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.sweepGradient(
                                            listOf(Color(0xFF7B2FBE), Color(0xFF4A90E2), Color(0xFF7B2FBE))
                                        )
                                    )
                            )
                            Text(
                                text = "Omni Browser",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Search or type URL",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(
                                "Google" to Color(0xFF4285F4),
                                "YouTube" to Color(0xFFFF0000),
                                "Wikipedia" to Color(0xFF8E9AA8),
                                "RebelRoot" to Color(0xFF7B2FBE)
                            ).forEach { (name, color) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                    }
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Drag to reposition",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Surface(
                color = cardColor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Zoom / Scale", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(String.format("%.1fx", tempScale), color = textSecondaryColor, fontSize = 14.sp)
                        }
                        Slider(
                            value = tempScale,
                            onValueChange = { tempScale = it },
                            valueRange = 1.0f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Blur amount", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${tempBlur.toInt()} dp", color = textSecondaryColor, fontSize = 14.sp)
                        }
                        Slider(
                            value = tempBlur,
                            onValueChange = { tempBlur = it },
                            valueRange = 0f..25f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dim opacity", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${(tempDim * 100).toInt()}%", color = textSecondaryColor, fontSize = 14.sp)
                        }
                        Slider(
                            value = tempDim,
                            onValueChange = { tempDim = it },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }

                    Button(
                        onClick = {
                            tempScale = 1.0f
                            tempOffsetX = 0f
                            tempOffsetY = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Reset Crop", tint = textPrimaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Position", color = textPrimaryColor)
                    }
                }
            }
        }
    }
}
