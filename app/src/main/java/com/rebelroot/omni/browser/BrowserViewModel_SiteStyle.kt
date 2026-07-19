package com.rebelroot.omni.browser

import android.content.Context

fun BrowserViewModel.applySiteStyleToActiveTab() {
    val session = geckoSession ?: return
    val hasCustomStyles = siteStyleTheme != "DEFAULT" || siteStyleFontSize != 100 || 
            siteStyleLineSpacing != 1.4f || siteStyleLetterSpacing != 0f || 
            siteStyleFontFamily != "inherit" || siteStyleHideImages || 
            siteStyleGrayscale || siteStyleWarmFilter

    val bgValue = when (siteStyleTheme) {
        "DARK" -> "#0B131E"
        "SEPIA" -> "#F4ECD8"
        "OLED" -> "#000000"
        "FOREST" -> "#E6F0E6"
        else -> null
    }
    val textValue = when (siteStyleTheme) {
        "DARK" -> "#E2E8F0"
        "SEPIA" -> "#5C4033"
        "OLED" -> "#E5E7EB"
        "FOREST" -> "#1E3F20"
        else -> null
    }
    
    val fontCss = if (siteStyleFontFamily != "inherit") "font-family: ${siteStyleFontFamily} !important;" else ""
    val bgCss = if (bgValue != null) "background-color: ${bgValue} !important; background: ${bgValue} !important;" else ""
    val textCss = if (textValue != null) "color: ${textValue} !important;" else ""
    val sizeCss = """
        font-size: ${siteStyleFontSize}% !important;
        -webkit-text-size-adjust: ${siteStyleFontSize}% !important;
        -moz-text-size-adjust: ${siteStyleFontSize}% !important;
        text-size-adjust: ${siteStyleFontSize}% !important;
    """.trimIndent()
    val lineSpacingCss = "line-height: ${siteStyleLineSpacing} !important;"
    val letterSpacingCss = "letter-spacing: ${siteStyleLetterSpacing}px !important;"

    var cssRules = ""
    if (hasCustomStyles) {
        val mainRules = """
            html, body, p, span, div, h1, h2, h3, h4, h5, h6, li, a, section, article {
                $bgCss
                $textCss
                $fontCss
                $lineSpacingCss
                $letterSpacingCss
            }
        """.trimIndent()

        val sizeRules = """
            html, body {
                $sizeCss
            }
        """.trimIndent()
        
        val hideImagesRules = if (siteStyleHideImages) {
            """
            img, picture, figure, [style*="background-image"] {
                display: none !important;
            }
            """.trimIndent()
        } else ""

        val grayscaleRules = if (siteStyleGrayscale) {
            """
            html {
                filter: grayscale(100%) !important;
            }
            """.trimIndent()
        } else ""

        val warmFilterRules = if (siteStyleWarmFilter) {
            """
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
            """.trimIndent()
        } else ""

        cssRules = (mainRules + "\n" + sizeRules + "\n" + hideImagesRules + "\n" + grayscaleRules + "\n" + warmFilterRules)
            .trimIndent().replace("\n", " ").replace("'", "\\'")
    }

    val js = """
        javascript:(function() {
            const styleId = 'omni-custom-site-style';
            let styleEl = document.getElementById(styleId);
            if ('$cssRules' === '') {
                if (styleEl) {
                    styleEl.remove();
                }
            } else {
                if (!styleEl) {
                    styleEl = document.createElement('style');
                    styleEl.id = styleId;
                    document.head.appendChild(styleEl);
                }
                styleEl.innerHTML = '$cssRules';
            }
        })();
    """.trimIndent()
    
    session.loadUri(js)
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
