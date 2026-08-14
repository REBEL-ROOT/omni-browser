# Offline AI Models

Omni does **not** host model weights. Models come from documented upstream hosts
whose licenses permit redistribution and use. No model is bundled in the APK; the
application carries only a tiny catalog (`assets/ai/models_catalog.json`) with
metadata, and downloads + verifies + atomically installs the actual weights into
application-private storage.

## Model record format

Each catalog entry records:

| Field | Meaning |
| --- | --- |
| `id` / `version` | stable identity |
| `task` | `translation` / `asr` |
| `name` | display name |
| `sourceLanguage` / `targetLanguage` | language pair (null = language-neutral) |
| `sizeBytes` | exact byte size (0 = unknown; size check skipped, see below) |
| `downloadUrl` | upstream HTTPS URL (allow-list restricted host) |
| `sha256` | pinned SHA-256; when null the file is verified by size only and flagged UNVERIFIED in the UI |
| `license` / `sourceProject` | license & upstream project |
| `minimumRuntimeVersion` | required engine/runtime version |

## Integrity pipeline

```
download → size validation → SHA-256 → (optional signature) → atomic activation
```

- Downloads resume via HTTP `Range` (`model.bin.partial`).
- Verification failure ⇒ file deleted/rejected, never activated.
- Updates download & verify the new version independently; the old version stays
  until the new one verifies (failed update never removes a working model).
- Model requests never carry webpage cookies/headers/origin.

## Bundled catalog

### Vosk small English (US) — ASR

- Purpose: offline speech-to-text captions.
- Languages: English (US).
- Source project: Vosk (Alpha Cephei).
- Download source: `https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip`
  (stable, well-known upstream URL).
- Code license: Apache-2.0 (Vosk runtime).
- Model license: Apache-2.0 (permissive; redistribution permitted).
- Size: ~50 MB (recorded as unknown `sizeBytes=0` in this build; should be pinned
  in production).
- Checksum: not pinned in this build ⇒ size-only verification, surfaced as
  UNVERIFIED in the UI.

### Translation models (downloaded, lexicon tier)

Translation models are now listed in the catalog and installed through the same
shared model platform as ASR. They ship as `asset://` lexicon models (a JSON
word→translation map) so they install fully offline with no external host. The
`ModelBackedTranslationEngine` loads each installed translation model from
`ModelStorage` and the `TranslationEngineManager` prefers it (higher quality)
over the bundled lexicon — so a downloaded translation model is actually used
for translation instead of falling back to Google.

When a compatible native NMT runtime (Bergamot / OPUS-MT) is wired in, a real
neural model can be added to the catalog the same way and will be preferred
automatically. See `OFFLINE_TRANSLATION_ARCHITECTURE.md`.

## Adding a model safely

1. Verify code + model license, redistribution rights, commercial-use
   restrictions, ARM64/Android compatibility, maintenance status.
2. Add a catalog entry with an allow-listed HTTPS host and, ideally, a pinned
   SHA-256 and exact `sizeBytes`.
3. Ship only the metadata (no weights).

## Unverified models

When `sha256`/`sizeBytes` are absent the download is still performed only from
the allow-listed catalog host, but the file cannot be cryptographically verified;
the UI must warn the user before install. Production builds should pin real
checksums for every model.
