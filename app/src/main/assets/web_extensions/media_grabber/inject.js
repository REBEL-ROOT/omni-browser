// inject.js — Omni Media Detection + Quetta-style Video Control Overlay & Native Player Handoff
(function() {
    'use strict';

    // Prevent duplicate injection in the same frame
    if (window._omniMediaGrabberInjected) return;
    window._omniMediaGrabberInjected = true;

    // Configuration
    let nativePlayerEnabled = true;
    let youtubeEnabled = false;

    // Tracked video elements: Map<videoId, VideoEntry>
    const trackedVideos = new Map();
    let videoIdCounter = 0;

    // Active handoff tracking
    let pendingHandoffVideo = null;
    let pendingHandoffId = null;
    let activeSessionId = null;

    // URL validation helpers & caches
    const detectedMediaUrls = new Set();
    const reportedNativeUrls = new Set();
    const dismissedVideoIds = new Set();

    // Video-to-stream associations for MSE blob resolution
    const videoStreamAssociations = new Map(); // videoId -> Set<string>

    function associateStreamWithActiveVideos(streamUrl) {
        if (!streamUrl) return;
        document.querySelectorAll('video').forEach(video => {
            if (!video._omniVideoId) {
                video._omniVideoId = 'omni_vid_' + (++videoIdCounter) + '_' + Math.random().toString(36).substr(2, 6);
            }
            const id = video._omniVideoId;
            if (id && (!video.paused || trackedVideos.has(id))) {
                if (!videoStreamAssociations.has(id)) {
                    videoStreamAssociations.set(id, new Set());
                }
                videoStreamAssociations.get(id).add(streamUrl);
            }
        });
    }

    function getAssociatedStreamsForVideo(video) {
        const set = new Set();
        const id = video ? video._omniVideoId : null;
        if (id && videoStreamAssociations.has(id)) {
            videoStreamAssociations.get(id).forEach(u => set.add(u));
        }
        detectedMediaUrls.forEach(u => set.add(u));
        return Array.from(set);
    }

    // =========================================================
    // Message Bridge & Native Handlers
    // =========================================================
    window.addEventListener('message', (event) => {
        if (event.source !== window) return;
        const data = event.data;
        if (!data || !data.type) return;

        if (data.type === 'OMNI_SET_NATIVE_PLAYER') {
            nativePlayerEnabled = !!data.enabled;
            youtubeEnabled = !!data.youtubeEnabled;
        } else if (data.type === 'EVAL_JS') {
            try {
                const result = window.eval(data.script);
                console.log("> " + (result === undefined ? 'undefined' : String(result)));
            } catch (err) {
                console.error("Error: " + (err.message || err));
            }
        } else if (data.type === 'HANDOFF_ACCEPTED' || data.type === 'PAUSE_AND_LAUNCH') {
            // Native accepted handoff — pause the website video
            const payload = data.payload || data;
            const targetVideoId = payload.videoId;
            const handoffId = payload.sessionId || payload.handoffId;
            activeSessionId = handoffId;

            let targetVideo = null;
            if (targetVideoId && trackedVideos.has(targetVideoId)) {
                targetVideo = trackedVideos.get(targetVideoId);
            } else if (pendingHandoffVideo) {
                targetVideo = pendingHandoffVideo;
            }

            if (targetVideo) {
                try {
                    targetVideo.pause();
                } catch(e) {}
                targetVideo._omniIntercepted = true;
            }
            pendingHandoffVideo = null;
            pendingHandoffId = null;
        } else if (data.type === 'HANDOFF_REJECTED' || data.type === 'RESUME_WEBSITE' || data.type === 'HANDOFF_ERROR') {
            // Native rejected handoff or preparation failed — resume/unpause website video
            const payload = data.payload || data;
            const targetVideoId = payload.videoId;
            console.log('[inject.js] Native rejected or failed handoff — resuming website video:', payload);

            let targetVideo = null;
            if (targetVideoId && trackedVideos.has(targetVideoId)) {
                targetVideo = trackedVideos.get(targetVideoId);
            } else if (pendingHandoffVideo) {
                targetVideo = pendingHandoffVideo;
            }

            if (targetVideo) {
                try {
                    if (targetVideo._omniWasPlayingBeforeHandoff) {
                        targetVideo.play().catch(() => {});
                    }
                } catch(e) {}
                delete targetVideo._omniIntercepted;
            }
            pendingHandoffVideo = null;
            pendingHandoffId = null;
            activeSessionId = null;
        } else if (data.type === 'DOWNLOAD_STARTED') {
            console.log('[inject.js] Download started:', data.payload || data);
        } else if (data.type === 'DOWNLOAD_REJECTED' || data.type === 'DOWNLOAD_ERROR') {
            console.log('[inject.js] Download rejected or failed:', data.payload || data);
        } else if (data.type === 'RESTORE_VIDEO_STATE') {
            // Native player minimized/exited — restore exact position and play state
            handleRestoreVideoState(data.payload || data);
        } else if (data.type === 'HANDOFF_COMPLETE') {
            // Native session ended
            console.log('[inject.js] Native session complete');
            activeSessionId = null;
            document.querySelectorAll('video').forEach(v => {
                delete v._omniIntercepted;
            });
        } else if (data.type === 'ADD_DETECTED_MANIFEST') {
            const url = data.url;
            if (url && isDownloadableUrl(url) && isPlayableMediaUrl(url)) {
                detectedMediaUrls.add(url);
                reportMedia(url, getMimeType(url));
            }
        }
    });

    /**
     * Restores the webpage video state on return from native player.
     */
    function handleRestoreVideoState(payload) {
        if (!payload) return;
        const videoId = payload.videoId;
        const sourceUri = payload.sourceUri;
        const currentTimeSec = (payload.currentTimeMs !== undefined) ? (payload.currentTimeMs / 1000.0) : null;
        const isPlaying = !!payload.isPlaying;
        const playbackRate = payload.playbackRate || 1.0;
        const volume = (payload.volume !== undefined) ? payload.volume : 1.0;
        const muted = !!payload.muted;
        const sessionId = payload.sessionId || activeSessionId;

        console.log('[inject.js] RESTORE_VIDEO_STATE received:', {
            videoId, sourceUri, currentTimeSec, isPlaying, playbackRate, volume, muted, sessionId
        });

        // 1. Locate video strictly by ID or exact source URI — NEVER blind videos[0] fallback
        let targetVideo = null;
        if (videoId && trackedVideos.has(videoId)) {
            targetVideo = trackedVideos.get(videoId);
        }
        if (!targetVideo && sourceUri) {
            const videos = Array.from(document.querySelectorAll('video'));
            targetVideo = videos.find(v => (v.currentSrc === sourceUri || v.src === sourceUri));
        }

        if (!targetVideo) {
            console.warn('[inject.js] No matching video element found for videoId=' + videoId + ' — skipping restore.');
            return;
        }

        // 2. Restore state atomically
        try {
            if (currentTimeSec !== null && isFinite(currentTimeSec) && currentTimeSec >= 0) {
                targetVideo.currentTime = currentTimeSec;
            }
            if (playbackRate && isFinite(playbackRate)) {
                targetVideo.playbackRate = playbackRate;
            }
            if (volume !== undefined && isFinite(volume)) {
                targetVideo.volume = Math.max(0, Math.min(1, volume));
            }
            if (muted !== undefined) {
                targetVideo.muted = muted;
            }

            if (isPlaying) {
                targetVideo.play().catch(() => {});
            } else {
                targetVideo.pause();
            }

            delete targetVideo._omniIntercepted;
        } catch(e) {
            console.error('[inject.js] Error applying restored state to video:', e);
        }

        // 3. Acknowledge restore to native
        window.postMessage({
            type: 'HANDOFF_RESTORED',
            sessionId: sessionId || '',
            videoId: targetVideo._omniVideoId || videoId || '',
            currentTimeMs: Math.floor(targetVideo.currentTime * 1000),
            isPlaying: !targetVideo.paused
        }, '*');

        activeSessionId = null;
    }

    function registerVideo(video) {
        if (!video || !video.isConnected) return;
        if (!video._omniVideoId) {
            video._omniVideoId = 'omni_vid_' + (++videoIdCounter) + '_' + Math.random().toString(36).substr(2, 6);
        }
        trackedVideos.set(video._omniVideoId, video);
    }

    function updateAllTrackedVideos() {
        document.querySelectorAll('video').forEach(video => {
            registerVideo(video);
        });
    }

    // =========================================================
    // Video Capture & Handoff
    // =========================================================

    function captureVideoState(video) {
        const duration = video.duration;
        const videoUrl = getVideoUrl(video) || video.currentSrc || video.src || '';
        return {
            sessionId: 'h_' + Math.random().toString(36).substr(2, 9),
            handoffId: 'h_' + Math.random().toString(36).substr(2, 9),
            videoId: video._omniVideoId || '',
            videoElementId: video._omniVideoId || '',
            sourceUri: videoUrl,
            pageUrl: window.location.href,
            title: document.title || '',
            currentPositionMs: Math.floor(video.currentTime * 1000),
            durationMs: (isFinite(duration) && duration > 0) ? Math.floor(duration * 1000) : null,
            isPaused: video.paused,
            isPlaying: !video.paused,
            playbackRate: video.playbackRate || 1.0,
            volume: video.volume || 1.0,
            muted: video.muted || false,
            mimeType: video.type || getMimeType(videoUrl) || '',
            videoWidth: video.videoWidth || 0,
            videoHeight: video.videoHeight || 0,
            poster: video.poster || ''
        };
    }

    function requestNativePlayback(video, videoUrl) {
        console.log('[inject.js] requestNativePlayback called for videoUrl:', videoUrl);
        if (!videoUrl) return false;

        // Capture live state BEFORE pausing
        const handoff = captureVideoState(video);
        handoff.capturedAt = Date.now();

        pendingHandoffVideo = video;
        pendingHandoffId = handoff.sessionId || handoff.handoffId;
        video._omniWasPlayingBeforeHandoff = !video.paused;

        // Collect streams specifically associated with this video element
        const associatedStreams = getAssociatedStreamsForVideo(video);

        // Exit full-screen if active
        try {
            if (document.fullscreenElement || document.webkitFullscreenElement) {
                (document.exitFullscreen || document.webkitExitFullscreen).call(document);
            }
        } catch(e) {}

        // Send to native bridge
        window.postMessage({
            type: 'REQUEST_HANDOFF',
            url: videoUrl,
            pageUrl: window.location.href,
            associatedStreams: associatedStreams,
            handoff: handoff,
            mimeType: getMimeType(videoUrl)
        }, '*');

        return true;
    }

    function requestDownload(video, videoUrl) {
        if (!videoUrl) return;
        const associatedStreams = getAssociatedStreamsForVideo(video);

        window.postMessage({
            type: 'REQUEST_DOWNLOAD',
            url: videoUrl,
            pageUrl: window.location.href,
            associatedStreams: associatedStreams,
            mimeType: getMimeType(videoUrl),
            title: document.title || 'Video',
            videoId: video._omniVideoId || '',
            requestId: 'dl_' + Math.random().toString(36).substr(2, 9)
        }, '*');
    }

    function getVideoUrl(video) {
        if (!video) return null;

        const directSrc = video.currentSrc || video.src;
        if (isDownloadableUrl(directSrc)) return directSrc;

        const sources = video.querySelectorAll('source');
        for (const source of sources) {
            if (isDownloadableUrl(source.src)) return source.src;
        }

        for (const attr of video.attributes) {
            const val = attr.value;
            if (isDownloadableUrl(val) && (val.includes('.mp4') || val.includes('.m3u8') || val.includes('.webm') || val.includes('.mpd'))) {
                return val;
            }
        }

        if (detectedMediaUrls.size > 0) {
            const urls = Array.from(detectedMediaUrls);
            const manifests = urls.filter(u => u.includes('.m3u8') || u.includes('.mpd') || u.includes('/hls/') || u.includes('/dash/') || u.includes('mpegurl'));
            if (manifests.length > 0) return manifests[0];
            const playableUrl = urls.find(u => isPlayableMediaUrl(u));
            if (playableUrl) return playableUrl;
        }

        return directSrc || video.src || null;
    }

    function isDownloadableUrl(url) {
        if (!url) return false;
        if (url.startsWith('blob:') || url.startsWith('data:')) return false;
        return (url.startsWith('http://') || url.startsWith('https://'));
    }

    function isPlayableMediaUrl(url) {
        if (!url) return false;
        const lower = url.toLowerCase();
        if (lower.includes('/segment') || lower.includes('/fragment') || lower.includes('.ts') || lower.includes('.m4s') || lower.includes('analytics') || lower.includes('telemetry')) {
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

    function reportMedia(url, mimeType) {
        if (!isDownloadableUrl(url)) return;
        if (isPlayableMediaUrl(url)) {
            detectedMediaUrls.add(url);
            associateStreamWithActiveVideos(url);
        }
        window.postMessage({
            type: 'MSE_MEDIA_STREAM_GRABBED',
            url: url,
            mimeType: mimeType || 'video/mp4'
        }, '*');
    }

    function reportVideoState(isPlaying) {
        window.postMessage({ type: 'VIDEO_STATE_CHANGE', isPlaying }, '*');
    }

    // =========================================================
    // Passive Network & Setter Interception
    // =========================================================

    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
        const requestUrl = typeof args[0] === 'string' ? args[0] : (args[0]?.url || '');
        if (isPlayableMediaUrl(requestUrl)) {
            reportMedia(requestUrl, getMimeType(requestUrl));
        }
        return originalFetch.apply(this, args);
    };

    const originalXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, ...args) {
        if (typeof url === 'string' && isPlayableMediaUrl(url)) {
            reportMedia(url, getMimeType(url));
        }
        return originalXhrOpen.call(this, method, url, ...args);
    };

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
    } catch(e) {}

    // Event listeners
    window.addEventListener('playing', e => { if (e.target?.tagName === 'VIDEO') reportVideoState(true); }, true);
    window.addEventListener('pause', e => { if (e.target?.tagName === 'VIDEO') reportVideoState(false); }, true);
    window.addEventListener('ended', e => { if (e.target?.tagName === 'VIDEO') reportVideoState(false); }, true);

    window.addEventListener('play', (event) => {
        try {
            const video = event.target;
            if (!video || video.tagName !== 'VIDEO') return;
            reportVideoState(true);

            const src = video.currentSrc || video.src;
            if (isDownloadableUrl(src)) {
                reportMedia(src, getMimeType(src));
            } else if (src && src.startsWith('blob:')) {
                const urls = Array.from(detectedMediaUrls);
                if (urls.length > 0) reportMedia(urls[0], getMimeType(urls[0]));
            }
        } catch(e) {}
    }, true);

    // =========================================================
    // MutationObserver & Lifecycle Management
    // =========================================================

    const observer = new MutationObserver((mutations) => {
        let shouldUpdate = false;
        for (const mutation of mutations) {
            if (mutation.type === 'childList') {
                for (const node of mutation.addedNodes) {
                    if (node.tagName === 'VIDEO' || (node.querySelector && node.querySelector('video'))) {
                        shouldUpdate = true;
                        break;
                    }
                }
                for (const node of mutation.removedNodes) {
                    if (node.tagName === 'VIDEO' || (node.querySelector && node.querySelector('video'))) {
                        shouldUpdate = true;
                        break;
                    }
                }
            }
        }
        if (shouldUpdate) {
            updateAllTrackedVideos();
        }
    });

    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            observer.observe(document.body, { childList: true, subtree: true });
        });
    }

    // Periodic check for video elements
    setInterval(updateAllTrackedVideos, 2000);
    window.addEventListener('DOMContentLoaded', updateAllTrackedVideos);

    // Reset dedup on SPA navigation
    let lastHref = window.location.href;
    setInterval(() => {
        if (window.location.href !== lastHref) {
            lastHref = window.location.href;
            reportedNativeUrls.clear();
            detectedMediaUrls.clear();
            dismissedVideoIds.clear();
            trackedVideos.clear();
            videoStreamAssociations.clear();
            updateAllTrackedVideos();
        }
    }, 600);

    function getMimeType(url) {
        if (!url) return 'video/mp4';
        const lower = url.toLowerCase();
        if (lower.includes('m3u8')) return 'application/x-mpegURL';
        if (lower.includes('mpd')) return 'application/dash+xml';
        if (lower.includes('.webm')) return 'video/webm';
        if (lower.includes('.mp4')) return 'video/mp4';
        if (lower.includes('.m4a') || lower.includes('.mp3') || lower.includes('.aac') || lower.includes('.ogg')) return 'audio/mpeg';
        return 'video/mp4';
    }
})();
