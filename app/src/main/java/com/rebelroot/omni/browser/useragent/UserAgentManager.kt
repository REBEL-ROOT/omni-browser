/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser.useragent

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class UserAgentPreset(val id: String, val displayName: String, val userAgentString: String) {
    DEFAULT("default", "Default (Browser Engine)", ""),
    CHROME_ANDROID("chrome_android", "Chrome (Android Mobile)", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"),
    FIREFOX_ANDROID("firefox_android", "Firefox (Android Mobile)", "Mozilla/5.0 (Android 14; Mobile; rv:129.0) Gecko/129.0 Firefox/129.0"),
    SAFARI_IPHONE("safari_iphone", "Safari (iPhone / iOS)", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/605.1.15"),
    CHROME_DESKTOP("chrome_desktop", "Chrome (Windows Desktop)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"),
    FIREFOX_DESKTOP("firefox_desktop", "Firefox (Windows Desktop)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"),
    SAFARI_MAC("safari_mac", "Safari (macOS Desktop)", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"),
    CUSTOM("custom", "Custom UA String", "");

    companion object {
        fun fromId(id: String): UserAgentPreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class UserAgentSiteRule(
    val id: String,
    val domain: String,
    val presetId: String,
    val customUaString: String = "",
    val isEnabled: Boolean = true
) {
    val effectiveUserAgent: String
        get() {
            val preset = UserAgentPreset.fromId(presetId)
            return if (preset == UserAgentPreset.CUSTOM) customUaString.trim() else preset.userAgentString
        }
}

class UserAgentManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("omni_user_agent_prefs", Context.MODE_PRIVATE)

    private val _globalPreset = MutableStateFlow(loadGlobalPreset())
    val globalPreset: StateFlow<UserAgentPreset> = _globalPreset.asStateFlow()

    private val _globalCustomUa = MutableStateFlow(loadGlobalCustomUa())
    val globalCustomUa: StateFlow<String> = _globalCustomUa.asStateFlow()

    private val _siteRules = MutableStateFlow<List<UserAgentSiteRule>>(loadSiteRules())
    val siteRules: StateFlow<List<UserAgentSiteRule>> = _siteRules.asStateFlow()

    private fun loadGlobalPreset(): UserAgentPreset {
        val id = prefs.getString("global_preset_id", UserAgentPreset.DEFAULT.id) ?: UserAgentPreset.DEFAULT.id
        return UserAgentPreset.fromId(id)
    }

    private fun loadGlobalCustomUa(): String {
        return prefs.getString("global_custom_ua", "") ?: ""
    }

    private fun loadSiteRules(): List<UserAgentSiteRule> {
        val json = prefs.getString("site_rules_json", null) ?: return emptyList()
        val list = mutableListOf<UserAgentSiteRule>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserAgentSiteRule(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        domain = obj.optString("domain", "*"),
                        presetId = obj.optString("presetId", UserAgentPreset.DEFAULT.id),
                        customUaString = obj.optString("customUaString", ""),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun setGlobalPreset(preset: UserAgentPreset, customUa: String = "") {
        _globalPreset.value = preset
        _globalCustomUa.value = customUa
        prefs.edit()
            .putString("global_preset_id", preset.id)
            .putString("global_custom_ua", customUa)
            .apply()
    }

    fun addOrUpdateSiteRule(domain: String, preset: UserAgentPreset, customUa: String = "", ruleId: String? = null) {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull() ?: "*"
        val current = _siteRules.value.toMutableList()
        val existingIndex = if (ruleId != null) {
            current.indexOfFirst { it.id == ruleId }
        } else {
            current.indexOfFirst { it.domain.equals(cleanDomain, ignoreCase = true) }
        }

        val rule = UserAgentSiteRule(
            id = ruleId ?: java.util.UUID.randomUUID().toString(),
            domain = cleanDomain,
            presetId = preset.id,
            customUaString = customUa,
            isEnabled = true
        )

        if (existingIndex != -1) {
            current[existingIndex] = rule
        } else {
            current.add(0, rule)
        }

        _siteRules.value = current
        saveSiteRules()
    }

    fun toggleSiteRule(ruleId: String, isEnabled: Boolean) {
        val current = _siteRules.value.toMutableList()
        val index = current.indexOfFirst { it.id == ruleId }
        if (index != -1) {
            current[index] = current[index].copy(isEnabled = isEnabled)
            _siteRules.value = current
            saveSiteRules()
        }
    }

    fun removeSiteRule(ruleId: String) {
        val current = _siteRules.value.filterNot { it.id == ruleId }
        _siteRules.value = current
        saveSiteRules()
    }

    fun removeSiteRulesForDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull() ?: "*"
        val current = _siteRules.value.filterNot { it.domain.equals(cleanDomain, ignoreCase = true) }
        _siteRules.value = current
        saveSiteRules()
    }

    private fun saveSiteRules() {
        val array = JSONArray()
        for (rule in _siteRules.value) {
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("domain", rule.domain)
            obj.put("presetId", rule.presetId)
            obj.put("customUaString", rule.customUaString)
            obj.put("isEnabled", rule.isEnabled)
            array.put(obj)
        }
        prefs.edit().putString("site_rules_json", array.toString()).apply()
    }

    /**
     * Resolves the effective User Agent string for a given URL.
     * Return null if default Gecko behavior should be used.
     */
    fun resolveUserAgent(url: String?): String? {
        if (url.isNullOrBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) {
            return resolveGlobalUserAgent()
        }

        val cleanDomain = try {
            val host = android.net.Uri.parse(url).host?.trim()?.lowercase()?.removePrefix("www.")
            host?.split("/")?.firstOrNull() ?: ""
        } catch (_: Exception) { "" }

        if (cleanDomain.isNotBlank()) {
            val siteRule = _siteRules.value.firstOrNull { rule ->
                if (!rule.isEnabled) return@firstOrNull false
                val rDomain = rule.domain.trim().lowercase().removePrefix("www.")
                rDomain == cleanDomain || cleanDomain.endsWith(".$rDomain") || rDomain.endsWith(".$cleanDomain")
            }
            if (siteRule != null) {
                val ua = siteRule.effectiveUserAgent
                if (ua.isNotBlank()) return ua
            }
        }

        return resolveGlobalUserAgent()
    }

    private fun resolveGlobalUserAgent(): String? {
        val preset = _globalPreset.value
        return when (preset) {
            UserAgentPreset.DEFAULT -> null
            UserAgentPreset.CUSTOM -> _globalCustomUa.value.ifBlank { null }
            else -> preset.userAgentString.ifBlank { null }
        }
    }
}
