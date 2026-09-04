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

package com.rebelroot.omni.browser

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import com.rebelroot.omni.browser.useragent.UserAgentPreset
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.viewinterop.AndroidView

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView
import com.rebelroot.omni.R
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.privacy.FireButton
import com.rebelroot.omni.tools.qrcode.BarcodeGenerator
import android.graphics.Bitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
 import androidx.compose.foundation.gestures.rememberTransformableState
 import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


// ── Android OS permission rationale dialog ────────────────────────────────────
// Shown BEFORE the Android system permission dialog to explain why access
// is needed — matches Chrome/Firefox "pre-permission prompt" UX pattern.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPermissionRationaleDialog(
    request: com.rebelroot.omni.browser.SystemPermissionRequest,
    onProceed: () -> Unit,
    onDeny: () -> Unit
) {
    val accent     = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurface  = MaterialTheme.colorScheme.onSurface
    val warnColor  = Color(0xFFF59E0B)

    val icon = when {
        request.rationaleTitle.contains("Camera") && request.rationaleTitle.contains("Micro") -> Icons.Rounded.Videocam
        request.rationaleTitle.contains("Camera")     -> Icons.Rounded.CameraAlt
        request.rationaleTitle.contains("Micro")      -> Icons.Rounded.Mic
        request.rationaleTitle.contains("Location")   -> Icons.Rounded.LocationOn
        else                                          -> Icons.Rounded.Security
    }

    ModalBottomSheet(
        onDismissRequest = onDeny,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant,
                width = 32.dp, height = 3.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = request.rationaleTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = request.rationaleBody,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            // Risk note
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = warnColor.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = warnColor, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(
                        "Android will ask you to grant this permission. You can revoke it anytime in System Settings → Apps.",
                        fontSize = 12.sp, color = warnColor, lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, errorColor.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = errorColor)
            ) {
                Icon(Icons.Rounded.Block, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Don't allow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebExtensionDownloadConfirmationDialog(
    prompt: BrowserViewModel.PendingWebExtensionDownload,
    onDismiss: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    AlertDialog(
        onDismissRequest = {
            prompt.onCancel()
            onDismiss()
        },
        containerColor = surfaceColor,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.download_confirmation_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.download_confirmation_desc, prompt.extensionName ?: prompt.extensionId),
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.download_file_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prompt.filename, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.download_type_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prompt.mimeType, fontSize = 12.sp, color = onSurface)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.download_source_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val host = try { android.net.Uri.parse(prompt.sourceUrl).host ?: prompt.extensionId } catch (e: Exception) { prompt.extensionId }
                            Text(host, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prompt.onConfirm()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(stringResource(R.string.download_btn), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prompt.onCancel()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.cancel_text))
            }
        }
    )
}

@Composable
fun PermissionPromptDialog(
    prompt: com.rebelroot.omni.browser.ContentPermissionPrompt,
    isDarkThemeEnabled: Boolean
) {
    val GEO  = org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION
    val NOTIF= org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
    val DRM  = org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS

    val icon = when (prompt.permissionType) {
        GEO   -> Icons.Rounded.LocationOn
        NOTIF -> Icons.Rounded.NotificationsActive
        DRM   -> Icons.Rounded.VpnKey
        8     -> Icons.Rounded.Storage
        else  -> Icons.Rounded.Info
    }
    val title = when (prompt.permissionType) {
        GEO   -> "Location Access"
        NOTIF -> "Notification Access"
        DRM   -> "DRM Media Access"
        8     -> "Storage Access"
        else  -> "Permission Request"
    }
    val description = when (prompt.permissionType) {
        GEO   -> "wants to access your precise physical location."
        NOTIF -> "wants to send you push notifications."
        DRM   -> "wants to verify device DRM keys for secure HD playback."
        8     -> "wants to access local storage for offline content."
        else  -> "is requesting a browser permission."
    }
    val risk = when (prompt.permissionType) {
        GEO   -> "Your location reveals where you are physically. Only allow trusted sites."
        NOTIF -> "Notifications can be used for spam. Only allow sites you actively use."
        else  -> null
    }

    // Extract clean hostname for display
    val host = try {
        android.net.Uri.parse(prompt.siteUri).host?.removePrefix("www.") ?: prompt.siteUri
    } catch (_: Exception) { prompt.siteUri }

    PermissionSheet(
        icon = icon,
        title = title,
        host = host,
        description = description,
        riskNote = risk,
        onAllow    = prompt.onAllow,
        onAllowOnce = prompt.onAllowOnce,
        onDeny     = prompt.onDeny
    )
}

