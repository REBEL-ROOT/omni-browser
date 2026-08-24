# Changelog

All notable changes to the Omni Browser project will be documented in this file.

## [1.2.9.5] - 2026-08-24

### Optimized
- **54% APK Size Reduction (~130 MB saved)**: Enabled native library compression (`useLegacyPackaging = true`) and stripped non-essential crash diagnostic binaries (`libminidump_analyzer.so`, `libcrashhelper.so`), cutting the `aarch64` release APK from 241 MB down to ~111 MB.

### Added
- **Bitwarden & Password Manager WebExtension Support**: Enabled dynamic extension permissions (`onOptionalPrompt`, `onUpdatePrompt` returning `ALLOW`) for full compatibility with Bitwarden, Proton Pass, 1Password, and KeePassXC WebExtensions.
- **Curated Password Managers**: Added official Bitwarden, Proton Pass, and KeePassXC-Browser entries to the curated extensions repository.
- **Omni Password Manager Master ON/OFF Toggle**: Added master switch in Password Manager screen and Privacy & Security settings with complete saving and autofill suppression when turned off.
- **GitHub APK Update Notification**: Automated system notifications with direct "Update Now" actions when a newer release is published on GitHub.
- **Download Notification Navigation**: Tapping in-progress download notification immediately opens Omni Browser's Downloads section; tapping completed download notification opens the file directly or navigates to Downloads.

---

## [1.2.9.2] - 2026-08-18

### Added
- **Android Autofill Framework & Third-Party Password Manager Integration**: Enabled standard Android Autofill Framework on GeckoView (`setAutofillEnabled(true)` and `IMPORTANT_FOR_AUTOFILL_YES`) alongside form autofill prefs (`signon.autofillForms`, `dom.forms.autocomplete.formautofill`, `extensions.formautofill.available`). Full compatibility with Bitwarden, 1Password, KeePass, Proton Pass, and Google Autofill.
- **Autofill Provider Preference Selector**: Added configuration option in *Settings > Privacy & Security* to choose between Android System Autofill, Omni Password Vault, or Both without input focus theft.
- **WebExtension `browser.downloads` Native Bridge**: Connected GeckoView 145's `WebExtension.DownloadDelegate` into Omni's `StreamDownloadEngine`, enabling WebExtensions (like Media Grabber) to initiate and manage native downloads.
- **Extension Download Security & Policy**: Added download confirmation prompts, filename sanitization against path traversal, safe URI scheme validation (`http`, `https`, `blob`, `data`), and configurable extension download policies (*Ask every time*, *Always allow trusted extensions*, *Never allow*).
- **HTTP Range Resumable Downloads**: Upgraded `StreamDownloadEngine` with `Range: bytes=X-` partial content resumption, automatic fallback on HTTP 200, Range Not Satisfiable (HTTP 416) handling, and download persistence across browser restarts.
- **Enhanced Download Manager UX**: Added inline Retry/Resume buttons on interrupted downloads and 3-dot dropdown menu actions (Retry/Resume, Pause, Open source page, Copy download link, Share, Rename, Delete).
- **Quetta-Style Native Video Integration & Handoff**: Seamless two-way state and position restoration when switching between web players and native ExoPlayer, with 3.5s auto-hide controls, tap-to-reveal, and gesture pass-through.
- **Fast Scroll Pill Geometry & Touch Controls**: Unified Safari-style fast scroll capsule indicator with smooth geometry calculation, draggable native thumb, and edge touch filtering.
- **100% Multilingual Localization Parity**: Synchronized and translated all new strings across 11 supported locales (`en`, `ar`, `de`, `es`, `fr`, `hi`, `ja`, `pl`, `pt`, `ru`, `zh`).

