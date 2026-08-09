/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.settings

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.useragent.UserAgentPreset
import com.rebelroot.omni.browser.useragent.UserAgentSiteRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgentSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val userAgentManager = viewModel.userAgentManager
    val globalPreset by userAgentManager.globalPreset.collectAsState()
    val globalCustomUa by userAgentManager.globalCustomUa.collectAsState()
    val siteRules by userAgentManager.siteRules.collectAsState()

    var showCustomUaDialog by remember { mutableStateOf(false) }
    var tempCustomUa by remember { mutableStateOf("") }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    val groupedRules = remember(siteRules) {
        siteRules.groupBy { rule ->
            val d = rule.domain.trim().lowercase().removePrefix("www.")
            if (d.isBlank() || d == "about:blank") "All Sites (*)" else d
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Agent Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddRuleDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Site Rule", tint = accentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Global User Agent Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, cardBorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Global User Agent", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimaryColor)
                            Text("Default User Agent for all websites", fontSize = 12.sp, color = textSecondaryColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    UserAgentPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (preset == UserAgentPreset.CUSTOM) {
                                        tempCustomUa = globalCustomUa
                                        showCustomUaDialog = true
                                    } else {
                                        userAgentManager.setGlobalPreset(preset)
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (globalPreset == preset),
                                onClick = {
                                    if (preset == UserAgentPreset.CUSTOM) {
                                        tempCustomUa = globalCustomUa
                                        showCustomUaDialog = true
                                    } else {
                                        userAgentManager.setGlobalPreset(preset)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor)
                                if (preset == UserAgentPreset.CUSTOM && globalCustomUa.isNotBlank()) {
                                    Text(globalCustomUa, fontSize = 11.sp, color = accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else if (preset.userAgentString.isNotBlank()) {
                                    Text(preset.userAgentString, fontSize = 11.sp, color = textSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Site-Specific Overrides Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Site-Specific Overrides", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimaryColor)
                    TextButton(onClick = { showAddRuleDialog = true }) {
                        Text("+ Add Rule", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }

                if (groupedRules.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = BorderStroke(1.dp, cardBorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Site-Specific User Agents", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textPrimaryColor)
                            Text("Add site overrides for websites that require specific user agents", fontSize = 12.sp, color = textSecondaryColor)
                        }
                    }
                } else {
                    groupedRules.forEach { (domain, rules) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = BorderStroke(1.dp, cardBorderColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (domain.contains("*")) Icons.Rounded.Public else Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(domain, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                    }
                                    IconButton(
                                        onClick = {
                                            userAgentManager.removeSiteRulesForDomain(domain)
                                            Toast.makeText(context, "Cleared User Agent rules for $domain", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Clear site rules", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = cardBorderColor)

                                rules.forEach { rule ->
                                    val preset = UserAgentPreset.fromId(rule.presetId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor)
                                            Text(rule.effectiveUserAgent, fontSize = 10.sp, color = textSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { userAgentManager.toggleSiteRule(rule.id, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Custom Global User Agent String
    if (showCustomUaDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUaDialog = false },
            title = { Text("Custom User Agent String") },
            text = {
                OutlinedTextField(
                    value = tempCustomUa,
                    onValueChange = { tempCustomUa = it },
                    label = { Text("User Agent String") },
                    placeholder = { Text("e.g. Mozilla/5.0 ...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userAgentManager.setGlobalPreset(UserAgentPreset.CUSTOM, tempCustomUa.trim())
                        showCustomUaDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add Site Rule
    if (showAddRuleDialog) {
        var inputDomain by remember { mutableStateOf("") }
        var selectedPreset by remember { mutableStateOf(UserAgentPreset.CHROME_DESKTOP) }
        var customInputUa by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Add Site User Agent Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputDomain,
                        onValueChange = { inputDomain = it },
                        label = { Text("Domain (e.g. wikipedia.org)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select User Agent:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 200.dp)) {
                        UserAgentPreset.entries.filter { it != UserAgentPreset.DEFAULT }.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPreset = p }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (selectedPreset == p), onClick = { selectedPreset = p })
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(p.displayName, fontSize = 13.sp)
                            }
                        }
                    }

                    if (selectedPreset == UserAgentPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customInputUa,
                            onValueChange = { customInputUa = it },
                            label = { Text("Custom UA String") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputDomain.isNotBlank()) {
                            userAgentManager.addOrUpdateSiteRule(inputDomain, selectedPreset, customInputUa)
                            showAddRuleDialog = false
                            Toast.makeText(context, "Site User Agent rule saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
