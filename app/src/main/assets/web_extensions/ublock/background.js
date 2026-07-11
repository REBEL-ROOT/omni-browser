/**
 * Omni Browser — Built-in Ad, Tracker & Popup Blocker
 *
 * This extension hooks into GeckoView's webRequest API to:
 *   1. Block outbound network requests to known ad/tracker domains.
 *   2. Block popup windows opened by ad scripts (window.open, target=_blank tricks).
 *
 * Implementation notes:
 *   - Uses MV2 webRequest blocking API (supported by GeckoView built-in extension host).
 *   - Popup blocking via webRequest.onBeforeRequest for new tabs opened by scripts.
 *   - Domain list covers major ad exchanges, SSPs, DSPs, analytics, and popup networks.
 *   - Cosmetic filtering is handled separately in content.js (injected via GeckoView).
 */

const api = typeof browser !== "undefined" ? browser : chrome;

// Blocklist domains
const BLOCKED_DOMAINS = [
    // Google ad domains
    "*://*.doubleclick.net/*",
    "*://*.google-analytics.com/*",
    "*://*.googlesyndication.com/*",
    "*://*.adservice.google.com/*",
    "*://*.adservice.google.co.in/*",
    "*://*.adservice.google.co.uk/*",
    "*://*.analytics.google.com/*",
    "*://*.googletagservices.com/*",
    "*://*.googletagmanager.com/*",
    "*://*.googleadservices.com/*",
    "*://*.pagead2.googlesyndication.com/*",
    "*://*.tpc.googlesyndication.com/*",

    // Programmatic SSPs
    "*://*.adnxs.com/*",           // Xandr (Microsoft Advertising)
    "*://*.pubmatic.com/*",
    "*://*.rubiconproject.com/*",
    "*://*.openx.net/*",
    "*://*.criteo.com/*",
    "*://*.casalemedia.com/*",     // Index Exchange
    "*://*.indexexchange.com/*",
    "*://*.smartadserver.com/*",
    "*://*.adform.net/*",
    "*://*.bidswitch.net/*",
    "*://*.adnxs-simple.com/*",
    "*://*.yieldlab.net/*",
    "*://*.33across.com/*",
    "*://*.sovrn.com/*",
    "*://*.lijit.com/*",
    "*://*.triplelift.com/*",
    "*://*.sharethrough.com/*",
    "*://*.teads.tv/*",
    "*://*.teads.com/*",
    "*://*.emxdgt.com/*",
    "*://*.rhythmone.com/*",
    "*://*.undertone.com/*",
    "*://*.synacor.com/*",
    "*://*.yahoo.com/admax/*",
    "*://*.advertising.yahoo.com/*",
    "*://*.ads.yahoo.com/*",
    "*://*.oath.com/*",
    "*://*.aol.com/ad/*",
    "*://*.verizonmedia.com/*",
    "*://*.tradedesk.com/*",
    "*://*.thetradedesk.com/*",
    "*://*.adsrvr.org/*",
    "*://*.rlcdn.com/*",
    "*://*.lkqd.net/*",
    "*://*.spotxchange.com/*",
    "*://*.spotx.tv/*",
    "*://*.springserve.com/*",
    "*://*.adtelligent.com/*",
    "*://*.appnexus.com/*",
    "*://*.contextweb.com/*",
    "*://*.pulsepoint.com/*",
    "*://*.conversantmedia.com/*",
    "*://*.dotomi.com/*",
    "*://*.freewheel.tv/*",
    "*://*.stickyadstv.com/*",

    // Content recommendations
    "*://*.outbrain.com/*",
    "*://*.taboola.com/*",
    "*://*.revcontent.com/*",
    "*://*.mgid.com/*",
    "*://*.content.ad/*",
    "*://*.zergnet.com/*",
    "*://*.around.io/*",

    // Popunder systems
    "*://*.popads.net/*",
    "*://*.popcash.net/*",
    "*://*.popunder.net/*",
    "*://*.trafficjunky.net/*",
    "*://*.juicyads.com/*",
    "*://*.exoclick.com/*",
    "*://*.plugrush.com/*",
    "*://*.adsterra.com/*",
    "*://*.propellerads.com/*",
    "*://*.hilltopads.net/*",
    "*://*.clickadu.com/*",
    "*://*.evadav.com/*",
    "*://*.megapush.com/*",
    "*://*.push.house/*",
    "*://*.richpush.co/*",
    "*://*.mgpusher.com/*",
    "*://*.pu.sh/*",
    "*://*.pushground.com/*",
    "*://*.subscribers.com/*",
    "*://*.onesignal.com/*",       // push notification spam
    "*://*.pushcrew.com/*",
    "*://*.pushengage.com/*",
    "*://*.sendpulse.com/*",
    "*://*.voluumdsp.com/*",
    "*://*.mondoagency.net/*",
    "*://*.trafficshop.com/*",
    "*://*.adf.ly/*",
    "*://*.j.gs/*",
    "*://*.link.tl/*",
    "*://*.za.gl/*",
    "*://*.ay.gy/*",
    "*://*.atominik.com/*",
    "*://*.adskeeper.com/*",
    "*://*.voluumtrk.com/*",
    "*://*.voluumtrk2.com/*",
    "*://*.hrefli.com/*",
    "*://*.linkvertise.com/*",

    // Ad networks
    "*://*.adcolony.com/*",
    "*://*.unityads.unity3d.com/*",
    "*://*.amazon-adsystem.com/*",
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
    "*://*.lijit.com/*",
    "*://*.districtm.io/*",
    "*://*.districtm.ca/*",
    "*://*.marfeel.com/*",
    "*://*.monetizemore.com/*",
    "*://*.setupad.com/*",
    "*://*.admanager.com/*",
    "*://*.bidvertiser.com/*",
    "*://*.yllix.com/*",
    "*://*.adhitz.com/*",
    "*://*.adhive.tv/*",
    "*://*.valueclick.com/*",
    "*://*.servedby.flashtalking.com/*",
    "*://*.flashtalking.com/*",

    // Mobile SDK integrations
    "*://*.flurry.com/*",
    "*://*.inmobi.com/*",
    "*://*.ironsrc.com/*",
    "*://*.mopub.com/*",
    "*://*.fyber.com/*",
    "*://*.chartboost.com/*",
    "*://*.vungle.com/*",
    "*://*.liftoff.io/*",
    "*://*.tapjoy.com/*",

    // Fingerprint trackers
    "*://*.quantserve.com/*",
    "*://*.scorecardresearch.com/*",
    "*://*.chartbeat.com/*",
    "*://*.hotjar.com/*",
    "*://*.mixpanel.com/*",
    "*://*.segment.io/*",
    "*://*.segment.com/*",
    "*://*.optimizely.com/*",
    "*://*.amplitude.com/*",
    "*://*.statcounter.com/*",
    "*://*.histats.com/*",
    "*://*.clicky.com/*",
    "*://*.crazyegg.com/*",
    "*://*.mouseflow.com/*",
    "*://*.clarity.ms/*",         // Microsoft Clarity heatmap
    "*://*.fullstory.com/*",
    "*://*.logrocket.com/*",
    "*://*.smartlook.com/*",
    "*://*.inspectlet.com/*",
    "*://*.heap.io/*",
    "*://*.counter.dev/*",
    "*://*.matomo.cloud/*",
    "*://*.piwik.pro/*",
    "*://*.kissmetrics.com/*",
    "*://*.intercom.io/*",        // chat + analytics
    "*://*.intercomcdn.com/*",
    "*://*.zdassets.com/*",
    "*://*.zendesk.com/*",        // support widget (can be re-enabled per site)

    // Redirects
    "*://*.fastclick.net/*",
    "*://*.adtech.de/*",
    "*://*.adtarget.io/*",
    "*://*.affili.net/*",
    "*://*.alimama.com/*",
    "*://*.yandex.ru/clck/*",
    "*://*.mc.yandex.ru/*",
    "*://*.yadro.ru/*",
    "*://*.rambler.ru/ad*",
    "*://*.cdn-cgi/ad*",

    // Social widgets
    "*://*.connect.facebook.net/*",
    "*://*.facebook.com/tr/*",
    "*://*.facebook.com/plugins/like*",
    "*://*.pixel.facebook.com/*",
    "*://*.twitter.com/i/adsct*",
    "*://*.t.co/track*",
    "*://*.linkedin.com/li/track*",
    "*://*.bat.bing.com/*",       // Bing UET tag
    "*://*.snap.licdn.com/*",
    "*://*.sc-static.net/*",      // Snapchat pixel
];

