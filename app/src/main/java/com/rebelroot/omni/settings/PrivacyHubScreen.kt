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

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.privacy.TorState
import com.rebelroot.omni.privacy.VpnManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyHubScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val vpnState by viewModel.vpnManager.state.collectAsState()
    val torState by viewModel.torManager.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.privacy_hub_title), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardColor)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Restart banner ──────────────────────────────────────────────
            if (viewModel.privacyRestartNeeded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(id = R.string.privacy_restart_banner),
                            color = Color(0xFFFF9500),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.restartApp(context) }) {
                            Text(stringResource(id = R.string.privacy_restart_now), fontSize = 11.sp, color = Color(0xFFFF9500), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.privacyRestartNeeded = false }) {
                            Text(stringResource(id = R.string.privacy_restart_dismiss), fontSize = 11.sp, color = textSecondaryColor)
                        }
                    }
                }
            }

            // ── CONNECTION ──────────────────────────────────────────────────
            SectionHeader(stringResource(id = R.string.privacy_hub_connection))
            ConnectionSection(
                viewModel = viewModel, vpnState = vpnState, torState = torState,
                textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor,
                cardColor = cardColor, cardBorderColor = cardBorderColor,
                dividerColor = dividerColor, accentColor = accentColor, context = context
            )

            // ── DNS ─────────────────────────────────────────────────────────
            SectionHeader(stringResource(id = R.string.privacy_hub_dns))
            DnsSection(
                viewModel = viewModel,
                textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor,
                cardColor = cardColor, cardBorderColor = cardBorderColor,
                dividerColor = dividerColor, accentColor = accentColor, context = context
            )

            // ── NETWORK ─────────────────────────────────────────────────────
            SectionHeader(stringResource(id = R.string.privacy_hub_network))
            NetworkSection(
                viewModel = viewModel,
                textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor,
                cardColor = cardColor, cardBorderColor = cardBorderColor,
                dividerColor = dividerColor, accentColor = accentColor, context = context
            )

            // ── IDENTITY ────────────────────────────────────────────────────
            SectionHeader(stringResource(id = R.string.privacy_hub_identity))
            IdentitySection(
                viewModel = viewModel,
                textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor,
                cardColor = cardColor, cardBorderColor = cardBorderColor,
                dividerColor = dividerColor, accentColor = accentColor, context = context
            )

            // ── FINGERPRINT ─────────────────────────────────────────────────
            SectionHeader(stringResource(id = R.string.privacy_hub_fingerprint))
            FingerprintSection(
                viewModel = viewModel,
                textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor,
                cardColor = cardColor, cardBorderColor = cardBorderColor,
                dividerColor = dividerColor, accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

/** Standard card wrapper used by every section. */
@Composable
private fun HubCard(
    cardColor: Color,
    cardBorderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(0.5.dp, cardBorderColor),
        content = { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    )
}

/** A labelled on/off row. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = textSecondaryColor, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
    }
}

// ── CONNECTION SECTION ────────────────────────────────────────────────────────

@Composable
private fun ConnectionSection(
    viewModel: BrowserViewModel,
    vpnState: VpnManager.VpnState,
    torState: TorState,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context
) {
    val selectedProvider = viewModel.proxyProvider
    var showCustomProxyDialog by remember { mutableStateOf(false) }
    var showVpnEditDialog by remember { mutableStateOf(false) }
    var configText by remember { mutableStateOf("") }

    // Hoisted so it survives provider switches while a pick is in flight.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)
                    ?.bufferedReader()?.use { r -> r.readText() }
                if (!content.isNullOrBlank()) {
                    viewModel.saveCustomVpnConfig(context, content)
                    Toast.makeText(context, context.getString(R.string.wg_import_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.wg_import_failed, e.message ?: "unknown"), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── 5-provider selector ─────────────────────────────────────────────────
    HubCard(cardColor, cardBorderColor) {
        listOf(
            "direct"       to stringResource(id = R.string.proxy_provider_direct),
            "wireguard"    to stringResource(id = R.string.proxy_provider_wireguard_unavailable),
            "tor"          to stringResource(id = R.string.proxy_provider_tor),
            "tor_over_vpn" to stringResource(id = R.string.proxy_provider_tor_over_vpn_unavailable),
            "custom_proxy" to stringResource(id = R.string.custom_proxy)
        ).forEach { (provider, label) ->
            val selected = selectedProvider == provider
            // WireGuard and Tor-over-VPN can establish a tunnel via the declared
            // VpnService in the manifest. The connect control inside the detail
            // card is enabled when a valid configuration has been imported/saved.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable {
                        when (provider) {
                            "custom_proxy" -> showCustomProxyDialog = true
                            "direct" -> {
                                viewModel.saveProxyProvider(context, provider)
                                viewModel.disconnectTor()
                            }
                            "tor" -> {
                                viewModel.saveProxyProvider(context, provider)
                                viewModel.connectTor()
                            }
                            "wireguard" -> {
                                // No tunnel can be brought up, so just record
                                // the choice and stop any active Tor routing.
                                viewModel.saveProxyProvider(context, provider)
                                viewModel.disconnectTor()
                            }
                            "tor_over_vpn" -> {
                                // Record the choice but do NOT start Tor here:
                                // without a VPN layer underneath this would be
                                // plain Tor, not Tor-over-VPN. The detail card
                                // explains the prerequisite and keeps the
                                // connect toggle disabled until a VPN is up.
                                viewModel.saveProxyProvider(context, provider)
                            }
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) accentColor.copy(alpha = 0.15f) else cardColor,
                border = BorderStroke(1.dp, if (selected) accentColor else cardBorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 14.dp)
                    ) {
                        Icon(
                            imageVector = when (provider) {
                                "direct" -> Icons.Rounded.Public
                                "wireguard" -> Icons.Rounded.VpnKey
                                "tor" -> Icons.Rounded.Security
                                "tor_over_vpn" -> Icons.Rounded.Lock
                                "custom_proxy" -> Icons.Rounded.SettingsEthernet
                                else -> Icons.Rounded.NetworkCheck
                            },
                            contentDescription = label,
                            tint = if (selected) accentColor else textSecondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = label,
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    if (selected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp).padding(end = 14.dp))
                    }
                }
            }
        }
    }

    // ── Detail card for the selected provider ───────────────────────────────
    when (selectedProvider) {
        "tor", "tor_over_vpn" -> TorDetailCard(
            viewModel, torState, vpnState, selectedProvider,
            textPrimaryColor, textSecondaryColor, cardColor, cardBorderColor, dividerColor, accentColor, context
        )
        "wireguard" -> WireGuardDetailCard(
            viewModel, vpnState,
            textPrimaryColor, textSecondaryColor, cardColor, cardBorderColor, dividerColor, accentColor, context,
            filePickerLauncher = { filePickerLauncher.launch("*/*") },
            onEditConfig = { configText = viewModel.customVpnConfig ?: ""; showVpnEditDialog = true }
        )
        "custom_proxy" -> CustomProxyDetailCard(
            viewModel, textPrimaryColor, textSecondaryColor, cardColor, cardBorderColor, accentColor,
            onEdit = { showCustomProxyDialog = true }
        )
        else -> DirectDetailCard(textPrimaryColor, textSecondaryColor, cardColor, cardBorderColor)
    }

    // ── Custom-proxy dialog ─────────────────────────────────────────────────
    if (showCustomProxyDialog) {
        // Local form state so the ViewModel is only mutated on save, not on
        // every keystroke. The port field uses a String so the user can clear
        // and retype without the value snapping back to 9050.
        var hostText by remember { mutableStateOf(viewModel.customSocksHost) }
        var portText by remember { mutableStateOf(viewModel.customSocksPort.toString()) }
        var portError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomProxyDialog = false },
            title = { Text(stringResource(id = R.string.custom_proxy), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = { hostText = it },
                        label = { Text(stringResource(id = R.string.custom_proxy_host_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = cardBorderColor)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.filter { c -> c.isDigit() }.take(5)
                            portError = false
                        },
                        label = { Text(stringResource(id = R.string.custom_proxy_port_label)) },
                        isError = portError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (portError) Color(0xFFFF453A) else accentColor,
                            unfocusedBorderColor = if (portError) Color(0xFFFF453A) else cardBorderColor
                        )
                    )
                    if (portError) {
                        Text(stringResource(id = R.string.custom_proxy_port_invalid), color = Color(0xFFFF453A), fontSize = 11.sp)
                    }
                    Text(text = stringResource(id = R.string.custom_proxy_desc), color = textSecondaryColor, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val host = hostText.trim()
                    val port = portText.toIntOrNull()
                    if (host.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.custom_proxy_host_empty), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (port == null || port !in 1..65535) {
                        portError = true
                        return@TextButton
                    }
                    viewModel.saveCustomSocksHost(context, host)
                    viewModel.saveCustomSocksPort(context, port)
                    viewModel.saveProxyProvider(context, "custom_proxy")
                    showCustomProxyDialog = false
                    Toast.makeText(context, context.getString(R.string.custom_proxy_saved_restart), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(id = R.string.save_text), color = accentColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomProxyDialog = false }) { Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor) }
            }
        )
    }

    // ── WireGuard config-edit dialog ────────────────────────────────────────
    if (showVpnEditDialog) {
        val hasConfig = !viewModel.customVpnConfig.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { showVpnEditDialog = false },
            title = { Text(stringResource(R.string.wg_config_title), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wg_config_hint), color = textSecondaryColor, fontSize = 12.sp)
                    OutlinedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 280.dp),
                        placeholder = { Text("[Interface]\nPrivateKey = ...\n[Peer]\nPublicKey = ...", color = textSecondaryColor.copy(alpha = 0.5f), fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = textPrimaryColor, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = cardBorderColor)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.saveCustomVpnConfig(context, configText); showVpnEditDialog = false; Toast.makeText(context, context.getString(R.string.wg_config_saved), Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
                    Text(stringResource(R.string.save_text), color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasConfig) {
                        TextButton(onClick = { viewModel.saveCustomVpnConfig(context, ""); showVpnEditDialog = false }) { Text(stringResource(R.string.clear_text), color = Color(0xFFFF453A)) }
                    }
                    TextButton(onClick = { showVpnEditDialog = false }) { Text(stringResource(id = R.string.cancel_text), color = textSecondaryColor) }
                }
            },
            containerColor = cardColor
        )
    }
}

