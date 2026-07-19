/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
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
import com.rebelroot.omni.ui.theme.AccentThemesLight
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLanguageChanged: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenWallpapers: () -> Unit = {},
    onOpenPrivacySecurity: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenSiteSettings: () -> Unit = {}
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val inputBgColor = MaterialTheme.colorScheme.surfaceVariant

    var isNotificationsEnabled by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotificationsEnabled = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    var showLanguageSelector by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAddSearchEngineDialog by remember { mutableStateOf(false) }



    val languages = listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "hi" to "हिन्दी",
        "pt" to "Português",
        "ru" to "Русский",
        "zh" to "简体中文",
        "ja" to "日本語"
    )

    val currentLangName = languages.find { it.first == viewModel.selectedLanguageCode }?.second ?: "English"

    val roleManager = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            context.getSystemService(android.content.Context.ROLE_SERVICE) as? android.app.role.RoleManager
        } else {
            null
        }
    }

    var isDefaultBrowser by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
            } else {
                val defaultBrowserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                val resolveInfo = context.packageManager.resolveActivity(defaultBrowserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName == context.packageName
            }
        )
    }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultBrowser = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
        } else {
            val defaultBrowserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
            val resolveInfo = context.packageManager.resolveActivity(defaultBrowserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f))
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(bgColor) // Dynamic background
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            var showClearCacheConfirmation by remember { mutableStateOf(false) }

            if (showClearCacheConfirmation) {
                AlertDialog(
                    onDismissRequest = { showClearCacheConfirmation = false },
                    title = { Text("Clear Cache & Site Data", color = textPrimaryColor, fontWeight = FontWeight.Bold) },
                    text = { Text("This will log you out of websites, clear all cookies, offline data, and free up browser storage. This cannot be undone.", color = textPrimaryColor) },
                    containerColor = cardColor,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearCacheConfirmation = false
                                viewModel.clearCacheAndSiteData(context)
                            }
                        ) {
                            Text("Clear", color = Color(0xFFFF4444))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearCacheConfirmation = false }) {
                            Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor)
                        }
                    }
                )
            }

            // ── Helper composable ─────────────────────────────────────────────────
            @Composable
            fun SectionHeader(title: String) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            @Composable
            fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
                }
            }

            @Composable
            fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, badge: String? = null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (badge != null) {
                                Surface(color = Color(0xFFFF9500).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text(badge, color = Color(0xFFFF9500), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(subtitle, color = textSecondaryColor, fontSize = 11.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                }
            }

            @Composable
            fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = textSecondaryColor, fontSize = 11.sp)
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                    )
                }
            }

            // ── 1. PERSONALIZATION ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("PERSONALIZATION")
                SettingsCard {
                    NavRow(Icons.Rounded.Palette, "Appearance", "Theme mode, accent colors, layout, and UI scale", onOpenAppearance)
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Wallpaper, "Wallpapers", "Browser background, dynamic wallpaper blur/dim", onOpenWallpapers, badge = "Experimental")
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.AccessibilityNew, "Accessibility", "Text scaling, force enable zoom, and high contrast mode", onOpenAccessibility)
                }
            }

            // ── 2. BROWSING ───────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("BROWSING")
                SettingsCard {
                    NavRow(Icons.Rounded.Tab, "Tabs", "Tab layouts, background tabs, and auto-closing settings", onOpenTabs)
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Language, "Site Settings", "Manage site permissions, javascript, autoplay, and popups", onOpenSiteSettings)
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Default Browser
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isDefaultBrowser) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        roleManager?.let { rm ->
                                            if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                                                defaultBrowserLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                                            }
                                        }
                                    } else {
                                        try {
                                            defaultBrowserLauncher.launch(android.content.Intent("android.intent.action.SET_DEFAULT").apply { addCategory(android.content.Intent.CATEGORY_DEFAULT); type = "text/html" })
                                        } catch (e: Exception) {
                                            try { defaultBrowserLauncher.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                                            catch (ex: Exception) { Toast.makeText(context, "Please set default browser in System Settings", Toast.LENGTH_LONG).show() }
                                        }
                                    }
                                } else { Toast.makeText(context, "Omni Browser is already your default browser!", Toast.LENGTH_SHORT).show() }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.default_browser_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isDefaultBrowser) stringResource(id = R.string.default_browser_active) else stringResource(id = R.string.default_browser_inactive),
                                color = if (isDefaultBrowser) Color(0xFF30D158) else textSecondaryColor,
                                fontSize = 11.sp,
                                fontWeight = if (isDefaultBrowser) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Icon(
                            imageVector = if (isDefaultBrowser) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (isDefaultBrowser) Color(0xFF30D158) else textSecondaryColor
                        )
                    }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // App Language
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageSelector = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Translate, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.app_language_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(currentLangName, color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    SwitchRow(Icons.Rounded.Newspaper, "Discover Feed", "Show news recommendations on the home page", viewModel.showDiscoverFeed) { viewModel.saveShowDiscoverFeed(context, it) }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Rounded.ViewAgenda, "Bottom Navigation Bar", "Show bottom toolbar with main navigation buttons", viewModel.showBottomNavBar) { viewModel.saveShowBottomNavBar(context, it) }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // PDF Export Theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Print, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                            Column {
                                Text(stringResource(id = R.string.pdf_export_theme_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(id = R.string.pdf_export_theme_desc), color = textSecondaryColor, fontSize = 11.sp)
                            }
                        }
                        var pdfExpanded by remember { mutableStateOf(false) }
                        val pdfThemes = listOf("default" to stringResource(id = R.string.system_default), "dark" to stringResource(id = R.string.dark_theme), "light" to stringResource(id = R.string.light_theme))
                        val currentPdfThemeVal = viewModel.pdfExportTheme
                        val currentPdfThemeLabel = pdfThemes.find { it.first == currentPdfThemeVal }?.second ?: "System Default"
                        Box {
                            Row(
                                modifier = Modifier
                                    .width(150.dp).height(36.dp)
                                    .background(inputBgColor, RoundedCornerShape(8.dp))
                                    .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))
                                    .clickable { pdfExpanded = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(currentPdfThemeLabel, color = textPrimaryColor, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = pdfExpanded, onDismissRequest = { pdfExpanded = false },
                                modifier = Modifier.width(150.dp).background(cardColor).border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))) {
                                pdfThemes.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, color = textPrimaryColor, fontSize = 12.sp) },
                                        onClick = { viewModel.savePdfExportTheme(context, value); pdfExpanded = false },
                                        colors = MenuDefaults.itemColors(textColor = textPrimaryColor),
                                        trailingIcon = { if (currentPdfThemeVal == value) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. PRIVACY & SECURITY ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("PRIVACY & SECURITY")
                SettingsCard {
                    NavRow(Icons.Rounded.Security, "Privacy and Security", "Clear browsing data, cookies, Safe Browsing, and device lock", onOpenPrivacySecurity)
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Rounded.VisibilityOff, "Private Browsing", stringResource(id = R.string.private_browsing_desc), viewModel.isIncognitoMode) { viewModel.toggleIncognitoMode(context) }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    // Notifications
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.notifications_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.notifications_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isNotificationsEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else { isNotificationsEnabled = true }
                                } else {
                                    isNotificationsEnabled = false
                                    Toast.makeText(context, "Notifications disabled. Revoke system permission in settings if desired.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                    }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearCacheConfirmation = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFFF4444), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Cache & Site Data", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Removes cookies, offline data, and frees storage", color = textSecondaryColor, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }
                }
            }

            // ── 4. MEDIA ──────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("MEDIA")
                SettingsCard {
                    SwitchRow(Icons.Rounded.PlayCircle, stringResource(id = R.string.native_player_title), stringResource(id = R.string.native_player_desc), viewModel.isNativePlayerEnabled) { viewModel.toggleNativePlayer(context) }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(Icons.Rounded.Block, stringResource(id = R.string.ai_blocker_title), stringResource(id = R.string.ai_blocker_desc), viewModel.isAiBlockerEnabled) { viewModel.toggleAiBlocker(context) }
                }
            }

            // ── 5. SEARCH ─────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(id = R.string.search_engine_section))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = R.string.search_engine_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        var expanded by remember { mutableStateOf(false) }
                        val engines = listOf("Google", "Yahoo", "Yandex", "DuckDuckGo", "Brave", "Bing", "Ecosia", "Startpage", "Qwant", "Custom") + viewModel.customSearchEngines.map { it.name }
                        val currentEngine = viewModel.selectedSearchEngine
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                    .background(inputBgColor, RoundedCornerShape(12.dp))
                                    .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(12.dp))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val icon = when (currentEngine) {
                                        "Google" -> Icons.Rounded.Search
                                        "DuckDuckGo", "Brave" -> Icons.Rounded.Security
                                        "Bing" -> Icons.Rounded.Language
                                        "Custom" -> Icons.Rounded.Build
                                        else -> Icons.Rounded.Settings
                                    }
                                    Icon(icon, contentDescription = null, tint = accentColor)
                                    Text(currentEngine, color = textPrimaryColor, fontSize = 14.sp)
                                }
                                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = textSecondaryColor)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(cardColor).border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))) {
                                engines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine, color = textPrimaryColor) },
                                        onClick = { viewModel.saveSearchEngine(context, engine); expanded = false },
                                        colors = MenuDefaults.itemColors(textColor = textPrimaryColor, trailingIconColor = accentColor),
                                        trailingIcon = { if (currentEngine == engine) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accentColor) }
                                    )
                                }
                            }
                        }
                        if (currentEngine == "Custom") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(id = R.string.custom_query_template), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            var customUrlText by remember(viewModel.customSearchUrl) { mutableStateOf(viewModel.customSearchUrl) }
                            OutlinedTextField(
                                value = customUrlText,
                                onValueChange = { customUrlText = it; viewModel.saveCustomSearchUrl(context, it) },
                                placeholder = { Text("https://example.com/search?q=%s", color = textSecondaryColor) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Rounded.Build, contentDescription = null, tint = textSecondaryColor) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimaryColor, unfocusedTextColor = textPrimaryColor,
                                    focusedBorderColor = accentColor, unfocusedBorderColor = cardBorderColor,
                                    focusedContainerColor = inputBgColor, unfocusedContainerColor = inputBgColor
                                )
                            )
                            Text(stringResource(id = R.string.custom_query_placeholder_desc), color = textSecondaryColor, fontSize = 11.sp)
                        }
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Custom Search Engines", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showAddSearchEngineDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add New", color = accentColor)
                            }
                        }
                        if (viewModel.customSearchEngines.isEmpty()) {
                            Text("No custom search engines added yet.", color = textSecondaryColor, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            viewModel.customSearchEngines.forEach { engine ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(engine.name, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(engine.queryUrl, color = textSecondaryColor, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.deleteCustomSearchEngine(context, engine) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 6. ABOUT ──────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(id = R.string.about_section))
                SettingsCard {
                    val appVersionName = remember {
                        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.6" }
                        catch (e: Exception) { "1.0.6" }
                    }
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var updateResult by remember { mutableStateOf<BrowserViewModel.UpdateCheckResult?>(null) }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(22.dp))
                        Text(stringResource(id = R.string.app_version_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(appVersionName, color = textSecondaryColor, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isCheckingUpdate) {
                            isCheckingUpdate = true
                            viewModel.checkAppUpdates(context) { result -> isCheckingUpdate = false; updateResult = result }
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(if (isCheckingUpdate) Icons.Rounded.Refresh else Icons.Rounded.Update, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Text(if (isCheckingUpdate) stringResource(id = R.string.checking_text) else stringResource(id = R.string.check_updates_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!isCheckingUpdate) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                    }

                    updateResult?.let { result ->
                        when (result) {
                            is BrowserViewModel.UpdateCheckResult.NewUpdateAvailable -> {
                                AlertDialog(
                                    onDismissRequest = { updateResult = null },
                                    title = { Text(stringResource(id = R.string.update_dialog_title), color = textPrimaryColor) },
                                    text = { Text(stringResource(id = R.string.update_dialog_desc, result.versionName), color = textPrimaryColor) },
                                    containerColor = cardColor,
                                    confirmButton = {
                                        Button(onClick = {
                                            updateResult = null
                                            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result.playStoreUrl)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                                            catch (e: Exception) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.rebelroot.omni")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                                        }) { Text(stringResource(id = R.string.update_dialog_btn)) }
                                    },
                                    dismissButton = { TextButton(onClick = { updateResult = null }) { Text(stringResource(id = R.string.update_dialog_later), color = textSecondaryColor) } }
                                )
                            }
                            is BrowserViewModel.UpdateCheckResult.NoUpdateAvailable -> { Toast.makeText(context, stringResource(id = R.string.update_no_update, appVersionName), Toast.LENGTH_SHORT).show(); updateResult = null }
                            is BrowserViewModel.UpdateCheckResult.Error -> {
                                AlertDialog(
                                    onDismissRequest = { updateResult = null },
                                    title = { Text(stringResource(id = R.string.update_failed_title), color = textPrimaryColor) },
                                    text = { Text(stringResource(id = R.string.update_failed_desc, result.message), color = textPrimaryColor) },
                                    containerColor = cardColor,
                                    confirmButton = {
                                        Button(onClick = {
                                            updateResult = null
                                            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.rebelroot.omni")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                                            catch (e: Exception) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.rebelroot.omni")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }
                                        }) { Text(stringResource(id = R.string.update_dialog_open_store)) }
                                    },
                                    dismissButton = { TextButton(onClick = { updateResult = null }) { Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor) } }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.AutoMirrored.Rounded.Help, "Help & Support", "Get help, FAQs, and contact us", onClick = { onOpenUrl("https://rebelroot.xyz/support") })
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Feedback, "Send Direct Feedback", "Report bugs, suggest features, share thoughts", onClick = { showFeedbackDialog = true })
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Code, stringResource(id = R.string.support_github), "View source code and file issues", onClick = { onOpenUrl("https://github.com/rebelroot") })
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Public, stringResource(id = R.string.website_omnibrowser), "Visit the official Omni Browser website", onClick = { onOpenUrl("https://www.rebelroot.xyz/omnibrowser") })
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    NavRow(Icons.Rounded.Shield, stringResource(id = R.string.privacy_policy_title), "Read our privacy policy", onClick = { onOpenUrl("https://www.rebelroot.xyz/omnibrowser/privacy-policy") })
                }
            }
        }
    }

    if (showLanguageSelector) {
        AlertDialog(
            onDismissRequest = { showLanguageSelector = false },
            title = {
                Text(
                    text = stringResource(id = R.string.app_language_title),
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = cardColor,
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageSelector = false
                                    coroutineScope.launch {
                                        viewModel.saveLanguagePreference(context, code)
                                        onLanguageChanged()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = if (viewModel.selectedLanguageCode == code) FontWeight.Bold else FontWeight.Normal
                            )
                            if (viewModel.selectedLanguageCode == code) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageSelector = false }) {
                    Text(
                        text = stringResource(id = R.string.cancel_text),
                        color = accentColor
                    )
                }
            }
        )
    }

    if (showFeedbackDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var rating by remember { mutableStateOf(5) }
        var comment by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showFeedbackDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Feedback, contentDescription = null, tint = accentColor)
                    Text("Send Direct Feedback", color = textPrimaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardColor,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your feedback is sent directly to the development team's Telegram bot. Thank you for helping us improve!",
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = textSecondaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = textSecondaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rating", color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 1..5) {
                                IconButton(
                                    onClick = { rating = i },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "$i Stars",
                                        tint = if (i <= rating) Color(0xFFFFD700) else textSecondaryColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Message / Suggestion") },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = textSecondaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        viewModel.sendFeedbackToTelegram(name, email, rating, comment) { success, error ->
                            isSubmitting = false
                            if (success) {
                                Toast.makeText(context, "Feedback sent successfully!", Toast.LENGTH_SHORT).show()
                                showFeedbackDialog = false
                            } else {
                                Toast.makeText(context, "Failed to send: ${error ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = comment.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Submit", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFeedbackDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor)
                }
            }
        )
    }

    if (showAddSearchEngineDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showAddSearchEngineDialog = false
                name = ""
                url = ""
                errorText = null
            },
            title = {
                Text(
                    text = "Add Custom Search Engine",
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = cardColor,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter the name and the search query URL for the custom search engine.",
                        color = textSecondaryColor,
                        fontSize = 13.sp
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            errorText = null
                        },
                        label = { Text("Name (e.g. Startpage)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorderColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor
                        )
                    )
                    
                    OutlinedTextField(
                        value = url,
                        onValueChange = { 
                            url = it
                            errorText = null
                        },
                        label = { Text("Search URL (with %s)") },
                        singleLine = true,
                        placeholder = { Text("https://example.com/search?q=%s") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = cardBorderColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor
                        )
                    )
                    
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedUrl = url.trim()
                        if (trimmedName.isEmpty()) {
                            errorText = "Name is required"
                            return@Button
                        }
                        if (trimmedUrl.isEmpty()) {
                            errorText = "URL is required"
                            return@Button
                        }
                        if (!trimmedUrl.contains("%s")) {
                            errorText = "URL must contain %s query placeholder"
                            return@Button
                        }
                        val builtInNames = listOf("Google", "DuckDuckGo", "Brave", "Bing", "Custom")
                        if (builtInNames.any { it.equals(trimmedName, ignoreCase = true) }) {
                            errorText = "Name matches a built-in search engine"
                            return@Button
                        }
                        if (viewModel.customSearchEngines.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                            errorText = "A custom search engine with this name already exists"
                            return@Button
                        }
                        viewModel.addCustomSearchEngine(context, trimmedName, trimmedUrl)
                        showAddSearchEngineDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showAddSearchEngineDialog = false
                    }
                ) {
                    Text("Cancel", color = textSecondaryColor)
                }
            }
        )
    }
}


