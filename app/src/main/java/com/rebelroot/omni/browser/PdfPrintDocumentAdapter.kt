package com.rebelroot.omni.browser

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import org.mozilla.geckoview.GeckoResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Custom PrintDocumentAdapter for GeckoSession print/save-as-pdf workflows.
 *
 * Replaces GeckoViewPrintDocumentAdapter which has a critical defect: it calls deleteTempPdf()
 * whenever a layout pass is cancelled (cancellationSignal.isCanceled == true).
 * In Android's system PrintSpooler / PrintActivity, changing print destinations (e.g. from
 * default printer to "Save as PDF") or paper size cancels prior layout passes, causing
 * GeckoView's built-in adapter to delete the backing PDF file mid-flow and hang on "Preparing preview...".
 *
 * This adapter persists the generated PDF file until onFinish() is invoked, and acquires a
 * temporary partial WakeLock to prevent Android's background app freezer from freezing the
 * browser process while the system PrintActivity is open.
 */
class PdfPrintDocumentAdapter(
    private val pdfFile: File,
    private val documentName: String,
    private val context: Context,
    private val onFinishResult: GeckoResult<Boolean>? = null
) : PrintDocumentAdapter() {

    companion object {
        private const val TAG = "PdfPrintDocAdapter"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    init {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omni:print_job")?.apply {
                acquire(10 * 60 * 1000L) // 10 minutes timeout to prevent system freezer
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock for printing", e)
        }
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            Log.d(TAG, "onLayout: cancelled by system")
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        val changed = newAttributes != oldAttributes
        callback?.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            Log.d(TAG, "onWrite: cancelled before write start")
            callback?.onWriteCancelled()
            return
        }

        Thread {
            try {
                if (cancellationSignal?.isCanceled == true) {
                    Log.d(TAG, "onWrite: cancelled during write")
                    callback?.onWriteCancelled()
                    return@Thread
                }
                val destFd = destination?.fileDescriptor
                if (destFd != null && pdfFile.exists()) {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destFd).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    Log.i(TAG, "onWrite: successfully copied PDF bytes to print spooler")
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } else {
                    Log.e(TAG, "onWrite: destination fd or pdfFile missing (exists=${pdfFile.exists()})")
                    callback?.onWriteFailed("Missing destination file descriptor or PDF file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onWrite: exception writing PDF to print spooler", e)
                callback?.onWriteFailed(e.message)
            }
        }.start()
    }

    override fun onFinish() {
        super.onFinish()
        Log.i(TAG, "onFinish: print job completed or cancelled, cleaning up temp file and wakelock")
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock", e)
        }
        try {
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting temp print file", e)
        }
        onFinishResult?.complete(true)
    }
}
