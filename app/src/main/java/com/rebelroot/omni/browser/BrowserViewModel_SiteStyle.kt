package com.rebelroot.omni.browser

import android.content.Context

/**
 * Applies Dark Reader-grade direct surface & CSS variable styling to GeckoView tabs.
 * Features:
 * 1. 100% Pure Pitch-Black OLED / AMOLED mode (#000000) that turns off OLED pixels on ALL websites.
 * 2. High-clarity crisp white typography (#F1F5F9) — ZERO dark grey text wash across Wikipedia, Google, articles.
 * 3. Natural border & outline handling: Clean, borderless icon buttons without artificial box clutter.
 * 4. Comfortable Dark mode (#121214) with subtle card elevations.
 * 5. Calibrated reading presets: Sepia (#FBF0D9) and Forest (#0F1C15).
 * 6. Image, video, canvas, poster, thumbnail protection — ZERO negative color distortion.
 * 7. Dynamic DOM mutation observer to style infinite scrolling feeds and SPAs.
 */
fun BrowserViewModel.applySiteStyleToTab(targetTab: TabState? = null) {
    val tab = targetTab ?: activeTab ?: return
    val session = tab.session ?: return

    // Determine active theme preset strictly from user's explicit selection
    val effectiveTheme = siteStyleTheme

    val hasCustomStyles = effectiveTheme != "DEFAULT" || siteStyleFontSize != 100 || 
            siteStyleLineSpacing != 1.4f || siteStyleLetterSpacing != 0f || 
            siteStyleFontFamily != "inherit" || siteStyleHideImages || 
            siteStyleGrayscale || siteStyleWarmFilter

    if (!hasCustomStyles) {
        clearSiteStyleFromTab(tab)
        return
    }

    var colorScheme = ""
    var cssRules = ""

    val fontCss = if (siteStyleFontFamily != "inherit") "font-family: ${siteStyleFontFamily} !important;" else ""
    val sizeCss = """
        font-size: ${siteStyleFontSize}% !important;
        -webkit-text-size-adjust: ${siteStyleFontSize}% !important;
        -moz-text-size-adjust: ${siteStyleFontSize}% !important;
        text-size-adjust: ${siteStyleFontSize}% !important;
    """.trimIndent()
    val lineSpacingCss = if (siteStyleLineSpacing != 1.4f) "line-height: ${siteStyleLineSpacing} !important;" else ""
    val letterSpacingCss = if (siteStyleLetterSpacing != 0f) "letter-spacing: ${siteStyleLetterSpacing}px !important;" else ""

    when (effectiveTheme) {
        "OLED" -> {
            colorScheme = "dark"
            cssRules = """
                :root, [data-theme], [data-color-mode], html, body {
                    --bg: #000000 !important;
                    --bg-color: #000000 !important;
                    --background: #000000 !important;
                    --background-color: #000000 !important;
                    --bg-primary: #000000 !important;
                    --bg-secondary: #000000 !important;
                    --surface: #000000 !important;
                    --surface-color: #000000 !important;
                    --card-bg: #000000 !important;
                    --header-bg: #000000 !important;
                    --footer-bg: #000000 !important;
                    --search-bg: #000000 !important;
                    --ub-bg-color: #000000 !important;
                    --theme-bg: #000000 !important;
                    --theme-surface: #000000 !important;
                    --theme-card: #000000 !important;
                    --surface-background: #000000 !important;
                    --yt-spec-base-background: #000000 !important;
                    --yt-spec-raised-background: #000000 !important;
                    --yt-spec-menu-background: #000000 !important;
                    --yt-spec-static-overlay-background-solid: #000000 !important;
                    --m3c-surface: #000000 !important;
                    --m3c-surface-container: #000000 !important;
                    --m3c-surface-container-high: #000000 !important;
                    --m3c-surface-container-highest: #000000 !important;
                    --m3c-surface-container-low: #000000 !important;
                    --m3c-surface-container-lowest: #000000 !important;
                    --color-surface: #000000 !important;
                    --color-surface-1: #000000 !important;
                    --color-surface-2: #000000 !important;
                    --color-surface-3: #000000 !important;
                    --color-surface-4: #000000 !important;
                    --color-surface-5: #000000 !important;
                    --color-background: #000000 !important;
                    --color-primary-background: #000000 !important;
                    --color-secondary-background: #000000 !important;
                    --text: #f1f5f9 !important;
                    --text-color: #f1f5f9 !important;
                    --text-primary: #f1f5f9 !important;
                    --text-secondary: #e2e8f0 !important;
                    --border-color: #26272b !important;
                    --border: #26272b !important;
                    color-scheme: dark !important;
                }
                html, body, #__next, #root, #app, main, [role="main"], [role="article"],
                section, article, header, nav, footer, aside, dialog,
                .container, .wrapper, .content, .main-content, .layout, .page,
                [class*="container" i], [class*="wrapper" i], [class*="layout" i], [class*="content" i], [class*="page" i],
                .card, .panel, .box, .sidebar, .modal, .dropdown, .menu,
                [class*="card" i], [class*="panel" i], [class*="box" i], [class*="sidebar" i], [class*="modal" i], [class*="menu" i], [class*="list" i],
                table, tr, td, th, ul, ol, li, dl, dt, dd,
                g-card, g-inner-card, g-header, g-flat-button, g-expandable-card, c-wiz,
                .g, .kp-blk, .xpd, .cUnBl, .MjjYud, .vdLWh, .wDYH0e, .K5qjJc, .g-blk, .sfbg, .e222eb, .minidiv, .appbar, #aria-main, #cnt, #rcnt, [data-async-context],
                .A8SBwf, #searchform, .tsf, .mJ2Mod, .PZPZlf, .QCzoEc, .Lj9dx, .ULSxyf {
                    background-color: #000000 !important;
                    background: #000000 !important;
                    color: #f1f5f9 !important;
                }

                .card, .panel, table, tr, td, th, hr, .divider, [class*="divider" i], [class*="separator" i] {
                    border-color: #26272b !important;
                }

                .RNNXgb, input, textarea, select, [contenteditable="true"], [role="searchbox"], [role="combobox"] {
                    background-color: #000000 !important;
                    background: #000000 !important;
                    color: #ffffff !important;
                    -webkit-text-fill-color: #ffffff !important;
                    border: 1px solid #2d3036 !important;
                    border-radius: 8px;
                }

                button, [role="button"], .btn, [class*="btn" i], [class*="chip" i], [class*="pill" i] {
                    background-color: #000000 !important;
                    background: #000000 !important;
                    color: #f1f5f9 !important;
                }

                /* Crisp readable white typography across all text elements — zero grey */
                p, h1, h2, h3, h4, h5, h6, label, strong, b, em, i, span, dt, dd, summary,
                small, blockquote, q, cite, code, pre, figcaption {
                    color: #f1f5f9 !important;
                }

                /* High-contrast crisp links */
                a, a:visited {
                    color: #8ab4f8 !important;
                }

                img, video, canvas, svg, picture, iframe,
                [style*="background-image"], [style*="background: url"],
                [class*="thumb" i], [class*="thumbnail" i], [class*="avatar" i], [class*="poster" i],
                [class*="cover" i], [class*="media" i], [class*="image" i], [class*="photo" i],
                g-img, figure {
                    filter: none !important;
                    opacity: 1 !important;
                    background: transparent !important;
                }
                *::-webkit-scrollbar {
                    background-color: #000000 !important;
                    width: 6px !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #222222 !important;
                    border-radius: 3px !important;
                }
            """.trimIndent()
        }

        "DARK" -> {
            colorScheme = "dark"
            cssRules = """
                :root, [data-theme], [data-color-mode], html, body {
                    --bg: #121214 !important;
                    --bg-color: #121214 !important;
                    --background: #121214 !important;
                    --background-color: #121214 !important;
                    --bg-primary: #121214 !important;
                    --bg-secondary: #1a1c20 !important;
                    --surface: #18191c !important;
                    --surface-color: #18191c !important;
                    --card-bg: #18191c !important;
                    --header-bg: #121214 !important;
                    --footer-bg: #121214 !important;
                    --text: #f1f5f9 !important;
                    --text-color: #f1f5f9 !important;
                    --text-primary: #f1f5f9 !important;
                    --text-secondary: #e2e8f0 !important;
                    --border-color: #2e3035 !important;
                    --border: #2e3035 !important;
                    color-scheme: dark !important;
                }
                html, body, #__next, #root, #app, main, [role="main"], [role="article"],
                section, article, header, nav, footer, aside, dialog,
                .container, .wrapper, .content, .main-content, .layout, .page,
                [class*="container" i], [class*="wrapper" i], [class*="layout" i], [class*="content" i], [class*="page" i] {
                    background-color: #121214 !important;
                    background: #121214 !important;
                    color: #f1f5f9 !important;
                }
                .card, .panel, .box, .sidebar, .modal, .dropdown, .menu,
                [class*="card" i], [class*="panel" i], [class*="box" i], [class*="sidebar" i], [class*="modal" i], [class*="menu" i], [class*="list" i],
                table, tr, td, th, ul, ol, li, dl, dt, dd {
                    background-color: #18191c !important;
                    background: #18191c !important;
                    border-color: #2e3035 !important;
                    color: #f1f5f9 !important;
                }
                p, h1, h2, h3, h4, h5, h6, label, strong, b, em, i, span, dt, dd, summary,
                small, blockquote, q, cite, code, pre, figcaption {
                    color: #f1f5f9 !important;
                }
                a, a:visited {
                    color: #8ab4f8 !important;
                }
                input, textarea, select, [contenteditable="true"], [role="searchbox"] {
                    background-color: #1e2024 !important;
                    background: #1e2024 !important;
                    color: #ffffff !important;
                    -webkit-text-fill-color: #ffffff !important;
                    border: 1px solid #3c4043 !important;
                }
                button, [role="button"], .btn, [class*="btn" i], [class*="chip" i], [class*="pill" i] {
                    background-color: #202228 !important;
                    color: #f1f5f9 !important;
                    border-color: #3c4043 !important;
                }
                img, video, canvas, svg, picture, iframe,
                [style*="background-image"], [style*="background: url"],
                [class*="thumb" i], [class*="thumbnail" i], [class*="avatar" i], [class*="poster" i],
                [class*="cover" i], [class*="media" i], [class*="image" i], [class*="photo" i],
                g-img, figure {
                    filter: none !important;
                    opacity: 1 !important;
                    background: transparent !important;
                }
                *::-webkit-scrollbar {
                    background-color: #121214 !important;
                    width: 6px !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #2e3035 !important;
                    border-radius: 3px !important;
                }
            """.trimIndent()
        }

        "SEPIA" -> {
            colorScheme = "light"
            cssRules = """
                :root, html {
                    background-color: #fbf0d9 !important;
                    color: #5f4b32 !important;
                    color-scheme: light !important;
                }
                body, p, span, div, h1, h2, h3, h4, h5, h6, li, a, article, section {
                    background-color: #fbf0d9 !important;
                    color: #5f4b32 !important;
                }
                a { color: #8c4303 !important; }
                ::selection { background: #e2c290 !important; color: #332211 !important; }
            """.trimIndent()
        }

        "FOREST" -> {
            colorScheme = "dark"
            cssRules = """
                :root, html {
                    background-color: #0f1c15 !important;
                    color: #d0e4d7 !important;
                    color-scheme: dark !important;
                }
                body, p, span, div, h1, h2, h3, h4, h5, h6, li, a, article, section {
                    background-color: #0f1c15 !important;
                    color: #d0e4d7 !important;
                }
                a { color: #52c486 !important; }
                input, textarea, select, button { color-scheme: dark !important; }
                ::selection { background: #1f422e !important; color: #ffffff !important; }
            """.trimIndent()
        }
    }

    val fontRules = if (fontCss.isNotBlank() || lineSpacingCss.isNotBlank() || letterSpacingCss.isNotBlank()) {
        """
        html, body, p, span, div, h1, h2, h3, h4, h5, h6, li, a, section, article {
            $fontCss
            $lineSpacingCss
            $letterSpacingCss
        }
        """.trimIndent()
    } else ""

    val sizeRules = if (siteStyleFontSize != 100) """
        html, body {
            $sizeCss
        }
    """.trimIndent() else ""

    val hideImagesRules = if (siteStyleHideImages) """
        img, picture, figure, [style*="background-image"] {
            display: none !important;
        }
    """.trimIndent() else ""

    val grayscaleRules = if (siteStyleGrayscale) """
        html {
            filter: grayscale(100%) !important;
        }
    """.trimIndent() else ""

    val warmFilterRules = if (siteStyleWarmFilter) """
        html::before {
            content: "" !important;
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            background: rgba(255, 140, 0, 0.08) !important;
            pointer-events: none !important;
            z-index: 2147483647 !important;
        }
    """.trimIndent() else ""

    val fullCss = (cssRules + "\n" + fontRules + "\n" + sizeRules + "\n" + hideImagesRules + "\n" + grayscaleRules + "\n" + warmFilterRules)
        .trimIndent().replace("\n", " ").replace("'", "\\'")

    val js = """
        javascript:(function() {
            const id = 'omni-custom-site-style';
            const metaId = id + '-meta';
            const target = document.head || document.documentElement;
            if (!target) return;

            if ('$colorScheme' === 'dark') {
                try {
                    if (location.hostname.indexOf('google.') !== -1) {
                        document.cookie = 'PREF=f6=400; domain=' + location.hostname.substring(location.hostname.indexOf('.')) + '; path=/; max-age=31536000';
                    }
                } catch(e) {}
            }

            if ('$colorScheme' !== '') {
                let meta = document.getElementById(metaId);
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.id = metaId;
                    meta.name = 'color-scheme';
                    target.appendChild(meta);
                }
                meta.content = '$colorScheme';
            }

            let style = document.getElementById(id);
            if ('$fullCss' === '') {
                if (style) style.remove();
            } else {
                if (!style) {
                    style = document.createElement('style');
                    style.id = id;
                    target.appendChild(style);
                }
                style.textContent = '$fullCss';
            }

            if ('$effectiveTheme' === 'OLED') {
                const fixOledSurfaces = function() {
                    try {
                        const nonMediaEls = document.querySelectorAll('div, section, article, nav, header, footer, main, form, table, g-card, c-wiz');
                        for (let i = 0; i < nonMediaEls.length; i++) {
                            const el = nonMediaEls[i];
                            if (el.__omniOled) continue;
                            const bg = window.getComputedStyle(el).backgroundColor;
                            if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'rgb(0, 0, 0)') {
                                el.style.setProperty('background-color', '#000000', 'important');
                                el.style.setProperty('background', '#000000', 'important');
                                el.__omniOled = true;
                            }
                        }
                    } catch(e) {}
                };
                fixOledSurfaces();
                setTimeout(fixOledSurfaces, 250);
                setTimeout(fixOledSurfaces, 800);

                if (window.__omniAmoledObserver) {
                    window.__omniAmoledObserver.disconnect();
                    window.__omniAmoledObserver = null;
                }
                var _omniOledTimer = null;
                window.__omniAmoledObserver = new MutationObserver(function() {
                    if (_omniOledTimer) return;
                    _omniOledTimer = setTimeout(function() {
                        _omniOledTimer = null;
                        let s = document.getElementById(id);
                        if (!s && document.head) {
                            try {
                                var ns = document.createElement('style');
                                ns.id = id;
                                ns.textContent = '$fullCss';
                                document.head.appendChild(ns);
                            } catch(e) {}
                        }
                        fixOledSurfaces();
                    }, 200);
                });
                try {
                    window.__omniAmoledObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
                } catch(e) {}
            }
        })();
    """.trimIndent()

    try {
        session.loadUri(js)
    } catch (_: Exception) {}
}

