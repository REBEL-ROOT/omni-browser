/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CuratedExtension(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val category: String,
    val rating: Float,
    val downloadUrl: String,
    val iconVector: ImageVector,
    val accentColor: Color
) {
    val iconUrl: String
        get() {
            val addonNumber = downloadUrl.substringAfter("addon-").substringBefore("-latest.xpi")
            val first3 = if (addonNumber.length >= 3) addonNumber.take(3) else "000"
            return "https://addons.mozilla.org/user-media/addon_icons/$first3/$addonNumber-64.png"
        }
}

object CuratedExtensionRepository {

    val categories = listOf("All", "Privacy", "Utilities", "Media", "Productivity")

    val curatedList = listOf(
        CuratedExtension(
            id = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            author = "Raymond Hill",
            description = "An efficient wide-spectrum content blocker. Blocks ads, popups, trackers, and malware sites natively.",
            category = "Privacy",
            rating = 4.8f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon-607454-latest.xpi",
            iconVector = Icons.Rounded.Shield,
            accentColor = Color(0xFFFF3B5C)
        ),
        CuratedExtension(
            id = "addon@darkreader.org",
            name = "Dark Reader",
            author = "Alexander Shutov",
            description = "Inverts bright web page colors to custom dark mode for comfortable night browsing.",
            category = "Utilities",
            rating = 4.7f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/addon-396701-latest.xpi",
            iconVector = Icons.Rounded.DarkMode,
            accentColor = Color(0xFF818CF8)
        ),
        CuratedExtension(
            id = "jid1-MnnAVZgavAyrHg@jetpack",
            name = "Privacy Badger",
            author = "EFF (Electronic Frontier Foundation)",
            description = "Automatically learns to block invisible tracking scripts as you browse the web.",
            category = "Privacy",
            rating = 4.6f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/addon-506646-latest.xpi",
            iconVector = Icons.Rounded.Security,
            accentColor = Color(0xFF10B981)
        ),
        CuratedExtension(
            id = "sponsorBlocker@ajay.app",
            name = "SponsorBlock for YouTube",
            author = "Ajay Ramachandran",
            description = "Skip YouTube video sponsors, intros, outros, and subscribe reminders automatically.",
            category = "Media",
            rating = 4.9f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/addon-941199-latest.xpi",
            iconVector = Icons.Rounded.PlayCircle,
            accentColor = Color(0xFFF59E0B)
        ),
        CuratedExtension(
            id = "twp-translator@twp.com",
            name = "Translate Web Pages (TWP)",
            author = "Filipe Ps",
            description = "Translates entire web pages in real-time using Google Translate or Yandex Translate.",
            category = "Productivity",
            rating = 4.7f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/traduzir-paginas-web/addon-961026-latest.xpi",
            iconVector = Icons.Rounded.Translate,
            accentColor = Color(0xFF3B82F6)
        ),
        CuratedExtension(
            id = "7790757a-9a99-4d69-b509-906560417539",
            name = "ClearURLs",
            author = "Kevin Roebert",
            description = "Removes tracking elements and parameters from URLs to protect your privacy when sharing links.",
            category = "Privacy",
            rating = 4.6f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/clearurls/addon-899885-latest.xpi",
            iconVector = Icons.Rounded.LinkOff,
            accentColor = Color(0xFFEC4899)
        ),
        CuratedExtension(
            id = "{c2c0f360-1d10-473a-9db1-64325033b92c}",
            name = "Violentmonkey",
            author = "topred",
            description = "Provides userscript support to customize website behavior and inject custom scripts.",
            category = "Productivity",
            rating = 4.7f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/violentmonkey/addon-824131-latest.xpi",
            iconVector = Icons.Rounded.Code,
            accentColor = Color(0xFF14B8A6)
        ),
        CuratedExtension(
            id = "jid1-BoFiL9Vbdl2zwA@jetpack",
            name = "Decentraleyes",
            author = "Thomas Rientjes",
            description = "Emulates Content Delivery Networks locally to prevent tracking by large CDN providers.",
            category = "Privacy",
            rating = 4.5f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/decentraleyes/addon-674489-latest.xpi",
            iconVector = Icons.Rounded.Storage,
            accentColor = Color(0xFF6366F1)
        ),
        CuratedExtension(
            id = "{446900e4-71c2-419f-a6a7-df9c091e268b}",
            name = "Bitwarden Password Manager",
            author = "Bitwarden Inc.",
            description = "A secure, open source password manager. Store, generate, and auto-fill logins and secure notes seamlessly.",
            category = "Privacy",
            rating = 4.8f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/bitwarden-password-manager/addon-854744-latest.xpi",
            iconVector = Icons.Rounded.Lock,
            accentColor = Color(0xFF175DDC)
        ),
        CuratedExtension(
            id = "78272b6fa5e24ba987ac@proton.me",
            name = "Proton Pass",
            author = "Proton AG",
            description = "End-to-end encrypted password manager and email alias generator from the makers of Proton Mail.",
            category = "Privacy",
            rating = 4.7f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/proton-pass/addon-1144005-latest.xpi",
            iconVector = Icons.Rounded.Key,
            accentColor = Color(0xFF6D4AFF)
        ),
        CuratedExtension(
            id = "keepassxc-browser@keepassxc.org",
            name = "KeePassXC-Browser",
            author = "KeePassXC Team",
            description = "Official browser integration for KeePassXC password manager to auto-fill logins securely.",
            category = "Privacy",
            rating = 4.6f,
            downloadUrl = "https://addons.mozilla.org/firefox/downloads/latest/keepassxc-browser/addon-893540-latest.xpi",
            iconVector = Icons.Rounded.VpnKey,
            accentColor = Color(0xFF53A048)
        )
    )
}
