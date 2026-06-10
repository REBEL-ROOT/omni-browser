package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val isDarkMode = viewModel.isDarkThemeEnabled
    
    val bgColor = if (isDarkMode) Color(0xFF070A0F) else Color(0xFFF8F9FA)
    val cardColor = if (isDarkMode) Color(0xFF16222F) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) Color(0xFF23374A) else Color(0x1F000000)
    val textPrimaryColor = if (isDarkMode) Color.White else Color(0xFF202124)
    val textSecondaryColor = if (isDarkMode) Color(0xFF8E9AA8) else Color(0xFF606266)
    val dividerColor = if (isDarkMode) Color(0xFF23374A).copy(alpha = 0.5f) else Color(0x1F000000)
    val navBgColor = if (isDarkMode) Color(0xFF0D1620) else Color(0xFFFFFFFF)
    val navBorderColor = if (isDarkMode) Color(0xFF16222F).copy(alpha = 0.5f) else Color(0x1F000000)
    val navContentColor = if (isDarkMode) Color.White else Color(0xFF202124)
    val navContentMutedColor = if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color(0xFF202124).copy(alpha = 0.2f)
    val inputBgColor = if (isDarkMode) Color(0xFF070A0F) else Color(0xFFF2F3F5)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
        },
        bottomBar = {
            // Flat minimal bottom bar persisting exactly as requested in screenshots
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = navBgColor,
                border = BorderStroke(0.5.dp, navBorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = navContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = navContentMutedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = navContentMutedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, navContentColor, RoundedCornerShape(4.dp))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.tabs.size.toString(),
                            color = navContentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = navContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
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
            // 1. Profile Avatar Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        Toast.makeText(context, "Profile settings", Toast.LENGTH_SHORT).show()
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
                            .background(if (isDarkMode) Color(0xFF243647) else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Avatar",
                            tint = Color(0xFF0088FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alex Morgan",
                            color = textPrimaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "alex.morgan@omni.app",
                            color = textSecondaryColor,
                            fontSize = 11.sp
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = textSecondaryColor
                    )
                }
            }

            // 2. GENERAL Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GENERAL",
                    color = Color(0xFF0088FF),
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
                        // Row 1: Dark Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = Color(0xFF0088FF))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark Mode", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Use dark theme across the app", color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.saveDarkTheme(context, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0088FF)
                                )
                            )
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
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Color(0xFF0088FF))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifications", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enable push notifications", color = textSecondaryColor, fontSize = 11.sp)
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
                                    checkedTrackColor = Color(0xFF0088FF)
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
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = Color(0xFF0088FF))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Private Browsing", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Block trackers and cookies", color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = viewModel.isIncognitoMode,
                                onCheckedChange = { viewModel.toggleIncognitoMode(context) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0088FF)
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
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF0088FF))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Native Video Player", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Open videos in Omni Player with download", color = textSecondaryColor, fontSize = 11.sp)
                            }
                            Switch(
                                checked = viewModel.isNativePlayerEnabled,
                                onCheckedChange = { viewModel.toggleNativePlayer(context) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0088FF)
                                )
                            )
                        }
                    }
                }
            }

            // WireGuard VPN Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WIREGUARD VPN",
                    color = Color(0xFF0088FF),
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
                                    tint = Color(0xFF0088FF)
                                )
                                Column {
                                    Text("VPN Tunnel Status", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    val statusText = when (vpnState) {
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connected -> "Connected"
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Connecting -> "Connecting..."
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Disconnected -> "Disconnected"
                                        is com.rebelroot.omni.privacy.VpnManager.VpnState.Error -> "Error: ${(vpnState as com.rebelroot.omni.privacy.VpnManager.VpnState.Error).message}"
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
                                        checkedTrackColor = Color(0xFF0088FF)
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
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) Color(0xFF243647) else Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = "Upload Config",
                                    tint = Color(0xFF0088FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import .conf", color = Color(0xFF0088FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (hasConfig) {
                                Button(
                                    onClick = {
                                        viewModel.connectCustomVpn()
                                    },
                                    enabled = vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Disconnected || vpnState is com.rebelroot.omni.privacy.VpnManager.VpnState.Error,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Start",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connect", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                Text("Disconnect VPN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. SEARCH ENGINE Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SEARCH ENGINE",
                    color = Color(0xFF0088FF),
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
                        Text("Default Search Engine", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        
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
                                        tint = Color(0xFF0088FF)
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
                                            trailingIconColor = Color(0xFF0088FF)
                                        ),
                                        trailingIcon = {
                                            if (currentEngine == engine) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF0088FF)
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
                                text = "Custom Query Template URL",
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
                                    focusedBorderColor = Color(0xFF0088FF),
                                    unfocusedBorderColor = cardBorderColor,
                                    focusedContainerColor = inputBgColor,
                                    unfocusedContainerColor = inputBgColor
                                )
                            )
                            Text(
                                text = "Use %s as a placeholder for the search query.",
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
                    text = "ABOUT",
                    color = Color(0xFF0088FF),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = textSecondaryColor)
                            Text("App Version", color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("2.4.1", color = textSecondaryColor, fontSize = 13.sp)
                        }
                        
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.ExitToApp, contentDescription = null, tint = Color(0xFFFF4444))
                            Text("Sign Out", color = Color(0xFFFF4444), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFFF4444))
                        }
                    }
                }
            }
        }
    }
}
