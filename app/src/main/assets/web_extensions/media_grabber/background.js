// background.js — media detection and Quetta-style video handoff bridge

const api = typeof browser !== "undefined" ? browser : chrome;
var chrome = api;

// Path 1: Network-level webRequest interception

const MEDIA_URL_PATTERNS = [
    /\.m3u8(\?|$|#)/i,
    /\.mpd(\?|$|#)/i,
    /\.mp4(\?|$|#)/i,
    /\.webm(\?|$|#)/i,
    /\.mp3(\?|$|#)/i,
    /\.m4a(\?|$|#)/i,
    /\.m4v(\?|$|#)/i,
    /\/\d+\.ts(\?|$|#)/i,
    /seg[\w-]*\.ts(\?|$|#)/i,
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
    if (url.includes('pixel') || url.includes('beacon') || url.includes('analytics')) return false;
    if (url.startsWith('moz-extension:') || url.startsWith('chrome-extension:')) return false;
    
    return MEDIA_URL_PATTERNS.some(pattern => pattern.test(url));
}

function reportToNative(url, mimeType, tabId) {
    if (reportedUrls.has(url)) return;
    reportedUrls.add(url);
    
    if (reportedUrls.size > 500) {
        const first = reportedUrls.values().next().value;
        reportedUrls.delete(first);
    }

    const sendReport = (cookieString) => {
        if (tabId !== undefined && tabId !== null && tabId >= 0) {
            if (!tabMediaMap.has(tabId)) {
                tabMediaMap.set(tabId, new Set());
            }
            const mediaItem = JSON.stringify({ url: url, mimeType: mimeType || 'video/mp4', cookies: cookieString || '' });
            const tabSet = tabMediaMap.get(tabId);
            tabSet.add(mediaItem);
            
            if (tabSet.size > 50) {
                const firstItem = tabSet.values().next().value;
                tabSet.delete(firstItem);
            }
        }

        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'MEDIA_GRABBED',
                url: url,
                mimeType: mimeType || 'video/mp4',
                cookies: cookieString || '',
                tabId: (tabId !== undefined && tabId !== null) ? String(tabId) : ''
            }).catch(() => {});
        } catch (e) {
            console.error('[MediaGrabber] Native message failed:', e);
        }

        if (tabId !== undefined && tabId >= 0) {
            try {
                chrome.tabs.sendMessage(tabId, {
                    type: 'NETWORK_MEDIA_DETECTED',
                    url: url,
                    mimeType: mimeType || 'video/mp4',
                    cookies: cookieString || ''
                });
            } catch (e) {}
        }
    };

    try {
        chrome.cookies.getAll({ url: url }, (cookiesList) => {
            let cookiesStr = "";
            if (cookiesList && cookiesList.length > 0) {
                cookiesStr = cookiesList.map(c => `${c.name}=${c.value}`).join('; ');
            }
            sendReport(cookiesStr);
        });
    } catch (e) {
        sendReport("");
    }
}

// Intercept network requests
chrome.webRequest.onBeforeRequest.addListener(
    function(details) {
        const url = details.url;
        if (!url) return;
        if (isMediaUrl(url)) {
            reportToNative(url, classifyUrl(url), details.tabId);
        }
    },
    { urls: ["<all_urls>"] },
    []
);

// Intercept response headers
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
                    const contentLength = responseHeaders.find(h => h.name.toLowerCase() === 'content-length');
                    const size = contentLength ? parseInt(contentLength.value, 10) : -1;
                    if (size > 0 && size < 50000) return;
                    
                    reportToNative(url, contentType, details.tabId);
                }
                break;
            }
        }
    },
    { urls: ["<all_urls>"] },
    ["responseHeaders"]
);

// Path 2: Native Player preferences & command polling
let nativePlayerEnabled = true;
let youtubeEnabled = false;

function broadcastStateToTabs() {
    chrome.tabs.query({}, (tabs) => {
        if (tabs && tabs.length > 0) {
            for (const tab of tabs) {
                try {
                    chrome.tabs.sendMessage(tab.id, {
                        type: 'OMNI_SET_NATIVE_PLAYER',
                        enabled: nativePlayerEnabled,
                        youtubeEnabled: youtubeEnabled
                    });
                } catch (e) {}
            }
        }
    });
}

function pollNativeSettings() {
    try {
        chrome.runtime.sendNativeMessage('omniApp', { type: 'GET_NATIVE_PLAYER_STATE' })
            .then((rawResponse) => {
                const response = typeof rawResponse === 'string' ? JSON.parse(rawResponse) : rawResponse;
                if (response && response.hasOwnProperty('enabled')) {
                    const newState = !!response.enabled;
                    if (newState !== nativePlayerEnabled) {
                        nativePlayerEnabled = newState;
                        broadcastStateToTabs();
                    }
                    if (response.hasOwnProperty('youtubeEnabled')) {
                        const newYt = !!response.youtubeEnabled;
                        if (newYt !== youtubeEnabled) {
                            youtubeEnabled = newYt;
                            broadcastStateToTabs();
                        }
                    }
                    
                    if (response.pendingJs) {
                        chrome.tabs.query({ active: true }, (tabs) => {
                            if (tabs && tabs[0]) {
                                chrome.tabs.sendMessage(tabs[0].id, {
                                    type: 'EVAL_JS',
                                    script: response.pendingJs
                                }).catch(() => {});
                            }
                        });
                    }
                }
            })
            .catch(() => {});
    } catch (e) {}
}

setInterval(pollNativeSettings, 30000);
pollNativeSettings();

// Message listener
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || !message.type) return;

    if (message.type === 'GET_NATIVE_PLAYER_STATE') {
        sendResponse({ enabled: nativePlayerEnabled, youtubeEnabled: youtubeEnabled });
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
    } else if (message.type === 'REQUEST_HANDOFF') {
        const url = message.url;
        const pageUrl = message.pageUrl || (sender.tab ? sender.tab.url : '');
        const tabId = sender.tab ? String(sender.tab.id) : '';

        const sendHandoff = (cookieString) => {
            const handoffObj = message.handoff || {};
            handoffObj.cookies = cookieString || '';
            handoffObj.tabId = tabId || handoffObj.tabId || '';
            handoffObj.pageUrl = pageUrl || handoffObj.pageUrl || '';

            try {
                chrome.runtime.sendNativeMessage('omniApp', {
                    type: 'REQUEST_HANDOFF',
                    url: url,
                    pageUrl: pageUrl,
                    tabId: tabId,
                    associatedStreams: message.associatedStreams || [],
                    mimeType: message.mimeType || 'video/mp4',
                    handoff: handoffObj
                }).catch((err) => {
                    console.error('[background.js] Error sending REQUEST_HANDOFF:', err);
                });
            } catch (e) {
                console.error('[background.js] Native message failed for REQUEST_HANDOFF:', e);
            }
        };

        if (url && (url.startsWith('http://') || url.startsWith('https://'))) {
            try {
                chrome.cookies.getAll({ url: url }, (cookiesList) => {
                    let cookiesStr = "";
                    if (cookiesList && cookiesList.length > 0) {
                        cookiesStr = cookiesList.map(c => `${c.name}=${c.value}`).join('; ');
                    }
                    sendHandoff(cookiesStr);
                });
            } catch (e) {
                sendHandoff("");
            }
        } else {
            sendHandoff("");
        }
    } else if (message.type === 'REQUEST_DOWNLOAD') {
        const url = message.url;
        const pageUrl = message.pageUrl || (sender.tab ? sender.tab.url : '');
        const tabId = sender.tab ? String(sender.tab.id) : '';

        const sendDownload = (cookiesStr) => {
            try {
                chrome.runtime.sendNativeMessage('omniApp', {
                    type: 'REQUEST_DOWNLOAD',
                    url: url,
                    pageUrl: pageUrl,
                    tabId: tabId,
                    associatedStreams: message.associatedStreams || [],
                    mimeType: message.mimeType || 'video/mp4',
                    title: message.title || '',
                    videoId: message.videoId || '',
                    requestId: message.requestId || '',
                    cookies: cookiesStr || ''
                }).catch((err) => {
                    console.error('[background.js] Error sending REQUEST_DOWNLOAD:', err);
                });
            } catch (e) {
                console.error('[background.js] Native message failed for REQUEST_DOWNLOAD:', e);
            }
        };

        if (url && (url.startsWith('http://') || url.startsWith('https://'))) {
            try {
                chrome.cookies.getAll({ url: url }, (cookiesList) => {
                    let cookiesStr = "";
                    if (cookiesList && cookiesList.length > 0) {
                        cookiesStr = cookiesList.map(c => `${c.name}=${c.value}`).join('; ');
                    }
                    sendDownload(cookiesStr);
                });
            } catch (e) {
                sendDownload("");
            }
        } else {
            sendDownload("");
        }
    } else if (message.type === 'HANDOFF_RESTORED') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'HANDOFF_RESTORED',
                sessionId: message.sessionId,
                videoId: message.videoId,
                currentTimeMs: message.currentTimeMs,
                isPlaying: message.isPlaying
            }).catch(() => {});
        } catch (e) {
            console.error('[background.js] Native message failed for HANDOFF_RESTORED:', e);
        }
    } else if (message.type === 'MEDIA_GRABBED') {
        const url = message.url;
        if (url && !url.startsWith('blob:')) {
            reportToNative(url, message.mimeType || 'video/mp4', sender.tab ? sender.tab.id : undefined);
        }
    } else if (message.type === 'VIDEO_STATE_CHANGE') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'VIDEO_STATE_CHANGE',
                isPlaying: message.isPlaying
            }).catch(() => {});
        } catch (e) {}
    } else if (message.type === 'INNER_SCROLL_STATE') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'INNER_SCROLL_STATE',
                isScrolled: message.isScrolled
            }).catch(() => {});
        } catch (e) {}
    } else if (message.type === 'PLAY_IN_NATIVE') {
        const sendPlayInNative = (cookieString) => {
            try {
                chrome.runtime.sendNativeMessage('omniApp', {
                    type: 'PLAY_IN_NATIVE',
                    url: message.url,
                    pageUrl: message.pageUrl,
                    mimeType: message.mimeType || 'video/mp4',
                    cookies: cookieString || '',
                    tabId: sender.tab ? String(sender.tab.id) : ''
                }).catch(() => {});
            } catch (e) {}
        };

        try {
            chrome.cookies.getAll({ url: message.url }, (cookiesList) => {
                let cookiesStr = "";
                if (cookiesList && cookiesList.length > 0) {
                    cookiesStr = cookiesList.map(c => `${c.name}=${c.value}`).join('; ');
                }
                sendPlayInNative(cookiesStr);
            });
        } catch (e) {
            sendPlayInNative("");
        }
    } else if (message.type === 'CONSOLE_LOG') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'CONSOLE_LOG',
                level: message.level,
                message: message.message
            }).catch(() => {});
        } catch (e) {}
    } else if (message.type === 'FOCUS_LOGIN_INPUT') {
        try {
            chrome.runtime.sendNativeMessage('omniApp', {
                type: 'FOCUS_LOGIN_INPUT',
                url: sender.tab ? sender.tab.url : ''
            }).catch(() => {});
        } catch (e) {}
    }
});
