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

/**
 * Registry containing curated Chrome and Brave type flags mapped to Mozilla GeckoView preferences.
 */
object OmniFlagsRegistry {

    val ALL_FLAGS: List<OmniEngineFlag> = listOf(
        // ── Group 1: Chrome Performance Flags ─────────────────────────────────
        OmniEngineFlag(
            id = "chrome_parallel_downloading",
            title = "Parallel Downloading",
            description = "Accelerates downloads by establishing multiple concurrent HTTP persistent socket connections per server.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-parallel-downloading",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "network.http.max-persistent-connections-per-server" to 10,
                "network.http.max-urgent-start-connections" to 8
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_back_forward_cache",
            title = "Back-Forward Cache (BFCache)",
            description = "Caches entire document trees in memory for instant, zero-latency back/forward navigation without re-requesting or re-rendering pages.",
            origin = FlagOrigin.CHROME,
            tag = "#back-forward-cache",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "fission.bfcacheInParent" to true,
                "browser.sessionhistory.max_total_viewers" to 5
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_smooth_scrolling",
            title = "Smooth & Fluid Touch Scrolling",
            description = "Enables APZ smooth scrolling physics and momentum interpolation for silky responsive gestures across web pages.",
            origin = FlagOrigin.CHROME,
            tag = "#smooth-scrolling",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "general.smoothScroll" to true,
                "general.smoothScroll.lines.durationMaxMS" to 125,
                "general.smoothScroll.mouseWheel.durationMaxMS" to 200
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_hardware_video_decode",
            title = "Hardware-Accelerated Video Decode",
            description = "Offloads high-resolution H.264, VP9, and AV1 video decoding to the device's hardware MediaCodec/GPU to conserve battery and reduce CPU thermals.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-accelerated-video-decode",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "media.hardware-video-decoding.enabled" to true,
                "media.android-media-codec.preferred" to true,
                "media.ffmpeg.vaapi.enabled" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_webgpu",
            title = "WebGPU Next-Gen 3D & Compute API",
            description = "Enables the modern low-overhead WebGPU API for high-performance 3D graphics, game engines, and in-browser AI tensor computing.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-unsafe-webgpu",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "dom.webgpu.enabled" to true,
                "gfx.webgpu.force-enabled" to true
            ),
            defaultEnabled = false
        ),
        OmniEngineFlag(
            id = "chrome_memory_saver",
            title = "Memory Saver (Automatic Tab Discarding)",
            description = "Automatically discards inactive background tabs when system RAM is low to prevent out-of-memory browser crashes and free device resources.",
            origin = FlagOrigin.CHROME,
            tag = "#high-efficiency-mode-available",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "browser.tabs.unloadOnLowMemory" to true,
                "browser.low_commit_space_threshold_mb" to 256
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_lazy_loading",
            title = "Lazy Loading for Images & Iframes",
            description = "Defers loading of below-the-fold images and iframes until the user scrolls near them, reducing mobile data consumption and initial load times.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-lazy-image-loading",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "dom.image-lazy-loading.enabled" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_tcp_fast_open",
            title = "TCP Fast Open (TFO)",
            description = "Speeds up repeated connections to the same host by sending payload data during the initial TCP SYN handshake packet.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-tcp-fast-open",
            category = FlagCategory.NETWORK,
            enginePrefs = mapOf(
                "network.tcp.tcp_fastopen_enable" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_wasm_simd",
            title = "WebAssembly SIMD Vectorization",
            description = "Enables 128-bit Single Instruction Multiple Data (SIMD) vector instructions in WebAssembly, accelerating web games, codecs, and offline AI.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-webassembly-simd",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "javascript.options.wasm_simd" to true,
                "javascript.options.wasm_simd_wormhole" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_subpixel_font",
            title = "Subpixel Font Positioning",
            description = "Forces fractional subpixel font glyph positioning for razor-sharp typography on high-DPI smartphone displays.",
            origin = FlagOrigin.CHROME,
            tag = "#lcd-text-aa",
            category = FlagCategory.PERFORMANCE,
            enginePrefs = mapOf(
                "gfx.text.subpixel-position.force-enabled" to true
            ),
            defaultEnabled = true
        ),

        // ── Group 2: Brave Shields & Privacy Hardening ────────────────────────
        OmniEngineFlag(
            id = "brave_shields_gpc",
            title = "Global Privacy Control (GPC) Signal",
            description = "Transmits the legal Sec-GPC header signal legally requiring websites and ad exchanges not to sell or share your personal browsing data.",
            origin = FlagOrigin.BRAVE,
            tag = "#brave-shields-gpc",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "privacy.globalprivacycontrol.enabled" to true,
                "privacy.globalprivacycontrol.functionality.enabled" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_shields_query_strip",
            title = "Query Parameter Tracking Stripper",
            description = "Cleans URLs before loading by stripping invasive tracking and surveillance tags (utm_source, fbclid, gclid, mc_eid, etc.).",
            origin = FlagOrigin.BRAVE,
            tag = "#brave-query-filter",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "privacy.query_stripping.enabled" to true,
                "privacy.query_stripping.enabled.pbmode" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_shields_strict_referrer",
            title = "Strict Cross-Origin Referrer Trimming",
            description = "Trims cross-origin referrers to strict domain origins (sends only 'https://example.com/' instead of full search queries or sensitive page paths).",
            origin = FlagOrigin.BRAVE,
            tag = "#brave-referrer-trimming",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "network.http.referer.trimmingPolicy" to 2,
                "network.http.referer.XOriginPolicy" to 2,
                "network.http.referer.XOriginTrimmingPolicy" to 2
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_shields_farbling",
            title = "Fingerprint Farbling (Canvas & Audio Entropy)",
            description = "Injects subtle pseudo-random noise into HTML5 Canvas, WebGL, and WebAudio outputs so trackers cannot generate persistent device fingerprints.",
            origin = FlagOrigin.BRAVE,
            tag = "#brave-fingerprint-farbling",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "privacy.resistFingerprinting.randomData" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_shields_block_sensors",
            title = "Block Hardware & Motion Sensors",
            description = "Blocks websites from accessing gyroscope, accelerometer, magnetometer, ambient light, gamepad, and vibrator APIs for fingerprinting.",
            origin = FlagOrigin.BRAVE,
            tag = "#device-sensors",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "device.sensors.enabled" to false,
                "dom.gamepad.enabled" to false,
                "dom.vibrator.enabled" to false,
                "dom.vr.enabled" to false
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_shields_bounce_tracking",
            title = "Bounce Tracking & Redirect Purge",
            description = "Detects intermediate bounce-tracking redirectors that try to deposit cookies across sites, and automatically purges their storage.",
            origin = FlagOrigin.BRAVE,
            tag = "#brave-bounce-tracking",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "privacy.purge_trackers.enabled" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "brave_total_cookie_protection",
            title = "Total Cookie Protection & dFPI Partitioning",
            description = "Enforces per-site cookie jars and partitions service workers and network state per top-level domain, isolating cross-site trackers.",
            origin = FlagOrigin.BRAVE,
            tag = "#partitioned-cookies",
            category = FlagCategory.BRAVE_SHIELDS,
            enginePrefs = mapOf(
                "network.cookie.cookieBehavior" to 5,
                "privacy.partition.network_state" to true,
                "privacy.partition.serviceWorkers" to true
            ),
            defaultEnabled = true
        ),

        // ── Group 3: Security & Protocol Hardening ────────────────────────────
        OmniEngineFlag(
            id = "chrome_encrypted_client_hello",
            title = "Encrypted Client Hello (ECH / TLS SNI Encryption)",
            description = "Encrypts the Server Name Indication (SNI) metadata inside TLS handshakes, stopping ISPs, firewalls, and Wi-Fi operators from spying on your destination domain.",
            origin = FlagOrigin.CHROME,
            tag = "#encrypted-client-hello",
            category = FlagCategory.SECURITY,
            enginePrefs = mapOf(
                "network.dns.echconfig.enabled" to true,
                "network.dns.use_https_rr_as_altsvc" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_webrtc_ip_policy",
            title = "WebRTC IP Handling Policy (Hide LAN Candidates)",
            description = "Restricts WebRTC ICE gathering to default public addresses and blocks local LAN IP address disclosure across peer connections.",
            origin = FlagOrigin.CHROME,
            tag = "#webrtc-ip-handling-policy",
            category = FlagCategory.SECURITY,
            enginePrefs = mapOf(
                "media.peerconnection.ice.default_address_only" to true,
                "media.peerconnection.ice.no_host" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_jit_hardening",
            title = "Super Duper Secure Mode (JIT Hardening)",
            description = "Disables baseline and Ion Just-In-Time (JIT) compiler optimizations to neutralize the vast majority of memory corruption exploits.",
            origin = FlagOrigin.CHROME,
            tag = "#v8-optimizer",
            category = FlagCategory.SECURITY,
            enginePrefs = mapOf(
                "javascript.options.ion" to false,
                "javascript.options.baselinejit" to false
            ),
            defaultEnabled = false
        ),
        OmniEngineFlag(
            id = "chrome_strict_file_origin",
            title = "Strict Local File Origin Policy",
            description = "Prevents local file:// pages from accessing other local files or directories, mitigating local arbitrary file disclosure.",
            origin = FlagOrigin.CHROME,
            tag = "#disable-file-system-api",
            category = FlagCategory.SECURITY,
            enginePrefs = mapOf(
                "security.fileuri.strict_origin_policy" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_abusive_redirect_intervention",
            title = "Abusive Redirect & Deceptive Pop-up Shield",
            description = "Blocks deceptive click-hijacking pop-ups and rogue automated redirection scripts triggered by invisible page overlays.",
            origin = FlagOrigin.CHROME,
            tag = "#abusive-ad-intervention",
            category = FlagCategory.SECURITY,
            enginePrefs = mapOf(
                "dom.popup_allowed_events" to "click"
            ),
            defaultEnabled = true
        ),

        // ── Group 4: Media & Display ──────────────────────────────────────────
        OmniEngineFlag(
            id = "chrome_autoplay_block",
            title = "Aggressive Autoplay Blocker (Audio & Video)",
            description = "Requires explicit user interaction before any web page audio or video element is permitted to begin playback.",
            origin = FlagOrigin.CHROME,
            tag = "#autoplay-policy",
            category = FlagCategory.MEDIA_UI,
            enginePrefs = mapOf(
                "media.autoplay.default" to 5,
                "media.autoplay.blocking_policy" to 2,
                "media.block-autoplay-until-in-view" to true
            ),
            defaultEnabled = true
        ),
        OmniEngineFlag(
            id = "chrome_force_dark_mode",
            title = "Force Dark Mode for Web Contents",
            description = "Renders all websites with a high-contrast dark theme by analyzing page colors and dynamically inverting luminance.",
            origin = FlagOrigin.CHROME,
            tag = "#enable-force-dark",
            category = FlagCategory.MEDIA_UI,
            enginePrefs = mapOf(
                "layout.force_dark_mode" to true,
                "ui.systemUsesDarkTheme" to 1
            ),
            defaultEnabled = false
        ),
        OmniEngineFlag(
            id = "chrome_overscroll_elasticity",
            title = "Overscroll Elasticity & Bounce Physics",
            description = "Enables fluid physics rubber-banding overscroll and visual pull-to-refresh indicators on touch navigation.",
            origin = FlagOrigin.CHROME,
            tag = "#pull-to-refresh",
            category = FlagCategory.MEDIA_UI,
            enginePrefs = mapOf(
                "apz.overscroll.enabled" to true
            ),
            defaultEnabled = true
        )
    )

    private val FLAG_MAP: Map<String, OmniEngineFlag> by lazy {
        ALL_FLAGS.associateBy { it.id }
    }

    fun getFlagById(id: String): OmniEngineFlag? = FLAG_MAP[id]

    fun getDefaultStateMap(): Map<String, Boolean> {
        return ALL_FLAGS.associate { it.id to it.defaultEnabled }
    }
}
