package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG
import com.rebelroot.omni.tools.passwords.PasswordEntry
import com.rebelroot.omni.tools.passwords.PasswordVaultManager

fun BrowserViewModel.loadSavedPasswords(context: Context) {
    // Auto-open the vault on every cold start — no master password prompt needed.
    // The derived key is stored in EncryptedSharedPreferences by MasterPasswordManager;
    // if the vault hasn't been set up yet, loadSessionKey() returns null and we skip.
    if (passwordVaultManager == null) {
        val mgr = com.rebelroot.omni.tools.passwords.MasterPasswordManager(context)
        val keyBytes = mgr.loadSessionKey() ?: return
        attachPasswordVault(context, keyBytes)
        keyBytes.fill(0)
    }
}

internal fun BrowserViewModel.persistSavedPasswords() {
    // Legacy JSON persistence has been retired.
}

fun BrowserViewModel.savePassword(domain: String, username: String, password: String) {
    // Replace existing entry for same domain+username, or add new
    val existing = savedPasswords.indexOfFirst { it.domain == domain && it.username == username }
    val entry = BrowserViewModel.SavedPassword(
        id = if (existing != -1) savedPasswords[existing].id else java.util.UUID.randomUUID().toString(),
        domain = domain,
        username = username,
        password = password,
        timestamp = System.currentTimeMillis()
    )
    if (existing != -1) savedPasswords[existing] = entry else savedPasswords.add(0, entry)

    val stored = entry.toPasswordEntry()
    val vault = passwordVaultManager
    if (vault != null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (existing != -1) vault.updatePassword(stored) else vault.addPassword(stored)
        }
    } else {
        // Vault not open yet — queue the write, flushed by attachPasswordVault() once open.
        synchronized(pendingVaultWrites) {
            pendingVaultWrites.removeAll { it.domain == domain && it.username == username }
            pendingVaultWrites.add(0, stored)
        }
    }

    pendingSaveCredential = null
}

fun BrowserViewModel.deletePassword(id: String) {
    savedPasswords.removeAll { it.id == id }
    passwordVaultManager?.let { vault ->
        viewModelScope.launch(Dispatchers.IO) {
            vault.deletePassword(id)
        }
    }
}

fun BrowserViewModel.clearAllSavedPasswords() {
    savedPasswords.clear()
    passwordVaultManager?.let { vault ->
        viewModelScope.launch(Dispatchers.IO) {
            vault.deleteAllPasswords()
        }
    }
}

fun BrowserViewModel.clearSavedPasswordsSince(cutoffTime: Long) {
    savedPasswords.removeAll { it.timestamp >= cutoffTime }
    passwordVaultManager?.let { vault ->
        viewModelScope.launch(Dispatchers.IO) {
            vault.deletePasswordsSince(cutoffTime)
        }
    }
}

fun BrowserViewModel.getPasswordsForDomain(domain: String): List<BrowserViewModel.SavedPassword> =
    savedPasswords.filter { it.domain.contains(domain, ignoreCase = true) || domain.contains(it.domain, ignoreCase = true) }

fun BrowserViewModel.checkAutofillForUrl(url: String) {
    // Reset post-fill chip on every new page load
    autofillWasPerformed = false
    autofillLastUsed = null
    autofillSuggestion = null
}

fun BrowserViewModel.checkAutofillForFocus(url: String) {
    if (url.isBlank() || url == "about:blank") {
        autofillMatches = emptyList()
        showAutofillBottomSheet = false
        // Reset post-fill chip when navigating away
        autofillWasPerformed = false
        autofillLastUsed = null
        return
    }
    try {
        val host = java.net.URI(url).host ?: ""
        val domain = host.removePrefix("www.")
        val matches = savedPasswords.filter {
            it.domain == domain || domain.contains(it.domain) || it.domain.contains(domain)
        }
        if (matches.isNotEmpty()) {
            autofillMatches = matches
            // Only open the sheet automatically; don't reset autofillWasPerformed
            // so the "Switch account" chip can still be tapped again
            showAutofillBottomSheet = true
        } else {
            autofillMatches = emptyList()
            showAutofillBottomSheet = false
        }
    } catch (e: Exception) {
        autofillMatches = emptyList()
        showAutofillBottomSheet = false
    }
}

fun BrowserViewModel.dismissSaveCredential() { pendingSaveCredential = null }
fun BrowserViewModel.neverSavePasswordForDomain(context: Context, domain: String) {
    val clean = domain.trim().lowercase().removePrefix("www.")
    if (clean.isNotEmpty()) {
        val updated = neverSavePasswordDomains + clean
        neverSavePasswordDomains = updated
        pendingSaveCredential = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.dataStore.edit { it[BrowserViewModel.NEVER_SAVE_PASSWORD_DOMAINS_KEY] = updated }
            } catch (_: Exception) {}
        }
    }
}
fun BrowserViewModel.dismissAutofill() {
    autofillSuggestion = null
    showAutofillBottomSheet = false
}

