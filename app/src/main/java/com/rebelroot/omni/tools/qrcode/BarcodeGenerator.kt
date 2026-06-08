package com.rebelroot.omni.tools.qrcode

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

object BarcodeGenerator {
    private const val TAG = "BarcodeGenerator"

    /**
     * Generates a QR Code Bitmap with specified dimensions and colors
     */
    fun generateQRCode(
        text: String,
        size: Int = 512,
        foreground: Int = 0xFF1C1D21.toInt(),
        background: Int = 0xFFFFFFFF.toInt()
    ): Bitmap? {
        if (text.trim().isEmpty()) return null

        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1) // Clean, slim border
            }

            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) foreground else background
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }

        } catch (e: WriterException) {
            Log.e(TAG, "Failed to write and encode QR code bitmap", e)
            null
        }
    }
}
