package com.rebelroot.omni.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Premium, curated color tokens
val LightBg = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVar = Color(0xFFF2F3F5)
val LightPrimary = Color(0xFF007AFF)      // Sleek iOS blue
val LightSecondary = Color(0xFF5856D6)    // Premium indigo
val LightTertiary = Color(0xFF34C759)     // Rich emerald green
val LightTextPrimary = Color(0xFF202124)  // Modern off-black
val LightTextSec = Color(0xFF606266)
val LightBorder = Color(0x1F000000)       // 12% thin divider

val DarkBg = Color(0xFF0D0E11)            // Deep void black
val DarkSurface = Color(0xFF16181D)       // Dark anthracite gray
val DarkSurfaceVar = Color(0xFF1E2028)
val DarkPrimary = Color(0xFF0A84FF)       // Vibrant electric blue
val DarkSecondary = Color(0xFF5E5CE6)     // Neon indigo
val DarkTertiary = Color(0xFF30D158)      // Vibrant lime-emerald
val DarkTextPrimary = Color(0xFFF3F4F6)   // Off-white
val DarkTextSec = Color(0xFF9CA3AF)
val DarkBorder = Color(0x14FFFFFF)        // 8% thin transparent white divider

val OmniDarkScheme = darkColorScheme(
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVar,
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSec,
    outline = DarkBorder,
    error = Color(0xFFFF453A)
)

val OmniLightScheme = lightColorScheme(
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVar,
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSec,
    outline = LightBorder,
    error = Color(0xFFFF3B30)
)