fun BrowserViewModel.autofillCredential(credential: BrowserViewModel.SavedPassword) {
    autofillLastUsed = credential
    autofillWasPerformed = true
    showAutofillBottomSheet = false
    val activeId = activeTabId ?: return
    val activeTab = tabs.find { it.id == activeId } ?: return
    val userEscaped = credential.username.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    val passEscaped = credential.password.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

    val js = """
        (function() {
            function setVal(el, val) {
                if (!el) return;
                try {
                    el.focus();
                    var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                    if (nativeSetter && nativeSetter.set) {
                        nativeSetter.set.call(el, val);
                    } else {
                        el.value = val;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new Event('blur', { bubbles: true }));
                } catch(e) {}
            }

            var passInputs = Array.from(document.querySelectorAll('input[type="password"]'));
            var passInput = passInputs.find(function(el) {
                return el.offsetWidth > 0 || el.offsetHeight > 0;
            }) || passInputs[0];

            var userInput = null;
            if (passInput) {
                var form = passInput.form;
                if (form) {
                    var formInputs = Array.from(form.querySelectorAll('input'));
                    var passIdx = formInputs.indexOf(passInput);
                    for (var i = passIdx - 1; i >= 0; i--) {
                        var inp = formInputs[i];
                        var type = (inp.getAttribute('type') || 'text').toLowerCase();
                        if (['text', 'email', 'tel', 'number', 'url'].indexOf(type) !== -1 || (inp.name && inp.name.indexOf('user') !== -1) || (inp.id && inp.id.indexOf('user') !== -1)) {
                            userInput = inp;
                            break;
                        }
                    }
                }
                if (!userInput) {
                    var allInputs = Array.from(document.querySelectorAll('input'));
                    var passIdx = allInputs.indexOf(passInput);
                    for (var i = passIdx - 1; i >= 0; i--) {
                        var inp = allInputs[i];
                        var type = (inp.getAttribute('type') || 'text').toLowerCase();
                        if (['text', 'email', 'tel', 'number', 'url'].indexOf(type) !== -1) {
                            userInput = inp;
                            break;
                        }
                    }
                }
            } else {
                var allInputs = Array.from(document.querySelectorAll('input'));
                userInput = allInputs.find(function(inp) {
                    var type = (inp.getAttribute('type') || 'text').toLowerCase();
                    return type === 'email' || type === 'text' || (inp.name && inp.name.indexOf('user') !== -1) || (inp.id && inp.id.indexOf('user') !== -1);
                });
            }

            if (userInput) setVal(userInput, '$userEscaped');
            if (passInput) setVal(passInput, '$passEscaped');
        })();
    """.trimIndent()

    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            activeTab.session.loadUri("javascript:$js")
        } catch (e: Exception) {
            Log.e(TAG, "Autofill injection failed", e)
        }
    }
}

fun BrowserViewModel.attachPasswordVault(context: Context, masterKeyBytes: ByteArray) {
    val keyBytes = masterKeyBytes.copyOf()
    passwordVaultSyncJob?.cancel()
    passwordVaultManager?.close()
    passwordVaultManager = PasswordVaultManager(context, keyBytes)

    val snapshot = savedPasswords.toList()
    passwordVaultSyncJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (snapshot.isNotEmpty()) {
                passwordVaultManager?.importAll(snapshot.map { it.toPasswordEntry() })
            }
            // Flush any credentials saved before the vault finished opening
            val pending = synchronized(pendingVaultWrites) {
                pendingVaultWrites.toList().also { pendingVaultWrites.clear() }
            }
            if (pending.isNotEmpty()) {
                passwordVaultManager?.importAll(pending)
            }
            migrateLegacyPasswordsIfNeeded(context)
            passwordVaultManager?.getAllPasswords()?.collect { entries ->
                withContext(Dispatchers.Main) {
                    savedPasswords.clear()
                    savedPasswords.addAll(entries.map { it.toSavedPassword() })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching password vault", e)
        }
    }
}

