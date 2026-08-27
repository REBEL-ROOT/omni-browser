/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser.adblock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID

data class AdBlockProvider(
    val id: String,
    val name: String,
    val url: String,
    val isPreset: Boolean,
    var isEnabled: Boolean,
    var ruleCount: Int = 0,
    var lastUpdated: Long = 0L
)

class AdBlockManager(private val context: Context) {

    companion object {
        private const val TAG = "AdBlockManager"
        private const val PREF_NAME = "adblock_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_TOTAL_BLOCKED = "total_blocked"
        private const val KEY_PROVIDERS_JSON = "providers_json"

        val PRESET_PROVIDERS = listOf(
            AdBlockProvider(
                id = "easylist_base",
                name = "EasyList Base (Ads & Banners)",
                url = "https://easylist.to/easylist/easylist.txt",
                isPreset = true,
                isEnabled = true
            ),
            AdBlockProvider(
                id = "adguard_base",
                name = "AdGuard Base Filter",
                url = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
                isPreset = true,
                isEnabled = true
            ),
            AdBlockProvider(
                id = "adguard_anti_adblock",
                name = "AdGuard Anti-AdBlock Defusers",
                url = "https://filters.adtidy.org/extension/ublock/filters/14.txt",
                isPreset = true,
                isEnabled = true
            ),
            AdBlockProvider(
                id = "ublock_unbreak",
                name = "uBlock Origin Unbreak & Anti-Block",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
                isPreset = true,
                isEnabled = true
            ),
            AdBlockProvider(
                id = "peter_lowe",
                name = "Peter Lowe's Ad & Tracker List",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                isPreset = true,
                isEnabled = true
            ),
            AdBlockProvider(
                id = "steven_black",
                name = "Steven Black Unified Hosts",
                url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                isPreset = true,
                isEnabled = false
            ),
            AdBlockProvider(
                id = "fanboy_social",
                name = "Fanboy Social Tracking Blocker",
                url = "https://easylist.to/easylist/fanboy-social.txt",
                isPreset = true,
                isEnabled = true
            )
        )
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()

    var totalBlockedCount: Long
        get() = prefs.getLong(KEY_TOTAL_BLOCKED, 0L)
        private set(value) = prefs.edit().putLong(KEY_TOTAL_BLOCKED, value).apply()

    private val _providers = MutableStateFlow<List<AdBlockProvider>>(emptyList())
    val providers: StateFlow<List<AdBlockProvider>> = _providers.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val blockedDomains = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        loadProviders()
        loadCachedRules()
    }

    fun incrementBlockedCount(amount: Int = 1) {
        totalBlockedCount += amount
    }

    fun clearBlockedStats() {
        totalBlockedCount = 0L
        prefs.edit().putLong(KEY_TOTAL_BLOCKED, 0L).apply()
    }