// ── Connection detail cards ───────────────────────────────────────────────────

@Composable
private fun TorDetailCard(
    viewModel: BrowserViewModel,
    torState: TorState,
    vpnState: VpnManager.VpnState,
    selectedProvider: String,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context
) {
    val (statusText, statusColor) = when (torState) {
        is TorState.Connected -> stringResource(R.string.tor_status_connected) to Color(0xFF30D158)
        is TorState.Connecting -> stringResource(R.string.tor_connecting_waiting) to Color(0xFFFF9500)
        is TorState.Disconnected -> stringResource(R.string.tor_status_disconnected) to textSecondaryColor
        is TorState.Error -> { val e = (torState as TorState.Error).message; stringResource(R.string.tor_status_error_prefix, e) to Color(0xFFFF453A) }
        // Bootstrap % was previously synthesized from poll iteration count, not
        // real Tor bootstrap progress. Show an honest indeterminate state instead.
        is TorState.Bootstrap -> stringResource(R.string.tor_connecting_waiting) to Color(0xFFFF9500)
    }

    // A new circuit can only be requested when Tor is actually connected via
    // Orbot. Remote/custom SOCKS proxies have no control channel.
    val canNewCircuit = torState is TorState.Connected && viewModel.customSocksHost.isBlank()

    HubCard(cardColor, cardBorderColor) {
        // Tor-over-VPN prerequisite note
        if (selectedProvider == "tor_over_vpn" && vpnState !is VpnManager.VpnState.Connected) {
            Text(stringResource(R.string.tor_over_vpn_needs_vpn), color = Color(0xFFFF9500), fontSize = 11.sp)
            HorizontalDivider(color = dividerColor)
        }

        // Status + connect toggle. For Tor-over-VPN the toggle is disabled
        // until a VPN tunnel is actually up — otherwise turning it on would
        // start plain Tor and mislabel the connection as "over VPN".
        val torConnectEnabled = !(selectedProvider == "tor_over_vpn" && vpnState !is VpnManager.VpnState.Connected)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(R.string.tor_status_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Switch(
                checked = torState is TorState.Connected,
                enabled = torConnectEnabled,
                onCheckedChange = { on ->
                    if (on) { viewModel.connectTor(); Toast.makeText(context, context.getString(R.string.tor_connecting_toast), Toast.LENGTH_SHORT).show() }
                    else { viewModel.disconnectTor(); Toast.makeText(context, context.getString(R.string.tor_disconnected_toast), Toast.LENGTH_SHORT).show() }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
            )
        }

        // When Tor shows Connected, the SOCKS port is reachable but the Gecko
        // engine still routes traffic directly until the app is restarted.
        if (torState is TorState.Connected && viewModel.privacyRestartNeeded) {
            Text(stringResource(R.string.tor_connected_restart_hint), color = Color(0xFFFF9500), fontSize = 11.sp)
        }

        HorizontalDivider(color = dividerColor)

        ToggleRow(stringResource(R.string.tor_use_bridges), stringResource(R.string.tor_use_bridges_desc), viewModel.isTorUseBridges, onCheckedChange = { viewModel.saveTorUseBridges(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
        ToggleRow(stringResource(R.string.tor_auto_connect), stringResource(R.string.tor_auto_connect_desc), viewModel.isTorAutoConnect, onCheckedChange = { viewModel.saveTorAutoConnect(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)

        HorizontalDivider(color = dividerColor)

        // New Tor Circuit — opens Orbot so the user can tap "New Identity".
        // Disabled when Tor is not connected or when using a remote SOCKS proxy.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.new_tor_circuit), color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (canNewCircuit) stringResource(R.string.tor_new_circuit_hint) else stringResource(R.string.tor_new_circuit_disabled_hint),
                    color = textSecondaryColor, fontSize = 11.sp
                )
            }
            TextButton(
                onClick = { viewModel.requestNewCircuit() },
                enabled = canNewCircuit
            ) {
                Text(
                    stringResource(R.string.new_tor_circuit),
                    color = if (canNewCircuit) accentColor else textSecondaryColor,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = dividerColor)

        // Onion routing — informational only; active iff Tor is connected.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tor_onion_routing), color = textPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.tor_onion_active_desc), color = textSecondaryColor, fontSize = 11.sp)
            }
            Switch(checked = torState is TorState.Connected, enabled = false, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor))
        }
    }
}

@Composable
private fun WireGuardDetailCard(
    viewModel: BrowserViewModel,
    vpnState: VpnManager.VpnState,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context,
    filePickerLauncher: () -> Unit,
    onEditConfig: () -> Unit
) {
    val hasConfig = !viewModel.customVpnConfig.isNullOrBlank()
    val statusText = when (vpnState) {
        is VpnManager.VpnState.Connected -> stringResource(R.string.vpn_status_connected)
        is VpnManager.VpnState.Connecting -> stringResource(R.string.vpn_status_connecting)
        is VpnManager.VpnState.Disconnected -> stringResource(R.string.vpn_status_disconnected)
        is VpnManager.VpnState.Error -> { val e = (vpnState as VpnManager.VpnState.Error).message; stringResource(R.string.vpn_status_error_prefix, e) }
    }
    val statusColor = when (vpnState) {
        is VpnManager.VpnState.Connected -> Color(0xFF30D158)
        is VpnManager.VpnState.Connecting -> Color(0xFFFF9500)
        is VpnManager.VpnState.Disconnected -> textSecondaryColor
        is VpnManager.VpnState.Error -> Color(0xFFFF453A)
    }

    HubCard(cardColor, cardBorderColor) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.VpnLock, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(R.string.vpn_status_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            val vpnConnected = vpnState is VpnManager.VpnState.Connected
            val vpnConnecting = vpnState is VpnManager.VpnState.Connecting
            Switch(
                checked = vpnConnected || vpnConnecting,
                enabled = hasConfig,
                onCheckedChange = { on ->
                    if (on) {
                        viewModel.connectCustomVpn()
                        Toast.makeText(context, context.getString(R.string.vpn_connecting_toast), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.disconnectVpn()
                        Toast.makeText(context, context.getString(R.string.vpn_disconnected_toast), Toast.LENGTH_SHORT).show()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
            )
        }

        if (!hasConfig) {
            Text(stringResource(R.string.vpn_not_available_hint), color = Color(0xFFFF9500), fontSize = 11.sp)
        }

        HorizontalDivider(color = dividerColor)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = filePickerLauncher, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor.copy(alpha = 0.8f)), border = BorderStroke(1.dp, accentColor), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.vpn_import_conf), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onEditConfig, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor.copy(alpha = 0.8f)), border = BorderStroke(1.dp, accentColor), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (hasConfig) stringResource(R.string.wg_edit_config) else stringResource(R.string.wg_add_config), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomProxyDetailCard(
    viewModel: BrowserViewModel,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    accentColor: Color,
    onEdit: () -> Unit
) {
    val host = viewModel.customSocksHost
    val port = viewModel.customSocksPort
    HubCard(cardColor, cardBorderColor) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(R.string.wg_socks5_proxy), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (host.isNotBlank()) "$host:$port" else stringResource(R.string.wg_not_configured), color = if (host.isNotBlank()) Color(0xFF30D158) else textSecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.wg_edit), color = accentColor, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DirectDetailCard(textPrimaryColor: Color, textSecondaryColor: Color, cardColor: Color, cardBorderColor: Color) {
    HubCard(cardColor, cardBorderColor) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(24.dp))
            Column {
                Text(stringResource(R.string.direct_connection), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.direct_connection_desc), color = textSecondaryColor, fontSize = 11.sp)
            }
        }
    }
}

