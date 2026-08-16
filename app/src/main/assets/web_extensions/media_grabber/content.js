// content.js — Omni Media Grabber Content Script
const api = typeof browser !== "undefined" ? browser : chrome;
var chrome = api;

// Inject our page context hook script to detect media URLs and render Quetta overlay
const script = document.createElement('script');
script.src = chrome.runtime.getURL('inject.js');
script.onload = function() { this.remove(); };
(document.head || document.documentElement).appendChild(script);

// On start, request the current native player state from the background script
try {
    chrome.runtime.sendMessage({ type: 'GET_NATIVE_PLAYER_STATE' })
        .then((response) => {
            if (response && response.hasOwnProperty('enabled')) {
                window.postMessage({
                    type: 'OMNI_SET_NATIVE_PLAYER',
                    enabled: response.enabled,
                    youtubeEnabled: response.youtubeEnabled
                }, '*');
            }
        })
        .catch(() => {});
} catch (e) {
    console.error('[content.js] Failed to request native player state:', e);
}

// Request previously cached media URLs for this tab on startup & periodically
function refreshTabMedia() {
    try {
        chrome.runtime.sendMessage({ type: 'GET_TAB_MEDIA' })
            .then((response) => {
                if (response && Array.isArray(response)) {
                    response.forEach(item => {
                        window.postMessage({
                            type: 'ADD_DETECTED_MANIFEST',
                            url: item.url,
                            mimeType: item.mimeType
                        }, '*');
                    });
                }
            })
            .catch(() => {});
    } catch (e) {}
}
refreshTabMedia();
setTimeout(refreshTabMedia, 1200);
setTimeout(refreshTabMedia, 3000);
setTimeout(refreshTabMedia, 6000);

// Listen for messages from background script (network-detected manifests + native player config + handoff control)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || !message.type) return;

    if (message.type === 'NETWORK_MEDIA_DETECTED') {
        window.postMessage({
            type: 'ADD_DETECTED_MANIFEST',
            url: message.url,
            mimeType: message.mimeType
        }, '*');
    } else if (message.type === 'OMNI_SET_NATIVE_PLAYER') {
        window.postMessage({
            type: 'OMNI_SET_NATIVE_PLAYER',
            enabled: message.enabled,
            youtubeEnabled: message.youtubeEnabled
        }, '*');
    } else if (message.type === 'PAUSE_AND_LAUNCH') {
        window.postMessage({
            type: 'PAUSE_AND_LAUNCH',
            handoffId: message.handoffId,
            sessionId: message.sessionId,
            videoId: message.videoId
        }, '*');
    } else if (message.type === 'RESUME_WEBSITE') {
        window.postMessage({
            type: 'RESUME_WEBSITE',
            handoffId: message.handoffId,
            sessionId: message.sessionId,
            videoId: message.videoId
        }, '*');
    } else if (message.type === 'RESTORE_VIDEO_STATE') {
        window.postMessage({
            type: 'RESTORE_VIDEO_STATE',
            payload: message.payload || message
        }, '*');
    } else if (message.type === 'HANDOFF_COMPLETE') {
        window.postMessage({
            type: 'HANDOFF_COMPLETE',
            sessionId: message.sessionId
        }, '*');
    } else if (message.type === 'EVAL_JS') {
        window.postMessage({
            type: 'EVAL_JS',
            script: message.script
        }, '*');
    }
});

// Relay messages from the page-level inject.js hook to background.js
window.addEventListener('message', (event) => {
    if (event.source !== window) return;
    const data = event.data;
    if (!data || !data.type) return;

    if (data.type === 'REQUEST_HANDOFF') {
        chrome.runtime.sendMessage({
            type: 'REQUEST_HANDOFF',
            url: data.url,
            pageUrl: data.pageUrl,
            handoff: data.handoff,
            mimeType: data.mimeType
        });
    } else if (data.type === 'REQUEST_DOWNLOAD') {
        chrome.runtime.sendMessage({
            type: 'REQUEST_DOWNLOAD',
            url: data.url,
            pageUrl: data.pageUrl,
            mimeType: data.mimeType,
            title: data.title
        });
    } else if (data.type === 'HANDOFF_RESTORED') {
        chrome.runtime.sendMessage({
            type: 'HANDOFF_RESTORED',
            sessionId: data.sessionId,
            videoId: data.videoId,
            currentTimeMs: data.currentTimeMs,
            isPlaying: data.isPlaying
        });
    } else if (data.type === 'MSE_MEDIA_STREAM_GRABBED') {
        chrome.runtime.sendMessage({
            type: 'MEDIA_GRABBED',
            url: data.url,
            mimeType: data.mimeType
        });
    } else if (data.type === 'VIDEO_STATE_CHANGE') {
        chrome.runtime.sendMessage({
            type: 'VIDEO_STATE_CHANGE',
            isPlaying: data.isPlaying
        });
    } else if (data.type === 'OMNI_CONSOLE_LOG') {
        chrome.runtime.sendMessage({
            type: 'CONSOLE_LOG',
            level: data.level,
            message: data.message
        });
    } else if (data.type === 'PLAY_IN_NATIVE') {
        chrome.runtime.sendMessage({
            type: 'PLAY_IN_NATIVE',
            url: data.url,
            pageUrl: data.pageUrl,
            mimeType: data.mimeType
        });
    } else if (data.type === 'OMNI_FOCUS_LOGIN_INPUT') {
        chrome.runtime.sendMessage({
            type: 'FOCUS_LOGIN_INPUT'
        });
    } else if (data.type === 'OMNI_INNER_SCROLL_STATE') {
        chrome.runtime.sendMessage({
            type: 'INNER_SCROLL_STATE',
            isScrolled: data.isScrolled
        });
    }
});
