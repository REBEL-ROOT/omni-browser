package com.omni.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun OmniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OmniDarkScheme else OmniLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmniTypography,
        shapes = OmniShapes,
        content = content
    )
}
