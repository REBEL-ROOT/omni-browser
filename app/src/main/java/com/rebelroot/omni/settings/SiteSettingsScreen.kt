/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.SitePermission
import androidx.compose.ui.platform.LocalContext

enum class SiteSettingsSubView {
    HOME,
    ALL_SITES,
    PERMISSION_DETAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    var currentSubView by remember { mutableStateOf(SiteSettingsSubView.HOME) }
    var selectedPermissionType by remember { mutableStateOf("location") } // location, camera, microphone, notifications, javascript, autoplay, popups
    var searchQuery by remember { mutableStateOf("") }
    
    // Site Details Dialog State
    var showSiteDetailsDialog by remember { mutableStateOf(false) }
    var selectedSiteHost by remember { mutableStateOf("") }

    val accentColor = MaterialTheme.colorScheme.primary
    val isDark = viewModel.isDarkThemeEnabled
    val isAmoled = viewModel.isAmoledMode
    
    val backgroundColor = when {
        isAmoled -> Color.Black
        isDark -> Color(0xFF070A0F)
        else -> MaterialTheme.colorScheme.background
    }
    
    val cardColor = when {
        isAmoled -> Color(0xFF000000)
        isDark -> Color(0xFF0F1B26)
        else -> MaterialTheme.colorScheme.surface
    }

