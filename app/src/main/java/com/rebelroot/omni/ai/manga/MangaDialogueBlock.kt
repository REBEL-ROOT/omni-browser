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

package com.rebelroot.omni.ai.manga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Pure Kotlin immutable rectangle for dialogue bounding geometry,
 * fully testable on JVM and converted to [RectF] on Android Canvas.
 */
data class MangaRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun toAndroidRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * An individual recognized line of text inside a manga panel.
 */
data class MangaDialogueLine(
    val text: String,
    val boundingBox: MangaRect
)

/**
 * A clustered speech bubble dialogue block containing one or more lines
 * forming a single grammatically coherent sentence or speech unit.
 */
data class MangaDialogueBlock(
    val id: String,
    val rawText: String,
    val translatedText: String = "",
    val boundingBox: MangaRect,
    val lines: List<MangaDialogueLine> = emptyList(),
    val isVertical: Boolean = false,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val textColor: Int = 0xFF000000.toInt()
)

/**
 * User-customizable typography and layout settings for Manga translation.
 */
data class MangaTypographyStyle(
    val fontSizeScale: Float = 1.0f, // 0.75f (Small) to 1.4f (Large)
    val fontFamily: String = "Comic", // "Comic", "Sans", "Serif", "Clean"
    val textColorMode: String = "Auto", // "Auto", "Black", "White"
    val bgFillMode: String = "Auto" // "Auto", "White", "Transparent"
) {
    val cacheKey: String get() = "${fontSizeScale}_${fontFamily}_${textColorMode}_${bgFillMode}"
}

/**
 * Persistent preferences for Manga translation settings.
 * Allows applying typography and language options globally across all translations.
 */
object MangaPreferences {
    private const val PREFS_NAME = "omni_manga_preferences"
    private const val KEY_APPLY_TO_ALL = "apply_to_all_manga"
    private const val KEY_FONT_SIZE_SCALE = "font_size_scale"
    private const val KEY_FONT_FAMILY = "font_family"
    private const val KEY_TEXT_COLOR_MODE = "text_color_mode"
    private const val KEY_BG_FILL_MODE = "bg_fill_mode"
    private const val KEY_SOURCE_LANG_NAME = "source_lang_name"
    private const val KEY_SOURCE_LANG_CODE = "source_lang_code"
    private const val KEY_TARGET_LANG_NAME = "target_lang_name"
    private const val KEY_TARGET_LANG_CODE = "target_lang_code"

    fun loadApplyToAll(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APPLY_TO_ALL, true)
    }

    fun saveApplyToAll(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_APPLY_TO_ALL, enabled).apply()
    }

    fun loadTypographyStyle(context: Context): MangaTypographyStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return MangaTypographyStyle(
            fontSizeScale = prefs.getFloat(KEY_FONT_SIZE_SCALE, 1.0f),
            fontFamily = prefs.getString(KEY_FONT_FAMILY, "Comic") ?: "Comic",
            textColorMode = prefs.getString(KEY_TEXT_COLOR_MODE, "Auto") ?: "Auto",
            bgFillMode = prefs.getString(KEY_BG_FILL_MODE, "Auto") ?: "Auto"
        )
    }

    fun saveTypographyStyle(context: Context, style: MangaTypographyStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SIZE_SCALE, style.fontSizeScale)
            .putString(KEY_FONT_FAMILY, style.fontFamily)
            .putString(KEY_TEXT_COLOR_MODE, style.textColorMode)
            .putString(KEY_BG_FILL_MODE, style.bgFillMode)
            .apply()
    }

    fun loadLanguages(context: Context): Pair<Pair<String, String>, Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val srcName = prefs.getString(KEY_SOURCE_LANG_NAME, "Japanese (日本語)") ?: "Japanese (日本語)"
        val srcCode = prefs.getString(KEY_SOURCE_LANG_CODE, "ja") ?: "ja"
        val tgtName = prefs.getString(KEY_TARGET_LANG_NAME, "English") ?: "English"
        val tgtCode = prefs.getString(KEY_TARGET_LANG_CODE, "en") ?: "en"
        return (srcName to srcCode) to (tgtName to tgtCode)
    }

    fun saveLanguages(context: Context, source: Pair<String, String>, target: Pair<String, String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE_LANG_NAME, source.first)
            .putString(KEY_SOURCE_LANG_CODE, source.second)
            .putString(KEY_TARGET_LANG_NAME, target.first)
            .putString(KEY_TARGET_LANG_CODE, target.second)
            .apply()
    }
}

/**
 * Complete result of translating a manga or comic image.
 */
data class MangaTranslationResult(
    val originalBitmap: Bitmap,
    val translatedBitmap: Bitmap,
    val blocks: List<MangaDialogueBlock>,
    val sourceLanguage: String?,
    val targetLanguage: String
)
