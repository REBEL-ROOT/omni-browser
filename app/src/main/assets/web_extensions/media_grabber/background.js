// background.js
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'MEDIA_GRABBED') {
        console.log('[MediaGrabber] Captured stream link:', message.url);
        
        // Send message to our Kotlin Host app using native messaging
        chrome.runtime.sendNativeMessage('omniApp', {
            type: 'MEDIA_GRABBED',
            url: message.url,
            mimeType: message.mimeType || 'video/mp4'
        });
    }
});
