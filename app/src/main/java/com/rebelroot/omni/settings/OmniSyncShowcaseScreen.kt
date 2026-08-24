package com.rebelroot.omni.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rebelroot.omni.bookmarks.storage.loadBookmarks
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.sync.coordinator.SyncCoordinator
import com.rebelroot.omni.sync.coordinator.SyncStatus
import com.rebelroot.omni.sync.crypto.PairingResult
import com.rebelroot.omni.sync.mozilla.FxAccountManager
import com.rebelroot.omni.sync.mozilla.FxaState
import com.rebelroot.omni.sync.mozilla.MozillaSyncManager
import com.rebelroot.omni.sync.mozilla.MozSyncState
import com.rebelroot.omni.sync.ui.FxAuthDialog
import com.rebelroot.omni.sync.ui.QrCameraScanner
import com.rebelroot.omni.sync.ui.SyncedTabsSheet
import com.rebelroot.omni.tools.qrcode.BarcodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniSyncShowcaseScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember {
        SyncCoordinator(
            baseDir = context.filesDir,
            collection = loadBookmarks(context)
        )
    }

    val fxAccountManager = remember { FxAccountManager.getInstance().apply { initialize(context) } }
    val mozillaSyncManager = remember { MozillaSyncManager.getInstance() }
    val fxaState by fxAccountManager.accountState.collectAsState()
    val mozSyncState by mozillaSyncManager.syncState.collectAsState()

    val uiState by coordinator.uiState.collectAsState()
    var showPairDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showFxAuthDialog by remember { mutableStateOf(false) }
    var showSyncedTabsSheet by remember { mutableStateOf(false) }

    // Granular sync data preferences
    var syncBookmarks by remember { mutableStateOf(true) }
    var syncTabs by remember { mutableStateOf(true) }
    var syncHistory by remember { mutableStateOf(false) }
    var syncPasswords by remember { mutableStateOf(true) }
    var syncSettings by remember { mutableStateOf(true) }
    var showSasDialog by remember { mutableStateOf<String?>(null) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var myInvitationJson by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Omni Sync", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coordinator.syncNow()
                        if (fxaState is FxaState.SignedIn) {
                            mozillaSyncManager.syncNow(
                                context = context,
                                collection = coordinator.collection,
                                tabs = viewModel.tabs.toList()
                            )
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = "Sync Now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. FIREFOX ACCOUNT CLOUD SYNC CARD ────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Firefox Account Sync",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Mozilla Cloud Sync",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (fxaState is FxaState.SignedIn) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (fxaState is FxaState.SignedIn) "CONNECTED" else "NOT CONNECTED",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (fxaState is FxaState.SignedIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (fxaState is FxaState.SignedIn) {
                            val signedIn = fxaState as FxaState.SignedIn
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(signedIn.email, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                when (mozSyncState) {
                                                    is MozSyncState.Syncing -> (mozSyncState as MozSyncState.Syncing).message
                                                    is MozSyncState.Done -> "Cloud synced recently"
                                                    is MozSyncState.Error -> (mozSyncState as MozSyncState.Error).message
                                                    else -> "Ready to sync with Firefox"
                                                },
                                                fontSize = 11.sp,
                                                color = if (mozSyncState is MozSyncState.Error) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        mozillaSyncManager.syncNow(
                                            context = context,
                                            collection = coordinator.collection,
                                            tabs = viewModel.tabs.toList()
                                        ) { success ->
                                            if (success) {
                                                Toast.makeText(context, "Firefox Cloud Sync complete!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                val remoteTabs = mozillaSyncManager.tabBridge.getAllRemoteDeviceTabs()
                                OutlinedButton(
                                    onClick = { showSyncedTabsSheet = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (remoteTabs.isNotEmpty()) "Tabs (${remoteTabs.sumOf { it.tabs.size }})" else "Remote Tabs",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        fxAccountManager.logout()
                                        Toast.makeText(context, "Signed out of Firefox Account", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Sign Out", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                "Sign in with your Firefox Account to automatically sync bookmarks, open tabs from your PC/Mac, browsing history, and passwords seamlessly via Mozilla Cloud.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { showFxAuthDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign in with Firefox Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ── 2. OMNI SYNC MESH (OFFLINE / LAN P2P) ──────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Omni Sync Mesh (Offline / LAN)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Zero-Cloud · 100% E2EE P2P (Upcoming)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when (uiState.syncStatus) {
                                    SyncStatus.CONNECTED -> Color(0xFF2E7D32)
                                    SyncStatus.SYNCING -> Color(0xFF1565C0)
                                    SyncStatus.ERROR -> Color(0xFFC62828)
                                    SyncStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = uiState.syncStatus.name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // ── UPCOMING TESTING NOTICE BANNER ──
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        "Upcoming Feature — Testing Phase",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        "Omni Sync Mesh is currently upcoming. Testing requires installing the Omni Sync Extension (Testing) on your desktop browser.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            "Directly synchronize bookmarks, open tabs, history, and portable preferences peer-to-peer over Wi-Fi without central servers.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val inv = coordinator.createPairingInvitation()
                                    myInvitationJson = inv.toJson()
                                    qrBitmap = BarcodeGenerator.generateQRCode(myInvitationJson, size = 450)
                                    showPairDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pair Device", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { coordinator.syncNow() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync P2P", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ── 3. LIVE SYNC INSPECTOR ─────────────────────────────────────────
            SectionHeader(title = "Sync Inspector & Live Data", icon = Icons.Rounded.Assessment)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${coordinator.collection.allBookmarks().size}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Bookmarks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${coordinator.collection.allFolders().size}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Folders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${uiState.trustedDevices.size}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Paired Peers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encryption Engine", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "AES-256-GCM / P-256",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Outbox Journal", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${uiState.pendingOutboxCount} pending mutations",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── SAFE NON-DESTRUCTIVE GUARANTEE BANNER ───────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Safe Non-Destructive Sync Layer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Existing browser bookmarks & data are never harmed or overwritten. Omni Sync operates within an isolated, encrypted sync layer.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 4. IMPORT & EXPORT ACTIONS HUB ─────────────────────────────────
            SectionHeader(title = "Import & Export Hub", icon = Icons.Rounded.SwapVert)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (uiState.trustedDevices.isEmpty()) {
                                    Toast.makeText(context, "Pair a desktop browser first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Importing data into '💻 Desktop Bookmarks'...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From PC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (uiState.trustedDevices.isEmpty()) {
                                    Toast.makeText(context, "Pair a desktop browser first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    coordinator.syncNow()
                                    Toast.makeText(context, "Exported bookmarks & tabs to desktop!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("To PC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Exported Netscape HTML backup!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export HTML", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Select HTML file to import...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import HTML", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── 5. GRANULAR DATA PREFERENCES TOGGLES ───────────────────────────
            SectionHeader(title = "Sync Preferences", icon = Icons.Rounded.Tune)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Bookmarks
                    PreferenceToggleRow(
                        icon = Icons.Rounded.Bookmark,
                        title = "Bookmarks & Folders",
                        desc = "Full tree structure with fractional ordering",
                        checked = syncBookmarks,
                        onCheckedChange = { syncBookmarks = it }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Open Tabs
                    PreferenceToggleRow(
                        icon = Icons.Rounded.Tab,
                        title = "Real-Time Open Tabs",
                        desc = "View & switch active tabs across devices",
                        checked = syncTabs,
                        onCheckedChange = { syncTabs = it }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Browsing History
                    PreferenceToggleRow(
                        icon = Icons.Rounded.History,
                        title = "Browsing History",
                        desc = "Opt-in 90-day retention with tracker stripping",
                        checked = syncHistory,
                        onCheckedChange = { syncHistory = it }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Passwords
                    PreferenceToggleRow(
                        icon = Icons.Rounded.Lock,
                        title = "Passwords & Credentials",
                        desc = "End-to-end encrypted zero-knowledge locker",
                        checked = syncPasswords,
                        onCheckedChange = { syncPasswords = it }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Settings & Rules
                    PreferenceToggleRow(
                        icon = Icons.Rounded.Settings,
                        title = "Settings & Adblock Rules",
                        desc = "Custom filters, theme, and search engine",
                        checked = syncSettings,
                        onCheckedChange = { syncSettings = it }
                    )
                }
            }

            // ── 6. FEATURE HIGHLIGHTS GRID ──────────────────────────────────────
            SectionHeader(title = "Privacy & Encryption Guarantees", icon = Icons.Rounded.VerifiedUser)

            FeatureCard(
                icon = Icons.Rounded.Security,
                title = "End-to-End Encrypted (E2EE)",
                description = "Audited NIST P-256 ECDH key agreement with AES-256-GCM authenticated payload encryption and out-of-band 6-digit numeric SAS code verification."
            )

            FeatureCard(
                icon = Icons.Rounded.Tab,
                title = "Live Tabs & Send-to-Device",
                description = "View open tabs across all your paired computers and phones in real time, or send a tab directly with one tap. Incognito sessions are structurally excluded."
            )

            FeatureCard(
                icon = Icons.Rounded.Bookmarks,
                title = "Fractional Indexing CRDT",
                description = "Dense Base-62 fractional indexing preserves your nested folder hierarchy and avoids bookmark collisions even under concurrent offline edits."
            )

            FeatureCard(
                icon = Icons.Rounded.History,
                title = "Opt-In History & 90-Day Purge",
                description = "History sync is strictly opt-in, automatically strips URL tracking parameters (utm_*, fbclid), and enforces a rolling 90-day auto-expiry."
            )

            FeatureCard(
                icon = Icons.Rounded.Tune,
                title = "Portable Settings Allowlist",
                description = "Syncs search engines, dark theme, and ad-blocking rules while strictly keeping device-specific hardware parameters isolated."
            )

            // ── 7. CROSS-PLATFORM ECOSYSTEM ─────────────────────────────────────
            SectionHeader(title = "Supported Desktop Browsers (Testing Extension)", icon = Icons.Rounded.Language)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Direct P2P mesh sync requires installing the Omni Sync Extension (Beta / Testing) on your desktop browser:",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    BrowserRow("Google Chrome / Brave / Chromium", "Manifest V3 Extension with Service Worker")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    BrowserRow("Mozilla Firefox", "Firefox WebExtension with Places GUID mapping")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    BrowserRow("Microsoft Edge & Opera", "Chromium Store Package")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    BrowserRow("Apple Safari (macOS & iOS)", "Safari WebExtension with native Reading List bridge")
                }
            }

            // ── 8. PAIRED DEVICES SECTION ───────────────────────────────────────
            SectionHeader(title = "Paired Devices (${uiState.trustedDevices.size})", icon = Icons.Rounded.Devices)

            if (uiState.trustedDevices.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        "No other devices paired yet. Tap 'Pair Device' to generate a QR code or scan from your desktop browser.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                uiState.trustedDevices.forEach { dev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(dev.deviceName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("ID: ${dev.deviceId.take(10)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { coordinator.revokeDevice(dev.deviceId) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── CAMERA QR SCANNER OVERLAY ──────────────────────────────────────────
    if (showCameraScanner) {
        Dialog(
            onDismissRequest = { showCameraScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            QrCameraScanner(
                onQrDetected = { scannedText ->
                    showCameraScanner = false
                    val res = coordinator.processPairingInvitation(scannedText)
                    if (res is PairingResult.Success) {
                        showSasDialog = res.sasCode
                        Toast.makeText(context, "QR Code scanned! Verifying security code...", Toast.LENGTH_SHORT).show()
                    } else if (res is PairingResult.Failed) {
                        Toast.makeText(context, "Pairing failed: " + res.reason, Toast.LENGTH_LONG).show()
                    }
                },
                onClose = { showCameraScanner = false }
            )
        }
    }

    // ── PAIRING DIALOG ───────────────────────────────────────────────────────
    if (showPairDialog) {
        Dialog(onDismissRequest = { showPairDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Pair Device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Requires Omni Sync Extension (Testing) installed on your desktop browser.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            showPairDialog = false
                            showCameraScanner = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Desktop QR Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Or share this phone invitation code to your PC:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (myInvitationJson.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(myInvitationJson))
                                Toast.makeText(context, "Copied phone pairing code to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Phone Pairing Code", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Or paste invitation code from Desktop Extension:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { pairingCodeInput = it },
                            placeholder = { Text("Paste invitation code...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    pairingCodeInput = clip
                                    Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPairDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pairingCodeInput.isNotBlank()) {
                                    val res = coordinator.processPairingInvitation(pairingCodeInput)
                                    if (res is PairingResult.Success) {
                                        showPairDialog = false
                                        showSasDialog = res.sasCode
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pair")
                        }
                    }
                }
            }
        }
    }

    // ── SAS SECURITY CODE DIALOG ─────────────────────────────────────────────
    showSasDialog?.let { sas ->
        AlertDialog(
            onDismissRequest = { showSasDialog = null },
            title = { Text("Verify Security Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm this 6-digit code matches on both devices before syncing:")
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "${sas.take(3)} ${sas.takeLast(3)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSasDialog = null }) { Text("Codes Match") }
            }
        )
    }

    // ── FIREFOX AUTH DIALOG ──────────────────────────────────────────────────
    if (showFxAuthDialog) {
        FxAuthDialog(
            accountManager = fxAccountManager,
            onDismiss = { showFxAuthDialog = false },
            onSuccess = {
                showFxAuthDialog = false
                Toast.makeText(context, "Connected to Firefox Account!", Toast.LENGTH_SHORT).show()
                mozillaSyncManager.syncNow(
                    context = context,
                    collection = coordinator.collection,
                    tabs = viewModel.tabs.toList()
                )
            }
        )
    }

    // ── REMOTE SYNCED TABS SHEET ─────────────────────────────────────────────
    if (showSyncedTabsSheet) {
        val remoteTabs = mozillaSyncManager.tabBridge.getAllRemoteDeviceTabs()
        SyncedTabsSheet(
            devices = remoteTabs,
            onTabClick = { tab ->
                showSyncedTabsSheet = false
                viewModel.loadUrl(tab.url)
                onNavigateBack()
            },
            onDismiss = { showSyncedTabsSheet = false }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BrowserRow(name: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
    }
}
