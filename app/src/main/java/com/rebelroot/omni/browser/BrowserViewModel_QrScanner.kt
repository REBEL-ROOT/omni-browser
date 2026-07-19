package com.rebelroot.omni.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.tools.qrcode.QrCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult

fun BrowserViewModel.scanPageForQrCodes() {
    val geckoView = activeGeckoViewRef?.get()
    if (geckoView == null) {
        qrScanError = "No active page to scan"
        return
    }

    isQrScanning = true
    qrScanResults = emptyList()
    qrScanError = null

    geckoView.capturePixels().then { bitmap ->
        if (bitmap == null) {
            viewModelScope.launch(Dispatchers.Main) {
                isQrScanning = false
                qrScanError = "Could not capture page"
            }
            return@then GeckoResult.fromValue(false)
        }

        // Run FOSS ZXing barcode detection on the captured bitmap
        val result = QrCodeDecoder.decodeBitmap(bitmap)
        val results = if (result != null) listOf(result) else emptyList()
        viewModelScope.launch(Dispatchers.Main) {
            isQrScanning = false
            qrScanResults = results
            if (results.isEmpty()) {
                qrScanError = "No QR codes found on this page"
            }
        }

        GeckoResult.fromValue(true)
    }.exceptionally { throwable ->
        viewModelScope.launch(Dispatchers.Main) {
            isQrScanning = false
            qrScanError = "Capture failed: ${throwable.localizedMessage}"
        }
        GeckoResult.fromValue(false)
    }
}

fun BrowserViewModel.scanImageForQrCodes(context: Context, uri: Uri) {
    isQrScanning = true
    qrScanResults = emptyList()
    qrScanError = null

    try {
        val result = QrCodeDecoder.decodeUri(context, uri)
        val results = if (result != null) listOf(result) else emptyList()
        viewModelScope.launch(Dispatchers.Main) {
            isQrScanning = false
            qrScanResults = results
            if (results.isEmpty()) {
                qrScanError = "No QR codes found in this image"
            }
        }
    } catch (e: Exception) {
        isQrScanning = false
        qrScanError = "Failed to load image: ${e.localizedMessage}"
    }
}

fun BrowserViewModel.clearQrScanResults() {
    qrScanResults = emptyList()
    qrScanError = null
}
