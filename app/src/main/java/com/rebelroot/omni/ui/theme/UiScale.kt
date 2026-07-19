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

fun getUiSizeConfig(scale: Float): UiSizeConfig {
    return UiSizeConfig(
        addressBarHeight = (48 * scale).dp,
        searchBoxHeight = (40 * scale).dp,
        barIconSize = (36 * scale).dp,
        innerIconSize = (20 * scale).dp,
        fontSize = (15 * scale).sp,
        bottomNavBarHeight = (50 * scale).dp,
        paddingVertical = (4 * scale).dp,
        paddingHorizontal = (8 * scale).dp
    )
}
