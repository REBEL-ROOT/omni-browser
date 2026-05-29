// content.js
// Inject our page context hook script to override MediaSource prototypes
const script = document.createElement('script');
script.src = chrome.runtime.getURL('inject.js');
script.onload = function() {
    this.remove();
};
(document.head || document.documentElement).appendChild(script);

// Listen for messages dispatched by our page-level hook script
window.addEventListener('message', (event) => {
    // Only accept messages from our own window context
    if (event.source !== window) return;

    if (event.data && event.data.type === 'MSE_MEDIA_STREAM_GRABBED') {
        chrome.runtime.sendMessage({
            type: 'MEDIA_GRABBED',
            url: event.data.url,
            mimeType: event.data.mimeType
        });
    }
});
