/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.chrome.ChromeActionRegistry
import com.rebelroot.omni.browser.chrome.ChromeSurface

/**
 * Add / remove / reorder editor for any [ChromeSurface] layout.
 *
 * Stateless: it renders [layout] and reports edits through [onLayoutChange]; the caller
 * owns persistence. Kept generic so the two navigation bars, quick tools and any future
 * surface share one editor.
 */
@Composable
fun ChromeLayoutEditor(
    title: String,
    surface: ChromeSurface,
    layout: List<String>,
    onLayoutChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    minItems: Int = 1,
) {
    val context = LocalContext.current

    @Composable
    fun labelOf(id: String): String {
        val action = ChromeActionRegistry.find(id) ?: return id
        return action.labelRes?.let { stringResource(it) } ?: action.labelLiteral ?: id
    }

    val available = ChromeActionRegistry.idsFor(surface).filter { it !in layout }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )

        layout.forEachIndexed { index, id ->
            val action = ChromeActionRegistry.find(id) ?: return@forEachIndexed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = labelOf(id),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val next = layout.toMutableList()
                        if (index > 0) {
                            val tmp = next[index - 1]; next[index - 1] = next[index]; next[index] = tmp
                            onLayoutChange(next)
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.nav_move_up), modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        val next = layout.toMutableList()
                        if (index < next.size - 1) {
                            val tmp = next[index + 1]; next[index + 1] = next[index]; next[index] = tmp
                            onLayoutChange(next)
                        }
                    },
                    enabled = index < layout.size - 1,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.nav_move_down), modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        if (layout.size <= minItems) {
                            Toast.makeText(context, context.getString(R.string.nav_min_one), Toast.LENGTH_SHORT).show()
                        } else {
                            onLayoutChange(layout.toMutableList().also { it.removeAt(index) })
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.nav_remove_button, labelOf(id)),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.nav_all_added),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.nav_add_button),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            available.chunked(2).forEach { rowIds ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowIds.forEach { id ->
                        val action = ChromeActionRegistry.find(id) ?: return@forEach
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onLayoutChange(layout + id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = labelOf(id),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(2 - rowIds.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}