// background.js — Omni Aggressive Media Grabber
// Dual-path media detection: webRequest network interception + MSE content script relay

// ============================================
// PATH 1: Network-level webRequest interception
// Catches ALL sub-resource media requests (not just navigation)
// ============================================

const MEDIA_URL_PATTERNS = [
    /\.m3u8(\?|$|#)/i,
    /\.mpd(\?|$|#)/i,
    /\.mp4(\?|$|#)/i,
    /\.webm(\?|$|#)/i,
    /\.mp3(\?|$|#)/i,
    /\.m4a(\?|$|#)/i,
    /\.m4v(\?|$|#)/i,
    /\/\d+\.ts(\?|$|#)/i,   // HLS transport segments (e.g., /001.ts, /segment123.ts)
    /seg[\w-]*\.ts(\?|$|#)/i, // Named segments (e.g., seg1.ts, segment-001.ts)
    /\.aac(\?|$|#)/i,
    /\.ogg(\?|$|#)/i,
    /\.flv(\?|$|#)/i,
    /\.avi(\?|$|#)/i,
    /\/hls\//i,
    /\/dash\//i,
    /\/video\//i,
    /\/audio\//i,
    /manifest.*\.m3u8/i,
    /manifest.*\.mpd/i,
    /index.*\.m3u8/i,
    /master.*\.m3u8/i,
    /playlist.*\.m3u8/i,
    /videoplayback/i,
    /googlevideo\.com/i,
    /\.googlevideo\.com.*itag=/i,
    /mime=video/i,
    /mime=audio/i
];

const MEDIA_CONTENT_TYPES = [
    'video/',
    'audio/',
    'application/x-mpegurl',
    'application/vnd.apple.mpegurl',
    'application/dash+xml',
    'application/octet-stream'
];

// Track reported URLs to avoid duplicates
const reportedUrls = new Set();
const tabMediaMap = new Map(); // tabId -> Set of JSON-serialized media objects

// Clean up tab cache when tabs are closed or navigated
chrome.tabs.onRemoved.addListener((tabId) => {
    tabMediaMap.delete(tabId);
});
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
    if (changeInfo.status === 'loading') {
        tabMediaMap.delete(tabId);
    }
});

function classifyUrl(url) {
    const lower = url.toLowerCase();
    if (lower.includes('.m3u8') || lower.includes('mpegurl')) return 'application/x-mpegURL';
    if (lower.includes('.mpd') || lower.includes('dash+xml')) return 'application/dash+xml';
    if (lower.includes('.mp4') || lower.includes('videoplayback') || lower.includes('googlevideo')) return 'video/mp4';
    if (lower.includes('.webm')) return 'video/webm';
    if (lower.includes('.mp3') || lower.includes('.m4a') || lower.includes('.aac') || lower.includes('.ogg')) return 'audio/mpeg';
    if (lower.includes('.flv')) return 'video/x-flv';
    return 'video/mp4';
}

function isMediaUrl(url) {
    if (!url || url.startsWith('blob:') || url.startsWith('data:')) return false;
    // Skip very small tracking pixel / beacon URLs
    if (url.includes('pixel') || url.includes('beacon') || url.includes('analytics')) return false;
    // Skip extension internal URLs
    if (url.startsWith('moz-extension:') || url.startsWith('chrome-extension:')) return false;
    
    return MEDIA_URL_PATTERNS.some(pattern => pattern.test(url));
}

function reportToNative(url, mimeType, tabId) {
    if (reportedUrls.has(url)) return;
    reportedUrls.add(url);
    
    // Limit set size to prevent memory leaks
    if (reportedUrls.size > 500) {
        const first = reportedUrls.values().next().value;
        reportedUrls.delete(first);
    }

    console.log('[MediaGrabber] Detected media:', mimeType, url.substring(0, 120));
    
    // Cache the media URL for this tab
    if (tabId !== undefined && tabId !== null && tabId >= 0) {
        if (!tabMediaMap.has(tabId)) {
            tabMediaMap.set(tabId, new Set());
        }
        const mediaItem = JSON.stringify({ url: url, mimeType: mimeType || 'video/mp4' });
        const tabSet = tabMediaMap.get(tabId);
        tabSet.add(mediaItem);
        
        // Limit tab cache size to 50
        if (tabSet.size > 50) {
            const firstItem = tabSet.values().next().value;
            tabSet.delete(firstItem);
        }
    }

    try {
        chrome.runtime.sendNativeMessage('omniApp', {
            type: 'MEDIA_GRABBED',
            url: url,
            mimeType: mimeType || 'video/mp4'
        });
    } catch (e) {
        console.error('[MediaGrabber] Native message failed:', e);
    }

    if (tabId !== undefined && tabId >= 0) {
        try {
            chrome.tabs.sendMessage(tabId, {
                type: 'NETWORK_MEDIA_DETECTED',
                url: url,
                mimeType: mimeType || 'video/mp4'
            });
        } catch (e) {
            // Ignore if tab is not ready
        }
    }
}

// Intercept ALL network requests and check for media URLs/content-types
chrome.webRequest.onBeforeRequest.addListener(
    function(details) {
        const url = details.url;
        if (!url) return;
        
        // Check URL patterns
        if (isMediaUrl(url)) {
            reportToNative(url, classifyUrl(url), details.tabId);
        }
    },
    { urls: ["<all_urls>"] },
    []
);

// Also intercept response headers to detect media by Content-Type
chrome.webRequest.onHeadersReceived.addListener(
    function(details) {
        const url = details.url;
        if (!url || url.startsWith('blob:') || url.startsWith('data:')) return;

        const responseHeaders = details.responseHeaders || [];
        for (const header of responseHeaders) {
            if (header.name.toLowerCase() === 'content-type') {
                const contentType = (header.value || '').toLowerCase();
                const isMedia = MEDIA_CONTENT_TYPES.some(mt => contentType.includes(mt));
                if (isMedia) {
                    // Skip tiny responses (tracking pixels etc.)
                    const contentLength = responseHeaders.find(h => h.name.toLowerCase() === 'content-length');
                    const size = contentLength ? parseInt(contentLength.value, 10) : -1;
                    if (size > 0 && size < 50000) return; // Skip files under 50KB
                    
                    reportToNative(url, contentType, details.tabId);
                }
                break;
            }
        }
    },
    { urls: ["<all_urls>"] },
    ["responseHeaders"]
);

// ============================================
// PATH 2: Relay from content script MSE hooks
// ============================================

// Settings cache and polling
let nativePlayerEnabled = true; // Default to true

function broadcastStateToTabs() {
    chrome.tabs.query({}, (tabs) => {
        if (tabs && tabs.length > 0) {
            for (const tab of tabs) {
                try {
                    chrome.tabs.sendMessage(tab.id, {
                        type: 'OMNI_SET_NATIVE_PLAYER',
                        enabled: nativePlayerEnabled
                    });
                } catch (e) { /* ignore */ }
            }
        }
    });
}

function pollNativeSettings() {
    try {
        chrome.runtime.sendNativeMessage('omniApp', { type: 'GET_NATIVE_PLAYER_STATE' }, (response) => {
            if (response && response.hasOwnProperty('enabled')) {
                const newState = !!response.enabled;
                if (newState !== nativePlayerEnabled) {
                    nativePlayerEnabled = newState;
                    console.log('[background.js] Native player preference updated from app:', nativePlayerEnabled);
                    broadcastStateToTabs();
                }
            }
        });
    } catch (e) {
        // Polling failed (app might be starting up), ignore and retry
    }
}

// Poll settings every 2 seconds
setInterval(pollNativeSettings, 2000);
pollNativeSettings(); // Run immediately on start

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'GET_NATIVE_PLAYER_STATE') {
        sendResponse({ enabled: nativePlayerEnabled });
        return true;
    } else if (message.type === 'GET_TAB_MEDIA') {
        const tabId = sender.tab ? sender.tab.id : null;
        if (tabId && tabMediaMap.has(tabId)) {
            const items = Array.from(tabMediaMap.get(tabId)).map(item => JSON.parse(item));
            sendResponse(items);
        } else {
            sendResponse([]);
        }
        return true;
    } else if (message.type === 'MEDIA_GRABBED') {
        const url = message.url;
        // Skip blob: URLs as they can't be downloaded via HTTP
        if (url && !url.startsWith('blob:')) {
            reportToNative(url, message.mimeType || 'video/mp4', sender.tab ? sender.tab.id : undefined);
        }
    } else if (message.type === 'VIDEO_STATE_CHANGE') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'VIDEO_STATE_CHANGE',
                isPlaying: message.isPlaying
            });
        } catch (e) {
            console.error('[Omni] Native message failed for video state:', e);
        }
    } else if (message.type === 'PLAY_IN_NATIVE') {
        // Relay to native app — this triggers navigation to the native VideoPlayerScreen
        console.log('[background.js] Received PLAY_IN_NATIVE from content script for URL:', message.url);
        try {
            console.log('[background.js] Dispatching sendNativeMessage to omniApp with PLAY_IN_NATIVE...');
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'PLAY_IN_NATIVE',
                url: message.url,
                pageUrl: message.pageUrl,
                mimeType: message.mimeType || 'video/mp4'
            });
            console.log('[background.js] sendNativeMessage successfully dispatched.');
        } catch (e) {
            console.error('[Omni] Native message failed for PLAY_IN_NATIVE:', e);
        }
    } else if (message.type === 'CONSOLE_LOG') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'CONSOLE_LOG',
                level: message.level,
                message: message.message
            });
        } catch (e) {
            console.error('[Omni] Native message failed:', e);
        }
    }
});
