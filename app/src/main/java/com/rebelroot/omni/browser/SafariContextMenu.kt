/*
 * Omni Browser - Safari-style link/image context menu with live preview.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rebelroot.omni.R

// ── Tracking parameters stripped by "Copy Clean Link" ────────────────────────
private val TRACKING_PARAMS = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
    "utm_id", "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
    "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
    "fbclid", "fb_action_ids", "fb_action_types",
    "msclkid", "mc_eid", "mc_cid",
    "ref", "referrer", "_ga", "_gl",
    "igshid", "twclid", "li_fat_id",
    "ttclid", "rdt_cid", "epik"
)

private fun cleanUrl(raw: String): String {
    return try {
        val uri = android.net.Uri.parse(raw)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames
            .filter { it.lowercase() !in TRACKING_PARAMS }
            .forEach { key -> uri.getQueryParameter(key)?.let { builder.appendQueryParameter(key, it) } }
        builder.build().toString()
    } catch (_: Exception) {
        raw
    }
}

private fun guessFilename(url: String): String {
    return try {
        val path = android.net.Uri.parse(url).lastPathSegment ?: "file"
        if (path.contains('.')) path else "$path.bin"
    } catch (_: Exception) {
        "download"
    }
}

private fun extractHost(url: String): String {
    return try {
        android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}

private fun copyToClip(context: Context, label: String, text: String, toast: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

// ── Main composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafariContextMenuSheet(
    viewModel: BrowserViewModel,
    context: Context
) {
    val menu = viewModel.activeContextMenu ?: return
    val isDark = viewModel.isDarkThemeEnabled
    val isAmoled = viewModel.isAmoledMode

    // Colors matching the app theme
    val sheetBg = Color.Transparent
    val cardBg = when {
        isAmoled -> Color(0xFF0C0D10)
        isDark   -> Color(0xFF1C1E26)
        else     -> Color(0xFFF2F2F7)
    }
    val cardBorder = when {
        isAmoled -> Color(0xFF1A1A1A)
        isDark   -> Color(0xFF2C2E38)
        else     -> Color(0xFFDDDDE0)
    }
    val headerBg = when {
        isAmoled -> Color(0xFF111216)
        isDark   -> Color(0xFF252830)
        else     -> Color(0xFFFFFFFF)
    }
    val divColor = when {
        isAmoled -> Color(0xFF1A1A1A)
        isDark   -> Color(0xFF2C2E38)
        else     -> Color(0xFFE0E0E5)
    }
    val textPrimary = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val iconTint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF8E8E93)
    val grabberColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)

    val isImage = !menu.srcUri.isNullOrEmpty()
    val targetUrl = (if (isImage) menu.srcUri else menu.linkUri) ?: return
    val activeTabUrl = viewModel.currentUrl
    val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: ""
    val host = extractHost(targetUrl)

    // Group the active tab's group ID for "Open in Group"
    val activeGroupId = remember(viewModel.activeTabId, viewModel.tabGroups.size) {
        viewModel.tabGroups.find { it.tabIds.contains(viewModel.activeTabId) }?.id
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissContextMenu() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Grabber ───────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(grabberColor)
            )
            Spacer(Modifier.height(4.dp))

            // ── Card 1: Preview ───────────────────────────────────────────────
            PreviewCard(
                menu = menu,
                targetUrl = targetUrl,
                host = host,
                isImage = isImage,
                isDark = isDark,
                cardBg = cardBg,
                cardBorder = cardBorder,
                headerBg = headerBg,
                divColor = divColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {
                    viewModel.dismissContextMenu()
                    viewModel.loadUrl(targetUrl)
                }
            )

            // ── Card 2: Actions ───────────────────────────────────────────────
            ActionsCard(
                menu = menu,
                targetUrl = targetUrl,
                isImage = isImage,
                isDark = isDark,
                isAmoled = isAmoled,
                cardBg = cardBg,
                cardBorder = cardBorder,
                divColor = divColor,
                textPrimary = textPrimary,
                iconTint = iconTint,
                activeGroupId = activeGroupId,
                viewModel = viewModel,
                context = context
            )
        }
    }
}

// ── Card 1: Preview ───────────────────────────────────────────────────────────

@Composable
private fun PreviewCard(
    menu: ContextMenuElement,
    targetUrl: String,
    host: String,
    isImage: Boolean,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    headerBg: Color,
    divColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, cardBorder),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row: favicon placeholder + host + URL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Favicon via Google S2
                AsyncImage(
                    model = "https://www.google.com/s2/favicons?domain=$host&sz=64",
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isImage) stringResource(R.string.ctx_image_option) else host,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = targetUrl,
                        fontSize = 11.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = divColor, thickness = 0.5.dp)

            // Preview body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(if (isDark) Color(0xFF111216) else Color(0xFFF9F9FB)),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    AsyncImage(
                        model = menu.srcUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    LinkWebPreview(
                        url = targetUrl,
                        isDark = isDark
                    )
                }
                
                // Overlay Box to capture clicks for the entire preview body and prevent interaction with WebView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { onClick() }
                )
            }
        }
    }
}

// ── Miniature WebView link preview ───────────────────────────────────────────

@Composable
private fun LinkWebPreview(url: String, isDark: Boolean) {
    var isLoading by remember { mutableStateOf(true) }
    val bgColor = if (isDark) 0xFF111216.toInt() else 0xFFF9F9FB.toInt()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        blockNetworkImage = false
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        savePassword = false
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                    isClickable = false
                    isFocusable = false
                    setBackgroundColor(bgColor)
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(
                            view: android.webkit.WebView?,
                            loadedUrl: String?
                        ) {
                            isLoading = false
                        }
                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ) = false // allow redirects
                    }
                    loadUrl(url)
                }
            },
            // Layout fills the full 200dp container, then graphicsLayer scales
            // it down visually to 50% so the full page viewport is visible.
            // graphicsLayer is draw-only — it does NOT shrink the layout box,
            // so the WebView still measures at 200dp and renders a real page.
            // We use layout modifier to size the WebView at 2x bounds and report
            // 1x layout bounds, and graphicsLayer to scale it down to exactly fit.
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = constraints.maxWidth * 2,
                            maxWidth = constraints.maxWidth * 2,
                            minHeight = constraints.maxHeight * 2,
                            maxHeight = constraints.maxHeight * 2
                        )
                    )
                    layout(placeable.width / 2, placeable.height / 2) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    scaleX = 0.5f
                    scaleY = 0.5f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF8E8E93),
                strokeWidth = 2.5.dp
            )
        }
    }
}

// ── Card 2: Actions ───────────────────────────────────────────────────────────

@Composable
private fun ActionsCard(
    menu: ContextMenuElement,
    targetUrl: String,
    isImage: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    cardBg: Color,
    cardBorder: Color,
    divColor: Color,
    textPrimary: Color,
    iconTint: Color,
    activeGroupId: String?,
    viewModel: BrowserViewModel,
    context: Context
) {
    val errorColor = Color(0xFFFF453A)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, cardBorder),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Open in New Tab
            SafariMenuItem(
                label = stringResource(R.string.ctx_open_new_tab),
                icon = Icons.Rounded.OpenInBrowser,
                textColor = textPrimary,
                iconTint = iconTint,
                onClick = {
                    viewModel.dismissContextMenu()
                    viewModel.createNewTab(context, targetUrl)
                }
            )
            HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

            // Open in New Tab in Group
            SafariMenuItem(
                label = stringResource(R.string.ctx_open_new_tab_group),
                icon = Icons.Rounded.GridView,
                textColor = textPrimary,
                iconTint = iconTint,
                onClick = {
                    viewModel.dismissContextMenu()
                    if (activeGroupId != null) {
                        viewModel.createNewTab(context, targetUrl, groupId = activeGroupId)
                    } else {
                        // No active group — create one with both tabs
                        val newGroupColor = 0xFF5E81F4
                        viewModel.createTabGroup("Group", newGroupColor, viewModel.activeTabId)
                        val newGroupId = viewModel.tabGroups.lastOrNull()?.id
                        viewModel.createNewTab(context, targetUrl, groupId = newGroupId)
                    }
                }
            )
            HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

            // Open in Private Tab
            SafariMenuItem(
                label = stringResource(R.string.ctx_open_private_tab),
                icon = Icons.Rounded.VisibilityOff,
                textColor = textPrimary,
                iconTint = iconTint,
                onClick = {
                    viewModel.dismissContextMenu()
                    viewModel.createNewTab(context, targetUrl, isIncognito = true)
                }
            )
            HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

            // Copy link address / Copy image link
            SafariMenuItem(
                label = if (isImage) stringResource(R.string.ctx_copy_image_link)
                        else stringResource(R.string.ctx_copy_link_address),
                icon = Icons.Rounded.ContentCopy,
                textColor = textPrimary,
                iconTint = iconTint,
                onClick = {
                    viewModel.dismissContextMenu()
                    val toast = if (isImage) context.getString(R.string.ctx_image_link_copied)
                                else context.getString(R.string.ctx_link_copied)
                    copyToClip(context, "URL", targetUrl, toast)
                }
            )

            if (!isImage) {
                HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                // Copy Clean Link
                SafariMenuItem(
                    label = stringResource(R.string.ctx_copy_clean_link),
                    icon = Icons.Rounded.CleaningServices,
                    textColor = textPrimary,
                    iconTint = iconTint,
                    onClick = {
                        viewModel.dismissContextMenu()
                        copyToClip(context, "URL", cleanUrl(targetUrl),
                            context.getString(R.string.ctx_clean_link_copied))
                    }
                )

                if (!menu.linkText.isNullOrBlank()) {
                    HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                    // Copy Link Text
                    SafariMenuItem(
                        label = stringResource(R.string.ctx_copy_link_text),
                        icon = Icons.Rounded.TextFields,
                        textColor = textPrimary,
                        iconTint = iconTint,
                        onClick = {
                            viewModel.dismissContextMenu()
                            copyToClip(context, "Text", menu.linkText,
                                context.getString(R.string.ctx_link_text_copied))
                        }
                    )
                }

                HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                // Download Link
                SafariMenuItem(
                    label = stringResource(R.string.ctx_download_link),
                    icon = Icons.Rounded.FileDownload,
                    textColor = textPrimary,
                    iconTint = iconTint,
                    onClick = {
                        viewModel.dismissContextMenu()
                        val filename = guessFilename(targetUrl)
                        viewModel.pendingGenericDownload = BrowserViewModel.PendingGenericDownload(
                            url = targetUrl,
                            filename = filename,
                            contentType = null
                        )
                    }
                )

                HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                // Add to Bookmarks
                SafariMenuItem(
                    label = stringResource(R.string.ctx_add_to_bookmarks),
                    icon = Icons.Rounded.BookmarkAdd,
                    textColor = textPrimary,
                    iconTint = iconTint,
                    onClick = {
                        viewModel.dismissContextMenu()
                        val title = menu.linkText?.ifBlank { null }
                            ?: extractHost(targetUrl)
                        viewModel.addToBookmarks(title, targetUrl)
                        Toast.makeText(context, context.getString(R.string.ctx_bookmark_saved),
                            Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (isImage) {
                HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                // Search with Google Lens (web)
                SafariMenuItem(
                    label = stringResource(R.string.ctx_search_image_lens),
                    icon = Icons.Rounded.CameraAlt,
                    textColor = textPrimary,
                    iconTint = iconTint,
                    onClick = {
                        viewModel.dismissContextMenu()
                        val encoded = android.net.Uri.encode(targetUrl)
                        viewModel.createNewTab(context, "https://lens.google.com/uploadbyurl?url=$encoded")
                    }
                )
                HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

                // Search with Google Lens app
                SafariMenuItem(
                    label = stringResource(R.string.ctx_search_image_lens_app),
                    icon = Icons.Rounded.Camera,
                    textColor = textPrimary,
                    iconTint = iconTint,
                    onClick = {
                        viewModel.dismissContextMenu()
                        try {
                            val intent = Intent("com.google.lens.intent.action.LENS_INPUT")
                                .setPackage("com.google.android.googlequicksearchbox")
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context,
                                context.getString(R.string.ctx_lens_not_installed),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            HorizontalDivider(color = divColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

            // Share Link
            SafariMenuItem(
                label = stringResource(R.string.ctx_share_link),
                icon = Icons.Rounded.Share,
                textColor = textPrimary,
                iconTint = iconTint,
                onClick = {
                    viewModel.dismissContextMenu()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, targetUrl)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(
                        Intent.createChooser(intent, null).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            )
        }
    }
}

// ── SafariMenuItem ────────────────────────────────────────────────────────────

@Composable
private fun SafariMenuItem(
    label: String,
    icon: ImageVector,
    textColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}
