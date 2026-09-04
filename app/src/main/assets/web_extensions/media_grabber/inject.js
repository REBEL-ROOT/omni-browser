// inject.js — Omni Media Detection & Native Player Handoff Hook
(function() {
    'use strict';

    // Prevent duplicate injection in the same frame
    if (window._omniMediaGrabberInjected) return;
    window._omniMediaGrabberInjected = true;

    // Configuration
    let nativePlayerEnabled = true;
    let youtubeEnabled = false;

    // Tracked video elements: Map<videoId, { video: HTMLVideoElement }>
    const trackedVideos = new Map();
    let videoIdCounter = 0;

    // Active handoff tracking
    let pendingHandoffVideo = null;
    let pendingHandoffId = null;
    let activeSessionId = null;

    // URL validation helpers & caches
    const detectedMediaUrls = new Set();
    const reportedNativeUrls = new Set();

    // Video-to-stream associations for MSE blob resolution
    const videoStreamAssociations = new Map(); // videoId -> Set<string>

    function registerVideo(video) {
        if (!video || video.tagName !== 'VIDEO') return;
        if (!video._omniVideoId) {
            video._omniVideoId = 'omni_vid_' + (++videoIdCounter) + '_' + Math.random().toString(36).substr(2, 6);
        }
        trackedVideos.set(video._omniVideoId, { video: video });
    }

    function associateStreamWithActiveVideos(streamUrl) {
        if (!streamUrl) return;
        document.querySelectorAll('video').forEach(video => {
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
                targetVideo = trackedVideos.get(targetVideoId).video;
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
                targetVideo = trackedVideos.get(targetVideoId).video;
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
            targetVideo = trackedVideos.get(videoId).video;
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

    // Direct download for the site overlay button — uses a simpler path that bypasses
    // the media resolution pipeline and starts the download immediately.
    function requestSiteDownload(video, videoUrl) {
        if (!videoUrl) return;
        window.postMessage({
            type: 'SITE_DOWNLOAD_REQUEST',
            url: videoUrl,
            pageUrl: window.location.href,
            mimeType: getMimeType(videoUrl),
            title: document.title || 'Video'
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
        // Exclude non-playable resources that often pass other checks
        if (lower.includes('.vtt') || lower.includes('.srt') || lower.includes('.ass') || lower.includes('.ssa') ||
            lower.includes('.webvtt') || lower.includes('thumbnail') || lower.includes('thumb')) {
            return false;
        }
        if (lower.includes('/segment') || lower.includes('/fragment') || lower.includes('.ts') || lower.includes('.m4s') || lower.includes('analytics') || lower.includes('telemetry')) {
            return false;
        }
        return lower.includes('.m3u8') ||
               lower.includes('.mpd')  ||
               lower.includes('.mp4')  ||
               lower.includes('.webm') ||
               lower.includes('.mkv')  ||
               (lower.includes('/hls/') && !lower.includes('/thumbnails/')) ||
               (lower.includes('/dash/') && !lower.includes('/thumbnails/')) ||
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



    // Event listeners
    window.addEventListener('playing', e => {
        if (e.target?.tagName === 'VIDEO') {
            registerVideo(e.target);
            reportVideoState(true);
        }
    }, true);

    window.addEventListener('pause', e => {
        if (e.target?.tagName === 'VIDEO') {
            reportVideoState(false);
        }
    }, true);

    window.addEventListener('ended', e => {
        if (e.target?.tagName === 'VIDEO') {
            reportVideoState(false);
        }
    }, true);

    window.addEventListener('play', (event) => {
        try {
            const video = event.target;
            if (!video || video.tagName !== 'VIDEO') return;
            registerVideo(video);
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
        for (const mutation of mutations) {
            if (mutation.type === 'childList') {
                for (const node of mutation.addedNodes) {
                    if (node.tagName === 'VIDEO') {
                        registerVideo(node);
                    } else if (node.querySelectorAll) {
                        node.querySelectorAll('video').forEach(registerVideo);
                    }
                }
                for (const node of mutation.removedNodes) {
                    if (node.tagName === 'VIDEO' && node._omniVideoId) {
                        trackedVideos.delete(node._omniVideoId);
                    } else if (node.querySelectorAll) {
                        node.querySelectorAll('video').forEach(v => {
                            if (v._omniVideoId) trackedVideos.delete(v._omniVideoId);
                        });
                    }
                }
            }
        }
    });

    function scanAndRegisterVideos() {
        document.querySelectorAll('video').forEach(registerVideo);
    }

    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        });
    }

    scanAndRegisterVideos();
    window.addEventListener('DOMContentLoaded', scanAndRegisterVideos);

    // Reset dedup on SPA navigation
    let lastHref = window.location.href;
    setInterval(() => {
        if (window.location.href !== lastHref) {
            lastHref = window.location.href;
            reportedNativeUrls.clear();
            detectedMediaUrls.clear();
            trackedVideos.clear();
            scanAndRegisterVideos();
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

    // =========================================================
    // Site Player Overlay — Quetta-Style Download Button
    // =========================================================

    const OVERLAY_STYLE_ID = '_omni_site_overlay_style';
    const OVERLAY_BUTTON_CLASS = '_omni_site_overlay_btn';
    const OVERLAY_CLASS = '_omni_site_overlay';

    function ensureOverlayStyles() {
        if (document.getElementById(OVERLAY_STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = OVERLAY_STYLE_ID;
        style.textContent = [
            '.' + OVERLAY_CLASS + ' {',
            '  position: absolute;',
            '  z-index: 2147483646;',
            '  pointer-events: none;',
            '  opacity: 0;',
            '  transition: opacity 0.18s ease, transform 0.18s ease;',
            '  transform: translateY(6px);',
            '  display: flex;',
            '  align-items: center;',
            '  gap: 6px;',
            '}',
            '.' + OVERLAY_CLASS + '._omni_visible {',
            '  opacity: 1;',
            '  transform: translateY(0);',
            '}',
            '.' + OVERLAY_CLASS + '._omni_right { right: 12px; top: 12px; flex-direction: column; }',
            '.' + OVERLAY_CLASS + '._omni_top { top: 12px; }',
            '.' + OVERLAY_CLASS + '._omni_bottom { bottom: 12px; }',
            '.' + OVERLAY_BUTTON_CLASS + ' {',
            '  pointer-events: auto;',
            '  width: 40px;',
            '  height: 40px;',
            '  border-radius: 22px;',
            '  background: rgba(0, 0, 0, 0.55);',
            '  backdrop-filter: blur(8px);',
            '  -webkit-backdrop-filter: blur(8px);',
            '  color: #fff;',
            '  border: 1px solid rgba(255, 255, 255, 0.18);',
            '  display: inline-flex;',
            '  align-items: center;',
            '  justify-content: center;',
            '  cursor: pointer;',
            '  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.35);',
            '  transition: background 0.15s ease, transform 0.15s ease;',
            '}',
            '.' + OVERLAY_BUTTON_CLASS + ':hover {',
            '  background: rgba(20, 20, 20, 0.85);',
            '  transform: scale(1.05);',
            '}',
            '.' + OVERLAY_BUTTON_CLASS + ':active {',
            '  transform: scale(0.95);',
            '}',
            '.' + OVERLAY_BUTTON_CLASS + ' svg { width: 20px; height: 20px; }',
            '@media (prefers-color-scheme: light) {',
            '  .' + OVERLAY_BUTTON_CLASS + ' {',
            '    background: rgba(255, 255, 255, 0.85);',
            '    color: #111;',
            '    border-color: rgba(0, 0, 0, 0.1);',
            '  }',
            '}'
        ].join('\n');
        (document.head || document.documentElement).appendChild(style);
    }

    const DOWNLOAD_ICON_SVG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg>';

    function buildOverlay(video) {
        const overlay = document.createElement('div');
        overlay.className = OVERLAY_CLASS + ' _omni_right';
        overlay.dataset.omniOverlay = '1';

        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = OVERLAY_BUTTON_CLASS;
        btn.title = 'Download with Omni';
        btn.setAttribute('aria-label', 'Download with Omni');
        btn.innerHTML = DOWNLOAD_ICON_SVG;

        btn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            try {
                const videoUrl = getVideoUrl(video);
                // Try the direct SITE_DOWNLOAD_REQUEST first (no resolution pipeline)
                requestSiteDownload(video, videoUrl);
                // Also send REQUEST_DOWNLOAD as a fallback for the media sniffer pipeline
                setTimeout(() => {
                    requestDownload(video, videoUrl);
                }, 200);
            } catch (err) {
                console.error('[inject.js] Site overlay download failed:', err);
            }
        });

        overlay.appendChild(btn);
        return overlay;
    }

    function attachOverlayToVideo(video) {
        if (!video || video._omniOverlay) return;
        ensureOverlayStyles();
        const position = window.getComputedStyle(video).position;
        if (position === 'static') {
            video.style.position = 'relative';
        }
        const overlay = buildOverlay(video);
        const host = video.parentElement || video;
        host.appendChild(overlay);
        video._omniOverlay = overlay;
        video._omniOverlayHost = host;

        const show = () => overlay.classList.add('_omni_visible');
        const hide = () => overlay.classList.remove('_omni_visible');
        video.addEventListener('mouseenter', show);
        video.addEventListener('mouseleave', hide);
        video.addEventListener('play', show);
        video.addEventListener('pause', hide);
        video.addEventListener('touchstart', show, { passive: true });
        if (!video.paused) show();
    }

    function detachOverlayFromVideo(video) {
        const overlay = video._omniOverlay;
        const host = video._omniOverlayHost || (video.parentElement || video);
        if (overlay && overlay.parentElement === host) {
            host.removeChild(overlay);
        }
        video._omniOverlay = null;
        video._omniOverlayHost = null;
    }

    function isOverlayCandidate(video) {
        if (!video || video.tagName !== 'VIDEO') return false;
        const rect = video.getBoundingClientRect();
        if (rect.width < 200 || rect.height < 120) return false;
        if (video.muted && video.controls === false && video.getAttribute('autoplay') !== null && rect.width < 400) {
            return false;
        }
        return true;
    }

    const siteOverlayObserver = new MutationObserver(() => {
        document.querySelectorAll('video').forEach(v => {
            if (isOverlayCandidate(v) && !v._omniOverlay) {
                registerVideo(v);
                attachOverlayToVideo(v);
            } else if (v._omniOverlay && !isOverlayCandidate(v)) {
                detachOverlayFromVideo(v);
            }
        });
    });

    function scanAndAttachSiteOverlays() {
        document.querySelectorAll('video').forEach(v => {
            if (isOverlayCandidate(v) && !v._omniOverlay) {
                registerVideo(v);
                attachOverlayToVideo(v);
            }
        });
    }

    if (document.body) {
        siteOverlayObserver.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            if (document.body) {
                siteOverlayObserver.observe(document.body, { childList: true, subtree: true });
            }
        });
    }

    scanAndAttachSiteOverlays();
    window.addEventListener('DOMContentLoaded', scanAndAttachSiteOverlays);
    window.addEventListener('resize', scanAndAttachSiteOverlays);

    const removalObserver = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            if (mutation.type === 'childList') {
                for (const node of mutation.removedNodes) {
                    if (node.tagName === 'VIDEO' && node._omniOverlay) {
                        detachOverlayFromVideo(node);
                    } else if (node.querySelectorAll) {
                        node.querySelectorAll('video').forEach(v => {
                            if (v._omniOverlay) detachOverlayFromVideo(v);
                        });
                    }
                }
            }
        }
    });
    if (document.body) {
        removalObserver.observe(document.body, { childList: true, subtree: true });
    }
})();
