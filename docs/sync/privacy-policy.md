# Omni Sync Privacy Policy & Data Handling Disclosure

**Effective Date:** August 24, 2026  
**Developer:** RebelRoot Ltd (Contact: rebelroot.ai@gmail.com)

## 1. Synchronization Architecture & Data Choices

Omni Browser provides users with complete transparency and control over how their data is synchronized:

### A. Firefox Account Cloud Sync (Optional)
When you choose to sign in with your Firefox Account:
- Authentication tokens and sync data are communicated directly and securely with Mozilla's official TokenServer and Sync 1.5 endpoints.
- RebelRoot does not operate intermediate servers, intercept tokens, or store your Firefox credentials.
- Incognito/private tabs and internal browser URLs are never sent to Mozilla Sync servers.

### B. Omni Sync Mesh (Upcoming / Testing Phase)
When using direct peer-to-peer (P2P) mesh synchronization with the companion desktop extension:
- **Zero Cloud Storage:** All data is transferred directly between your devices over local Wi-Fi.
- **End-to-End Encryption (E2EE):** Authenticated `AES-256-GCM` with NIST `P-256` ECDH key agreement and 6-digit numeric SAS code verification.
- No central database, tracking, or telemetry servers are involved.

## 2. Local Storage & Security
Encryption keys, trusted device pairings, and local sync journals are stored exclusively within the browser's sandboxed storage (`EncryptedSharedPreferences` / Android Keystore).

## 3. Open Source & Verifiable
The complete source code of Omni Sync is open source under the GNU General Public License v3.0 (GPL-3.0). Anyone can independently inspect, audit, and build the software from source.
