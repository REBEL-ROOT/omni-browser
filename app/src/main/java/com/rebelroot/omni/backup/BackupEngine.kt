/*
 * Omni Browser - Backup & Restore engine
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Builds a single JSON backup file containing any user-selected combination of
 * settings, bookmarks, history, saved passwords and dev notes, and restores
 * those sections back into the live app state.
 *
 * Security: secrets (app-lock PIN, master-password vault keys, SQLCipher keys,
 * Firefox Sync OAuth tokens, device identity private keys) are NEVER included.
 * Saved passwords ARE included when the user explicitly opts in, and are written
 * as plaintext — the UI warns about this.
 */

package com.rebelroot.omni.backup

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rebelroot.omni.bookmarks.export.exportNetscapeBookmarkHtml
import com.rebelroot.omni.bookmarks.importexport.DuplicatePolicy
import com.rebelroot.omni.bookmarks.importexport.importBookmarks
import com.rebelroot.omni.bookmarks.parser.parseNetscapeBookmarkHtml
import com.rebelroot.omni.bookmarks.storage.loadBookmarks
import com.rebelroot.omni.bookmarks.storage.saveBookmarks
import com.rebelroot.omni.browser.BackupImportResult
import com.rebelroot.omni.browser.BrowserViewModel
import com.rebelroot.omni.browser.dataStore
import com.rebelroot.omni.browser.loadHistory
import com.rebelroot.omni.browser.refreshBookmarks
import com.rebelroot.omni.tools.passwords.PasswordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupEngine {

    const val SCHEMA_VERSION = 2
    const val APP_TAG = "OmniBrowser"
    private const val TAG = "BackupEngine"

    private const val HISTORY_FILE = "browser_history.json"
    private const val DEV_NOTES_FILE = "dev_notes.json"
    private const val TABS_FILE = "browser_tabs.json"
    private const val TAB_GROUPS_FILE = "browser_tab_groups.json"

    /** SharedPreferences files holding user-facing settings (never secrets). */
    private val SETTINGS_PREF_FILES = listOf(
        "omni_prefs",
        "adblock_prefs",
        "visual_block_prefs",
        "omni_user_agent_prefs",
        "omni_manga_preferences"
    )

    /**
     * JSON files under filesDir holding user-facing settings. Speed-dial
     * shortcuts live in [SHORTCUTS_FILE] and are exported through
     * [BackupSection.SPEED_DIAL] instead; restore is driven by the file's own
     * keys, so older backups that stored shortcuts here still come back.
     */
    private val SETTINGS_JSON_FILES = listOf(
        "browser_site_permissions.json"
    )

    private const val SHORTCUTS_FILE = "browser_shortcuts.json"

    // ── Export ────────────────────────────────────────────────────────────────

    suspend fun build(
        context: Context,
        viewModel: BrowserViewModel,
        sections: Set<BackupSection>
    ): String {
        // Snapshot Compose-observed state on the calling thread before any IO.
        val historySnapshot = viewModel.historyList.toList()
        val notesSnapshot = viewModel.devNotes.toList()
        val savedPasswordsSnapshot = viewModel.savedPasswords.toList()
        val shortcutsSnapshot = viewModel.shortcutsList.toList()
        val vault = viewModel.passwordVaultManager

        return withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("app", APP_TAG)
            root.put("schema_version", SCHEMA_VERSION)
            root.put("exported_at_ms", System.currentTimeMillis())
            root.put("sections", JSONArray(sections.map { it.key }.sorted()))

            if (BackupSection.SETTINGS in sections) root.put("settings", buildSettings(context))
            if (BackupSection.BOOKMARKS in sections) root.put("bookmarks", buildBookmarks(context))
            if (BackupSection.SPEED_DIAL in sections) root.put("speed_dial", buildSpeedDial(shortcutsSnapshot))
            if (BackupSection.HISTORY in sections) root.put("history", buildHistory(historySnapshot))
            if (BackupSection.TABS in sections) root.put("tabs", buildTabs(context))
            if (BackupSection.PASSWORDS in sections) root.put("passwords", buildPasswords(vault, savedPasswordsSnapshot))
            if (BackupSection.NOTES in sections) root.put("notes", buildNotes(notesSnapshot))

            root.toString(2)
        }
    }

    private suspend fun buildSettings(context: Context): JSONObject {
        val prefsMap = context.dataStore.data.first()
        val dsArray = JSONArray()
        for ((k, v) in prefsMap.asMap()) {
            val entry = JSONObject()
            entry.put("key", k.name)
            when (v) {
                is Boolean -> { entry.put("type", "bool"); entry.put("value", v) }
                is Int -> { entry.put("type", "int"); entry.put("value", v) }
                is Float -> { entry.put("type", "float"); entry.put("value", v.toDouble()) }
                is Long -> { entry.put("type", "long"); entry.put("value", v) }
                is Double -> { entry.put("type", "double"); entry.put("value", v) }
                is String -> { entry.put("type", "string"); entry.put("value", v) }
                is Set<*> -> {
                    entry.put("type", "string_set")
                    val arr = JSONArray()
                    v.forEach { item -> if (item != null) arr.put(item.toString()) }
                    entry.put("value", arr)
                }
                else -> continue
            }
            dsArray.put(entry)
        }
        val dsObj = JSONObject().put("omni_settings", dsArray)

        val prefsObj = JSONObject()
        for (name in SETTINGS_PREF_FILES) {
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            prefsObj.put(name, dumpPrefMap(all))
        }

        val filesObj = JSONObject()
        for (name in SETTINGS_JSON_FILES) {
            val file = File(context.filesDir, name)
            if (file.exists()) filesObj.put(name, file.readText())
        }

        return JSONObject().apply {
            put("datastore", dsObj)
            put("shared_prefs", prefsObj)
            put("files", filesObj)
        }
    }

    /** Serializes a `Map<String, ?>` from SharedPreferences into a typed JSON object. */
    private fun dumpPrefMap(map: Map<String, *>): JSONObject {
        val out = JSONObject()
        for ((key, v) in map) {
            if (v == null) continue
            val entry = JSONObject()
            when (v) {
                is Boolean -> { entry.put("type", "bool"); entry.put("value", v) }
                is Int -> { entry.put("type", "int"); entry.put("value", v) }
                is Float -> { entry.put("type", "float"); entry.put("value", v.toDouble()) }
                is Long -> { entry.put("type", "long"); entry.put("value", v) }
                is Double -> { entry.put("type", "double"); entry.put("value", v) }
                is String -> { entry.put("type", "string"); entry.put("value", v) }
                is Set<*> -> {
                    entry.put("type", "string_set")
                    val arr = JSONArray()
                    v.forEach { item -> if (item != null) arr.put(item.toString()) }
                    entry.put("value", arr)
                }
                else -> continue
            }
            out.put(key, entry)
        }
        return out
    }

    private fun buildBookmarks(context: Context): JSONObject {
        val collection = loadBookmarks(context)
        val html = exportNetscapeBookmarkHtml(collection, title = "Omni Bookmarks")
        return JSONObject().apply {
            put("format", "netscape_html")
            put("count", collection.bookmarkCount())
            put("html", html)
        }
    }

    /** Serializes the speed-dial shortcut tiles (including feature/permanent flags). */
    private fun buildSpeedDial(shortcuts: List<com.rebelroot.omni.browser.HomeShortcut>): JSONArray {
        val arr = JSONArray()
        shortcuts
            .filter { !it.url.isBlank() && it.url != "about:blank" && !it.url.contains("about:blank") }
            .forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("url", s.url)
                    put("isFeature", s.isFeature)
                    put("isPermanent", s.isPermanent)
                })
            }
        return arr
    }

    private fun buildHistory(history: List<com.rebelroot.omni.browser.HistoryEntry>): JSONArray {
        val arr = JSONArray()
        history.forEach { h ->
            arr.put(JSONObject().apply {
                put("title", h.title)
                put("url", h.url)
                put("timestamp", h.timestamp)
            })
        }
        return arr
    }

    private suspend fun buildPasswords(
        vault: com.rebelroot.omni.tools.passwords.PasswordVaultManager?,
        fallback: List<com.rebelroot.omni.browser.BrowserViewModel.SavedPassword>
    ): JSONArray {
        val arr = JSONArray()
        if (vault != null) {
            vault.exportAll().forEach { p ->
                arr.put(JSONObject().apply {
                    put("domain", p.domain)
                    put("username", p.username)
                    put("password", p.password)
                    put("label", p.label)
                    put("notes", p.notes)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                })
            }
        } else {
            // Vault locked / not initialized: fall back to the in-memory list.
            fallback.forEach { p ->
                arr.put(JSONObject().apply {
                    put("domain", p.domain)
                    put("username", p.username)
                    put("password", p.password)
                    put("createdAt", p.timestamp)
                    put("updatedAt", p.timestamp)
                })
            }
        }
        return arr
    }

    private fun buildNotes(notes: List<com.rebelroot.omni.browser.BrowserViewModel.DevNote>): JSONArray {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("type", n.type)
                put("timestamp", n.timestamp)
            })
        }
        return arr
    }

    /** Serializes the on-disk open-tab session and tab-group files. */
    private fun buildTabs(context: Context): JSONObject {
        val obj = JSONObject()
        File(context.filesDir, TABS_FILE).takeIf { it.exists() }?.let { obj.put(TABS_FILE, it.readText()) }
        File(context.filesDir, TAB_GROUPS_FILE).takeIf { it.exists() }?.let { obj.put(TAB_GROUPS_FILE, it.readText()) }
        return obj
    }

    // ── Inspect ───────────────────────────────────────────────────────────────

    /** Parses [jsonText] and reports which sections it contains. Null if invalid. */
    fun inspect(jsonText: String): BackupInspection? {
        val root = try { JSONObject(jsonText) } catch (e: Exception) { return null }
        val app = root.optString("app", "")
        if (app.isNotEmpty() && app != APP_TAG) return null
        val version = root.optInt("schema_version", 1)

        val available = mutableListOf<BackupSection>()
        val counts = mutableMapOf<BackupSection, Int>()

        // Settings: schema 1 kept datastore/shared_prefs at the root.
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null || root.has("datastore") || root.has("shared_prefs")) {
            val s = settingsObj ?: root
            val n = (s.optJSONObject("datastore")?.optJSONArray("omni_settings")?.length() ?: 0) +
                (s.optJSONObject("shared_prefs")?.let { prefs -> prefs.keys().asSequence().count() } ?: 0)
            available += BackupSection.SETTINGS
            counts[BackupSection.SETTINGS] = n
        }
        root.optJSONObject("bookmarks")?.let {
            available += BackupSection.BOOKMARKS
            counts[BackupSection.BOOKMARKS] = it.optInt("count", 0)
        }
        root.optJSONArray("speed_dial")?.let {
            available += BackupSection.SPEED_DIAL
            counts[BackupSection.SPEED_DIAL] = it.length()
        }
        root.optJSONArray("history")?.let {
            available += BackupSection.HISTORY
            counts[BackupSection.HISTORY] = it.length()
        }
        root.optJSONObject("tabs")?.let {
            available += BackupSection.TABS
            counts[BackupSection.TABS] = it.keys().asSequence().count()
        }
        root.optJSONArray("passwords")?.let {
            available += BackupSection.PASSWORDS
            counts[BackupSection.PASSWORDS] = it.length()
        }
        root.optJSONArray("notes")?.let {
            available += BackupSection.NOTES
            counts[BackupSection.NOTES] = it.length()
        }

        return BackupInspection(schemaVersion = version, available = available, counts = counts)
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    suspend fun restore(
        context: Context,
        viewModel: BrowserViewModel,
        jsonText: String,
        sections: Set<BackupSection>
    ): BackupImportResult = withContext(Dispatchers.IO) {
        val root = try { JSONObject(jsonText) } catch (e: Exception) {
            return@withContext BackupImportResult.InvalidFile
        }
        val app = root.optString("app", "")
        if (app.isNotEmpty() && app != APP_TAG) return@withContext BackupImportResult.InvalidFile
        if (root.optInt("schema_version", 1) > SCHEMA_VERSION) {
            return@withContext BackupImportResult.InvalidVersion
        }

        var restored = 0
        var skipped = 0

        // Each section is isolated: a failure in one (e.g. settings reload) must
        // not prevent the others (bookmarks, history, tabs…) from restoring.
        if (BackupSection.SETTINGS in sections) {
            try {
                val settingsObj = root.optJSONObject("settings") ?: root
                val (r, s) = restoreSettings(context, settingsObj)
                restored += r
                skipped += s
                reloadSettings(context, viewModel)
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: settings section failed", e)
                skipped++
            }
        }

        if (BackupSection.BOOKMARKS in sections) {
            try {
                restored += restoreBookmarks(context, viewModel, root.optJSONObject("bookmarks"))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: bookmarks section failed", e)
                skipped++
            }
        }

        if (BackupSection.SPEED_DIAL in sections) {
            try {
                restored += restoreSpeedDial(context, viewModel, root.optJSONArray("speed_dial"))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: speed dial section failed", e)
                skipped++
            }
        }

        if (BackupSection.HISTORY in sections) {
            try {
                restored += restoreHistory(context, viewModel, root.optJSONArray("history"))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: history section failed", e)
                skipped++
            }
        }

        if (BackupSection.TABS in sections) {
            try {
                restored += restoreTabs(context, viewModel, root.optJSONObject("tabs"))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: tabs section failed", e)
                skipped++
            }
        }

        if (BackupSection.PASSWORDS in sections) {
            try {
                val (r, s) = restorePasswords(viewModel, root.optJSONArray("passwords"))
                restored += r
                skipped += s
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: passwords section failed", e)
                skipped++
            }
        }

        if (BackupSection.NOTES in sections) {
            try {
                restored += restoreNotes(context, viewModel, root.optJSONArray("notes"))
            } catch (e: Exception) {
                Log.e(TAG, "Backup restore: notes section failed", e)
                skipped++
            }
        }

        BackupImportResult.Success(restored, skipped)
    }

    private suspend fun restoreSettings(context: Context, settingsObj: JSONObject): Pair<Int, Int> {
        var restored = 0
        var skipped = 0

        // DataStore
        val dsArray = settingsObj.optJSONObject("datastore")?.optJSONArray("omni_settings")
        if (dsArray != null) {
            context.dataStore.edit { prefs ->
                for (i in 0 until dsArray.length()) {
                    val item = dsArray.optJSONObject(i) ?: continue
                    val name = item.optString("key", "")
                    val type = item.optString("type", "")
                    val raw = item.opt("value")
                    if (name.isEmpty() || raw == null || raw == JSONObject.NULL) { skipped++; continue }
                    try {
                        when (type) {
                            "bool" -> prefs[booleanPreferencesKey(name)] = raw as Boolean
                            "int" -> prefs[intPreferencesKey(name)] = (raw as Number).toInt()
                            "float" -> prefs[floatPreferencesKey(name)] = (raw as Number).toFloat()
                            "long" -> prefs[longPreferencesKey(name)] = (raw as Number).toLong()
                            "double" -> prefs[doublePreferencesKey(name)] = (raw as Number).toDouble()
                            "string" -> prefs[stringPreferencesKey(name)] = raw as String
                            "string_set" -> prefs[stringSetPreferencesKey(name)] = jsonArrayToStringSet(raw as JSONArray)
                            else -> { skipped++; continue }
                        }
                        restored++
                    } catch (e: Exception) { skipped++ }
                }
            }
        }

        // SharedPreferences files
        settingsObj.optJSONObject("shared_prefs")?.let { prefsObj ->
            val keys = prefsObj.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                val entryMap = prefsObj.optJSONObject(fileName) ?: continue
                val (r, s) = restoreSharedPrefsFile(context, fileName, entryMap)
                restored += r
                skipped += s
            }
        }

        // Raw JSON settings files
        settingsObj.optJSONObject("files")?.let { filesObj ->
            val keys = filesObj.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                val content = filesObj.optString(fileName, "")
                if (content.isBlank()) continue
                try {
                    File(context.filesDir, fileName).writeText(content)
                    restored++
                } catch (e: Exception) { skipped++ }
            }
        }

        return restored to skipped
    }

    private fun restoreSharedPrefsFile(
        context: Context,
        fileName: String,
        entryMap: JSONObject
    ): Pair<Int, Int> {
        var restored = 0
        var skipped = 0
        val sp = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        val editor = sp.edit()
        val keys = entryMap.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = entryMap.optJSONObject(key) ?: continue
            val type = entry.optString("type", "")
            val raw = entry.opt("value")
            if (raw == null || raw == JSONObject.NULL) { skipped++; continue }
            try {
                when (type) {
                    "bool" -> editor.putBoolean(key, raw as Boolean)
                    "int" -> editor.putInt(key, (raw as Number).toInt())
                    "float" -> editor.putFloat(key, (raw as Number).toFloat())
                    "long" -> editor.putLong(key, (raw as Number).toLong())
                    "double" -> editor.putLong(key, java.lang.Double.doubleToRawLongBits((raw as Number).toDouble()))
                    "string" -> editor.putString(key, raw as String)
                    "string_set" -> editor.putStringSet(key, jsonArrayToStringSet(raw as JSONArray))
                    else -> { skipped++; continue }
                }
                restored++
            } catch (e: Exception) { skipped++ }
        }
        editor.apply()
        return restored to skipped
    }

    private fun jsonArrayToStringSet(arr: JSONArray): Set<String> {
        val set = mutableSetOf<String>()
        for (j in 0 until arr.length()) set.add(arr.getString(j))
        return set
    }

    private suspend fun reloadSettings(context: Context, viewModel: BrowserViewModel) {
        viewModel.reloadSettingsAfterImport(context)
        viewModel.loadShortcuts(context)
        viewModel.loadSitePermissions(context)
        runCatching { viewModel.adBlockManager.reload() }
        runCatching { viewModel.userAgentManager.reload() }
        runCatching { viewModel.visualBlockManager.reload() }
    }

    private suspend fun restoreBookmarks(
        context: Context,
        viewModel: BrowserViewModel,
        bookmarksObj: JSONObject?
    ): Int {
        val html = bookmarksObj?.optString("html", "")?.takeIf { it.isNotBlank() } ?: return 0
        val parsed = parseNetscapeBookmarkHtml(html)
        val live = loadBookmarks(context)
        val result = importBookmarks(source = parsed.collection, target = live, policy = DuplicatePolicy.SKIP)
        saveBookmarks(context, live)
        viewModel.refreshBookmarks(context)
        return result.addedBookmarks
    }

    /**
     * Writes the speed-dial shortcut list and reloads it. The on-disk shape
     * matches [BrowserViewModel.saveShortcuts]; [BrowserViewModel.loadShortcuts]
     * re-adds the built-in permanent/feature tiles if a restored file omits them.
     */
    private suspend fun restoreSpeedDial(
        context: Context,
        viewModel: BrowserViewModel,
        arr: JSONArray?
    ): Int {
        if (arr == null) return 0
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url", "")
            if (url.isBlank() || url == "about:blank" || url.contains("about:blank")) continue
            out.put(JSONObject().apply {
                put("id", o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() })
                put("title", o.optString("title", url))
                put("url", url)
                put("isFeature", o.optBoolean("isFeature", false))
                put("isPermanent", o.optBoolean("isPermanent", false))
            })
        }
        File(context.filesDir, SHORTCUTS_FILE).writeText(out.toString())
        viewModel.loadShortcuts(context)
        return out.length()
    }

    private suspend fun restoreHistory(
        context: Context,
        viewModel: BrowserViewModel,
        arr: JSONArray?
    ): Int {
        if (arr == null) return 0
        File(context.filesDir, HISTORY_FILE).writeText(arr.toString())
        viewModel.loadHistory(context)
        return arr.length()
    }

    /**
     * Writes the session tab and tab-group files, then reloads. `initTabs` only
     * populates an empty session, so restored tabs appear on the next cold start
     * rather than over the current live session.
     */
    private suspend fun restoreTabs(
        context: Context,
        viewModel: BrowserViewModel,
        tabsObj: JSONObject?
    ): Int {
        if (tabsObj == null) return 0
        var n = 0
        for (name in listOf(TABS_FILE, TAB_GROUPS_FILE)) {
            val content = tabsObj.optString(name, "")
            if (content.isNotBlank()) {
                File(context.filesDir, name).writeText(content)
                n++
            }
        }
        if (tabsObj.optString(TABS_FILE, "").isNotBlank()) {
            // Guard the restored session until the next cold start loads it:
            // saveTabs() is a no-op while this marker exists, so the current
            // live session can't overwrite what we just restored.
            File(context.filesDir, BrowserViewModel.PENDING_TAB_RESTORE_FILE).writeText("1")
        }
        return n
    }

    private suspend fun restorePasswords(viewModel: BrowserViewModel, arr: JSONArray?): Pair<Int, Int> {
        if (arr == null) return 0 to 0
        val vault = viewModel.passwordVaultManager
        if (vault == null) {
            // Vault is locked / not unlocked this session — nothing we can safely write.
            return 0 to arr.length()
        }
        val entries = mutableListOf<PasswordEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            entries += PasswordEntry(
                domain = o.optString("domain", ""),
                username = o.optString("username", ""),
                password = o.optString("password", ""),
                label = o.optString("label", ""),
                notes = o.optString("notes", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        vault.importAll(entries)
        return entries.size to 0
    }

    private suspend fun restoreNotes(
        context: Context,
        viewModel: BrowserViewModel,
        arr: JSONArray?
    ): Int {
        if (arr == null) return 0
        File(context.filesDir, DEV_NOTES_FILE).writeText(arr.toString())
        viewModel.loadDevNotes(context)
        return arr.length()
    }
}