// ── DNS SECTION ───────────────────────────────────────────────────────────────

@Composable
private fun DnsSection(
    viewModel: BrowserViewModel,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context
) {
    var showDohDialog by remember { mutableStateOf(false) }

    HubCard(cardColor, cardBorderColor) {
        // DoH — fully functional via network.trr.* prefs.
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDohDialog = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.doh_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (viewModel.isDohEnabled) viewModel.dohUri else stringResource(R.string.dns_disabled),
                    color = textSecondaryColor, fontSize = 12.sp, maxLines = 1
                )
            }
            Switch(
                checked = viewModel.isDohEnabled,
                onCheckedChange = { viewModel.saveDohEnabled(context, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
            )
        }

        // DNS leak warning: DoH bypasses Tor's DNS routing. When both are
        // active, DNS queries go to the DoH provider instead of through the
        // Tor circuit, leaking the user's browsing destinations.
        if (viewModel.isDohEnabled && viewModel.proxyProvider == "tor") {
            Text(stringResource(R.string.dns_leak_warning), color = Color(0xFFFF453A), fontSize = 11.sp)
        }

        HorizontalDivider(color = dividerColor)

        // DoT — cannot be set from the app; open the system Private DNS page.
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Toast.makeText(context, context.getString(R.string.dot_open_settings), Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.dot_open_settings_failed), Toast.LENGTH_SHORT).show()
                }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dot_title), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.dot_system_hint), color = textSecondaryColor, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(18.dp))
        }
    }

    // DoH provider / custom-endpoint dialog
    if (showDohDialog) {
        var customUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDohDialog = false },
            title = { Text(stringResource(R.string.doh_title), color = textPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Presets
                    listOf(
                        "https://dns.google/dns-query" to "Google",
                        "https://cloudflare-dns.com/dns-query" to "Cloudflare",
                        "https://dns.quad9.net/dns-query" to "Quad9"
                    ).forEach { (uri, name) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.saveDohUri(context, uri)
                                if (!viewModel.isDohEnabled) viewModel.saveDohEnabled(context, true)
                                showDohDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewModel.dohUri == uri) accentColor.copy(alpha = 0.15f) else cardColor,
                            border = BorderStroke(1.dp, if (viewModel.dohUri == uri) accentColor else cardBorderColor)
                        ) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = textPrimaryColor, fontSize = 13.sp)
                                Text(uri, color = textSecondaryColor, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    HorizontalDivider(color = dividerColor)
                    // Custom DoH endpoint — any RFC 8484 compliant URL works in-engine.
                    Text(stringResource(R.string.doh_custom_endpoint), color = textPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("https://…/dns-query") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = cardBorderColor)
                    )
                    TextButton(onClick = {
                        val url = customUrl.trim()
                        if (!url.startsWith("https://")) { Toast.makeText(context, context.getString(R.string.doh_url_invalid), Toast.LENGTH_SHORT).show(); return@TextButton }
                        viewModel.saveDohUri(context, url)
                        if (!viewModel.isDohEnabled) viewModel.saveDohEnabled(context, true)
                        showDohDialog = false
                    }) { Text(stringResource(R.string.save_text), color = accentColor, fontWeight = FontWeight.Bold) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDohDialog = false }) { Text(stringResource(R.string.cancel_text), color = textSecondaryColor) }
            }
        )
    }
}

