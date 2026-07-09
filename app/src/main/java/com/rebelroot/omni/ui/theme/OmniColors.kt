/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Google-Quality Design Tokens ──

// Light mode neutrals (Calm, readable, low-strain)
val LightBg = Color(0xFFF8F9FA)           // Google standard subtle background
val LightSurface = Color(0xFFFFFFFF)      // Pure White surface for cards (elevation separation)
val LightSurfaceVar = Color(0xFFF1F3F4)   // Google standard search bar / button surface
val LightTextPrimary = Color(0xFF202124)  // Slate dark gray (not absolute black)
val LightTextSec = Color(0xFF5F6368)      // Muted gray for subtitles/icons
val LightBorder = Color(0xFFDADCE0)       // Extremely subtle divider
val LightError = Color(0xFFB3261E)

// Dark mode neutrals (Deep, professional, immersive)
val DarkBg = Color(0xFF131314)            // Google standard dark background
val DarkSurface = Color(0xFF1E1E1E)       // Slightly elevated dark surface
val DarkSurfaceVar = Color(0xFF28292A)    // Elevated variant (search bars, menus)
val DarkTextPrimary = Color(0xFFE3E3E3)   // Soft white
val DarkTextSec = Color(0xFF9AA0A6)       // Readable muted gray
val DarkBorder = Color(0xFF444746)        // Subtle dark divider
val DarkError = Color(0xFFF2B8B5)

// ── Accent Theme Definitions ──

data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color
)

// Premium tailored accents based on Google Material standards
val OceanBlueDark = AccentPalette(Color(0xFFA8C7FA), Color(0xFF7CAAFC), Color(0xFF6991D6), DarkError)
val OceanBlueLight = AccentPalette(Color(0xFF0B57D0), Color(0xFF0842A0), Color(0xFF001D35), LightError)

val CrimsonRedDark = AccentPalette(Color(0xFFF2B8B5), Color(0xFFE46962), Color(0xFF8C1D18), DarkError)
val CrimsonRedLight = AccentPalette(Color(0xFFB3261E), Color(0xFF8C1D18), Color(0xFF410E0B), LightError)

val EmeraldGreenDark = AccentPalette(Color(0xFF81C995), Color(0xFF5BB974), Color(0xFF1E8E3E), DarkError)
val EmeraldGreenLight = AccentPalette(Color(0xFF1E8E3E), Color(0xFF137333), Color(0xFF0D5022), LightError)

val SunsetOrangeDark = AccentPalette(Color(0xFFFDD663), Color(0xFFF9AB00), Color(0xFFE37400), DarkError)
val SunsetOrangeLight = AccentPalette(Color(0xFFE37400), Color(0xFFB06000), Color(0xFF824B00), LightError)

val RoyalPurpleDark = AccentPalette(Color(0xFFD0BCFF), Color(0xFFB69DF8), Color(0xFF4F378B), DarkError)
val RoyalPurpleLight = AccentPalette(Color(0xFF6750A4), Color(0xFF4F378B), Color(0xFF21005D), LightError)

val MonochromeDark = AccentPalette(Color(0xFFC7C7C7), Color(0xFFAAAAAA), Color(0xFF303030), DarkError)
val MonochromeLight = AccentPalette(Color(0xFF5E5E5E), Color(0xFF474747), Color(0xFF1F1F1F), LightError)

val AccentThemesDark = mapOf(
    "Ocean Blue" to OceanBlueDark,
    "Crimson Red" to CrimsonRedDark,
    "Emerald Green" to EmeraldGreenDark,
    "Sunset Orange" to SunsetOrangeDark,
    "Royal Purple" to RoyalPurpleDark,
    "Monochrome" to MonochromeDark
)

val AccentThemesLight = mapOf(
    "Ocean Blue" to OceanBlueLight,
    "Crimson Red" to CrimsonRedLight,
    "Emerald Green" to EmeraldGreenLight,
    "Sunset Orange" to SunsetOrangeLight,
    "Royal Purple" to RoyalPurpleLight,
    "Monochrome" to MonochromeLight
)

fun buildDarkScheme(accent: AccentPalette): ColorScheme = darkColorScheme(
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVar,
    primary = accent.primary,
    secondary = accent.secondary,
    tertiary = accent.tertiary,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSec,
    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.5f),
    error = accent.error
)

fun buildLightScheme(accent: AccentPalette): ColorScheme = lightColorScheme(
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVar,
    primary = accent.primary,
    secondary = accent.secondary,
    tertiary = accent.tertiary,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSec,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.5f),
    error = accent.error
)

// Legacy schemes for backward compat (default Ocean Blue)
val OmniDarkScheme = buildDarkScheme(OceanBlueDark)
val OmniLightScheme = buildLightScheme(OceanBlueLight)

/**
 * Returns the appropriate ColorScheme for the given accent theme name and dark/light mode.
 */
fun getColorScheme(accentTheme: String, isDark: Boolean): ColorScheme {
    val paletteMap = if (isDark) AccentThemesDark else AccentThemesLight
    val palette = paletteMap[accentTheme] ?: paletteMap["Ocean Blue"]!!
    return if (isDark) buildDarkScheme(palette) else buildLightScheme(palette)
}
