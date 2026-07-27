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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import com.rebelroot.omni.ui.theme.getUiSizeConfig
import com.rebelroot.omni.ui.theme.UiSizeConfig
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


@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhoneAddressBar(
    viewModel: BrowserViewModel,
    inputUrl: androidx.compose.ui.text.input.TextFieldValue,
    onInputUrlChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    isInputFocused: Boolean,
    onInputFocusedChange: (Boolean) -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    hasActiveUserExtensions: Boolean,
    onShowExtensionsSheet: () -> Unit,
    onShowToolsSheet: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowCustomizationSheet: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowQuickTools: () -> Unit = {},
    onShowPlayerSettings: () -> Unit,
    onShowTabGroups: () -> Unit = {},
    onShowSiteInfo: () -> Unit = {},
    onShowAllInOneMenuSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val config = getUiSizeConfig(viewModel.uiScale, screenWidthDp)

    var showAddressBarContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = config.paddingHorizontal, vertical = config.paddingVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = !isInputFocused) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.loadUrl("about:blank") },
                    modifier = Modifier.size(config.barIconSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Go Home",
                        modifier = Modifier.size(config.innerIconSize),
                        tint = if (viewModel.isDarkThemeEnabled) Color.White else Color(0xFF202124)
                    )
                }

                // Quick Tools (Toolbox) — visible on Left Hand Side in All-in-One mode
                if (viewModel.chromeNavBarEnabled) {
                    IconButton(
                        onClick = onShowToolsSheet,
                        modifier = Modifier.size(config.barIconSize)
                    ) {
                        Icon(
                            imageVector = BlackholeIcon,
                            contentDescription = "Quick Tools",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(config.innerIconSize)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(config.searchBoxHeight)
                .padding(horizontal = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(config.searchBoxHeight / 2)
                )
                .border(
                    width = 1.dp,
                    color = if (isInputFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(config.searchBoxHeight / 2)
                )
                .combinedClickable(
                    onClick = {
                        if (!isInputFocused) {
                            focusRequester.requestFocus()
                        }
                    },
                    onLongClick = {
                        if (!isInputFocused) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddressBarContextMenu = true
                        }
                    }
                )
                .padding(horizontal = config.searchBoxHeight * 0.35f),
            contentAlignment = Alignment.CenterStart
        ) {
            DropdownMenu(
                expanded = showAddressBarContextMenu,
                onDismissRequest = { showAddressBarContextMenu = false },
                modifier = Modifier.width(260.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = if (viewModel.isDarkThemeEnabled && viewModel.isAmoledMode) Color(0xFF0C0D10) else if (viewModel.isDarkThemeEnabled) Color(0xFF20222A) else Color(0xFFFFFFFF),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, if (viewModel.isDarkThemeEnabled) Color(0xFF2D303C) else Color(0xFFE5E7EB))
            ) {
                val isBottom = viewModel.addressBarPosition == "Bottom"
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isBottom) "Move address bar to the top" else "Move address bar to the bottom",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFFF3F4F6) else Color(0xFF1F2937)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isBottom) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = if (viewModel.isDarkThemeEnabled) Color(0xFFD1D5DB) else Color(0xFF4B5563),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showAddressBarContextMenu = false
                        val newPos = if (isBottom) "Top" else "Bottom"
                        viewModel.saveAddressBarPosition(context, newPos)
                        Toast.makeText(context, "Address bar moved to $newPos", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2D303C) else Color(0xFFE5E7EB), thickness = 0.5.dp)

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Copy link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (viewModel.isDarkThemeEnabled) Color(0xFFF3F4F6) else Color(0xFF1F2937)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = if (viewModel.isDarkThemeEnabled) Color(0xFFD1D5DB) else Color(0xFF4B5563),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showAddressBarContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("URL", viewModel.currentUrl)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isSecure = viewModel.currentUrl.startsWith("https://")
                val isHttp = viewModel.currentUrl.startsWith("http://")
                val showSecurityIcon = !isInputFocused && viewModel.currentUrl.isNotEmpty() && viewModel.currentUrl != "about:blank"

                if (!isInputFocused) {
                    Box(
                        modifier = Modifier
                            .size(config.innerIconSize + 4.dp)
                            .clip(CircleShape)
                            .clickable(enabled = showSecurityIcon) { onShowSiteInfo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                viewModel.isIncognitoMode -> Icons.Rounded.VisibilityOff
                                showSecurityIcon -> Icons.Rounded.Tune
                                else -> Icons.Rounded.Search
                            },
                            contentDescription = "Search or Site controls icon",
                            modifier = Modifier.size(config.innerIconSize * 0.75f),
                            tint = when {
                                viewModel.isIncognitoMode -> Color(0xFFCBB2FF)
                                showSecurityIcon -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            }
                        )
                    }
                }


                val domainColor = MaterialTheme.colorScheme.onSurface
                val pathColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                val urlTransformation = remember(isInputFocused, domainColor, pathColor) {
                    UrlVisualTransformation(isInputFocused, domainColor, pathColor)
                }

                val density = androidx.compose.ui.platform.LocalDensity.current
                val scrollState = rememberScrollState()
                var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                var containerWidth by remember { mutableStateOf(0) }

                LaunchedEffect(inputUrl.selection, textLayoutResult, containerWidth) {
                    val layout = textLayoutResult ?: return@LaunchedEffect
                    val selection = inputUrl.selection
                    val cursorStart = selection.start
                    val layoutTextLength = layout.layoutInput.text.length
                    if (cursorStart >= 0 && cursorStart <= layoutTextLength) {
                        try {
                            val cursorRect = layout.getCursorRect(cursorStart)
                            val cursorLeft = cursorRect.left
                            val cursorRight = cursorRect.right
                            
                            val viewportWidth = containerWidth
                            if (viewportWidth > 0) {
                                val scrollVal = scrollState.value
                                val paddingPx = with(density) { 36.dp.toPx() }
                                val leftBoundary = scrollVal + paddingPx
                                val rightBoundary = scrollVal + viewportWidth - paddingPx
                                
                                if (cursorLeft < leftBoundary) {
                                    scrollState.animateScrollTo((cursorLeft - paddingPx).coerceAtLeast(0f).toInt())
                                } else if (cursorRight > rightBoundary) {
                                    scrollState.animateScrollTo((cursorRight - viewportWidth + paddingPx).toInt())
                                }
                            }
                        } catch (e: Throwable) {
                            // Safely ignore transient layout bounds mismatch during rapid typing or focus changes
                        }
                    }
                }

                LaunchedEffect(isInputFocused) {
                    if (!isInputFocused) {
                        scrollState.scrollTo(0)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { containerWidth = it.size.width },
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = if (inputUrl.text == "about:blank") androidx.compose.ui.text.input.TextFieldValue("") else inputUrl,
                        onValueChange = onInputUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .focusRequester(focusRequester)
                            .onFocusChanged { onInputFocusedChange(it.isFocused) },
                        onTextLayout = { textLayoutResult = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = config.fontSize
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                viewModel.loadUrl(inputUrl.text)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = urlTransformation
                    )
                }

                // X Clear button — only shown when the user is actively editing the URL
                if (isInputFocused && inputUrl.text.isNotEmpty() && inputUrl.text != "about:blank") {
                    Box(
                        modifier = Modifier
                            .size(config.innerIconSize + 4.dp)
                            .clickable { onInputUrlChange(androidx.compose.ui.text.input.TextFieldValue("")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(config.innerIconSize * 0.75f),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                // Bookmark star — only visible in classic mode (All-in-One has it in the dropdown menu)
                if (!viewModel.chromeNavBarEnabled && viewModel.currentUrl.isNotEmpty() && viewModel.currentUrl != "about:blank" && !isInputFocused) {
                    val isBookmarked = viewModel.isBookmarked(viewModel.currentUrl)
                    Box(
                        modifier = Modifier
                            .size(config.innerIconSize + 4.dp)
                            .clickable {
                                if (isBookmarked) {
                                    viewModel.removeBookmark(viewModel.currentUrl)
                                } else {
                                    val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Page"
                                    viewModel.addToBookmarks(activeTabTitle, viewModel.currentUrl)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(config.innerIconSize)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isInputFocused) {
            IconButton(
                onClick = {
                    viewModel.loadUrl(inputUrl.text)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                modifier = Modifier.size(config.barIconSize)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Submit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(config.innerIconSize)
                )
            }
        }

        AnimatedVisibility(visible = isInputFocused) {
            TextButton(
                onClick = {
                    onInputUrlChange(androidx.compose.ui.text.input.TextFieldValue(viewModel.currentUrl))
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = (config.fontSize.value - 1f).sp)
            }
        }



        AnimatedVisibility(visible = !isInputFocused && !viewModel.chromeNavBarEnabled) {
            IconButton(
                onClick = onShowExtensionsSheet,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = "Extensions",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(config.innerIconSize)
                    )
                    if (hasActiveUserExtensions) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF8B5CF6), androidx.compose.foundation.shape.CircleShape)
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = !isInputFocused && (!viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            val infiniteTransition = rememberInfiniteTransition(label = "tabPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val currentScale = if (viewModel.showBackgroundTabNotification) pulseScale else 1f
            val pulseColor = if (viewModel.showBackgroundTabNotification) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground

            IconButton(
                onClick = onShowTabGroups,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Box(
                    modifier = Modifier
                        .size(config.innerIconSize + 4.dp)
                        .graphicsLayer(scaleX = currentScale, scaleY = currentScale)
                        .border(1.dp, pulseColor, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                        color = pulseColor,
                        fontSize = (config.fontSize.value * 0.66f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isInputFocused && (!viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            Box {
                IconButton(
                    onClick = {
                        if (viewModel.addressBarPosition == "Bottom") {
                            onShowAllInOneMenuSheet()
                        } else {
                            onShowMenuChange(true)
                        }
                    },
                    modifier = Modifier.size(config.barIconSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(config.innerIconSize)
                    )
                }

                omnimenuDropdown(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                    viewModel = viewModel,
                    onNewTab = {
                        viewModel.createNewTab(context, "about:blank")
                    },
                    onNewIncognitoTab = {
                        if (!viewModel.isIncognitoMode) {
                            viewModel.toggleIncognitoMode(context)
                        }
                        viewModel.createNewTab(context, "about:blank")
                    },
                    onOpenHistory = onOpenHistory,
                    onBurnData = {
                        coroutineScope.launch {
                            val runtime = viewModel.getGeckoRuntime(context)
                            FireButton(runtime, context).burn()
                            viewModel.burnAllData(context)
                            Toast.makeText(context, "🔥 All history and tabs burned", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenDownloads = onOpenDownloads,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    onShowThemeSheet = onShowThemeSheet,
                    onShowQuickTools = onShowQuickTools,
                    onShowCustomizationSheet = onShowCustomizationSheet,
                    onShowExtensions = onShowExtensionsSheet,
                    onShowPlayerSettings = onShowPlayerSettings,
                    onShowSiteInfo = onShowSiteInfo,
                    onFindInPage = { viewModel.openFindInPage() }
                )
            }
        }
    }
}

@Composable
fun omnimenuDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: BrowserViewModel,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenHistory: () -> Unit,
    onBurnData: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowQuickTools: () -> Unit = {},
    onShowCustomizationSheet: () -> Unit,
    onShowExtensions: () -> Unit,
    onShowPlayerSettings: () -> Unit,
    onShowSiteInfo: () -> Unit,
    onFindInPage: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
    val isHome = viewModel.currentUrl == "about:blank" || activeTab == null

    val cardBg = if (isDark && viewModel.isAmoledMode) Color(0xFF0C0D10) else if (isDark) Color(0xFF20222A) else Color(0xFFF6F7F9)
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val iconTint = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563)
    val dividerColor = if (isDark) Color(0xFF2D303C) else Color(0xFFE5E7EB)
    val circleBg = if (isDark) Color(0xFF2C2E38) else Color(0xFFE5E7EB)
    val accentColor = MaterialTheme.colorScheme.primary

    if (!expanded) return

    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = (screenHeightDp - 70.dp).coerceAtLeast(300.dp)

    androidx.compose.ui.window.Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = remember {
            object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: androidx.compose.ui.unit.IntRect,
                    windowSize: androidx.compose.ui.unit.IntSize,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    popupContentSize: androidx.compose.ui.unit.IntSize
                ): androidx.compose.ui.unit.IntOffset {
                    val isBottom = anchorBounds.top > windowSize.height / 2

                    // Horizontal: right align with anchor button
                    val x = (anchorBounds.right - popupContentSize.width).coerceIn(
                        12,
                        (windowSize.width - popupContentSize.width - 12).coerceAtLeast(0)
                    )

                    // Vertical:
                    // If bottom nav: bottom of popup sits directly on top of anchor bounds (top of bottom bar)
                    // If top nav: top of popup sits directly below anchor bounds (bottom of top bar)
                    val y = if (isBottom) {
                        anchorBounds.top - popupContentSize.height - 4
                    } else {
                        anchorBounds.bottom + 4
                    }

                    val clampedY = y.coerceIn(
                        12,
                        (windowSize.height - popupContentSize.height - 12).coerceAtLeast(0)
                    )

                    return androidx.compose.ui.unit.IntOffset(x, clampedY)
                }
            }
        },
        properties = androidx.compose.ui.window.PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .width(260.dp)
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF2D303C) else Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
            // ── Top 6 Circular Quick Navigation Row ──────────────────
            val canBack = activeTab?.canGoBack == true
            val canForward = activeTab?.canGoForward == true
            val isBookmarked = !isHome && viewModel.isBookmarked(viewModel.currentUrl)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                CircleMenuIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    enabled = canBack,
                    tint = if (canBack) iconTint else iconTint.copy(alpha = 0.3f),
                    bg = circleBg,
                    onClick = { onDismissRequest(); viewModel.goBack() }
                )
                // Forward
                CircleMenuIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Forward",
                    enabled = canForward,
                    tint = if (canForward) iconTint else iconTint.copy(alpha = 0.3f),
                    bg = circleBg,
                    onClick = { onDismissRequest(); viewModel.goForward() }
                )
                // Save / Bookmark
                CircleMenuIconButton(
                    icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Bookmark",
                    enabled = !isHome,
                    tint = if (!isHome) (if (isBookmarked) accentColor else iconTint) else iconTint.copy(alpha = 0.3f),
                    bg = if (isBookmarked) accentColor.copy(alpha = 0.2f) else circleBg,
                    onClick = {
                        onDismissRequest()
                        if (!isHome) {
                            if (isBookmarked) viewModel.removeBookmark(viewModel.currentUrl)
                            else viewModel.addToBookmarks(activeTab?.title ?: "Webpage", viewModel.currentUrl)
                        }
                    }
                )
                // Save PDF
                CircleMenuIconButton(
                    icon = Icons.Rounded.Download,
                    contentDescription = "Save PDF",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else iconTint.copy(alpha = 0.3f),
                    bg = circleBg,
                    onClick = { onDismissRequest(); if (!isHome) viewModel.printCurrentPage(context) }
                )
                // Info
                CircleMenuIconButton(
                    icon = Icons.Rounded.Info,
                    contentDescription = "Info",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else iconTint.copy(alpha = 0.3f),
                    bg = circleBg,
                    onClick = { onDismissRequest(); if (!isHome) onShowSiteInfo() }
                )
                // Reload
                CircleMenuIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Reload",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else iconTint.copy(alpha = 0.3f),
                    bg = circleBg,
                    onClick = { onDismissRequest(); if (!isHome) viewModel.reload() }
                )
            }

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

            // ── Section 1: Tabs ────────────────────────────────────
            MinimalMenuItem(
                text = "New tab",
                icon = Icons.Rounded.Add,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onNewTab() }
            )
            MinimalMenuItem(
                text = "New Incognito tab",
                icon = Icons.Rounded.VisibilityOff,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onNewIncognitoTab() }
            )
            MinimalMenuItem(
                text = "Add tab to new group",
                icon = Icons.Rounded.GridView,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); Toast.makeText(context, "Group created with active tab", Toast.LENGTH_SHORT).show() }
            )

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

            // ── Section 2: Browse & Data ───────────────────────────
            MinimalMenuItem(
                text = "History",
                icon = Icons.Rounded.History,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onOpenHistory() }
            )
            MinimalMenuItem(
                text = "Clear Browsing Data",
                icon = Icons.Rounded.DeleteOutline,
                iconTint = Color(0xFFFF453A),
                textColor = Color(0xFFFF453A),
                onClick = { onDismissRequest(); onBurnData() }
            )

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

            // ── Section 3: Library & Page Tools ────────────────────
            MinimalMenuItem(
                text = "Downloads",
                icon = Icons.Rounded.FileDownload,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onOpenDownloads() }
            )
            MinimalMenuItem(
                text = "Bookmarks",
                icon = Icons.Rounded.StarBorder,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onOpenBookmarks() }
            )
            MinimalMenuItem(
                text = "Recent tabs",
                icon = Icons.Rounded.Devices,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onOpenHistory() }
            )

            if (!isHome) {
                MinimalMenuItem(
                    text = "Desktop Site",
                    icon = Icons.Rounded.Computer,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    onClick = { onDismissRequest(); viewModel.toggleDesktopMode(context) },
                    trailingContent = {
                        Switch(
                            checked = viewModel.isDesktopMode,
                            onCheckedChange = { onDismissRequest(); viewModel.toggleDesktopMode(context) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                )
                MinimalMenuItem(
                    text = "Find in Page",
                    icon = Icons.Rounded.Search,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    onClick = { onDismissRequest(); onFindInPage() }
                )
                MinimalMenuItem(
                    text = "Add to Shortcuts",
                    icon = Icons.Rounded.AddCircle,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        val currentUrl = viewModel.currentUrl
                        val currentTitle = activeTab?.title ?: "Webpage"
                        viewModel.addShortcut(currentTitle, currentUrl)
                    }
                )
                MinimalMenuItem(
                    text = "Extensions",
                    icon = Icons.Rounded.Extension,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    onClick = { onDismissRequest(); onShowExtensions() }
                )
            }

            MinimalMenuItem(
                text = "Player Settings",
                icon = Icons.Rounded.PlayCircle,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onShowPlayerSettings() }
            )

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

            // ── Section 4: App Settings ────────────────────────────
            MinimalMenuItem(
                text = "Theme",
                icon = Icons.Rounded.Palette,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onShowThemeSheet() }
            )
            MinimalMenuItem(
                text = "Quick Tools",
                icon = Icons.Rounded.Build,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onShowQuickTools() }
            )
            MinimalMenuItem(
                text = "Settings",
                icon = Icons.Rounded.Settings,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onOpenSettings() }
            )
            MinimalMenuItem(
                text = "Customize new tab page",
                icon = Icons.Rounded.Edit,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); onShowCustomizationSheet() }
            )
            MinimalMenuItem(
                text = "Help & feedback",
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                iconTint = iconTint,
                textColor = textPrimary,
                onClick = { onDismissRequest(); Toast.makeText(context, "Omni Browser v1.0.9", Toast.LENGTH_SHORT).show() }
            )

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
}

