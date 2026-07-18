/**
 * background.js — AI Blocker dynamic DOM removal
 *
 * Many search engines inject their AI answer panels after the initial HTML parse
 * (via JS framework hydration). A CSS-only approach with run_at: document_start
 * hides panels that are already in the DOM, but dynamically injected ones appear
 * briefly before being hidden. This MutationObserver script removes them from the
 * DOM entirely so there is no flash-of-content.
 */

"use strict";

// CSS selectors that identify AI answer/overview containers.
// Keep in sync with override.css.
const AI_SELECTORS = [
  // Google AI Overview / SGE
  "div.ULSxyf", "div.Gy5B3f", "div.M8OgIe", "div.L3Ezfd", "div.WpKAof",
  "div.d6VFfa", "div.K7pOsc", "div.e3Fmkd", "div.TzHB6b", "div.rHmby",
  "div.v9i61e", "div.NFQFxe", "div.bNg8Rb", "div.Kno3md", "div.c2xzTb",
  "div.KST9uc", "div.RqBzHd", "div.sg-answ",
  "div[data-sgai]",
  "div[data-la*='ai-overview']",
  "div[data-la*='generative']",
  "div[data-attrid*='ai_overview']",
  "g-section-with-header:has(a[href*='generative-ai'])",
  "g-section-with-header:has(a[href*='search-labs'])",
  // Brave Leo
  "div.search-result-answer", "div.answer-card", "div.answer-wrapper",
  "div.ai-summary", "div.leo-chat-container", "div.brave-ai-response",
  "aside.answer-box",
  // Bing Copilot
  "div#b_copilot", "div.b_ans_chat", "div.copilot-chat-container",
  "div.b_algo_copilot", "div#b_sydConvCont", "div.b_aiSuggest",
  // DuckDuckGo DuckAssist
  "div.duckassist", "div.duckassist-wrapper", "div.duckassist-card",
  // Generic
  "div.ai-overview-container", "div.ai-block", "div.ai-response",
  "div.generative-ai-box", "aside.ai-assistant",
];

function removeAiElements(root) {
  if (!root || typeof root.querySelectorAll !== "function") return;
  try {
    AI_SELECTORS.forEach(function(sel) {
      try {
        root.querySelectorAll(sel).forEach(function(el) {
          el.remove();
        });
      } catch (_) {
        // :has() is not supported in all Gecko versions — skip silently
      }
    });
  } catch (_) {}
}

// Initial sweep once the content script is active
removeAiElements(document);

// Watch for dynamically added nodes (Google/Bing lazy-load their AI panels)
const observer = new MutationObserver(function(mutations) {
  mutations.forEach(function(mutation) {
    mutation.addedNodes.forEach(function(node) {
      if (node.nodeType === Node.ELEMENT_NODE) {
        removeAiElements(node);
        // Also check the node itself against our selector list
        AI_SELECTORS.forEach(function(sel) {
          try {
            if (node.matches && node.matches(sel)) {
              node.remove();
            }
          } catch (_) {}
        });
      }
    });
  });
});

observer.observe(document.documentElement, { childList: true, subtree: true });
