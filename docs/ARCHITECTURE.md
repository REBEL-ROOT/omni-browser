# 🏗️ Omni Browser — Architecture

This document describes the technical architecture of Omni Browser in detail.

---

## Overview

Omni Browser follows a **MVVM (Model-View-ViewModel)** pattern with **Unidirectional Data Flow (UDF)**, common in modern Jetpack Compose applications.

```
User Interaction → UI (Compose) → ViewModel → Engine (GeckoView) → Delegates → ViewModel → UI Update
```

---

## Layer Breakdown

### 1. UI Layer — Jetpack Compose

All UI is built using **Jetpack Compose with Material 3** components.

| File | Responsibility |
|---|---|
| `BrowserScreen.kt` | Main browser screen — address bar, tabs, menus, bottom sheets |
| `OnboardingScreen.kt` | First-launch onboarding slides with language selection |
| `SettingsScreen.kt` | App settings — theme, search engine, privacy options |
| `LanguageSelectionScreen.kt` | Language picker composable |

**Key patterns:**
- All state is observed via `by viewModel.property` using Compose `State`.
- Bottom sheets handle extensions, tabs, history, and tools.
- `GeckoView` is embedded via `AndroidView` composable.

---

### 2. ViewModel Layer

**`BrowserViewModel.kt`** is the central state holder and orchestrator.

It manages:
- `tabs: SnapshotStateList<TabState>` — live list of all open tabs
- `activeTabId: String?` — currently visible tab
- `isAdblockerEnabled`, `isIncognitoMode`, etc. — feature flags
- Extension state: `grabberExtension`
- Settings: desktop mode, search engine, theme preference

**Tab lifecycle:**
```
createNewTab() → GeckoSession(settings) → open(runtime) → bind(GeckoView) → TabState stored
```

---

### 3. Engine Layer — GeckoView

**`GeckoRuntime`** (singleton per process) is created once via `getGeckoRuntime(context)` with:
- `GeckoRuntimeSettings` (locale, debug flags, color scheme)
- `WebExtensionController.PromptDelegate` set to auto-approve extension install prompts

**`GeckoSession`** is created per tab with:
- `usePrivateMode(isIncognitoMode)` for private tabs
- `userAgentMode` / `viewportMode` for desktop switching
- All delegate callbacks routed back to `BrowserViewModel`

**Delegates implemented:**
| Delegate | Key Callbacks |
|---|---|
| `NavigationDelegate` | `onLoadRequest`, `onLocationChange`, `onCanGoBack/Forward` |
| `ContentDelegate` | `onTitleChange`, `onFullScreen`, `onCrash` |
| `ProgressDelegate` | `onPageStart`, `onPageStop`, `onProgressChange` |
| `SelectionActionDelegate` | `onShowActionRequest` (text selection menu) |
| `PermissionDelegate` | Camera, microphone, geolocation |
| `PromptDelegate` | Alert, confirm, auth, file chooser prompts |

---

### 4. WebExtensions

Extensions are managed by `WebExtensionController` on the `GeckoRuntime`.

| Extension | Type | Path |
|---|---|---|

| Media Grabber | Built-in | `assets/web_extensions/media_grabber/` |
| Universal Copy | Built-in (via `UniversalCopyManager`) | `assets/web_extensions/universal_copy/` |
| AI Blocker | Built-in (via `AiBlockerManager`) | `assets/web_extensions/ai_blocker/` |
| User extensions | Installed via `install(url)` | addons.mozilla.org |

All extensions use `setAllowedInPrivateBrowsing(ext, true)` to function in Incognito tabs.

---

### 5. Media Engine

**Stream detection (dual-path):**
1. `webRequest.onBeforeRequest` listener in `media_grabber/background.js` captures network-level HLS/DASH URLs.
2. MSE hooks in `media_grabber/content.js` intercept `MediaSource.addSourceBuffer` for blob-based streams.

Both paths relay detected media URLs to the native app via `chrome.runtime.sendNativeMessage('omniApp', {...})`.

**Native playback:**
- `VideoPlayerScreen.kt` wraps **Media3 ExoPlayer**.
- Supports: gesture brightness/volume, double-tap seek, speed control (0.25×–3×), PiP, background audio.
- FFmpegKit is used for audio extraction from non-standard containers.

---

### 6. Data Persistence

| Data | Storage |
|---|---|
| Browsing history | JSON file in app internal storage |
| Bookmarks | JSON file in app internal storage |
| Settings & preferences | AndroidX DataStore (Proto) |
| Vault files | AES-256 encrypted, Keystore-backed |
| Tabs (session restore) | JSON serialized TabState |

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   Compose UI (BrowserScreen)            │
│  Address Bar ──→ viewModel.loadUrl()                    │
│  Menu Tap    ──→ viewModel.toggleAdblock()              │
│  GeckoView   ←── AndroidView(factory={geckoView})       │
└──────────────────────┬──────────────────────────────────┘
                       │ State Observation
┌──────────────────────▼──────────────────────────────────┐
│               BrowserViewModel                          │
│  tabs (SnapshotStateList<TabState>)                     │
│  activeTabId, isAdblockerEnabled, isIncognitoMode       │
│  ──→ GeckoRuntime.webExtensionController                │
│  ──→ GeckoSession.loadUri()                             │
└──────────────────────┬──────────────────────────────────┘
                       │ Delegate Callbacks
┌──────────────────────▼──────────────────────────────────┐
│               GeckoSession Delegates                    │
│  onLocationChange → viewModel.updateTabUrl()            │
│  onTitleChange    → viewModel.updateTabTitle()          │
│  onLoadRequest    → route xpi/stream/deep-link          │
└─────────────────────────────────────────────────────────┘
```

---

## Threading

- All GeckoView callbacks fire on the **main thread** unless explicitly dispatched.
- Long-running operations (history load, file decryption, FFmpeg) use `viewModelScope.launch(Dispatchers.IO)`.
- Extension message handling in `NativeAppMessageDelegate` is dispatched back to main via `Handler(Looper.getMainLooper()).post {}`.
