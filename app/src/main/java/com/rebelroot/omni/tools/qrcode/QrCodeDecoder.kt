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
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream
import java.nio.ByteBuffer

object QrCodeDecoder {

    /**
     * Decodes a QR code from a Compose/Android Bitmap.
     */
    fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val reader = MultiFormatReader()
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a QR code from a gallery/file Uri.
     * Uses a two-pass BitmapFactory decode: first reads image dimensions with
     * inJustDecodeBounds, then calculates an inSampleSize so the decoded
     * bitmap fits within 1024×1024 — preventing OOM on large gallery photos.
     */
    fun decodeUri(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        return try {
            // Pass 1: read dimensions only (no pixels allocated)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream?.close()

            // Calculate the largest power-of-2 sample size that keeps the image
            // within a 1024×1024 box — a safe size for ZXing to decode.
            val maxDim = 1024
            var sampleSize = 1
            var w = opts.outWidth
            var h = opts.outHeight
            while (w > maxDim || h > maxDim) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            // Pass 2: decode scaled-down bitmap
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
     * without performing expensive Bitmap allocations.
     */
    fun decodeImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val planes = imageProxy.planes
            val yBuffer: ByteBuffer = planes[0].buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)

            val width = imageProxy.width
            val height = imageProxy.height

            // PlanarYUVLuminanceSource decodes directly from raw YUV frame plane
            val source = PlanarYUVLuminanceSource(
                yArray, width, height,
                0, 0, width, height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