// Popups to block
const POPUP_BLOCKED_PATTERNS = [
    // Ad popup networks
    "*://*.popads.net/*",
    "*://*.popcash.net/*",
    "*://*.exoclick.com/*",
    "*://*.trafficjunky.net/*",
    "*://*.juicyads.com/*",
    "*://*.adsterra.com/*",
    "*://*.propellerads.com/*",
    "*://*.hilltopads.net/*",
    "*://*.clickadu.com/*",
    "*://*.evadav.com/*",
    "*://*.megapush.com/*",
    "*://*.adf.ly/*",
    "*://*.j.gs/*",
    "*://*.linkvertise.com/*",
    "*://*.doublelift.net/*",
    "*://*.trafficfactory.biz/*",
    "*://*.tsyndicate.com/*",
    // Catch-all for new tab popups that load about:blank and redirect
    // (GeckoView already exposes hasUserGesture; this is an extra filter)
];

if (api.webRequest.onBeforeRequest) {
    // Block network requests to ad domains
    api.webRequest.onBeforeRequest.addListener(
        function(details) {
            return { cancel: true };
        },
        { urls: BLOCKED_DOMAINS },
        ["blocking"]
    );
}

// Block popup windows that navigate to known ad networks
if (api.webRequest.onCreatedNavigationTarget) {
    api.webRequest.onCreatedNavigationTarget.addListener(
        function(details) {
            // details.url is where the popup wants to navigate
            const url = (details.url || "").toLowerCase();
            for (const pattern of POPUP_BLOCKED_PATTERNS) {
                // Convert glob pattern to simple domain match
                const domain = pattern.replace(/\*:\/\/\*\./g, "").replace(/\/\*$/, "");
                if (url.includes(domain)) {
                    // Remove the popup tab
                    if (api.tabs && api.tabs.remove) {
                        api.tabs.remove(details.tabId);
                    }
                    return;
                }
            }
        }
    );
}
