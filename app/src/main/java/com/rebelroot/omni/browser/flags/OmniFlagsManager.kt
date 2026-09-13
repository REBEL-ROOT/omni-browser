/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser.flags

import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages serialization, deserialization, and YAML compilation for engine flags and custom preferences.
 */
object OmniFlagsManager {

    /**
     * Serializes a flag state map (flagId -> isEnabled) into a JSON string for DataStore persistence.
     */
    fun encodeFlagsState(state: Map<String, Boolean>): String {
        val json = JSONObject()
        state.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    /**
     * Deserializes a JSON string into a flag state map, populating missing flags with their defaults.
     */
    fun decodeFlagsState(jsonStr: String?): Map<String, Boolean> {
        val resultMap = OmniFlagsRegistry.getDefaultStateMap().toMutableMap()
        if (jsonStr.isNullOrBlank()) return resultMap

        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                resultMap[key] = json.optBoolean(key, resultMap[key] ?: false)
            }
        } catch (_: Exception) {}

        return resultMap
    }

    /**
     * Serializes user custom preferences into a JSON string.
     */
    fun encodeCustomPrefs(prefs: List<CustomPref>): String {
        val array = JSONArray()
        prefs.forEach { pref ->
            val obj = JSONObject()
            obj.put("key", pref.key)
            obj.put("type", pref.type.name)
            obj.put("value", pref.value)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Deserializes a JSON string into a list of CustomPref objects.
     */
    fun decodeCustomPrefs(jsonStr: String?): List<CustomPref> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<CustomPref>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val key = obj.getString("key")
                val typeStr = obj.optString("type", PrefType.STRING.name)
                val type = runCatching { PrefType.valueOf(typeStr) }.getOrDefault(PrefType.STRING)
                val value = obj.getString("value")
                list.add(CustomPref(key = key, type = type, value = value))
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Evaluates all active flags and custom preferences, producing a consolidated map of engine preferences.
     */
    fun getComputedPrefs(
        flagsState: Map<String, Boolean>,
        customPrefs: List<CustomPref>
    ): Map<String, Any> {
        val computed = LinkedHashMap<String, Any>()

        // 1. Process curated flags
        for (flag in OmniFlagsRegistry.ALL_FLAGS) {
            val isEnabled = flagsState[flag.id] ?: flag.defaultEnabled
            if (isEnabled) {
                computed.putAll(flag.enginePrefs)
            }
        }

        // 2. Process custom preferences (can override curated flags if explicitly defined)
        for (custom in customPrefs) {
            val parsedValue: Any = when (custom.type) {
                PrefType.BOOLEAN -> custom.value.equals("true", ignoreCase = true)
                PrefType.INT -> custom.value.trim().toIntOrNull() ?: custom.value
                PrefType.STRING -> custom.value
            }
            computed[custom.key] = parsedValue
        }

        return computed
    }

    /**
     * Formats a map of preferences into YAML lines with two-space indentation.
     */
    fun formatPrefsToYaml(prefs: Map<String, Any>): String {
        val sb = StringBuilder()
        for ((key, value) in prefs) {
            val formattedValue = when (value) {
                is Boolean -> if (value) "true" else "false"
                is Number -> value.toString()
                is String -> "\"${value.replace("\"", "\\\"")}\""
                else -> "\"$value\""
            }
            sb.append("  ").append(key).append(": ").append(formattedValue).append("\n")
        }
        return sb.toString()
    }
}
