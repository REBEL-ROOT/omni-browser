package com.rebelroot.omni.browser

import org.junit.Test
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class GeckoViewApiAuditTest {

    @Test
    fun inspectDownloadClasses() {
        println("=== WebExtension.DownloadDelegate methods ===")
        val delCls = Class.forName("org.mozilla.geckoview.WebExtension\$DownloadDelegate")
        delCls.declaredMethods.forEach { println("  DownloadDelegate method: $it") }

        println("=== WebExtensionController createDownload ===")
        WebExtensionController::class.java.declaredMethods.forEach {
            if (it.name.contains("createDownload") || it.name.contains("download")) {
                println("  Controller method: $it")
            }
        }

        println("=== WebExtension.Download constructors and methods ===")
        val dlCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download")
        dlCls.declaredConstructors.forEach { println("  Constructor: $it") }
        dlCls.declaredMethods.forEach { println("  Method: $it") }

        println("=== WebExtension.Download.Info methods ===")
        val infoCls = Class.forName("org.mozilla.geckoview.WebExtension\$Download\$Info")
        infoCls.declaredMethods.forEach { println("  Info method: $it") }

        println("=== WebExtension.DownloadInitData ===")
        try {
            val initDataCls = Class.forName("org.mozilla.geckoview.WebExtension\$DownloadInitData")
            initDataCls.declaredConstructors.forEach { println("  InitData constructor: $it") }
            initDataCls.declaredMethods.forEach { println("  InitData method: $it") }
        } catch (e: Exception) {
            println("  DownloadInitData not found: $e")
        }

        println("=== WebExtension.MetaData ===")
        val metaCls = Class.forName("org.mozilla.geckoview.WebExtension\$MetaData")
        metaCls.declaredFields.forEach { println("  MetaData field: $it") }
        metaCls.declaredMethods.forEach { println("  MetaData method: $it") }
    }
}
