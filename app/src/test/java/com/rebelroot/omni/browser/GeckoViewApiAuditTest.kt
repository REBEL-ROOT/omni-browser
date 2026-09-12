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

        println("=== GeckoSession WebExtension methods ===")
        org.mozilla.geckoview.GeckoSession::class.java.methods.forEach {
            if (it.name.contains("extension", ignoreCase = true) || 
                it.name.contains("webExtension", ignoreCase = true) ||
                it.returnType.name.contains("Extension", ignoreCase = true)) {
                println("  GeckoSession method: $it")
            }
        }

        println("=== WebExtensionController all methods ===")
        WebExtensionController::class.java.methods.forEach {
            println("  WebExtensionController method: $it")
        }

        println("=== WebExtension.SessionController methods ===")
        val sessCtrlCls = Class.forName("org.mozilla.geckoview.WebExtension\$SessionController")
        sessCtrlCls.declaredMethods.forEach { println("  SessionController method: $it") }

        println("=== WebExtension.SessionTabDelegate methods ===")
        try {
            val sessTabDelCls = Class.forName("org.mozilla.geckoview.WebExtension\$SessionTabDelegate")
            sessTabDelCls.declaredMethods.forEach { println("  SessionTabDelegate method: $it") }

            println("=== WebExtension.UpdateTabDetails fields & methods ===")
            val updTabCls = Class.forName("org.mozilla.geckoview.WebExtension\$UpdateTabDetails")
            updTabCls.declaredFields.forEach { println("  UpdateTabDetails field: $it") }
            updTabCls.declaredMethods.forEach { println("  UpdateTabDetails method: $it") }
        } catch (e: Exception) {
            println("  SessionTabDelegate error: $e")
        }

        println("=== GeckoRuntimeSettings methods ===")
        org.mozilla.geckoview.GeckoRuntimeSettings::class.java.declaredMethods.forEach {
            println("  GeckoRuntimeSettings method: $it")
        }
        println("=== GeckoRuntimeSettings.Builder methods ===")
        org.mozilla.geckoview.GeckoRuntimeSettings.Builder::class.java.declaredMethods.forEach {
            println("  GeckoRuntimeSettings.Builder method: $it")
        }
        println("=== GeckoSessionSettings methods ===")
        org.mozilla.geckoview.GeckoSessionSettings::class.java.declaredMethods.forEach {
            println("  GeckoSessionSettings method: $it")
        }
        println("=== GeckoSessionSettings.Builder methods ===")
        org.mozilla.geckoview.GeckoSessionSettings.Builder::class.java.declaredMethods.forEach {
            println("  GeckoSessionSettings.Builder method: $it")
        }
    }

    @Test
    fun testDefaultSettings() {
        val settings = org.mozilla.geckoview.GeckoRuntimeSettings.Builder()
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .build()
        println("extensionsProcessEnabled: ${settings.extensionsProcessEnabled}")
        println("extensionsWebAPIEnabled: ${settings.extensionsWebAPIEnabled}")
        println("fissionEnabled: ${settings.fissionEnabled}")
        println("isolatedProcessEnabled: ${settings.isolatedProcessEnabled}")

        val superCls = org.mozilla.geckoview.GeckoRuntimeSettings::class.java.superclass
        val getPrefsMapMethod = superCls.getDeclaredMethod("getPrefsMap")
        getPrefsMapMethod.isAccessible = true
        val prefsMap = getPrefsMapMethod.invoke(settings) as? Map<*, *> ?: emptyMap<Any, Any>()
        println("Prefs count: ${prefsMap.size}")
        for (entry in prefsMap.entries) {
            println("  pref: ${entry.key} = ${entry.value}")
        }
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
