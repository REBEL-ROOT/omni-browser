# Omni Browser — Seamless Media Handoff

**Status**: Phases 2-7 complete, pushed to main  
**Branch**: `main`  
**Tests**: 192 passing (180 bookmark + 12 handoff)  
**Last updated**: 2026-08-12

---

## What This Fixes

Before: When a user opened a website video in Omni Player, playback restarted at `0:00` with a new ExoPlayer session, losing the live position.

After: Omni captures the live `<video>` element state (position, speed, volume, mute, play/pause) and restores it in the native player.

---

## Architecture

```
Website <video>
    │ play / fullscreen
    ▼
inject.js — captureVideoState()
    │ currentTime, duration, paused, playbackRate, volume, muted
    ▼
REQUEST_HANDOFF → BrowserViewModel_Extensions
    │
    ▼ MediaSourceClassifier.classify()
    ├─ Supported (MP4, WebM, HLS, DASH, YouTube)
    │  → PAUSE_AND_LAUNCH → JS pauses video
    │  → pendingHandoff stored
    │  → VideoPlayerScreen launched
    │
    └─ Unsupported (blob:, data:, DRM)
       → RESUME_WEBSITE → video keeps playing

VideoPlayerScreen
    │ consumePendingHandoff()
    ▼
ExoPlayer — seekTo(livePosition)
    │ setPlaybackParameters(speed)
    │ volume = handoff.volume
    │ playWhenReady = !handoff.isPaused
    ▼
Seamless playback continues
```

---

## File Map

| File | Purpose |
|------|---------|
| `media/handoff/MediaHandoff.kt` | Immutable handoff model with live state |
| `media/handoff/MediaSourceType.kt` | Source classification enum with `isSupported` |
| `media/handoff/MediaSourceClassifier.kt` | URL/MIME pattern classifier (pure Kotlin) |
| `media/handoff/MediaHandoffManager.kt` | Staleness detection, tab matching, validation |
| `media/player/VideoPlayerScreen.kt` | Consumes handoff, restores state in ExoPlayer |
| `browser/BrowserViewModel.kt` | `pendingHandoff` state, `consumePendingHandoff()` |
| `browser/BrowserViewModel_Extensions.kt` | `handleRequestHandoff()`, `sendJsMessage()` |
| `assets/web_extensions/media_grabber/inject.js` | `captureVideoState()`, `REQUEST_HANDOFF` flow |

---

## Handoff State Priority (in VideoPlayerScreen)

```
1. Live MediaHandoff.currentPositionMs
2. Persisted Omni position (fallback)
3. 0L
```

---

## Supported vs Unsupported Sources

| Source Type | Handoff? | Fallback |
|-------------|----------|----------|
| `.mp4`, `.m4v` | ✅ Yes | — |
| `.webm` | ✅ Yes | — |
| `.m3u8` (HLS) | ✅ Yes | — |
| `.mpd` (DASH) | ✅ Yes | — |
| YouTube | ✅ Yes | — |
| `blob:` (MSE) | ❌ No | Keep website playing |
| `data:` | ❌ No | Keep website playing |
| DRM-protected | ❌ No | Keep website playing |

---

## Security & Limits

- **Consume-once**: `consumePendingHandoff()` clears the state after first read
- **Tab-scoped**: Handoff includes `tabId` for validation
- **Staleness**: Handoffs older than 30 seconds are rejected
- **URI matching**: Handoff must match the expected source URI
- **No sensitive data logged**: Only source type, position, duration — never cookies or auth tokens

---

## Testing

| Test File | Tests | Focus |
|-----------|-------|-------|
| `MediaSourceClassifierTest.kt` | 12 | URL/MIME classification, DRM detection, support matrix |
| `BookmarkCollectionTest.kt` | 32 | (existing) |
| ... | ... | 180 bookmark tests continue passing |

---

## Known Limitations

1. **Cannot share Chromium connection** — ExoPlayer creates its own HTTP stack
2. **Blob/MSE non-transferable** — browser-internal buffers cannot be handed off
3. **DRM blocked** — Widevine/PlayReady stays in website player
4. **Position precision** — JS `currentTime` has ~50-200ms precision
5. **No persistent handoff storage** — handoffs are in-memory only, lost on app kill

---

## Future Work

- Phase 8-11 hardening: Add `MediaHandoffLogger` with structured events
- Phase 12-14: Live stream edge cases, multiple video detection, PiP integration
- Phase 15: Expand test coverage to integration/E2E tests
