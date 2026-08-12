# Omni Browser vs. Mainstream Browsers — Gap Analysis & Improvement Plan

**Date:** 2026-08-12
**Scope:** Android browser (GeckoView 145 + Jetpack Compose, ~60K lines Kotlin, v1.2.8.3)
**Comparators:** Google Chrome, Mozilla Firefox, Brave, Microsoft Edge, Apple Safari (iOS), Samsung Internet

---

## 1. Executive Summary

Omni Browser is **feature-dense but not feature-complete**. For a project of this size it
already ships things mainstream browsers refused to build: an embedded Tor daemon, an AES-256
biometric vault, an aggressive media grabber + HLS download engine, reader mode with TTS, and
AMO extension install. That is a real moat.

However, the audit found **most "everyday browser" features that users take for granted are
missing or only half-wired**, and several flagship features are **marketing-false** (VPN is a
no-op stub, FFmpeg is simulated, "offline ML translation" is actually an online Google API,
the lock icon is faked from the URL scheme). Before Omni can compete as a primary browser, the
ranking below should drive the roadmap.

Headline verdicts vs. mainstream:

| Capability domain | Omni vs. mainstream |
|---|---|
| Privacy tooling (Tor, vault, burn, adblock) | **Ahead** on gadgets, **behind** on fundamentals (no real per-site cookies, weak adblock engine, fake security indicator) |
| Core browsing UX (omnibox, tabs, sync, cast) | **Well behind** Chrome/Firefox/Edge |
| Media & download power | **Ahead** (native player, HLS sniffer) but contains broken advertisements ("FFmpeg", DASH download) |
| Extensions | **Ahead** (AMO install + bundled MV2) — rivals only Firefox |
| Identity & auth (passkeys, real TLS state) | **Critically behind** — completely absent |
| Cloud sync | **Entirely absent** — the single biggest retention killer |

---

## 2. Feature Comparison Matrix

Legend: ✅ implemented / 🟡 partial / ❌ missing / 🟥 **broken** (advertised but not working)

| Feature | Omni | Chrome | Firefox | Brave | Edge | Safari* | Samsung |
|---|---|---|---|---|---|---|---|
| **Cross-device sync** (bookmarks/history/passwords/tabs) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Passkeys / WebAuthn** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Real TLS indicator** (cert validity from engine) | 🟥 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cert-error bypass flow** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **HTTPS-only mode** (stable) | 🟥 | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Cast to TV / DLNA** | ❌ | ✅ | ✅ | 🟡 | ✅ | ✅ | ✅ |
| **VPN (working)** | 🟥 | – | – | ✅ (paid) | – | – | – |
| **Tor** (in-app) | ✅ (global) | – | – | ✅ (per-window) | – | – | – |
| **Built-in ad/tracker block** | 🟡 | – | 🟡 (ETP) | ✅ (Shields) | 🟡 | 🟡 | 🟡 |
| **Content/cosmetic filtering** (uBlock-grade) | 🟡 | – | 🟡 (retire uBO) | ✅ | – | – | – |
| **Per-site cookie control / auto-delete** | 🟥 | ✅ | ✅ (TCP) | ✅ | ✅ | ✅ | 🟡 |
| **Per-site permissions (allow-once etc.)** | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Password breach/weak-password audit** | ❌ (stub) | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Credit-card autofill** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ (Apple Pay) | ✅ |
| **Address-bar suggestions + URL autocomplete** | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Tab search** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Tab groups + reorder** | ❌ (groups only) | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| **Tab "inactive/sleep"** | ❌ | ✅ | ✅ | ✅ | ✅ | – | – |
| **Send tab / send-to-device** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ (Handoff) | 🟡 |
| **Cloud reading list / read-later** | ❌ | 🟡 (list) | ✅ (Pocket) | ✅ (Playlist) | ✅ (Collections) | ✅ (Reading List) | 🟡 |
| **Reader mode** | ✅ (+TTS!) | 🟡 | ✅ | ✅ (Speedreader) | ✅ | ✅ | ✅ |
| **In-page translation (native)** | 🟡 (google proxy) | ✅ | ✅ (on-device) | ✅ | ✅ | ✅ (on-device) | ✅ |
| **Background audio + lock-screen controls** | 🟥 | ✅ | ✅ | ✅ (Playlist) | ✅ | – | 🟡 |
| **Video pop-out / PiP** | 🟡 (manual only) | ✅ (auto) | ✅ | ✅ | ✅ | ✅ | ✅ (assistant) |
| **Voice search in address bar** | 🟡 (home only) | ✅ | 🟡 | 🟡 | ✅ | ✅ | 🟡 |
| **Home-screen Android widgets** | ❌ | 🟡 (search) | 🟡 | 🟡 | 🟡 | – | ✅ |
| **Newsfeed on home / offline** | 🟡 (no offline) | ✅ (Discover) | ✅ (Pocket) | ✅ | ✅ | ✅ (News) | ✅ |
| **Backup / export of full data** | ❌ | 🟡 | 🟡 | 🟡 | 🟡 | ✅ (iCloud) | 🟡 |
| **AI assistant (privacy-aware)** | ❌ | ✅ (Gemini) | – | ✅ (Leo) | ✅ (Copilot) | ✅ (Siri) | ✅ (Bixby) |
| **Extension install from store** | ✅ (AMO) | – | ✅ | ✅ (CWS) | – | 🟡 | 🟡 |
| **File vault / private locker** | ✅ | – | – | – | – | – | – |
| **Stream/HLS media download** | ✅ | – | – | 🟡 | – | – | – |

