/*
 * Omni Translate content script.
 *
 * Security model:
 *  - This script runs in the PAGE's content process but has NO access to the
 *    app's model runtime, file storage, or download manager. It can only send
 *    plain text to the app (via runtime.sendMessage) and receive translated
 *    text back. It can never choose a model URL or trigger installation.
 *  - It only acts after the app dispatches the `omni-translate-start` event
 *    (user-initiated), so arbitrary page scripts cannot silently use it.
 *  - It never translates nodes already marked translated, preventing loops.
 */
(function () {
  var SKIP = { SCRIPT: 1, STYLE: 1, CODE: 1, PRE: 1, NOSCRIPT: 1, TEXTAREA: 1, OPTION: 1 };
  var enabled = false;
  var observer = null;

  function acceptNode(n) {
    if (!n.nodeValue || !n.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
    var p = n.parentElement;
    while (p) {
      var tag = p.tagName;
      if (SKIP[tag]) return NodeFilter.FILTER_REJECT;
      if (p.dataset && p.dataset.omniTranslated === "1") return NodeFilter.FILTER_REJECT;
      if (tag === "INPUT" && p.type && (p.type === "password" || p.type === "hidden")) return NodeFilter.FILTER_REJECT;
      p = p.parentElement;
    }
    return NodeFilter.FILTER_ACCEPT;
  }

  function collect() {
    var out = [];
    var i = 0;
    var root = document.body || document.documentElement;
    if (!root) return out;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, { acceptNode: acceptNode }, false);
    var n;
    while ((n = w.nextNode())) { out.push({ i: i, text: n.nodeValue }); i++; }
    return out;
  }

  function applyMap(map) {
    var j = 0;
    var root = document.body || document.documentElement;
    if (!root) return;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, { acceptNode: acceptNode }, false);
    var n;
    while ((n = w.nextNode())) {
      var key = "" + j;
      if (map[key] !== undefined) {
        var el = n.parentElement;
        if (el && el.dataset && el.dataset.omniOriginal === undefined) el.dataset.omniOriginal = n.nodeValue;
        n.nodeValue = map[key];
        if (el) el.dataset.omniTranslated = "1";
      }
      j++;
    }
  }

  function restore() {
    var root = document.body || document.documentElement;
    if (!root) return;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
    var n;
    while ((n = w.nextNode())) {
      var el = n.parentElement;
      if (el && el.dataset && el.dataset.omniTranslated === "1" && el.dataset.omniOriginal !== undefined) {
        n.nodeValue = el.dataset.omniOriginal;
        delete el.dataset.omniTranslated;
        delete el.dataset.omniOriginal;
      }
    }
  }

  function translateOnce() {
    if (!enabled) return;
    var segs = collect();
    if (!segs.length) return;
    browser.runtime.sendMessage({ nativeApp: "omniTranslate", type: "translate", segments: segs })
      .then(function (resp) {
        try { applyMap(JSON.parse(resp)); } catch (e) { /* keep original */ }
      })
      .catch(function () { /* ignore */ });
  }

  function startLive() {
    if (observer) return;
    observer = new MutationObserver(function (mutations) {
      var hasNew = false;
      for (var m = 0; m < mutations.length; m++) {
        mutations[m].addedNodes.forEach(function (node) {
          if (node.nodeType === 3 && node.nodeValue && node.nodeValue.trim()) hasNew = true;
          else if (node.nodeType === 1 && node.querySelector && node.querySelector("body, div, span, p, a, li, td, h1, h2, h3")) hasNew = true;
        });
      }
      if (hasNew) translateOnce();
    });
    observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  }

  document.addEventListener("omni-translate-start", function () {
    enabled = true;
    translateOnce();
    startLive();
  });
  document.addEventListener("omni-translate-stop", function () {
    enabled = false;
    if (observer) { observer.disconnect(); observer = null; }
    restore();
  });
})();
