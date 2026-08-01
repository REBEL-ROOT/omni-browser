/*
 * Omni Proxy Router — background script.
 *
 * Routes the browser's HTTP(S) traffic through the active Tor / SOCKS proxy by
 * implementing the WebExtension `proxy` API (browser.proxy.onRequest). The app
 * tells us the current SOCKS endpoint via native messaging; we cache it and
 * hand it back per request.
 *
 * Why this exists: on GeckoView/Android the `network.proxy.*` preferences do NOT
 * route HTTP(S) through a SOCKS proxy (the network layer goes direct), so the
 * only reliable way to proxy traffic is this API — the same approach used by
 * WebLibre. Returning the endpoint here also fails CLOSED: if the SOCKS port is
 * not yet reachable the request errors out instead of silently leaking the real
 * IP over a direct connection.
 *
 * Pull model: the app has no app->extension push channel, so we poll the app for
 * the current endpoint (mirrors how media_grabber polls GET_NATIVE_PLAYER_STATE).
 *
 * IMPORTANT: this extension uses its OWN native-messaging name "omniProxy"
 * (NOT "omniApp", which media_grabber owns). Sharing the name across two
 * extensions' setMessageDelegate breaks routing in GeckoView.
 */

// Normalize the API object exactly like the working media_grabber extension, so
// we use whichever global this GeckoView build actually populates.
const api = (typeof browser !== "undefined") ? browser : chrome;

const TAG = "[ProxyRouter]";
const NATIVE_APP = "omniProxy";

// Cached SOCKS endpoint from the app, or null when traffic should go direct.
let endpoint = null;
// True once we have heard back from the app at least once (so the first request
// after install never leaks direct while we are still fetching the endpoint).
let ready = false;
// A single in-flight refresh promise, so concurrent early requests share one
// native round-trip instead of spamming sendNativeMessage.
let pending = null;
// Throttle onRequest logging so we get evidence without flooding logcat.
let lastDecision = null;
let requestCount = 0;

console.log(TAG, "background script loaded; api.proxy present =", !!(api && api.proxy));

function parseReply(raw) {
  // The app may return a JSON string or an already-parsed object (GeckoView
  // delivers the GeckoResult value as-is). Handle both, like media_grabber.
  let obj = raw;
  if (typeof raw === "string") {
    try { obj = JSON.parse(raw); } catch (e) { return undefined; }
  }
  if (!obj || typeof obj !== "object") return undefined;
  const host = obj.host;
  const port = obj.port;
  if (!host || typeof host !== "string" || !port) return null; // explicit direct
  return { host: host, port: Number(port) };
}

function refresh() {
  if (pending) return pending;
  pending = new Promise((resolve) => {
    try {
      api.runtime.sendNativeMessage(NATIVE_APP, { type: "GET_PROXY_ENDPOINT" })
        .then((raw) => {
          const parsed = parseReply(raw);
          // parsed === undefined  -> malformed reply, keep last endpoint
          // parsed === null       -> app says "direct"
          // parsed === {host,port}-> route through it
          if (parsed !== undefined) endpoint = parsed;
          ready = true;
          console.log(TAG, "refresh ok ->", endpoint ? (endpoint.host + ":" + endpoint.port) : "DIRECT");
          resolve();
        })
        .catch((e) => {
          ready = true;
          console.error(TAG, "sendNativeMessage failed:", e && e.message);
          resolve();
        });
    } catch (e) {
      ready = true;
      console.error(TAG, "sendNativeMessage threw:", e && e.message);
      resolve();
    }
  }).then(() => { pending = null; });
  return pending;
}

function proxyFor() {
  if (!endpoint) return [];
  // type "socks" == SOCKS5 in the proxy API. proxyDNS routes DNS through the
  // proxy (remote DNS, like Tor's socks_remote_dns). failoverTimeout 0 disables
  // any silent fallback to a direct connection.
  return [{
    type: "socks",
    host: endpoint.host,
    port: endpoint.port,
    proxyDNS: true,
    failoverTimeout: 0
  }];
}

function registerProxyApi() {
  if (!api || !api.proxy || !api.proxy.onRequest) {
    console.error(TAG, "PROXY API UNAVAILABLE on this build — cannot route via extension. api.proxy =", api && api.proxy);
    return false;
  }
  try {
    api.proxy.onRequest.addListener(
      (details) => {
        const url = details && details.url ? details.url : "";
        // Only HTTP(S) goes through the proxy; let other schemes (about:, data:,
        // moz-extension:, etc.) go direct.
        if (!/^https?:/i.test(url)) return [];
        let result;
        if (!ready) {
          // Until the first native reply lands, await it so we never leak direct
          // on a cold start.
          result = (pending || refresh()).then(proxyFor);
        } else {
          result = proxyFor();
        }
        // Throttled evidence log: first 3 requests, then only when the decision
        // (direct vs proxied) flips.
        requestCount++;
        const decision = endpoint ? ("PROXY " + endpoint.host + ":" + endpoint.port) : "DIRECT";
        if (requestCount <= 3 || decision !== lastDecision) {
          console.log(TAG, "onRequest #" + requestCount, decision, url.substring(0, 80));
          lastDecision = decision;
        }
        return result;
      },
      { urls: ["<all_urls>"] }
    );
    if (api.proxy.onError) {
      api.proxy.onError.addListener((e) => {
        console.error(TAG, "proxy.onError:", e && e.message, "-> refreshing endpoint");
        refresh();
      });
    }
    console.log(TAG, "proxy.onRequest registered");
    return true;
  } catch (e) {
    console.error(TAG, "FAILED to register proxy.onRequest:", e && e.message);
    return false;
  }
}

const proxyOk = registerProxyApi();

// Initial fetch, then keep the cached endpoint in sync with the app's state
// (provider switches, connect/disconnect, bridge/custom-proxy edits). Only poll
// if we could register the proxy API; otherwise polling is pointless.
if (proxyOk) {
  refresh();
  setInterval(refresh, 1000);
}
