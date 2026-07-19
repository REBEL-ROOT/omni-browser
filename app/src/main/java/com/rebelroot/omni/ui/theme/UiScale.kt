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

fun getUiSizeConfig(size: String): UiSizeConfig {
    return when (size) {
        "Small" -> UiSizeConfig(
            addressBarHeight = 42.dp,
            searchBoxHeight = 34.dp,
            barIconSize = 32.dp,
            innerIconSize = 16.dp,
            fontSize = 13.sp,
            bottomNavBarHeight = 44.dp,
            paddingVertical = 2.dp,
            paddingHorizontal = 6.dp
        )
        "Large" -> UiSizeConfig(
            addressBarHeight = 56.dp,
            searchBoxHeight = 46.dp,
            barIconSize = 42.dp,
            innerIconSize = 22.dp,
            fontSize = 17.sp,
            bottomNavBarHeight = 58.dp,
            paddingVertical = 6.dp,
            paddingHorizontal = 10.dp
        )
        else -> UiSizeConfig( // Medium
            addressBarHeight = 48.dp,
            searchBoxHeight = 40.dp,
            barIconSize = 36.dp,
            innerIconSize = 20.dp,
            fontSize = 15.sp,
            bottomNavBarHeight = 50.dp,
            paddingVertical = 4.dp,
            paddingHorizontal = 8.dp
        )
    }
}
