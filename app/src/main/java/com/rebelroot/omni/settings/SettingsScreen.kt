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
import com.rebelroot.omni.ui.theme.AccentThemes
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLanguageChanged: () -> Unit = {}
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode = viewModel.isDarkThemeEnabled
    
    val bgColor = if (isDarkMode) Color(0xFF070A0F) else Color(0xFFF8F9FA)
    val cardColor = if (isDarkMode) Color(0xFF16222F) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF23374A) else Color(0x1F000000)
    val textPrimaryColor = if (isDarkMode) Color.White else Color(0xFF202124)
    val textSecondaryColor = if (isDarkMode) Color(0xFF8E9AA8) else Color(0xFF606266)
    val dividerColor = if (isDarkMode) Color(0xFF23374A).copy(alpha = 0.5f) else Color(0x1F000000)
    val inputBgColor = if (isDarkMode) Color(0xFF070A0F) else Color(0xFFF2F3F5)
    val accentColor = MaterialTheme.colorScheme.primary

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

    val gso = remember {
        com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.handleGoogleSignInResult(account)
            Toast.makeText(context, "Signed in successfully as ${account.displayName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkGoogleSignInStatus(context)
    }

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
            // 1. Profile Avatar Header Card (Google SSO integrated)
            val isSignedIn = viewModel.googleAccountIsSignedIn
            val email = viewModel.googleAccountEmail ?: "Sign in to sync your bookmarks & tabs"
            val displayName = viewModel.googleAccountDisplayName ?: "Sign In with Google"
            val photoUrl = viewModel.googleAccountPhotoUrl

            var showSignOutConfirmation by remember { mutableStateOf(false) }
            var showClearCacheConfirmation by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (isSignedIn) {
                            showSignOutConfirmation = true
                        } else {
                            try {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Google Play Services not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                color = cardColor,
                border = BorderStroke(0.5.dp, cardBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSignedIn && !photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Avatar",
                                tint = accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = textPrimaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = email,
                            color = textSecondaryColor,
                            fontSize = 11.sp
                        )
                    }
                    
                    if (isSignedIn) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = Color(0xFFFF4444)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textSecondaryColor
                        )
                    }
                }
            }

            if (showSignOutConfirmation) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirmation = false },
                    title = { Text(stringResource(id = R.string.sign_out_title), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(id = R.string.sign_out_confirm_desc), color = textPrimaryColor) },
                    containerColor = cardColor,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSignOutConfirmation = false
                                viewModel.googleSignOut(context) {
                                    Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.sign_out_title), color = Color(0xFFFF4444))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirmation = false }) {
                            Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor)
                        }
                    }
                )
            }

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

            // 2. GENERAL Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.general_section),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Appearance: Dark Mode + Accent Color
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            // Dark Mode toggle row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = accentColor)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(id = R.string.dark_mode_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(id = R.string.dark_mode_desc), color = textSecondaryColor, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { viewModel.saveDarkTheme(context, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentColor
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            // Accent Color selector
                            Text(
                                stringResource(id = R.string.accent_color_title),
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val accentOptions = listOf(
                                "Ocean Blue" to Color(0xFF0A84FF),
                                "Crimson Red" to Color(0xFFFF3B5C),
                                "Emerald Green" to Color(0xFF00C853),
                                "Sunset Orange" to Color(0xFFFF6D00),
                                "Royal Purple" to Color(0xFF7C4DFF),
                                "Monochrome" to Color(0xFFAAAAAA)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                accentOptions.forEach { (name, color) ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { viewModel.saveAccentTheme(context, name) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (viewModel.selectedAccentTheme == name) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            // PDF Export Theme
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Print, contentDescription = null, tint = accentColor)
                                    Column {
                                        Text(stringResource(id = R.string.pdf_export_theme_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(id = R.string.pdf_export_theme_desc), color = textSecondaryColor, fontSize = 11.sp)
                                    }
                                }

                                var pdfExpanded by remember { mutableStateOf(false) }
                                val pdfThemes = listOf(
                                    "default" to stringResource(id = R.string.system_default),
                                    "dark" to stringResource(id = R.string.dark_theme),
                                    "light" to stringResource(id = R.string.light_theme)
                                )
                                val currentPdfThemeVal = viewModel.pdfExportTheme
                                val currentPdfThemeLabel = pdfThemes.find { it.first == currentPdfThemeVal }?.second ?: "System Default"

                                Box {
                                    Row(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(36.dp)
                                            .background(inputBgColor, RoundedCornerShape(8.dp))
                                            .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))
                                            .clickable { pdfExpanded = true }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = currentPdfThemeLabel,
                                            color = textPrimaryColor,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = textSecondaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = pdfExpanded,
                                        onDismissRequest = { pdfExpanded = false },
                                        modifier = Modifier
                                            .width(150.dp)
                                            .background(cardColor)
                                            .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))
                                    ) {
                                        pdfThemes.forEach { (value, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label, color = textPrimaryColor, fontSize = 12.sp) },
                                                onClick = {
                                                    viewModel.savePdfExportTheme(context, value)
                                                    pdfExpanded = false
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = textPrimaryColor
                                                ),
                                                trailingIcon = {
                                                    if (currentPdfThemeVal == value) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "Selected",
                                                            tint = accentColor,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // Row 2: Notifications
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = accentColor)
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
                                        } else {
                                            isNotificationsEnabled = true
                                        }
                                    } else {
                                        isNotificationsEnabled = false
                                        Toast.makeText(context, "Notifications disabled. Revoke system permission in settings if desired.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // Row 3: Private Browsing (Incognito Mode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = accentColor)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.private_browsing_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(id = R.string.private_browsing_desc), color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = viewModel.isIncognitoMode,
                                onCheckedChange = { viewModel.toggleIncognitoMode(context) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // Row 4: Native Video Player
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = accentColor)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.native_player_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(id = R.string.native_player_desc), color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = viewModel.isNativePlayerEnabled,
                                onCheckedChange = { viewModel.toggleNativePlayer(context) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        // Row 4.5: AI Blocker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, tint = accentColor)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.ai_blocker_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(id = R.string.ai_blocker_desc), color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = viewModel.isAiBlockerEnabled,
                                onCheckedChange = { viewModel.toggleAiBlocker(context) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        // Row 5: Default Browser
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isDefaultBrowser) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            roleManager?.let { rm ->
                                                if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && 
                                                    !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                                                    val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                                                    defaultBrowserLauncher.launch(intent)
                                                }
                                            }
                                        } else {
                                            try {
                                                val intent = android.content.Intent("android.intent.action.SET_DEFAULT").apply {
                                                    addCategory(android.content.Intent.CATEGORY_DEFAULT)
                                                    type = "text/html"
                                                }
                                                defaultBrowserLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                                    defaultBrowserLauncher.launch(intent)
                                                } catch (ex: Exception) {
                                                    Toast.makeText(context, "Please set default browser in System Settings", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Omni Browser is already your default browser!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = accentColor)
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

                        /*
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        // Row 6: App Language
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageSelector = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = accentColor)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.app_language_title),
                                    color = textPrimaryColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentLangName,
                                    color = textSecondaryColor,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textSecondaryColor
                            )
                        }
                        */
                    }
                }
            }

            // WireGuard VPN Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.vpn_section),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                val vpnState by viewModel.vpnManager.state.collectAsState()
                val hasConfig = !viewModel.customVpnConfig.isNullOrBlank()

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        try {
                            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                            if (!content.isNullOrBlank()) {
                                viewModel.saveCustomVpnConfig(context, content)
                                Toast.makeText(context, "WireGuard configuration imported successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to parse file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // VPN Status indicator row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnLock,
                                    contentDescription = "VPN Lock",
                                    tint = accentColor
                                )
                                Column {
                                    Text(stringResource(id = R.string.vpn_status_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    val statusText = when (vpnState) {
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected -> stringResource(id = R.string.vpn_status_connected)
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connecting -> stringResource(id = R.string.vpn_status_connecting)
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Disconnected -> stringResource(id = R.string.vpn_status_disconnected)
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Error -> stringResource(id = R.string.vpn_status_error_prefix, (vpnState as com.rebelroot.omni.privacy.VpnManager.VpnState.Error).message)
                                    }
                                    val statusColor = when (vpnState) {
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected -> Color(0xFF30D158) // Lime green
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connecting -> Color(0xFFFF9500) // Orange
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Disconnected -> textSecondaryColor
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Error -> Color(0xFFFF453A) // Red
                                    }
                                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Switch to quickly toggle connection state
                            if (hasConfig) {
                                Switch(
                                    checked = vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            viewModel.connectCustomVpn()
                                        } else {
                                            viewModel.disconnectVpn()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentColor
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = dividerColor)

                        // Configuration actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch("*/*")
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = "Upload Config",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.vpn_import_conf), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (hasConfig) {
                                Button(
                                    onClick = {
                                        viewModel.connectCustomVpn()
                                    },
                                    enabled = vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Disconnected || vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Error,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Start",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(id = R.string.vpn_connect), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (hasConfig && vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected) {
                            Button(
                                onClick = {
                                    viewModel.disconnectVpn()
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.vpn_disconnect), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. SEARCH ENGINE Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.search_engine_section),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(id = R.string.search_engine_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        
                        var expanded by remember { mutableStateOf(false) }
                        val engines = listOf("Google", "DuckDuckGo", "Brave", "Bing", "Custom")
                        val currentEngine = viewModel.selectedSearchEngine
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(inputBgColor, RoundedCornerShape(12.dp))
                                    .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(12.dp))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val icon = when (currentEngine) {
                                        "Google" -> Icons.Rounded.Search
                                        "DuckDuckGo" -> Icons.Rounded.Security
                                        "Brave" -> Icons.Rounded.Security
                                        "Bing" -> Icons.Rounded.Language
                                        else -> Icons.Rounded.Settings
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = accentColor
                                    )
                                    Text(
                                        text = currentEngine,
                                        color = textPrimaryColor,
                                        fontSize = 14.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = textSecondaryColor
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(cardColor)
                                    .border(BorderStroke(0.5.dp, cardBorderColor), RoundedCornerShape(8.dp))
                            ) {
                                engines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine, color = textPrimaryColor) },
                                        onClick = {
                                            viewModel.saveSearchEngine(context, engine)
                                            expanded = false
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = textPrimaryColor,
                                            trailingIconColor = accentColor
                                        ),
                                        trailingIcon = {
                                            if (currentEngine == engine) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = accentColor
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (currentEngine == "Custom") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.custom_query_template),
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            var customUrlText by remember(viewModel.customSearchUrl) { mutableStateOf(viewModel.customSearchUrl) }
                            
                            OutlinedTextField(
                                value = customUrlText,
                                onValueChange = {
                                    customUrlText = it
                                    viewModel.saveCustomSearchUrl(context, it)
                                },
                                placeholder = {
                                    Text("https://example.com/search?q=%s", color = textSecondaryColor)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(Icons.Rounded.Build, contentDescription = null, tint = textSecondaryColor)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimaryColor,
                                    unfocusedTextColor = textPrimaryColor,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = cardBorderColor,
                                    focusedContainerColor = inputBgColor,
                                    unfocusedContainerColor = inputBgColor
                                )
                            )
                            Text(
                                text = stringResource(id = R.string.custom_query_placeholder_desc),
                                color = textSecondaryColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 4. ABOUT Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.about_section),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val appVersionName = remember {
                            try {
                                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                pInfo.versionName ?: "1.0.6"
                            } catch (e: Exception) {
                                "1.0.6"
                            }
                        }

                        var isCheckingUpdate by remember { mutableStateOf(false) }
                        var updateResult by remember { mutableStateOf<BrowserViewModel.UpdateCheckResult?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = textSecondaryColor)
                            Text(stringResource(id = R.string.app_version_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(appVersionName, color = textSecondaryColor, fontSize = 13.sp)
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCheckingUpdate) {
                                    isCheckingUpdate = true
                                    viewModel.checkAppUpdates(context) { result ->
                                        isCheckingUpdate = false
                                        updateResult = result
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isCheckingUpdate) Icons.Rounded.Refresh else Icons.Rounded.Update,
                                contentDescription = null,
                                tint = accentColor
                            )
                            Text(
                                text = if (isCheckingUpdate) stringResource(id = R.string.checking_text) else stringResource(id = R.string.check_updates_title),
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isCheckingUpdate) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                            }
                        }

                        updateResult?.let { result ->
                            when (result) {
                                is BrowserViewModel.UpdateCheckResult.NewUpdateAvailable -> {
                                    AlertDialog(
                                        onDismissRequest = { updateResult = null },
                                        title = { Text(stringResource(id = R.string.update_dialog_title), color = textPrimaryColor) },
                                        text = { Text(stringResource(id = R.string.update_dialog_desc, result.versionName), color = textPrimaryColor) },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    updateResult = null
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result.playStoreUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.rebelroot.omni")).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(fallbackIntent)
                                                    }
                                                }
                                            ) {
                                                Text(stringResource(id = R.string.update_dialog_btn))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { updateResult = null }) {
                                                Text(stringResource(id = R.string.update_dialog_later), color = textSecondaryColor)
                                            }
                                        }
                                    )
                                }
                                is BrowserViewModel.UpdateCheckResult.NoUpdateAvailable -> {
                                    Toast.makeText(context, stringResource(id = R.string.update_no_update, appVersionName), Toast.LENGTH_SHORT).show()
                                    updateResult = null
                                }
                                is BrowserViewModel.UpdateCheckResult.Error -> {
                                    AlertDialog(
                                        onDismissRequest = { updateResult = null },
                                        title = { Text(stringResource(id = R.string.update_failed_title), color = textPrimaryColor) },
                                        text = { Text(stringResource(id = R.string.update_failed_desc, result.message), color = textPrimaryColor) },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    updateResult = null
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.rebelroot.omni")).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.rebelroot.omni")).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(fallbackIntent)
                                                    }
                                                }
                                            ) {
                                                Text(stringResource(id = R.string.update_dialog_open_store))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { updateResult = null }) {
                                                Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl("https://github.com/rebelroot")
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null, tint = accentColor)
                            Text(stringResource(id = R.string.support_github), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl("https://www.rebelroot.xyz/omnibrowser")
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = accentColor)
                            Text(stringResource(id = R.string.website_omnibrowser), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl("https://www.rebelroot.xyz/omnibrowser/privacy-policy")
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = accentColor)
                            Text(stringResource(id = R.string.privacy_policy_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showClearCacheConfirmation = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = accentColor)
                            Text("Clear Cache & Site Data", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = textSecondaryColor)
                        }
                        
                        if (isSignedIn) {
                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSignOutConfirmation = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, tint = Color(0xFFFF4444))
                                Text(stringResource(id = R.string.sign_out_title), color = Color(0xFFFF4444), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFFF4444))
                            }
                        }
                    }
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
}