    private fun loadProviders() {
        val savedJson = prefs.getString(KEY_PROVIDERS_JSON, null)
        val list = mutableListOf<AdBlockProvider>()

        if (savedJson.isNullOrEmpty()) {
            list.addAll(PRESET_PROVIDERS)
        } else {
            try {
                val array = JSONArray(savedJson)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val p = AdBlockProvider(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        isPreset = obj.optBoolean("isPreset", false),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        ruleCount = obj.optInt("ruleCount", 0),
                        lastUpdated = obj.optLong("lastUpdated", 0L)
                    )
                    list.add(p)
                }

                for (preset in PRESET_PROVIDERS) {
                    if (list.none { it.id == preset.id }) {
                        list.add(preset)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved providers, falling back to default", e)
                list.addAll(PRESET_PROVIDERS)
            }
        }
        _providers.value = list
        saveProviders()
    }

    private fun saveProviders() {
        try {
            val array = JSONArray()
            for (p in _providers.value) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("url", p.url)
                    put("isPreset", p.isPreset)
                    put("isEnabled", p.isEnabled)
                    put("ruleCount", p.ruleCount)
                    put("lastUpdated", p.lastUpdated)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_PROVIDERS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving providers", e)
        }
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        val updated = _providers.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        _providers.value = updated
        saveProviders()
        reloadBlockedDomainsFromCache()
    }

    fun addCustomProvider(name: String, url: String) {
        val newProvider = AdBlockProvider(
            id = "custom_${UUID.randomUUID()}",
            name = name.ifBlank { "Custom Filter" },
            url = url.trim(),
            isPreset = false,
            isEnabled = true
        )
        _providers.value = _providers.value + newProvider
        saveProviders()
        syncProvider(newProvider)
    }

    fun removeProvider(id: String) {
        val provider = _providers.value.find { it.id == id } ?: return
        if (provider.isPreset) return

        val cacheFile = File(context.cacheDir, "adblock_${provider.id}.txt")
        if (cacheFile.exists()) cacheFile.delete()

        _providers.value = _providers.value.filter { it.id != id }
        saveProviders()
        reloadBlockedDomainsFromCache()
    }

    fun syncAllProviders() {
        scope.launch {
            _isSyncing.value = true
            for (p in _providers.value) {
                if (p.isEnabled) {
                    syncProviderInternal(p)
                }
            }
            reloadBlockedDomainsFromCache()
            _isSyncing.value = false
        }
    }

    fun syncProvider(provider: AdBlockProvider) {
        scope.launch {
            _isSyncing.value = true
            syncProviderInternal(provider)
            reloadBlockedDomainsFromCache()
            _isSyncing.value = false
        }
    }

    private suspend fun syncProviderInternal(p: AdBlockProvider) {
        try {
            Log.i(TAG, "Fetching filter list for ${p.name}: ${p.url}")
            val text = withContext(Dispatchers.IO) {
                URL(p.url).readText()
            }
            val parsedDomains = parseFilterRules(text)
            val cacheFile = File(context.cacheDir, "adblock_${p.id}.txt")
            cacheFile.writeText(parsedDomains.joinToString("\n"))

            val updated = _providers.value.map {
                if (it.id == p.id) {
                    it.copy(ruleCount = parsedDomains.size, lastUpdated = System.currentTimeMillis())
                } else it
            }
            _providers.value = updated
            saveProviders()
            Log.i(TAG, "Synced ${p.name}: ${parsedDomains.size} domains parsed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync provider ${p.name}", e)
        }
    }

    private fun parseFilterRules(text: String): List<String> {
        val domains = mutableListOf<String>()
        val lines = text.lineSequence()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) continue

            if (line.startsWith("127.0.0.1") || line.startsWith("0.0.0.0")) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val host = parts[1].trim().lowercase()
                    if (host != "localhost" && host.contains(".")) {
                        domains.add(host)
                    }
                }
                continue
            }

            if (line.startsWith("||")) {
                val domain = line.removePrefix("||")
                    .takeWhile { it != '^' && it != '/' && it != ':' && it != '$' }
                    .lowercase()
                if (domain.contains(".")) {
                    domains.add(domain)
                }
                continue
            }

            if (!line.contains("/") && !line.contains(" ") && line.contains(".")) {
                domains.add(line.lowercase())
            }
        }
        return domains.distinct()
    }

    private fun loadCachedRules() {
        scope.launch {
            reloadBlockedDomainsFromCache()
        }
    }

    private fun reloadBlockedDomainsFromCache() {
        val newSet = mutableSetOf<String>()
        for (p in _providers.value) {
            if (!p.isEnabled) continue
            val cacheFile = File(context.cacheDir, "adblock_${p.id}.txt")
            if (cacheFile.exists()) {
                try {
                    cacheFile.forEachLine { line ->
                        if (line.isNotBlank()) newSet.add(line.trim().lowercase())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading cached rules for ${p.name}", e)
                }
            }
        }
        blockedDomains.clear()
        blockedDomains.addAll(newSet)
        Log.i(TAG, "Loaded ${blockedDomains.size} active blocked domains into memory")
    }

    /**
     * Clears the in-memory blocked-domains set to reclaim RAM under memory
     * pressure. The set is lazily rebuilt from the disk cache on next lookup.
     * Called from [BrowserViewModel.onCriticalMemory].
     */
    fun trimBlockedDomains() {
        blockedDomains.clear()
        Log.i(TAG, "trimBlockedDomains: in-memory set cleared")
    }

    /**
     * Cancels the background coroutine scope. Call from ViewModel.onCleared()
     * so the IO scope does not outlive the ViewModel.
     */
    fun shutdown() {
        scope.cancel()
        Log.i(TAG, "AdBlockManager shutdown")
    }

    fun isHostBlocked(host: String?): Boolean {
        if (!isMasterEnabled || host.isNullOrEmpty()) return false
        val cleanHost = host.lowercase().trim().removePrefix("www.")

        // Essential authentication & identity domains must never be blocked
        if (cleanHost == "accounts.google.com" ||
            cleanHost == "apis.google.com" ||
            cleanHost == "ssl.gstatic.com" ||
            cleanHost == "accounts.youtube.com" ||
            cleanHost == "appleid.apple.com" ||
            cleanHost == "login.microsoftonline.com" ||
            cleanHost == "login.live.com" ||
            cleanHost == "github.com"
        ) {
            return false
        }

        if (blockedDomains.contains(cleanHost)) return true

        val parts = cleanHost.split(".")
        if (parts.size > 2) {
            val rootDomain = parts.takeLast(2).joinToString(".")
            if (blockedDomains.contains(rootDomain)) return true
        }
        return false
    }

    fun getCosmeticAdBlockCss(): String {
        if (!isMasterEnabled) return ""
        return """
            [id*="google_ads"], [class*="google-auto-placed"], [id*="taboola"], [id*="outbrain"],
            [class*="ad-slot"], [class*="sponsored-post"], [src*="doubleclick.net"], [src*="googlesyndication.com"],
            iframe[src*="doubleclick"], .outbrain_widget, .taboola-container, .adsbygoogle
            { display: none !important; visibility: hidden !important; opacity: 0 !important; height: 0 !important; max-height: 0 !important; overflow: hidden !important; pointer-events: none !important; }
        """.trimIndent()
    }

    fun getStealthDefuserJs(): String {
        if (!isMasterEnabled) return ""
        return """
            (function() {
                if (window.__omniStealthDefuserActive) return;
                window.__omniStealthDefuserActive = true;

                /* 1. Stub common ad network globals expected by site scripts */
                window.google_ad_client = true;
                window.google_ad_status = 1;
                window.canRunAds = true;
                window.isAdBlockActive = false;
                window.fuckAdBlock = false;
                window.BlockAdBlock = false;
                window.SniffAdBlock = false;
                window.adBlockDetected = false;

                if (!window.googletag) {
                    var noop = function() {};
                    window.googletag = {
                        cmd: [],
                        flag: {},
                        display: noop,
                        openConsole: noop,
                        enableServices: noop,
                        pubads: function() {
                            return {
                                addEventListener: noop,
                                clear: noop,
                                clearTargeting: noop,
                                collapseEmptyDivs: noop,
                                definePassback: function() { return { display: noop, set: noop }; },
                                enableLazyLoad: noop,
                                enableSingleRequest: noop,
                                getSlots: function() { return []; },
                                refresh: noop,
                                set: noop,
                                setTargeting: noop
                            };
                        }
                    };
                }

                if (!window.ga) window.ga = function() {};
                if (!window._gaq) window._gaq = [];

                /* 2. Define traps for window properties probed by FuckAdBlock / BlockAdBlock / Admiral / CMPs */
                try {
                    var trueProps = ['canRunAds', 'google_ad_client', 'google_ad_status', 'pubads'];
                    var falseProps = ['isAdBlockActive', 'fuckAdBlock', 'BlockAdBlock', 'SniffAdBlock', 'adBlockDetected'];

                    trueProps.forEach(function(p) {
                        try {
                            Object.defineProperty(window, p, {
                                get: function() { return true; },
                                set: function() {},
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    });

                    falseProps.forEach(function(p) {
                        try {
                            Object.defineProperty(window, p, {
                                get: function() { return false; },
                                set: function() {},
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    });
                } catch(e) {}

                /* 3. OffsetHeight & ClientHeight Proxy Spoofing for Bait Elements */
                try {
                    var origOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
                    if (origOffsetHeight && origOffsetHeight.get) {
                        Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
                            get: function() {
                                var h = origOffsetHeight.get.call(this);
                                if (h === 0 && (this.className || this.id)) {
                                    var cls = (this.className || '').toString().toLowerCase();
                                    var id = (this.id || '').toString().toLowerCase();
                                    if (cls.indexOf('ad') !== -1 || cls.indexOf('banner') !== -1 || cls.indexOf('sponsor') !== -1 ||
                                        id.indexOf('ad') !== -1 || id.indexOf('banner') !== -1 || id.indexOf('sponsor') !== -1) {
                                        return 250;
                                    }
                                }
                                return h;
                            },
                            configurable: true,
                            enumerable: true
                        });
                    }
                } catch(e) {}

                /* 4. MutationObserver Anti-Adblock Overlay Defuser */
                try {
                    var removeAntiAdblockOverlays = function() {
                        var selectors = [
                            '.fc-ab-root', '.tp-backdrop', '.tp-modal', '#ab-overlay',
                            '.adblock-overlay', '.adblock-modal', '[class*="anti-adblock"]',
                            '[id*="anti-adblock"]', '[class*="adblock-warning"]',
                            '#adblock-notice', '.adblock-notice'
                        ];
                        selectors.forEach(function(sel) {
                            var elems = document.querySelectorAll(sel);
                            for (var i = 0; i < elems.length; i++) {
                                var el = elems[i];
                                if (el) {
                                    el.style.setProperty('display', 'none', 'important');
                                    el.style.setProperty('visibility', 'hidden', 'important');
                                }
                            }
                        });
                        if (document.body) {
                            document.body.style.setProperty('overflow', 'auto', 'important');
                        }
                        if (document.documentElement) {
                            document.documentElement.style.setProperty('overflow', 'auto', 'important');
                        }
                    };

                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', removeAntiAdblockOverlays);
                    } else {
                        removeAntiAdblockOverlays();
                    }

                    var observer = new MutationObserver(function() {
                        removeAntiAdblockOverlays();
                    });
                    if (document.documentElement) {
                        observer.observe(document.documentElement, { childList: true, subtree: true });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
