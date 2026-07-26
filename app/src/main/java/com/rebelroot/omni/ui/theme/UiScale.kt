package com.rebelroot.omni.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class UiSizeConfig(
    val addressBarHeight: Dp,
    val searchBoxHeight: Dp,
    val barIconSize: Dp,
    val innerIconSize: Dp,
    val fontSize: TextUnit,
    val bottomNavBarHeight: Dp,
    val paddingVertical: Dp,
    val paddingHorizontal: Dp
)

fun getUiSizeConfig(scale: Float, screenWidthDp: Int = 360): UiSizeConfig {
    // Smooth responsive factor: scales gently according to device width so small screens don't get bloated elements
    val widthFactor = (screenWidthDp / 390f).coerceIn(0.82f, 1.05f)
    val effectiveScale = (scale * widthFactor).coerceIn(0.75f, 1.15f)

    return UiSizeConfig(
        addressBarHeight = (46 * effectiveScale).dp,
        searchBoxHeight = (38 * effectiveScale).dp,
        barIconSize = (34 * effectiveScale).dp,
        innerIconSize = (18.5f * effectiveScale).dp,
        fontSize = (14 * effectiveScale).sp,
        bottomNavBarHeight = (48 * effectiveScale).dp,
        paddingVertical = (4 * effectiveScale).dp,
        paddingHorizontal = (8 * effectiveScale).dp
    )
}
