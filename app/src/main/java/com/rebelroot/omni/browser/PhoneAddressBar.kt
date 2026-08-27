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

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.rebelroot.omni.browser

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
    onOpenPasswordManager: () -> Unit,
    onShowCustomizationSheet: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowQuickTools: () -> Unit = {},
    onShowFeedbackDialog: () -> Unit = {},
    onShowPlayerSettings: () -> Unit,
    onShowTabGroups: () -> Unit = {},
    onShowSiteInfo: () -> Unit = {},
    onShowAllInOneMenuSheet: () -> Unit = {},
    onOpenMediaSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val config = getUiSizeConfig(viewModel.uiScale, screenWidthDp)

    var showAddressBarContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var dragAmountAccumulated by remember { mutableFloatStateOf(0f) }
    val isBottom = viewModel.addressBarPosition == "Bottom"

    @Composable
    fun MainAddressBar() {
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isInputFocused) {
                    Modifier.pointerInput(viewModel.activeTabId, viewModel.isIncognitoMode) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = 100f
                                if (dragAmountAccumulated > threshold) {
                                    val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                    val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                    if (currentIndex > 0) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.selectTab(currentModeTabs[currentIndex - 1].id)
                                    }
                                } else if (dragAmountAccumulated < -threshold) {
                                    val currentModeTabs = viewModel.tabs.filter { it.isIncognito == viewModel.isIncognitoMode }
                                    val currentIndex = currentModeTabs.indexOfFirst { it.id == viewModel.activeTabId }
                                    if (currentIndex != -1 && currentIndex < currentModeTabs.size - 1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.selectTab(currentModeTabs[currentIndex + 1].id)
                                    }
                                }
                                dragAmountAccumulated = 0f
                            },
                            onDragCancel = {
                                dragAmountAccumulated = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragAmountAccumulated += dragAmount
                            }
                        )
                    }
                } else Modifier
            )
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
                    color = if (viewModel.isDarkThemeEnabled) {
                        if (viewModel.isAmoledMode) Color(0xFF202124) else Color(0xFF2C2E35)
                    } else {
                        Color(0xFFF1F3F4)
                    },
                    shape = RoundedCornerShape(config.searchBoxHeight / 2)
                )
                .then(
                    if (isInputFocused) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(config.searchBoxHeight / 2)
                        )
                    } else Modifier
                )
                .pointerInput(isInputFocused, viewModel.addressBarPosition) {
                    if (isInputFocused) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragY = 0f
                        var totalDragX = 0f
                        var isMoved = false
                        val pointerId = down.id

                        do {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (pointer.pressed) {
                                val dragChange = pointer.position - pointer.previousPosition
                                totalDragY += dragChange.y
                                totalDragX += dragChange.x

                                val verticalThreshold = 40.dp.toPx()
                                if (!isMoved && kotlin.math.abs(totalDragY) > verticalThreshold && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.4f) {
                                    val currentPos = viewModel.addressBarPosition
                                    if (totalDragY > 0 && (currentPos == "Top" || currentPos == "Split")) {
                                        pointer.consume()
                                        isMoved = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.saveAddressBarPosition(context, "Bottom")
                                    } else if (totalDragY < 0 && currentPos == "Bottom") {
                                        pointer.consume()
                                        isMoved = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.saveAddressBarPosition(context, "Top")
                                    }
                                }
                                if (isMoved) {
                                    pointer.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
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
                            text = if (isBottom) stringResource(id = R.string.menu_move_address_bar_top) else stringResource(id = R.string.menu_move_address_bar_bottom),
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
                    }
                )

                HorizontalDivider(color = if (viewModel.isDarkThemeEnabled) Color(0xFF2D303C) else Color(0xFFE5E7EB), thickness = 0.5.dp)

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = R.string.menu_copy_link),
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
                        Toast.makeText(context, context.getString(R.string.menu_copy_link), Toast.LENGTH_SHORT).show()
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
                            .horizontalScroll(scrollState, enabled = isInputFocused)
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



        AnimatedVisibility(visible = !isInputFocused && (viewModel.addressBarPosition == "Top" || viewModel.addressBarPosition == "Split" || !viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            IconButton(
                onClick = onShowExtensionsSheet,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = stringResource(R.string.menu_extensions),
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



        AnimatedVisibility(visible = !isInputFocused && (viewModel.addressBarPosition == "Top" || !viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
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

        AnimatedVisibility(visible = !isInputFocused && (viewModel.addressBarPosition == "Top" || !viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            Box(
                modifier = Modifier.size(config.barIconSize)
            ) {
                IconButton(
                    onClick = { onShowAllInOneMenuSheet() },
                    modifier = Modifier.matchParentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(config.innerIconSize)
                    )
                }
            }
        }
    }
    }

    @Composable
    fun QuickActionBar() {
        // ── Address Bar Editing Quick Action Bar (Issue #88) ───────────────────────
        if (isInputFocused) {
            val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
            val clipboardText = remember(isInputFocused) {
                try {
                    clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                } catch (_: Exception) {
                    null
                }
            }
            val currentText = inputUrl.text.ifEmpty { viewModel.currentUrl }.takeIf { it.isNotEmpty() && it != "about:blank" }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = config.paddingHorizontal)
                    .offset(y = if (isBottom) (-4).dp else 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (viewModel.isAmoledMode) Color(0xFF0C0D10) else if (viewModel.isDarkThemeEnabled) Color(0xFF20222A) else Color(0xFFF2F4F7),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!clipboardText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            viewModel.loadUrl(clipboardText)
                            onInputUrlChange(androidx.compose.ui.text.input.TextFieldValue(clipboardText))
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.address_bar_paste_go),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = null
                    )
                }

                if (!currentText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            val clip = ClipData.newPlainText("URL", currentText)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.ctx_link_copied), Toast.LENGTH_SHORT).show()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.address_bar_copy),
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )

                    AssistChip(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, currentText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.address_bar_share),
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                }

                if (inputUrl.text.isNotEmpty()) {
                    AssistChip(
                        onClick = {
                            onInputUrlChange(androidx.compose.ui.text.input.TextFieldValue(""))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.address_bar_clear),
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                }
            }
        }
    }
    }

    @Composable
    fun HistorySuggestions() {
        // ── History Suggestions Dropdown ─────────────────────────────────────────
        if (isInputFocused && viewModel.historySuggestions.isNotEmpty()) {
            val shape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = config.paddingHorizontal)
                    .offset(y = if (isBottom) (-4).dp else 4.dp),
            shape = shape,
            color = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                viewModel.historySuggestions.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loadUrl(entry.url)
                                onInputUrlChange(androidx.compose.ui.text.input.TextFieldValue(entry.url))
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title.ifBlank { entry.url },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.title.isNotBlank()) {
                                Text(
                                    text = entry.url,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (isBottom) {
        HistorySuggestions()
        QuickActionBar()
        MainAddressBar()
    } else {
        MainAddressBar()
        QuickActionBar()
        HistorySuggestions()
    }
}

@Composable
fun omnimenuDropdownCard(
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
    onOpenPasswordManager: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowQuickTools: () -> Unit = {},
    onShowFeedbackDialog: () -> Unit = {},
    onShowCustomizationSheet: () -> Unit = {},
    onShowExtensions: () -> Unit = {},
    onShowPlayerSettings: () -> Unit = {},
    onShowSiteInfo: () -> Unit = {},
    onFindInPage: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkThemeEnabled
    val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
    val isHome = viewModel.currentUrl == "about:blank" || activeTab == null

    val cardBg = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val gridCardBg = if (viewModel.isAmoledMode) Color(0xFF111114) else MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val isCompact = screenWidthDp < 360.dp || screenHeightDp < 680.dp
    val isVeryCompact = screenWidthDp < 320.dp || screenHeightDp < 560.dp

    // Responsive width: dynamically scales with screen width on smaller devices
    val menuWidth = when {
        screenWidthDp < 320.dp -> (screenWidthDp - 16.dp).coerceAtLeast(200.dp)
        screenWidthDp < 360.dp -> (screenWidthDp - 24.dp).coerceAtLeast(230.dp)
        screenWidthDp < 400.dp -> (screenWidthDp * 0.74f).coerceIn(240.dp, 280.dp)
        else -> 300.dp
    }
    val maxHeight = (screenHeightDp - if (isCompact) 80.dp else 130.dp).coerceAtLeast(220.dp)

    Surface(
        modifier = Modifier
            .width(menuWidth)
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(if (isCompact) 18.dp else 24.dp),
        color = cardBg,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    vertical = if (isCompact) 6.dp else 10.dp,
                    horizontal = if (isCompact) 8.dp else 12.dp
                )
        ) {
            // ── Top Circular Quick Navigation Row ──────────────────
            val canBack = activeTab?.canGoBack == true
            val canForward = activeTab?.canGoForward == true
            val isBookmarked = !isHome && viewModel.isBookmarked(viewModel.currentUrl)
            val quickNavBtnSize = if (isCompact) 32.dp else 40.dp
            val quickNavIconSize = if (isCompact) 16.dp else 20.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 2.dp else 4.dp, vertical = if (isCompact) 1.dp else 2.dp)
                    .background(
                        color = if (viewModel.isAmoledMode) Color(0xFF111114) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp)
                    )
                    .padding(vertical = if (isCompact) 2.dp else 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(
                    onClick = { onDismissRequest(); viewModel.goBack() },
                    enabled = canBack,
                    modifier = Modifier.size(quickNavBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canBack) iconTint else iconTint.copy(alpha = 0.3f),
                        modifier = Modifier.size(quickNavIconSize)
                    )
                }
                // Forward
                IconButton(
                    onClick = { onDismissRequest(); viewModel.goForward() },
                    enabled = canForward,
                    modifier = Modifier.size(quickNavBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canForward) iconTint else iconTint.copy(alpha = 0.3f),
                        modifier = Modifier.size(quickNavIconSize)
                    )
                }
                // Save / Bookmark
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .size(quickNavBtnSize)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = !isHome,
                            onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onDismissRequest()
                                onOpenBookmarks()
                            },
                            onClick = {
                                onDismissRequest()
                                if (!isHome) {
                                    if (isBookmarked) viewModel.removeBookmark(viewModel.currentUrl)
                                    else viewModel.addToBookmarks(activeTab?.title ?: "Webpage", viewModel.currentUrl)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (!isHome) (if (isBookmarked) accentColor else iconTint) else iconTint.copy(alpha = 0.3f),
                        modifier = Modifier.size(quickNavIconSize)
                    )
                }
                // Share
                IconButton(
                    onClick = {
                        onDismissRequest()
                        if (!isHome) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, viewModel.currentUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(shareIntent)
                        }
                    },
                    enabled = !isHome,
                    modifier = Modifier.size(quickNavBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = "Share",
                        tint = if (!isHome) iconTint else iconTint.copy(alpha = 0.3f),
                        modifier = Modifier.size(quickNavIconSize)
                    )
                }
                // Reload
                IconButton(
                    onClick = { onDismissRequest(); if (!isHome) viewModel.reload() },
                    enabled = !isHome,
                    modifier = Modifier.size(quickNavBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        tint = if (!isHome) iconTint else iconTint.copy(alpha = 0.3f),
                        modifier = Modifier.size(quickNavIconSize)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))

            // ── TOP NAVIGATION (GLOBAL ACTIONS) ────────────────────
            MenuSectionLabel(text = stringResource(R.string.menu_section_top_navigation), textColor = textSecondary, isCompact = isCompact)

            val gridSpacing = if (isCompact) 5.dp else 8.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 2.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    GlobalActionCard(
                        icon = Icons.Rounded.Add,
                        customIcon = {
                            Box(
                                modifier = Modifier
                                    .size(if (isCompact) 18.dp else 22.dp)
                                    .border(1.5.dp, iconTint, RoundedCornerShape(if (isCompact) 4.dp else 6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(if (isCompact) 10.dp else 13.dp)
                                )
                            }
                        },
                        label = stringResource(R.string.menu_new_tab),
                        iconTint = iconTint,
                        cardBg = gridCardBg,
                        textColor = textPrimary,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1f),
                        onClick = { onDismissRequest(); onNewTab() }
                    )
                    GlobalActionCard(
                        icon = Icons.Rounded.VisibilityOff,
                        customIcon = {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                            )
                        },
                        label = stringResource(R.string.menu_new_incognito_tab),
                        iconTint = iconTint,
                        cardBg = gridCardBg,
                        textColor = textPrimary,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1f),
                        onClick = { onDismissRequest(); onNewIncognitoTab() }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    GlobalActionCard(
                        icon = Icons.Rounded.History,
                        label = stringResource(R.string.menu_history),
                        iconTint = iconTint,
                        cardBg = gridCardBg,
                        textColor = textPrimary,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1f),
                        onClick = { onDismissRequest(); onOpenHistory() }
                    )
                    GlobalActionCard(
                        icon = Icons.Rounded.FileDownload,
                        label = stringResource(R.string.menu_downloads),
                        iconTint = iconTint,
                        cardBg = gridCardBg,
                        textColor = textPrimary,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1f),
                        onClick = { onDismissRequest(); onOpenDownloads() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 3.dp else 6.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = if (isCompact) 2.dp else 4.dp, horizontal = 4.dp))

            // ── PAGE & TABS ────────────────────────────────────
            MenuSectionLabel(text = stringResource(R.string.menu_section_page_tabs), textColor = textSecondary, isCompact = isCompact)

            MinimalMenuItem(
                text = stringResource(R.string.menu_add_tab_to_new_group),
                icon = Icons.Rounded.GridView,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); Toast.makeText(context, context.getString(R.string.toast_group_created), Toast.LENGTH_SHORT).show() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_bookmarks),
                icon = Icons.Rounded.StarBorder,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onOpenBookmarks() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_recent_tabs),
                icon = Icons.Rounded.Devices,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onOpenHistory() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_extensions),
                icon = Icons.Rounded.Extension,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onShowExtensions() }
            )

            if (!isHome) {
                MinimalMenuItem(
                    text = stringResource(R.string.menu_desktop_site_item),
                    icon = Icons.Rounded.Computer,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    isCompact = isCompact,
                    onClick = { onDismissRequest(); viewModel.toggleDesktopMode(context) },
                    trailingContent = {
                        Switch(
                            checked = viewModel.isDesktopMode,
                            onCheckedChange = { onDismissRequest(); viewModel.toggleDesktopMode(context) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                            modifier = Modifier.scale(if (isCompact) 0.6f else 0.7f)
                        )
                    }
                )
                MinimalMenuItem(
                    text = stringResource(R.string.menu_find_in_page_item),
                    icon = Icons.Rounded.Search,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    isCompact = isCompact,
                    onClick = { onDismissRequest(); onFindInPage() }
                )
                if (viewModel.currentUrl.isNotBlank() && viewModel.currentUrl != "about:blank") {
                    MinimalMenuItem(
                        text = stringResource(R.string.menu_add_to_shortcuts),
                        icon = Icons.Rounded.AddCircle,
                        iconTint = iconTint,
                        textColor = textPrimary,
                        isCompact = isCompact,
                        onClick = {
                            onDismissRequest()
                            val currentUrl = viewModel.currentUrl
                            val currentTitle = activeTab?.title ?: "Webpage"
                            viewModel.addShortcut(currentTitle, currentUrl)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = if (isCompact) 2.dp else 4.dp, horizontal = 4.dp))

            // ── PRIVACY & UTILITIES ───────────────────────────
            MenuSectionLabel(text = stringResource(R.string.menu_section_privacy_utilities), textColor = textSecondary, isCompact = isCompact)

            MinimalMenuItem(
                text = stringResource(R.string.menu_password_manager),
                icon = Icons.Rounded.Lock,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onOpenPasswordManager() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_clear_browsing_data),
                icon = Icons.Rounded.DeleteOutline,
                iconTint = Color(0xFFFF453A),
                textColor = Color(0xFFFF453A),
                isCompact = isCompact,
                onClick = { onDismissRequest(); onBurnData() }
            )

            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = if (isCompact) 2.dp else 4.dp, horizontal = 4.dp))

            // ── SETTINGS & DISPLAY ────────────────────────────
            MenuSectionLabel(text = stringResource(R.string.menu_section_settings_display), textColor = textSecondary, isCompact = isCompact)

            MinimalMenuItem(
                text = stringResource(R.string.menu_settings),
                icon = Icons.Rounded.Settings,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onOpenSettings() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_theme),
                icon = Icons.Rounded.Palette,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onShowThemeSheet() }
            )
            MinimalMenuItem(
                text = stringResource(R.string.menu_player_settings),
                icon = Icons.Rounded.PlayCircle,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onShowPlayerSettings() }
            )
            if (isHome) {
                MinimalMenuItem(
                    text = if (viewModel.hideHomeBottomNav) stringResource(R.string.menu_show_home_nav_bar) else stringResource(R.string.menu_hide_home_nav_bar),
                    icon = if (viewModel.hideHomeBottomNav) Icons.Rounded.TvOff else Icons.Rounded.HideSource,
                    iconTint = iconTint,
                    textColor = textPrimary,
                    isCompact = isCompact,
                    onClick = { onDismissRequest(); viewModel.saveHideHomeBottomNav(context, !viewModel.hideHomeBottomNav) }
                )
            }
            MinimalMenuItem(
                text = stringResource(R.string.menu_help_feedback),
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                iconTint = iconTint,
                textColor = textPrimary,
                isCompact = isCompact,
                onClick = { onDismissRequest(); onShowFeedbackDialog() }
            )

            Spacer(modifier = Modifier.height(2.dp))
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
    onOpenPasswordManager: () -> Unit,
    onShowThemeSheet: () -> Unit = {},
    onShowQuickTools: () -> Unit = {},
    onShowFeedbackDialog: () -> Unit = {},
    onShowCustomizationSheet: () -> Unit = {},
    onShowExtensions: () -> Unit = {},
    onShowPlayerSettings: () -> Unit = {},
    onShowSiteInfo: () -> Unit = {},
    onFindInPage: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(Color.Transparent),
        containerColor = Color.Transparent,
        shadowElevation = 0.dp,
        border = null
    ) {
        omnimenuDropdownCard(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            viewModel = viewModel,
            onNewTab = onNewTab,
            onNewIncognitoTab = onNewIncognitoTab,
            onOpenHistory = onOpenHistory,
            onBurnData = onBurnData,
            onOpenDownloads = onOpenDownloads,
            onOpenBookmarks = onOpenBookmarks,
            onOpenSettings = onOpenSettings,
            onOpenPasswordManager = onOpenPasswordManager,
            onShowThemeSheet = onShowThemeSheet,
            onShowQuickTools = onShowQuickTools,
            onShowFeedbackDialog = onShowFeedbackDialog,
            onShowCustomizationSheet = onShowCustomizationSheet,
            onShowExtensions = onShowExtensions,
            onShowPlayerSettings = onShowPlayerSettings,
            onShowSiteInfo = onShowSiteInfo,
            onFindInPage = onFindInPage
        )
    }
}

@Composable
private fun GlobalActionCard(
    icon: ImageVector,
    customIcon: (@Composable () -> Unit)? = null,
    label: String,
    iconTint: Color,
    cardBg: Color,
    textColor: Color,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (isCompact) 12.dp else 16.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(vertical = if (isCompact) 8.dp else 12.dp, horizontal = if (isCompact) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (customIcon != null) {
            customIcon()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
            )
        }
        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = if (isCompact) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MinimalMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    textColor: Color,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompact) 2.dp else 4.dp, vertical = 0.5.dp)
            .clip(RoundedCornerShape(if (isCompact) 8.dp else 12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = if (isCompact) 4.5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(if (isCompact) 17.dp else 20.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = if (isCompact) 13.sp else 14.sp,
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
private fun MenuSectionLabel(text: String, textColor: Color, isCompact: Boolean = false) {
    Text(
        text = text.uppercase(),
        color = textColor,
        fontSize = if (isCompact) 9.5.sp else 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = if (isCompact) 0.5.sp else 0.8.sp,
        modifier = Modifier.padding(horizontal = if (isCompact) 8.dp else 16.dp, vertical = if (isCompact) 3.dp else 6.dp)
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
                                    text = stringResource(R.string.menu_find_in_page_hint),
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
