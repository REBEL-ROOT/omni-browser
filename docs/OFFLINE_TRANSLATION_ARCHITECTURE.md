# Offline Translation Architecture

This document records the engine-selection decision for Omni Browser's offline
webpage translation (master prompt Phase 3) and the resulting architecture.

## Requirements recap

- Fully on-device translation; no cloud call in `OFFLINE_ONLY` mode.
- No large model weights bundled in the base APK.
- Models downloaded from legitimate upstream sources, checksum-verified, and
  atomically installed into application-private storage.
- Lazy loading: AI runtimes must not initialise during normal browser startup.
- Pluggable: future local-AI features reuse the same model/platform layer.

## Candidates evaluated

### A. Bergamot / Marian-derived NMT (C++/native, often via ONNX/TFLite)
- **Quality:** high (neural MT, good for many pairs).
- **License:** Marian is MIT; Bergamot runtime is MPL-2.0; OPUS-MT model weights
  are mostly permissively licensed (Apache-2.0 / MPL-2.0) — all compatible with
  Omni's GPL app.
- **Size/RAM:** compact quantized models (~20–80 MB per pair), ARM64-friendly.
- **Integration:** requires shipping a native inference runtime in the app
  (ONNX Runtime Mobile or TFLite). That runtime is a library, not model weights,
  so it does not violate "no large model in APK" — but it adds APK size and NDK
  complexity that must be measured before shipping.
- **Maintenance:** upstream OPUS-MT / Bergamot models are actively maintained.

### B. Lexicon/dictionary baseline (bundled tiny resources)
- **Quality:** low (word-by-word, no grammar/reordering).
- **License:** our own bundled resources.
- **Size/RAM:** negligible.
- **Integration:** pure Kotlin, zero native deps, works offline out-of-the-box.

## Decision

We adopt a **pluggable engine architecture** (`OfflineTranslationEngine`) and ship
**two tiers**:

1. **Bundled lexicon engine** (`LexiconTranslationEngine`) — the guaranteed
   offline baseline. It needs no download, satisfies "offline translation works
   out-of-the-box," and demonstrates the full pipeline (provider → coordinator →
   engine → DOM update). Quality is intentionally limited and is **not** marketed
   as a real NMT.
2. **Model-backed NMT engine** (reserved) — when a user installs a Bergamot/
   OPUS-MT model through the shared model platform, a registered
   `OfflineTranslationEngine` loads it from `ModelStorage` and runs inference via
   a native backend. The `TranslationEngineManager` automatically prefers the
   higher-quality model-backed engine for a pair when installed.

The native NMT runtime is intentionally **not** bundled yet: it must be added
behind a measured APK-size/NDK gate and validated on-device before it becomes the
default. Until then the lexicon engine is the active offline baseline and the
model platform is ready to fetch a real NMT model the moment a compatible runtime
is wired in.

## Why not just call the existing Google endpoint offline?
The previous translator is cloud-only and cannot run without network. It is kept
as the `OnlineTranslationProvider` for `ONLINE_ONLY` / `ASK` modes; it is never
selected in `OFFLINE_ONLY`.

## Data flow

```
GeckoView page
  → WebTranslationController (extract text nodes, skip script/style/code/pre/password)
  → TranslationCoordinator (applies TranslationMode)
      → OfflineTranslationProvider
          → TranslationEngineManager (pick best engine for pair)
              → LexiconTranslationEngine  (or model-backed NMT when installed)
      → (else, in ASK/ONLINE_ONLY) OnlineTranslationProvider (Google gtx)
  → DOM textContent update (original preserved for reversal)
```

## Privacy guarantees
- `OFFLINE_ONLY` resolves only to `OfflineTranslationProvider`; the coordinator
  throws `UnsupportedLanguagePairException` rather than falling back to cloud.
- Model URLs come solely from the application-controlled catalog; webpage JS
  cannot supply or alter them.
- No webpage cookies/headers/origin are attached to model requests.
