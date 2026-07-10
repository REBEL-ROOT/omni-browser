/**
 * Omni Browser — Built-in Ad & Tracker Blocker
 *
 * This extension hooks into GeckoView's webRequest API to block outbound network
 * requests to known advertising, analytics, and telemetry domains before they
 * leave the device. It runs as a GeckoView built-in extension (resource://android),
 * meaning it loads from the APK assets — not from addons.mozilla.org.
 *
 * Implementation notes:
 *   - Uses the MV2 webRequest blocking API (supported by GeckoView's built-in extension host).
 *   - The `api` alias handles both the `browser` namespace (Firefox/GeckoView standard)
 *     and the `chrome` namespace (Chromium-origin extensions) for compatibility.
 *   - Domain list is intentionally kept as a flat pattern array for low overhead.
 *     GeckoView's webRequest listener is evaluated per-request at the engine level,
 *     so keeping the ruleset simple avoids memory churn in the extension context.
 *   - For cosmetic filtering (hiding ad slots, cookie banners), see content.js.
 *
 * To add a new blocked domain: append "*://*.example.com/*" to BLOCKED_DOMAINS.
 */

const BLOCKED_DOMAINS = [
    // ── Google Advertising & Analytics ───────────────────────
    "*://*.doubleclick.net/*",
    "*://*.google-analytics.com/*",
    "*://*.googlesyndication.com/*",
    "*://*.adservice.google.com/*",
    "*://*.adservice.google.co.in/*",
    "*://*.adservice.google.co.uk/*",
    "*://*.analytics.google.com/*",
    "*://*.googletagservices.com/*",
    "*://*.googletagmanager.com/*",

    // ── Programmatic Ad Exchanges ─────────────────────────────
    "*://*.adnxs.com/*",          // Xandr (Microsoft Advertising)
    "*://*.pubmatic.com/*",
    "*://*.rubiconproject.com/*",
    "*://*.openx.net/*",
    "*://*.criteo.com/*",
    "*://*.casalemedia.com/*",
    "*://*.smartadserver.com/*",
    "*://*.adform.net/*",
    "*://*.bidswitch.net/*",
    "*://*.adnxs-simple.com/*",
    "*://*.yieldlab.net/*",

    // ── Native / Content Recommendation Networks ─────────────
    "*://*.outbrain.com/*",
    "*://*.taboola.com/*",
    "*://*.revcontent.com/*",
    "*://*.mgid.com/*",

    // ── Direct Ad Networks ────────────────────────────────────
    "*://*.adcolony.com/*",
    "*://*.unityads.unity3d.com/*",
    "*://*.amazon-adsystem.com/*",
    "*://*.popads.net/*",
    "*://*.popcash.net/*",
    "*://*.exponential.com/*",
    "*://*.media.net/*",
    "*://*.buysellads.com/*",
    "*://*.carbonads.net/*",
    "*://*.adzerk.net/*",
    "*://*.aaxads.com/*",
    "*://*.adbutler.com/*",
    "*://*.adkernel.com/*",
    "*://*.admixer.net/*",
    "*://*.adpushup.com/*",
    "*://*.adroll.com/*",
    "*://*.adscale.de/*",
    "*://*.advertising.com/*",
    "*://*.applovin.com/*",

    // ── Mobile Ad SDKs ────────────────────────────────────────
    "*://*.flurry.com/*",
    "*://*.inmobi.com/*",
    "*://*.ironsrc.com/*",
    "*://*.mopub.com/*",        // Acquired by AppLovin, kept for legacy requests
    "*://*.fyber.com/*",

    // ── Analytics & Session Recording ────────────────────────
    "*://*.quantserve.com/*",
    "*://*.scorecardresearch.com/*",
    "*://*.chartbeat.com/*",
    "*://*.hotjar.com/*",
    "*://*.mixpanel.com/*",
    "*://*.segment.io/*",
    "*://*.optimizely.com/*",
    "*://*.amplitude.com/*",
    "*://*.statcounter.com/*",
    "*://*.histats.com/*",
    "*://*.clicky.com/*",
    "*://*.crazyegg.com/*",

    // ── Click Trackers & Misc ─────────────────────────────────
    "*://*.fastclick.net/*",
    "*://*.adtech.de/*",
    "*://*.adtarget.io/*",
    "*://*.affili.net/*",
    "*://*.alimama.com/*",
    "*://*.yandex.ru/clck/*",
    "*://*.mc.yandex.ru/*"
];

// Normalise the API surface between GeckoView (browser.*) and Chrome-origin environments.
const api = typeof browser !== "undefined" ? browser : chrome;

api.webRequest.onBeforeRequest.addListener(
    function(details) {
        // Block the request silently — no logging in production to avoid
        // leaking URLs to the extension's background page console.
        return { cancel: true };
    },
    { urls: BLOCKED_DOMAINS },
    ["blocking"]
);
