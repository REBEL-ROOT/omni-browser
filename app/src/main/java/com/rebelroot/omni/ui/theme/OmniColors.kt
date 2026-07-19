/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Google-Quality Design Tokens ──

// Light mode neutrals
val LightBg = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVar = Color(0xFFF1F3F4)
val LightTextPrimary = Color(0xFF202124)
val LightTextSec = Color(0xFF5F6368)
val LightBorder = Color(0xFFDADCE0)
val LightError = Color(0xFFB3261E)

// Dark mode neutrals
val DarkBg = Color(0xFF131314)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVar = Color(0xFF28292A)
val DarkTextPrimary = Color(0xFFE3E3E3)
val DarkTextSec = Color(0xFF9AA0A6)
val DarkBorder = Color(0xFF444746)
val DarkError = Color(0xFFF2B8B5)

// AMOLED mode neutrals — true black for OLED power savings
val AmoledBg = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledSurfaceVar = Color(0xFF141414)
val AmoledBorder = Color(0xFF2A2A2A)

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

/**
 * Builds an AMOLED (true-black) dark scheme — same accent as chosen dark theme
 * but with pure-black background and near-black surfaces for OLED power savings.
 */
fun buildAmoledScheme(accent: AccentPalette): ColorScheme = darkColorScheme(
    background = AmoledBg,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVar,
    primary = accent.primary,
    secondary = accent.secondary,
    tertiary = accent.tertiary,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSec,
    outline = AmoledBorder,
    outlineVariant = AmoledBorder.copy(alpha = 0.5f),
    error = accent.error
)

// Legacy schemes for backward compat (default Ocean Blue)
val OmniDarkScheme = buildDarkScheme(OceanBlueDark)
val OmniLightScheme = buildLightScheme(OceanBlueLight)

/**
 * Returns the appropriate ColorScheme for the given theme mode, accent name, and context.
 *
 * @param accentTheme Accent name from [AccentThemesDark]/[AccentThemesLight]
 * @param isDark true for dark/AMOLED, false for light
 * @param isAmoled true to use pure-black AMOLED surfaces (only applies when isDark=true)
 * @param isDynamic true to extract palette from wallpaper (Android 12+ only)
 * @param context Android Context needed for dynamic color extraction
 */
fun getColorScheme(
    accentTheme: String,
    isDark: Boolean,
    isAmoled: Boolean = false,
    isDynamic: Boolean = false,
    context: android.content.Context? = null
): ColorScheme {
    // Material You dynamic color (Android 12+)
    if (isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
        val dynamicScheme = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        // Apply AMOLED override on top of dynamic scheme if requested
        return if (isDark && isAmoled) {
            dynamicScheme.copy(
                background = AmoledBg,
                surface = AmoledSurface,
                surfaceVariant = AmoledSurfaceVar,
                outline = AmoledBorder,
                outlineVariant = AmoledBorder.copy(alpha = 0.5f)
            )
        } else {
            dynamicScheme
        }
    }

    // Static accent palette
    val paletteMap = if (isDark) AccentThemesDark else AccentThemesLight
    val palette = paletteMap[accentTheme] ?: paletteMap["Ocean Blue"]!!
    return when {
        isDark && isAmoled -> buildAmoledScheme(palette)
        isDark             -> buildDarkScheme(palette)
        else               -> buildLightScheme(palette)
    }
}

// ── Single-source surface color helper ──

/**
 * Returns the correct sheet/dialog background color for the current theme.
 * Use this in ALL ModalBottomSheets, Dialogs, Cards, and elevated Surfaces
 * instead of hardcoding hex values.
 *
 * - AMOLED dark → Color.Black
 * - Regular dark → MaterialTheme surface (#1E1E1E)
 * - Light → MaterialTheme surface (White)
 */
@Composable
fun omniSheetContainerColor(): Color = MaterialTheme.colorScheme.surface

/**
 * Returns the drag-handle color for bottom sheets.
 * Always use this instead of hardcoded hex values.
 */
@Composable
fun omniDragHandleColor(): Color = MaterialTheme.colorScheme.outlineVariant
