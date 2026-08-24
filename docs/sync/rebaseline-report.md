# Omni Sync — Phase 00 Rebaseline Audit & Design Freeze Report

**Audit Date:** 2026-08-22  
**Auditor:** Principal Software Architect & Security Engineer  
**Repository:** `omni-browser` (`REBEL-ROOT/omni-browser`)  
**Status:** COMPLETE · GO FOR PHASE 01  

---

## 1. Repository Build & Version Identity

| Attribute | Verified Value | Evidence Source |
| :--- | :--- | :--- |
| **Git Branch** | `main` | `git rev-parse --abbrev-ref HEAD` |
| **HEAD Commit** | `52173a18abc21125958ca7d6907feb59cf6f8e94` | `git rev-parse HEAD` |
| **Working Tree** | Clean (`nothing to commit`) | `git status` |
| **Version Name** | `1.2.9.3` | `app/build.gradle.kts` (`baseVersionName`) |
| **Version Code** | `2043` (base) + ABI offsets (+1M arm, +2M aarch64, +3M universal) | `app/build.gradle.kts` (`baseVersionCode`) |
| **Compile / Target SDK** | `compileSdk = 36`, `targetSdk = 36` | `app/build.gradle.kts` |
| **Min SDK** | `minSdk = 26` (Android 8.0 Oreo) | `app/build.gradle.kts` |
| **Java / JVM Target** | Java 17 / JVM 17 | `app/build.gradle.kts` |
| **GeckoView Engine** | `145.0.20251124145406` | `app/build.gradle.kts` |
| **Compose BOM** | `2024.09.03` | `app/build.gradle.kts` |
| **DataStore** | `androidx.datastore:datastore-preferences:1.1.1` | `app/build.gradle.kts` |
| **Crypto & Security** | `androidx.security:security-crypto:1.1.0-alpha06`, `net.zetetic:sqlcipher-android:4.6.1` | `app/build.gradle.kts` |
| **QR / Barcode** | `com.google.zxing:core:3.5.3` (Pure FOSS) | `app/build.gradle.kts` |
| **Background / Work** | `androidx.work:work-runtime:2.9.1` | `app/build.gradle.kts` |

---

## 2. Live Architecture Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                               OMNI BROWSER                                  │
│                                                                             │
│  ┌───────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │     UI Layer          │  │     Browser Core     │  │   Offline AI     │  │
│  │ Jetpack Compose M3    │  │ BrowserViewModel     │  │ Vosk ASR Engine  │  │
│  │ Haze Glassmorphism    │  │ MVVM / UDF           │  │ Offline NMT      │  │
│  └──────────┬────────────┘  └──────────┬───────────┘  └──────────────────┘  │
│             │                          │                                    │
│             ▼                          ▼                                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       Data & Persistence Layer                        │  │
│  │                                                                       │  │
│  │  • Bookmarks: browser_bookmarks_v2.json (Canonical)                   │  │
│  │    [Legacy: browser_bookmarks.json in BrowserViewModel_Bookmarks.kt]  │  │
│  │  • History: browser_history.json (Capped 500 entries)                 │  │
│  │  • Session: browser_session_states.json (Debounced Atomic v1)         │  │
│  │  • Settings: omni_settings (Preferences DataStore)                    │  │
│  │  • Passwords / Vault: SQLCipher Encrypted DBs + Android Keystore      │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                     GeckoView Engine Layer                            │  │
│  │  • Mozilla GeckoView 145                                              │  │
│  │  • WebExtension Manager (Built-in + Custom Extensions)                │  │
│  │  • ContentBlocking & GeckoSession Lifecycle Delegates                 │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Authoritative Data Inventory & Subsystem Mapping

