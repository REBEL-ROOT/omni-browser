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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.Dp

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
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF161C24) else MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(top = topPadding)
    ) {
        // --- Header Bar ---
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
                                imageVector = Icons.Rounded.SettingsSuggest,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        text = "Power Engine",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "General & curated browser engine configurations",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter config flags (e.g. dns, https, webrtc...)", fontSize = 12.sp) },
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
            }
        }

        // --- Config Items Scroll List ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category 1: Privacy & Anti-Tracking
            ConfigCategoryGroup(
                categoryTitle = "Privacy & Anti-Tracking Hardening",
                searchQuery = searchQuery,
                items = listOf(
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
                        key = "privacy.resistFingerprinting",
                        title = "Fingerprint Resistance Shield",
                        description = "Spoofs screen dimensions, timezone, & system metrics to prevent canvas/browser fingerprinting.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isFingerprintProtection,
                            onCheckedChange = {
                                viewModel.saveFingerprintProtection(context, it)
                                Toast.makeText(context, "omni:config: Fingerprint shield ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    ),
                    ConfigItemData(
                        key = "privacy.clearOnShutdown.cookies",
                        title = "Clear Cookies & Cache on Shutdown",
                        description = "Automatically purges session state & cookies whenever Omni Browser exits.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isClearCookiesOnShutdown,
                            onCheckedChange = {
                                viewModel.saveClearCookiesOnShutdown(context, it)
                                Toast.makeText(context, "omni:config: Clear on shutdown ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                )
            )

            // Category 2: Security & Protocol Shielding
            ConfigCategoryGroup(
                categoryTitle = "Security & Protocol Shielding",
                searchQuery = searchQuery,
                items = listOf(
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
                        key = "media.peerconnection.enabled",
                        title = "WebRTC Protection (Prevent IP Leaks)",
                        description = "Disables WebRTC STUN/TURN peer connections to prevent real-IP leaks through proxies.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isDisableWebrtc,
                            onCheckedChange = {
                                viewModel.saveDisableWebrtc(context, it)
                                Toast.makeText(context, "omni:config: WebRTC Block ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    ),
                    ConfigItemData(
                        key = "network.quic.enabled",
                        title = "QUIC / HTTP3 Protocol Shield",
                        description = "Blocks UDP QUIC traffic to enforce TCP proxy routing & avoid DNS bypass leaks.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isBlockQuic,
                            onCheckedChange = {
                                viewModel.saveBlockQuic(context, it)
                                Toast.makeText(context, "omni:config: QUIC Shield ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    ),
                    ConfigItemData(
                        key = "browser.safebrowsing.enabled",
                        title = "Safe Browsing Level",
                        description = "Real-time threat protection against phishing, deceptive sites, & malicious downloads.",
                        control = ConfigControl.ChoiceControl(
                            selectedOption = when (viewModel.safeBrowsingLevel) {
                                0 -> "Off"
                                2 -> "Strict"
                                else -> "Standard"
                            },
                            options = listOf("Off" to "Disabled", "Standard" to "Standard Protection", "Strict" to "Strict Shield"),
                            onSelect = { option ->
                                val level = when (option) {
                                    "Off" -> 0
                                    "Strict" -> 2
                                    else -> 1
                                }
                                viewModel.saveSafeBrowsingLevel(context, level)
                                Toast.makeText(context, "omni:config: Safe browsing set to $option", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                )
            )

            // Category 3: High Refresh Rate & GPU Acceleration (Issue #59 Engine Flags)
            ConfigCategoryGroup(
                categoryTitle = "High Refresh Rate (120Hz) & WebRender Acceleration",
                searchQuery = searchQuery,
                items = listOf(
                    ConfigItemData(
                        key = "gfx.webrender.all",
                        title = "WebRender GPU Hardware Acceleration",
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
                        description = "Forces GPU hardware layer compositor acceleration on Android phone GPUs.",
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
                    )
                )
            )

            // Category 4: Performance & Optimization
            ConfigCategoryGroup(
                categoryTitle = "Engine Performance & Optimization",
                searchQuery = searchQuery,
                items = listOf(
                    ConfigItemData(
                        key = "network.dns.disablePrefetch",
                        title = "DNS & Link Preloading",
                        description = "Pre-resolves DNS & preloads next pages for faster load times.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.preloadPages > 0,
                            onCheckedChange = {
                                viewModel.savePreloadPages(context, if (it) 1 else 0)
                                Toast.makeText(context, "omni:config: Preload ${if (it) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
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
            )

            // Category 5: Network & DNS-over-HTTPS
            ConfigCategoryGroup(
                categoryTitle = "Advanced Network & DNS-over-HTTPS (DoH)",
                searchQuery = searchQuery,
                items = listOf(
                    ConfigItemData(
                        key = "network.trr.mode",
                        title = "DNS-over-HTTPS (DoH)",
                        description = "Encrypts DNS lookups over HTTPS to prevent ISP interception & DNS spoofing.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isDohEnabled,
                            onCheckedChange = {
                                viewModel.saveDohEnabled(context, it)
                                Toast.makeText(context, "omni:config: DoH ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    ),
                    ConfigItemData(
                        key = "network.trr.uri",
                        title = "DoH Resolver Preset",
                        description = "Select secure encrypted DNS provider endpoint.",
                        control = ConfigControl.ChoiceControl(
                            selectedOption = when {
                                viewModel.dohUri.contains("cloudflare") || viewModel.dohUri.contains("1.1.1.1") -> "Cloudflare"
                                viewModel.dohUri.contains("quad9") -> "Quad9"
                                viewModel.dohUri.contains("nextdns") -> "NextDNS"
                                else -> "Cloudflare"
                            },
                            options = listOf(
                                "Cloudflare" to "Cloudflare (1.1.1.1)",
                                "Quad9" to "Quad9 (9.9.9.9)",
                                "NextDNS" to "NextDNS Secure"
                            ),
                            onSelect = { option ->
                                val uri = when (option) {
                                    "Quad9" -> "https://dns.quad9.net/dns-query"
                                    "NextDNS" -> "https://dns.nextdns.io"
                                    else -> "https://cloudflare-dns.com/dns-query"
                                }
                                viewModel.saveDohUri(context, uri)
                                Toast.makeText(context, "omni:config: DoH provider set to $option", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                )
            )

            // Category 6: Identity & Viewport Engine
            ConfigCategoryGroup(
                categoryTitle = "Identity & Session Engine",
                searchQuery = searchQuery,
                items = listOf(
                    ConfigItemData(
                        key = "general.useragent.randomize",
                        title = "Session User-Agent Randomization",
                        description = "Randomizes User-Agent header strings periodically across browsing sessions.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isRandomizeUa,
                            onCheckedChange = {
                                viewModel.saveRandomizeUa(context, it)
                                Toast.makeText(context, "omni:config: UA Randomization ${if (it) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    ),
                    ConfigItemData(
                        key = "layout.css.devPixelsPerPx",
                        title = "Force Desktop Viewport Density",
                        description = "Forces desktop resolution layout mode for all sites by default.",
                        control = ConfigControl.SwitchControl(
                            checked = viewModel.isDesktopMode,
                            onCheckedChange = {
                                viewModel.toggleDesktopMode(context)
                                Toast.makeText(context, "omni:config: Desktop Viewport ${if (viewModel.isDesktopMode) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                )
            )
        }
    }
}

@Composable
private fun ConfigCategoryGroup(
    categoryTitle: String,
    searchQuery: String,
    items: List<ConfigItemData>
) {
    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.key.contains(searchQuery, ignoreCase = true) ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredItems.isNotEmpty()) {
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
                filteredItems.forEachIndexed { index, item ->
                    ConfigItemRow(item = item)
                    if (index < filteredItems.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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
                    fontSize = 15.sp,
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
                is ConfigControl.ChoiceControl -> {
                    // Render option selector chips
                }
            }
        }
        Text(
            text = item.description,
            fontSize = 12.sp,
            color = textSecondary,
            lineHeight = 16.sp
        )

        // If ChoiceControl, render options chips
        if (item.control is ConfigControl.ChoiceControl) {
            val control = item.control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                control.options.forEach { (optionKey, optionLabel) ->
                    val isSelected = (optionKey == control.selectedOption)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clickable { control.onSelect(optionKey) }
                    ) {
                        Text(
                            text = optionLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
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
