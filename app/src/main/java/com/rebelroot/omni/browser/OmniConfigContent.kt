/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.flags.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniConfigContent(
    viewModel: BrowserViewModel,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 16.dp,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FlagCategory.ALL) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF161C24) else MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // Count modified flags
    val modifiedFlagsCount = remember(viewModel.engineFlagsState.toMap()) {
        OmniFlagsRegistry.ALL_FLAGS.count { flag ->
            val current = viewModel.engineFlagsState[flag.id] ?: flag.defaultEnabled
            current != flag.defaultEnabled
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(top = topPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header Bar ──────────────────────────────────────────────────────────
            Surface(
                color = cardColor,
                border = BorderStroke(0.5.dp, cardBorderColor),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "omni:config",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Gecko Engine Flags",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Chrome & Brave style flags for the Firefox engine",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        // Action Buttons: Reset All & Add Custom Pref
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showResetAllDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(13.dp), tint = textSecondary)
                                Spacer(Modifier.width(3.dp))
                                Text("Reset All", fontSize = 11.sp, color = textSecondary)
                            }
                            FilledTonalButton(
                                onClick = { showAddCustomDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                                Spacer(Modifier.width(3.dp))
                                Text("Add Pref", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search flags by name, tag (e.g. #parallel), or pref...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = bgColor,
                            unfocusedContainerColor = bgColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlagCategory.values().forEach { category ->
                            val isSelected = (category == selectedCategory)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, if (isSelected) accentColor else cardBorderColor),
                                modifier = Modifier.clickable { selectedCategory = category }
                            ) {
                                Text(
                                    text = category.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Scrollable Config & Flags List ───────────────────────────────────────
            val listBottomPad = if (viewModel.privacyRestartNeeded) bottomPadding + 88.dp else bottomPadding
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = listBottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Filter curated flags
                val filteredFlags = remember(searchQuery, selectedCategory, viewModel.engineFlagsState.toMap()) {
                    OmniFlagsRegistry.ALL_FLAGS.filter { flag ->
                        val matchesCategory = (selectedCategory == FlagCategory.ALL) || (flag.category == selectedCategory)
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            flag.title.contains(searchQuery, ignoreCase = true) ||
                            flag.description.contains(searchQuery, ignoreCase = true) ||
                            flag.tag.contains(searchQuery, ignoreCase = true) ||
                            flag.origin.displayName.contains(searchQuery, ignoreCase = true) ||
                            flag.enginePrefs.keys.any { it.contains(searchQuery, ignoreCase = true) }
                        }
                        matchesCategory && matchesSearch
                    }
                }

                // Group and display curated flags if any match
                if (filteredFlags.isNotEmpty()) {
                    val groupedFlags = filteredFlags.groupBy { it.category }
                    groupedFlags.forEach { (cat, flagsInCat) ->
                        FlagCategorySection(
                            categoryTitle = cat.displayName,
                            flags = flagsInCat,
                            viewModel = viewModel
                        )
                    }
                }

                // ── Legacy Core Engine Hardening & Rendering ───────────────────────────
                if (selectedCategory == FlagCategory.ALL || selectedCategory == FlagCategory.PERFORMANCE || selectedCategory == FlagCategory.SECURITY) {
                    val showLegacyCategory = searchQuery.isBlank() ||
                        "WebRender GPU Compositor 120Hz Safe Browsing HTTPS Do Not Track Zoom UA".contains(searchQuery, ignoreCase = true)

                    if (showLegacyCategory) {
                        LegacyEngineSection(
                            viewModel = viewModel,
                            searchQuery = searchQuery
                        )
                    }
                }

                // ── Custom Preferences (about:config power user mode) ──────────────────
                if (selectedCategory == FlagCategory.ALL || selectedCategory == FlagCategory.CUSTOM) {
                    CustomPrefsSection(
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        onAddClick = { showAddCustomDialog = true }
                    )
                }

                if (filteredFlags.isEmpty() && selectedCategory != FlagCategory.CUSTOM && selectedCategory != FlagCategory.ALL) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No flags match the current filter.",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ── Sticky Relaunch Required Banner ──────────────────────────────────────────
        AnimatedVisibility(
            visible = viewModel.privacyRestartNeeded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = bottomPadding)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (viewModel.isDarkThemeEnabled) Color(0xFF1E2630) else Color(0xFF2B323B),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Relaunch Required",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9500)
                        )
                        Text(
                            text = "Flags will take effect the next time you restart Omni.",
                            fontSize = 11.sp,
                            color = Color(0xFFD0D7DE),
                            lineHeight = 14.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.restartApp(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Relaunch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    IconButton(
                        onClick = { viewModel.privacyRestartNeeded = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Dismiss", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // ── Reset All Dialog ────────────────────────────────────────────────────────────
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Engine Flags?") },
            text = { Text("This will restore all Chrome and Brave type flags to their default configurations. Custom user preferences will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllEngineFlags(context)
                        showResetAllDialog = false
                        Toast.makeText(context, "All flags reset to default", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Add Custom Preference Dialog (about:config mode) ───────────────────────────
    if (showAddCustomDialog) {
        var prefKey by remember { mutableStateOf("") }
        var prefType by remember { mutableStateOf(PrefType.BOOLEAN) }
        var prefValue by remember { mutableStateOf("true") }

        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("Add Engine Preference") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Inject an arbitrary GeckoView/about:config preference directly into the engine.", fontSize = 12.sp, color = textSecondary)

                    OutlinedTextField(
                        value = prefKey,
                        onValueChange = { prefKey = it },
                        label = { Text("Preference Key (e.g. image.animation_mode)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrefType.values().forEach { t ->
                            val isSel = (t == prefType)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                modifier = Modifier.clickable {
                                    prefType = t
                                    if (t == PrefType.BOOLEAN && prefValue != "true" && prefValue != "false") {
                                        prefValue = "true"
                                    }
                                }
                            ) {
                                Text(
                                    text = t.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (prefType == PrefType.BOOLEAN) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Value: ", fontSize = 12.sp, color = textSecondary)
                            listOf("true", "false").forEach { v ->
                                val isV = (prefValue == v)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isV) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    modifier = Modifier.clickable { prefValue = v }
                                ) {
                                    Text(
                                        text = v,
                                        fontSize = 11.sp,
                                        fontWeight = if (isV) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isV) Color.White else textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = prefValue,
                            onValueChange = { prefValue = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedKey = prefKey.trim()
                        if (trimmedKey.isNotBlank()) {
                            viewModel.addCustomEnginePref(context, CustomPref(trimmedKey, prefType, prefValue.trim()))
                            showAddCustomDialog = false
                            Toast.makeText(context, "Added pref: $trimmedKey", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FlagCategorySection(
    categoryTitle: String,
    flags: List<OmniEngineFlag>,
    viewModel: BrowserViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = categoryTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            flags.forEachIndexed { index, flag ->
                FlagItemRow(flag = flag, viewModel = viewModel)
                if (index < flags.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun FlagItemRow(
    flag: OmniEngineFlag,
    viewModel: BrowserViewModel
) {
    val context = LocalContext.current
    val isEnabled = viewModel.engineFlagsState[flag.id] ?: flag.defaultEnabled
    val isModified = (isEnabled != flag.defaultEnabled)
    val accentColor = MaterialTheme.colorScheme.primary
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val originColor = when (flag.origin) {
        FlagOrigin.CHROME -> Color(0xFF1A73E8)
        FlagOrigin.BRAVE -> Color(0xFFFF5500)
        FlagOrigin.FIREFOX -> Color(0xFFFF7139)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = originColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = flag.origin.displayName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = originColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = flag.tag,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (isModified) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF9500).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Modified",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9500),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = flag.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isModified) {
                    IconButton(
                        onClick = {
                            viewModel.setEngineFlag(context, flag.id, flag.defaultEnabled)
                            Toast.makeText(context, "${flag.title} reset to default", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        viewModel.setEngineFlag(context, flag.id, it)
                        Toast.makeText(context, "omni:config: ${flag.title} ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                )
            }
        }

        Text(
            text = flag.description,
            fontSize = 12.sp,
            color = textSecondary,
            lineHeight = 16.sp
        )

        // Engine preference mapping preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            flag.enginePrefs.forEach { (k, v) ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "$k: $v",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPrefsSection(
    viewModel: BrowserViewModel,
    searchQuery: String,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val filteredCustom = remember(searchQuery, viewModel.customEnginePrefs.toList()) {
        if (searchQuery.isBlank()) viewModel.customEnginePrefs
        else viewModel.customEnginePrefs.filter {
            it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Custom Engine Preferences (about:config)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
            IconButton(onClick = onAddClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Add custom pref", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            if (filteredCustom.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom engine preferences added yet. Tap '+ Add Pref' to inject custom about:config flags.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                filteredCustom.forEachIndexed { index, pref ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = pref.type.displayName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = pref.key,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Value: ${pref.value}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.removeCustomEnginePref(context, pref.key)
                                Toast.makeText(context, "Removed pref: ${pref.key}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete pref",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (index < filteredCustom.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyEngineSection(
    viewModel: BrowserViewModel,
    searchQuery: String
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary

    val items = listOf(
        ConfigItemData(
            key = "gfx.webrender.all",
            title = "WebRender Hardware Acceleration",
            description = "Forces WebRender GPU hardware acceleration to eliminate stutter on heavy sites & enable 120Hz rendering.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.isWebRenderEnabled,
                onCheckedChange = {
                    viewModel.saveWebRenderEnabled(context, it)
                    Toast.makeText(context, "omni:config: WebRender ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        ConfigItemData(
            key = "layers.acceleration.force-enabled",
            title = "GPU Compositor Acceleration",
            description = "Forces GPU hardware layer compositor acceleration on Android GPUs.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.isGpuAccelerationEnabled,
                onCheckedChange = {
                    viewModel.saveGpuAccelerationEnabled(context, it)
                    Toast.makeText(context, "omni:config: GPU Compositor ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        ConfigItemData(
            key = "layout.frame_rate",
            title = "Force Maximum Refresh Rate (120Hz)",
            description = "Forces browser engine render loop to target maximum 120Hz refresh rate on high-refresh phone screens.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.isForceHighRefreshRate,
                onCheckedChange = {
                    viewModel.saveForceHighRefreshRate(context, it)
                    Toast.makeText(context, "omni:config: 120Hz Force ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        ConfigItemData(
            key = "dom.security.https_only_mode",
            title = "HTTPS-Only Mode",
            description = "Forces all web navigation over encrypted HTTPS; alerts before loading plaintext HTTP.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.httpsOnlyMode,
                onCheckedChange = {
                    viewModel.saveHttpsOnlyMode(context, it)
                    Toast.makeText(context, "omni:config: HTTPS-Only ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        ConfigItemData(
            key = "privacy.donottrackheader.enabled",
            title = "Do Not Track (DNT) Header",
            description = "Instructs web servers not to track your browsing activity.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.doNotTrack,
                onCheckedChange = {
                    viewModel.saveDoNotTrack(context, it)
                    Toast.makeText(context, "omni:config: DNT ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        ConfigItemData(
            key = "accessibility.force_zoom",
            title = "Force Enable Pinch-Zoom",
            description = "Overrides webpage meta user-scalable=no tags to allow pinch-zooming on any site.",
            control = ConfigControl.SwitchControl(
                checked = viewModel.accessibilityForceZoom,
                onCheckedChange = {
                    viewModel.saveAccessibilityForceZoom(context, it)
                    Toast.makeText(context, "omni:config: Force zoom ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                }
            )
        )
    )

    val filtered = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.key.contains(searchQuery, ignoreCase = true) ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filtered.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Core Engine Acceleration & Display Tuning",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(start = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            ) {
                filtered.forEachIndexed { index, item ->
                    ConfigItemRow(item = item)
                    if (index < filtered.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigItemRow(item: ConfigItemData) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.key,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
            }
            when (val control = item.control) {
                is ConfigControl.SwitchControl -> {
                    Switch(
                        checked = control.checked,
                        onCheckedChange = control.onCheckedChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
                is ConfigControl.ChoiceControl -> {}
            }
        }
        Text(
            text = item.description,
            fontSize = 12.sp,
            color = textSecondary,
            lineHeight = 16.sp
        )
    }
}

data class ConfigItemData(
    val key: String,
    val title: String,
    val description: String,
    val control: ConfigControl
)

sealed class ConfigControl {
    data class SwitchControl(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : ConfigControl()
    data class ChoiceControl(val selectedOption: String, val options: List<Pair<String, String>>, val onSelect: (String) -> Unit) : ConfigControl()
}
