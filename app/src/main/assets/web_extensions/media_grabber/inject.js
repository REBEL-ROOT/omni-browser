// inject.js — Omni Media Detection + Native Player Takeover
// Dual-mode: passively detects media URLs AND actively intercepts video playback
// to redirect to the native ExoPlayer-based Omni Player with download button.
(function() {
    'use strict';

    // =========================================================
    // Configuration — set from native side via postMessage
    // =========================================================
    let nativePlayerEnabled = true; // Default ON — intercept fullscreen + play
    let youtubeEnabled = false; // Default OFF — play on original YouTube player by default

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
    let isYouTube = window.location.hostname.includes('youtube.com') || window.location.hostname.includes('youtu.be');

    // Reset YouTube dedup set on SPA navigation (YouTube changes URL without full reload)
    let lastYtHref = window.location.href;
    setInterval(() => {
        if (window.location.href !== lastYtHref) {
            lastYtHref = window.location.href;
            // Clear dedup caches so new video page can trigger native player
            if (window._omniLaunchedYtIds) window._omniLaunchedYtIds.clear();
            reportedNativeUrls.clear();
            detectedMediaUrls.clear();
            document.querySelectorAll('video').forEach(video => {
                delete video._omniIntercepted;
            });
            console.log('[inject.js] SPA navigation detected, cleared dedup caches and video intercept flags for:', window.location.href);
        }
    }, 500);

    // Also clear intercept flag when a video element loads a new source
    window.addEventListener('loadstart', (e) => {
        if (e.target?.tagName === 'VIDEO') {
            delete e.target._omniIntercepted;
            console.log('[inject.js] Video loadstart detected, cleared _omniIntercepted flag.');
        }
    }, true);

    function getYoutubeIdFromUrl(url) {
        if (!url) return null;
        if (url.includes('youtube.com/watch')) {
            const match = url.match(/[?&]v=([^&#]+)/);
            return match ? match[1] : null;
        }
        if (url.includes('youtu.be/')) {
            return url.split('youtu.be/')[1]?.split('?')[0]?.split('/')[0] || null;
        }
        if (url.includes('youtube.com/embed/')) {
            return url.split('youtube.com/embed/')[1]?.split('?')[0]?.split('/')[0] || null;
        }
        if (url.includes('youtube.com/shorts/')) {
            return url.split('youtube.com/shorts/')[1]?.split('?')[0]?.split('/')[0] || null;
        }
        return null;
    }

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

    // Listen to focus/click events on input elements to trigger native password manager/autofill bottom sheet
    document.addEventListener('focusin', (e) => {
        try {
            const input = e.target;
            if (!input || input.tagName !== 'INPUT') return;
            const type = (input.getAttribute('type') || 'text').toLowerCase();
            
            // Check if it's a potential login field (text, email, password)
            if (['text', 'email', 'password'].indexOf(type) !== -1) {
                // If it's already filled, don't trigger autofill
                if (input.value && input.value.trim().length > 0) {
                    console.log('[inject.js] Input focused but already has value, skipping autofill trigger.');
                    return;
                }
                
                // Check if there is a password field on the page/form (indicates a login page)
                const hasPasswordField = document.querySelector('input[type="password"]') !== null;
                if (hasPasswordField || type === 'password' || input.name.includes('user') || input.name.includes('login') || input.id.includes('user') || input.id.includes('login') || input.name.includes('email') || input.id.includes('email')) {
                    console.log('[inject.js] Login input focused, notifying native app...');
                    window.postMessage({ type: 'OMNI_FOCUS_LOGIN_INPUT' }, '*');
                }
            }
        } catch (e) { /* ignore */ }
    }, true);

    // Also report the video URL when it starts playing (so the banner shows even
    // if the background.js webRequest hook missed it — e.g. blob-backed MSE streams).
    window.addEventListener('play', (event) => {
        try {
            const video = event.target;
            if (!video || video.tagName !== 'VIDEO') return;
            reportVideoState(true);

            // Guard against infinite loop when returning from native player
            if (video._omniIntercepted) {
                console.log('[inject.js] Video already intercepted recently, skipping native takeover.');
                return;
            }

            if (nativePlayerEnabled) {
                // Native player takeover for YouTube (gated on youtubeEnabled from settings)
                if (isYouTube && youtubeEnabled) {
                    const ytId = getYoutubeIdFromUrl(window.location.href);
                    if (ytId) {
                        window._omniLaunchedYtIds = window._omniLaunchedYtIds || new Set();
                        if (!window._omniLaunchedYtIds.has(ytId)) {
                            window._omniLaunchedYtIds.add(ytId);
                            video._omniIntercepted = true;
                            try { video.pause(); } catch(e) {}
                            requestNativePlayback(video, window.location.href);
                            return;
                        }
                    }
                } else if (!isYouTube && !isLikelyAdOrBanner(video)) {
                    // Native player takeover for other sites (direct ExoPlayer open on play event)
                    if (video.duration && video.duration > 0 && video.duration < 10) {
                        console.log('[inject.js] Ignoring video due to short duration (< 10s):', video.duration);
                    } else {
                        const videoUrl = getVideoUrl(video);
                        if (videoUrl) {
                            video._omniIntercepted = true;
                            try { video.pause(); } catch(e) {}
                            requestNativePlayback(video, videoUrl);
                            return;
                        }
                    }
                }
            }

            // Always report current video src for the banner (all sites, not just YouTube)
            const src = video.currentSrc || video.src;
            if (isDownloadableUrl(src)) {
                reportMedia(src, getMimeType(src));
            } else if (src && src.startsWith('blob:')) {
                // For MSE blob sources, report the last known manifest instead
                const urls = Array.from(detectedMediaUrls);
                if (urls.length > 0) {
                    reportMedia(urls[0], getMimeType(urls[0]));
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

        // 3. Fall back to first detected manifest (prioritizing master manifest for MSE/blob-backed players)
        if (detectedMediaUrls.size > 0) {
            const urls = Array.from(detectedMediaUrls);
            // Prioritize actual HLS/DASH manifest streams for MSE/blob-backed video elements
            if (directSrc && directSrc.startsWith('blob:')) {
                const manifests = urls.filter(u => u.includes('.m3u8') || u.includes('.mpd') || u.includes('/hls/') || u.includes('/dash/') || u.includes('mpegurl'));
                if (manifests.length > 0) {
                    return manifests[0]; // Return FIRST manifest (master)
                }
            }
            return urls[0]; // Return FIRST detected URL
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
                    // For YouTube: always use the page URL so VideoPlayerScreen can extract
                    // the video ID and open the YouTube embed player. Googlevideo stream URLs
                    // do not reliably contain the video ID as a query parameter.
                    if (isYouTube && youtubeEnabled) {
                        const ytId = getYoutubeIdFromUrl(window.location.href);
                        if (ytId) {
                            window._omniLaunchedYtIds = window._omniLaunchedYtIds || new Set();
                            window._omniLaunchedYtIds.add(ytId);
                            requestNativePlayback(video, window.location.href);
                            return Promise.resolve(); // Prevent fullscreen
                        }
                    }
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
                        if (isYouTube && youtubeEnabled) {
                            const ytId = getYoutubeIdFromUrl(window.location.href);
                            if (ytId) {
                                window._omniLaunchedYtIds = window._omniLaunchedYtIds || new Set();
                                window._omniLaunchedYtIds.add(ytId);
                                requestNativePlayback(video, window.location.href);
                                return;
                            }
                        }
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
                        if (isYouTube && youtubeEnabled) {
                            const ytId = getYoutubeIdFromUrl(window.location.href);
                            if (ytId) {
                                window._omniLaunchedYtIds = window._omniLaunchedYtIds || new Set();
                                window._omniLaunchedYtIds.add(ytId);
                                requestNativePlayback(video, window.location.href);
                                return;
                            }
                        }
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
                // Always report video src for banner (all sites)
                if (isDownloadableUrl(src)) reportMedia(src, getMimeType(src));
                // Also report blob-backed MSE videos via last known manifest
                if (src && src.startsWith('blob:') && detectedMediaUrls.size > 0) {
                    const urls = Array.from(detectedMediaUrls);
                    const lastUrl = urls[0]; // FIRST manifest
                    if (lastUrl) reportMedia(lastUrl, getMimeType(lastUrl));
                }
                video.querySelectorAll('source').forEach(s => {
                    if (isDownloadableUrl(s.src)) reportMedia(s.src, getMimeType(s.src));
                });
                
                // If a video is currently playing, check for native player takeover
                if (video.currentTime > 0 && !video.paused && !video.ended && video.readyState > 2) {
                    anyPlaying = true;

                    if (nativePlayerEnabled && !video._omniIntercepted && !isLikelyAdOrBanner(video)) {
                        // YouTube takeover (gated on youtubeEnabled from settings)
                        if (isYouTube && youtubeEnabled) {
                            const ytId = getYoutubeIdFromUrl(window.location.href);
                            if (ytId) {
                                window._omniLaunchedYtIds = window._omniLaunchedYtIds || new Set();
                                if (!window._omniLaunchedYtIds.has(ytId)) {
                                    window._omniLaunchedYtIds.add(ytId);
                                    video._omniIntercepted = true;
                                    try { video.pause(); } catch(e) {}
                                    requestNativePlayback(video, window.location.href);
                                }
                            }
                        } else if (!isYouTube) {
                            // Takeover for other sites on play detection
                            if (video.duration && video.duration > 0 && video.duration < 10) {
                                // Ignore short videos
                            } else {
                                const videoUrl = getVideoUrl(video);
                                if (videoUrl) {
                                    video._omniIntercepted = true;
                                    try { video.pause(); } catch(e) {}
                                    requestNativePlayback(video, videoUrl);
                                }
                            }
                        }
                    }
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
