// content.js
const api = typeof browser !== "undefined" ? browser : chrome;
var chrome = api;
// Inject our page context hook script to detect media URLs (passive detection only)
const script = document.createElement('script');

script.src = chrome.runtime.getURL('inject.js');
script.onload = function() { this.remove(); };
(document.head || document.documentElement).appendChild(script);

// On start, request the current native player state from the background script
try {
    chrome.runtime.sendMessage({ type: 'GET_NATIVE_PLAYER_STATE' })
        .then((response) => {
            if (response && response.hasOwnProperty('enabled')) {
                console.log('[content.js] Received initial native player state:', response.enabled);
                window.postMessage({
                    type: 'OMNI_SET_NATIVE_PLAYER',
                    enabled: response.enabled
                }, '*');
            }
        })
        .catch(() => {});
} catch (e) {
    console.error('[content.js] Failed to request native player state:', e);
}

// Request previously cached media URLs for this tab on startup
try {
    chrome.runtime.sendMessage({ type: 'GET_TAB_MEDIA' })
        .then((response) => {
            if (response && Array.isArray(response)) {
                console.log('[content.js] Received cached tab media items:', response.length);
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
} catch (e) {
    console.error('[content.js] Failed to request cached tab media:', e);
}

// Listen for messages from background script (network-detected manifests + native player config)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'NETWORK_MEDIA_DETECTED') {
        window.postMessage({
            type: 'ADD_DETECTED_MANIFEST',
            url: message.url,
            mimeType: message.mimeType
        }, '*');
    } else if (message.type === 'OMNI_SET_NATIVE_PLAYER') {
        // Relay native player preference to the inject.js page context
        console.log('[content.js] Relaying native player state change:', message.enabled);
        window.postMessage({
            type: 'OMNI_SET_NATIVE_PLAYER',
            enabled: message.enabled
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
    if (!data) return;

    if (data.type === 'MSE_MEDIA_STREAM_GRABBED') {
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
        console.log('[content.js] Received PLAY_IN_NATIVE from window context, sending to background.js for URL:', data.url);
        chrome.runtime.sendMessage({
            type: 'PLAY_IN_NATIVE',
            url: data.url,
            pageUrl: data.pageUrl,
            mimeType: data.mimeType
        });
    }
});