*\*Safari is iOS-only; included as UX/features reference.*

---

## 3. Where Mainstream Browsers Beat Omni (with code evidence)

### 3.1 Critical — fixes a product can’t grow without

**C1. Cloud sync is completely absent.**
No Firefox Accounts, no Google sign-in, no "send to device". Bookmarks/history are plain local
JSON (`browser/BrowserViewModel_Bookmarks.kt:16`, `BrowserViewModel_History.kt:16`), passwords
are a local Room DB (`tools/passwords/PasswordDatabase.kt:87`). Every comparator bundles sync;
a browser without it **loses the "set as default browser" decision** for most users. GeckoView
cannot do FxA sync for you — this needs a backend (Firebase/self-hosted) *or* a leak-free
accountless scheme (Brave-style encrypted sync chain). See §4.

**C2. Passkeys / WebAuthn — entirely absent.** `grep webauthn|fido|passkey|ctap` → nothing.
Browsers are the main passkey client on Android; companies that don’t implement it drop sites
like Google/Dropbox/Amazon sign-ins.

**C3. Real TLS security indicator is fake.** The lock icon + "Certificate valid (TLS) /
Identity verified" text are derived *only* from `currentUrl.startsWith("https://")`
(`browser/BrowserScreen.kt:5403, 5488-5563`). There is zero `onSecurityChange`/cert handling —
an https page served with a bad/self-signed cert still shows a green lock. Mainstream browsers
drive the indicator from the engine’s actual security info. Implement `GeckoSession.ProgressDelegate.onSecurityChange`
and render lock state from `SecurityInformation` — and add a cert-error bypass dialog (Chrome’s
"Advanced → Proceed").

**C4. VPN is a dead stub while the UI advertises it.** Real WireGuard `GoBackend` + VpnService
exist (`privacy/VpnManager.kt`, manifest), but every ViewModel wrapper is a no-op —
`connectVpn`/`disconnectVpn`/`saveCustomVpnConfig` (`browser/BrowserViewModel.kt:4792-4802,
3648-3654`). The Privacy Hub switch is permanently disabled and the provider selector labels
WireGuard "unavailable" (`settings/PrivacyHubScreen.kt:274, 614, 641`). Either rewire the
backend into the VM or remove the UI — a disabled "VPN" row on the privacy hub hurts trust.

**C5. Casting (Chromecast/DLNA) — missing.** Zero hits for `cast|mediaroute|dlna`. Brave/Samsung
are the peer set here; but this is lower priority than C1-C4 (see roadmap).

### 3.2 High — visible to power users and reviewers

**H1. Cookie controls are miswired & non-per-site.** The settings dialog maps
`1`=block-all and `2`=block-third-party (`settings/PrivacySecurityScreen.kt:491-494`) but
GeckoView’s constants are the reverse (1=accept first-party, 2=block everything) — the two
restrictive options behave opposite to their labels. Engine config also hardcodes
`cookieBehavior: 5` (`BrowserViewModel.kt:2627, 5434`). Add per-site cookie control
(`SitePermission` has no cookie field) and honour device-wide third-party cookie policy.

**H2. Adblock is a domain-list matcher, not a content blocker.** `adblock/AdBlockManager.kt`
parses filter lists into bare domains, dropping cosmetic/`$`/regex rules; cosmetic filtering is
one hardcoded CSS blob (`AdBlockManager.kt:361-369`). GeckoView’s built-in tracking protection
is a separate always-on net (`BrowserViewModel.kt:2597`). To compete with Brave Shields,
upgrade to real `ContentBlocker` pattern strings (GeckoView supports `ContentBlocking` with
regEx+cosmetic via per-extension content blocking), or permanently embed uBlock-style MV2 —
the code comment admits uBlock "could crash the app" (`CuratedExtensions.kt`), which is exactly
why the in-house engine must be strengthened.

