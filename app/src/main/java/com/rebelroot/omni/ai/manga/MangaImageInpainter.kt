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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * High-Quality Comic & Manga Image Inpainter and Typography Renderer.
 *
 * Inpaints speech bubbles cleanly, smartly detects floating text over artwork vs
 * solid speech balloons to prevent covering hair/scenes with white boxes,
 * and renders translated text with crisp contrast outlines.
 */
class MangaImageInpainter {

    /**
     * Inpaints speech bubbles and floating text, rendering translated dialogue in natural comic typography.
     *
     * @param originalBitmap The original source image.
     * @param blocks The detected dialogue blocks with translations.
     * @param style User-customized typography and color style.
     */
    suspend fun inpaintAndRender(
        originalBitmap: Bitmap,
        blocks: List<MangaDialogueBlock>,
        style: MangaTypographyStyle = MangaTypographyStyle()
    ): Bitmap = withContext(Dispatchers.Default) {
        val outputBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
        }

        val baseTypeface = when (style.fontFamily) {
            "Serif" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "Sans" -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            "Clean" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) // Comic bold
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            typeface = baseTypeface
        }

        val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            typeface = baseTypeface
            this.style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        for (block in blocks) {
            val textToRender = block.translatedText.ifEmpty { block.rawText }.trim()
            if (textToRender.isEmpty()) continue

            val box = block.boundingBox
            val width = box.width
            val height = box.height
            if (width < 4 || height < 4) continue

            // 1. Analyze background context: Solid Speech Bubble vs Floating Art Text
            val inpaintCtx = analyzeBubbleBackground(originalBitmap, box)
            val isWhiteFill = style.bgFillMode == "White" || (style.bgFillMode == "Auto" && inpaintCtx.isSolidWhiteBubble)
            val isBlackFill = style.bgFillMode == "Auto" && inpaintCtx.isSolidBlackBubble
            val isTransparent = style.bgFillMode == "Transparent"

            // 2. Erase original text
            if (isWhiteFill) {
                // Genuine white speech bubble: clean full bubble erasure
                bgPaint.color = Color.WHITE
                val cornerRadius = min(12f, min(width, height) * 0.2f)
                val paddedBox = RectF(
                    max(0f, box.left - 2f),
                    max(0f, box.top - 2f),
                    min(originalBitmap.width.toFloat(), box.right + 2f),
                    min(originalBitmap.height.toFloat(), box.bottom + 2f)
                )
                canvas.drawRoundRect(paddedBox, cornerRadius, cornerRadius, bgPaint)
            } else if (isBlackFill) {
                // Genuine black thought bubble: clean black erasure
                bgPaint.color = Color.BLACK
                val cornerRadius = min(12f, min(width, height) * 0.2f)
                val paddedBox = RectF(
                    max(0f, box.left - 2f),
                    max(0f, box.top - 2f),
                    min(originalBitmap.width.toFloat(), box.right + 2f),
                    min(originalBitmap.height.toFloat(), box.bottom + 2f)
                )
                canvas.drawRoundRect(paddedBox, cornerRadius, cornerRadius, bgPaint)
            } else if (!isTransparent) {
                // Floating text over artwork / hair / scene / photo:
                // Erase ONLY the line text strokes tightly, do NOT place a giant solid white box over character hair!
                bgPaint.color = inpaintCtx.sampledColor
                for (line in block.lines) {
                    val lineBox = line.boundingBox
                    val paddedLine = RectF(
                        max(0f, lineBox.left - 2f),
                        max(0f, lineBox.top - 2f),
                        min(originalBitmap.width.toFloat(), lineBox.right + 2f),
                        min(originalBitmap.height.toFloat(), lineBox.bottom + 2f)
                    )
                    canvas.drawRoundRect(paddedLine, 4f, 4f, bgPaint)
                }
            }

            // 3. Contrast & Stroke Typography setup
            val isLightBackground = isWhiteFill || (calculateLuminance(inpaintCtx.sampledColor) > 130)

            when (style.textColorMode) {
                "Black" -> {
                    textPaint.color = Color.BLACK
                    strokePaint.color = Color.WHITE
                }
                "White" -> {
                    textPaint.color = Color.WHITE
                    strokePaint.color = Color.BLACK
                }
                else -> {
                    if (isLightBackground && isWhiteFill) {
                        textPaint.color = Color.BLACK
                    } else if (isLightBackground) {
                        textPaint.color = Color.BLACK
                        strokePaint.color = Color.WHITE
                    } else {
                        textPaint.color = Color.WHITE
                        strokePaint.color = Color.BLACK
                    }
                }
            }

            // 4. Formats text within the natural bubble width with multi-line vertical wrapping
            val scaleFactor = style.fontSizeScale.coerceIn(0.6f, 3.0f)
            val maxBubbleWidth = (originalBitmap.width * 0.45f).toInt()
            val availableWidth = if (block.isVertical) {
                val estWidth = max(width * 1.25f * ((scaleFactor + 1f) / 2f), 60f)
                estWidth.toInt().coerceIn(55, maxBubbleWidth)
            } else {
                max(width * 1.05f * ((scaleFactor + 1f) / 2f), 60f).toInt().coerceIn(55, maxBubbleWidth)
            }
            val availableHeight = max(height * 1.15f * ((scaleFactor + 1f) / 2f), 40f)

            // Scaled maximum font size based on user preference (supports up to 300% zoom)
            val maxAllowedFontSize = (18f * style.fontSizeScale).coerceIn(9f, 64f)

            val layout = fitTextLayout(textToRender, textPaint, availableWidth, availableHeight, maxAllowedFontSize)

            // 5. Draw translated text centered at the exact original position
            val layoutWidth = layout.width.toFloat()
            val layoutHeight = layout.height.toFloat()

            val posX = (box.centerX - layoutWidth / 2f).coerceIn(4f, (originalBitmap.width - layoutWidth - 4f).coerceAtLeast(0f))
            val posY = (box.centerY - layoutHeight / 2f).coerceIn(4f, (originalBitmap.height - layoutHeight - 4f).coerceAtLeast(0f))

            canvas.save()
            canvas.translate(posX, posY)

            val needsStroke = (!isWhiteFill && !isBlackFill) || style.textColorMode == "White" || style.textColorMode == "Black"
            if (needsStroke) {
                strokePaint.textSize = textPaint.textSize
                val strokeLayout = createLayout(textToRender, strokePaint, availableWidth)
                strokeLayout.draw(canvas)
            }

            layout.draw(canvas)
            canvas.restore()
        }

        outputBitmap
    }

    /**
     * Binary search to find optimal font size that fits comfortably across multiple lines.
     */
    private fun fitTextLayout(
        text: String,
        paint: TextPaint,
        maxWidth: Int,
        maxHeight: Float,
        maxAllowedFontSize: Float
    ): StaticLayout {
        var low = 9f
        var high = maxAllowedFontSize
        var bestSize = low
        var bestLayout = createLayout(text, paint.apply { textSize = low }, maxWidth)

        while (high - low > 0.5f) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val layout = createLayout(text, paint, maxWidth)

            if (layout.height <= maxHeight) {
                bestSize = mid
                bestLayout = layout
                low = mid
            } else {
                high = mid
            }
        }

        paint.textSize = bestSize
        return bestLayout
    }

    private fun createLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        }

        return builder.build()
    }

    data class InpaintContext(
        val isSolidWhiteBubble: Boolean,
        val isSolidBlackBubble: Boolean,
        val sampledColor: Int
    )

    /**
     * Samples perimeter points of the dialogue box and determines whether it is an
     * enclosed white/black balloon or floating dialogue text over character art/hair/photos.
     */
    private fun analyzeBubbleBackground(bitmap: Bitmap, box: MangaRect): InpaintContext {
        val samplePoints = mutableListOf<Int>()
        val bmpWidth = bitmap.width
        val bmpHeight = bitmap.height

        val left = (box.left - 2).toInt().coerceIn(0, bmpWidth - 1)
        val right = (box.right + 2).toInt().coerceIn(0, bmpWidth - 1)
        val top = (box.top - 2).toInt().coerceIn(0, bmpHeight - 1)
        val bottom = (box.bottom + 2).toInt().coerceIn(0, bmpHeight - 1)

        val steps = 8
        for (i in 0..steps) {
            val x = (left + (right - left) * (i / steps.toFloat())).toInt().coerceIn(0, bmpWidth - 1)
            val y = (top + (bottom - top) * (i / steps.toFloat())).toInt().coerceIn(0, bmpHeight - 1)

            samplePoints.add(bitmap.getPixel(x, top))
            samplePoints.add(bitmap.getPixel(x, bottom))
            samplePoints.add(bitmap.getPixel(left, y))
            samplePoints.add(bitmap.getPixel(right, y))
        }

        if (samplePoints.isEmpty()) {
            return InpaintContext(isSolidWhiteBubble = true, isSolidBlackBubble = false, sampledColor = Color.WHITE)
        }

        var pureWhiteCount = 0
        var pureBlackCount = 0
        var sumR = 0L; var sumG = 0L; var sumB = 0L

        for (c in samplePoints) {
            val lum = calculateLuminance(c)
            if (lum > 225) pureWhiteCount++
            if (lum < 40) pureBlackCount++
            sumR += Color.red(c)
            sumG += Color.green(c)
            sumB += Color.blue(c)
        }

        val total = samplePoints.size
        // Solid white balloon requires high white perimeter density (>= 75%)
        val isSolidWhiteBubble = (pureWhiteCount >= total * 0.75)
        // Solid black balloon requires high black perimeter density (>= 75%)
        val isSolidBlackBubble = (pureBlackCount >= total * 0.75)

        val avgR = (sumR / total).toInt().coerceIn(0, 255)
        val avgG = (sumG / total).toInt().coerceIn(0, 255)
        val avgB = (sumB / total).toInt().coerceIn(0, 255)
        val avgColor = Color.rgb(avgR, avgG, avgB)

        val sampledColor = when {
            isSolidWhiteBubble -> Color.WHITE
            isSolidBlackBubble -> Color.BLACK
            else -> avgColor
        }

        return InpaintContext(isSolidWhiteBubble, isSolidBlackBubble, sampledColor)
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
