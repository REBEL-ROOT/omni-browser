(function() {
    'use strict';

    // 1. Signal dark color-scheme preference to document
    try {
        var docEl = document.documentElement || document.getElementsByTagName('html')[0];
        if (docEl) {
            docEl.style.setProperty('color-scheme', 'dark');
        }
        if (location.hostname.indexOf('google.') !== -1) {
            try {
                var host = location.hostname;
                var domain = host.substring(host.indexOf('google.'));
                document.cookie = "PREF=f6=400; path=/; domain=" + domain;
            } catch(e) {}
        }
    } catch(e) {}

    // 2. Check if the site natively supports or rendered in Dark Theme
    function hasNativeDarkTheme() {
        try {
            // Check meta color-scheme
            var metaColorScheme = document.querySelector('meta[name="color-scheme"]');
            if (metaColorScheme && metaColorScheme.content && metaColorScheme.content.indexOf('dark') !== -1) {
                return true;
            }

            // Check stylesheet rules for prefers-color-scheme
            try {
                var sheets = document.styleSheets;
                for (var i = 0; i < sheets.length; i++) {
                    try {
                        var rules = sheets[i].cssRules || sheets[i].rules;
                        if (rules) {
                            for (var j = 0; j < rules.length; j++) {
                                if (rules[j].type === CSSRule.MEDIA_RULE && rules[j].conditionText && rules[j].conditionText.indexOf('prefers-color-scheme') !== -1) {
                                    return true;
                                }
                            }
                        }
                    } catch(e) {} // Cross-origin stylesheets
                }
            } catch(e) {}

            // Check computed background color of body or html
            var body = document.body || document.documentElement;
            if (body) {
                var bg = window.getComputedStyle(body).backgroundColor;
                if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
                    var rgb = bg.match(/\d+/g);
                    if (rgb && rgb.length >= 3) {
                        var r = parseInt(rgb[0], 10);
                        var g = parseInt(rgb[1], 10);
                        var b = parseInt(rgb[2], 10);
                        // Perceived brightness formula (0-255). If < 110, page is already dark.
                        var brightness = (r * 299 + g * 587 + b * 114) / 1000;
                        if (brightness < 110) {
                            return true;
                        }
                    }
                }
            }
        } catch(e) {}
        return false;
    }

    // 3. Apply force-dark CSS class ONLY if site lacks native dark theme
    function evaluateForceDark() {
        var target = document.documentElement || document.body;
        if (!target) return;
        
        if (hasNativeDarkTheme()) {
            // Site has native dark mode — keep original styling
            target.classList.remove('omni-force-dark-active');
        } else {
            // Site is light-only — apply forced dark override
            target.classList.add('omni-force-dark-active');
        }
    }

    evaluateForceDark();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', evaluateForceDark, { once: true });
    }

    window.addEventListener('load', evaluateForceDark, { once: true });
})();
