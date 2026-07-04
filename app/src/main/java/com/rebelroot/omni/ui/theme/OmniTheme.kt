package com.rebelroot.omni.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
@Composable
fun OmniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: String = "Ocean Blue",
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(accentTheme, darkTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Enforce transparent status and navigation bars for Android 15+ edge-to-edge compliance
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

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
