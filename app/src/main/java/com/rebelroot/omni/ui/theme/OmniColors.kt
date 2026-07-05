/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Base neutral tokens (shared across all accent themes) ──

// Light mode neutrals
val LightBg = Color(0xFFFFFFFF)           // Pure White background
val LightSurface = Color(0xFFFFFFFF)      // Pure White surface
val LightSurfaceVar = Color(0xFFF1F3F4)   // Chrome-like light grey
val LightTextPrimary = Color(0xFF1C1C1E)  // Premium off-black
val LightTextSec = Color(0xFF8E8E93)      // Modern secondary grey
val LightBorder = Color(0xFFE5E5EA)       // Thin iOS/Chrome style divider

// Dark mode neutrals
val DarkBg = Color(0xFF0B0B0C)            // Professional OLED Black
val DarkSurface = Color(0xFF1C1C1E)       // Charcoal dark grey
val DarkSurfaceVar = Color(0xFF2C2C2E)    // Dark border/card grey
val DarkTextPrimary = Color(0xFFFFFFFF)   // Pure white text
val DarkTextSec = Color(0xFF8E8E93)       // Neutral grey
val DarkBorder = Color(0xFF2C2C2E)        // Thin neutral dark divider

// ── Accent Theme Definitions ──

data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color = Color(0xFFFF453A)
)

val OceanBlue = AccentPalette(
    primary = Color(0xFF0A84FF),
    secondary = Color(0xFF5E5CE6),
    tertiary = Color(0xFF30D158)
)

val CrimsonRed = AccentPalette(
    primary = Color(0xFFFF3B5C),
    secondary = Color(0xFFFF6B6B),
    tertiary = Color(0xFFFFB347)
)

val EmeraldGreen = AccentPalette(
    primary = Color(0xFF00C853),
    secondary = Color(0xFF69F0AE),
    tertiary = Color(0xFF40C4FF)
)

val SunsetOrange = AccentPalette(
    primary = Color(0xFFFF6D00),
    secondary = Color(0xFFFFAB40),
    tertiary = Color(0xFFFFD740)
)

val RoyalPurple = AccentPalette(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFFB388FF),
    tertiary = Color(0xFFEA80FC)
)

val Monochrome = AccentPalette(
    primary = Color(0xFFAAAAAA),
    secondary = Color(0xFF888888),
    tertiary = Color(0xFFCCCCCC)
)

val AccentThemes = mapOf(
    "Ocean Blue" to OceanBlue,
    "Crimson Red" to CrimsonRed,
    "Emerald Green" to EmeraldGreen,
    "Sunset Orange" to SunsetOrange,
    "Royal Purple" to RoyalPurple,
    "Monochrome" to Monochrome
)

// Light mode palettes — per accent, adapted for light backgrounds
private val LightAccentOverrides = mapOf(
    "Ocean Blue" to AccentPalette(Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFF34C759)),
    "Crimson Red" to AccentPalette(Color(0xFFE8334A), Color(0xFFD45454), Color(0xFFE09430)),
    "Emerald Green" to AccentPalette(Color(0xFF00A844), Color(0xFF4ECC8A), Color(0xFF2EA8D6)),
    "Sunset Orange" to AccentPalette(Color(0xFFE06000), Color(0xFFDD9530), Color(0xFFDDC030)),
    "Royal Purple" to AccentPalette(Color(0xFF6A3DE8), Color(0xFF9A6FE8), Color(0xFFD06FE8)),
    "Monochrome" to AccentPalette(Color(0xFF777777), Color(0xFF666666), Color(0xFF999999))
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
    error = accent.error
)

fun buildLightScheme(accent: AccentPalette): ColorScheme = lightColorScheme(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    primary = accent.primary,
    secondary = accent.secondary,
    tertiary = accent.tertiary,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1)
)


// Legacy schemes for backward compat (default Ocean Blue)
val OmniDarkScheme = buildDarkScheme(OceanBlue)
val OmniLightScheme = buildLightScheme(OceanBlue)

/**
 * Returns the appropriate ColorScheme for the given accent theme name and dark/light mode.
 */
fun getColorScheme(accentTheme: String, isDark: Boolean): ColorScheme {
    val palette = if (isDark) {
        AccentThemes[accentTheme] ?: OceanBlue
    } else {
        LightAccentOverrides[accentTheme] ?: LightAccentOverrides["Ocean Blue"]!!
    }
    return if (isDark) buildDarkScheme(palette) else buildLightScheme(palette)
}