| Subsystem | Exact Source File(s) | Storage Format & Location | Mutation Entry Points |
| :--- | :--- | :--- | :--- |
| **Canonical Bookmarks (v2)** | `com/rebelroot/omni/bookmarks/model/OmniBookmark.kt`<br>`com/rebelroot/omni/bookmarks/model/BookmarkCollection.kt`<br>`com/rebelroot/omni/bookmarks/storage/BookmarkStorage.kt` | Atomic JSON (`browser_bookmarks_v2.json` in `context.filesDir`) | `BookmarkCollection.addBookmark()`, `addFolder()`, `moveNode()`, `deleteNode()`, `renameFolder()`, `saveBookmarks()` |
| **Legacy Bookmarks (v1)** | `com/rebelroot/omni/browser/BookmarkEntry.kt`<br>`com/rebelroot/omni/browser/BrowserViewModel_Bookmarks.kt` | JSON Array (`browser_bookmarks.json` in `context.filesDir`) | `BrowserViewModel.addToBookmarks()`, `removeBookmark()`, `clearAllBookmarks()` |
| **History** | `com/rebelroot/omni/browser/HistoryEntry.kt`<br>`com/rebelroot/omni/browser/BrowserViewModel_History.kt` | JSON Array (`browser_history.json` in `context.filesDir`) | `BrowserViewModel.addToHistory()`, `deleteHistoryEntry()`, `clearAllHistory()`, `clearHistorySince()` |
| **Tabs & Session** | `com/rebelroot/omni/browser/TabState.kt`<br>`com/rebelroot/omni/browser/session/OmniSessionState.kt`<br>`com/rebelroot/omni/browser/session/SessionStatePersistence.kt` | Debounced Atomic JSON (`browser_session_states.json` in `context.filesDir`) | `SessionStatePersistence.requestPersist()`, `forceCheckpoint()`, `removeDurableState()` |
| **Settings & Preferences** | `com/rebelroot/omni/browser/BrowserViewModel.kt` | Android Preferences DataStore (`omni_settings`) | `dataStore.edit { prefs -> ... }` |
| **Password Vault** | `com/rebelroot/omni/tools/passwords/PasswordVaultManager.kt`<br>`com/rebelroot/omni/tools/passwords/PasswordDatabase.kt` | Room + SQLCipher Encrypted SQLite DB | `PasswordVaultManager.savePassword()`, `deletePassword()` |
| **Safe Locker** | `com/rebelroot/omni/tools/locker/PrivateLockerManager.kt`<br>`com/rebelroot/omni/tools/locker/LockerDatabase.kt` | SQLCipher Encrypted DB + App Private Files | `PrivateLockerManager.importFile()`, `deleteFile()` |
| **QR Tools** | `com/rebelroot/omni/tools/qrcode/BarcodeGenerator.kt`<br>`com/rebelroot/omni/tools/qrcode/QrCodeDecoder.kt` | ZXing Memory Bitmaps (Pure FOSS) | `BarcodeGenerator.generateQRCode()`, `QrCodeDecoder.decodeQRCode()` |
| **VPN & Proxy** | `com/rebelroot/omni/privacy/VpnManager.kt`<br>`com/rebelroot/omni/privacy/TorManager.kt`<br>`com/rebelroot/omni/privacy/EmbeddedTorManager.kt` | WireGuard Tunnel + kmp-tor in-process daemon | `VpnManager.connect()`, `TorManager.start()` |

---

## 4. Exact Bookmark Data Contract & Mutation Boundary

### Canonical Bookmark Data Model (`OmniBookmark.kt`)
```kotlin
const val ROOT_FOLDER_ID: String = "root"

data class OmniBookmark(
    val id: String,        // UUID string — canonical sync identity
    val parentId: String,  // ROOT_FOLDER_ID or parent folder UUID
    val position: Long,    // 0-based dense index within parent
    val title: String,
    val url: String,
    val createdAt: Long,
    val modifiedAt: Long
)

data class OmniBookmarkFolder(
    val id: String,        // UUID string — canonical sync identity
    val parentId: String,  // ROOT_FOLDER_ID or parent folder UUID
    val position: Long,    // 0-based dense index within parent
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long
)
```

### Bookmark Invariants
1. **Dense Positions:** Within any given parent folder, positions must be consecutive integers `0, 1, ..., n-1`.
2. **Cycle Prevention:** A folder cannot be moved into its own descendant tree.
3. **No Orphan Items:** Every node's `parentId` must exist in `folders` map or equal `ROOT_FOLDER_ID`.
4. **Stable Sync Identity:** `id` is a UUID generated on creation and preserved through moves, renames, and exports.

