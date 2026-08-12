# Offline Captions

On-device closed captions for videos, powered by the shared model platform.

## Sources

1. **Existing captions** — if the native player / page exposes a subtitle track,
   use it directly (`SubtitleOrigin.NATIVE`). ASR is never run unnecessarily.
2. **Generated captions** — when no usable subtitles exist and Omni can
   legitimately access the audio stream of a supported (non-protected) source:

   ```
   audio stream → PCM chunks → AsrEngine → WordTimestamp[]
        → CaptionSegmenter → CaptionSegment[] → WebVtt → Media3 SubtitleConfiguration
   ```

3. **Translated captions** — existing or generated captions can be translated
   fully on-device via `CaptionTranslation` (reuses the offline MT provider;
   `OFFLINE_ONLY` never hits a cloud translation service).

## ASR engine selection

Candidates evaluated: Vosk (compact, ~50 MB small models, Apache-2.0, good
real-time factor for captions) and Whisper-family mobile runtimes (higher quality,
larger footprint). The architecture exposes an `AsrEngine` interface; **Vosk** is
the currently catalogued model (bundled catalog entry `vosk-small-en-us`), loaded
from `ModelStorage` after a verified download. The engine is intentionally **not
bundled** in the APK and the shortlist rationale is recorded in
`OFFLINE_AI_MODELS.md`.

## Caption timing

`CaptionSegmenter` splits word timestamps into cues using:

- speech pauses (`maxGapMs`),
- maximum cue duration (`maxDurationMs`),
- maximum character length (`maxChars`).

Output is standard WebVTT (`HH:MM:SS.mmm → HH:MM:SS.mmm`).

## Seek / pause / resume

`SubtitleController` cancels in-flight ASR on seek/disable/destroy and discards
stale results via a monotonic seek token — skipped audio is never transcribed.

## Privacy & DRM

- Caption generation is fully on-device; audio is never uploaded.
- Protected/DRM media is never captured; if the audio cannot be legitimately
  accessed the UI reports "Offline captions unavailable for this video."
- No microphone permission is requested as a workaround.

## Supported media

Works when Omni can safely access a supported audio stream (e.g. local/non-DRM
Media3 playback). It is **not** universal: DRM-backed services, in-page MSE
playback without an accessible audio pipeline, and sites that block subtitle
access may be unsupported.

## Validation status

The pure pipeline (segmenter, WebVTT, caption translation, seek-token logic) is
covered by JVM unit tests. The end-to-end audio tap → model → Media3
`SubtitleConfiguration` wiring requires on-device verification and a downloaded
model; see the README "Known limitations."
