/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.chrome.ChromeActionRegistry
import com.rebelroot.omni.browser.chrome.ChromeSurface

/**
 * Drag-and-drop layout editor for any [ChromeSurface].
 *
 * Tiles are dragged with a long press and float (scale + shadow + alpha) as neighbours
 * swap in real time — the same interaction the Quick Tools sheet uses. Stateless: it
 * renders [layout] and reports every change through [onLayoutChange].
 */
@Composable
fun ChromeDragLayoutEditor(
    surface: ChromeSurface,
    layout: List<String>,
    onLayoutChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    columns: Int = 4,
    minItems: Int = 0,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // One stable list instance for the whole editor session. Recreating it whenever the
    // layout changes would restart the pointerInput below and cancel an in-flight drag
    // after the first swap — which is exactly "starts to drag, then won't reorder".
    val ordered = remember { mutableStateListOf<String>() }
    androidx.compose.runtime.LaunchedEffect(layout) {
        if (ordered.toList() != layout) {
            ordered.clear()
            ordered.addAll(layout)
        }
    }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val itemCenters = remember { mutableStateMapOf<String, Offset>() }
    var gridTopLeft by remember { mutableStateOf(Offset.Zero) }
    var lastSwapMs by remember { mutableStateOf(0L) }

    fun labelOf(id: String): String {
        val action = ChromeActionRegistry.find(id) ?: return id
        return action.labelRes?.let { context.getString(it) } ?: action.labelLiteral ?: id
    }

    fun persist() = onLayoutChange(ordered.toList())

    val available = ChromeActionRegistry.idsFor(surface).filter { it !in ordered }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        if (title != null) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { gridTopLeft = it.boundsInWindow().topLeft }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val abs = gridTopLeft + offset
                            val nearest = itemCenters.entries
                                .filter { it.key in ordered }
                                .minByOrNull { e ->
                                    val dx = e.value.x - abs.x; val dy = e.value.y - abs.y
                                    dx * dx + dy * dy
                                }
                            if (nearest != null) {
                                val dx = nearest.value.x - abs.x; val dy = nearest.value.y - abs.y
                                if (dx * dx + dy * dy < 2500f) {
                                    draggedId = nearest.key
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                            val center = itemCenters[id] ?: return@detectDragGesturesAfterLongPress
                            val newCenter = center + dragAmount
                            itemCenters[id] = newCenter
                            val nearestOther = itemCenters.entries
                                .filter { it.key != id && it.key in ordered }
                                .minByOrNull { e ->
                                    val dx = e.value.x - newCenter.x; val dy = e.value.y - newCenter.y
                                    dx * dx + dy * dy
                                } ?: return@detectDragGesturesAfterLongPress
                            val dx = nearestOther.value.x - newCenter.x; val dy = nearestOther.value.y - newCenter.y
                            val now = System.currentTimeMillis()
                            if (dx * dx + dy * dy < 2500f && now - lastSwapMs > 120L) {
                                val from = ordered.indexOf(id)
                                val to = ordered.indexOf(nearestOther.key)
                                if (from != -1 && to != -1 && from != to) {
                                    ordered.removeAt(from)
                                    ordered.add(to, id)
                                    lastSwapMs = now
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = { draggedId = null; persist() },
                        onDragCancel = { draggedId = null },
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ordered.chunked(columns).forEach { rowItems ->
                    val padded = rowItems + List(columns - rowItems.size) { "" }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        padded.forEach { id ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        if (id.isNotEmpty()) itemCenters[id] = coords.boundsInWindow().center
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (id.isNotEmpty()) {
                                    EditorTile(
                                        id = id,
                                        dragged = draggedId == id,
                                        onClick = {
                                            if (ordered.size <= minItems) {
                                                Toast.makeText(context, context.getString(R.string.nav_min_one), Toast.LENGTH_SHORT).show()
                                            } else {
                                                ordered.remove(id)
                                                persist()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
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
            available.chunked(columns).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { id ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { ordered.add(id); persist() }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = labelOf(id),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EditorTile(
    id: String,
    dragged: Boolean,
    onClick: () -> Unit,
) {
    val action = ChromeActionRegistry.find(id)
    val label = action?.labelRes?.let { stringResource(it) } ?: action?.labelLiteral ?: id
    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 76.dp)
            .graphicsLayer {
                if (dragged) {
                    scaleX = 1.12f; scaleY = 1.12f
                    shadowElevation = 18f
                    alpha = 0.85f
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (action != null) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}