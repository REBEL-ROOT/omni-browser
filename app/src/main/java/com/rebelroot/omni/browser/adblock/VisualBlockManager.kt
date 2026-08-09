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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VisualBlockRule(
    val id: String,
    val domain: String,
    val selector: String,
    val textPreview: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isEnabled: Boolean = true
)

class VisualBlockManager(private val context: Context) {

    companion object {
        private const val TAG = "VisualBlockManager"
        private const val PREF_NAME = "visual_block_prefs"
        private const val KEY_RULES_JSON = "visual_block_rules_json"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<VisualBlockRule>>(emptyList())
    val rules: StateFlow<List<VisualBlockRule>> = _rules.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        val savedJson = prefs.getString(KEY_RULES_JSON, null)
        val list = mutableListOf<VisualBlockRule>()
        if (!savedJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        VisualBlockRule(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            domain = obj.optString("domain", "*"),
                            selector = obj.optString("selector", ""),
                            textPreview = obj.optString("textPreview", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing visual block rules", e)
            }
        }
        _rules.value = list
    }

    private fun saveRules() {
        try {
            val array = JSONArray()
            for (rule in _rules.value) {
                val obj = JSONObject().apply {
                    put("id", rule.id)
                    put("domain", rule.domain)
                    put("selector", rule.selector)
                    put("textPreview", rule.textPreview)
                    put("timestamp", rule.timestamp)
                    put("isEnabled", rule.isEnabled)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_RULES_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving visual block rules", e)
        }
    }

    fun addRule(domain: String, selector: String, textPreview: String): VisualBlockRule {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: "*"
        val newRule = VisualBlockRule(
            id = UUID.randomUUID().toString(),
            domain = cleanDomain,
            selector = selector.trim(),
            textPreview = textPreview.trim().take(80),
            timestamp = System.currentTimeMillis(),
            isEnabled = true
        )
        // Avoid duplicate selector for the same domain
        val filtered = _rules.value.filterNot { it.domain.equals(cleanDomain, ignoreCase = true) && it.selector == newRule.selector }
        _rules.value = filtered + newRule
        saveRules()
        Log.i(TAG, "Added visual block rule for $cleanDomain: $selector")
        return newRule
    }

    fun removeRule(id: String) {
        _rules.value = _rules.value.filterNot { it.id == id }
        saveRules()
    }

    fun toggleRule(id: String, enabled: Boolean) {
        _rules.value = _rules.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveRules()
    }

    fun clearRulesForDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull() ?: "*"
        _rules.value = _rules.value.filterNot { rule ->
            val rRaw = rule.domain.trim().lowercase()
            val rDomain = rRaw.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull() ?: "*"
            val isGlobalTarget = cleanDomain == "*" || cleanDomain == "all sites (*)" || cleanDomain == "about:blank"
            val isRuleGlobal = rDomain == "*" || rDomain == "all sites (*)" || rDomain == "about:blank"
            rDomain == cleanDomain || (isGlobalTarget && isRuleGlobal)
        }
        saveRules()
    }

    fun clearAllRules() {
        _rules.value = emptyList()
        saveRules()
    }

    fun getRulesForDomain(domain: String): List<VisualBlockRule> {
        val cleanDomain = domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: "*"
        return _rules.value.filter { rule ->
            val rRaw = rule.domain.trim().lowercase()
            val ruleDomain = rRaw.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull() ?: "*"
            val isGlobal = ruleDomain == "*" || ruleDomain == "about:blank" || ruleDomain.isBlank() || ruleDomain == "all sites (*)"
            (isGlobal || ruleDomain == cleanDomain || cleanDomain.endsWith(".$ruleDomain") || ruleDomain.endsWith(".$cleanDomain")) && rule.isEnabled
        }
    }

    fun buildCosmeticCssForDomain(domain: String): String {
        val activeRules = getRulesForDomain(domain)
        if (activeRules.isEmpty()) return ""
        val selectors = activeRules.mapNotNull { rule ->
            var sel = rule.selector.trim()
            if (sel.isBlank()) return@mapNotNull null
            // Convert any legacy CSS.escape backslashed IDs (e.g., #\34 9219508) to clean [id="..."] selectors
            if (sel.contains("\\")) {
                sel = sel.replace(Regex("""#\\(\d+)\s*""")) { match ->
                    val num = match.groupValues[1]
                    "[id=\"$num\"]"
                }.replace("\\", "")
            }
            sel
        }
        if (selectors.isEmpty()) return ""
        val joined = selectors.joinToString(",\n")
        return "$joined { display: none !important; visibility: hidden !important; opacity: 0 !important; height: 0px !important; max-height: 0px !important; width: 0px !important; max-width: 0px !important; margin: 0px !important; padding: 0px !important; overflow: hidden !important; pointer-events: none !important; }"
    }

    /**
     * Returns the JavaScript snippet that turns on visual selection mode in WebView.
     * @param bottomOffsetPx Extra bottom offset (in CSS pixels) to clear the browser chrome.
     *                       Defaults to 96 to stay above a typical bottom nav bar.
     */
    fun getInspectorJsScript(bottomOffsetPx: Int = 96): String {
        return """
            javascript:(function() {
                if (window.__omniVisualBlockActive) {
                    if (window.__omniVisualBlockCleanup) window.__omniVisualBlockCleanup();
                    return;
                }
                window.__omniVisualBlockActive = true;

                let selectedEl = null;
                let hoverOverlay = null;
                let actionToolbar = null;

                function createOverlay() {
                    hoverOverlay = document.createElement('div');
                    hoverOverlay.id = 'omni-visual-block-overlay';
                    hoverOverlay.style.cssText = 'position: absolute !important; z-index: 2147483646 !important; background: rgba(255, 59, 48, 0.25) !important; border: 2px solid #FF3B30 !important; pointer-events: none !important; transition: all 0.1s ease !important; box-sizing: border-box !important; border-radius: 4px !important; display: none !important;';
                    document.body.appendChild(hoverOverlay);
                }

                function createToolbar() {
                    actionToolbar = document.createElement('div');
                    actionToolbar.id = 'omni-visual-block-toolbar';
                    actionToolbar.style.cssText = 'position: fixed !important; bottom: ${bottomOffsetPx}px !important; left: 50% !important; transform: translateX(-50%) !important; z-index: 2147483647 !important; background: #1C1C1E !important; color: #FFFFFF !important; font-family: -apple-system, BlinkMacSystemFont, Roboto, sans-serif !important; font-size: 12px !important; font-weight: 600 !important; padding: 8px 12px !important; border-radius: 30px !important; display: flex !important; align-items: center !important; gap: 8px !important; box-shadow: 0 8px 32px rgba(0,0,0,0.5) !important; border: 1px solid rgba(255,255,255,0.18) !important; backdrop-filter: blur(16px) !important; width: auto !important; max-width: 92% !important; box-sizing: border-box !important;';
                    
                    actionToolbar.innerHTML = '<span id="omni-vb-text" style="max-width: 70px !important; overflow: hidden !important; text-overflow: ellipsis !important; white-space: nowrap !important; opacity: 0.85 !important; flex-shrink: 1 !important;">Tap element</span>' +
                        '<button id="omni-vb-parent" style="background: #2C2C2E !important; color: #FFF !important; border: none !important; padding: 6px 10px !important; border-radius: 16px !important; font-weight: 600 !important; font-size: 11px !important; cursor: pointer !important; flex-shrink: 0 !important; white-space: nowrap !important;">⬆ Parent</button>' +
                        '<button id="omni-vb-confirm" style="background: #FF3B30 !important; color: #FFF !important; border: none !important; padding: 6px 12px !important; border-radius: 16px !important; font-weight: 700 !important; font-size: 11px !important; cursor: pointer !important; flex-shrink: 0 !important; white-space: nowrap !important;">✓ Block</button>' +
                        '<button id="omni-vb-settings" style="background: transparent !important; color: #8E8E93 !important; border: none !important; font-size: 14px !important; cursor: pointer !important; padding: 0 4px !important; flex-shrink: 0 !important;">⚙</button>' +
                        '<button id="omni-vb-cancel" style="background: transparent !important; color: #8E8E93 !important; border: none !important; font-size: 14px !important; cursor: pointer !important; padding: 0 4px !important; flex-shrink: 0 !important;">✕</button>';
                    
                    document.body.appendChild(actionToolbar);

                    document.getElementById('omni-vb-settings').addEventListener('click', function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        cleanup();
                        alert('OMNI_VISUAL_BLOCK_SETTINGS:true');
                    });

                    document.getElementById('omni-vb-parent').addEventListener('click', function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        if (selectedEl && selectedEl.parentElement && selectedEl.parentElement !== document.body && selectedEl.parentElement !== document.documentElement) {
                            selectedEl = selectedEl.parentElement;
                            updateHighlight(selectedEl);
                        }
                    });

                    document.getElementById('omni-vb-confirm').addEventListener('click', function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        if (!selectedEl) return;
                        const selector = getUniqueSelector(selectedEl);
                        const previewText = (selectedEl.innerText || selectedEl.alt || selectedEl.title || selectedEl.tagName).trim().substring(0, 60);
                        
                        selectedEl.style.display = 'none';
                        selectedEl.setAttribute('data-omni-blocked', 'true');
                        
                        const domain = (window.location.hostname || '*').replace(/^www\./, '');
                        const payload = JSON.stringify({ selector: selector, preview: previewText, domain: domain });
                        cleanup();
                        alert('OMNI_VISUAL_BLOCK_ADD:' + payload);
                    });

                    document.getElementById('omni-vb-cancel').addEventListener('click', function(e) {
                        e.stopPropagation();
                        e.preventDefault();
                        cleanup();
                        alert('OMNI_VISUAL_BLOCK_CANCEL:true');
                    });
                }

                function updateHighlight(el) {
                    if (!el || !hoverOverlay) return;
                    const rect = el.getBoundingClientRect();
                    hoverOverlay.style.top = (rect.top + window.scrollY) + 'px';
                    hoverOverlay.style.left = (rect.left + window.scrollX) + 'px';
                    hoverOverlay.style.width = rect.width + 'px';
                    hoverOverlay.style.height = rect.height + 'px';
                    hoverOverlay.style.display = 'block';

                    const txtEl = document.getElementById('omni-vb-text');
                    if (txtEl) {
                        txtEl.textContent = '<' + el.tagName.toLowerCase() + '>' + (el.id ? '#' + el.id : '');
                    }
                }

                function getUniqueSelector(el) {
                    if (!el || el.nodeType !== 1) return '';
                    let path = [];
                    let curr = el;
                    while (curr && curr.nodeType === 1 && curr !== document.body && curr !== document.documentElement) {
                        let tag = curr.nodeName.toLowerCase();
                        if (curr.id) {
                            let cleanId = curr.id.replace(/"/g, '\\"');
                            path.unshift(tag + '[id="' + cleanId + '"]');
                            break;
                        } else {
                            let sibling = curr;
                            let nth = 1;
                            while (sibling = sibling.previousElementSibling) {
                                if (sibling.nodeName.toLowerCase() === tag) nth++;
                            }
                            if (nth > 1) tag += ':nth-of-type(' + nth + ')';
                        }
                        path.unshift(tag);
                        curr = curr.parentNode;
                    }
                    return path.join(' > ');
                }

                function handlePointer(e) {
                    if (actionToolbar && actionToolbar.contains(e.target)) return;
                    e.preventDefault();
                    e.stopPropagation();
                    const target = document.elementFromPoint(e.clientX, e.clientY);
                    if (target && target !== hoverOverlay && target !== actionToolbar) {
                        selectedEl = target;
                        updateHighlight(target);
                    }
                }

                function cleanup() {
                    window.__omniVisualBlockActive = false;
                    document.removeEventListener('click', handlePointer, true);
                    document.removeEventListener('touchstart', handlePointer, true);
                    if (hoverOverlay) hoverOverlay.remove();
                    if (actionToolbar) actionToolbar.remove();
                }

                window.__omniVisualBlockCleanup = cleanup;
                createOverlay();
                createToolbar();
                document.addEventListener('click', handlePointer, true);
                document.addEventListener('touchstart', handlePointer, true);
            })();
        """.trimIndent()
    }
}
