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

    // =========================================================
    // CSS for Quetta-Style Video Overlay
    // =========================================================
    function injectOverlayStyles() {
        if (document.getElementById('_omni_video_overlay_styles')) return;
        const style = document.createElement('style');
        style.id = '_omni_video_overlay_styles';
        style.textContent = `
            .omni-video-overlay-wrapper {
                position: absolute !important;
                z-index: 2147483647 !important;
                pointer-events: auto !important;
                transform: translateZ(0);
                margin: 0 !important;
                padding: 0 !important;
                border: none !important;
                box-sizing: border-box !important;
                touch-action: manipulation !important;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif !important;
            }
            .omni-video-overlay-pill {
                pointer-events: auto !important;
                touch-action: manipulation !important;
                display: inline-flex !important;
                align-items: center !important;
                gap: 6px !important;
                padding: 5px 8px !important;
                border-radius: 20px !important;
                background: rgba(18, 22, 28, 0.94) !important;
                -webkit-backdrop-filter: blur(16px) !important;
                backdrop-filter: blur(16px) !important;
                border: 1px solid rgba(255, 255, 255, 0.22) !important;
                box-shadow: 0 4px 16px rgba(0, 0, 0, 0.55) !important;
                color: #ffffff !important;
                font-size: 12px !important;
                font-weight: 500 !important;
                letter-spacing: 0.2px !important;
                opacity: 0;
                transform: translateY(-4px) scale(0.95);
                transition: opacity 0.22s cubic-bezier(0.4, 0, 0.2, 1), transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
                user-select: none !important;
                -webkit-user-select: none !important;
                -webkit-tap-highlight-color: transparent !important;
            }
            .omni-video-overlay-pill.omni-visible {
                opacity: 1 !important;
                transform: translateY(0) scale(1) !important;
            }
            .omni-btn {
                pointer-events: auto !important;
                touch-action: manipulation !important;
                display: inline-flex !important;
                align-items: center !important;
                gap: 5px !important;
                border: none !important;
                outline: none !important;
                color: #ffffff !important;
                padding: 5px 9px !important;
                border-radius: 14px !important;
                font-size: 11px !important;
                font-weight: 600 !important;
                line-height: 1 !important;
                cursor: pointer !important;
                background: rgba(255, 255, 255, 0.14) !important;
                transition: background 0.15s, transform 0.1s;
                -webkit-tap-highlight-color: transparent !important;
                -webkit-user-select: none !important;
                user-select: none !important;
            }
            .omni-btn:active {
                transform: scale(0.94) !important;
                background: rgba(255, 255, 255, 0.25) !important;
            }
            .omni-btn-player {
                background: #00A5C4 !important;
                color: #ffffff !important;
            }
            .omni-btn-player:active {
                background: #008ba6 !important;
            }
            .omni-btn-download {
                background: rgba(255, 255, 255, 0.16) !important;
                color: #f0f0f0 !important;
            }
            .omni-btn-download:active {
                background: rgba(255, 255, 255, 0.32) !important;
            }
            .omni-btn-close {
                background: transparent !important;
                padding: 4px 5px !important;
                color: rgba(255, 255, 255, 0.6) !important;
                font-size: 13px !important;
                border-radius: 10px !important;
            }
            .omni-btn-close:active {
                color: #ffffff !important;
                background: rgba(255, 255, 255, 0.22) !important;
            }
            .omni-icon {
                width: 14px !important;
                height: 14px !important;
                fill: currentColor !important;
                display: block !important;
                pointer-events: none !important;
            }
            .omni-btn span {
                pointer-events: none !important;
            }
        `;
        (document.head || document.documentElement).appendChild(style);
    }

    // Video-to-stream associations for MSE blob resolution
    const videoStreamAssociations = new Map(); // videoId -> Set<string>

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

    function disableOverlayButtons(videoId) {
        if (videoId && trackedVideos.has(videoId)) {
            const entry = trackedVideos.get(videoId);
            if (entry && entry.pill) {
                entry.pill.querySelectorAll('button').forEach(btn => {
                    btn.disabled = true;
                    btn.style.opacity = '0.6';
                    btn.style.pointerEvents = 'none';
                });
            }
        }
        setTimeout(enableOverlayButtons, 6000); // Safety timeout
    }

    function enableOverlayButtons() {
        trackedVideos.forEach(entry => {
            if (entry.pill) {
                entry.pill.querySelectorAll('button').forEach(btn => {
                    btn.disabled = false;
                    btn.style.opacity = '1';
                    btn.style.pointerEvents = 'auto';
                });
            }
        });
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
            updateAllOverlaysVisibility();
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
            enableOverlayButtons();
        } else if (data.type === 'DOWNLOAD_STARTED') {
            console.log('[inject.js] Download started:', data.payload || data);
            enableOverlayButtons();
        } else if (data.type === 'DOWNLOAD_REJECTED' || data.type === 'DOWNLOAD_ERROR') {
            console.log('[inject.js] Download rejected or failed:', data.payload || data);
            enableOverlayButtons();
        } else if (data.type === 'RESTORE_VIDEO_STATE') {
            // Native player minimized/exited — restore exact position and play state
            handleRestoreVideoState(data.payload || data);
            enableOverlayButtons();
        } else if (data.type === 'HANDOFF_COMPLETE') {
            // Native session ended
            console.log('[inject.js] Native session complete');
            activeSessionId = null;
            document.querySelectorAll('video').forEach(v => {
                delete v._omniIntercepted;
            });
            enableOverlayButtons();
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
    // Quetta-Style Video Overlay Management
    // =========================================================

    const SVG_ICONS = {
        player: `<svg class="omni-icon" viewBox="0 0 24 24"><path d="M19 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zm-9 11V9l5 3-5 3z"/></svg>`,
        download: `<svg class="omni-icon" viewBox="0 0 24 24"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/></svg>`,
        close: `<svg class="omni-icon" viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>`
    };

    /**
     * Reliable touch and click event binder for mobile web overlays.
     * Prevents underlying video players from intercepting or cancelling button taps.
     */
    function bindButtonAction(btn, onTrigger) {
        let lastTriggerTime = 0;

        const handleAction = (e) => {
            const now = Date.now();
            if (now - lastTriggerTime < 450) return; // Debounce duplicate triggers
            lastTriggerTime = now;

            if (e) {
                try { e.preventDefault(); } catch(_) {}
                try { e.stopPropagation(); } catch(_) {}
                if (e.stopImmediatePropagation) {
                    try { e.stopImmediatePropagation(); } catch(_) {}
                }
            }
            try {
                onTrigger(e);
            } catch(err) {
                console.error('[inject.js] Error in button action:', err);
            }
        };

        btn.addEventListener('click', handleAction, { capture: true });
        btn.addEventListener('touchend', handleAction, { passive: false, capture: true });
        btn.addEventListener('pointerup', handleAction, { passive: false, capture: true });

        ['touchstart', 'pointerdown', 'mousedown'].forEach(evt => {
            btn.addEventListener(evt, (e) => {
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
            }, { passive: true, capture: true });
        });
    }

    /**
     * Checks if a video element is eligible for the Quetta overlay.
     */
    function isVideoEligible(video) {
        if (!video || !video.isConnected) return false;
        if (dismissedVideoIds.has(video._omniVideoId)) return false;

        const rect = video.getBoundingClientRect();
        if (rect.width < 140 || rect.height < 90) return false;

        // Skip background video loops (muted + loop with near-zero controls)
        if (video.loop && video.muted && !video.controls && rect.width >= window.innerWidth * 0.9) {
            return false;
        }

        return true;
    }

    /**
     * Creates or updates the floating overlay for a video element.
     */
    function attachOverlayToVideo(video) {
        if (!video._omniVideoId) {
            video._omniVideoId = 'omni_vid_' + (++videoIdCounter) + '_' + Math.random().toString(36).substr(2, 6);
        }
        const videoId = video._omniVideoId;

        if (trackedVideos.has(videoId)) {
            const entry = trackedVideos.get(videoId);
            positionOverlay(entry);
            return;
        }

        injectOverlayStyles();

        // Create overlay container
        const wrapper = document.createElement('div');
        wrapper.className = 'omni-video-overlay-wrapper';
        wrapper.setAttribute('data-omni-for', videoId);

        // Prevent underlying player from capturing touches on the overlay
        wrapper.addEventListener('touchstart', (e) => {
            e.stopPropagation();
        }, { passive: true, capture: true });
        wrapper.addEventListener('pointerdown', (e) => {
            e.stopPropagation();
        }, { passive: true, capture: true });

        const pill = document.createElement('div');
        pill.className = 'omni-video-overlay-pill';

        // Play in Omni Button
        const playBtn = document.createElement('button');
        playBtn.className = 'omni-btn omni-btn-player';
        playBtn.innerHTML = `${SVG_ICONS.player}<span>Omni Player</span>`;
        playBtn.title = "Open in Omni Player";
        bindButtonAction(playBtn, () => {
            const url = getVideoUrl(video) || video.currentSrc || video.src || window.location.href;
            console.log('[inject.js] Omni Player button clicked for URL:', url);
            requestNativePlayback(video, url);
        });

        // Download Button
        const downloadBtn = document.createElement('button');
        downloadBtn.className = 'omni-btn omni-btn-download';
        downloadBtn.innerHTML = `${SVG_ICONS.download}<span>Download</span>`;
        downloadBtn.title = "Download Video";
        bindButtonAction(downloadBtn, () => {
            const url = getVideoUrl(video) || video.currentSrc || video.src || window.location.href;
            console.log('[inject.js] Download button clicked for URL:', url);
            requestDownload(video, url);
        });

        // Close Button
        const closeBtn = document.createElement('button');
        closeBtn.className = 'omni-btn omni-btn-close';
        closeBtn.innerHTML = SVG_ICONS.close;
        closeBtn.title = "Dismiss";
        bindButtonAction(closeBtn, () => {
            dismissedVideoIds.add(videoId);
            removeOverlay(videoId);
        });

        pill.appendChild(playBtn);
        pill.appendChild(downloadBtn);
        pill.appendChild(closeBtn);
        wrapper.appendChild(pill);
        
        const parentContainer = document.fullscreenElement || document.webkitFullscreenElement || document.body || document.documentElement;
        parentContainer.appendChild(wrapper);

        const entry = {
            video: video,
            wrapper: wrapper,
            pill: pill,
            hideTimer: null,
            isHovered: false
        };
        trackedVideos.set(videoId, entry);

        // Position overlay
        positionOverlay(entry);
        showOverlay(entry);

        // Event listeners for auto-hide and interaction
        const resetHideTimer = () => {
            if (entry.hideTimer) clearTimeout(entry.hideTimer);
            showOverlay(entry);
            if (!video.paused && !video.ended && !entry.isHovered) {
                entry.hideTimer = setTimeout(() => {
                    hideOverlay(entry);
                }, 3500);
            }
        };

        wrapper.addEventListener('mouseenter', () => {
            entry.isHovered = true;
            if (entry.hideTimer) clearTimeout(entry.hideTimer);
            showOverlay(entry);
        });
        wrapper.addEventListener('mouseleave', () => {
            entry.isHovered = false;
            resetHideTimer();
        });

        video.addEventListener('play', resetHideTimer);
        video.addEventListener('pause', () => {
            if (entry.hideTimer) clearTimeout(entry.hideTimer);
            showOverlay(entry);
        });
        video.addEventListener('mousemove', resetHideTimer);
        video.addEventListener('touchstart', resetHideTimer, { passive: true });
        video.addEventListener('loadedmetadata', () => positionOverlay(entry));
        video.addEventListener('emptied', () => {
            if (!isVideoEligible(video)) removeOverlay(videoId);
        });

        resetHideTimer();
    }

    function showOverlay(entry) {
        if (!nativePlayerEnabled) return;
        if (!entry.pill.classList.contains('omni-visible')) {
            entry.pill.classList.add('omni-visible');
        }
    }

    function hideOverlay(entry) {
        if (entry.pill.classList.contains('omni-visible')) {
            entry.pill.classList.remove('omni-visible');
        }
    }

    function positionOverlay(entry) {
        const video = entry.video;
        if (!video.isConnected) {
            removeOverlay(video._omniVideoId);
            return;
        }

        const fullscreenEl = document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement;

        if (fullscreenEl) {
            // Reparent inside fullscreen element if needed
            if (entry.wrapper.parentNode !== fullscreenEl) {
                try {
                    fullscreenEl.appendChild(entry.wrapper);
                } catch(e) {}
            }
            entry.wrapper.style.display = 'block';
            entry.wrapper.style.position = 'fixed';
            entry.wrapper.style.top = '16px';
            entry.wrapper.style.right = '16px';
            entry.wrapper.style.left = 'auto';
            entry.wrapper.style.bottom = 'auto';
            entry.wrapper.style.zIndex = '2147483647';
            return;
        }

        // Non-fullscreen mode: ensure attached to document.body
        if (entry.wrapper.parentNode !== document.body && document.body) {
            try {
                document.body.appendChild(entry.wrapper);
            } catch(e) {}
        }
        entry.wrapper.style.position = 'absolute';
        entry.wrapper.style.right = 'auto';
        entry.wrapper.style.bottom = 'auto';
        entry.wrapper.style.zIndex = '2147483647';

        const rect = video.getBoundingClientRect();
        if (rect.width < 140 || rect.height < 90 || rect.bottom <= 0 || rect.top >= window.innerHeight) {
            entry.wrapper.style.display = 'none';
            return;
        }

        entry.wrapper.style.display = 'block';
        const scrollX = window.pageXOffset || document.documentElement.scrollLeft || 0;
        const scrollY = window.pageYOffset || document.documentElement.scrollTop || 0;

        // Position at top-right inside the video bounds
        const top = rect.top + scrollY + 10;
        const left = rect.right + scrollX - (entry.wrapper.offsetWidth || 190) - 10;

        entry.wrapper.style.top = Math.max(scrollY + 4, top) + 'px';
        entry.wrapper.style.left = Math.max(scrollX + 4, left) + 'px';
    }

    function removeOverlay(videoId) {
        if (trackedVideos.has(videoId)) {
            const entry = trackedVideos.get(videoId);
            if (entry.hideTimer) clearTimeout(entry.hideTimer);
            if (entry.wrapper && entry.wrapper.parentNode) {
                entry.wrapper.parentNode.removeChild(entry.wrapper);
            }
            trackedVideos.delete(videoId);
        }
    }

    function updateAllOverlays() {
        if (!nativePlayerEnabled) {
            trackedVideos.forEach(entry => hideOverlay(entry));
            return;
        }

        document.querySelectorAll('video').forEach(video => {
            if (isVideoEligible(video)) {
                attachOverlayToVideo(video);
            } else if (video._omniVideoId && trackedVideos.has(video._omniVideoId)) {
                removeOverlay(video._omniVideoId);
            }
        });

        trackedVideos.forEach(entry => {
            positionOverlay(entry);
        });
    }

    function updateAllOverlaysVisibility() {
        if (!nativePlayerEnabled) {
            trackedVideos.forEach(entry => hideOverlay(entry));
        } else {
            trackedVideos.forEach(entry => showOverlay(entry));
        }
    }

    window.addEventListener('scroll', () => {
        trackedVideos.forEach(entry => positionOverlay(entry));
    }, { passive: true, capture: true });

    window.addEventListener('resize', () => {
        trackedVideos.forEach(entry => positionOverlay(entry));
    }, { passive: true });

    // Fullscreen event listeners
    ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange', 'MSFullscreenChange'].forEach(evt => {
        document.addEventListener(evt, () => {
            setTimeout(updateAllOverlays, 60);
        }, true);
    });

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

        // Temporarily disable overlay buttons to prevent rapid repeat taps
        disableOverlayButtons(video._omniVideoId);

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
        disableOverlayButtons(video._omniVideoId);

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
            updateAllOverlays();
        }
    });

    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            observer.observe(document.body, { childList: true, subtree: true });
        });
    }

    // Periodic check for responsive changes
    setInterval(updateAllOverlays, 1800);
    window.addEventListener('DOMContentLoaded', updateAllOverlays);

    // Reset dedup on SPA navigation
    let lastHref = window.location.href;
    setInterval(() => {
        if (window.location.href !== lastHref) {
            lastHref = window.location.href;
            reportedNativeUrls.clear();
            detectedMediaUrls.clear();
            dismissedVideoIds.clear();
            trackedVideos.forEach((_, id) => removeOverlay(id));
            updateAllOverlays();
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
