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
    onShowPlayerSettings: () -> Unit,
    onShowTabGroups: () -> Unit = {},
    onShowSiteInfo: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val config = getUiSizeConfig(viewModel.uiScale)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = config.paddingHorizontal, vertical = config.paddingVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = !isInputFocused) {
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
                .padding(horizontal = config.searchBoxHeight * 0.35f),
            contentAlignment = Alignment.CenterStart
        ) {
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
                                showSecurityIcon && isSecure -> Icons.Rounded.Lock
                                showSecurityIcon && isHttp -> Icons.Rounded.LockOpen
                                else -> Icons.Rounded.Search
                            },
                            contentDescription = "Search or Security icon",
                            modifier = Modifier.size(config.innerIconSize * 0.75f),
                            tint = when {
                                viewModel.isIncognitoMode -> Color(0xFFCBB2FF)
                                showSecurityIcon && isSecure -> Color(0xFF34C759)
                                showSecurityIcon && isHttp -> Color(0xFFFF9500)
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

                val bringIntoViewRequester = remember { BringIntoViewRequester() }

                BasicTextField(
                    value = if (inputUrl.text == "about:blank") androidx.compose.ui.text.input.TextFieldValue("") else inputUrl,
                    onValueChange = onInputUrlChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onInputFocusedChange(it.isFocused) }
                        .bringIntoViewRequester(bringIntoViewRequester),
                    onTextLayout = { textLayoutResult ->
                        val cursorStart = inputUrl.selection.start
                        if (cursorStart >= 0 && cursorStart <= inputUrl.text.length) {
                            val cursorRect = textLayoutResult.getCursorRect(cursorStart)
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView(cursorRect)
                            }
                        }
                    },
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



        // Toolbox — only visible in All-in-One mode
        AnimatedVisibility(visible = !isInputFocused && viewModel.chromeNavBarEnabled) {
            IconButton(
                onClick = onShowToolsSheet,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Icon(
                    imageVector = BlackholeIcon,
                    contentDescription = "Toolbox",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(config.innerIconSize)
                )
            }
        }

        AnimatedVisibility(visible = !isInputFocused) {
            IconButton(
                onClick = onShowExtensionsSheet,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = "Extensions",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(config.innerIconSize)
                    )
                    if (hasActiveUserExtensions) {
                        Box(
                            modifier = Modifier
                                .size(config.innerIconSize * 0.25f)
                                .offset(x = 1.dp, y = (-1).dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                     }
                }
            }
        }

        AnimatedVisibility(visible = !isInputFocused && (!viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            IconButton(
                onClick = onShowTabGroups,
                modifier = Modifier.size(config.barIconSize)
            ) {
                Box(
                    modifier = Modifier
                        .size(config.innerIconSize + 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = (config.fontSize.value * 0.66f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isInputFocused && (!viewModel.showBottomNavBar || viewModel.chromeNavBarEnabled)) {
            Box {
                IconButton(
                    onClick = { onShowMenuChange(true) },
                    modifier = Modifier.size(config.barIconSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(config.innerIconSize)
                    )
                }

                if (viewModel.chromeNavBarEnabled) {
                    ChromeMenuDropdown(
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
}

@Composable
fun ChromeMenuDropdown(
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

    val cardBg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val iconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF161618) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val iconBg = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = cardBg,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Page Info Header ──────────────────────────────
            if (!isHome && activeTab != null) {
                val pageTitle = activeTab.title?.takeIf { it.isNotBlank() } ?: "Webpage"
                val pageDomain = try {
                    android.net.Uri.parse(viewModel.currentUrl).host?.removePrefix("www.") ?: viewModel.currentUrl
                } catch (_: Exception) { viewModel.currentUrl }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor.copy(alpha = 0.18f), accentColor.copy(alpha = 0.06f))
                                ),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle,
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = pageDomain,
                            color = textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = dividerColor)
            }

            // ── Quick Action Pills ────────────────────────────
            val canForward = activeTab?.canGoForward == true
            val isBookmarked = !isHome && viewModel.isBookmarked(viewModel.currentUrl)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Forward
                MenuActionPill(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    label = "Forward",
                    enabled = canForward,
                    tint = if (canForward) iconTint else textSecondary.copy(alpha = 0.35f),
                    bg = if (canForward) surfaceVariant else surfaceVariant.copy(alpha = 0.4f),
                    onClick = { onDismissRequest(); viewModel.goForward() }
                )
                // Bookmark
                MenuActionPill(
                    icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    label = if (isBookmarked) "Saved" else "Save",
                    enabled = !isHome,
                    tint = if (!isHome) (if (isBookmarked) accentColor else iconTint) else textSecondary.copy(alpha = 0.35f),
                    bg = if (!isHome) (if (isBookmarked) accentColor.copy(alpha = 0.15f) else surfaceVariant) else surfaceVariant.copy(alpha = 0.4f),
                    onClick = {
                        onDismissRequest()
                        if (!isHome) {
                            if (isBookmarked) viewModel.removeBookmark(viewModel.currentUrl)
                            else viewModel.addToBookmarks(activeTab?.title ?: "Webpage", viewModel.currentUrl)
                        }
                    }
                )
                // Download
                MenuActionPill(
                    icon = Icons.Rounded.Download,
                    label = "Save PDF",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else textSecondary.copy(alpha = 0.35f),
                    bg = if (!isHome) surfaceVariant else surfaceVariant.copy(alpha = 0.4f),
                    onClick = { onDismissRequest(); if (!isHome) viewModel.printCurrentPage(context) }
                )
                // Info
                MenuActionPill(
                    icon = Icons.Rounded.Info,
                    label = "Info",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else textSecondary.copy(alpha = 0.35f),
                    bg = if (!isHome) surfaceVariant else surfaceVariant.copy(alpha = 0.4f),
                    onClick = { onDismissRequest(); if (!isHome) onShowSiteInfo() }
                )
                // Reload
                MenuActionPill(
                    icon = Icons.Rounded.Refresh,
                    label = "Reload",
                    enabled = !isHome,
                    tint = if (!isHome) iconTint else textSecondary.copy(alpha = 0.35f),
                    bg = if (!isHome) surfaceVariant else surfaceVariant.copy(alpha = 0.4f),
                    onClick = { onDismissRequest(); if (!isHome) viewModel.reload() }
                )
            }

            HorizontalDivider(color = dividerColor)
            // ── Tabs section header ───────────────────────────
            MenuSectionLabel(text = "Tabs", textColor = textSecondary)

            // New Tab
            LuxuryMenuItem(
                text = "New Tab",
                icon = Icons.Rounded.AddBox,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onNewTab()
                }
            )

            // New Incognito Tab
            LuxuryMenuItem(
                text = "New Incognito Tab",
                icon = Icons.Rounded.VisibilityOff,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onNewIncognitoTab()
                }
            )

            HorizontalDivider(color = dividerColor)
            MenuSectionLabel(text = "Browse", textColor = textSecondary)

            // History
            LuxuryMenuItem(
                text = "History",
                icon = Icons.Rounded.History,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onOpenHistory()
                }
            )

            // Delete browsing data
            LuxuryMenuItem(
                text = "Clear Browsing Data",
                icon = Icons.Rounded.LocalFireDepartment,
                iconTint = Color(0xFFFF3B30),
                iconBg = Color(0xFFFF3B30).copy(alpha = 0.12f),
                textColor = Color(0xFFFF3B30),
                onClick = {
                    onDismissRequest()
                    onBurnData()
                }
            )

            HorizontalDivider(color = dividerColor)
            MenuSectionLabel(text = "Library", textColor = textSecondary)

            // Downloads
            LuxuryMenuItem(
                text = "Downloads",
                icon = Icons.Rounded.Download,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onOpenDownloads()
                }
            )

            // Bookmarks
            LuxuryMenuItem(
                text = "Bookmarks",
                icon = Icons.Rounded.Bookmark,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onOpenBookmarks()
                }
            )

            // Desktop Site (only show if not on Home screen)
            if (!isHome) {
                LuxuryMenuItem(
                    text = "Desktop Site",
                    icon = Icons.Rounded.Computer,
                    iconTint = iconTint,
                    iconBg = iconBg,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        viewModel.toggleDesktopMode(context)
                    },
                    trailingContent = {
                        Switch(
                            checked = viewModel.isDesktopMode,
                            onCheckedChange = {
                                onDismissRequest()
                                viewModel.toggleDesktopMode(context)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                )
            }

            // Find in page (only when a page is open)
            if (!isHome) {
                LuxuryMenuItem(
                    text = "Find in Page",
                    icon = Icons.Rounded.Search,
                    iconTint = iconTint,
                    iconBg = iconBg,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onFindInPage()
                    }
                )
            }

            // Add to shortcuts (only when a page is open)
            if (!isHome) {
                LuxuryMenuItem(
                    text = "Add to Shortcuts",
                    icon = Icons.Rounded.AddCircle,
                    iconTint = iconTint,
                    iconBg = iconBg,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        val currentUrl = viewModel.currentUrl
                        val currentTitle = activeTab?.title ?: "Webpage"
                        viewModel.addShortcut(currentTitle, currentUrl)
                    }
                )
            }

            // Extensions (only show if not on Home screen)
            if (!isHome) {
                LuxuryMenuItem(
                    text = "Extensions",
                    icon = Icons.Rounded.Extension,
                    iconTint = iconTint,
                    iconBg = iconBg,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onShowExtensions()
                    }
                )
            }

            // Player Settings
            LuxuryMenuItem(
                text = "Player Settings",
                icon = Icons.Rounded.PlayCircle,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onShowPlayerSettings()
                }
            )

            HorizontalDivider(color = dividerColor)
            MenuSectionLabel(text = "App", textColor = textSecondary)

            // Settings
            LuxuryMenuItem(
                text = "Settings",
                icon = Icons.Rounded.Settings,
                iconTint = iconTint,
                iconBg = iconBg,
                textColor = textPrimary,
                onClick = {
                    onDismissRequest()
                    onOpenSettings()
                }
            )

            // Customize home screen (only show if on Home screen)
            if (isHome) {
                LuxuryMenuItem(
                    text = "Customize Home",
                    icon = Icons.Rounded.Edit,
                    iconTint = iconTint,
                    iconBg = iconBg,
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onShowCustomizationSheet()
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
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
    val bg = if (viewModel.isAmoledMode) Color(0xFF000000) else if (isDark) Color(0xFF1C1C1E) else Color.White
    val border = if (viewModel.isAmoledMode) Color(0xFF1A1A1A) else if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
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

        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        
        var protocolEnd = 0
        if (rawText.startsWith("https://")) {
            protocolEnd = 8
        } else if (rawText.startsWith("http://")) {
            protocolEnd = 7
        }

        val domainStart = protocolEnd
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

        // Protocol
        if (protocolEnd > 0) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = pathColor))
            builder.append(rawText.substring(0, protocolEnd))
            builder.pop()
        }

        // Domain
        builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = domainColor, fontWeight = FontWeight.Bold))
        builder.append(rawText.substring(domainStart, domainEnd))
        builder.pop()

        // Path / params
        if (domainEnd < rawText.length) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = pathColor))
            builder.append(rawText.substring(domainEnd))
            builder.pop()
        }

        return androidx.compose.ui.text.input.TransformedText(
            builder.toAnnotatedString(),
            androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
}