### Fixed
- **Google & YouTube OAuth Authentication**: Resolved popup and redirect handling for Google, YouTube, Apple, and Microsoft identity providers (#85).
- **YouTube Web Fullscreen**: Fixed video fullscreen transitions and fallback injection in YouTube web player.

---

## [1.2.8.3] - 2026-08-10

### Added
- **Deep Link & Native App Delegation**: Added automatic detection and redirection for supported URLs (`https://reddit.com/r/...`, `https://youtube.com/watch...`, `intent://`, `mailto:`, etc.) to installed native Android applications with `Intent.FLAG_ACTIVITY_NEW_TASK`.
- **Browser-Style Deep Link Permission Dialog**: Prompts user with an explicit `"Open in [App Name]?"` permission dialog before opening external apps, supporting *"Always allow for this site"*, *"Cancel / Stay in Browser"*, and per-site permission management.

### Fixed
- **External Intent Back-Button Task Stack Return**: Fixed back-gesture behavior when Omni Browser is launched via external `ACTION_VIEW` intents (e.g. from RSS reader apps, email clients, or social apps). Automatically finishes the activity and returns control directly to the caller's task stack when no web history remains.
- **Enhanced Dark & Dark AMOLED Theme Scripts**: Upgraded site style theme scripts using modern open-source smart-inversion techniques with media element preservation (`img`, `video`, `canvas`, `svg image`, `picture`, `[style*="background-image"]`), nested iframe protection, and native `<meta name="color-scheme" content="dark">` injection.
- **Light-Mode White Flash Elimination**: Injected site style theme scripts into `document.head || document.documentElement` at `onLocationChange`, `onPageStart`, and early progress (5%) to apply DOM styling on the very first paint frame before `<body>` renders.

---

## [1.2.8.1] - 2026-08-06

### Added
- **App Lifecycle & Browser Optimizations**: Private tab creation no longer modifies global incognito state in the background. UI scale, wallpaper URI, and navigation params pre-load synchronously to prevent layout flash on app start.
- **Enhanced Permission UX & Security**: Brave/Chrome-style rationale pre-permission sheets with 3-button permission choices (Allow, Allow once, Don't allow). Biometric vault key cryptographically bound to Android Keystore (AES-256-GCM).
- **YouTube Media Stream Extractor**: Multi-client fallback chain (Android, Android VR, TVHTML5) for YouTube video and audio stream extraction using InnerTube API.
- **Android RoleManager Helper**: System RoleManager helper for Android Q+ to manage default browser prompt and role verification.
- **Redesigned Onboarding & Proxy Hub**: Material 3 Expressive onboarding UI with dedicated Proxy Hub selection (Direct, Built-in Tor, Custom SOCKS5).

### Fixed
- **Direct GitHub OTA Update Checking**: Single authoritative call to GitHub Releases API with ABI-aware APK asset parsing for reliable update checks.
- **Safari-Style Context Menu & Page Menu**: Live WebView preview in context menu, copy clean link, open in private tab, Google Lens, and refined 3-dot dropdown for Top/Split navigation bars.

---

## [1.2.7.1] - 2026-08-02

### Added
- **Chrome & Brave-Style Downloads Overhaul**: Redesigned Downloads screen with real-time storage metrics (`Using X MB of Y GB`), live search query bar, scrollable category filter chips (*All, Videos, Audio, Images, Documents, APKs, Other*), and 3-dot item context menus (**Share**, **Rename**, **Delete**).
- **Default Quick Scroll Buttons**: Enabled floating scroll shortcuts (Scroll to Top / Scroll to Bottom) by default across the browser.
- **Authentic News Photography & Multi-Tier Fallback Chain**: Replaced all synthetic AI stock photos with genuine editorial photography across all categories (*Top Stories, Technology, Business, World, Sports, Science, Entertainment, Health, Astrology, Recipes*). Implemented a 3-tier photo fallback chain with an editorial press fallback banner.
- **Enhanced GitHub Releases OTA Updates Engine**: Implemented dual-mode version checking (`version.json` + direct GitHub Releases API fallback) and ABI-aware APK asset parsing for seamless background updates and one-tap package installation.

### Fixed
- **Android 11+ Package Visibility & External File Opener**: Resolved "no app available to open this file" issue by adding package queries (`ACTION_VIEW`, `ACTION_SEND`, `ACTION_INSTALL_PACKAGE`) to `AndroidManifest.xml` and explicit `grantUriPermission()` calls across resolved target apps.
- **Physical File Deletion & Vault Synchronization**: Fixed `deleteDownload()` to physically delete files from local storage, remove MediaStore records, and sync deletion and renaming with the encrypted SQLCipher Room vault (`PrivateLockerManager.kt`).
- **Built-in Force Dark WebExtension & Glitch Fixes**: Renamed extension to `"Omni Force Dark Theme"` (`omni-force-dark@omnibrowser.app`), fixed Google Search dark mode glitches via native `color-scheme: dark` injection and `PREF=f6=400` cookie setting.

---

## [1.2.6.7] - 2026-08-01

### Fixed
- **Direct Connection Network Loading**: Fixed `network.proxy.type: 0` (No Proxy) bypassing Android's DNS resolver and system network stack — changed to type `5` (System Proxy) so direct browsing respects the device's network configuration.
- **Seamless Dynamic Proxy & Tor Routing**: `currentProxyEndpoint()` now checks `TorState.Connected` before arming SOCKS proxy endpoints. Automatically falls back to system proxy whenever Tor is off/disconnected and re-arms routing as soon as Tor connects.
- **Proxy Router WebExtension**: Fixed `proxy.onRequest` handler to return `[]` (defer to GeckoView system settings) instead of `[{ type: "direct" }]` which explicitly bypassed Android system proxy.

---

## [1.2.6.4] - 2026-07-27

### Fixed
- **Automated GitHub Release Pipeline**: Resolved asset upload failures on immutable release tags by enhancing release draft creation, automatic fallback version naming, and uploading signed APK assets directly to draft releases.

---

## [1.2.6.3] - 2026-07-27

### Added
- **Dedicated Theme Bottom Sheet**: Introduced a quick-access Theme bottom sheet containing Theme Mode options (Light, Dark, AMOLED), an interactive App Nav Scaler slider (80%-130%), and 6 dynamic accent color pickers.
- **Telegram Bot Direct Help & Feedback Integration**: Upgraded "Help & feedback" across all menus (top bar dropdown, bottom sheet, settings) to connect directly to the team's Telegram bot endpoint for instant user suggestions and bug reports.
- **Feature Parity in Dropdown Menu**: Added Theme and Quick Tools menu items directly to `omnimenuDropdown` for top and split address bar navigation modes.

### Changed
- **Compact Quick Tools Grid**: Redesigned the Quick Tools sheet layout to be ~30% vertically sleeker with 50dp circle icons, tighter paddings, and 10.5sp text labels to eliminate empty scroll space.
- **Version Code Synchronization**: Updated `baseVersionCode` to `2029` for seamless update compatibility across GitHub releases.

---

## [1.2.6.2] - 2026-07-27

### Fixed
- **Immutable Tag GitHub Deployment**: Fixed GitHub Actions release deployment pipeline handling for tag versioning and automated APK builds.
- **Dropdown Component Cleanups**: Standardized composable names and menu state callbacks across phone address bar views.

---

## [1.2.6.1] - 2026-07-26

### Fixed
- **Version Code Mismatch Resolution**: Resolved a build automation versioning issue where local config overrides caused `versionCode` regressions across side-loaded releases.
- **Split Mode Progress Bar**: Pinned the page load progress indicator strictly to the top bar in split/top modes and bottom bar in bottom mode.

---

## [1.2.6] - 2026-07-26

### Added
- **Appearance Settings Header Optimization**: Moved Address Bar position and Navigation bar controls to the top of Appearance settings for faster configuration.
- **Home Palette Accent Color Key Sync**: Fixed a key mismatch between home screen wallpaper accent palettes and the global theme engine map.

---

## [1.2.5.1] - 2026-07-20

### Fixed
- **Global Language Application**: Updated locale configuration management so language selection updates the entire application UI immediately without requiring manual app restarts.

---

## [1.2.5] - 2026-07-15

### Added
- **WireGuard Permission Checks**: Enforced system `VpnService.prepare()` checks before initiating WireGuard VPN connections to prevent null-intent exceptions on Android 10+.
- **Enhanced All-In-One Menu Sheet**: Redesigned the All-In-One bottom menu bar to be more compact, streamlined, and responsive across device orientations.

---

## [1.2.4] - 2026-07-13

### Fixed
- **GitHub Release Workflow**: Fixed environment variable decoding for base64 keystores during automated GitHub Actions builds.
- **Private Vault Security Hardening**: Enhanced AES Keystore encryption routines and improved memory cleanup on locker exit.

---

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
