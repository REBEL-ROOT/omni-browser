package com.rebelroot.omni.browser

import android.content.Context

/**
 * Applies optimized, non-flashing custom site styles and dark/AMOLED theme presets to GeckoView tabs.
 * Features a Direct DOM Pitch-Black AMOLED engine (#000000) that turns off OLED pixels on all websites,
 * preserves media element colors, sets Google dark search cookies, and handles dynamic DOM cards.
 */
fun BrowserViewModel.applySiteStyleToTab(targetTab: TabState? = null) {
    val tab = targetTab ?: activeTab ?: return
    val session = tab.session ?: return

    // Determine active theme preset
    val effectiveTheme = when {
        siteStyleTheme != "DEFAULT" -> siteStyleTheme
        isAmoledMode -> "OLED"
        else -> "DEFAULT"
    }

    val hasCustomStyles = effectiveTheme != "DEFAULT" || siteStyleFontSize != 100 || 
            siteStyleLineSpacing != 1.4f || siteStyleLetterSpacing != 0f || 
            siteStyleFontFamily != "inherit" || siteStyleHideImages || 
            siteStyleGrayscale || siteStyleWarmFilter

    if (!hasCustomStyles) {
        val clearJs = """
            javascript:(function() {
                const s = document.getElementById('omni-custom-site-style');
                if (s) s.remove();
                const m = document.getElementById('omni-custom-site-style-meta');
                if (m) m.remove();
            })();
        """.trimIndent()
        try { session.loadUri(clearJs) } catch (_: Exception) {}
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
                    --color-surface: #000000 !important;
                    --color-surface-1: #000000 !important;
                    --color-surface-2: #000000 !important;
                    --color-surface-3: #000000 !important;
                    --color-surface-4: #000000 !important;
                    --color-surface-5: #000000 !important;
                    --color-background: #000000 !important;
                    --color-primary-background: #000000 !important;
                    --color-secondary-background: #000000 !important;
                    --search-bg: #000000 !important;
                    --ub-bg-color: #000000 !important;
                    --theme-bg: #000000 !important;
                    --theme-surface: #000000 !important;
                    --theme-card: #000000 !important;
                    --surface-background: #000000 !important;
                    --header-bg: #000000 !important;
                    --footer-bg: #000000 !important;
                    --bg-primary: #000000 !important;
                    --bg-secondary: #000000 !important;
                    --bg-tertiary: #000000 !important;
                    --background-color: #000000 !important;
                    --yt-spec-base-background: #000000 !important;
                    --yt-spec-raised-background: #000000 !important;
                    --yt-spec-menu-background: #000000 !important;
                    --yt-spec-static-overlay-background-solid: #000000 !important;
                }
                :root, html, body, div, span, p,
                section, article, main, header, footer, nav, aside, dialog,
                form, table, tr, td, th, ul, ol, li, summary, details,
                [class*="card" i], [class*="container" i], [class*="wrapper" i], [class*="box" i],
                [class*="header" i], [class*="footer" i], [class*="nav" i], [class*="search" i], [class*="bar" i],
                [class*="sidebar" i], [class*="modal" i], [class*="dialog" i], [class*="popup" i], [class*="menu" i], [class*="panel" i], [class*="block" i],
                [role="main"], [role="article"], [role="navigation"], [role="region"], [role="dialog"], [role="search"],
                g-card, g-inner-card, g-header, g-flat-button, g-expandable-card, c-wiz,
                .g, .kp-blk, .xpd, .cUnBl, .MjjYud, .vdLWh, .wDYH0e, .K5qjJc, .g-blk, .sfbg, .e222eb, .minidiv, .appbar, #aria-main, #cnt, #rcnt, [data-async-context] {
                    background-color: #000000 !important;
                    background: #000000 !important;
                    color: #E2E8F0 !important;
                    border-color: #1A1A1A !important;
                    box-shadow: none !important;
                    color-scheme: dark !important;
                }
                p, h1, h2, h3, h4, h5, h6, label, strong, b, em, i, span, summary, dt, dd,
                [role="button"], [role="heading"], [role="option"], [role="treeitem"], [role="accordion"],
                [class*="question" i], [class*="title" i], [class*="heading" i], [class*="header" i], [class*="text" i] {
                    color: #E2E8F0 !important;
                }
                [role="button"] *, summary *, [class*="question" i] *, c-wiz * {
                    color: #E2E8F0 !important;
                }
                a, a * {
                    color: #60A5FA !important;
                }
                a:visited, a:visited * {
                    color: #A78BFA !important;
                }
                a:hover {
                    color: #93C5FD !important;
                }
                mark, .mark, [class*="highlight" i] {
                    background-color: transparent !important;
                    background: transparent !important;
                    color: #60A5FA !important;
                }
                img, video, canvas, svg, picture, iframe, g-img, g-img *,
                [style*="background-image"], [style*="background: url"],
                [class*="thumb" i], [class*="thumb" i] *,
                [class*="thumbnail" i], [class*="thumbnail" i] *,
                [class*="poster" i], [class*="poster" i] *,
                [class*="avatar" i], [class*="avatar" i] *,
                [class*="media" i], [class*="media" i] *,
                [class*="video" i], [class*="video" i] *,
                [class*="player" i], [class*="player" i] *,
                [class*="image" i], [class*="image" i] *,
                [class*="img" i], [class*="img" i] *,
                [class*="photo" i], [class*="photo" i] *,
                .v55W0e, .v55W0e *, .m652ud, .m652ud *, .Q83fi, .Q83fi *, .ctoDDe, .ctoDDe *, .Yk428, .Yk428 *, .vLffw, .vLffw *, .oJ3Ryb, .oJ3Ryb *, .B5e95, .B5e95 *, .rISNhc, .rISNhc *,
                figure, figure * {
                    background-color: transparent !important;
                    background: transparent !important;
                    opacity: 1 !important;
                    filter: none !important;
                }
                input, textarea, select, [contenteditable="true"], [role="combobox"], [role="searchbox"] {
                    background-color: #000000 !important;
                    background: #000000 !important;
                    color: #FFFFFF !important;
                    -webkit-text-fill-color: #FFFFFF !important;
                    border: none !important;
                    outline: none !important;
                    color-scheme: dark !important;
                }
                input::placeholder, textarea::placeholder {
                    color: #8E8E93 !important;
                    -webkit-text-fill-color: #8E8E93 !important;
                }
                button, [role="button"], [class*="chip" i], [class*="pill" i], [class*="badge" i] {
                    color-scheme: dark !important;
                }
                *::-webkit-scrollbar {
                    background-color: #000000 !important;
                    width: 6px !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #222222 !important;
                    border-radius: 4px !important;
                }
                ::selection {
                    background: #2563EB !important;
                    color: #FFFFFF !important;
                }
            """.trimIndent()
        }

        "DARK" -> {
            colorScheme = "dark"
            cssRules = """
                :root, html, body {
                    background-color: #121212 !important;
                    background: #121212 !important;
                    color: #E2E8F0 !important;
                    color-scheme: dark !important;
                }
                section, article, main, header, footer, nav, aside, dialog,
                form, table, tr, td, th, ul, ol, li,
                [class*="card" i], [class*="container" i], [class*="wrapper" i],
                [class*="sidebar" i], [class*="modal" i], [class*="dialog" i], [class*="popup" i], [class*="menu" i], [class*="panel" i],
                [role="main"], [role="article"], [role="navigation"], [role="region"], [role="dialog"],
                .g, .kp-blk, .xpd, .cUnBl, .MjjYud {
                    background-color: #1E1E1E !important;
                    color: #E2E8F0 !important;
                    border-color: #2D2D2D !important;
                }
                img, video, canvas, svg, picture, iframe, g-img, g-img *,
                [style*="background-image"], [style*="background: url"],
                [class*="thumb" i], [class*="thumb" i] *,
                [class*="thumbnail" i], [class*="thumbnail" i] *,
                [class*="poster" i], [class*="poster" i] *,
                [class*="avatar" i], [class*="avatar" i] *,
                [class*="media" i], [class*="media" i] *,
                [class*="video" i], [class*="video" i] *,
                [class*="player" i], [class*="player" i] *,
                [class*="image" i], [class*="image" i] *,
                [class*="img" i], [class*="img" i] *,
                [class*="photo" i], [class*="photo" i] *,
                .v55W0e, .v55W0e *, .m652ud, .m652ud *, .Q83fi, .Q83fi *, .ctoDDe, .ctoDDe *, .Yk428, .Yk428 *, .vLffw, .vLffw *, .oJ3Ryb, .oJ3Ryb *, .B5e95, .B5e95 *, .rISNhc, .rISNhc *,
                figure, figure * {
                    background-color: transparent !important;
                    background: transparent !important;
                    opacity: 1 !important;
                    filter: none !important;
                }
                p, h1, h2, h3, h4, h5, h6, label, strong, b, em, i {
                    color: #E2E8F0 !important;
                }
                a {
                    color: #60A5FA !important;
                }
                a:visited {
                    color: #A78BFA !important;
                }
                a:hover {
                    color: #93C5FD !important;
                }
                input, textarea, select, [contenteditable="true"], [role="combobox"], [role="searchbox"] {
                    background-color: #1E1E1E !important;
                    color: #FFFFFF !important;
                    -webkit-text-fill-color: #FFFFFF !important;
                    border: none !important;
                    outline: none !important;
                    color-scheme: dark !important;
                }
                input::placeholder, textarea::placeholder {
                    color: #8E8E93 !important;
                    -webkit-text-fill-color: #8E8E93 !important;
                }
                button, [role="button"], [class*="chip" i], [class*="pill" i], [class*="badge" i] {
                    color-scheme: dark !important;
                }
                *::-webkit-scrollbar {
                    background-color: #121212 !important;
                    width: 6px !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #333333 !important;
                    border-radius: 4px !important;
                }
                ::selection {
                    background: #2563EB !important;
                    color: #FFFFFF !important;
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

            if ('$colorScheme' === 'dark') {
                const fixMediaParents = function() {
                    try {
                        const media = document.querySelectorAll('img, video, g-img, canvas, [class*="thumb"], [class*="video"]');
                        for (let i = 0; i < media.length; i++) {
                            let p = media[i].parentElement;
                            let count = 0;
                            while (p && count < 3) {
                                if (p.style) {
                                    p.style.setProperty('background-color', 'transparent', 'important');
                                    p.style.setProperty('background', 'transparent', 'important');
                                }
                                p = p.parentElement;
                                count++;
                            }
                        }
                    } catch(e) {}
                };
                fixMediaParents();
                setTimeout(fixMediaParents, 300);
                setTimeout(fixMediaParents, 1000);

                if (!window.__omniAmoledObserver) {
                    window.__omniAmoledObserver = new MutationObserver(function() {
                        let s = document.getElementById(id);
                        if (!s && target && style) {
                            target.appendChild(style);
                        }
                        fixMediaParents();
                    });
                    try {
                        window.__omniAmoledObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
                    } catch(e) {}
                }
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

    applySiteStyleToActiveTab()
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
