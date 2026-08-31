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

package com.rebelroot.omni.tools.qrcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeReader
import java.io.InputStream
import java.nio.ByteBuffer

object QrCodeDecoder {

    private val baseHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39
        ),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * Decodes a single QR / barcode text from a Bitmap.
     */
    fun decodeBitmap(bitmap: Bitmap): String? {
        return decodeBitmapAll(bitmap).firstOrNull()
    }

    /**
     * Decodes ALL QR codes and barcodes found on a Bitmap.
     * Uses multiple binarizers, multi-code readers, inverted color fallback,
     * and downscaled resolution fallback for high-DPI full-page captures.
     */
    fun decodeBitmapAll(bitmap: Bitmap): List<String> {
        val foundResults = mutableListOf<String>()

        fun scanLuminanceSource(source: LuminanceSource) {
            val binarizers = listOf(
                HybridBinarizer(source),
                GlobalHistogramBinarizer(source)
            )

            for (binarizer in binarizers) {
                val binaryBitmap = BinaryBitmap(binarizer)

                // 1. Try QRCodeMultiReader for multiple QR codes
                try {
                    val multiReader = QRCodeMultiReader()
                    val results = multiReader.decodeMultiple(binaryBitmap, baseHints)
                    for (res in results) {
                        if (!res.text.isNullOrBlank() && !foundResults.contains(res.text)) {
                            foundResults.add(res.text)
                        }
                    }
                } catch (_: Exception) {}

                // 2. Try GenericMultipleBarcodeReader for mixed/general barcodes
                try {
                    val genericMulti = GenericMultipleBarcodeReader(MultiFormatReader())
                    val results = genericMulti.decodeMultiple(binaryBitmap, baseHints)
                    for (res in results) {
                        if (!res.text.isNullOrBlank() && !foundResults.contains(res.text)) {
                            foundResults.add(res.text)
                        }
                    }
                } catch (_: Exception) {}

                // 3. Fallback to single reader
                if (foundResults.isEmpty()) {
                    try {
                        val reader = MultiFormatReader()
                        val result = reader.decode(binaryBitmap, baseHints)
                        if (!result.text.isNullOrBlank() && !foundResults.contains(result.text)) {
                            foundResults.add(result.text)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val rgbSource = RGBLuminanceSource(width, height, pixels)
            scanLuminanceSource(rgbSource)

            // Try inverted luminance source (useful for dark themes / inverted QR codes)
            if (foundResults.isEmpty()) {
                scanLuminanceSource(rgbSource.invert())
            }

            // If still empty and image is very large, try a downscaled version (1024 max)
            if (foundResults.isEmpty() && (width > 1200 || height > 1200)) {
                val scale = 1024f / maxOf(width, height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                if (scaledBitmap != null) {
                    val sWidth = scaledBitmap.width
                    val sHeight = scaledBitmap.height
                    val sPixels = IntArray(sWidth * sHeight)
                    scaledBitmap.getPixels(sPixels, 0, sWidth, 0, 0, sWidth, sHeight)
                    scaledBitmap.recycle()

                    val scaledSource = RGBLuminanceSource(sWidth, sHeight, sPixels)
                    scanLuminanceSource(scaledSource)
                    if (foundResults.isEmpty()) {
                        scanLuminanceSource(scaledSource.invert())
                    }
                }
            }
        } catch (_: Exception) {}

        return foundResults
    }

    /**
     * Decodes a QR code from a gallery/file Uri.
     */
    fun decodeUri(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream?.close()

            val maxDim = 1280
            var sampleSize = 1
            var w = opts.outWidth
            var h = opts.outHeight
            while (w > maxDim || h > maxDim) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOpts)
            if (bitmap != null) {
                val result = decodeBitmap(bitmap)
                bitmap.recycle()
                result
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Decodes a QR code directly from CameraX ImageProxy (YUV_420_888 frame)
     * correctly handling rowStride padding and frame rotation.
     */
    fun decodeImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val yBuffer: ByteBuffer = plane.buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)

            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val rotation = imageProxy.imageInfo.rotationDegrees

            // Rotate Y-plane and strip rowStride in a single fast pass
            val (rotatedBytes, rotWidth, rotHeight) = when (rotation) {
                90 -> {
                    val rotated = ByteArray(width * height)
                    for (y in 0 until height) {
                        val srcRow = y * rowStride
                        for (x in 0 until width) {
                            rotated[x * height + (height - y - 1)] = yBytes[srcRow + x]
                        }
                    }
                    Triple(rotated, height, width)
                }
                180 -> {
                    val rotated = ByteArray(width * height)
                    for (y in 0 until height) {
                        val srcRow = y * rowStride
                        for (x in 0 until width) {
                            rotated[(height - y - 1) * width + (width - x - 1)] = yBytes[srcRow + x]
                        }
                    }
                    Triple(rotated, width, height)
                }
                270 -> {
                    val rotated = ByteArray(width * height)
                    for (y in 0 until height) {
                        val srcRow = y * rowStride
                        for (x in 0 until width) {
                            rotated[(width - x - 1) * height + y] = yBytes[srcRow + x]
                        }
                    }
                    Triple(rotated, height, width)
                }
                else -> {
                    if (rowStride == width) {
                        Triple(yBytes, width, height)
                    } else {
                        val stripped = ByteArray(width * height)
                        for (y in 0 until height) {
                            System.arraycopy(yBytes, y * rowStride, stripped, y * width, width)
                        }
                        Triple(stripped, width, height)
                    }
                }
            }

            val source = PlanarYUVLuminanceSource(
                rotatedBytes,
                rotWidth,
                rotHeight,
                0,
                0,
                rotWidth,
                rotHeight,
                false
            )

            val primaryResult = decodeLuminanceSource(source)
            if (primaryResult != null) return primaryResult

            // Fallback: if rotation != 0, try original un-rotated stripped frame as well
            if (rotation != 0) {
                val stripped = if (rowStride == width) {
                    yBytes
                } else {
                    val bytes = ByteArray(width * height)
                    for (y in 0 until height) {
                        System.arraycopy(yBytes, y * rowStride, bytes, y * width, width)
                    }
                    bytes
                }
                val rawSource = PlanarYUVLuminanceSource(
                    stripped,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
                )
                val fallbackResult = decodeLuminanceSource(rawSource)
                if (fallbackResult != null) return fallbackResult
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val binarizers = listOf(
            HybridBinarizer(source),
            GlobalHistogramBinarizer(source)
        )

        for (binarizer in binarizers) {
            try {
                val binaryBitmap = BinaryBitmap(binarizer)
                val reader = MultiFormatReader()
                val result = reader.decode(binaryBitmap, baseHints)
                if (!result.text.isNullOrBlank()) {
                    return result.text
                }
            } catch (_: Exception) {}
        }

        // Try inverted
        try {
            val invBinary = BinaryBitmap(HybridBinarizer(source.invert()))
            val reader = MultiFormatReader()
            val result = reader.decode(invBinary, baseHints)
            if (!result.text.isNullOrBlank()) {
                return result.text
            }
        } catch (_: Exception) {}

        return null
    }
}
