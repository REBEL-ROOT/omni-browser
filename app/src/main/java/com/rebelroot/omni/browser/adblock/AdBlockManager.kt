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

        private val DOMAIN_REGEX = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        private val IPV4_REGEX = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

        private val IMMUNE_ROOT_DOMAINS = setOf(
            "google.com", "bing.com", "duckduckgo.com", "yahoo.com", "baidu.com", "yandex.com", "yandex.ru",
            "ecosia.org", "startpage.com", "brave.com", "kagi.com", "qwant.com",
            "wikipedia.org", "wikimedia.org", "archive.org",
            "youtube.com", "youtu.be", "spotify.com", "amazon.com", "apple.com", "icloud.com",
            "microsoft.com", "live.com", "office.com", "outlook.com", "github.com", "gitlab.com",
            "reddit.com", "twitter.com", "x.com", "facebook.com", "instagram.com", "threads.net", "whatsapp.com",
            "linkedin.com", "netflix.com", "twitch.tv", "pinterest.com", "cloudflare.com", "mozilla.org", "android.com"
        )

        private val SEARCH_SHOPPING_REGIONAL_REGEX = Regex("^(?:www\\.)?(google|amazon|yahoo|yandex)\\.[a-z]{2,3}(?:\\.[a-z]{2})?$")

        fun isImmuneDomain(host: String?): Boolean {
            if (host.isNullOrBlank()) return false
            val clean = host.lowercase().trim().removePrefix("www.")
            if (IMMUNE_ROOT_DOMAINS.contains(clean)) return true
            if (SEARCH_SHOPPING_REGIONAL_REGEX.matches(clean)) return true
            return false
        }

        fun isValidDomainCandidate(candidate: String): Boolean {
            if (candidate.length in 4..253 && (DOMAIN_REGEX.matches(candidate) || (IPV4_REGEX.matches(candidate) && candidate != "0.0.0.0" && candidate != "127.0.0.1"))) {
                return true
            }
            return false
        }

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

    internal fun parseFilterRules(text: String): List<String> {
        val domains = mutableListOf<String>()
        val lines = text.lineSequence()
        for (rawLine in lines) {
            val line = rawLine.trim()
            // Skip empty lines, comments, metadata, whitelists, and element hiding rules
            if (line.isBlank() ||
                line.startsWith("!") ||
                line.startsWith("#") ||
                line.startsWith("[") ||
                line.startsWith("@@") ||
                line.startsWith("$") ||
                line.contains("##") ||
                line.contains("#?#") ||
                line.contains("#@#") ||
                line.contains("#$#") ||
                line.contains("\$badfilter")
            ) {
                continue
            }

            // Hosts file format: 127.0.0.1 domain or 0.0.0.0 domain
            if (line.startsWith("127.0.0.1") || line.startsWith("0.0.0.0")) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val candidate = parts[1].trim().lowercase().removePrefix("www.")
                    if (candidate != "localhost" &&
                        candidate != "broadcasthost" &&
                        candidate != "local" &&
                        isValidDomainCandidate(candidate) &&
                        !isImmuneDomain(candidate)
                    ) {
                        domains.add(candidate)
                    }
                }
                continue
            }

            // Adblock Plus domain blocking format: ||domain.com^ or ||domain.com^$options
            if (line.startsWith("||")) {
                val after = line.removePrefix("||")
                val caret = after.indexOf('^')
                if (caret != -1) {
                    val rest = after.substring(caret + 1)
                    // In a full domain block, after '^' must be end-of-string or '$options'
                    // Path characters (like ||google.com^*/ads) indicate a URL path filter, not whole domain block
                    if (rest.isEmpty() || rest.startsWith("$")) {
                        // Exclude modifier options that don't represent whole-domain network drops
                        if (rest.startsWith("$")) {
                            val options = rest.substring(1).lowercase().split(",")
                            val isExcluded = options.any { opt ->
                                opt.startsWith("domain=") ||
                                opt.startsWith("from=") ||
                                opt.startsWith("removeparam") ||
                                opt.startsWith("csp") ||
                                opt.startsWith("redirect") ||
                                opt.startsWith("replace") ||
                                opt.startsWith("header") ||
                                opt == "badfilter" ||
                                opt == "generichide" ||
                                opt == "ghide"
                            }
                            if (isExcluded) continue
                        }

                        val candidate = after.substring(0, caret).trim().lowercase().removePrefix("www.")
                        if (isValidDomainCandidate(candidate) && !isImmuneDomain(candidate)) {
                            domains.add(candidate)
                        }
                    }
                }
                continue
            }

            // Plain domain entry (one per line, e.g. custom or seed lists)
            val candidate = line.lowercase().removePrefix("www.")
            if (isValidDomainCandidate(candidate) && !isImmuneDomain(candidate)) {
                domains.add(candidate)
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

        // If cache is empty (fresh install or cache cleared), load pre-bundled seed rules from assets
        if (newSet.isEmpty()) {
            loadSeedDomainsFromAssets(newSet)
        }

        blockedDomains.clear()
        blockedDomains.addAll(newSet)
        Log.i(TAG, "Loaded ${blockedDomains.size} active blocked domains into memory")

        // If rules were empty and only seed was loaded, trigger background sync for full lists
        checkAndTriggerBackgroundSync()
    }

    private fun loadSeedDomainsFromAssets(outSet: MutableSet<String>) {
        try {
            context.assets.open("adblock_seed.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                        outSet.add(trimmed)
                    }
                }
            }
            Log.i(TAG, "Loaded ${outSet.size} seed domains from assets/adblock_seed.txt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load seed domains from assets", e)
        }
    }

    private fun checkAndTriggerBackgroundSync() {
        val hasAnyCache = _providers.value.any { p ->
            p.isEnabled && File(context.cacheDir, "adblock_${p.id}.txt").exists()
        }
        if (!hasAnyCache && isMasterEnabled) {
            Log.i(TAG, "No cached adblock lists found on disk — auto-triggering background syncAllProviders()")
            syncAllProviders()
        }
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

        // Essential authentication, identity, and platform root domains must never be blocked
        if (isImmuneDomain(cleanHost) ||
            cleanHost == "accounts.google.com" ||
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

        // Hierarchical domain suffix check:
        // Checks cleanHost (e.g. sub.ads.exoclick.com), then ads.exoclick.com, then exoclick.com
        var current: String? = cleanHost
        while (!current.isNullOrEmpty() && current.contains('.')) {
            if (isImmuneDomain(current)) {
                // If a parent domain suffix is an immune domain (e.g. "google.com", "amazon.com", "spotify.com"),
                // break out to avoid false-positive root domain matching.
                break
            }
            if (blockedDomains.contains(current)) return true
            val dotIdx = current.indexOf('.')
            current = if (dotIdx != -1 && dotIdx + 1 < current.length) {
                current.substring(dotIdx + 1)
            } else null
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
