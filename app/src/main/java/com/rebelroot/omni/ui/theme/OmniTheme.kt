package com.rebelroot.omni.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun OmniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OmniDarkScheme else OmniLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use solid obsidian black 0xFF070A0F for status bar in dark mode, and background color in light mode
            val statusBarColor = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF070A0F) else colorScheme.background
            window.statusBarColor = statusBarColor.toArgb()
            
            // Set navigation bar color to match the bottom bar (Color(0xFF0D1620)) in dark mode
            val navigationBarColor = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0D1620) else colorScheme.background
            window.navigationBarColor = navigationBarColor.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmniTypography,
        shapes = OmniShapes,
        content = content
    )
}