@Composable
fun MediaPermissionPromptDialog(
    prompt: com.rebelroot.omni.browser.MediaPermissionPrompt,
    isDarkThemeEnabled: Boolean
) {
    val title = when {
        prompt.hasVideo && prompt.hasAudio -> "Camera & Microphone"
        prompt.hasVideo -> "Camera Access"
        else            -> "Microphone Access"
    }
    val description = when {
        prompt.hasVideo && prompt.hasAudio -> "wants to use your camera and microphone."
        prompt.hasVideo -> "wants to use your camera."
        else            -> "wants to use your microphone."
    }
    val icon = when {
        prompt.hasVideo && prompt.hasAudio -> Icons.Rounded.Videocam
        prompt.hasVideo -> Icons.Rounded.CameraAlt
        else            -> Icons.Rounded.Mic
    }
    val risk = "Audio/video capture is sensitive. Only allow trusted sites like video-calling services."

    val host = try {
        android.net.Uri.parse(prompt.siteUri).host?.removePrefix("www.") ?: prompt.siteUri
    } catch (_: Exception) { prompt.siteUri }

    val bestVideo = prompt.videoSources?.firstOrNull()
    val bestAudio = prompt.audioSources?.firstOrNull()

    PermissionSheet(
        icon = icon,
        title = title,
        host = host,
        description = description,
        riskNote = risk,
        onAllow     = { prompt.onAllow(bestVideo, bestAudio) },
        onAllowOnce = { prompt.onAllowOnce(bestVideo, bestAudio) },
        onDeny      = prompt.onDeny
    )
}

