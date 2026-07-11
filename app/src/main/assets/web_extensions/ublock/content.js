/**
 * Omni Browser — Cosmetic Ad Filter (Content Script)
 *
 * Runs in every page to:
 *   1. Hide ad container elements (slots, banners, iframes from ad servers).
 *   2. Remove cookie consent / GDPR banners that obscure content.
 *   3. Block push-notification permission prompts injected by ad scripts.
 *
 * This runs AFTER the DOM is available and uses MutationObserver to catch
 * ads injected dynamically (infinite scroll, lazy load, SPA navigation).
 *
 * NOTE: This script does NOT block network requests — that is handled by
 * background.js via the webRequest API. This script only hides elements.
 */

(function () {
    "use strict";

    // ── Selectors for ad containers to hide ──────────────────────────────────
    const AD_SELECTORS = [
        // Generic ad slot class names & IDs
        "[id*='google_ads']",
        "[id*='googletag']",
        "[id*='dfp-ad']",
        "[id*='ad-slot']",
        "[id*='adslot']",
        "[id*='banner-ad']",
        "[id*='leaderboard-ad']",
        "[id*='sidebar-ad']",
        "[id*='inline-ad']",
        "[id*='ad-container']",
        "[class*='adsbygoogle']",
        "[class*='ad-unit']",
        "[class*='ad-banner']",
        "[class*='ad-wrapper']",
        "[class*='ad-container']",
        "[class*='ad-slot']",
        "[class*='ad-block']",
        "[class*='ad-sidebar']",
        "[class*='ad-top']",
        "[class*='ad-bottom']",
        "[class*='ad-interstitial']",
        "[class*='advertisement']",
        "[class*='advert']",
        "[class*='sponsored-content']",
        "[class*='sponsored-link']",
        "[class*='sponsored-post']",
        "[class*='native-ad']",
        "[class*='outbrain']",
        "[class*='taboola']",
        "[class*='mgid']",

        // Specific ad network containers
        "ins.adsbygoogle",
        "div[data-google-query-id]",
        "div[data-ad-slot]",
        "div[data-ad-unit]",
        "div[data-outbrain-section-id]",
        "div[data-taboola-target-id]",
        "div[id^='taboola']",
        "div[id^='outbrain']",
        "div[id^='mgbox']",
        "div[class^='mgbox']",

        // Cookie consent / GDPR banners
        "[id*='cookie-banner']",
        "[id*='cookie-consent']",
        "[id*='cookie-notice']",
        "[id*='cookie-popup']",
        "[id*='cookie-bar']",
        "[id*='gdpr']",
        "[class*='cookie-banner']",
        "[class*='cookie-consent']",
        "[class*='cookie-notice']",
        "[class*='cookie-popup']",
        "[class*='cookie-bar']",
        "[class*='gdpr-banner']",
        "[class*='consent-banner']",
        "[class*='consent-notice']",
        "[class*='privacy-banner']",
        "[class*='cmp-popup']",
        "[id*='onetrust']",
        "[class*='onetrust']",
        "[id*='sp_message']",
        "[class*='sp_message']",
        "[class*='qc-cmp']",
        "[id*='qc-cmp']",
        "[class*='didomi']",
        "[id*='didomi']",
        "#cookie-law-info-bar",
        "#cookie-notice",
        "#cookiebanner",
        "#cookie_banner",
        ".cookie_banner",
        ".cc-banner",
        ".cc-window",
        "#consent_blackbar",

        // Push notification overlays (injected by adware)
        "[class*='push-notification-prompt']",
        "[id*='push-prompt']",
        "[class*='notification-prompt']",
        "[class*='webpush-onsite']",
        "[id*='onesignal-slidedown']",
        "[class*='onesignal']",

        // Generic popup overlays from ad scripts
        "[class*='interstitial']",
        "[id*='interstitial']",
        "[class*='overlay-ad']",
        "[id*='overlay-ad']",
        "[class*='fullpage-ad']",
        "[id*='fullpage-ad']",
    ];

    // ── CSS injection for immediate hiding (before JS runs) ──────────────────
    const COSMETIC_CSS = AD_SELECTORS.join(",\n") + " { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; overflow: hidden !important; }";

    function injectCosmeticCSS() {
        try {
            const style = document.createElement("style");
            style.id = "omni-cosmetic-filter";
            style.textContent = COSMETIC_CSS;
            (document.head || document.documentElement).appendChild(style);
        } catch (e) {
            // Ignore — some pages restrict style injection
        }
    }

    // ── Forcibly remove matched elements (some sites re-show via JS) ─────────
    function removeAdElements() {
        for (const selector of AD_SELECTORS) {
            try {
                document.querySelectorAll(selector).forEach(function (el) {
                    // Only remove elements with no meaningful text content
                    // (avoid nuking nav bars that happen to have "ad" in class)
                    const text = (el.innerText || "").trim();
                    if (text.length < 300) {
                        el.remove();
                    } else {
                        el.style.cssText = "display:none!important;height:0!important;overflow:hidden!important";
                    }
                });
            } catch (e) {
                // Invalid selector in some contexts — skip
            }
        }
    }


    // ── Block automatic redirects via meta refresh + JS location changes ──────
    function blockAutoRedirects() {
        // Remove meta refresh tags
        document.querySelectorAll('meta[http-equiv="refresh"]').forEach(function (el) {
            const content = (el.getAttribute("content") || "").toLowerCase();
            // Only remove if it points to 0 or very short delay (ad trick)
            if (/^0;/.test(content) || /^1;/.test(content)) {
                el.remove();
            }
        });
    }

    // ── MutationObserver: catch dynamically inserted ads ─────────────────────
    function observeDynamicAds() {
        const observer = new MutationObserver(function (mutations) {
            let shouldClean = false;
            for (const mutation of mutations) {
                if (mutation.addedNodes.length > 0) {
                    shouldClean = true;
                    break;
                }
            }
            if (shouldClean) {
                removeAdElements();
            }
        });

        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
        });
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    injectCosmeticCSS();


    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            removeAdElements();
            blockAutoRedirects();
            observeDynamicAds();
        });
    } else {
        removeAdElements();
        blockAutoRedirects();
        observeDynamicAds();
    }
})();