fun BrowserViewModel.applySiteStyleToActiveTab() {
    applySiteStyleToTab(activeTab)
}

fun BrowserViewModel.clearSiteStyleFromTab(targetTab: TabState? = null) {
    val tab = targetTab ?: activeTab ?: return
    val session = tab.session ?: return
    val clearJs = """
        javascript:(function() {
            try {
                const s = document.getElementById('omni-custom-site-style');
                if (s) s.remove();
                const m = document.getElementById('omni-custom-site-style-meta');
                if (m) m.remove();
                if (window.__omniAmoledObserver) {
                    window.__omniAmoledObserver.disconnect();
                    window.__omniAmoledObserver = null;
                }
                var oledEls = document.querySelectorAll('[__omniOled]');
                for (var i = 0; i < oledEls.length; i++) {
                    var el = oledEls[i];
                    el.removeAttribute('__omniOled');
                    el.style.backgroundColor = '';
                    el.style.background = '';
                    el.style.color = '';
                }
            } catch(e) {}
        })();
    """.trimIndent()
    try { session.loadUri(clearJs) } catch (_: Exception) {}
}

fun BrowserViewModel.updateSiteStyle(
    fontSize: Int,
    theme: String,
    lineSpacing: Float,
    letterSpacing: Float,
    fontFamily: String,
    appliedGlobally: Boolean,
    hideImages: Boolean,
    grayscale: Boolean,
    warmFilter: Boolean
) {
    siteStyleFontSize = fontSize
    siteStyleTheme = theme
    siteStyleLineSpacing = lineSpacing
    siteStyleLetterSpacing = letterSpacing
    siteStyleFontFamily = fontFamily
    siteStyleAppliedGlobally = appliedGlobally
    siteStyleHideImages = hideImages
    siteStyleGrayscale = grayscale
    siteStyleWarmFilter = warmFilter

    val context = appContext ?: return
    val sp = context.getSharedPreferences("omni_prefs", Context.MODE_PRIVATE)
    sp.edit().apply {
        putInt("site_style_font_size", fontSize)
        putString("site_style_theme", theme)
        putFloat("site_style_line_spacing", lineSpacing)
        putFloat("site_style_letter_spacing", letterSpacing)
        putString("site_style_font_family", fontFamily)
        putBoolean("site_style_applied_globally", appliedGlobally)
        putBoolean("site_style_hide_images", hideImages)
        putBoolean("site_style_grayscale", grayscale)
        putBoolean("site_style_warm_filter", warmFilter)
    }.apply()

    updateGeckoColorScheme()
    val hasCustomStyles = siteStyleTheme != "DEFAULT" || siteStyleFontSize != 100 || 
            siteStyleLineSpacing != 1.4f || siteStyleLetterSpacing != 0f || 
            siteStyleFontFamily != "inherit" || siteStyleHideImages || 
            siteStyleGrayscale || siteStyleWarmFilter
    if (hasCustomStyles) {
        applySiteStyleToActiveTab()
    } else {
        clearSiteStyleFromTab()
    }
}

fun BrowserViewModel.resetSiteStyle() {
    updateSiteStyle(
        fontSize = 100,
        theme = "DEFAULT",
        lineSpacing = 1.4f,
        letterSpacing = 0f,
        fontFamily = "inherit",
        appliedGlobally = false,
        hideImages = false,
        grayscale = false,
        warmFilter = false
    )
}