### Critical Finding & Divergence (The "Two-Storage" Issue)
- **Live State:** `BookmarkStorage.kt` (`browser_bookmarks_v2.json`) implements the full hierarchical tree model. `BrowserViewModel_Bookmarks.kt` still writes a legacy flat list to `browser_bookmarks.json`.
- **Phase 02 Requirement:** The bookmark adapter in Phase 02 must unify all UI bookmark mutations (`addToBookmarks`, `removeBookmark`, `BookmarksScreen`) through `BookmarkCollection` and `BookmarkStorage` v2 to ensure atomic, single-source durability before attaching the sync mutation journal.

---

## 5. Tab & Session Contract

### Data Model (`OmniSessionState.kt`)
```kotlin
data class OmniSessionState(
    val schemaVersion: Int = 1,
    val tabId: String,
    val sessionStateBytes: ByteArray, // Opaque serialized GeckoSession state
    val metadata: TabMetadata,
    val timestamp: Long
)

data class TabMetadata(
    val title: String,
    val url: String,
    val isIncognito: Boolean,
    val lastActiveTime: Long,
    val canGoBack: Boolean,
    val canGoForward: Boolean
)
```

### Portable vs. Device-Local Subset for Sync (Phase 12)
- **Portable Subset (Safe to Sync):** `tabId`, `metadata.url`, `metadata.title`, `metadata.lastActiveTime`.
- **Device-Local Only (Do NOT Sync):** `sessionStateBytes` (GeckoView-internal byte stream), window coordinates, `suspendThumbnail`, `sessionGenerationId`.
- **Excluded:** Incognito tabs (`metadata.isIncognito == true`) are never persisted and never synchronized.

---

## 6. Settings Allowlist Matrix (DataStore)

| Category | Setting Key | Data Type | Sync Policy | Rationale |
| :--- | :--- | :--- | :---: | :--- |
| **Search** | `default_search_engine` | String | **SYNC (Allowlist)** | Portable across Android & Extension |
| **Search** | `custom_search_url` | String | **SYNC (Allowlist)** | Portable search query template |
| **Search** | `custom_suggest_url` | String | **SYNC (Allowlist)** | Portable suggest query template |
| **Appearance** | `dark_theme_enabled` | Boolean | **SYNC (Allowlist)** | User theme preference |
| **Appearance** | `follow_system_theme` | Boolean | **SYNC (Allowlist)** | User theme preference |
| **Web Content** | `force_dark_websites` | Boolean | **SYNC (Allowlist)** | Web rendering preference |
| **Privacy** | `do_not_track` | Boolean | **SYNC (Allowlist)** | DNT header preference |
| **Privacy** | `https_only_mode` | Boolean | **SYNC (Allowlist)** | Security mode preference |
| **Privacy** | `cookie_behavior` | Int | **SYNC (Allowlist)** | Cookie blocking policy |
| **Tools** | `universal_copy_enabled` | Boolean | **SYNC (Allowlist)** | Browser feature toggle |
| **Tools** | `ai_blocker_enabled` | Boolean | **SYNC (Allowlist)** | Content blocking toggle |
| **Android UI** | `address_bar_position` | String | **EXCLUDED** | Android-specific layout |
| **Android UI** | `amoled_mode`, `ui_scale` | Boolean/Float | **EXCLUDED** | Device display specific |
| **Android UI** | `browser_wallpaper_uri` | String | **EXCLUDED** | Device-local file URI |
| **Downloads** | `download_wifi_only` | Boolean | **EXCLUDED** | Mobile data preference |
| **Security** | `lock_incognito` | Boolean | **EXCLUDED** | Biometric / local device lock |
| **Security** | `never_save_password_domains` | StringSet | **EXCLUDED** | Vault-adjacent security list |
| **Network** | `proxy_provider`, `tor_*` | String/Bool | **EXCLUDED** | Network configuration secrets |
| **Network** | `doh_uri`, `dot_host` | String | **EXCLUDED** | Network connection settings |

---

## 7. WebExtension & Native Bridge Findings

