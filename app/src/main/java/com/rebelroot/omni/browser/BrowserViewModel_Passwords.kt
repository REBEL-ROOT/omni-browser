package com.rebelroot.omni.browser

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import com.rebelroot.omni.browser.BrowserViewModel.Companion.TAG

fun BrowserViewModel.loadSavedPasswords(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(context.filesDir, "saved_passwords.json")
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                val list = mutableListOf<BrowserViewModel.SavedPassword>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(BrowserViewModel.SavedPassword(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        domain = o.optString("domain", ""),
                        username = o.optString("username", ""),
                        password = o.optString("password", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
                withContext(Dispatchers.Main) {
                    savedPasswords.clear()
                    savedPasswords.addAll(list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading passwords", e)
            }
        }
    }
}

internal fun BrowserViewModel.persistSavedPasswords() {
    val ctx = appContext ?: return
    val snapshot = savedPasswords.toList()
    viewModelScope.launch(Dispatchers.IO) {
        val file = File(ctx.filesDir, "saved_passwords.json")
        try {
            val arr = JSONArray()
            snapshot.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("domain", p.domain)
                    put("username", p.username)
                    put("password", p.password)
                    put("timestamp", p.timestamp)
                })
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving passwords", e)
        }
    }
}

fun BrowserViewModel.savePassword(domain: String, username: String, password: String) {
    // Replace existing entry for same domain+username, or add new
    val existing = savedPasswords.indexOfFirst { it.domain == domain && it.username == username }
    val entry = BrowserViewModel.SavedPassword(domain = domain, username = username, password = password)
    if (existing != -1) savedPasswords[existing] = entry else savedPasswords.add(0, entry)
    persistSavedPasswords()

    // Also save to DevNotes so it appears in the notes list
    val existingNote = devNotes.indexOfFirst { it.title == "Credentials for $domain" && it.type == "PASSWORD" }
    val noteTitle = "Credentials for $domain"
    val noteContent = "$username\n$password"
    if (existingNote != -1) {
        devNotes[existingNote] = devNotes[existingNote].copy(title = noteTitle, content = noteContent, timestamp = System.currentTimeMillis())
        saveDevNotes()
    } else {
        addDevNote(noteTitle, noteContent, "PASSWORD")
    }

    pendingSaveCredential = null
}

fun BrowserViewModel.deletePassword(id: String) {
    savedPasswords.removeAll { it.id == id }
    persistSavedPasswords()
}

fun BrowserViewModel.clearAllSavedPasswords() {
    savedPasswords.clear()
    persistSavedPasswords()
}

fun BrowserViewModel.clearSavedPasswordsSince(cutoffTime: Long) {
    savedPasswords.removeAll { it.timestamp >= cutoffTime }
    persistSavedPasswords()
}

fun BrowserViewModel.getPasswordsForDomain(domain: String): List<BrowserViewModel.SavedPassword> =
    savedPasswords.filter { it.domain.contains(domain, ignoreCase = true) || domain.contains(it.domain, ignoreCase = true) }

fun BrowserViewModel.checkAutofillForUrl(url: String) {
    // Disabled to prevent auto-showing on page load
    autofillSuggestion = null
}

fun BrowserViewModel.checkAutofillForFocus(url: String) {
    if (url.isBlank() || url == "about:blank") {
        autofillMatches = emptyList()
        showAutofillBottomSheet = false
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
fun BrowserViewModel.dismissAutofill() {
    autofillSuggestion = null
    showAutofillBottomSheet = false
}

fun BrowserViewModel.autofillCredential(credential: BrowserViewModel.SavedPassword) {
    val activeId = activeTabId ?: return
    val activeTab = tabs.find { it.id == activeId } ?: return
    val userEscaped = credential.username.replace("'", "\\'")
    val passEscaped = credential.password.replace("'", "\\'")

    val js = """
        (function() {
            var passInputs = Array.from(document.querySelectorAll('input[type="password"]'));
            if (passInputs.length === 0) return;
            
            var passInput = passInputs.find(function(el) {
                return el.offsetWidth > 0 || el.offsetHeight > 0;
            }) || passInputs[0];
            
            var userInput = null;
            var form = passInput.form;
            
            if (form) {
                var formInputs = Array.from(form.querySelectorAll('input'));
                var passIdx = formInputs.indexOf(passInput);
                for (var i = passIdx - 1; i >= 0; i--) {
                    var inp = formInputs[i];
                    var type = (inp.getAttribute('type') || 'text').toLowerCase();
                    if (['text', 'email', 'tel', 'number', 'url'].indexOf(type) !== -1 || inp.name.indexOf('user') !== -1 || inp.id.indexOf('user') !== -1) {
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
            
            if (passInput) {
                passInput.focus();
                passInput.value = '$passEscaped';
                passInput.dispatchEvent(new Event('input', { bubbles: true }));
                passInput.dispatchEvent(new Event('change', { bubbles: true }));
                passInput.dispatchEvent(new Event('blur', { bubbles: true }));
            }
            if (userInput) {
                userInput.focus();
                userInput.value = '$userEscaped';
                userInput.dispatchEvent(new Event('input', { bubbles: true }));
                userInput.dispatchEvent(new Event('change', { bubbles: true }));
                userInput.dispatchEvent(new Event('blur', { bubbles: true }));
            }
        })();
    """.trimIndent()

    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            activeTab.session.loadUri("javascript:\$js")
            showAutofillBottomSheet = false
        } catch (e: Exception) {
            Log.e(TAG, "Autofill injection failed", e)
        }
    }
}
