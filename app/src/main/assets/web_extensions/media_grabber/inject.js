// inject.js — Omni Media Detection + Native Player Takeover
// Dual-mode: passively detects media URLs AND actively intercepts video playback
// to redirect to the native ExoPlayer-based Omni Player with download button.
(function() {
    'use strict';

    // =========================================================
    // Configuration — set from native side via postMessage
    // =========================================================
    let nativePlayerEnabled = true; // Default ON — intercept fullscreen + play
    let youtubeEnabled = false; // Default OFF — YouTube restricted unless the user enables it

    window.addEventListener('message', (event) => {
        if (event.source !== window) return;
        if (event.data?.type === 'OMNI_SET_NATIVE_PLAYER') {
            nativePlayerEnabled = !!event.data.enabled;
            youtubeEnabled = !!event.data.youtubeEnabled;
            console.log('[inject.js] Received OMNI_SET_NATIVE_PLAYER. nativePlayerEnabled set to:', nativePlayerEnabled, 'youtubeEnabled:', youtubeEnabled);
        } else if (event.data?.type === 'EVAL_JS') {
            const script = event.data.script;
            try {
                // Execute code in window scope
                const result = window.eval(script);
                console.log("> " + (result === undefined ? 'undefined' : String(result)));
            } catch (err) {
                console.error("Error: " + (err.message || err));
            }
        }
    });

    // =========================================================
    // URL validation helpers
    // =========================================================
    const detectedMediaUrls = new Set();
    const reportedNativeUrls = new Set(); // Prevent duplicate PLAY_IN_NATIVE

    function isDownloadableUrl(url) {
        if (!url) return false;
        if (url.startsWith('blob:') || url.startsWith('data:')) return false;
        return (url.startsWith('http://') || url.startsWith('https://'));
    }

    function isPlayableMediaUrl(url) {
        if (!url) return false;
        const lower = url.toLowerCase();
        
        // Exclude tracking, telemetry, and segments/chunks that cannot be played directly
        if (lower.includes('/segment') || 
            lower.includes('/fragment') || 
            lower.includes('.ts') || 
            lower.includes('.m4s') ||
            lower.includes('analytics') ||
            lower.includes('telemetry')
        ) {
            return false;
        }
        
        return lower.includes('.m3u8') ||
               lower.includes('.mpd')  ||
               lower.includes('.mp4')  ||
               lower.includes('.webm') ||
               lower.includes('.mkv')  ||
               lower.includes('/hls/') ||
               lower.includes('/dash/') ||
               lower.includes('mpegurl') ||
               lower.includes('googlevideo.com') ||
               lower.includes('videoplayback');
    }

    function isLikelyAdOrBanner(video) {
        if (!video) return true;
        
        // Loop + muted is almost always a background banner
        if (video.loop && video.muted) return true;
        
        // Check size: very small videos are ads/tracking/anchors
        const width = video.offsetWidth || video.clientWidth || 0;
        const height = video.offsetHeight || video.clientHeight || 0;
        if (width > 0 && width < 150) return true;
        if (height > 0 && height < 100) return true;
        
        return false;
    }

    // Post grabbed media URL back to the content script (passive — never blocks)
    function reportMedia(url, mimeType) {
        if (!isDownloadableUrl(url)) return;
        if (isPlayableMediaUrl(url)) {
            detectedMediaUrls.add(url);
        }
        window.postMessage({
            type: 'MSE_MEDIA_STREAM_GRABBED',
            url: url,
            mimeType: mimeType || 'video/mp4'
        }, '*');
    }

    // =========================================================
    // 0. Intercept console logs (relay to native DevTools)
    // =========================================================
    const consoleMethods = ['log', 'warn', 'error', 'info'];
    consoleMethods.forEach(method => {
        const original = console[method];
        console[method] = function(...args) {
            try {
                const message = args.map(a => {
                    if (typeof a === 'object') { try { return JSON.stringify(a); } catch(e) { return String(a); } }
                    return String(a);
                }).join(' ');
                window.postMessage({ type: 'OMNI_CONSOLE_LOG', level: method.toUpperCase(), message }, '*');
            } catch (e) { /* ignore */ }
            return original.apply(console, args);
        };
    });

    // =========================================================
    // 1. Passive fetch intercept — detect HLS/DASH manifests
    // =========================================================
    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
        const requestUrl = typeof args[0] === 'string' ? args[0] : (args[0]?.url || '');
        if (isPlayableMediaUrl(requestUrl)) {
            reportMedia(requestUrl, getMimeType(requestUrl));
        }
        return originalFetch.apply(this, args);
    };

    // =========================================================
    // 2. Passive XHR intercept — detect HLS/DASH manifests
    // =========================================================
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, ...args) {
        if (typeof url === 'string' && isPlayableMediaUrl(url)) {
            reportMedia(url, getMimeType(url));
        }
        return originalXhrOpen.call(this, method, url, ...args);
    };

    // =========================================================
    // 3. Passive src setter intercept — detect direct video srcs
    // =========================================================
    try {
        const srcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (srcDescriptor && srcDescriptor.set) {
            const originalSrcSet = srcDescriptor.set;
            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                ...srcDescriptor,
                set: function(value) {
                    if (isDownloadableUrl(value)) {
                        reportMedia(value, getMimeType(value));
                    }
                    return originalSrcSet.call(this, value);
                }
            });
        }
    } catch(e) { /* ignore */ }

    // =========================================================
    // 4. Listen for network manifests forwarded from background.js
    // =========================================================
    window.addEventListener('message', (event) => {
        if (event.source !== window) return;
        if (event.data?.type === 'ADD_DETECTED_MANIFEST') {
            const url = event.data.url;
            if (url && isDownloadableUrl(url) && isPlayableMediaUrl(url)) {
                detectedMediaUrls.add(url);
                reportMedia(url, getMimeType(url));
            }
        }
    });

    // =========================================================
    // 5. VIDEO_STATE_CHANGE — report play/pause to native so the
    //    fullscreen download overlay auto-fades while playing and
    //    stays visible while paused.
    //    Completely passive — never modifies playback.
    // =========================================================
    function reportVideoState(isPlaying) {
        window.postMessage({ type: 'VIDEO_STATE_CHANGE', isPlaying }, '*');
    }

    window.addEventListener('playing', e => { if (e.target?.tagName === 'VIDEO') reportVideoState(true);  }, true);
    window.addEventListener('pause',   e => { if (e.target?.tagName === 'VIDEO') reportVideoState(false); }, true);
    window.addEventListener('ended',   e => { if (e.target?.tagName === 'VIDEO') reportVideoState(false); }, true);

    // Also report the video URL when it starts playing (so the FAB can show even
    // if the background.js webRequest hook missed it — e.g. blob-backed MSE streams).
    window.addEventListener('play', (event) => {
        try {
            const video = event.target;
            if (!video || video.tagName !== 'VIDEO') return;
            reportVideoState(true);
            const src = video.currentSrc || video.src;
            if (isDownloadableUrl(src)) {
                reportMedia(src, getMimeType(src));
            } else if (src && src.startsWith('blob:')) {
                // For MSE blob sources, report the last known manifest instead
                const urls = Array.from(detectedMediaUrls);
                if (urls.length > 0) {
                    reportMedia(urls[urls.length - 1], getMimeType(urls[urls.length - 1]));
                }
            }
        } catch(e) { /* ignore */ }
    }, true);

    // =========================================================
    // 6. NATIVE PLAYER TAKEOVER — Aloha-style
    //    Intercepts requestFullscreen() on video elements and
    //    redirects playback to the native Omni Player.
    // =========================================================

    /**
     * Get the best downloadable URL for a video element.
     * Returns null if no downloadable URL can be found (e.g. DRM blob).
     */
    function getVideoUrl(video) {
        if (!video) return null;

        // 1. Try direct src
        const directSrc = video.currentSrc || video.src;
        if (isDownloadableUrl(directSrc)) return directSrc;

        // 2. Try <source> children
        const sources = video.querySelectorAll('source');
        for (const source of sources) {
            if (isDownloadableUrl(source.src)) return source.src;
        }

        // 2b. Try video element attributes (like data-src, data-video-src, data-config, etc.)
        for (const attr of video.attributes) {
            const val = attr.value;
            if (isDownloadableUrl(val) && (val.includes('.mp4') || val.includes('.m3u8') || val.includes('.webm') || val.includes('.mpd'))) {
                return val;
            }
        }

        // 3. Fall back to last detected manifest (for MSE/blob-backed players)
        if (detectedMediaUrls.size > 0) {
            const urls = Array.from(detectedMediaUrls);
            // Prioritize actual HLS/DASH manifest streams for MSE/blob-backed video elements
            if (directSrc && directSrc.startsWith('blob:')) {
                const manifests = urls.filter(u => u.includes('.m3u8') || u.includes('.mpd') || u.includes('/hls/') || u.includes('/dash/') || u.includes('mpegurl'));
                if (manifests.length > 0) {
                    return manifests[manifests.length - 1];
                }
            }
            return urls[urls.length - 1];
        }

        return null;
    }

    /**
     * Request the native app to play a video in the Omni Player.
     * Pauses the in-page video and sends the URL to native.
     */
    function requestNativePlayback(video, videoUrl) {
        console.log('[inject.js] requestNativePlayback called for videoUrl:', videoUrl);
        if (!videoUrl) {
            console.warn('[inject.js] requestNativePlayback: videoUrl is empty!');
            return false;
        }
        if (reportedNativeUrls.has(videoUrl)) {
            console.log('[inject.js] requestNativePlayback: videoUrl already reported recently, skipping to prevent loop.');
            return false;
        }
        reportedNativeUrls.add(videoUrl);

        // Clear after 2s so re-tapping fullscreen works again
        setTimeout(() => {
            reportedNativeUrls.delete(videoUrl);
            console.log('[inject.js] Cleared reported videoUrl from rate-limit cache:', videoUrl);
        }, 2000);

        // Pause the in-page player
        try { 
            video.pause(); 
            console.log('[inject.js] Successfully paused web video element.');
        } catch(e) { 
            console.error('[inject.js] Failed to pause web video element:', e); 
        }

        // Exit any in-page fullscreen
        try {
            if (document.fullscreenElement) {
                document.exitFullscreen();
                console.log('[inject.js] Exited browser-level fullscreen.');
            }
        } catch(e) { 
            console.error('[inject.js] Failed to exit browser fullscreen:', e); 
        }

        console.log('[inject.js] Posting PLAY_IN_NATIVE message to window context...');
        // Send to native
        window.postMessage({
            type: 'PLAY_IN_NATIVE',
            url: videoUrl,
            pageUrl: window.location.href,
            mimeType: getMimeType(videoUrl)
        }, '*');

        return true;
    }

    // (play() hijack removed to allow normal browser in-page video playback, ad skipping, and prevent loops on back navigation)

    const isYouTube = window.location.hostname.includes('youtube.com') || window.location.hostname.includes('youtu.be');

    // --- Intercept requestFullscreen() on video elements and their containers ---
    const originalRequestFullscreen = Element.prototype.requestFullscreen;
    Element.prototype.requestFullscreen = function(...args) {
        if (nativePlayerEnabled && (!isYouTube || youtubeEnabled)) {
            // Check if this element IS a video or CONTAINS a video
            const video = (this.tagName === 'VIDEO') ? this : this.querySelector('video');
            if (video && !isLikelyAdOrBanner(video)) {
                if (video.duration && video.duration > 0 && video.duration < 15) {
                    console.log('[inject.js] Ignoring video due to short duration (< 15s):', video.duration);
                } else {
                    const videoUrl = getVideoUrl(video);
                    if (videoUrl) {
                        // Hijack: open in native player instead of fullscreen
                        requestNativePlayback(video, videoUrl);
                        return Promise.resolve(); // Prevent fullscreen
                    }
                }
            }
        }
        // Not a video or native player disabled — allow normal fullscreen
        return originalRequestFullscreen.apply(this, args);
    };

    // Also intercept webkit-prefixed fullscreen (older Android WebViews)
    if (Element.prototype.webkitRequestFullscreen) {
        const originalWebkit = Element.prototype.webkitRequestFullscreen;
        Element.prototype.webkitRequestFullscreen = function(...args) {
            if (nativePlayerEnabled && (!isYouTube || youtubeEnabled)) {
                const video = (this.tagName === 'VIDEO') ? this : this.querySelector('video');
                if (video && !isLikelyAdOrBanner(video)) {
                    if (video.duration && video.duration > 0 && video.duration < 15) {
                        console.log('[inject.js] Ignoring webkit video due to short duration (< 15s):', video.duration);
                    } else {
                        const videoUrl = getVideoUrl(video);
                        if (videoUrl) {
                            requestNativePlayback(video, videoUrl);
                            return;
                        }
                    }
                }
            }
            return originalWebkit.apply(this, args);
        };
    }

    // Also intercept webkitRequestFullScreen (camelCase variant)
    if (Element.prototype.webkitRequestFullScreen) {
        const originalWebkitAlt = Element.prototype.webkitRequestFullScreen;
        Element.prototype.webkitRequestFullScreen = function(...args) {
            if (nativePlayerEnabled && (!isYouTube || youtubeEnabled)) {
                const video = (this.tagName === 'VIDEO') ? this : this.querySelector('video');
                if (video && !isLikelyAdOrBanner(video)) {
                    if (video.duration && video.duration > 0 && video.duration < 15) {
                        console.log('[inject.js] Ignoring webkit video alt due to short duration (< 15s):', video.duration);
                    } else {
                        const videoUrl = getVideoUrl(video);
                        if (videoUrl) {
                            requestNativePlayback(video, videoUrl);
                            return;
                        }
                    }
                }
            }
            return originalWebkitAlt.apply(this, args);
        };
    }

    // =========================================================
    // 7. Periodic DOM scanner — catches dynamically-rendered videos
    //    Completely passive — never touches playback state.
    // =========================================================
    function scanVideos() {
        try {
            let anyPlaying = false;
            document.querySelectorAll('video').forEach(video => {
                const src = video.currentSrc || video.src;
                if (isDownloadableUrl(src)) reportMedia(src, getMimeType(src));
                video.querySelectorAll('source').forEach(s => {
                    if (isDownloadableUrl(s.src)) reportMedia(s.src, getMimeType(s.src));
                });
                
                // If a video is currently playing, report it to the native app!
                if (video.currentTime > 0 && !video.paused && !video.ended && video.readyState > 2) {
                    anyPlaying = true;
                }
            });
            if (anyPlaying) {
                reportVideoState(true);
            }
        } catch(e) { /* ignore */ }
    }

    setInterval(scanVideos, 2000);
    window.addEventListener('DOMContentLoaded', scanVideos);

    // =========================================================
    // Helper functions
    // =========================================================
    function getMimeType(url) {
        const lower = url.toLowerCase();
        if (lower.includes('m3u8'))  return 'application/x-mpegURL';
        if (lower.includes('mpd'))   return 'application/dash+xml';
        if (lower.includes('mime=video/webm') || lower.includes('mime=audio/webm') || lower.includes('.webm')) return 'video/webm';
        if (lower.includes('mime=video/mp4') || lower.includes('mime=audio/mp4') || lower.includes('.mp4')) return 'video/mp4';
        if (lower.includes('mime=audio/')) return 'audio/mpeg';
        return 'video/mp4';
    }
})();
