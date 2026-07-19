/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.browser

data class SitePermission(
    val host: String,
    val location: String = "ask",       // "allow", "block", "ask"
    val camera: String = "ask",         // "allow", "block", "ask"
    val microphone: String = "ask",     // "allow", "block", "ask"
    val notifications: String = "ask",  // "allow", "block", "ask"
    val javascript: String = "allow",   // "allow", "block"
    val autoplay: String = "allow"      // "allow", "block"
)
