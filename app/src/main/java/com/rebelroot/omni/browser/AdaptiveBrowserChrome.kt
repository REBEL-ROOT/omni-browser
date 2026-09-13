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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rebelroot.omni.R
import com.rebelroot.omni.ui.adaptive.AdaptiveUiMetrics
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────────
// Adaptive tablet chrome (≥600dp windows).
//
// These composables replace the old fixed-dimension tablet branch inside
// BrowserScreen. All sizing comes from [AdaptiveUiMetrics]; the phone chrome
// (PhoneAddressBar) is untouched and remains the compact-width path.
// ─────────────────────────────────────────────────────────────────────────────────

/** Chrome color set shared by the tab strip, toolbar and rail so they stay consistent. */
private data class TabletChromeColors(
    val barBackground: Color,
    val content: Color,
    val activeTabBackground: Color,
    val activeTabText: Color,
    val inactiveTabText: Color,
    val divider: Color,
)

@Composable
private fun tabletChromeColors(
    isDarkTheme: Boolean,
    isAmoled: Boolean,
    isIncognito: Boolean,
): TabletChromeColors {
    val darkish = isDarkTheme || isIncognito
    return TabletChromeColors(
        barBackground = if (isAmoled) Color(0xFF000000) else if (darkish) Color(0xFF1C1C1E) else Color(0xFFF1F3F4),
        content = if (darkish) Color.White else Color(0xFF202124),
        activeTabBackground = if (darkish) Color(0xFF2C2C2E) else Color.White,
        activeTabText = if (darkish) Color.White else Color(0xFF202124),
        inactiveTabText = if (darkish) Color.White.copy(alpha = 0.6f) else Color(0xFF606266),
        divider = if (isDarkTheme) Color(0xFF16222F) else Color(0x1F000000),
    )
}

/**
 * Responsive tablet tab strip. Tab width is computed from the actual available
 * width (min/max clamped) instead of the old fixed 164dp, so a 600dp window with
 * many tabs scrolls horizontally while a 1600dp desktop window lets a handful of
 * tabs breathe. The active tab is always scrolled into view after selection, the
 * close button keeps a comfortable hit target, and the "+" button is fixed
 * outside the scroll region so a new tab is always one tap away.
 */
@Composable
fun OmniTabStrip(
    tabs: List<TabState>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (TabState) -> Unit,
    onNewTab: () -> Unit,
    metrics: AdaptiveUiMetrics,
    isDarkTheme: Boolean,
    isAmoled: Boolean,
    isIncognito: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = tabletChromeColors(isDarkTheme, isAmoled, isIncognito)
    val listState = rememberLazyListState()
    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }

    // Keep the active tab visible whenever selection changes (multi-window,
    // locale changes and normal tab switches all land here).
    LaunchedEffect(activeTabId, tabs.size) {
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(activeIndex) }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.tabStripHeight)
                .background(colors.barBackground)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val availableForTabs = maxWidth - metrics.tabSpacing
                val tabCount = tabs.size.coerceAtLeast(1)
                // Fit as many tabs as possible without scrolling; clamp to the
                // metric range so a few tabs never stretch absurdly and many
                // tabs never become unusably narrow.
                val tabWidth = (availableForTabs / tabCount)
                    .coerceIn(metrics.tabMinWidth, metrics.tabMaxWidth)

                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.tabSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(if (isActive) colors.activeTabBackground else Color.Transparent)
                                .clickable { onSelectTab(tab.id) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (tab.title.isNullOrBlank()) stringResource(R.string.new_tab_title) else tab.title,
                                color = if (isActive) colors.activeTabText else colors.inactiveTabText,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )

                            if (tabs.size > 1 || isIncognito) {
                                // Full tab-height hit target so the glyph never clips
                                // and the touch area stays practical.
                                Box(
                                    modifier = Modifier
                                        .width(metrics.tabCloseTargetWidth)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(tab) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.close_tab_desc),
                                        tint = (if (isActive) colors.activeTabText else colors.inactiveTabText).copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // "+" is deliberately OUTSIDE the LazyRow: always visible, never scrolled away.
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(metrics.toolbarTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.menu_new_tab),
                    tint = colors.content,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = colors.divider)
    }
}

