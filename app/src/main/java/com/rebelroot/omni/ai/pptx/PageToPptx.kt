/*
 * Omni Browser - Page → PPTX high-level helper
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Bridges browser state (page title, summary text) into PptxSlide list.
 */
package com.rebelroot.omni.ai.pptx

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

private const val TAG = "PageToPptx"

object PageToPptx {

    /**
     * Build a presentation from a list of bullet-point items and save it
     * to the cache directory. Returns the FileProvider URI for sharing.
     */
    fun buildPresentation(
        context: Context,
        title: String,
        bulletsBySlide: List<List<String>>,
        fileName: String = "omni_presentation"
    ): Uri? {
        if (bulletsBySlide.isEmpty()) {
            Log.w(TAG, "buildPresentation: no slides")
            return null
        }
        val slides = bulletsBySlide.mapIndexed { idx, bullets ->
            val slideTitle = when {
                idx == 0 && title.isNotBlank() -> title
                else -> "Slide ${idx + 1}"
            }
            PptxSlide(title = slideTitle, bullets = bullets)
        }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "omni_presentation" }
        val outFile = PptxGenerator.writeToCache(context, slides, title, safeName) ?: return null
        return try {
            FileProvider.getUriForFile(
                context,
                "com.rebelroot.omni.fileprovider",
                outFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider getUriForFile failed", e)
            null
        }
    }

    /**
     * Build a single-slide presentation from a title and a chunk of body
     * text. Long body text is split into multiple slides.
     */
    fun buildFromText(
        context: Context,
        title: String,
        body: String,
        fileName: String = "omni_presentation"
    ): Uri? {
        val chunks = chunkText(body)
        val slides = chunks.mapIndexed { idx, chunk ->
            PptxSlide(
                title = title,
                bullets = chunk
            )
        }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "omni_presentation" }
        val outFile = PptxGenerator.writeToCache(context, slides, title, safeName) ?: return null
        return try {
            FileProvider.getUriForFile(
                context,
                "com.rebelroot.omni.fileprovider",
                outFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider getUriForFile failed", e)
            null
        }
    }

    /**
     * Build a title + image cover slide and save it.
     */
    fun buildWithCoverImage(
        context: Context,
        title: String,
        bullets: List<String>,
        coverImage: Bitmap,
        fileName: String = "omni_presentation"
    ): Uri? {
        val coverBytes = bitmapToBytes(coverImage, "image/png")
        val slides = mutableListOf<PptxSlide>()
        slides.add(PptxSlide(title = title, imageBytes = coverBytes, imageMime = "image/png"))
        val bodyChunks = chunkTextList(bullets, 6)
        for (chunk in bodyChunks) {
            slides.add(PptxSlide(title = title, bullets = chunk))
        }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "omni_presentation" }
        val outFile = PptxGenerator.writeToCache(context, slides, title, safeName) ?: return null
        return try {
            FileProvider.getUriForFile(
                context,
                "com.rebelroot.omni.fileprovider",
                outFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider getUriForFile failed", e)
            null
        }
    }

    private fun chunkText(text: String, maxLinesPerSlide: Int = 7): List<List<String>> {
        if (text.isBlank()) return listOf(emptyList())
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return listOf(emptyList())
        val chunks = mutableListOf<List<String>>()
        var i = 0
        while (i < lines.size) {
            val end = max(i + 1, (i + maxLinesPerSlide).coerceAtMost(lines.size))
            chunks.add(lines.subList(i, end))
            i = end
        }
        return chunks
    }

    private fun chunkTextList(items: List<String>, perSlide: Int): List<List<String>> {
        if (items.isEmpty()) return listOf(emptyList())
        val chunks = mutableListOf<List<String>>()
        var i = 0
        while (i < items.size) {
            val end = (i + perSlide).coerceAtMost(items.size)
            chunks.add(items.subList(i, end))
            i = end
        }
        return chunks
    }

    private fun bitmapToBytes(bitmap: Bitmap, mime: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val format = if (mime.contains("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bitmap.compress(format, 90, baos)
        return baos.toByteArray()
    }

    /**
     * Fetch a remote image (e.g., favicon) and return its bytes, or null
     * on failure. Use this to attach a cover image to a slide.
     */
    fun fetchImageBytes(url: String, maxBytes: Int = 1_500_000): ByteArray? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.size > maxBytes) null else bytes
        } catch (e: Exception) {
            Log.w(TAG, "fetchImageBytes($url) failed: ${e.message}")
            null
        }
    }
}
