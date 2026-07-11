/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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

    val context = LocalContext.current
    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (isDarkMode) Color(0xFF0B0B0C) else Color(0xFFF2F3F5)
    val cardColor = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val textPrimaryColor = if (isDarkMode) Color.White else Color(0xFF1C1C1E)
    val textSecondaryColor = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val selectedWallpaper = viewModel.browserWallpaperUri

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
                        .clickable { /* Future: Open Image Picker */ }
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

            // Change daily toggle
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

            // Standard Wallpapers Grid
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
                // Clear Wallpaper item
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
