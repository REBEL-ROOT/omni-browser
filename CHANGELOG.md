# Changelog

All notable changes to the Omni Browser project will be documented in this file.

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
