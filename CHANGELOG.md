# Changelog

All notable changes to the Omni Browser project will be documented in this file.

## [1.2.3] - 2026-07-11

### Added
- **Popup & Ad-Tab Blocker**: New dedicated toggle in the browser toolbar to silently block auto-jumping ad tabs, pop-unders, and redirect pop-ups — without interfering with normal page navigation.
- **Engine-Level Popup Blocking**: GeckoView `onNewSession` now intercepts and drops popup requests from 30+ known ad networks and `about:blank` hijack patterns before any tab is opened.
- **Expanded Ad & Tracker Blocklist**: Upgraded the built-in `background.js` blocklist from 70 to 180+ domains covering major advertising, tracking, fingerprinting, and affiliate networks.
- **Cosmetic Ad Filtering**: New `content.js` injection layer removes ad-slot elements (banners, sticky ads, overlay ads) using a live `MutationObserver` — no page reload required.
- **`window.open()` Interception**: JavaScript-level interception of `window.open()` calls with an allowlist of trusted origins, blocking tab-hijack popups without breaking legitimate site flows.

### Fixed
- **F-Droid Compliance**: Added `distributionSha256Sum` to `gradle-wrapper.properties` to satisfy Gradle wrapper integrity checks required by the F-Droid scanner.
- **F-Droid Maven Repo**: Added a content filter for `maven.mozilla.org` in `settings.gradle.kts` to resolve the `unknown maven repo` scanner error.
- **Tracker Dependency Removed**: Explicitly excluded `io.opencensus` (flagged as a tracker by F-Droid) from all transitive Gradle dependencies.
- **Worker Lock File**: Added `worker/package-lock.json` to satisfy the F-Droid dependency-lock requirement.
- **Fastlane Metadata**: Added full Fastlane directory with app title, short description, long description, and version changelogs to enable automatic F-Droid catalogue updates.

### Security
- **Signing Key Rotation**: Release signing key has been rotated. The old key is revoked and no longer used.
- **Keystore Removed from Repo**: Signing keystore and all hardcoded credentials have been permanently purged from the entire git history using `git-filter-repo`.
- **CI-Only Signing**: APK signing now happens exclusively via encrypted GitHub Secrets on the Actions runner — the keystore never touches the repository.

---

## [1.2.2] - 2026-07-10

### Fixed
- **WebExtensions not working on pages**: Resolved a critical bug where all browser extensions (uBlock, Universal Copy, AI Blocker, Media Grabber) were silently inactive due to missing `enable()` call after installation.
- **Extensions disabled in Incognito mode**: All bundled and user-installed extensions are now explicitly allowed in private browsing sessions via `setAllowedInPrivateBrowsing(true)`.
- **Extension API namespace**: Fixed `chrome.webRequest` namespace compatibility issue — now uses a cross-engine fallback (`const api = typeof browser !== 'undefined' ? browser : chrome`).
- **Adblocker engine coverage**: Expanded blocked domains from 14 to 70+ major advertising, analytics, and tracker networks.

### Changed
- Visual theme polish: updated color tokens, rounded shape sizes, and full Material 3 typography scale.

---

## [1.2.1] - 2026-07-06

### Fixed
- **Edge-to-edge display**: Replaced deprecated `setDecorFitsSystemWindows(window, false)` and manual bar coloring with the modern `enableEdgeToEdge()` + `SystemBarStyle.auto()` API.
- **Large screen compatibility**: Declared `android:resizeableActivity="true"` to support tablets, foldables, and split-screen mode.

---

## [1.2.0] - 2026-07-05

### Added
- **Speak Aloud**: Added a "Speak Aloud" option to the text selection context menu using Android TextToSpeech.
- **Select All fix**: Fixed "Select All" text selection using GeckoSession native action with JS fallback.
- **Incognito Tab Groups**: Separated Normal and Incognito tabs into distinct groups in the tab switcher.
- **Redesigned Onboarding**: Replaced flat onboarding illustrations with circular-cropped, cream-themed 3D artwork.
- **Light Mode Logo**: Added a high-contrast dark-metallic version of the logo for light theme.

