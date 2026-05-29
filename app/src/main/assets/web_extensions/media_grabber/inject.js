// inject.js
(function() {
    'use strict';

    console.log('[OmniInject] Aggressive media hook injected.');

    // Helper to post grabbed media urls back to content script
    function reportMedia(url, mimeType) {
        if (!url || url.startsWith('blob:') && !mimeType) return;
        window.postMessage({
            type: 'MSE_MEDIA_STREAM_GRABBED',
            url: url,
            mimeType: mimeType || 'video/mp4'
        }, '*');
    }

    // 1. Intercept native fetch requests for HLS/DASH manifest files
    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
        const requestUrl = typeof args[0] === 'string' ? args[0] : (args[0]?.url || '');
        if (isMediaManifest(requestUrl)) {
            reportMedia(requestUrl, getMimeType(requestUrl));
        }
        return originalFetch.apply(this, args);
    };

    // 2. Intercept XMLHttpRequest for manifest URLs
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, ...args) {
        if (typeof url === 'string' && isMediaManifest(url)) {
            reportMedia(url, getMimeType(url));
        }
        return originalXhrOpen.call(this, method, url, ...args);
    };

    // 3. Hook HTMLMediaElement source modifications
    const originalPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function() {
        const src = this.currentSrc || this.src;
        if (src) {
            reportMedia(src, getMimeType(src));
        }
        return originalPlay.apply(this, arguments);
    };

    // 4. Hook SourceBuffer prototype to intercept raw chunk streams (MSE)
    if (window.SourceBuffer) {
        const originalAppendBuffer = SourceBuffer.prototype.appendBuffer;
        SourceBuffer.prototype.appendBuffer = function(buffer) {
            // Buffer contains raw ArrayBuffer chunk segments.
            // On sites like YouTube, the main video tag will have a blob: URL.
            // We find any associated video elements and report their currentSrc blob URL!
            const videoElements = document.querySelectorAll('video');
            videoElements.forEach(video => {
                if (video.currentSrc && video.currentSrc.startsWith('blob:')) {
                    reportMedia(video.currentSrc, 'video/mp4');
                }
            });
            return originalAppendBuffer.apply(this, arguments);
        }
    }

    // Helper functions
    function isMediaManifest(url) {
        const lower = url.toLowerCase();
        return lower.includes('.m3u8') || 
               lower.includes('.mpd') || 
               lower.includes('/hls/') || 
               lower.includes('/dash/') || 
               lower.includes('mpegurl');
    }

    function getMimeType(url) {
        const lower = url.toLowerCase();
        return when {
            lower.includes('m3u8') -> 'application/x-mpegURL'
            lower.includes('mpd') -> 'application/dash+xml'
            lower.includes('.mp4') -> 'video/mp4'
            lower.includes('.webm') -> 'video/webm'
            else -> 'video/mp4'
        }
    }
})();
