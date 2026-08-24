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

        println("=== WebExtension methods ===")
        WebExtension::class.java.declaredMethods.forEach { println("  WebExtension method: $it") }

        println("=== WebExtension.Action methods ===")
        val actionCls = Class.forName("org.mozilla.geckoview.WebExtension\$Action")
        actionCls.declaredMethods.forEach { println("  Action method: $it") }

        println("=== WebExtension.TabDelegate methods ===")
        val tabDelCls = Class.forName("org.mozilla.geckoview.WebExtension\$TabDelegate")
        tabDelCls.declaredMethods.forEach { println("  TabDelegate method: $it") }

        println("=== WebExtension.ActionDelegate methods ===")
        val actDelCls = Class.forName("org.mozilla.geckoview.WebExtension\$ActionDelegate")
        actDelCls.declaredMethods.forEach { println("  ActionDelegate method: $it") }

        println("=== WebExtensionController.PromptDelegate methods ===")
        val promptDelCls = Class.forName("org.mozilla.geckoview.WebExtensionController\$PromptDelegate")
        promptDelCls.declaredMethods.forEach { println("  PromptDelegate method: $it") }
    }

    @Test
    fun testPermissionPromptResponse() {
        val response = WebExtension.PermissionPromptResponse(true, true, false)
        org.junit.Assert.assertEquals(true, response.isPermissionsGranted)
        org.junit.Assert.assertEquals(true, response.isPrivateModeGranted)
        org.junit.Assert.assertEquals(false, response.isTechnicalAndInteractionDataGranted)
    }

    @Test
    fun testSafeExtensionAccess() {
        val nullExt: WebExtension? = null
        org.junit.Assert.assertNull(nullExt.safeId)
        org.junit.Assert.assertNull(nullExt.safeMetaData)
    }
}
