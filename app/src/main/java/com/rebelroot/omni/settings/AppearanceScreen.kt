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
import com.rebelroot.omni.browser.BrowserViewModel

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
                    // System Default (For now just ties to Dark Mode on/off for simplicity or can be a separate state)
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
            
            // App Icon selection and dialog
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Icon", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .clickable { showIconDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = "App Icon", tint = accentColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(viewModel.appIconState, color = textPrimaryColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Change Icon", tint = textSecondaryColor)
                }
            }

            if (showIconDialog) {
                AlertDialog(
                    onDismissRequest = { showIconDialog = false },
                    title = { Text("Choose App Icon", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Default", "Dark", "Blue", "Gold").forEach { state ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.saveAppIconState(context, state)
                                            showIconDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = viewModel.appIconState == state,
                                        onClick = {
                                            viewModel.saveAppIconState(context, state)
                                            showIconDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(state, color = textPrimaryColor, fontSize = 16.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showIconDialog = false }) {
                            Text("Cancel", color = accentColor)
                        }
                    },
                    containerColor = cardColor
                )
            }

            // Address Bar
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
            
            // Navigation Visibility Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Navigation Visibility", color = textPrimaryColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
        }
    }
}