    val textPrimaryColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = if (isDark) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (isDark) Color(0xFF23374A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.4f)
    val cardBorder = if (isDark) BorderStroke(0.5.dp, Color(0xFF1E2D3F)) else BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentSubView) {
                            SiteSettingsSubView.HOME -> "Site Settings"
                            SiteSettingsSubView.ALL_SITES -> "All Sites"
                            SiteSettingsSubView.PERMISSION_DETAIL -> when (selectedPermissionType) {
                                "location" -> "Location Permission"
                                "camera" -> "Camera Permission"
                                "microphone" -> "Microphone Permission"
                                "notifications" -> "Notifications Permission"
                                "javascript" -> "JavaScript"
                                "autoplay" -> "Autoplay"
                                "popups" -> "Pop-ups & Redirects"
                                else -> "Permission"
                            }
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentSubView == SiteSettingsSubView.HOME) {
                                onNavigateBack()
                            } else {
                                currentSubView = SiteSettingsSubView.HOME
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentSubView,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "SiteSettingsTransition",
            modifier = Modifier.padding(innerPadding)
        ) { subView ->
            when (subView) {
                SiteSettingsSubView.HOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: All Sites
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardColor,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentSubView = SiteSettingsSubView.ALL_SITES }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Web, null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("All Sites", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${viewModel.sitePermissions.size} site${if (viewModel.sitePermissions.size != 1) "s" else ""} with custom settings",
                                        color = textSecondaryColor,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = textSecondaryColor)
                            }
                        }

                        // Section 2: Permissions Header
                        Text(
                            "Default Permissions",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Section 3: Default Permissions List
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardColor,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                val permissionsList = listOf(
                                    Triple("location", "Location", Icons.Rounded.LocationOn),
                                    Triple("camera", "Camera", Icons.Rounded.CameraAlt),
                                    Triple("microphone", "Microphone", Icons.Rounded.Mic),
                                    Triple("notifications", "Notifications", Icons.Rounded.Notifications),
                                    Triple("javascript", "JavaScript", Icons.Rounded.Code),
                                    Triple("autoplay", "Autoplay", Icons.Rounded.PlayCircle),
                                    Triple("popups", "Pop-ups and redirects", Icons.Rounded.OpenInNew)
                                )

                                permissionsList.forEachIndexed { index, (type, label, icon) ->
                                    val subtitle = when (type) {
                                        "location" -> "Default: ${viewModel.defaultGeolocation.replaceFirstChar { it.uppercase() }}"
                                        "camera" -> "Default: ${viewModel.defaultCamera.replaceFirstChar { it.uppercase() }}"
                                        "microphone" -> "Default: ${viewModel.defaultMicrophone.replaceFirstChar { it.uppercase() }}"
                                        "notifications" -> "Default: ${viewModel.defaultNotifications.replaceFirstChar { it.uppercase() }}"
                                        "javascript" -> if (viewModel.defaultJavascriptAllowed) "Allowed" else "Blocked"
                                        "autoplay" -> if (viewModel.defaultAutoplayAllowed) "Allowed" else "Blocked"
                                        "popups" -> if (viewModel.isPopupBlockerEnabled) "Blocked (Recommended)" else "Allowed"
                                        else -> ""
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPermissionType = type
                                                currentSubView = SiteSettingsSubView.PERMISSION_DETAIL
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(accentColor.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text(subtitle, color = textSecondaryColor, fontSize = 11.sp)
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = textSecondaryColor, modifier = Modifier.size(20.dp))
                                    }

                                    if (index < permissionsList.lastIndex) {
                                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                SiteSettingsSubView.ALL_SITES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search sites...", fontSize = 13.sp) },
                            prefix = { Icon(Icons.Rounded.Search, null, tint = textSecondaryColor, modifier = Modifier.padding(end = 4.dp).size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = dividerColor,
                                focusedTextColor = textPrimaryColor,
                                unfocusedTextColor = textPrimaryColor
                            )
                        )

                        // Clear all header
                        if (viewModel.sitePermissions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Custom Site Overrides",
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                TextButton(
                                    onClick = { viewModel.clearAllSitePermissions() }
                                ) {
                                    Text("Clear All", color = Color(0xFFFF3B5C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        val filteredSites = viewModel.sitePermissions.filter {
                            it.host.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredSites.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.WebAssetOff, null, tint = textSecondaryColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                    Text("No sites with custom permissions", color = textSecondaryColor, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = cardColor,
                                border = cardBorder,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                LazyColumn {
                                    items(filteredSites) { site ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedSiteHost = site.host
                                                    showSiteDetailsDialog = true
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isDark) Color(0xFF1E2D3F) else Color(0xFFF1F5F9)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                        .data("https://www.google.com/s2/favicons?domain=${site.host}&sz=64")
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(site.host, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                val overriddenList = mutableListOf<String>()
                                                if (site.location != "ask") overriddenList.add("Location")
                                                if (site.camera != "ask") overriddenList.add("Camera")
                                                if (site.microphone != "ask") overriddenList.add("Microphone")
                                                if (site.notifications != "ask") overriddenList.add("Notifications")
                                                if (site.javascript != "allow") overriddenList.add("JavaScript Blocked")
                                                if (site.autoplay != "allow") overriddenList.add("Autoplay Blocked")
                                                
                                                val overridesStr = if (overriddenList.isEmpty()) "No overrides" else overriddenList.joinToString(", ")
                                                Text(overridesStr, color = textSecondaryColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = textSecondaryColor, modifier = Modifier.size(20.dp))
                                        }
                                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                SiteSettingsSubView.PERMISSION_DETAIL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val isBinary = selectedPermissionType == "javascript" || selectedPermissionType == "autoplay" || selectedPermissionType == "popups"
                        
                        // Option Descriptions
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardColor,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val desc = when (selectedPermissionType) {
                                    "location" -> "Control whether websites can ask for or access your physical location coordinates."
                                    "camera" -> "Control whether websites can access your front or back camera for photo/video."
                                    "microphone" -> "Control whether websites can record audio or use your device microphone."
                                    "notifications" -> "Control whether websites can send push notifications to your device."
                                    "javascript" -> "Choose whether websites are allowed to execute JavaScript code. Blocking JS improves security but breaks many websites."
                                    "autoplay" -> "Choose whether audio or video can play automatically when you visit a webpage."
                                    "popups" -> "Block website pop-up windows and redirects from opening without clicking."
                                    else -> ""
                                }
                                Text("About this feature", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(desc, color = textSecondaryColor, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }

                        // Setting Selection Selector
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardColor,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                if (isBinary) {
                                    // Binary options (JavaScript, Autoplay, Popups)
                                    val (allowedText, blockedText) = when (selectedPermissionType) {
                                        "javascript" -> "Allowed (Recommended)" to "Blocked"
                                        "autoplay" -> "Allowed" to "Blocked"
                                        "popups" -> "Allowed (Not Recommended)" to "Blocked (Recommended)"
                                        else -> "" to ""
                                    }

                                    val isAllowed = when (selectedPermissionType) {
                                        "javascript" -> viewModel.defaultJavascriptAllowed
                                        "autoplay" -> viewModel.defaultAutoplayAllowed
                                        "popups" -> !viewModel.isPopupBlockerEnabled
                                        else -> true
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                when (selectedPermissionType) {
                                                    "javascript" -> viewModel.updateGlobalJavascriptAllowed(true)
                                                    "autoplay" -> viewModel.updateGlobalAutoplayAllowed(true)
                                                    "popups" -> viewModel.updatePopupBlockerEnabled(false, viewModel.appContext ?: return@clickable)
                                                }
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(selected = isAllowed, onClick = {
                                            when (selectedPermissionType) {
                                                "javascript" -> viewModel.updateGlobalJavascriptAllowed(true)
                                                "autoplay" -> viewModel.updateGlobalAutoplayAllowed(true)
                                                "popups" -> viewModel.updatePopupBlockerEnabled(false, viewModel.appContext ?: return@RadioButton)
                                            }
                                        }, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
                                        Text(allowedText, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    
                                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                when (selectedPermissionType) {
                                                    "javascript" -> viewModel.updateGlobalJavascriptAllowed(false)
                                                    "autoplay" -> viewModel.updateGlobalAutoplayAllowed(false)
                                                    "popups" -> viewModel.updatePopupBlockerEnabled(true, viewModel.appContext ?: return@clickable)
                                                }
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(selected = !isAllowed, onClick = {
                                            when (selectedPermissionType) {
                                                "javascript" -> viewModel.updateGlobalJavascriptAllowed(false)
                                                "autoplay" -> viewModel.updateGlobalAutoplayAllowed(false)
                                                "popups" -> viewModel.updatePopupBlockerEnabled(true, viewModel.appContext ?: return@RadioButton)
                                            }
                                        }, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
                                        Text(blockedText, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    // Ternary options (Location, Camera, Mic, Notifications: Ask, Allow, Block)
                                    val currentVal = when (selectedPermissionType) {
                                        "location" -> viewModel.defaultGeolocation
                                        "camera" -> viewModel.defaultCamera
                                        "microphone" -> viewModel.defaultMicrophone
                                        "notifications" -> viewModel.defaultNotifications
                                        else -> "ask"
                                    }

                                    val options = listOf(
                                        "ask" to "Ask first (Recommended)",
                                        "allow" to "Allowed",
                                        "block" to "Blocked"
                                    )

                                    options.forEachIndexed { index, (optVal, optLabel) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.updateGlobalSitePermission(selectedPermissionType, optVal) }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(selected = currentVal == optVal, onClick = {
                                                viewModel.updateGlobalSitePermission(selectedPermissionType, optVal)
                                            }, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
                                            Text(optLabel, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        if (index < options.lastIndex) {
                                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Site Custom Overrides Details Dialog ──
    if (showSiteDetailsDialog && selectedSiteHost.isNotBlank()) {
        val site = viewModel.sitePermissions.find { it.host == selectedSiteHost } ?: SitePermission(host = selectedSiteHost)
        
        AlertDialog(
            onDismissRequest = { showSiteDetailsDialog = false },
            containerColor = cardColor,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E2D3F) else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data("https://www.google.com/s2/favicons?domain=${selectedSiteHost}&sz=64")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = selectedSiteHost,
                        color = textPrimaryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Permissions overrides:",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    val permissionOptions = listOf(
                        "location" to "Location",
                        "camera" to "Camera",
                        "microphone" to "Microphone",
                        "notifications" to "Notifications",
                        "javascript" to "JavaScript",
                        "autoplay" to "Autoplay"
                    )

                    permissionOptions.forEach { (type, label) ->
                        val currentVal = when (type) {
                            "location" -> site.location
                            "camera" -> site.camera
                            "microphone" -> site.microphone
                            "notifications" -> site.notifications
                            "javascript" -> site.javascript
                            "autoplay" -> site.autoplay
                            else -> "ask"
                        }

                        val allowedLabel = if (type == "javascript" || type == "autoplay") "Allow" else "Allowed"
                        val blockedLabel = if (type == "javascript" || type == "autoplay") "Block" else "Blocked"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (type != "javascript" && type != "autoplay") {
                                    // Ternary Selector Ask/Allow/Block
                                    listOf("ask", "allow", "block").forEach { opt ->
                                        val btnColor = if (currentVal == opt) accentColor else Color.Transparent
                                        val contentCol = if (currentVal == opt) Color.White else textSecondaryColor
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(btnColor)
                                                .clickable { viewModel.updateSitePermission(selectedSiteHost, type, opt) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (opt) {
                                                    "ask" -> "Ask"
                                                    "allow" -> "Allow"
                                                    else -> "Block"
                                                },
                                                color = contentCol,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    // Binary Selector Allow/Block
                                    listOf("allow", "block").forEach { opt ->
                                        val btnColor = if (currentVal == opt) accentColor else Color.Transparent
                                        val contentCol = if (currentVal == opt) Color.White else textSecondaryColor
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(btnColor)
                                                .clickable { viewModel.updateSitePermission(selectedSiteHost, type, opt) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (opt == "allow") "Allow" else "Block",
                                                color = contentCol,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSiteDetailsDialog = false }
                ) {
                    Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSitePermission(selectedSiteHost)
                        showSiteDetailsDialog = false
                    }
                ) {
                    Text("Reset Permissions", color = Color(0xFFFF3B5C), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
