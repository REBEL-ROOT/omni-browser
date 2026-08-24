# Omni Sync Browser Extension Permission Justifications

## 1. `bookmarks`
- **Purpose:** Read and write browser bookmarks and bookmark folders to synchronize them in real time across the user's paired Omni Browser and desktop browser instances.
- **Usage:** Listens to `onCreated`, `onChanged`, `onMoved`, and `onRemoved` events to record local changes and applies incoming encrypted remote changes.

## 2. `storage`
- **Purpose:** Store paired device public keys, device identity certificates, and local synchronization state in `chrome.storage.local`.
- **Usage:** Persists cryptographic state across service worker lifecycles.

## 3. `alarms`
- **Purpose:** Schedule periodic background synchronization checks, outbox flushing, and 90-day history/tombstone pruning.

## 4. `sidePanel`
- **Purpose:** Provide a dedicated desktop sidebar UI for managing paired devices, viewing live sync status, and triggering manual sync operations without obstructing the main webpage.