- **GeckoView WebExtension Runtime:** GeckoView provides a native WebExtension host via `GeckoRuntime.webExtensionController`. WebExtensions communicate via `WebExtension.MessageDelegate` and `WebExtension.PortDelegate`.
- **Privilege Separation:** Privileged native commands (`OMNI_*` alerts) are origin-checked via `isTrustedOmniOrigin()` in `BrowserViewModel_Session.kt`.
- **Desktop Separation:** Desktop companion extensions (Chrome MV3, Firefox) will run directly inside their native browser extension hosts and communicate with Omni Sync via the shared TypeScript protocol core (Phase 08) over LAN / WebRTC, NOT through GeckoView internal messaging.

---

## 8. Lifecycle & Background Constraints

- **Android Background Limits:** Sockets cannot be kept alive indefinitely in the background on Android 12+ (API 31+) without a foreground service.
- **Sync Architecture Approach:**
  1. Primary synchronization occurs when the browser is active (Foreground) or during user-initiated sync.
  2. Short-lived reconnect & catchup jobs via `WorkManager` for background synchronization.
  3. All local mutations write to the durable outbox journal first, ensuring zero data loss if process termination occurs while offline.

---

## 9. Privacy & Security Constraints

- **Privacy Policy Statement:** `PRIVACY_POLICY.md` guarantees that bookmarks, history, tabs, and vault data are stored locally and never sent to remote tracking/cloud servers.
- **Sync Architecture Compatibility:**
  1. **Zero-Knowledge:** Application-layer E2E encryption (`AES-256-GCM` / `XChaCha20-Poly1305`) before any data leaves the device.
  2. **No Cloud Storage:** No central server stores or decrypts sync payloads.
  3. **Relay Metadata Disclosure:** Remote signaling / TURN relay servers only see encrypted packets and IP routing metadata, fully disclosed in privacy updates.

---

## 10. Dependency Graph & Phase Boundaries

```
Phase 00: Rebaseline Audit (Complete)
    │
    ▼
Phase 01: Canonical Domain & Protocol Contract (Specification & Fixtures)
    │
    ├────────────────────────────────────────┐
    ▼                                        ▼
Phase 02: Bookmark Adapter (Android)    Phase 08: Desktop Core (TypeScript)
    │                                        │
    ▼                                        ├───────────────┬───────────────┐
Phase 03: Durability & Outbox                ▼               ▼               ▼
    │                                   Phase 09:       Phase 10:       Phase 11:
    ▼                                   Chrome MV3      Firefox         Edge/Opera
Phase 04: Conflict Engine (Simulation)
    │
    ▼
Phase 05: Identity & Crypto (Keystore/WebCrypto)
    │
    ▼
Phase 06: LAN Transport (Wi-Fi P2P)
    │
    ▼
Phase 07: Android-to-Android Bookmark MVP
    │
    ├────────────────────────────────────────┬───────────────────────────────┐
    ▼                                        ▼                               ▼
Phase 12: Tabs Sync                     Phase 13: History Sync          Phase 14: Settings Sync
    │                                        │                               │
    └────────────────────────────────────────┼───────────────────────────────┘
                                             │
                                             ▼
                                Phase 15: Remote P2P & TURN Relay
                                             │
                                             ▼
                                Phase 16: Multi-Device Trust Mesh
                                             │
                                             ▼
                                Phase 17: Security Audit
                                             │
                                             ▼
                                Phase 18: Safari Extension
                                             │
                                             ▼
                                Phase 19: Compliance & Store Listings
                                             │
                                             ▼
                                Phase 20: Release Sign-Off (GA)
```

---

## 11. Proposed Scope for v1 Launch

- **Entities:** Bookmarks & Bookmark Folders (Full hierarchy, dense positions, titles, URLs).
- **Transports:** Local LAN / Wi-Fi discovery + encrypted direct sockets.
- **Platforms:** Android (`omni-browser`) $\leftrightarrow$ Android (`omni-browser`), followed by Chrome Extension companion.
- **Pairing:** QR Code transport (ZXing) with human-verifiable SAS / confirmation code.
- **Security:** Platform-backed keys (Android Keystore / WebCrypto), authenticated encryption, replay nonce verification.

---

## 12. Final Rebaseline Decision: GO

All required architectural facts, storage locations, data contracts, and security constraints have been evidenced from the live `omni-browser` repository. Zero critical integration points remain `UNKNOWN`.

**Next Phase:** Proceed to **Phase 01: Canonical Sync Domain & Protocol Contract**.
