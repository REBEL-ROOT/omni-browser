(function() {
    'use strict';

    function setDarkMode() {
        try {
            var docEl = document.documentElement || document.getElementsByTagName('html')[0];
            if (docEl) {
                docEl.style.setProperty('color-scheme', 'dark', 'important');
            }

            if (location.hostname.indexOf('google.') !== -1) {
                try {
                    var host = location.hostname;
                    var domain = host.substring(host.indexOf('google.'));
                    document.cookie = "PREF=f6=400; path=/; domain=" + domain;
                } catch(e) {}
            }
        } catch(e) {}
    }

    setDarkMode();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setDarkMode, { once: true });
    } else {
        setDarkMode();
    }

    try {
        var observer = new MutationObserver(function() {
            var docEl = document.documentElement;
            if (docEl && !docEl.style.getPropertyValue('color-scheme')) {
                docEl.style.setProperty('color-scheme', 'dark', 'important');
            }
        });
        observer.observe(document.documentElement || document.body, {
            childList: true,
            subtree: true
        });
    } catch(e) {}
})();
