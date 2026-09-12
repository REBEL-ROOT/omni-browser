package com.rebelroot.omni.browser

import org.junit.Assert.*
import org.junit.Test
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.lang.reflect.Modifier

class WebExtensionSessionDelegatesTest {

    @Test
    fun testWebExtensionSessionControllerMethodsExist() {
        val sessionControllerCls = Class.forName("org.mozilla.geckoview.WebExtension\$SessionController")
        assertNotNull(sessionControllerCls)

        // Verify setActionDelegate exists and accepts (WebExtension, ActionDelegate)
        val setActionDelegateMethod = sessionControllerCls.getMethod(
            "setActionDelegate",
            WebExtension::class.java,
            WebExtension.ActionDelegate::class.java
        )
        assertNotNull(setActionDelegateMethod)
        assertTrue(Modifier.isPublic(setActionDelegateMethod.modifiers))

        // Verify setTabDelegate exists and accepts (WebExtension, SessionTabDelegate)
        val setTabDelegateMethod = sessionControllerCls.getMethod(
            "setTabDelegate",
            WebExtension::class.java,
            WebExtension.SessionTabDelegate::class.java
        )
        assertNotNull(setTabDelegateMethod)
        assertTrue(Modifier.isPublic(setTabDelegateMethod.modifiers))
    }

    @Test
    fun testActionDelegateMethods() {
        val actionDelegateCls = Class.forName("org.mozilla.geckoview.WebExtension\$ActionDelegate")
        assertNotNull(actionDelegateCls)

        val methods = actionDelegateCls.methods.map { it.name }
        assertTrue(methods.contains("onBrowserAction"))
        assertTrue(methods.contains("onPageAction"))
        assertTrue(methods.contains("onOpenPopup"))
        assertTrue(methods.contains("onTogglePopup"))
    }

    @Test
    fun testSessionTabDelegateMethods() {
        val tabDelegateCls = Class.forName("org.mozilla.geckoview.WebExtension\$SessionTabDelegate")
        assertNotNull(tabDelegateCls)

        val methods = tabDelegateCls.methods.map { it.name }
        assertTrue(methods.contains("onCloseTab"))
        assertTrue(methods.contains("onUpdateTab"))
    }

    @Test
    fun testExtensionActionLookupHierarchy() {
        // Test tab-action map simulation
        val defaultActions = mutableMapOf<String, String>()
        val sessionActions = mutableMapOf<String, MutableMap<String, String>>()

        fun getAction(tabId: String?, extId: String): String? {
            return (if (tabId != null) sessionActions[tabId]?.get(extId) else null) ?: defaultActions[extId]
        }

        defaultActions["uBlock0@raymondhill.net"] = "GlobalAction"
        assertEquals("GlobalAction", getAction("tab-1", "uBlock0@raymondhill.net"))

        // When tab-1 receives an updated browser action (e.g. badge count '12')
        val tab1Map = sessionActions.getOrPut("tab-1") { mutableMapOf() }
        tab1Map["uBlock0@raymondhill.net"] = "Tab1Action_12"
        sessionActions["tab-1"] = tab1Map

        // Tab-1 should now return Tab1Action_12, while Tab-2 returns fallback GlobalAction
        assertEquals("Tab1Action_12", getAction("tab-1", "uBlock0@raymondhill.net"))
        assertEquals("GlobalAction", getAction("tab-2", "uBlock0@raymondhill.net"))
    }

    @Test
    fun testWebExtensionControllerSetTabActiveMethodExists() {
        val controllerCls = Class.forName("org.mozilla.geckoview.WebExtensionController")
        assertNotNull(controllerCls)

        val setTabActiveMethod = controllerCls.getMethod(
            "setTabActive",
            GeckoSession::class.java,
            Boolean::class.javaPrimitiveType
        )
        assertNotNull(setTabActiveMethod)
        assertTrue(Modifier.isPublic(setTabActiveMethod.modifiers))
    }

    @Test
    fun testGeckoRuntimeSettingsExtensionFlags() {
        val builderCls = Class.forName("org.mozilla.geckoview.GeckoRuntimeSettings\$Builder")
        assertNotNull(builderCls)

        val extProcMethod = builderCls.getMethod("extensionsProcessEnabled", Boolean::class.javaPrimitiveType)
        assertNotNull(extProcMethod)
        assertTrue(Modifier.isPublic(extProcMethod.modifiers))

        val extApiMethod = builderCls.getMethod("extensionsWebAPIEnabled", Boolean::class.javaPrimitiveType)
        assertNotNull(extApiMethod)
        assertTrue(Modifier.isPublic(extApiMethod.modifiers))

        val settings = org.mozilla.geckoview.GeckoRuntimeSettings.Builder()
            .extensionsProcessEnabled(false)
            .extensionsWebAPIEnabled(true)
            .build()
        assertEquals(false, settings.extensionsProcessEnabled)
        assertEquals(true, settings.extensionsWebAPIEnabled)
    }

    @Test
    fun testTabActiveLifecycleOrderingSimulation() {
        var isSessionOpen = false
        var activeTabNotifiedWithOpenSession = false
        val executionOrder = mutableListOf<String>()

        fun openSession() {
            isSessionOpen = true
            executionOrder.add("openSession")
        }

        fun setTabActive(active: Boolean) {
            if (active && isSessionOpen) {
                activeTabNotifiedWithOpenSession = true
            }
            executionOrder.add("setTabActive($active, isOpen=$isSessionOpen)")
        }

        // Simulate lazy tab selection: session must be opened BEFORE setTabActive
        openSession()
        setTabActive(true)

        assertTrue("setTabActive must be called while session is open", activeTabNotifiedWithOpenSession)
        assertEquals(listOf("openSession", "setTabActive(true, isOpen=true)"), executionOrder)
    }
}