### Changed
- Language card selection: Fixed square outline bug by switching from `Modifier.clickable` to native `Surface(onClick = ...)`.
- License: Replaced MIT with **GNU General Public License v3 (GPLv3)**.
- All Kotlin source files now include the standard GPLv3 copyright header.

---

## [1.1.1] - 2026-06-29

### Added
- **Developer Offline Pad & Vault**: Integrated a completely local, secure, and offline scratchpad and credentials vault.
- **Responsive Flow Quick Tools Grid**: Upgraded the Quick Tools sheet layout to a modern, fully-responsive dynamic grid using `FlowRow`.
- **Interactive Developer Console REPL**: Added support for executing arbitrary JavaScript code dynamically within the page context.
- **Global User-Agent Bypasses**: Configured persistent Firefox Mobile/Desktop User-Agent strings globally for all sessions.
- **UPI & App Chooser Integration**: Integrated Android's app chooser via `Intent.createChooser()` for all custom protocols.
- **Fallback URL Redirection**: Enabled extraction and navigation to `S.browser_fallback_url` embedded in payment gateways' `intent://` links.

### Optimized
- **Unified Video Playback Interception**: Optimized HTML5 video player detection and premium takeover across all websites.
- **Single-Tap Premium Takeover**: Integrated a direct "Play in Premium Player" button onto the page-level media detection banner.

---

## [1.1.0] - 2026-06-28

### Added
- **Language Selector & Setup**: Implemented a startup language selection flow and Accept-Language HTTP headers.

### Fixed
- **GeckoView Page Blanking**: Resolved compositor freezes and blank page rendering issues when navigating back.
- **UPI Payments & Custom Schemes**: Intercepted and routed custom protocol URLs to external applications safely.
- **Homepage News Category Loading**: Updated the Google News RSS feed category paths, resolving the `400 Bad Request` issue.

---

## [1.0.9] - 2026-06-22

### Added
- **OLED Dark Mode & Theme Toggle**: Added a global dynamic light/dark theme toggle and a pure OLED black theme option.
- **Search Engine Dropdown**: Added an interactive search engine selector dropdown directly on the homepage search bar.
- **WireGuard Import**: Added a WireGuard VPN configuration importer directly in settings.
- **Quick Tools**: Integrated a Quick Tools bottom sheet.
- **Tab Swipe Gestures**: Added bottom bar tab swipe gestures for seamless navigation.

### Fixed
- **Extensions Popups**: Resolved issues preventing web extension popups from opening.
- **Toolbar & Alignment**: Fixed alignments on the toolbar and bottom navigation bar.

---

## [1.0.0] - 2026-05-30

### Added
- **Multi-Tab system**: Real Chrome-like horizontal scrollable Tab Bar chip row at the top left.
- **On-Device Browser History**: Local JSON-based secure persistence with dynamic date formatting and filtering SearchBar.
- **Video Sniffer & Player**: Integrated Google Media3 ExoPlayer with full swipe gesture controllers and PiP mode.
- **Biometric Secure Locker**: Encryption standard AES Keystore vault room.
- **On-Device Translator**: Device-local machine learning offline ES/EN translation backed by Google ML Kit.
- **Adblocker (uBlock Origin)**: Fully integrated add-ons manager list panel supporting Firefox Android Extensions.
- **Document Scanner**: Auto-perspective paper document scanner backed by ML Kit.

### Fixed
- **Settings Exit Bug**: Added safe popBackStack fallback checking for previous entry.
- **Incognito Relocation**: Relocated private browsing toggles to tools dropdown.
- **Session Open Crash**: Refactored GeckoSession binding to use Compose update block with safe `!session.isOpen` check.