**H3. Password manager lacks audit & a real breach check.** "Warn you of compromised
passwords" toggle only stores a boolean, never consulted (`BrowserViewModel.kt:7738-7743`).
Chrome/Firefox surface compromised/reused checks. Also fix: vault auto-unlocks on cold start
with no master-password prompt (`BrowserViewModel.kt:2903`), and `changeMasterPassword`
swaps the stored key without re-encrypting the SQLCipher DB — it would corrupt the vault and is
dead code (`tools/passwords/MasterPasswordManager.kt:217-231`).

**H4. Background media is broken / dead code.** `OmniMediaService.kt` (MediaSessionService) is
referenced nowhere and **missing from AndroidManifest.xml** — no lock-screen media controls, no
true background playback, despite a background-playback toggle. Fix by registering the service
in the manifest and starting it from the player for background/PiP audio (Samsung Video
Assistant parity). Re-enable auto-PiP on home (removed at `MainActivity.kt:789-791`).

**H5. Download/stream advertisements are false:**
- **FFmpeg is simulated** — on download failure `FFmpegLoader.simulateLocalInstallation()`
writes dummy text files as "binaries" (`media/FFmpegLoader.kt:215-234`); `FFmpegBridge` only
concatenates `.ts` files. Either ship real FFmpeg binaries (AFL/AGPL — verify licensing) or
stop advertising it.
- **DASH (.mpd) download is broken** — routes to direct download and saves manifest XML as
`.mp4` (`media/StreamDownloadEngine.kt:447-451`).
- **"Extract Audio" is broken** — renames a stream to `.mp3` without extracting
(`VideoPlayerScreen.kt:1806-1820`).
- **No pause/resume**; `maxConcurrentDownloads` setting is never enforced
(`StreamDownloadEngine.kt`, grep in `media/` = 0 hits).

**H6. No full backup/export.** Bookmarks & history have no export UI at all; the settings
backup covers prefs only (`BrowserViewModel.kt:8322, 8438`). Add HTML/JSON export of
bookmarks+history (all mainstream offer this or have sync).

**H7. HTTPS-only mode is unreliable.** The config writer overwrites the HTTPS-only pref with a
safe-browsing-derived value on every settings save (`BrowserViewModel.kt:2622` vs `:5430`), so
standalone HTTPS-Only doesn’t survive a restart. No exception list.

### 3.3 Medium — polish that mainstream browsers treat as baseline

- **Omnibox suggestions/autocomplete** only exist on the home search bar
(`HomeScreenContent.kt:220`); the in-page address bar is a plain `BasicTextField` with no
dropdown (`PhoneAddressBar.kt:462-488`). This is the #1 "feels cheap" gap vs Chrome.
- **Voice search** exists only on the home bar, not the omnibox.
- **Tab search, drag-to-reorder tabs** — absent (`BrowserScreen.kt:4000-4700`).
- **Send tab / send-to-device** — absent (blocks on sync).
- **Android home-screen widget** (search widget) — absent (`grep AppWidgetProvider` → none).
- **News feed**: works but no offline persistence (in-memory only, `BrowserViewModel.kt:6628`).
- **In-page translation** is a `translate.goog` URL rewrite (`BrowserScreen.kt:3809`), not a
native engine; the "translator tool" is not ML Kit as README claims — it calls Google’s gtx API
(`tools/TranslationManager.kt:66`; "ML Kit removed for FOSS/F-Droid"). Either restore offline
ML Kit (per README claim) or correct the copy.
- **Fingerprint/anti-tracking hardening**: `privacy.resistFingerprinting` + `firstparty.isolate`
only engage in Tor mode (`BrowserViewModel.kt:2680`); no referrer control; "Transient
Containers" is a disabled row (`PrivacyHubScreen.kt:912`).
- **Profiles / multi-user** (Edge/Chrome profiles) — absent.
- **Credit-card autofill** — absent.
- **AI assistant** — none (only the AI *blocker*). A privacy-first angle: offline/on-device
LLM summarization would differentiate; at minimum add a "Summarize with a local model" to
reader mode.

### 3.4 Where Omni already beats mainstream (protect this moat)

- **Embedded Tor daemon**, real kmp-tor with NEWNYM and leak-aware config
(`privacy/EmbeddedTorManager.kt`) — better in-app than Brave (only global, not per-window).
- **Safe Locker vault** — AES-256-GCM EncryptedFile + SQLCipher, biometric gate — no mainstream
competitor has an in-browser encrypted file vault.
- **Reader mode + TTS + dyslexic font + ToC** (`BrowserViewModel.kt:5842-5938`) — richer than
every comparator’s reader.
- **AMO add-on install + bundled MV2 extensions** (`proxy_router`, `media_grabber`,
`force_dark`, `ai_blocker`, `universal_copy`) — only Firefox is close.
- **HLS/split mux download engine with AES-128 decryption** — mainstream browsers block this.
- **Per-site permission UX (Allow once / remember)** — matches/beats Chrome.
- **Customization depth**: animated wallpapers, accent colors, per-site UA, UI scale — far
beyond Chrome/Firefox.