@Composable
private fun CircleMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(35.dp)
            .background(bg, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun MinimalMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun MenuActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(44.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .background(bg, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = tint.copy(alpha = if (enabled) 1f else 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuSectionLabel(text: String, textColor: Color) {
    Text(
        text = text.uppercase(),
        color = textColor,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun LuxuryMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color = iconTint.copy(alpha = 0.12f),
    textColor: Color,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun FindInPageBar(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val isDark = viewModel.isDarkThemeEnabled
    val bg = if (isDark && viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val border = if (isDark && viewModel.isAmoledMode) Color(0xFF1A1A1A) else if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val mutedColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accentColor = MaterialTheme.colorScheme.primary

    // Auto-focus on open
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Surface(
        modifier = modifier,
        color = bg,
        shadowElevation = 16.dp,
        shape = androidx.compose.ui.graphics.RectangleShape,
        border = BorderStroke(0.5.dp, border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thin accent line at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search icon
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = mutedColor,
                    modifier = Modifier.size(20.dp)
                )

                // Text field — takes all remaining space
                androidx.compose.foundation.text.BasicTextField(
                    value = viewModel.findQuery,
                    onValueChange = { viewModel.updateFindQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.findNext() }
                    ),
                    decorationBox = { innerField ->
                        Box {
                            if (viewModel.findQuery.isEmpty()) {
                                Text(
                                    text = "Find in page…",
                                    color = mutedColor,
                                    fontSize = 15.sp
                                )
                            }
                            innerField()
                        }
                    }
                )

                // Match counter  e.g. "3 / 12"
                val matchText = when {
                    viewModel.findQuery.isEmpty() -> ""
                    !viewModel.findMatchFound -> "No matches"
                    viewModel.findMatchTotal > 0 ->
                        "${viewModel.findMatchCurrent} / ${viewModel.findMatchTotal}"
                    else -> ""
                }
                if (matchText.isNotEmpty()) {
                    Text(
                        text = matchText,
                        color = if (!viewModel.findMatchFound) Color(0xFFFF4444) else mutedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { viewModel.findPrev() },
                    enabled = viewModel.findQuery.isNotEmpty() && viewModel.findMatchFound,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = if (viewModel.findQuery.isNotEmpty() && viewModel.findMatchFound) textColor else mutedColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { viewModel.findNext() },
                    enabled = viewModel.findQuery.isNotEmpty() && viewModel.findMatchFound,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = if (viewModel.findQuery.isNotEmpty() && viewModel.findMatchFound) textColor else mutedColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Close
                IconButton(
                    onClick = { viewModel.closeFindInPage() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close find in page",
                        tint = mutedColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MenuGridCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xFF8E9AA8),
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

class UrlVisualTransformation(
    private val isFocused: Boolean,
    private val domainColor: Color,
    private val pathColor: Color
) : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val rawText = text.text
        if (rawText.isBlank() || rawText == "about:blank" || isFocused) {
            return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        }

        var protocolLen = 0
        if (rawText.startsWith("https://")) {
            protocolLen = 8
        } else if (rawText.startsWith("http://")) {
            protocolLen = 7
        }

        val domainStart = protocolLen
        var domainEnd = rawText.indexOf('/', domainStart)
        if (domainEnd == -1) {
            domainEnd = rawText.indexOf('?', domainStart)
        }
        if (domainEnd == -1) {
            domainEnd = rawText.indexOf('#', domainStart)
        }
        if (domainEnd == -1) {
            domainEnd = rawText.length
        }

        val domainPart = rawText.substring(domainStart, domainEnd)
        val wwwLen = if (domainPart.startsWith("www.")) 4 else 0
        val hiddenPrefixLen = protocolLen + wwwLen

        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        
        // Append domain (excluding www. if present)
        val transDomain = rawText.substring(hiddenPrefixLen, domainEnd)
        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = domainColor, fontWeight = FontWeight.Bold))
        builder.append(transDomain)
        builder.pop()

        // Append path / params
        if (domainEnd < rawText.length) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = pathColor))
            builder.append(rawText.substring(domainEnd))
            builder.pop()
        }

        val transformedLength = rawText.length - hiddenPrefixLen
        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= hiddenPrefixLen) return 0
                return (offset - hiddenPrefixLen).coerceIn(0, transformedLength)
            }

            override fun transformedToOriginal(offset: Int): Int {
                return (offset + hiddenPrefixLen).coerceIn(0, rawText.length)
            }
        }

        return androidx.compose.ui.text.input.TransformedText(
            builder.toAnnotatedString(),
            offsetMapping
        )
    }
}
