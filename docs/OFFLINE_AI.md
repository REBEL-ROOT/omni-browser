# Omni Offline AI — Overview

Privacy-first, on-device AI platform for Omni Browser. No cloud inference, no
Omni-hosted model CDN, no large models in the base APK, no audio/page-text
upload when offline mode is selected.

## Modules

| Module | Location | Responsibility |
| --- | --- | --- |
| Translation providers | `ai/translation/` | `TranslationProvider` abstraction, `OnlineTranslationProvider` (original Google "gtx"), `OfflineTranslationProvider`, `TranslationCoordinator` (enforces `OFFLINE_ONLY`), `TranslationMode`. |
| Offline engines | `ai/engine/` | `OfflineTranslationEngine` interface, `TranslationEngineManager` (selects best engine), `LexiconTranslationEngine` (bundled offline baseline). |
| Model platform | `ai/models/` | `ModelCatalog`, `ModelDescriptor`, `ModelStorage`, `ModelVerifier` (SHA-256), `ModelDownloader` (resumable HTTP Range), `ModelRepository`, `ModelPlatform`, WorkManager `ModelDownloadWorker`. |
| Page translation | `ai/web/` | `WebTranslationController`, `OmniTranslateBridge`, `PageTranslationPlanner` (pure dedup/translate), `omni_translate` content-script extension. |
| Captions | `ai/asr/`, `ai/captions/` | `AsrEngine` abstraction, `CaptionSegmenter`, `WebVtt`, `SubtitleSource`, `CaptionTranslation`, `SubtitleController`. |

## Translation modes

- `OFFLINE_ONLY` — only `OfflineTranslationProvider` may run. If no offline engine
  supports the pair, translation **fails**; it never silently falls back to cloud.
- `ONLINE_ONLY` — only the online provider runs.
- `ASK` — prefers offline when a model exists for the pair, otherwise online.

Default is `ASK`, which preserves the previous (online-first) behaviour while
using the bundled lexicon offline where possible. Users can choose strict
`OFFLINE_ONLY` in Settings.

## Privacy guarantees

- Offline mode performs no network requests for translation or captions.
- Model downloads are application-owned: URLs come only from the bundled catalog,
  restricted to an allow-list of hosts. No cookies/headers/origin from webpages
  are attached, and webpage JS can never choose a model URL.
- The `omni_translate` content script only sends plain text to the app; it never
  receives model paths, files, or native runtime access, and only acts after a
  user-initiated event.
- Private (incognito) tabs never share translation/caption caches with normal
  tabs; every job is scoped by session/tab id.

## Lazy loading / performance

- No AI runtime or model is loaded at application startup.
- Engines load on first use and are released when a feature goes idle
  (`TranslationEngineManager.releaseAll`, `AsrEngine.unload`).
- Caption generation never blocks playback and cancels on seek/destroy.

## Known limitations

- The bundled offline translation baseline is a small lexicon engine (EN→ES/FR/DE),
  not a full NMT model. Real NMT would be added by downloading a model through
  the platform and registering a model-backed engine (see `OFFLINE_TRANSLATION_ARCHITECTURE.md`).
- ASR produces captions only when the app can legitimately access a supported
  audio stream **and** an ASR model is installed. DRM/protected media is never
  bypassed; it reports "offline captions unavailable".
- GeckoView integration details (extension messaging, DOM update) require
  on-device verification; the pure logic (planner, segmenter, WebVTT, verifier,
  downloader, repository) is covered by JVM unit tests.
