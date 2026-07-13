// override.js
(function() {
    'use strict';

    const events = [
        'copy', 'cut', 'paste', 'selectstart', 'mousedown',
        'mouseup', 'contextmenu', 'dragstart', 'keydown'
    ];

    // Intercept with capturing phase to bypass inline handlers
    events.forEach(eventName => {
        document.addEventListener(eventName, function(e) {
            e.stopPropagation();
        }, true);
    });

    // Clear document event properties
    document.onselectstart = null;
    document.onmousedown = null;
    document.oncopy = null;
    document.oncut = null;
    document.onpaste = null;
    document.oncontextmenu = null;

    if (document.body) {
        document.body.onselectstart = null;
        document.body.onmousedown = null;
        document.body.style.userSelect = 'text';
        document.body.style.webkitUserSelect = 'text';
    }

    console.log('[OmniCopy] Universal selection forced.');
})();