private suspend fun BrowserViewModel.migrateLegacyPasswordsIfNeeded(context: Context) {
    val prefs = context.dataStore.data.first()

    // ── 1. Migrate saved_passwords.json (structured JSON) ────────────────────
    if (prefs[BrowserViewModel.Companion.PASSWORD_MIGRATION_DONE_KEY] != true) {
        val vault = passwordVaultManager ?: return
        val legacyFile = File(context.filesDir, "saved_passwords.json")
        if (legacyFile.exists()) {
            try {
                val arr = JSONArray(legacyFile.readText())
                val importedEntries = mutableListOf<PasswordEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val saved = BrowserViewModel.SavedPassword(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        domain = o.optString("domain", ""),
                        username = o.optString("username", ""),
                        password = o.optString("password", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                    importedEntries.add(saved.toPasswordEntry())
                }
                if (importedEntries.isNotEmpty()) {
                    vault.importAll(importedEntries)
                }
                legacyFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating legacy passwords JSON", e)
            }
        }
        context.dataStore.edit { it[BrowserViewModel.Companion.PASSWORD_MIGRATION_DONE_KEY] = true }
    }

    // ── 2. Migrate DevNotes with type == "PASSWORD" ──────────────────────────
    // Runs independently — separate flag so users who already ran migration v1
    // (which deleted DevNotes without importing them) still get this pass.
    // Uses the in-memory devNotes list which loadDevNotes() populated on init.
    if (prefs[BrowserViewModel.Companion.DEVNOTES_PASSWORD_MIGRATION_KEY] != true) {
        val vault = passwordVaultManager ?: return
        val devNotePasswords = devNotes.filter { it.type == "PASSWORD" }
        if (devNotePasswords.isNotEmpty()) {
            val importedFromNotes = devNotePasswords.mapNotNull { parseDevNotePassword(it) }
            if (importedFromNotes.isNotEmpty()) {
                vault.importAll(importedFromNotes)
                Log.i(TAG, "Migrated ${importedFromNotes.size} DevNote passwords to vault")
            }
            withContext(Dispatchers.Main) {
                devNotes.removeAll { it.type == "PASSWORD" }
            }
            saveDevNotes()
        }
        context.dataStore.edit { it[BrowserViewModel.Companion.DEVNOTES_PASSWORD_MIGRATION_KEY] = true }
    }
}

/**
 * Best-effort parse of a free-form DevNote PASSWORD entry into a structured PasswordEntry.
 *
 * Handles common patterns users type:
 *   "github.com\nusername: john\npassword: secret"
 *   "user: john@email.com | pass: abc123"
 *   "mysite.com - john - abc123"
 *   Plain password with the site name in the title
 */
private fun parseDevNotePassword(note: BrowserViewModel.DevNote): PasswordEntry? {
    val now = System.currentTimeMillis()
    val content = note.content.trim()
    if (content.isBlank()) return null

    // Normalise: treat common separators as newlines for uniform processing
    val lines = content
        .replace("|", "\n")
        .replace(" - ", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    var extractedUsername = ""
    var extractedPassword = ""
    var extractedDomain = ""
    val notesLines = mutableListOf<String>()

    val userKeywords = listOf("user", "username", "email", "login", "account")
    val passKeywords = listOf("pass", "password", "pwd", "secret", "key")
    val domainKeywords = listOf("site", "url", "domain", "website", "http", "www", ".")

    for (line in lines) {
        val lower = line.lowercase()
        val colonIdx = line.indexOf(':')

        // Key: value format
        if (colonIdx > 0) {
            val key = line.substring(0, colonIdx).trim().lowercase()
            val value = line.substring(colonIdx + 1).trim()
            when {
                userKeywords.any { key.contains(it) } -> extractedUsername = value
                passKeywords.any { key.contains(it) } -> extractedPassword = value
                domainKeywords.any { key.contains(it) } ->
                    extractedDomain = value.removePrefix("https://").removePrefix("http://")
                        .removePrefix("www.").substringBefore("/").trim()
                else -> notesLines.add(line)
            }
        } else {
            // No colon — classify by line content
            when {
                lower.startsWith("http") || lower.contains("www.") || lower.matches(Regex(".*\\.[a-z]{2,}.*")) ->
                    if (extractedDomain.isBlank())
                        extractedDomain = line.removePrefix("https://").removePrefix("http://")
                            .removePrefix("www.").substringBefore("/").trim()
                    else notesLines.add(line)
                line.contains("@") && extractedUsername.isBlank() -> extractedUsername = line
                else -> notesLines.add(line)
            }
        }
    }

    // If we still have no domain, use the note title (often "github.com" or "Netflix login")
    if (extractedDomain.isBlank()) {
        extractedDomain = note.title
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore("/").trim()
    }

    // If we found no structured password, treat leftover lines as the password
    // so the user doesn't lose data. They can clean it up in Password Manager.
    if (extractedPassword.isBlank()) {
        // Last unclassified line is most likely the password
        if (notesLines.isNotEmpty()) {
            extractedPassword = notesLines.removeLast()
        } else if (extractedUsername.isBlank()) {
            // Only one chunk of text — store as password, title as domain
            extractedPassword = content
        }
    }

    // Nothing useful — skip
    if (extractedPassword.isBlank() && extractedUsername.isBlank()) return null

    return PasswordEntry(
        id = java.util.UUID.randomUUID().toString(),
        label = note.title.take(100),
        domain = extractedDomain.take(255),
        username = extractedUsername.take(255),
        password = extractedPassword,
        notes = if (notesLines.isNotEmpty()) notesLines.joinToString("\n").take(500) else "",
        createdAt = note.timestamp,
        updatedAt = now
    )
}

private fun BrowserViewModel.SavedPassword.toPasswordEntry(): PasswordEntry {
    return PasswordEntry(
        id = id,
        domain = domain,
        username = username,
        password = password,
        label = "",
        notes = "",
        createdAt = timestamp,
        updatedAt = timestamp
    )
}

private fun PasswordEntry.toSavedPassword(): BrowserViewModel.SavedPassword {
    return BrowserViewModel.SavedPassword(
        id = id,
        domain = domain,
        username = username,
        password = password,
        timestamp = maxOf(createdAt, updatedAt)
    )
}
