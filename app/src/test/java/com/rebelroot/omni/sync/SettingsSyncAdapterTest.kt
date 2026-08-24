package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.model.Hlc
import com.rebelroot.omni.sync.model.HlcClock
import com.rebelroot.omni.sync.settings.SettingsSyncAdapter
import org.junit.Assert.*
import org.junit.Test

class SettingsSyncAdapterTest {

    @Test
    fun settingsSync_enforcesAllowlist() {
        val clock = HlcClock("dev_android_01")
        val adapter = SettingsSyncAdapter(clock)

        // Portable allowed key succeeds
        val s1 = adapter.updateLocalSetting("search_engine", "\"duckduckgo\"")
        assertNotNull("Allowlisted key must succeed", s1)

        // Non-portable hardware/local key rejected
        val s2 = adapter.updateLocalSetting("wallpaper_path", "\"/sdcard/img.png\"")
        assertNull("Hardware key must be rejected", s2)

        val s3 = adapter.updateLocalSetting("ui_scale", "1.25")
        assertNull("Local UI scale must be rejected", s3)
    }

    @Test
    fun settingsSync_resolvesConflictsViaLww() {
        val clockA = HlcClock("dev_A")
        val clockB = HlcClock("dev_B")

        val adapterA = SettingsSyncAdapter(clockA)

        // Device A sets search engine at HLC t=1000
        val hlcA = Hlc.initial("dev_A", 1000L)
        adapterA.applyRemoteSetting("search_engine", "\"google\"", hlcA)

        // Stale update from Device B at HLC t=500 should be rejected
        val hlcBStale = Hlc.initial("dev_B", 500L)
        val appliedStale = adapterA.applyRemoteSetting("search_engine", "\"bing\"", hlcBStale)
        assertFalse("Stale update must be rejected", appliedStale)
        assertEquals("\"google\"", adapterA.getSetting("search_engine")?.valueJson)

        // Newer update from Device B at HLC t=2000 should win
        val hlcBNew = Hlc.initial("dev_B", 2000L)
        val appliedNew = adapterA.applyRemoteSetting("search_engine", "\"brave\"", hlcBNew)
        assertTrue("Newer update must win", appliedNew)
        assertEquals("\"brave\"", adapterA.getSetting("search_engine")?.valueJson)
    }
}
