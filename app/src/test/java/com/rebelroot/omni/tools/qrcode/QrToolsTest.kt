package com.rebelroot.omni.tools.qrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import org.junit.Assert.*
import org.junit.Test

class QrToolsTest {

    @Test
    fun testQrEncodingAndDecoding() {
        val testUrl = "https://example.com/test-qr-code"
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            testUrl,
            BarcodeFormat.QR_CODE,
            256,
            256
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val yuvBytes = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                yuvBytes[y * width + x] = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
            }
        }

        val source = PlanarYUVLuminanceSource(
            yuvBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)

        assertNotNull(result)
        assertEquals(testUrl, result.text)
    }

    @Test
    fun testRotatedQrDecoding() {
        val testContent = "omni-browser-rotated-test"
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            testContent,
            BarcodeFormat.QR_CODE,
            200,
            200
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val yuvBytes = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                yuvBytes[y * width + x] = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
            }
        }

        // Rotate 90 degrees
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - y - 1)] = yuvBytes[y * width + x]
            }
        }

        val source = PlanarYUVLuminanceSource(
            rotated,
            height,
            width,
            0,
            0,
            height,
            width,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)

        assertNotNull(result)
        assertEquals(testContent, result.text)
    }
}