---

## 4. Recommended Roadmap (prioritized)

### P0 – Trust & correctness (fix what’s advertised-broken; 1–2 sprints)
1. Replace fake lock indicator with engine `onSecurityChange` state + cert-error bypass
   (BrowserScreen.kt:5403, new ProgressDelegate wiring). *C3*
2. Fix cookie mapping bug + persist cookieBehavior correctly (PrivacySecurityScreen.kt:491,
   BrowserViewModel.kt:2627). *H1*
3. Fix HTTPS-only persistence (BrowserViewModel.kt:2622 vs 5430). *H7*
4. Rewire or hide the VPN stub (BrowserViewModel.kt:4792; PrivacyHubScreen.kt:614). *C4*
5. Password vault: don’t auto-unlock on cold start; fix/remove dead `changeMasterPassword`.
   *H3*

### P1 – Growth features (next)
6. **Encrypted sync + send-to-device** — start with bookmarks+history+open tabs via a minimal
   backend or accountless sync chain (Brave model, no account = fits the privacy brand). *C1*
7. **Passkeys/WebAuthn** — critical for adoption as default browser. *C2*
8. Omnibox suggestions + URL autocomplete + voice search in the address bar. *M*
9. Register OmniMediaService, add lock-screen controls & reliable background audio; restore
   auto-PiP. *H4*
10. Full bookmarks/history export + improved backup. *H6*

### P2 – Competitive parity & polish
11. Tab search + drag-to-reorder + inactive-tab sleeping. *M*
12. Per-site cookie controls + cookie auto-delete toggle. *H1*
13. Adblock engine: GeckoView ContentBlocking regex/cosmetic support + per-site toggle
    (match Brave Shields). *H2*
14. Password audit (weak/reused/breach) + credit-card autofill. *H3*
15. Fix DASH download + real audio extraction; ship real FFmpeg or remove claims. *H5*
16. Home-screen search widget; offline news cache. *M*

> **Theme for positioning:** Omni should lean into what no mainstream browser will do —
> *embedded Tor, encrypted local vault, media grabber, AMO extensions* — and use P0 fixes to
> remove the credibility gaps (fake lock, fake VPN, fake offline translation, fake FFmpeg).
> Honesty in the README about these four would build more trust than the current claims
> (README.md, `A fully offline AI toolkit`, `100% on-device offline translation via ML Kit`,
> `WireGuard VPN integration` are all currently overstated).

---

## Appendix A — Evidence index (file → issue)
- Fake TLS indicator: `browser/BrowserScreen.kt:5403,5488-5563` · no `onSecurityChange` anywhere
- VPN stub: `browser/BrowserViewModel.kt:3648-3654,4792-4802`; `settings/PrivacyHubScreen.kt:274,614`
- No sync: `browser/BrowserViewModel_Bookmarks.kt:16`; `BrowserViewModel_History.kt:16`;
  `tools/passwords/PasswordDatabase.kt:87`
- Cookie mapping bug: `settings/PrivacySecurityScreen.kt:491-494` vs `BrowserViewModel.kt:2605,2627,5434`
- Adblock mechanics: `browser/adblock/AdBlockManager.kt:114,245-301,361-369`
- Background media dead: `media/player/OmniMediaService.kt`; manifest has no service entry
- FFmpeg simulation: `media/FFmpegLoader.kt:215-234`; `media/FFmpegBridge.kt:52-55`
- DASH/audio download bugs: `media/StreamDownloadEngine.kt:447-451`; `VideoPlayerScreen.kt:1806-1820`
- Password audit stub: `BrowserViewModel.kt:7738-7743`; auto-unlock `BrowserViewModel.kt:2903`
- Master-password corruption path: `tools/passwords/MasterPasswordManager.kt:217-231`
- Omnibox no suggestions: `browser/PhoneAddressBar.kt:462-488` vs `HomeScreenContent.kt:220`
- HTTPS-only overwrite: `BrowserViewModel.kt:2622` vs `:5430`
- Translation is online gtx: `tools/TranslationManager.kt:66`

## Appendix B — Where facts came from
- Omni: full-source audit (3 passes: core browsing, privacy/security, media/tools),
  2026-08-12.
- Comparators: product documentation (Brave.com/features), Wikipedia (Chrome/Firefox), and
  feature knowledge of Chrome/Edge/Safari/Samsung; re-verify before making roadmap bets.
