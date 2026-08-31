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

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.rebelroot.omni.ai.translation.TranslationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Unified Manga & Comic Image Translation Pipeline.
 *
 * Coordinates on-device text detection & speech bubble clustering, translation
 * via Omni's [TranslationCoordinator], manual user editing, and comic typography inpainting.
 * Includes a fast two-tier memory cache for silky-smooth continuous reading.
 */
class MangaTranslationPipeline(
    private val coordinator: TranslationCoordinator,
    private val detector: MangaTextDetector = MangaTextDetector(),
    private val inpainter: MangaImageInpainter = MangaImageInpainter()
) {

    // 40 MB LRU cache for translated bitmaps
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(16 * 1024, 64 * 1024)

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val blocksCache = LruCache<String, List<MangaDialogueBlock>>(100)

    /**
     * Translates a manga page [bitmap].
     *
     * @param cacheKey Unique identifier (e.g. image URL or file path) for fast LRU lookup.
     * @param bitmap The source image bitmap.
     * @param sourceLanguage Source language script (e.g. "ja", "ko", "zh", "auto").
     * @param targetLanguage Target translation language (e.g. "en", "es", "fr", "hi").
     * @param isRtl Whether to apply Manga Right-to-Left panel reading order.
     * @param style User-customized typography and color style.
     */
    suspend fun translateImage(
        cacheKey: String,
        bitmap: Bitmap,
        sourceLanguage: String? = "auto",
        targetLanguage: String = "en",
        isRtl: Boolean = true,
        style: MangaTypographyStyle = MangaTypographyStyle()
    ): MangaTranslationResult = withContext(Dispatchers.Default) {
        val normalizedSrc = sourceLanguage?.lowercase() ?: "auto"
        val normalizedTgt = targetLanguage.lowercase()
        val textCacheKey = "${cacheKey}_${normalizedSrc}_${normalizedTgt}"
        val renderCacheKey = "${textCacheKey}_${style.cacheKey}"

        // 1. Check if exact rendered bitmap is already cached
        val cachedBitmap = bitmapCache.get(renderCacheKey)
        val cachedBlocks = blocksCache.get(textCacheKey)
        if (cachedBitmap != null && cachedBlocks != null) {
            return@withContext MangaTranslationResult(
                originalBitmap = bitmap,
                translatedBitmap = cachedBitmap,
                blocks = cachedBlocks,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }

        try {
            // 2. Re-use cached translated blocks if available (preserves translated text during style/font changes)
            val translatedBlocks = if (cachedBlocks != null && cachedBlocks.isNotEmpty()) {
                cachedBlocks
            } else {
                // OCR Text Localization & Speech Bubble Detection
                val detectedBlocks = detector.detectDialogueBlocks(bitmap, sourceLanguage, isRtl)

                if (detectedBlocks.isEmpty()) {
                    bitmapCache.put(renderCacheKey, bitmap)
                    blocksCache.put(textCacheKey, emptyList())
                    return@withContext MangaTranslationResult(
                        originalBitmap = bitmap,
                        translatedBitmap = bitmap,
                        blocks = emptyList(),
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage
                    )
                }

                // Batch Translation via TranslationCoordinator
                val translated = detectedBlocks.map { block ->
                    async {
                        val raw = block.rawText.trim()
                        if (raw.isEmpty()) {
                            block
                        } else {
                            val res = runCatching {
                                coordinator.translate(raw, sourceLanguage, targetLanguage).translatedText
                            }.getOrDefault(raw)

                            block.copy(translatedText = res)
                        }
                    }
                }.awaitAll()

                blocksCache.put(textCacheKey, translated)
                translated
            }

            // 3. Render Typography with the requested style
            val translatedBitmap = inpainter.inpaintAndRender(bitmap, translatedBlocks, style)
            bitmapCache.put(renderCacheKey, translatedBitmap)

            MangaTranslationResult(
                originalBitmap = bitmap,
                translatedBitmap = translatedBitmap,
                blocks = translatedBlocks,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Manga image translation failed for $cacheKey", e)
            MangaTranslationResult(
                originalBitmap = bitmap,
                translatedBitmap = bitmap,
                blocks = emptyList(),
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }
    }

    /**
     * Retrieves cached dialogue blocks for a page, if available.
     */
    fun getCachedBlocks(
        cacheKey: String,
        sourceLanguage: String? = "auto",
        targetLanguage: String = "en"
    ): List<MangaDialogueBlock>? {
        val normalizedSrc = sourceLanguage?.lowercase() ?: "auto"
        val normalizedTgt = targetLanguage.lowercase()
        val textCacheKey = "${cacheKey}_${normalizedSrc}_${normalizedTgt}"
        return blocksCache.get(textCacheKey)
    }

    /**
     * Translates a single text string on demand.
     */
    suspend fun retranslateText(
        text: String,
        sourceLanguage: String? = "auto",
        targetLanguage: String = "en"
    ): String = withContext(Dispatchers.Default) {
        val raw = text.trim()
        if (raw.isEmpty()) return@withContext ""
        runCatching {
            coordinator.translate(raw, sourceLanguage, targetLanguage).translatedText
        }.getOrDefault(raw)
    }

    /**
     * Applies manual user edits to dialogue blocks, re-inpaints the bitmap,
     * and updates the cache.
     */
    suspend fun applyCustomBlocks(
        cacheKey: String,
        bitmap: Bitmap,
        sourceLanguage: String? = "auto",
        targetLanguage: String = "en",
        customBlocks: List<MangaDialogueBlock>,
        style: MangaTypographyStyle = MangaTypographyStyle()
    ): MangaTranslationResult = withContext(Dispatchers.Default) {
        val normalizedSrc = sourceLanguage?.lowercase() ?: "auto"
        val normalizedTgt = targetLanguage.lowercase()
        val textCacheKey = "${cacheKey}_${normalizedSrc}_${normalizedTgt}"
        val renderCacheKey = "${textCacheKey}_${style.cacheKey}"

        blocksCache.put(textCacheKey, customBlocks)
        val translatedBitmap = inpainter.inpaintAndRender(bitmap, customBlocks, style)
        bitmapCache.put(renderCacheKey, translatedBitmap)

        MangaTranslationResult(
            originalBitmap = bitmap,
            translatedBitmap = translatedBitmap,
            blocks = customBlocks,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )
    }

    fun clearCache() {
        bitmapCache.evictAll()
        blocksCache.evictAll()
    }

    companion object {
        private const val TAG = "MangaTranslationPipeline"
    }
}