// ── Shared permission bottom-sheet UI ────────────────────────────────────────
// Matches Chrome/Brave design: site origin at top, permission icon + description,
// risk note in amber, three clear actions: Allow / Allow Once / Deny.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    host: String,
    description: String,
    riskNote: String?,
    onAllow: () -> Unit,
    onAllowOnce: () -> Unit,
    onDeny: () -> Unit
) {
    val accent      = MaterialTheme.colorScheme.primary
    val errorColor  = MaterialTheme.colorScheme.error
    val surfaceVar  = MaterialTheme.colorScheme.surfaceVariant
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar= MaterialTheme.colorScheme.onSurfaceVariant
    val warnColor   = Color(0xFFF59E0B)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDeny,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant,
                width = 32.dp, height = 3.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission icon in tonal circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Site origin — prominent, styled like Chrome
            Text(
                text = host,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$description",
                fontSize = 14.sp,
                color = onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Risk note — amber warning like Brave
            if (riskNote != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = warnColor.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = warnColor,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = riskNote,
                            fontSize = 12.sp,
                            color = warnColor,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Allow (remember)
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Allow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Allow once (session only) — tonal
            FilledTonalButton(
                onClick = onAllowOnce,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accent.copy(alpha = 0.10f),
                    contentColor = accent
                ),
                elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Allow this time only", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Deny (remember)
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, errorColor.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = errorColor)
            ) {
                Icon(Icons.Rounded.Block, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Don't allow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun EqualizerIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val animHeights = (0..3).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 350 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "height_$index"
        )
    }

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        animHeights.forEach { animHeight ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight(animHeight.value)
                    .background(color, shape = RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
fun LanguageDropdownSelector(
    label: String,
    selectedLanguageName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    languages: List<Pair<String, String>>,
    onLanguageSelected: (Pair<String, String>) -> Unit
) {
    Box(modifier = Modifier.width(135.dp)) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF23374A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedLanguageName,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 240.dp).background(Color(0xFF16222F))
        ) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.first, color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        onLanguageSelected(lang)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var showSnifferSubDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Native Player Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Switch 1: Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Native Player", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Play supported web videos in high-performance native media view", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isNativePlayerEnabled,
                        onCheckedChange = { viewModel.toggleNativePlayer(context) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 1.5: Media Sniffer / Fetcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clickable { showSnifferSubDialog = true }
                    ) {
                        Text("Media Sniffer / Fetcher", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Detect web page videos and display sniffer banner at top of site", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSnifferSubDialog = true }) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Sniffer Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Switch(
                            checked = viewModel.isMediaGrabberEnabled,
                            onCheckedChange = { viewModel.toggleMediaGrabber(context) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 2: Auto-Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Start playback automatically when video opens", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerAutoPlayEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "autoplay", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 3: Loop Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Loop Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Repeat video playback in a loop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerLoopEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "loop", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 4: Brightness Gesture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Brightness Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Swipe vertically on the left half to adjust brightness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerBrightnessGestureEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "brightness_gesture", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 5: Volume Gesture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Volume Gestures", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Swipe vertically on the right half to adjust volume", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerVolumeGestureEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "volume_gesture", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 6: Resume Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resume Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Remember video position and resume where you left off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerResumePlaybackEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "resume", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 7: Background Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Continue playing audio when app is minimized", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = viewModel.isPlayerBackgroundPlaybackEnabled,
                        onCheckedChange = { viewModel.savePlayerSetting(context, "background", it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Quality Dropdown

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Quality Limit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Maximum resolution to select automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var expandedQuality by remember { mutableStateOf(false) }
                    val qualities = listOf("Auto", "360p", "480p", "720p", "1080p")
                    Box {
                        TextButton(onClick = { expandedQuality = true }) {
                            Text(
                                text = viewModel.playerDefaultQuality,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        DropdownMenu(
                            expanded = expandedQuality,
                            onDismissRequest = { expandedQuality = false }
                        ) {
                            qualities.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q) },
                                    onClick = {
                                        viewModel.savePlayerSetting(context, "quality", q)
                                        expandedQuality = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showSnifferSubDialog) {
        MediaSnifferSettingsDialog(
            viewModel = viewModel,
            onDismissRequest = { showSnifferSubDialog = false }
        )
    }
}

@Composable
fun MediaSnifferSettingsDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var newDomainInput by remember { mutableStateOf("") }
    val currentHost = remember(viewModel.currentUrl) {
        try {
            android.net.Uri.parse(viewModel.currentUrl).host?.lowercase()
                ?.removePrefix("www.") ?: ""
        } catch (_: Exception) { "" }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.media_sniffer_settings_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Switch 1: Enable Media Sniffer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.media_sniffer_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.media_sniffer_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.isMediaGrabberEnabled,
                        onCheckedChange = { viewModel.toggleMediaGrabber(context) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Switch 2: Enable on YouTube
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable on YouTube",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Capture video streams on YouTube and Google domains",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.isYouTubeEnabled,
                        onCheckedChange = { viewModel.toggleYouTube(context) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Minimum Detection Size
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.media_sniffer_min_size_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.media_sniffer_min_size_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val durations = listOf(
                        0 to stringResource(R.string.media_sniffer_min_size_off),
                        15 to "15 sec+",
                        30 to "30 sec+",
                        60 to "1 min+",
                        900 to "15 min+",
                        1800 to "30 min+"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durations.forEach { (durSec, label) ->
                            val isSelected = viewModel.mediaSnifferMinDurationSec == durSec
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setMediaSnifferMinDurationSec(context, durSec) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Website Blocklist
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.media_sniffer_blocklist_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.media_sniffer_blocklist_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Add current site chip if available and not blocked
                    if (currentHost.isNotEmpty() && !viewModel.mediaSnifferBlocklist.contains(currentHost)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.addDomainToMediaSnifferBlocklist(context, currentHost)
                            },
                            label = {
                                Text(
                                    stringResource(R.string.media_sniffer_add_current_site, currentHost),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }

                    // Domain input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text(stringResource(R.string.media_sniffer_add_domain_hint), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newDomainInput.isNotBlank()) {
                                    viewModel.addDomainToMediaSnifferBlocklist(context, newDomainInput)
                                    newDomainInput = ""
                                }
                            },
                            enabled = newDomainInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.media_sniffer_add_domain_btn), fontSize = 12.sp)
                        }
                    }

                    // Blocked domains list
                    if (viewModel.mediaSnifferBlocklist.isEmpty()) {
                        Text(
                            text = stringResource(R.string.media_sniffer_no_blocked_sites),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            viewModel.mediaSnifferBlocklist.forEach { domain ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = domain,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeDomainFromMediaSnifferBlocklist(context, domain) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            TextButton(onClick = onDismissRequest) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

val BlackholeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FourBoxGrid",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Top-Left Box
        path(fill = SolidColor(Color.White)) {
            moveTo(6.5f, 4f)
            lineTo(8.5f, 4f)
            quadTo(11f, 4f, 11f, 6.5f)
            lineTo(11f, 8.5f)
            quadTo(11f, 11f, 8.5f, 11f)
            lineTo(6.5f, 11f)
            quadTo(4f, 11f, 4f, 8.5f)
            lineTo(4f, 6.5f)
            quadTo(4f, 4f, 6.5f, 4f)
            close()
        }
        // Top-Right Box
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 4f)
            lineTo(17.5f, 4f)
            quadTo(20f, 4f, 20f, 6.5f)
            lineTo(20f, 8.5f)
            quadTo(20f, 11f, 17.5f, 11f)
            lineTo(15.5f, 11f)
            quadTo(13f, 11f, 13f, 8.5f)
            lineTo(13f, 6.5f)
            quadTo(13f, 4f, 15.5f, 4f)
            close()
        }
        // Bottom-Left Box
        path(fill = SolidColor(Color.White)) {
            moveTo(6.5f, 13f)
            lineTo(8.5f, 13f)
            quadTo(11f, 13f, 11f, 15.5f)
            lineTo(11f, 17.5f)
            quadTo(11f, 20f, 8.5f, 20f)
            lineTo(6.5f, 20f)
            quadTo(4f, 20f, 4f, 17.5f)
            lineTo(4f, 15.5f)
            quadTo(4f, 13f, 6.5f, 13f)
            close()
        }
        // Bottom-Right Box
        path(fill = SolidColor(Color.White)) {
            moveTo(15.5f, 13f)
            lineTo(17.5f, 13f)
            quadTo(20f, 13f, 20f, 15.5f)
            lineTo(20f, 17.5f)
            quadTo(20f, 20f, 17.5f, 20f)
            lineTo(15.5f, 20f)
            quadTo(13f, 20f, 13f, 17.5f)
            lineTo(13f, 15.5f)
            quadTo(13f, 13f, 15.5f, 13f)
            close()
        }
    }.build()
}

@Composable
fun RainbowScanBorder(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val baseColors = listOf(
        Color(0xFFFF3366), // Hot pink
        Color(0xFFFF9933), // Orange
        Color(0xFFFFCC33), // Yellow
        Color(0xFF33CC66), // Green
        Color(0xFF3399FF), // Blue
        Color(0xFF9933FF), // Purple
        Color(0xFFFF3366)  // Hot pink
    )

    val shiftedColors = remember(offsetFraction) {
        val size = baseColors.size - 1
        val shift = (offsetFraction * size).toInt()
        val fraction = (offsetFraction * size) - shift
        
        List(baseColors.size) { i ->
            val index1 = (i + shift) % size
            val index2 = (index1 + 1) % size
            lerpColor(baseColors[index1], baseColors[index2], fraction)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 4.dp,
                brush = Brush.sweepGradient(colors = shiftedColors),
                shape = RoundedCornerShape(0.dp)
            )
            .alpha(pulseAlpha)
    )
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + fraction * (stop.red - start.red),
        green = start.green + fraction * (stop.green - start.green),
        blue = start.blue + fraction * (stop.blue - start.blue),
        alpha = start.alpha + fraction * (stop.alpha - start.alpha)
    )
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap, displayName: String): Boolean {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/OmniBrowser")
    }

    val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    var success = false
    if (targetUri != null) {
        try {
            resolver.openOutputStream(targetUri)?.use { output ->
                success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } catch (e: java.lang.Exception) {
            Log.e("BrowserScreen", "Failed to save bitmap: ${e.localizedMessage}")
        }
    }
    return success
}

@Composable
fun QrScanResultComposer(
    results: List<String>,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    if (results.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }
    val currentResult = results.getOrNull(currentIndex) ?: ""
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 70.dp) // clear bottom bar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isDarkTheme) Color(0xFF1E2E3D) else Color(0xFFF0F4F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.qr_detected),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (results.size > 1) {
                                Text(
                                    text = stringResource(R.string.qr_found_codes, results.size, currentIndex + 1, results.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (results.size > 1) {
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex - 1 + results.size) % results.size
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex + 1) % results.size
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Result Content Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (isDarkTheme) Color(0xFF16222F) else Color(0xFFF5F7FA),
                    border = BorderStroke(0.5.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentResult,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Scanned Text", currentResult)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.qr_copied_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.qr_composer_copy), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentResult)
                            }
                            val chooser = Intent.createChooser(intent, "Share Link")
                            context.startActivity(chooser)
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.qr_composer_share), fontSize = 12.sp)
                    }

                    val isUrl = currentResult.startsWith("http://") || currentResult.startsWith("https://")
                    Button(
                        onClick = {
                            if (isUrl) {
                                onOpenUrl(currentResult)
                            } else {
                                onOpenUrl("https://www.google.com/search?q=${Uri.encode(currentResult)}")
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isUrl) Icons.Rounded.OpenInBrowser else Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrl) stringResource(R.string.qr_open_link) else stringResource(R.string.qr_search),
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorDialog(
    initialUrl: String,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    val context = LocalContext.current

    // Generate QR bitmap reactively
    val qrBitmap = remember(urlText) {
        if (urlText.isNotEmpty()) {
            BarcodeGenerator.generateQRCode(
                text = urlText,
                size = 512,
                foreground = 0xFF000000.toInt(), // Black QR
                background = 0xFFFFFFFF.toInt()  // White background
            )
        } else {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.qr_gen_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            // QR Preview Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray.copy(alpha = 0.5f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(220.dp)
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.qr_gen_enter_url),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // URL input field
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text(stringResource(R.string.qr_gen_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Share
                OutlinedButton(
                    onClick = {
                        qrBitmap?.let { bitmap ->
                            try {
                                val cacheFile = File(context.cacheDir, "omni_shared_qr.png")
                                FileOutputStream(cacheFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(intent, "Share QR Code")
                                context.startActivity(chooser)
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, context.getString(R.string.qr_gen_share_failed, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF23374A) else Color.LightGray),
                    enabled = qrBitmap != null
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_gen_share))
                }

                // Save
                Button(
                    onClick = {
                        qrBitmap?.let { bitmap ->
                            try {
                                val saved = saveBitmapToGallery(context, bitmap, "Omni_QR_${System.currentTimeMillis()}.png")
                                if (saved) {
                                    Toast.makeText(context, context.getString(R.string.qr_gen_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.qr_gen_save_failed), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, context.getString(R.string.qr_gen_save_err, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = qrBitmap != null
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_text), color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureOverviewDialog(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(24.dp, shape = RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Feature Icon with glowing background circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description / Info Detail
                Text(
                    text = description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action button: Got It
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.overview_got_it),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TabGroupDialog(
    viewModel: BrowserViewModel,
    targetTabId: String,
    currentGroup: TabGroup?,
    newGroupTitle: String,
    onNewGroupTitleChange: (String) -> Unit,
    newGroupColorIndex: Int,
    onNewGroupColorIndexChange: (Int) -> Unit,
    groupColors: List<Long>,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1B26) else MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(R.string.tab_groups_title),
                color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Remove from group option
                if (currentGroup != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF3B5C).copy(alpha = 0.12f))
                            .clickable {
                                viewModel.removeTabFromGroup(targetTabId, currentGroup.id)
                                onDismissRequest()
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color(0xFFFF3B5C), modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.remove_from_group, currentGroup.title), color = Color(0xFFFF3B5C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Existing groups
                val existingGroups = viewModel.tabGroups.filter { it.id != currentGroup?.id }
                if (existingGroups.isNotEmpty()) {
                    Text(
                        stringResource(R.string.add_to_existing_group),
                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    existingGroups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(group.color).copy(alpha = 0.12f))
                                .clickable {
                                    viewModel.addTabToGroup(targetTabId, group.id)
                                    onDismissRequest()
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(group.color)))
                            Text(group.title, color = Color(group.color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            val tabCountText = if (group.tabIds.size == 1)
                                stringResource(R.string.tab_group_count_singular)
                            else
                                stringResource(R.string.tab_group_count_plural, group.tabIds.size)
                            Text(tabCountText, color = Color(group.color).copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                    }
                }

                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))

                // Create new group
                Text(
                    stringResource(R.string.create_new_group),
                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.OutlinedTextField(
                    value = newGroupTitle,
                    onValueChange = onNewGroupTitleChange,
                    placeholder = { Text(stringResource(R.string.group_name_placeholder), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(groupColors[newGroupColorIndex]),
                        unfocusedBorderColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                )
                // Color picker row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupColors.forEachIndexed { i, colorLong ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .border(
                                    if (i == newGroupColorIndex) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                    CircleShape
                                )
                                .clickable { onNewGroupColorIndexChange(i) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newGroupTitle.isNotBlank()) {
                        viewModel.createTabGroup(
                            title = newGroupTitle.trim(),
                            color = groupColors[newGroupColorIndex],
                            initialTabId = targetTabId
                        )
                    }
                    onDismissRequest()
                }
            ) {
                Text(if (newGroupTitle.isNotBlank()) stringResource(R.string.create_and_add) else stringResource(R.string.close_button),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel_text), color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable

fun CreateNewGroupComposerDialog(
    viewModel: BrowserViewModel,
    onDismissRequest: () -> Unit,
    onGroupCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    var groupTitle by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    val groupColors = remember {
        listOf(
            0xFF4285F4L, 0xFF34A853L, 0xFFFBBC05L, 0xFFEA4335L,
            0xFF8AB4F8L, 0xFF81C995L, 0xFFFDE293L, 0xFFF28B82L,
            0xFF9AA0A6L, 0xFF607D8BL, 0xFFFF9800L, 0xFF9C27B0L,
            0xFFE91E63L, 0xFF795548L, 0xFF009688L, 0xFF3F51B5L
        )
    }

    val ungroupedTabs = remember(viewModel.tabs.toList(), viewModel.tabGroups.toList(), viewModel.isIncognitoMode) {
        val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
        val groupedTabIds = viewModel.tabGroups.flatMap { it.tabIds }.toSet()
        currentModeTabs.filter { it.id !in groupedTabIds }
    }

    val selectedTabIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1B26) else MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.create_new_group).removeSuffix(":"),
                    color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.group_name_simple_placeholder),
                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    placeholder = { Text(stringResource(R.string.group_name_placeholder), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(groupColors[selectedColorIndex]),
                        unfocusedBorderColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )

                Text(
                    "Color Theme",
                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(groupColors) { i, colorLong ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .border(
                                    if (i == selectedColorIndex) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                    CircleShape
                                )
                                .clickable { selectedColorIndex = i },
                            contentAlignment = Alignment.Center
                        ) {
                            if (i == selectedColorIndex) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (ungroupedTabs.isNotEmpty()) {
                    HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
                    Text(
                        "Include Open Tabs",
                        color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ungroupedTabs.forEach { tab ->
                            val isChecked = tab.id in selectedTabIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isChecked) Color(groupColors[selectedColorIndex]).copy(alpha = 0.15f)
                                        else (if (viewModel.isDarkThemeEnabled) Color(0xFF1E2D3F) else Color(0xFFF2F4F7))
                                    )
                                    .clickable {
                                        if (isChecked) selectedTabIds.remove(tab.id) else selectedTabIds.add(tab.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedTabIds.add(tab.id) else selectedTabIds.remove(tab.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(groupColors[selectedColorIndex]))
                                )
                                Text(
                                    text = if (tab.title == "about:blank" || tab.title.isBlank()) stringResource(R.string.new_tab_title) else tab.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = groupTitle.trim().ifBlank {
                        "Group ${viewModel.tabGroups.size + 1}"
                    }
                    val newGroupId = java.util.UUID.randomUUID().toString()
                    val chosenColor = groupColors[selectedColorIndex]

                    if (selectedTabIds.isNotEmpty()) {
                        val group = TabGroup(
                            id = newGroupId,
                            title = finalTitle,
                            color = chosenColor,
                            tabIds = selectedTabIds.toList()
                        )
                        viewModel.tabGroups.add(group)
                        viewModel.saveTabGroups()
                    } else {
                        val newTabId = java.util.UUID.randomUUID().toString()
                        val group = TabGroup(
                            id = newGroupId,
                            title = finalTitle,
                            color = chosenColor,
                            tabIds = listOf(newTabId)
                        )
                        viewModel.tabGroups.add(group)
                        viewModel.saveTabGroups()
                        viewModel.createNewTab(context, "about:blank", groupId = newGroupId)
                    }
                    onGroupCreated()
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(groupColors[selectedColorIndex])),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    stringResource(R.string.create_new_group).removeSuffix(":"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    stringResource(R.string.cancel_text),
                    color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    )
}

@Composable
fun RenameTabGroupDialog(
    viewModel: BrowserViewModel,
    renameGroupTarget: TabGroup,
    renameGroupText: String,
    onRenameGroupTextChange: (String) -> Unit,
    groupColors: List<Long>,
    onDismissRequest: () -> Unit,
    onRenameGroupTargetChange: (TabGroup) -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = if (viewModel.isDarkThemeEnabled) Color(0xFF0F1B26) else MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.rename_group_title),
                color = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = renameGroupText,
                    onValueChange = onRenameGroupTextChange,
                    placeholder = { Text(stringResource(R.string.group_name_simple_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (viewModel.isDarkThemeEnabled) Color(0xFF23374A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (viewModel.isDarkThemeEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                )
                // Color change row
                Text(stringResource(R.string.change_color_title), color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupColors.forEach { colorLong ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .border(
                                    if (colorLong == renameGroupTarget.color) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                    CircleShape
                                )
                                .clickable {
                                    onRenameGroupTargetChange(renameGroupTarget.copy(color = colorLong))
                                    viewModel.changeTabGroupColor(renameGroupTarget.id, colorLong)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (renameGroupText.isNotBlank()) {
                    viewModel.renameTabGroup(renameGroupTarget.id, renameGroupText.trim())
                }
                onDismissRequest()
            }) {
                Text(stringResource(R.string.save_text), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel_text), color = if (viewModel.isDarkThemeEnabled) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun ExternalAppRedirectDialog(
    request: BrowserViewModel.PendingExternalAppRequest,
    viewModel: BrowserViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    val isDark = viewModel.isDarkThemeEnabled
    val containerColor = if (viewModel.isAmoledMode) Color(0xFF000000)
                         else if (isDark) Color(0xFF0F1B26)
                         else MaterialTheme.colorScheme.surface
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color(0xFF8E9AA8) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    // Derive a display name: app package label > scheme > truncated URI
    val appLabel = remember(request.packageName) {
        if (!request.packageName.isNullOrBlank()) {
            try {
                context.packageManager
                    .getApplicationLabel(
                        context.packageManager.getApplicationInfo(request.packageName, 0)
                    ).toString()
            } catch (_: Exception) { request.packageName }
        } else {
            try { Uri.parse(request.uri).scheme ?: request.uri } catch (_: Exception) { request.uri }
        }
    }

    val siteLabel = request.sourceHost.ifBlank { "this site" }

    // Issue #113: for plain https(s) app links, staying in the browser must
    // actually load the tapped URL — the navigation was denied to allow the
    // app handoff, so without this the user would land on a blank page.
    val stayInBrowser = {
        if (!request.fallbackUrl.isNullOrBlank()) {
            viewModel.loadUrl(request.fallbackUrl)
        } else if (request.webUrlFallback) {
            viewModel.loadUrl(request.uri)
        }
        onDismiss()
        Unit
    }

    fun doLaunch() {
        try {
            val lowerUri = request.uri.lowercase()
            val intent = if (lowerUri.startsWith("intent:")) {
                Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setComponent(null)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) setSelector(null)
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(request.uri)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            }
            val pm = context.packageManager
            val handlers = try {
                pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) { emptyList() }

            when {
                handlers.size == 1 || !intent.getPackage().isNullOrBlank() ->
                    context.startActivity(intent)
                handlers.size > 1 -> {
                    val chooser = Intent.createChooser(intent, "Open with")
                    context.startActivity(chooser)
                }
                else -> Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_SHORT).show()
            }
        } catch (e: android.content.ActivityNotFoundException) {
            // Try fallback URL if present
            if (!request.fallbackUrl.isNullOrBlank()) {
                viewModel.loadUrl(request.fallbackUrl)
            } else {
                Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ExternalAppDialog", "Failed to launch external app", e)
            Toast.makeText(context, "Could not open external app", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = { stayInBrowser() },
        containerColor = containerColor,
        icon = {
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Open in $appLabel?",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$siteLabel wants to open an external app.",
                    color = textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Always open" — saves per-site allow and launches
                Button(
                    onClick = {
                        viewModel.updateSitePermission(request.sourceHost, "externalApp", "allow")
                        doLaunch()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Always open", fontWeight = FontWeight.SemiBold)
                }
                // "Open once" — launches without saving
                OutlinedButton(
                    onClick = {
                        doLaunch()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                ) {
                    Text("Open once", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
                // "Stay" — saves per-site block so future auto-redirects from this site are silently denied
                TextButton(
                    onClick = {
                        viewModel.updateSitePermission(request.sourceHost, "externalApp", "block")
                        // Load fallback URL in-browser if available, otherwise stay
                        stayInBrowser()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stay on page", color = textSecondary)
                }
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofIdentityChooserDialog(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onOpenUserAgentSettings: () -> Unit
) {
    val context = LocalContext.current
    val userAgentManager = viewModel.userAgentManager
    val globalPreset by userAgentManager.globalPreset.collectAsState()
    val globalCustomUa by userAgentManager.globalCustomUa.collectAsState()
    val siteRules by userAgentManager.siteRules.collectAsState()

    val activeTab = viewModel.activeTab
    val currentUrl = viewModel.currentUrl

    // Extract domain from active URL if available
    val currentDomain = remember(currentUrl, activeTab) {
        val urlToParse = when {
            activeTab != null && activeTab.url.isNotBlank() && activeTab.url != "about:blank" -> activeTab.url
            currentUrl.isNotBlank() && currentUrl != "about:blank" -> currentUrl
            else -> ""
        }
        if (urlToParse.startsWith("http://") || urlToParse.startsWith("https://")) {
            try {
                val host = android.net.Uri.parse(urlToParse).host?.trim()?.lowercase()?.removePrefix("www.")
                host?.split("/")?.firstOrNull() ?: ""
            } catch (_: Exception) { "" }
        } else {
            ""
        }
    }

    val hasSiteDomain = currentDomain.isNotBlank()
    var selectedScope by remember(currentDomain) { mutableStateOf(if (hasSiteDomain) "site" else "global") }

    val existingSiteRule = remember(siteRules, currentDomain) {
        if (currentDomain.isBlank()) null
        else siteRules.firstOrNull { rule ->
            val rDomain = rule.domain.trim().lowercase().removePrefix("www.")
            rDomain == currentDomain || currentDomain.endsWith(".$rDomain") || rDomain.endsWith(".$currentDomain")
        }
    }

    val selectedPresetId = if (selectedScope == "site") {
        existingSiteRule?.presetId ?: UserAgentPreset.DEFAULT.id
    } else {
        globalPreset.id
    }

    var customUaInput by remember { mutableStateOf(if (selectedScope == "site") (existingSiteRule?.customUaString ?: "") else globalCustomUa) }
    var showCustomUaInput by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Spoof Identity",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = if (hasSiteDomain && selectedScope == "site") "Set rule for $currentDomain" else "Set default browser identity",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onDismiss()
                        onOpenUserAgentSettings()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "User Agent Settings",
                        tint = textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasSiteDomain) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardColor)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedScope == "site") accentColor else Color.Transparent)
                                .clickable { selectedScope = "site" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This Site",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedScope == "site") Color.White else textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedScope == "global") accentColor else Color.Transparent)
                                .clickable { selectedScope = "global" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All Sites (Global)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedScope == "global") Color.White else textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (selectedScope == "site" && existingSiteRule != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Site rule active for $currentDomain",
                                fontSize = 11.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                modifier = Modifier.clickable {
                                    userAgentManager.removeSiteRulesForDomain(currentDomain)
                                    viewModel.applyUserAgentForTab(activeTab)
                                    activeTab?.session?.reload()
                                    Toast.makeText(context, "Site rule removed for $currentDomain", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            ) {
                                Text(
                                    text = "Remove Site Rule",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserAgentPreset.entries.forEach { preset ->
                        val isSelected = (preset.id == selectedPresetId)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.12f) else cardColor,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accentColor else cardBorderColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (preset == UserAgentPreset.CUSTOM) {
                                        showCustomUaInput = true
                                    } else {
                                        if (selectedScope == "site" && hasSiteDomain) {
                                            userAgentManager.addOrUpdateSiteRule(currentDomain, preset)
                                            Toast.makeText(context, "Saved site identity for $currentDomain", Toast.LENGTH_SHORT).show()
                                        } else {
                                            userAgentManager.setGlobalPreset(preset)
                                            Toast.makeText(context, "Global identity updated", Toast.LENGTH_SHORT).show()
                                        }
                                        viewModel.applyUserAgentForTab(activeTab)
                                        activeTab?.session?.reload()
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (preset == UserAgentPreset.CUSTOM) {
                                            showCustomUaInput = true
                                        } else {
                                            if (selectedScope == "site" && hasSiteDomain) {
                                                userAgentManager.addOrUpdateSiteRule(currentDomain, preset)
                                                Toast.makeText(context, "Saved site identity for $currentDomain", Toast.LENGTH_SHORT).show()
                                            } else {
                                                userAgentManager.setGlobalPreset(preset)
                                                Toast.makeText(context, "Global identity updated", Toast.LENGTH_SHORT).show()
                                            }
                                            viewModel.applyUserAgentForTab(activeTab)
                                            activeTab?.session?.reload()
                                            onDismiss()
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) accentColor else textPrimary
                                    )
                                    if (preset.userAgentString.isNotBlank()) {
                                        Text(
                                            text = preset.userAgentString,
                                            fontSize = 10.sp,
                                            color = textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCustomUaInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customUaInput,
                            onValueChange = { customUaInput = it },
                            label = { Text("Custom User Agent String", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCustomUaInput = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val trimmed = customUaInput.trim()
                                    if (trimmed.isNotBlank()) {
                                        if (selectedScope == "site" && hasSiteDomain) {
                                            userAgentManager.addOrUpdateSiteRule(currentDomain, UserAgentPreset.CUSTOM, trimmed)
                                            Toast.makeText(context, "Custom identity saved for $currentDomain", Toast.LENGTH_SHORT).show()
                                        } else {
                                            userAgentManager.setGlobalPreset(UserAgentPreset.CUSTOM, trimmed)
                                            Toast.makeText(context, "Global custom identity updated", Toast.LENGTH_SHORT).show()
                                        }
                                        viewModel.applyUserAgentForTab(activeTab)
                                        activeTab?.session?.reload()
                                        onDismiss()
                                    }
                                }
                            ) {
                                Text("Save & Apply")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenUserAgentSettings()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Full Settings",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Close", color = textSecondary)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDownloaderDialog(
    viewModel: BrowserViewModel,
    initialUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var magnetInput by remember(initialUrl) { mutableStateOf(initialUrl?.trim() ?: "") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            magnetInput = initialUrl.trim()
        } else {
            val clipText = clipboardManager.getText()?.text?.trim()
            if (!clipText.isNullOrEmpty() && (clipText.startsWith("magnet:") || clipText.endsWith(".torrent"))) {
                magnetInput = clipText
            }
        }
    }

    val parsedInfo = remember(magnetInput) {
        val trimmed = magnetInput.trim()
        if (trimmed.startsWith("magnet:")) {
            val dnMatch = Regex("""dn=([^&]+)""").find(trimmed)?.groupValues?.get(1)?.let {
                runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            }
            val hashMatch = Regex("""xt=urn:btih:([^&]+)""").find(trimmed)?.groupValues?.get(1)
            val trackers = Regex("""tr=([^&]+)""").findAll(trimmed).map {
                runCatching { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault(it.groupValues[1])
            }.toList()
            Triple(dnMatch ?: "Torrent Download", hashMatch ?: "Unknown Hash", trackers)
        } else {
            Triple(trimmed.substringAfterLast("/").ifBlank { "Torrent Download" }, "", emptyList())
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
    val cardColor = if (viewModel.isDarkThemeEnabled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text("Torrent & Magnet Downloader", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text("High-Speed Packet Torrent Engine", fontSize = 11.sp, color = textSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = magnetInput,
                    onValueChange = { magnetInput = it },
                    label = { Text("Paste Magnet Link or Torrent URL", fontSize = 12.sp) },
                    placeholder = { Text("magnet:?xt=urn:btih:...", fontSize = 11.sp) },
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val text = clipboardManager.getText()?.text?.trim()
                                if (!text.isNullOrEmpty()) magnetInput = text
                            }
                        ) {
                            Text("Paste", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (magnetInput.trim().isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "Parsed Torrent Metadata", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            Text(text = "Name: ${parsedInfo.first}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            if (parsedInfo.second.isNotBlank()) {
                                Text(text = "InfoHash: ${parsedInfo.second}", fontSize = 10.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (parsedInfo.third.isNotEmpty()) {
                                Text(text = "Trackers (${parsedInfo.third.size}): ${parsedInfo.third.take(2).joinToString(", ")}", fontSize = 10.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val urlToDownload = magnetInput.trim()
            val isMagnet = urlToDownload.startsWith("magnet:", ignoreCase = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val magnetIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToDownload)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            val chooser = Intent.createChooser(magnetIntent, "Open with Torrent App")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                            onDismiss()
                        } catch (_: Exception) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Magnet Link", urlToDownload))
                            Toast.makeText(context, "Magnet link copied to clipboard", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = urlToDownload.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("External App")
                }

                Button(
                    onClick = {
                        if (urlToDownload.isNotBlank()) {
                            viewModel.startTorrentDownload(urlToDownload, parsedInfo.first)
                            Toast.makeText(context, "Downloading: ${parsedInfo.first}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = urlToDownload.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Download in Omni")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}


