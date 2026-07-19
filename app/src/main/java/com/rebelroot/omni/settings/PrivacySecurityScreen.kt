/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val isDarkMode = viewModel.isDarkThemeEnabled
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF111111) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    // Dialog state controllers
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showPrivacyGuideDialog by remember { mutableStateOf(false) }
    var showCookieBehaviorDialog by remember { mutableStateOf(false) }
    var showAdPrivacyDialog by remember { mutableStateOf(false) }
    var showPreloadPagesDialog by remember { mutableStateOf(false) }
    var showSafeBrowsingDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy and security", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
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
            // ── PRIVACY CATEGORY ───────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    // Item 1: Delete browsing data
                    SettingsRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Delete browsing data",
                        subtitle = "Delete history, cookies, site data, cache...",
                        onClick = { showClearDataDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 2: Privacy Guide
                    SettingsRow(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "Privacy Guide",
                        subtitle = "Review key privacy and security controls",
                        onClick = { showPrivacyGuideDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 3: Third-party cookies
                    val cookieSubtitle = when (viewModel.cookieBehavior) {
                        0 -> "Third-party cookies are allowed"
                        1 -> "All cookies are blocked"
                        2 -> "Third-party cookies are blocked"
                        else -> "Third-party tracking cookies are blocked (Recommended)"
                    }
                    SettingsRow(
                        icon = Icons.Rounded.Cookie,
                        title = "Third-party cookies",
                        subtitle = cookieSubtitle,
                        onClick = { showCookieBehaviorDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 4: Ad privacy
                    SettingsRow(
                        icon = Icons.Rounded.FeaturedPlayList,
                        title = "Ad privacy",
                        subtitle = "Customize the info used by sites to show you ads",
                        onClick = { showAdPrivacyDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 5: Do Not Track
                    SettingsSwitchRow(
                        icon = Icons.Rounded.RemoveRedEye,
                        title = "Send a \"Do Not Track\" request",
                        subtitle = if (viewModel.doNotTrack) "On" else "Off",
                        checked = viewModel.doNotTrack,
                        onCheckedChange = { viewModel.saveDoNotTrack(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 6: Preload pages
                    val preloadSubtitle = when (viewModel.preloadPages) {
                        0 -> "No preloading"
                        else -> "Standard preloading"
                    }
                    SettingsRow(
                        icon = Icons.Rounded.NetworkWifi,
                        title = "Preload pages",
                        subtitle = preloadSubtitle,
                        onClick = { showPreloadPagesDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 7: Lock Incognito tabs
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Lock,
                        title = "Lock Incognito tabs when you leave",
                        subtitle = "Authenticate to access your private tabs",
                        checked = viewModel.lockIncognito,
                        onCheckedChange = { viewModel.saveLockIncognito(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                }
            }

            // ── SECURITY CATEGORY ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Security", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                ) {
                    // Item 8: Safe Browsing
                    val sbSubtitle = when (viewModel.safeBrowsingLevel) {
                        0 -> "No protection (warnings disabled)"
                        2 -> "Enhanced protection is on"
                        else -> "Standard protection is on (Recommended)"
                    }
                    SettingsRow(
                        icon = Icons.Rounded.Shield,
                        title = "Safe Browsing",
                        subtitle = sbSubtitle,
                        onClick = { showSafeBrowsingDialog = true },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 9: HTTPS-Only Mode
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Https,
                        title = "HTTPS-Only Mode",
                        subtitle = "Upgrade all connection attempts to HTTPS",
                        checked = viewModel.httpsOnlyMode,
                        onCheckedChange = { viewModel.saveHttpsOnlyMode(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // Item 10: Compromised password warning
                    SettingsSwitchRow(
                        icon = Icons.Rounded.ReportProblem,
                        title = "Warn you of compromised passwords",
                        subtitle = "Alerts you if saved logins leak in a data breach",
                        checked = viewModel.compromisedPasswordWarning,
                        onCheckedChange = { viewModel.saveCompromisedPasswordWarning(context, it) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        accentColor = accentColor
                    )
                }
            }
        }
    }

    // ── DIALOGS IMPLEMENTATION ──────────────────────────────────────────────

    // Delete Browsing Data Dialog
    if (showClearDataDialog) {
        var clearHist by remember { mutableStateOf(true) }
        var clearCook by remember { mutableStateOf(true) }
        var clearCach by remember { mutableStateOf(true) }
        var clearPass by remember { mutableStateOf(false) }
        var clearAuto by remember { mutableStateOf(false) }
        var selectedTimeRange by remember { mutableStateOf(-1) } // Default: all time (-1)

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Delete browsing data", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Time Range Selector
                    Text("Time range", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    var expandedTimeRange by remember { mutableStateOf(false) }
                    val timeRanges = listOf(
                        "Last hour" to 60,
                        "Last 24 hours" to 1440,
                        "Last 7 days" to 10080,
                        "Last 4 weeks" to 40320,
                        "All time" to -1
                    )
                    Box {
                        Button(
                            onClick = { expandedTimeRange = true },
                            colors = ButtonDefaults.buttonColors(containerColor = cardColor, contentColor = textPrimaryColor),
                            modifier = Modifier.fillMaxWidth().border(0.5.dp, cardBorderColor, RoundedCornerShape(8.dp))
                        ) {
                            Text(timeRanges.find { it.second == selectedTimeRange }?.first ?: "All time")
                        }
                        DropdownMenu(
                            expanded = expandedTimeRange,
                            onDismissRequest = { expandedTimeRange = false }
                        ) {
                            timeRanges.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedTimeRange = value
                                        expandedTimeRange = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // Checkboxes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearHist, onCheckedChange = { clearHist = it })
                        Text("Browsing history", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearCook, onCheckedChange = { clearCook = it })
                        Text("Cookies and site data", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearCach, onCheckedChange = { clearCach = it })
                        Text("Cached images and files", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearPass, onCheckedChange = { clearPass = it })
                        Text("Saved passwords", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearAuto, onCheckedChange = { clearAuto = it })
                        Text("Autofill form data", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCustomBrowsingData(
                            context = context,
                            clearHistory = clearHist,
                            clearCookies = clearCook,
                            clearCache = clearCach,
                            clearPasswords = clearPass,
                            clearAutofill = clearAuto,
                            timeRangeMinutes = selectedTimeRange,
                            onComplete = {
                                showClearDataDialog = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text("Delete data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = textPrimaryColor)
                }
            }
        )
    }

    // Privacy Guide Wizard Dialog
    if (showPrivacyGuideDialog) {
        var currentStep by remember { mutableStateOf(0) }
        val steps = listOf(
            Triple(
                "Search and suggestions",
                "As you type in the address bar, Omni Browser queries suggestions from your selected search engine. Using privacy-centric engines like DuckDuckGo or Startpage helps block search profiling.",
                Icons.Rounded.Search
            ),
            Triple(
                "Safe Browsing Protection",
                "Safe Browsing detects and alerts you about suspicious sites, files, and compromised certificates. Toggle Enhanced protection to actively intercept phishing targets beforehand.",
                Icons.Rounded.Security
            ),
            Triple(
                "Blocking cookies",
                "You can choose to allow cookies, block third-party trackers, block all third-party cookies, or block all cookies. Restricting cookies keeps tracker networks from mapping you.",
                Icons.Rounded.Cookie
            )
        )

        AlertDialog(
            onDismissRequest = { showPrivacyGuideDialog = false },
            title = { Text(steps[currentStep].first, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = steps[currentStep].third,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = steps[currentStep].second,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        color = textPrimaryColor
                    )
                    Text(
                        text = "Step ${currentStep + 1} of ${steps.size}",
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                if (currentStep < steps.size - 1) {
                    Button(onClick = { currentStep++ }) {
                        Text("Next")
                    }
                } else {
                    Button(onClick = { showPrivacyGuideDialog = false }) {
                        Text("Got it")
                    }
                }
            },
            dismissButton = {
                if (currentStep > 0) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back", color = textPrimaryColor)
                    }
                } else {
                    TextButton(onClick = { showPrivacyGuideDialog = false }) {
                        Text("Cancel", color = textPrimaryColor)
                    }
                }
            }
        )
    }

    // Cookie Behavior Choice Dialog
    if (showCookieBehaviorDialog) {
        val behaviors = listOf(
            0 to "Allow all cookies",
            3 to "Block third-party tracking cookies (Recommended)",
            2 to "Block all third-party cookies",
            1 to "Block all cookies"
        )
        AlertDialog(
            onDismissRequest = { showCookieBehaviorDialog = false },
            title = { Text("Third-party cookies", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    behaviors.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.saveCookieBehavior(context, value)
                                    showCookieBehaviorDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.cookieBehavior == value,
                                onClick = {
                                    viewModel.saveCookieBehavior(context, value)
                                    showCookieBehaviorDialog = false
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Ad Privacy Explanatory Dialog
    if (showAdPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showAdPrivacyDialog = false },
            title = { Text("Ad privacy settings", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Omni Browser blocks known third-party ad networks, telemetry trackers, and fingerprinters natively inside GeckoView. We do not support targeted site-based advertising protocols.",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(onClick = { showAdPrivacyDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Preload Pages Dialog
    if (showPreloadPagesDialog) {
        val preloadOptions = listOf(
            1 to "Standard preloading (Recommended)",
            0 to "No preloading"
        )
        AlertDialog(
            onDismissRequest = { showPreloadPagesDialog = false },
            title = { Text("Preload pages", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preloadOptions.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.savePreloadPages(context, value)
                                    showPreloadPagesDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.preloadPages == value,
                                onClick = {
                                    viewModel.savePreloadPages(context, value)
                                    showPreloadPagesDialog = false
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Safe Browsing Protection Levels Dialog
    if (showSafeBrowsingDialog) {
        val safeBrowsingOptions = listOf(
            2 to "Enhanced protection (Includes HTTPS-Only Mode upgrade)",
            1 to "Standard protection (Recommended)",
            0 to "No protection (warnings disabled)"
        )
        AlertDialog(
            onDismissRequest = { showSafeBrowsingDialog = false },
            title = { Text("Safe Browsing protection", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    safeBrowsingOptions.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.saveSafeBrowsingLevel(context, value)
                                    showSafeBrowsingDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.safeBrowsingLevel == value,
                                onClick = {
                                    viewModel.saveSafeBrowsingLevel(context, value)
                                    showSafeBrowsingDialog = false
                                }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// Helpers for settings rows
@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSecondaryColor, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = textSecondaryColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSecondaryColor, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}
