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

  function imageToBase64(img) {
    try {
      var canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth || img.width || 300;
      canvas.height = img.naturalHeight || img.height || 400;
      var ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0);
      return canvas.toDataURL("image/jpeg", 0.85);
    } catch (e) {
      return null;
    }
  }

  function translateImages() {
    if (!enabled) return;
    var imgs = document.querySelectorAll("img");
    imgs.forEach(function (img, idx) {
      if (img.dataset && img.dataset.omniImgTranslated === "1") return;
      var w = img.naturalWidth || img.width || 0;
      var h = img.naturalHeight || img.height || 0;
      // Target manga / comic pages or prominent graphics (>= 180x180)
      if (w < 180 || h < 180) return;

      var b64 = imageToBase64(img);
      if (!b64) return;

      var imgId = "img_" + idx + "_" + (img.src ? img.src.substring(img.src.lastIndexOf("/") + 1).slice(0, 20) : "page");
      browser.runtime.sendMessage({
        nativeApp: "omniTranslate",
        type: "translateImage",
        imageId: imgId,
        base64: b64
      }).then(function (resp) {
        try {
          var parsed = typeof resp === "string" ? JSON.parse(resp) : resp;
          if (parsed && parsed.translatedSrc) {
            if (!img.dataset.omniOriginalSrc) img.dataset.omniOriginalSrc = img.src;
            img.src = parsed.translatedSrc;
            img.dataset.omniImgTranslated = "1";
          }
        } catch (e) { /* keep original */ }
      }).catch(function () {});
    });
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
    var imgs = document.querySelectorAll("img[data-omni-img-translated='1']");
    imgs.forEach(function (img) {
      if (img.dataset.omniOriginalSrc) {
        img.src = img.dataset.omniOriginalSrc;
        delete img.dataset.omniOriginalSrc;
      }
      delete img.dataset.omniImgTranslated;
    });
  }

  function translateOnce() {
    if (!enabled) return;
    var segs = collect();
    if (segs.length) {
      browser.runtime.sendMessage({ nativeApp: "omniTranslate", type: "translate", segments: segs })
        .then(function (resp) {
          try { applyMap(JSON.parse(resp)); } catch (e) { /* keep original */ }
        })
        .catch(function () { /* ignore */ });
    }
    translateImages();
  }

  function startLive() {
    if (observer) return;
    observer = new MutationObserver(function (mutations) {
      var hasNew = false;
      for (var m = 0; m < mutations.length; m++) {
        mutations[m].addedNodes.forEach(function (node) {
          if (node.nodeType === 3 && node.nodeValue && node.nodeValue.trim()) hasNew = true;
          else if (node.nodeType === 1 && node.querySelector && (node.querySelector("body, div, span, p, a, li, td, h1, h2, h3") || node.tagName === "IMG")) hasNew = true;
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
