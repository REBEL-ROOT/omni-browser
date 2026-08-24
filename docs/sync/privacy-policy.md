# Omni Sync Privacy Policy & Zero-Data Collection Disclosure

**Effective Date:** August 22, 2026  
**Developer:** RebelRoot Ltd (Contact: rebelroot.ai@gmail.com)

## 1. Zero Cloud Data Collection
Omni Sync does not collect, store, track, sell, or transmit any personal data, browsing history, bookmarks, open tabs, or device identifiers to any central server or third party.

## 2. End-to-End Encryption (E2EE)
All synchronization data transferred between paired devices (over Local Area Network Wi-Fi or peer-to-peer WebRTC) is encrypted end-to-end using standard, audited cryptographic primitives:
- **Key Agreement:** NIST P-256 (`secp256r1`) Elliptic Curve Diffie-Hellman (ECDH).
- **Payload Encryption:** Authenticated AES-256-GCM with unique, non-repeating Initialization Vectors (IV) and sequence numbers.
- **Key Derivation:** HKDF-SHA256 with distinct salt and info context strings.
- **Short Authentication String (SAS):** 6-digit numeric out-of-band visual verification.

## 3. Local Storage Only
Encryption keys, trusted device public keys, and synchronization outbox/inbox journals are stored exclusively in the browser's local sandbox (`chrome.storage.local` or Android EncryptedSharedPreferences/Keystore).

## 4. Open Source & Verifiable
The complete source code of Omni Sync is open source under the GNU General Public License v3.0 (GPL-3.0). Anyone can independently inspect, audit, and build the software from source.
