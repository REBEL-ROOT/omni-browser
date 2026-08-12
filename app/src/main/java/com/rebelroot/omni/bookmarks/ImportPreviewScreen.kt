/*
 * Omni Browser - Bookmark Import Preview Screen
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 05: UI for previewing what an import will do before the user confirms.
 * Shows the folder tree, counts, warnings, and lets the user pick a duplicate
 * policy (KEEP_BOTH, SKIP, REPLACE, MERGE).
 */

package com.rebelroot.omni.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.bookmarks.importexport.*
import com.rebelroot.omni.bookmarks.model.BookmarkNode
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onImportComplete: (ImportConfirmationResult) -> Unit
) {
    val preview = viewModel.importPreview
    var selectedPolicy by remember { mutableStateOf(DuplicatePolicy.KEEP_BOTH) }
    var showPolicyMenu by remember { mutableStateOf(false) }
    var showWarnings by remember { mutableStateOf(false) }
    var showIssues by remember { mutableStateOf(false) }

    val context = LocalContext.current

    BackHandler {
        viewModel.clearImportPreview()
        onNavigateBack()
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = Color(0xFFFFA726)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.import_preview_title),
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearImportPreview()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f))
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                color = cardColor,
                border = BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Duplicate policy selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.import_duplicate_policy),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondaryColor
                        )
                        Box {
                            TextButton(
                                onClick = { showPolicyMenu = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = policyLabel(selectedPolicy),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showPolicyMenu,
                                onDismissRequest = { showPolicyMenu = false }
                            ) {
                                DuplicatePolicy.entries.forEach { policy ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = policyLabel(policy),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = policyDescription(policy),
                                                    fontSize = 12.sp,
                                                    color = textSecondaryColor
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedPolicy = policy
                                            showPolicyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Confirm / Cancel buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearImportPreview()
                                onNavigateBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(id = R.string.import_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.confirmImport(
                                    context = context,
                                    policy = selectedPolicy,
                                    onResult = onImportComplete
                                )
                            },
                            enabled = preview != null && !preview.hasFatalIssues && !viewModel.isImporting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (viewModel.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(id = R.string.import_confirm))
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (preview == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(bgColor),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary cards
            item {
                ImportSummaryCard(
                    preview = preview,
                    cardColor = cardColor,
                    cardBorderColor = cardBorderColor,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    errorColor = errorColor,
                    warningColor = warningColor
                )
            }

            // Warnings section (collapsible)
            if (preview.hasWarnings) {
                item {
                    WarningsSection(
                        warnings = preview.warnings,
                        expanded = showWarnings,
                        onToggle = { showWarnings = !showWarnings },
                        cardColor = cardColor,
                        cardBorderColor = cardBorderColor,
                        textPrimaryColor = textPrimaryColor,
                        warningColor = warningColor
                    )
                }
            }

            // Validation issues section (collapsible)
            if (preview.hasFatalIssues) {
                item {
                    IssuesSection(
                        issues = preview.validationIssues,
                        expanded = showIssues,
                        onToggle = { showIssues = !showIssues },
                        cardColor = cardColor,
                        cardBorderColor = cardBorderColor,
                        textPrimaryColor = textPrimaryColor,
                        errorColor = errorColor
                    )
                }
            }

            // Tree preview header
            item {
                Text(
                    text = stringResource(id = R.string.import_tree_preview),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textPrimaryColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Tree items
            val flatItems = flattenTreeForPreview(preview.tree).drop(1) // drop root itself
            items(flatItems, key = { it.second.id }) { (depth, node) ->
                TreeItemRow(
                    depth = depth,
                    node = node,
                    cardColor = cardColor,
                    cardBorderColor = cardBorderColor,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun ImportSummaryCard(
    preview: ImportPreviewState,
    cardColor: Color,
    cardBorderColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    errorColor: Color,
    warningColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountChip(
                    icon = Icons.Rounded.Bookmark,
                    count = preview.totalBookmarks,
                    label = stringResource(id = R.string.import_bookmarks_label),
                    color = MaterialTheme.colorScheme.primary
                )
                CountChip(
                    icon = Icons.Rounded.Folder,
                    count = preview.totalFolders,
                    label = stringResource(id = R.string.import_folders_label),
                    color = MaterialTheme.colorScheme.tertiary
                )
                CountChip(
                    icon = Icons.Rounded.ContentCopy,
                    count = preview.duplicateCount,
                    label = stringResource(id = R.string.import_duplicates_label),
                    color = if (preview.duplicateCount > 0) warningColor else textSecondaryColor
                )
            }

            if (preview.hasFatalIssues) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(errorColor.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.import_fatal_issues),
                        color = errorColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else if (preview.hasWarnings) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(warningColor.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.import_warnings_present),
                        color = warningColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CountChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun WarningsSection(
    warnings: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    cardBorderColor: Color,
    textPrimaryColor: Color,
    warningColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, warningColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.import_warnings_count, warnings.size),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = warningColor
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = warningColor
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    warnings.forEach { warning ->
                        Text(
                            text = "• $warning",
                            fontSize = 12.sp,
                            color = textPrimaryColor.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IssuesSection(
    issues: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    cardBorderColor: Color,
    textPrimaryColor: Color,
    errorColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, errorColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.import_issues_count, issues.size),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = errorColor
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = errorColor
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    issues.forEach { issue ->
                        Text(
                            text = "• $issue",
                            fontSize = 12.sp,
                            color = textPrimaryColor.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeItemRow(
    depth: Int,
    node: BookmarkNode,
    cardColor: Color,
    cardBorderColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    val indent = (depth * 16).dp
    val isFolder = node is BookmarkNode.Folder
    val title = node.title
    val url = if (node is BookmarkNode.Item) node.url else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isFolder) Icons.Rounded.Folder else Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = if (isFolder) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifEmpty { if (isFolder) "(Untitled Folder)" else "(Untitled)" },
                    fontWeight = if (isFolder) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (url != null) {
                    Text(
                        text = url,
                        fontSize = 11.sp,
                        color = textSecondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── String helpers ──────────────────────────────────────────────────────────

@Composable
private fun policyLabel(policy: DuplicatePolicy): String {
    return when (policy) {
        DuplicatePolicy.KEEP_BOTH -> stringResource(id = R.string.policy_keep_both)
        DuplicatePolicy.SKIP -> stringResource(id = R.string.policy_skip)
        DuplicatePolicy.REPLACE -> stringResource(id = R.string.policy_replace)
        DuplicatePolicy.MERGE -> stringResource(id = R.string.policy_merge)
    }
}

@Composable
private fun policyDescription(policy: DuplicatePolicy): String {
    return when (policy) {
        DuplicatePolicy.KEEP_BOTH -> stringResource(id = R.string.policy_keep_both_desc)
        DuplicatePolicy.SKIP -> stringResource(id = R.string.policy_skip_desc)
        DuplicatePolicy.REPLACE -> stringResource(id = R.string.policy_replace_desc)
        DuplicatePolicy.MERGE -> stringResource(id = R.string.policy_merge_desc)
    }
}
