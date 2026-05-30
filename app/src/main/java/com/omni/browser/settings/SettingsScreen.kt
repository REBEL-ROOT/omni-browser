package com.omni.browser.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.lifecycle.viewModelScope
import com.omni.browser.browser.BrowserViewModel
import com.omni.browser.media.FFmpegLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    ffmpegLoader: FFmpegLoader,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ffmpegStatus by ffmpegLoader.status.collectAsState()

    // Local configuration states for Contabo VPS preset
    var vpsIp by remember { mutableStateOf("161.97.100.1") } // Mock Contabo IP example
    var vpsPrivateKey by remember { mutableStateOf("ClientPrivateKeyString==") }
    var vpsPublicKey by remember { mutableStateOf("ServerPublicKeyString==") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Core Protection (Adblock + Copy)
            SettingsCard(title = "Core Protection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🛡️ Ad Blocker (uBlock Origin)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Block ads and malicious scripts on pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = viewModel.isAdblockerEnabled,
                        onCheckedChange = { viewModel.toggleAdblock(context) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📋 Universal Text Copy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Force-enable text selection on restricted sites", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = viewModel.isUniversalCopyEnabled,
                        onCheckedChange = { viewModel.toggleUniversalCopy(context) }
                    )
                }

            }

            // Section 2: Media Remuxing (FFmpeg binaries)
            SettingsCard(title = "Media Engine (FFmpeg)") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Dynamic FFmpeg Binaries", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Used to remux and download HLS/DASH segment files offline.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    when (val status = ffmpegStatus) {
                        is FFmpegLoader.LoadStatus.NotInstalled -> {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        ffmpegLoader.downloadAndInstall()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download FFmpeg (~15MB)", fontSize = 12.sp)
                            }
                        }
                        is FFmpegLoader.LoadStatus.Downloading -> {
                            Column {
                                Text("Downloading: ${status.percent}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(
                                    progress = { status.percent / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        is FFmpegLoader.LoadStatus.Extracting -> {
                            Text("Extracting libraries...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                        is FFmpegLoader.LoadStatus.Installed -> {
                            Text("✅ FFmpeg Installed & Dynamic Loader Active", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                        is FFmpegLoader.LoadStatus.Error -> {
                            Text("❌ Error: ${status.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Section 3: Military-grade VPN (WireGuard & Contabo)
            SettingsCard(title = "Military-grade VPN (WireGuard)") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Contabo VPS WireGuard Configuration", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Quick-connect coordinates pointing to your high-performance VPS preset.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = vpsIp,
                        onValueChange = { vpsIp = it },
                        label = { Text("VPS Server IP", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = vpsPrivateKey,
                        onValueChange = { vpsPrivateKey = it },
                        label = { Text("Client Private Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = vpsPublicKey,
                        onValueChange = { vpsPublicKey = it },
                        label = { Text("Server Public Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.connectVpn(context, vpsIp, vpsPrivateKey, vpsPublicKey)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Connect VPN", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.disconnectVpn()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                RoundedCornerShape(12.dp)
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
