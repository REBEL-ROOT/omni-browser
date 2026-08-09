/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualBlockSettingsScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = if (viewModel.isAmoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
    val cardColor = if (viewModel.isAmoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (viewModel.isAmoledMode) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (viewModel.isAmoledMode) Color(0xFF111111) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val visualBlockManager = viewModel.visualBlockManager
    val visualRules by visualBlockManager.rules.collectAsState()

    // Group rules site by site (domain by domain)
    val groupedRules = remember(visualRules) {
        visualRules.groupBy { rule ->
            val d = rule.domain.trim().lowercase().removePrefix("www.")
            if (d.isBlank() || d == "about:blank") "All Sites (*)" else d
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.visual_block_settings_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.visual_block_settings_desc),
                fontSize = 13.sp,
                color = textSecondaryColor
            )

            if (visualRules.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    border = BorderStroke(0.5.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.LayersClear, contentDescription = null, tint = textSecondaryColor.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                        Text(stringResource(id = R.string.visual_block_no_rules), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPrimaryColor)
                        Text(stringResource(id = R.string.visual_block_no_rules_desc), fontSize = 12.sp, color = textSecondaryColor)
                    }
                }
            } else {
                // Render site by site cards
                groupedRules.forEach { (domain, rulesForSite) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardColor)
                            .border(0.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                    ) {
                        // Site Domain Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBorderColor.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = domain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textPrimaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                    contentColor = accentColor
                                ) {
                                    Text(
                                        text = "${rulesForSite.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Delete all rules for this site button
                            IconButton(
                                onClick = {
                                    visualBlockManager.clearRulesForDomain(domain)
                                    Toast.makeText(context, "Rules cleared for $domain", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete site rules",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = dividerColor)

                        // Rules under this site
                        rulesForSite.forEachIndexed { index, rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val selectorDisplay = "##${rule.selector}"
                                    Text(
                                        text = selectorDisplay,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textPrimaryColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (rule.textPreview.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Preview: \"${rule.textPreview}\"",
                                            fontSize = 11.sp,
                                            color = textSecondaryColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            visualBlockManager.removeRule(rule.id)
                                            Toast.makeText(context, context.getString(R.string.visual_block_rule_removed), Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Remove Rule",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Switch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { enabled -> visualBlockManager.toggleRule(rule.id, enabled) }
                                    )
                                }
                            }
                            if (index < rulesForSite.size - 1) {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
