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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision On-device Text Detection & Speech Bubble Clustering for Manga, Manhua, and Manhwa.
 *
 * Integrates ML Kit Text Recognition V2 with multi-script auto-detection and spatial clustering
 * to group all dialogue lines (in speech balloons or floating over artwork) into coherent blocks.
 */
class MangaTextDetector {

    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Detect and cluster speech bubbles and floating text from the given [bitmap].
     *
     * @param bitmap The manga page bitmap.
     * @param sourceLanguage Hint for OCR language script (e.g. "ja", "ko", "zh", "en", "auto").
     * @param isRtl Whether to apply Manga Right-to-Left panel reading order.
     */
    suspend fun detectDialogueBlocks(
        bitmap: Bitmap,
        sourceLanguage: String? = "auto",
        isRtl: Boolean = true
    ): List<MangaDialogueBlock> = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val normalizedLang = sourceLanguage?.lowercase() ?: "auto"

        val primaryRecognizer = selectRecognizer(normalizedLang)
        val primaryVisionText = processImageWithRecognizer(primaryRecognizer, inputImage)

        // Smart CJK Multi-Script Fallback (e.g. Chinese Manhua read under "ja" or "auto")
        val finalVisionText = if (normalizedLang == "auto" || normalizedLang == "ja") {
            val allText = primaryVisionText.textBlocks.joinToString("") { b -> b.lines.joinToString("") { it.text } }
            val hasKana = allText.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }
            val hasHanzi = allText.any { it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' }

            // If no Japanese Kana is present and Hanzi is found or primary OCR found few blocks, run Chinese OCR
            if (!hasKana && (hasHanzi || primaryVisionText.textBlocks.size < 2)) {
                val zhVisionText = processImageWithRecognizer(chineseRecognizer, inputImage)
                val zhText = zhVisionText.textBlocks.joinToString("") { b -> b.lines.joinToString("") { it.text } }
                if (zhText.length > allText.length) {
                    zhVisionText
                } else {
                    primaryVisionText
                }
            } else {
                primaryVisionText
            }
        } else {
            primaryVisionText
        }

        extractAndClusterSpeechBubbles(finalVisionText, bitmap.width, bitmap.height, sourceLanguage, isRtl)
    }

    private suspend fun processImageWithRecognizer(
        recognizer: TextRecognizer,
        inputImage: InputImage
    ): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                if (cont.isActive) cont.resume(text)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    private fun selectRecognizer(sourceLanguage: String?): TextRecognizer {
        val lang = sourceLanguage?.lowercase() ?: "auto"
        return when {
            lang.startsWith("zh") || lang == "chinese" -> chineseRecognizer
            lang.startsWith("ko") || lang == "korean" -> koreanRecognizer
            lang.startsWith("ja") || lang == "japanese" -> japaneseRecognizer
            lang.startsWith("en") || lang == "latin" || lang == "es" || lang == "fr" || lang == "de" -> latinRecognizer
            else -> japaneseRecognizer
        }
    }

    private fun extractAndClusterSpeechBubbles(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int,
        sourceLanguage: String?,
        isRtl: Boolean
    ): List<MangaDialogueBlock> {
        val rawLines = mutableListOf<MangaDialogueLine>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty() || box.width() < 4 || box.height() < 4) continue

                rawLines.add(
                    MangaDialogueLine(
                        text = text,
                        boundingBox = MangaRect(
                            box.left.toFloat(),
                            box.top.toFloat(),
                            box.right.toFloat(),
                            box.bottom.toFloat()
                        )
                    )
                )
            }
        }

        if (rawLines.isEmpty()) return emptyList()

        // Cluster lines that belong to the same speech bubble
        val clusters = clusterLinesIntoBubbles(rawLines, imageWidth, imageHeight)

        val blocks = clusters.mapIndexed { index, clusterLines ->
            val minX = clusterLines.minOf { it.boundingBox.left }
            val minY = clusterLines.minOf { it.boundingBox.top }
            val maxX = clusterLines.maxOf { it.boundingBox.right }
            val maxY = clusterLines.maxOf { it.boundingBox.bottom }

            val width = maxX - minX
            val height = maxY - minY
            val isVertical = height > width * 1.1f

            // In vertical Japanese/Chinese manga, columns are ordered from Right to Left
            val orderedLines = if (isVertical && isRtl) {
                clusterLines.sortedByDescending { it.boundingBox.centerX }
            } else {
                clusterLines.sortedBy { it.boundingBox.centerY }
            }

            val isCjk = isCjkScript(sourceLanguage)
            val combinedRawText = if (isCjk) {
                orderedLines.joinToString(separator = "") { it.text }
            } else {
                orderedLines.joinToString(separator = " ") { it.text }
            }

            MangaDialogueBlock(
                id = "bubble_${index + 1}_${UUID.randomUUID().toString().take(6)}",
                rawText = combinedRawText,
                boundingBox = MangaRect(minX, minY, maxX, maxY),
                lines = orderedLines,
                isVertical = isVertical
            )
        }

        // Sort speech bubbles in true reading order: Top-to-Bottom, Right-to-Left (for Manga) or Left-to-Right (Webtoon)
        val bandHeight = imageHeight * 0.12f // 12% vertical panel bands
        return blocks.sortedWith { a, b ->
            val aBand = (a.boundingBox.top / bandHeight).toInt()
            val bBand = (b.boundingBox.top / bandHeight).toInt()
            if (aBand != bBand) {
                aBand.compareTo(bBand)
            } else {
                if (isRtl) {
                    b.boundingBox.centerX.compareTo(a.boundingBox.centerX) // Right to Left
                } else {
                    a.boundingBox.centerX.compareTo(b.boundingBox.centerX) // Left to Right
                }
            }
        }
    }

    /**
     * Clusters disjointed OCR text lines based on spatial proximity into distinct dialogue balloons.
     */
    private fun clusterLinesIntoBubbles(
        lines: List<MangaDialogueLine>,
        imageWidth: Int,
        imageHeight: Int
    ): List<List<MangaDialogueLine>> {
        if (lines.isEmpty()) return emptyList()

        val maxBubbleDistanceX = imageWidth * 0.09f // 9% of page width horizontal proximity
        val maxBubbleDistanceY = imageHeight * 0.09f // 9% of page height vertical proximity

        val clusters = mutableListOf<MutableList<MangaDialogueLine>>()
        val visited = BooleanArray(lines.size) { false }

        for (i in lines.indices) {
            if (visited[i]) continue
            visited[i] = true

            val currentCluster = mutableListOf(lines[i])
            val queue = ArrayDeque<MangaDialogueLine>()
            queue.add(lines[i])

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val currentBox = current.boundingBox

                for (j in lines.indices) {
                    if (visited[j]) continue
                    val otherBox = lines[j].boundingBox

                    val distX = max(0f, max(currentBox.left - otherBox.right, otherBox.left - currentBox.right))
                    val distY = max(0f, max(currentBox.top - otherBox.bottom, otherBox.top - currentBox.bottom))

                    // If two lines are within speech balloon proximity, cluster them together
                    if (distX <= maxBubbleDistanceX && distY <= maxBubbleDistanceY) {
                        visited[j] = true
                        currentCluster.add(lines[j])
                        queue.add(lines[j])
                    }
                }
            }

            clusters.add(currentCluster)
        }

        return clusters
    }

    private fun isCjkScript(sourceLanguage: String?): Boolean {
        val lang = sourceLanguage?.lowercase() ?: "auto"
        return lang.startsWith("ja") || lang.startsWith("zh") || lang.startsWith("ko") || lang == "auto"
    }
}
