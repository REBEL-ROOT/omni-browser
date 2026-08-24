package com.rebelroot.omni.sync.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rebelroot.omni.sync.coordinator.SyncCoordinator
import com.rebelroot.omni.sync.coordinator.SyncStatus
import com.rebelroot.omni.sync.crypto.PairingResult
import com.rebelroot.omni.tools.qrcode.BarcodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    coordinator: SyncCoordinator,
    onNavigateBack: () -> Unit
) {
    val uiState by coordinator.uiState.collectAsState()
    var showPairDialog by remember { mutableStateOf(false) }
    var showSasDialog by remember { mutableStateOf<String?>(null) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Omni Sync (Experimental)", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { coordinator.syncNow() }) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sync Now")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(uiState.deviceName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Fingerprint: " + uiState.fingerprint.take(12) + "...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Status: " + uiState.statusMessage, fontSize = 13.sp, color = when(uiState.syncStatus) {
                        SyncStatus.CONNECTED -> Color(0xFF4CAF50)
                        SyncStatus.SYNCING -> Color(0xFF2196F3)
                        SyncStatus.ERROR -> Color(0xFFF44336)
                        SyncStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val inv = coordinator.createPairingInvitation()
                    qrBitmap = BarcodeGenerator.generateQRCode(inv.toJson(), size = 400)
                    showPairDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pair New Device")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto-Sync on LAN", fontWeight = FontWeight.Medium)
                    Text("Sync automatically when on Wi-Fi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = uiState.autoSyncEnabled,
                    onCheckedChange = { coordinator.setAutoSyncEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Paired Devices (" + uiState.trustedDevices.size + ")", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.trustedDevices.isEmpty()) {
                Text("No devices paired yet. Tap 'Pair New Device' to connect another Omni browser.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                uiState.trustedDevices.forEach { dev ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(dev.deviceName, fontWeight = FontWeight.SemiBold)
                                Text("ID: " + dev.deviceId.take(8) + "...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { coordinator.revokeDevice(dev.deviceId) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ═══════════════════════════════════════════════
            // FIREFOX SYNC SECTION
            // ═══════════════════════════════════════════════
            Text("Firefox Sync", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Sync bookmarks, history, tabs & passwords with Firefox browsers via your Firefox Account.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.fxAccountEmail != null) {
                // Signed in state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(uiState.fxAccountEmail ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Firefox Account connected", fontSize = 12.sp, color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sync category toggles
                Text("What to Sync", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Note: These toggles use coordinator-level state. 
                // In production, wire these to MozillaSyncManager engine toggles.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bookmarks")
                    Switch(checked = uiState.fxSyncEnabled, onCheckedChange = { /* TODO */ })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("History")
                    Switch(checked = uiState.fxSyncEnabled, onCheckedChange = { /* TODO */ })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Open Tabs")
                    Switch(checked = uiState.fxSyncEnabled, onCheckedChange = { /* TODO */ })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saved Passwords")
                    Switch(checked = uiState.fxSyncEnabled, onCheckedChange = { /* TODO */ })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { coordinator.syncNow() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                    OutlinedButton(
                        onClick = { /* TODO: coordinator.fxLogout() */ },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sign Out")
                    }
                }
            } else {
                // Signed out state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Sign in with your Firefox Account to sync your data across all your devices.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { /* TODO: Open FxA login flow */ },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Firefox")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPairDialog) {
        Dialog(onDismissRequest = { showPairDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Pair Device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = { pairingCodeInput = it },
                        label = { Text("Or paste invitation code") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPairDialog = false }) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (pairingCodeInput.isNotBlank()) {
                                    val res = coordinator.processPairingInvitation(pairingCodeInput)
                                    if (res is PairingResult.Success) {
                                        showPairDialog = false
                                        showSasDialog = res.sasCode
                                    }
                                }
                            }
                        ) { Text("Pair") }
                    }
                }
            }
        }
    }

    showSasDialog?.let { sas ->
        AlertDialog(
            onDismissRequest = { showSasDialog = null },
            title = { Text("Confirm Security Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Verify this 6-digit code matches on both devices:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        sas.take(3) + " " + sas.takeLast(3),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSasDialog = null }) { Text("Codes Match") }
            }
        )
    }
}
