(function() {
    'use strict';

    // 1. Signal dark color-scheme preference at document_start
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
            var metaColorScheme = document.querySelector('meta[name="color-scheme"]');
            if (metaColorScheme && metaColorScheme.content && metaColorScheme.content.indexOf('dark') !== -1) {
                return true;
            }

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
                    } catch(e) {}
                }
            } catch(e) {}

            var body = document.body || document.documentElement;
            if (body) {
                var bg = window.getComputedStyle(body).backgroundColor;
                if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
                    var rgb = bg.match(/\d+/g);
                    if (rgb && rgb.length >= 3) {
                        var r = parseInt(rgb[0], 10);
                        var g = parseInt(rgb[1], 10);
                        var b = parseInt(rgb[2], 10);
                        var a = rgb.length >= 4 ? parseFloat(rgb[3]) : 1;
                        if (a > 0.1) {
                            var brightness = (r * 299 + g * 587 + b * 114) / 1000;
                            if (brightness < 110) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch(e) {}
        return false;
    }

    function markVideoPlayers() {
        try {
            var videos = document.querySelectorAll('video');
            for (var i = 0; i < videos.length; i++) {
                var parent = videos[i].parentElement;
                var depth = 0;
                while (parent && depth < 5) {
                    if (parent.tagName === 'BODY' || parent.tagName === 'HTML') break;
                    if (!parent.classList.contains('omni-video-player-active')) {
                        parent.classList.add('omni-video-player-active');
                    }
                    parent = parent.parentElement;
                    depth++;
                }
            }
        } catch(e) {}
    }

    var observer = null;
    function setupObserver() {
        if (!document.documentElement || !document.documentElement.classList.contains('omni-force-dark-active')) {
            if (observer) {
                observer.disconnect();
                observer = null;
            }
            return;
        }
        if (observer) return;
        
        var timer = null;
        observer = new MutationObserver(function() {
            if (timer) return;
            timer = setTimeout(function() {
                timer = null;
                markVideoPlayers();
            }, 250);
        });
        try {
            observer.observe(document.documentElement, { childList: true, subtree: true });
        } catch(e) {}
    }

    function evaluateForceDark() {
        var target = document.documentElement || document.body;
        if (!target) return;
        
        if (hasNativeDarkTheme()) {
            target.classList.remove('omni-force-dark-active');
            if (observer) {
                observer.disconnect();
                observer = null;
            }
        } else {
            target.classList.add('omni-force-dark-active');
            markVideoPlayers();
            setupObserver();
        }
    }

    evaluateForceDark();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', evaluateForceDark, { once: true });
    }
    window.addEventListener('load', evaluateForceDark, { once: true });
})();
