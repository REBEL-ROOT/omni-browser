/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonsLayoutScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.buttons_layout_title),
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f)))
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        val adaptiveContentCap = com.rebelroot.omni.ui.adaptive.rememberAdaptiveUiMetrics().screenContentMaxWidth
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = adaptiveContentCap)
                .fillMaxHeight()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Home screen sections — reorderable top-to-bottom (drag and drop).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.home_sections_title),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(id = R.string.home_sections_hint),
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                    ChromeDragLayoutEditor(
                        surface = com.rebelroot.omni.browser.chrome.ChromeSurface.HOME_SECTIONS,
                        layout = viewModel.homeSectionsLayout,
                        onLayoutChange = { viewModel.saveHomeSectionsLayout(context, it) },
                        columns = 3,
                        minItems = 0
                    )
                }
            }

            // Navigation bar buttons — user-owned layouts for both bottom bars.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.nav_buttons_title),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ChromeDragLayoutEditor(
                        title = stringResource(id = R.string.nav_home_bar),
                        surface = com.rebelroot.omni.browser.chrome.ChromeSurface.HOME_BAR,
                        layout = viewModel.navHomeLayout,
                        onLayoutChange = { viewModel.saveNavLayout(context, true, it) }
                    )
                    HorizontalDivider(color = dividerColor)
                    ChromeDragLayoutEditor(
                        title = stringResource(id = R.string.nav_page_bar),
                        surface = com.rebelroot.omni.browser.chrome.ChromeSurface.PAGE_BAR,
                        layout = viewModel.navPageLayout,
                        onLayoutChange = { viewModel.saveNavLayout(context, false, it) }
                    )
                }
            }

            // Address bar (all-in-one) buttons — leading/trailing clusters around the omnibox.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(id = R.string.addressbar_buttons_title),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        stringResource(id = R.string.addressbar_hint),
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                    ChromeDragLayoutEditor(
                        title = stringResource(id = R.string.addressbar_leading),
                        surface = com.rebelroot.omni.browser.chrome.ChromeSurface.ADDRESS_BAR,
                        layout = viewModel.addressBarLeadingLayout,
                        onLayoutChange = { viewModel.saveAddressBarLayout(context, true, it) },
                        minItems = 0
                    )
                    HorizontalDivider(color = dividerColor)
                    ChromeDragLayoutEditor(
                        title = stringResource(id = R.string.addressbar_trailing),
                        surface = com.rebelroot.omni.browser.chrome.ChromeSurface.ADDRESS_BAR,
                        layout = viewModel.addressBarTrailingLayout,
                        onLayoutChange = { viewModel.saveAddressBarLayout(context, false, it) },
                        minItems = 0
                    )
                }
            }
        }
    }
}
