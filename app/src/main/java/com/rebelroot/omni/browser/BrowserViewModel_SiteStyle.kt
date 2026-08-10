package com.rebelroot.omni.browser

import android.content.Context

/**
 * Applies optimized, non-flashing custom site styles and dark/AMOLED theme presets to GeckoView tabs.
 * Based on modern open-source smart-inversion engine techniques to preserve natural colors on images,
 * videos, and media elements while enforcing early pre-document DOM styling.
 */
fun BrowserViewModel.applySiteStyleToTab(targetTab: TabState? = null) {
    val tab = targetTab ?: activeTab ?: return
    val session = tab.session ?: return

    // Determine active theme preset
    val effectiveTheme = when {
        siteStyleTheme != "DEFAULT" -> siteStyleTheme
        siteStyleAppliedGlobally && isAmoledMode -> "OLED"
        siteStyleAppliedGlobally && (isDarkThemeEnabled || forceDarkWebsites) -> "DARK"
        forceDarkWebsites -> if (isAmoledMode) "OLED" else "DARK"
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
        "DARK" -> {
            colorScheme = "dark"
            cssRules = """
                :root, html {
                    filter: invert(90%) hue-rotate(180deg) brightness(100%) contrast(100%) !important;
                    color-scheme: dark !important;
                    background-color: #121212 !important;
                    transition: filter 0.15s ease !important;
                }
                iframe, img, image, video, canvas, svg image, picture,
                [style*="background-image"], [style*="background:"] {
                    filter: invert(100%) hue-rotate(180deg) brightness(105%) contrast(105%) !important;
                }
                iframe img, iframe video, iframe canvas, iframe picture, iframe [style*="background-image"] {
                    filter: none !important;
                }
                ::selection {
                    background: #338fff !important;
                    color: #ffffff !important;
                }
                input, textarea, select, button {
                    color-scheme: dark !important;
                }
                *::-webkit-scrollbar {
                    background-color: #1a1a1a !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #333333 !important;
                    border-radius: 4px !important;
                }
            """.trimIndent()
        }

        "OLED" -> {
            colorScheme = "dark"
            cssRules = """
                :root, html {
                    filter: invert(95%) hue-rotate(180deg) brightness(105%) contrast(105%) !important;
                    color-scheme: dark !important;
                    background-color: #000000 !important;
                    background: #000000 !important;
                    transition: filter 0.15s ease !important;
                }
                body, main, article, section, header, footer, nav, dialog, div[role="main"] {
                    background-color: #000000 !important;
                }
                iframe, img, image, video, canvas, svg image, picture,
                [style*="background-image"], [style*="background:"] {
                    filter: invert(100%) hue-rotate(180deg) brightness(105%) contrast(105%) !important;
                }
                iframe img, iframe video, iframe canvas, iframe picture, iframe [style*="background-image"] {
                    filter: none !important;
                }
                ::selection {
                    background: #444444 !important;
                    color: #ffffff !important;
                }
                input, textarea, select, button {
                    color-scheme: dark !important;
                }
                *::-webkit-scrollbar {
                    background-color: #000000 !important;
                }
                *::-webkit-scrollbar-thumb {
                    background-color: #222222 !important;
                    border-radius: 4px !important;
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
