# Changelog

All notable changes to the Omni Browser project will be documented in this file.

## [1.1.1] - 2026-06-29

### Added
- **Developer Offline Pad & Vault**: Integrated a completely local, secure, and offline scratchpad and credentials vault. Supports storing normal text notes, code snippets, API keys, passwords, and site URLs. Features an interactive programmer keyboard row (symbols like `{}`, `[]`, `=>`, `;`), clipboard quick-paste, password visibility toggle, and a single-tap URL-launch button that automatically copies the saved password and opens the site.
- **Responsive Flow Quick Tools Grid**: Upgraded the Quick Tools sheet layout to a modern, fully-responsive dynamic grid using `FlowRow`. Removed all empty placeholder layouts and styled all card actions with a custom dark-adapted blueprint design (glowing borders, primary tint accents, and larger descriptive icons).
- **Interactive Developer Console REPL**: Added support for executing arbitrary JavaScript code dynamically within the page context directly from the Developer Console bottom sheet. Command outputs and exceptions are formatted and displayed inline within the console output list, transforming it from a read-only logger into a live programming shell.
- **Global User-Agent Bypasses**: Configured persistent Firefox Mobile/Desktop User-Agent strings globally for all sessions to prevent WebView blocker screens (`disallowed_useragent`) and support every OAuth provider (Google, Apple, Microsoft, GitHub, Facebook) natively.
- **UPI & App Chooser Integration**: Integrated Android's app chooser via `Intent.createChooser()` for all custom protocols (UPI, WhatsApp, mailto, tel, sms).
- **Fallback URL Redirection**: Enabled extraction and navigation to `S.browser_fallback_url` embedded in payment gateways' `intent://` links when targeted native apps are not installed.
- **Enhanced Android Package Queries**: Added comprehensive visibility permissions in `AndroidManifest.xml` for all major UPI providers (Google Pay, PhonePe, Paytm, CRED, BHIM) and messaging apps.

### Optimized
- **Unified Video Playback Interception**: Optimized HTML5 video player detection and premium takeover across all websites. Added a play-state polling checker in the injected extension scripts to detect autoplaying or already playing media streams instantly.
- **Single-Tap Premium Takeover**: Integrated a direct "Play in Premium Player" button onto the page-level media detection banner and a primary "Play" FAB on top of the fullscreen browser overlay, allowing users to switch from basic HTML5 players to the gesture-enabled ExoPlayer with a single click.

## [1.1.0] - 2026-06-28

### Added
- **Language Selector & Setup**: Implemented a startup language selection flow and Accept-Language HTTP headers automatically configured based on selection.

### Fixed
- **GeckoView Page Blanking**: Resolved compositor freezes and blank page rendering issues when navigating back to the browser screen.
- **UPI Payments & Custom Schemes**: Intercepted and routed custom protocol URLs (like `upi://`, `mailto:`, `tel:`, `sms:`, `whatsapp:`) to external applications safely, adding `<queries>` package visibility declarations in the manifest.
- **Homepage News Category Loading**: Updated the Google News RSS feed category paths to use permanent redirect sections (e.g. `headlines/section/topic/WORLD`), resolving the `400 Bad Request` issue.

---

## [1.0.9] - 2026-06-22

### Added
- **OLED Dark Mode & Theme Toggle**: Added a global dynamic light/dark theme toggle and a pure OLED black theme option.
- **Search Engine Dropdown**: Added an interactive search engine selector dropdown directly on the homepage search bar.
- **Search Customization**: Added default search engine preferences and custom query templates in settings.
- **WireGuard Import**: Added a WireGuard VPN configuration importer directly in settings.
- **Quick Tools**: Integrated a Quick Tools bottom sheet.
- **Tab Swipe Gestures**: Added bottom bar tab swipe gestures for seamless navigation.

### Changed
- **Extensions UI**: Overhauled the extensions manager list and popup interfaces.
- **Package Refactoring**: Refactored core package to `com.rebelroot.omni` and added a privacy policy.

### Fixed
- **Extensions Popups**: Resolved issues preventing web extension popups from opening.
- **Toolbar & Alignment**: Fixed alignments on the toolbar and bottom navigation bar, and removed rounded corners from the top navigation bar container for a sharper look.
- **Tab Lifecycle**: Prevented empty tabs state crashes.

---

## [1.0.0] - 2026-05-30

### Added
- **Multi-Tab system**: Real Chrome-like horizontal scrollable Tab Bar chip row at the top left.
- **On-Device Browser History**: Local JSON-based secure persistence with dynamic date formatting and filtering SearchBar.
- **Video Sniffer & Player**: Integrated Google Media3 ExoPlayer canvas with full swipe gesture controllers and PiP mode support, intercepting online network HTTP/HLS video streams in-browser.
- **Biometric Secure Locker**: Encryption standard AES Keystore vault room.
- **On-Device Translator**: Device-local machine learning offline ES/EN translation backed by Google ML Kit.
- **WireGuard preset**: Direct Contabo VPS preset single-tap VPN connector.
- **Adblocker (uBlock Origin)**: Fully integrated add-ons manager list panel supporting Firefox Android Extensions.
- **Document Scanner**: Auto-perspective paper document scanner backed by ML Kit.

### Fixed
- **Settings Exit Bug**: Added safe popBackStack fallback checking for previous entry to prevent app exiting when pressing top bar back button.
- **Incognito Relocation**: Relocated private browsing toggles to tools dropdown with race-prevention switch click listeners.
- **Session Open Crash**: Refactored GeckoSession binding to use Compose update block with safe `!session.isOpen` check.
- **Search Contrast**: Fixed search input text color matching.

---

## [1.2.2] - 2026-07-10

### Fixed
- **WebExtensions not working on pages**: Resolved a critical bug where all browser extensions (uBlock, Universal Copy, AI Blocker, Media Grabber) were silently inactive due to missing `enable()` call after installation.
- **Extensions disabled in Incognito mode**: All bundled and user-installed extensions are now explicitly allowed in private browsing sessions via `setAllowedInPrivateBrowsing(true)`.
- **Extension API namespace**: Fixed `chrome.webRequest` namespace compatibility issue — now uses a cross-engine fallback (`const api = typeof browser !== 'undefined' ? browser : chrome`).
- **adblocker engine coverage**: Expanded blocked domains from 14 to 70+ major advertising, analytics, and tracker networks.

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
