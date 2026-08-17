package com.rebelroot.omni.browser

import org.junit.Test
import org.mozilla.geckoview.GeckoSession

class GeckoApiTest {
    @Test
    fun testGeckoMethods() {
        val methods = GeckoSession::class.java.methods.map { it.name }.sorted().distinct()
        println("GeckoSession methods: $methods")
    }
}