// ── NETWORK SECTION ───────────────────────────────────────────────────────────

@Composable
private fun NetworkSection(
    viewModel: BrowserViewModel,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context
) {
    HubCard(cardColor, cardBorderColor) {
        ToggleRow(stringResource(R.string.block_quic), stringResource(R.string.block_quic_desc), viewModel.isBlockQuic, onCheckedChange = { viewModel.saveBlockQuic(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
        HorizontalDivider(color = dividerColor)
        ToggleRow(stringResource(R.string.disable_webrtc), stringResource(R.string.disable_webrtc_desc), viewModel.isDisableWebrtc, onCheckedChange = { viewModel.saveDisableWebrtc(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
    }
}

// ── IDENTITY SECTION ──────────────────────────────────────────────────────────

@Composable
private fun IdentitySection(
    viewModel: BrowserViewModel,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color,
    context: android.content.Context
) {
    val clearCookiesText = stringResource(R.string.clear_cookies)

    HubCard(cardColor, cardBorderColor) {
        ToggleRow(stringResource(R.string.randomize_ua), stringResource(R.string.randomize_ua_desc), viewModel.isRandomizeUa, onCheckedChange = { viewModel.saveRandomizeUa(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
        HorizontalDivider(color = dividerColor)
        ToggleRow(stringResource(R.string.rotate_identity), stringResource(R.string.rotate_identity_desc), viewModel.isAutoRotateIdentity, onCheckedChange = { viewModel.saveAutoRotateIdentity(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
        HorizontalDivider(color = dividerColor)
        // Clear Cookies — clears only cookies + DOM storage, not logins/history.
        Surface(
            modifier = Modifier.fillMaxWidth().height(40.dp).clickable {
                viewModel.clearCookiesOnly()
                Toast.makeText(context, clearCookiesText, Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(10.dp), color = cardColor, border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(clearCookiesText, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── FINGERPRINT SECTION ───────────────────────────────────────────────────────

@Composable
private fun FingerprintSection(
    viewModel: BrowserViewModel,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    cardColor: Color,
    cardBorderColor: Color,
    dividerColor: Color,
    accentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    HubCard(cardColor, cardBorderColor) {
        ToggleRow(stringResource(R.string.fingerprint_protection), stringResource(R.string.fingerprint_protection_desc), viewModel.isFingerprintProtection, onCheckedChange = { viewModel.saveFingerprintProtection(context, it) }, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor, accentColor = accentColor)
        HorizontalDivider(color = dividerColor)
        // Per-site container isolation is not exposed by GeckoView prefs.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.temp_containers), color = textPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.feature_not_available), color = textSecondaryColor, fontSize = 12.sp)
            }
            Switch(checked = false, enabled = false, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor))
        }
    }
}
