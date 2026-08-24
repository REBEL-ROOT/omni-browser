package com.rebelroot.omni.sync.settings

import com.rebelroot.omni.sync.model.Hlc
import com.rebelroot.omni.sync.model.HlcClock
import java.util.concurrent.ConcurrentHashMap

data class SyncedSetting(
    val key: String,
    val valueJson: String,
    val hlc: Hlc
)

class SettingsSyncAdapter(
    private val clock: HlcClock
) {
    companion object {
        val ALLOWLISTED_KEYS = setOf(
            "search_engine",
            "adblock_enabled",
            "tracker_blocking_level",
            "https_only_mode",
            "do_not_track",
            "theme_mode"
        )
    }

    private val localSettings = ConcurrentHashMap<String, SyncedSetting>()

    fun updateLocalSetting(key: String, valueJson: String): SyncedSetting? {
        if (!ALLOWLISTED_KEYS.contains(key)) return null // Reject non-portable keys
        val hlc = clock.now()
        val setting = SyncedSetting(key, valueJson, hlc)
        localSettings[key] = setting
        return setting
    }

    fun applyRemoteSetting(key: String, valueJson: String, remoteHlc: Hlc): Boolean {
        if (!ALLOWLISTED_KEYS.contains(key)) return false // Reject non-portable keys
        clock.update(remoteHlc)

        val existing = localSettings[key]
        if (existing == null || remoteHlc.compareTo(existing.hlc) > 0) {
            localSettings[key] = SyncedSetting(key, valueJson, remoteHlc)
            return true
        }
        return false // Stale remote update dropped via LWW
    }

    fun getSetting(key: String): SyncedSetting? = localSettings[key]

    fun allSettings(): List<SyncedSetting> = localSettings.values.toList()
}
