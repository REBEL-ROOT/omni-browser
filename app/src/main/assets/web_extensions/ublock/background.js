// Omni Ad Blocker Background Script
// Intercepts and blocks common ad networks and telemetry domains natively in the engine.

const BLOCKED_DOMAINS = [
    "*://*.doubleclick.net/*",
    "*://*.google-analytics.com/*",
    "*://*.googlesyndication.com/*",
    "*://*.adservice.google.com/*",
    "*://*.adservice.google.co.in/*",
    "*://*.adservice.google.co.uk/*",
    "*://*.analytics.google.com/*",
    "*://*.adnxs.com/*",
    "*://*.pubmatic.com/*",
    "*://*.outbrain.com/*",
    "*://*.taboola.com/*",
    "*://*.adcolony.com/*",
    "*://*.unityads.unity3d.com/*",
    "*://*.amazon-adsystem.com/*"
];

chrome.webRequest.onBeforeRequest.addListener(
    function(details) {
        console.log("Omni Blocker blocked ad request: " + details.url);
        return { cancel: true };
    },
    { urls: BLOCKED_DOMAINS },
    ["blocking"]
);
