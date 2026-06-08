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
    var isDarkMode by remember { mutableStateOf(true) }
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

    var defaultHomepageText by remember { mutableStateOf("https://omni.app/home") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070A0F)
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, Color(0xFF16222F).copy(alpha = 0.2f))
                )
            )
        },
        bottomBar = {
            // Flat minimal bottom bar persisting exactly as requested in screenshots
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = Color(0xFF0D1620),
                border = BorderStroke(0.5.dp, Color(0xFF16222F).copy(alpha = 0.5f))
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
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {}, enabled = false) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.tabs.size.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = Color.White,
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
                .background(Color(0xFF070A0F)) // Obsidian black background
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
                color = Color(0xFF16222F), // Slate-navy
                border = BorderStroke(0.5.dp, Color(0xFF23374A))
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
                            .background(Color(0xFF243647)),
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
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "alex.morgan@omni.app",
                            color = Color(0xFF8E9AA8),
                            fontSize = 11.sp
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF8E9AA8)
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
                    color = Color(0xFF16222F),
                    border = BorderStroke(0.5.dp, Color(0xFF23374A))
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
                                Text("Dark Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Use dark theme across the app", color = Color(0xFF8E9AA8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { isDarkMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0088FF)
                                )
                            )
                        }
                        
                        HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                        
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
                                Text("Notifications", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Enable push notifications", color = Color(0xFF8E9AA8), fontSize = 11.sp)
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
                        
                        HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                        
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
                                Text("Private Browsing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Block trackers and cookies", color = Color(0xFF8E9AA8), fontSize = 11.sp)
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
                        
                        HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                        
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
                                Text("Native Video Player", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Open videos in Omni Player with download", color = Color(0xFF8E9AA8), fontSize = 11.sp)
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
                    color = Color(0xFF16222F),
                    border = BorderStroke(0.5.dp, Color(0xFF23374A))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Default homepage", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        
                        OutlinedTextField(
                            value = defaultHomepageText,
                            onValueChange = { defaultHomepageText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF8E9AA8))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF0088FF),
                                unfocusedBorderColor = Color(0xFF23374A),
                                focusedContainerColor = Color(0xFF070A0F),
                                unfocusedContainerColor = Color(0xFF070A0F)
                            )
                        )
                        
                        Button(
                            onClick = {
                                Toast.makeText(context, "Homepage saved!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
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
                    color = Color(0xFF16222F),
                    border = BorderStroke(0.5.dp, Color(0xFF23374A))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF8E9AA8))
                            Text("App Version", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("2.4.1", color = Color(0xFF8E9AA8), fontSize = 13.sp)
                        }
                        
                        HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                        
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