/**
 * Deterministic toolbar action priority used to decide what the adaptive tablet
 * toolbar drops when horizontal room runs out or the address field is focused.
 *
 * P0 (never hidden): address field + menu — a browser without an address field
 *                    or an entry point to the rest of the UI is broken.
 * P1 (kept while idle, hidden while focused): back/forward/home navigation —
 *                    still reachable via gestures/history while typing.
 * P2 (first to drop): tools.
 * P3 (drops last of the secondaries): extensions (the active-extension badge is a
 *                    status signal, so it outlives tools).
 */
@Composable
fun AdaptiveTabletToolbar(
    inputUrl: TextFieldValue,
    onInputUrlChange: (TextFieldValue) -> Unit,
    isInputFocused: Boolean,
    onInputFocusedChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onCommitUrl: (String) -> Unit,
    onClearInput: () -> Unit,
    currentUrl: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    hasActiveUserExtensions: Boolean,
    onShowExtensions: () -> Unit,
    onShowTools: () -> Unit,
    onShowMenu: () -> Unit,
    onShowSiteInfo: () -> Unit,
    onShowSpeedDial: () -> Unit,
    showSpeedDialButton: Boolean,
    historySuggestions: List<HistoryEntry>,
    onSelectSuggestion: (HistoryEntry) -> Unit,
    metrics: AdaptiveUiMetrics,
    isDarkTheme: Boolean,
    isAmoled: Boolean,
    isIncognito: Boolean,
    isHomeScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = tabletChromeColors(isDarkTheme, isAmoled, isIncognito)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val touch = metrics.toolbarTouchTarget
        val gap = metrics.chromeGroupSpacing
        val horizontalPadding = metrics.chromeHorizontalPadding

        // Reserved widths for each logical group.
        val navClusterBase = touch * 3 + gap * 2
        val menuWidth = touch
        val fieldMin = metrics.addressFieldMinWidth
        val extensionsWidth = touch
        val toolsWidth = touch
        val speedDialWidth = touch
        val gutters = horizontalPadding * 2 + gap * 2

        fun fits(groups: List<Dp>): Boolean {
            var total = 0.dp
            for (g in groups) total += g
            return availableWidth >= total + gutters
        }

        // Speed dial is phone-bar parity; it collapses FIRST (P2), then tools,
        // then extensions (P3, P4). Menu + address field never hide.
        val showSpeedDial = showSpeedDialButton && !isInputFocused &&
            fits(listOf(navClusterBase, fieldMin, speedDialWidth, menuWidth))
        val navClusterWidth = navClusterBase + if (showSpeedDial) speedDialWidth + gap else 0.dp

        // Deterministic collapse: while focused everything secondary yields to
        // Cancel + field.
        val showNavigation = !isInputFocused || fits(listOf(navClusterWidth, fieldMin, menuWidth))
        val showTools = !isInputFocused && fits(listOf(navClusterWidth, fieldMin, toolsWidth, menuWidth))
        val showExtensions = !isInputFocused && fits(listOf(navClusterWidth, fieldMin, extensionsWidth, toolsWidth, menuWidth))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.toolbarHeight)
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                if (showNavigation) {
                    IconButton(
                        onClick = onBack,
                        enabled = canGoBack && !isHomeScreen,
                        modifier = Modifier.size(touch)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack && !isHomeScreen) colors.content else colors.content.copy(alpha = 0.2f),
                            modifier = Modifier.size(metrics.toolbarIconSize)
                        )
                    }

                    IconButton(
                        onClick = onForward,
                        enabled = canGoForward,
                        modifier = Modifier.size(touch)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) colors.content else colors.content.copy(alpha = 0.2f),
                            modifier = Modifier.size(metrics.toolbarIconSize)
                        )
                    }

                    IconButton(
                        onClick = onHome,
                        modifier = Modifier.size(touch)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "New Tab",
                            tint = colors.content,
                            modifier = Modifier.size(metrics.toolbarIconSize)
                        )
                    }

                    // Speed Dial — same placement/condition as the phone address bar.
                    if (showSpeedDial) {
                        IconButton(
                            onClick = onShowSpeedDial,
                            modifier = Modifier.size(touch)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "Speed Dial",
                                tint = colors.content,
                                modifier = Modifier.size(metrics.toolbarIconSize)
                            )
                        }
                    }
                }

                // ── Address field (P0, weighted, min width enforced by collapse rules) ─
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(metrics.addressFieldHeight)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isInputFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isInputFocused) {
                        // Leading pill icon: site controls (Tune) on a loaded page —
                        // tap opens site info, exactly like the phone address bar.
                        val hasPage = currentUrl.isNotEmpty() && currentUrl != "about:blank"
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable(enabled = hasPage && !isIncognito) { onShowSiteInfo() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isIncognito -> Icons.Rounded.VisibilityOff
                                    hasPage -> Icons.Rounded.Tune
                                    else -> Icons.Rounded.Search
                                },
                                contentDescription = if (hasPage && !isIncognito) "Site info" else "Search icon",
                                modifier = Modifier.size(16.dp),
                                tint = when {
                                    isIncognito -> Color(0xFFCBB2FF)
                                    hasPage -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

                    BasicTextField(
                        value = if (inputUrl.text == "about:blank") TextFieldValue("") else inputUrl,
                        onValueChange = onInputUrlChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused && !isInputFocused) {
                                    onInputUrlChange(inputUrl.copy(selection = TextRange(0, inputUrl.text.length)))
                                }
                                onInputFocusedChange(state.isFocused)
                            }
                            .bringIntoViewRequester(bringIntoViewRequester),
                        onTextLayout = { textLayoutResult ->
                            val cursorStart = inputUrl.selection.start
                            val layoutTextLength = textLayoutResult.layoutInput.text.length
                            if (cursorStart >= 0 && cursorStart <= layoutTextLength) {
                                try {
                                    val cursorRect = textLayoutResult.getCursorRect(cursorStart)
                                    coroutineScope.launch {
                                        bringIntoViewRequester.bringIntoView(cursorRect)
                                    }
                                } catch (_: Throwable) {
                                    // Safely ignore transient layout bounds mismatch
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                onCommitUrl(inputUrl.text)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = urlTransformation
                    )

                    if (inputUrl.text.isNotEmpty() && inputUrl.text != "about:blank") {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onClearInput() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (currentUrl.isNotEmpty() && currentUrl != "about:blank" && !isInputFocused) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onToggleBookmark() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Cancel (focused mode) — sits between field and the menu so it
                //    can never collide with extensions/tools (hidden while focused).
                if (isInputFocused) {
                    TextButton(
                        onClick = {
                            onInputFocusedChange(false)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        modifier = Modifier.widthIn(min = metrics.cancelActionWidth)
                    ) {
                        Text(
                            "Cancel",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize
                        )
                    }
                }

                // ── Secondary actions (P2/P3, dropped deterministically) ─────────
                if (showExtensions) {
                    IconButton(
                        onClick = onShowExtensions,
                        modifier = Modifier.size(touch)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = stringResource(R.string.ext_menu_cd),
                                tint = colors.content,
                                modifier = Modifier.size(metrics.toolbarIconSize)
                            )
                            if (hasActiveUserExtensions) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
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

                if (showTools) {
                    IconButton(
                        onClick = onShowTools,
                        modifier = Modifier.size(touch)
                    ) {
                        Icon(
                            imageVector = BlackholeIcon,
                            contentDescription = "Tools",
                            tint = colors.content,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Menu — P0, always visible.
                IconButton(
                    onClick = onShowMenu,
                    modifier = Modifier.size(touch)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Menu",
                        tint = colors.content,
                        modifier = Modifier.size(metrics.toolbarIconSize)
                    )
                }
            }

            // ── Focused-mode suggestions (same data as the phone bar) ────────────
            AnimatedVisibility(
                visible = isInputFocused && historySuggestions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAmoled) Color(0xFF000000) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        historySuggestions.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSuggestion(entry) }
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
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.title.isNotBlank()) {
                                        Text(
                                            text = entry.url,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
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
    }
}

/**
 * Persistent navigation rail for expanded+ browser windows (≥840dp). Replaces the
 * phone bottom bar, which would either duplicate the tablet toolbar or stretch a
 * 5-button row across a desktop-class window. Items reuse Omni's existing
 * interaction model (tab groups sheet, quick tools, all-in-one menu) and visuals.
 */
@Composable
fun BrowserNavigationRail(
    showHomeContent: Boolean,
    tabCount: Int,
    hasActiveUserExtensions: Boolean,
    onNewTab: () -> Unit,
    onShowTabGroups: () -> Unit,
    onShowQuickTools: () -> Unit,
    onShowExtensions: () -> Unit,
    onShowMenu: () -> Unit,
    onCustomizeHome: () -> Unit,
    onOpenNews: () -> Unit,
    metrics: AdaptiveUiMetrics,
    isDarkTheme: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
) {
    val darkish = isDarkTheme || isAmoled
    val railBg = if (isAmoled) Color(0xFF000000) else if (darkish) Color(0xFF1C1C1E) else Color(0xFFF1F3F4)
    val content = if (darkish) Color.White else Color(0xFF202124)

    Surface(
        modifier = modifier.width(metrics.navigationRailWidth),
        color = railBg,
        contentColor = content
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // New tab — first class action on large screens.
            RailActionButton(onClick = onNewTab) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.menu_new_tab),
                    tint = content,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (showHomeContent) {
                RailActionButton(onClick = onCustomizeHome) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.customize_home_cd),
                        tint = content,
                        modifier = Modifier.size(24.dp)
                    )
                }
                RailActionButton(onClick = onOpenNews) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Article,
                        contentDescription = stringResource(R.string.news_center_cd),
                        tint = content,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Tabs → existing tab groups sheet, count badge like the phone bar.
                RailActionButton(onClick = onShowTabGroups) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, content, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabCount.toString(),
                            color = content,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            RailActionButton(onClick = onShowQuickTools) {
                Icon(
                    imageVector = BlackholeIcon,
                    contentDescription = "Tools",
                    tint = content,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (hasActiveUserExtensions) {
                RailActionButton(onClick = onShowExtensions) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = stringResource(R.string.ext_menu_cd),
                        tint = content,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            RailActionButton(onClick = onShowMenu) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = content,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun RailActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}


/**
 * Places the optional navigation rail beside the browser scaffold WITHOUT
 * introducing a RowScope. A Row would insert an implicit-scope receiver between
 * BrowserScreen's root Box and overlay children that call `Modifier.align`,
 * breaking their resolution — a scope-free [Layout] keeps every existing
 * implicit receiver intact.
 */
@Composable
fun AdaptiveBrowserShell(
    showRail: Boolean,
    railContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The content subtree must keep the SAME composition slot whether the rail is
    // shown or not — branching between two different structures here would dispose
    // and recreate the whole browser content (losing text field focus whenever the
    // rail appears/disappears). The Layout node is unconditional; only the rail's
    // emission toggles.
    Layout(content = {
        if (showRail) railContent()
        content()
    }, modifier = modifier) { measurables, constraints ->
        // railContent emits no measurable when empty, so a single-child layout
        // means the rail was disabled by a concurrent state change.
        val hasRail = measurables.size == 2
        val railMs = if (hasRail) measurables[0] else null
        val contentMs = if (hasRail) measurables[1] else measurables[0]

        val railPlaceable = railMs?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val railWidth = railPlaceable?.width ?: 0
        val contentConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = (constraints.maxWidth - railWidth).coerceAtLeast(0)
        )
        val contentPlaceable = contentMs.measure(contentConstraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            railPlaceable?.placeRelative(0, 0)
            contentPlaceable.placeRelative(railWidth, 0)
        }
    }
}
