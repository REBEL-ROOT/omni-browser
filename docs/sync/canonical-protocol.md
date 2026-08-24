# Omni Sync — Canonical Sync Domain & Protocol Specification (v1.0)

**Document Version:** 1.0.0  
**Status:** CANONICAL SPECIFICATION · Language-Neutral  
**Target Environments:** Pure Kotlin (JVM / Android), Pure TypeScript (Node / Browser Extension)  
**Security Properties:** End-to-End Encrypted Payload, Authenticated Envelopes, Strict Bounded Inputs  

---

## Table of Contents

1. [Architectural Overview & Core Concepts](#1-architectural-overview--core-concepts)
2. [Protocol Versioning & Capability Negotiation](#2-protocol-versioning--capability-negotiation)
3. [Canonical Entity Models](#3-canonical-entity-models)
   - [3.1 Envelope & Transport Framing](#31-envelope--transport-framing)
   - [3.2 Bookmark Entity](#32-bookmark-entity)
   - [3.3 Folder Entity](#33-folder-entity)
   - [3.4 Root & Well-Known Virtual Containers](#34-root--well-known-virtual-containers)
   - [3.5 Tab & Session Entity](#35-tab--session-entity)
   - [3.6 History Entity](#36-history-entity)
   - [3.7 Portable Setting Entity](#37-portable-setting-entity)
4. [Operation Calculus & State Machine](#4-operation-calculus--state-machine)
   - [4.1 Operation Types](#41-operation-types)
   - [4.2 Fractional Indexing & Sibling Ordering](#42-fractional-indexing--sibling-ordering)
   - [4.3 Tombstone Lifecycle & Retention](#43-tombstone-lifecycle--retention)
   - [4.4 Snapshot Bootstrap vs. Incremental Ops](#44-snapshot-bootstrap-vs-incremental-ops)
5. [Conflict Resolution Engine](#5-conflict-resolution-engine)
   - [5.1 Conflict Model Selection & Evaluation](#51-conflict-model-selection--evaluation)
   - [5.2 Clock Model: Hybrid Logical Clock (HLC)](#52-clock-model-hybrid-logical-clock-hlc)
   - [5.3 Structural Conflict Rules (Trees & Hierarchies)](#53-structural-conflict-rules-trees--hierarchies)
   - [5.4 Content Conflict Rules](#54-content-conflict-rules)
6. [Security, Bounds & Input Sanitization](#6-security-bounds--input-sanitization)
7. [Cross-Platform Compatibility & Invariants](#7-cross-platform-compatibility--invariants)

---

## 1. Architectural Overview & Core Concepts

Omni Sync uses an **operation-based CRDT / LWW-Element-Set hybrid** over canonical domain entities. The protocol defines a pure abstraction layer independent of how any specific browser (Android SQLite/JSON, Chrome bookmarks API, Firefox Places) stores its data locally.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           OMNI SYNC DOMAIN MODEL                            │
│                                                                             │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────────┐  │
│  │   Canonical Entities    │  │           Sync Operations                │  │
│  │  • Bookmark             │  │  • CREATE                                │  │
│  │  • Folder               │  │  • UPDATE_CONTENT                        │  │
│  │  • TabSession           │  │  • MOVE_REORDER                          │  │
│  │  • HistoryEntry         │  │  • DELETE (Tombstone)                    │  │
│  │  • PortableSetting      │  │  • SNAPSHOT_BOOTSTRAP                    │  │
│  └────────────┬────────────┘  └───────────────────┬──────────────────────┘  │
│               │                                   │                         │
│               ▼                                   ▼                         │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                        Conflict Resolution Engine                     │  │
│  │  • Hybrid Logical Clock (HLC) Ordering                                │  │
│  │  • Lexicographical Fractional Indexing (Dense Sibling Ordering)       │  │
│  │  • Tree Invariant Enforcement (Cycle & Orphan Prevention)             │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                        Authenticated Wire Framing                     │  │
│  │  • Protocol Version Negotiation                                       │  │
│  │  • End-to-End Encrypted Payload Envelope                              │  │
│  │  • Replay Protection via Monotonic Sequence & Ephemeral Nonces        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Protocol Versioning & Capability Negotiation

### 2.1 Version Handshake (`SyncHello` & `SyncHelloAck`)
Before exchanging operations, two peers exchange capability envelopes during the encrypted session handshake:

```json
{
  "type": "SYNC_HELLO",
  "protocolVersion": 1,
  "minSupportedVersion": 1,
  "maxSupportedVersion": 1,
  "deviceId": "d4e2f89a-32b1-4c7a-96e5-18e8a93a1001",
  "deviceName": "Pixel 8 Pro (Omni Android)",
  "clientType": "OMNI_ANDROID",
  "clientVersion": "1.2.9.3",
  "supportedEntities": ["BOOKMARK", "FOLDER", "TAB", "HISTORY", "SETTING"],
  "capabilities": {
    "snapshotBootstrap": true,
    "incrementalSync": true,
    "fractionalIndexing": true,
    "maxBatchBytes": 10485760
  }
}
```

### 2.2 Compatibility Rules
1. **Major Version Mismatch:** If `protocolVersion` is higher than `maxSupportedVersion`, the connection must cleanly terminate with `ERR_UNSUPPORTED_VERSION`.
2. **Entity Isolation:** If a peer receives an unknown entity type (e.g. future `PASSKEY`), it must quarantine or ignore the unknown entity without failing the entire sync batch.
3. **Field Extension Safety:** All JSON parsers (Kotlin `org.json` / `kotlinx.serialization` and TypeScript interfaces) MUST ignore unknown JSON fields on known entities (open schema).

---

## 3. Canonical Entity Models

### 3.1 Envelope & Transport Framing

Every sync message sent between peers is wrapped in a `SyncEnvelope`:

```json
{
  "specVersion": 1,
  "messageId": "msg_01HZX87K9Q3E4R7P8A2B5C6D7E",
  "senderDeviceId": "dev_01HZX87K9Q3E4R7P8A2B5C6D7E",
  "recipientDeviceId": "dev_01HZX87L0R4F5S8Q9B3C6D7E8F",
  "timestamp": 1724330400000,
  "sequenceNumber": 42,
  "payloadType": "SYNC_OPERATIONS",
  "payload": { ... }
}
```

---

### 3.2 Bookmark Entity (`BookmarkEntity`)

```json
{
  "entityType": "BOOKMARK",
  "id": "bmk_8f9c0e2a-1b4d-4e3f-9a7c-5b2d1e0f3a4b",
  "parentId": "fld_3a2b1c0d-9e8f-7a6b-5c4d-3e2f1a0b9c8d",
  "position": "a0",
  "title": "GitHub — RebelRoot Omni Browser",
  "url": "https://github.com/REBEL-ROOT/omni-browser",
  "faviconUrl": "https://github.com/favicon.ico",
  "createdAt": 1724330400000,
  "modifiedAt": 1724330400000,
  "isDeleted": false,
  "hlc": "1724330400000:0001:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"
}
```

**Field Rules:**
- `id`: Stable UUID string prefixed with entity type (`bmk_` optional in storage, standard in protocol).
- `parentId`: UUID of containing folder, or `"root"`.
- `position`: String-based fractional index (e.g. `"a0"`, `"a0V"`, `"a1"`).
- `url`: Valid, canonicalized URI string (`http://`, `https://`, `ftp://`). Max 4,096 chars.
- `title`: UTF-8 string. Max 1,024 chars.
- `isDeleted`: Boolean tombstone flag.

---

### 3.3 Folder Entity (`FolderEntity`)

```json
{
  "entityType": "FOLDER",
  "id": "fld_3a2b1c0d-9e8f-7a6b-5c4d-3e2f1a0b9c8d",
  "parentId": "root",
  "position": "a0",
  "title": "Development",
  "createdAt": 1724330400000,
  "modifiedAt": 1724330400000,
  "isDeleted": false,
  "hlc": "1724330400000:0000:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"
}
```

---

### 3.4 Root & Well-Known Virtual Containers

To achieve interoperability between Android (flat/single root) and Desktop (Bookmarks Bar, Other Bookmarks, Mobile Bookmarks), the protocol maps virtual parent IDs:

| Virtual Container ID | Desktop Semantic | Android Semantic |
| :--- | :--- | :--- |
| `"root"` | Root of all bookmarks | Primary root |
| `"bookmarks_bar"` | Chrome/Firefox Bookmarks Bar / Toolbar | Root subfolder or root |
| `"other_bookmarks"` | Chrome "Other Bookmarks" / Firefox "Other" | Root subfolder |
| `"mobile_bookmarks"` | Chrome/Firefox "Mobile Bookmarks" | Root level container |

---

### 3.5 Tab & Session Entity (`TabSessionEntity`)

```json
{
  "entityType": "TAB",
  "id": "tab_5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
  "deviceId": "dev_01HZX87K9Q3E4R7P8A2B5C6D7E",
  "url": "https://en.wikipedia.org/wiki/Distributed_computing",
  "title": "Distributed computing - Wikipedia",
  "faviconUrl": "https://en.wikipedia.org/static/favicon/wikipedia.ico",
  "lastActiveTime": 1724330415000,
  "isPinned": false,
  "isClosed": false,
  "hlc": "1724330415000:0000:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"
}
```

---

### 3.6 History Entity (`HistoryEntryEntity`)

```json
{
  "entityType": "HISTORY",
  "id": "his_a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "url": "https://news.ycombinator.com/",
  "title": "Hacker News",
  "visitTime": 1724330420000,
  "visitCount": 1,
  "isDeleted": false,
  "hlc": "1724330420000:0000:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"
}
```

---

### 3.7 Portable Setting Entity (`PortableSettingEntity`)

```json
{
  "entityType": "SETTING",
  "key": "default_search_engine",
  "valueType": "STRING",
  "value": "DuckDuckGo",
  "modifiedAt": 1724330400000,
  "hlc": "1724330400000:0000:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"
}
```

---

## 4. Operation Calculus & State Machine

### 4.1 Operation Types

Sync mutations are packaged as atomic, immutable operations:

```json
{
  "opId": "op_01HZX87K9Q3E4R7P8A2B5C6D7E",
  "opType": "CREATE",
  "entityType": "BOOKMARK",
  "entityId": "bmk_8f9c0e2a-1b4d-4e3f-9a7c-5b2d1e0f3a4b",
  "hlc": "1724330400000:0001:dev_01HZX87K9Q3E4R7P8A2B5C6D7E",
  "payload": {
    "parentId": "root",
    "position": "a0",
    "title": "Omni Browser",
    "url": "https://omnibrowser.app"
  }
}
```

Supported operation types:
1. `CREATE`: Instantiate a new entity.
2. `UPDATE_CONTENT`: Modify mutable entity content (title, url, faviconUrl) without altering position/parent.
3. `MOVE_REORDER`: Change `parentId` or `position` (fractional index).
4. `DELETE`: Mark entity as deleted (tombstone).
5. `SNAPSHOT_BOOTSTRAP`: Full baseline state transfer for new device pairing.

---

### 4.2 Fractional Indexing & Sibling Ordering

To prevent concurrent insertion conflicts and avoid $O(N)$ re-indexing of sibling bookmarks, Omni Sync uses **lexicographical fractional indexing** (Base-62 / ASCII string keys):

- Initial item 0: `"a0"`
- Initial item 1: `"a1"`
- Initial item 2: `"a2"`
- Insert between `"a0"` and `"a1"`: `"a0V"`
- Insert before `"a0"`: `"Zz"`
- Insert after `"a2"`: `"a3"`

**Invariant:** Sibling nodes under the same `parentId` are strictly ordered by `position` ascending. If two peers generate the exact same position string for different entities, the tie is deterministically broken by `entityId` ascending.

---

### 4.3 Tombstone Lifecycle & Retention

1. Deletion creates a tombstone record with `isDeleted = true` and current `HLC`.
2. Tombstones MUST be retained for at least **30 days** (`TOMBSTONE_TTL_MS = 30 * 24 * 3600 * 1000L`).
3. If an old operation is received for a known tombstone whose HLC is older than the tombstone's HLC, the operation is discarded.
4. If a peer has been offline longer than `TOMBSTONE_TTL_MS`, it MUST perform a full `SNAPSHOT_BOOTSTRAP` rather than applying incremental operations to avoid resurrecting deleted entities.

---

## 5. Conflict Resolution Engine

### 5.1 Conflict Model Selection & Evaluation

| Approach | Consistency Model | Pros | Cons | Decision |
| :--- | :--- | :--- | :--- | :--- |
| **Simple Wall-Clock LWW** | Eventual | Easy to implement | Vulnerable to clock skew, silent overwrites | **REJECTED** |
| **JSON Document Diff/Merge** | Ad-hoc | No custom protocol | Non-deterministic across platforms | **REJECTED** |
| **Pure State-based CRDT** | Strong Eventual | Pure mathematical convergence | Massive metadata overhead | **REJECTED** |
| **Operation Log + HLC LWW-Element-Set + Fractional Indexing** | **Deterministic Eventual Convergence** | **Lightweight metadata, resilient to clock skew, safe tree moves** | **Requires explicit hierarchy checks** | **APPROVED (SELECTED)** |

---

### 5.2 Clock Model: Hybrid Logical Clock (HLC)

Every operation contains an HLC string formatted as:  
`"<physical_millis>:<logical_counter>:<device_id>"`  
(e.g., `"1724330400000:0001:dev_01HZX87K9Q3E4R7P8A2B5C6D7E"`)

**HLC Comparison Algorithm:**
1. Compare `physical_millis` as unsigned 64-bit integer.
2. If equal, compare `logical_counter` as unsigned 16-bit integer.
3. If equal, compare `device_id` lexicographically.
4. Total ordering is strict, deterministic, and identical on all platforms.

---

### 5.3 Structural Conflict Rules (Trees & Hierarchies)

1. **Cycle Prevention:** Before applying a `MOVE_REORDER` operation moving Folder $A$ into Folder $B$, the engine traverses ancestors of $B$. If $A$ is an ancestor of $B$, the move is **rejected** and $A$ remains in its previous valid parent.
2. **Deleted Parent Fallback (No Orphan Loss):** If Entity $E$ is moved into or created in Folder $F$, and $F$ is deleted or missing, $E$ is placed in `"root"` (or a virtual container `"recovered_orphans"`).
3. **Folder Delete vs. Child Mutation:** If Peer 1 edits Bookmark $B$ inside Folder $F$, while Peer 2 deletes Folder $F$:
   - The bookmark edit is preserved.
   - Bookmark $B$ is safely reparented to `"root"` with an updated position.
   - Folder $F$ remains deleted. User data is never silently destroyed.

---

### 5.4 Content Conflict Rules

- **Simultaneous Title / URL Edit on Same Bookmark:** Higher HLC wins.
- **Simultaneous Sibling Reordering:** Positions are sorted lexicographically by `position` string, tie-broken by `entityId`. Both items remain visible side-by-side with deterministic ordering.

---

## 6. Security, Bounds & Input Sanitization

1. **Maximum Payload Batch Size:** 10 MB (`10,485,760` bytes).
2. **Maximum Operations Per Batch:** 1,000 operations.
3. **Maximum Entity String Limits:**
   - `url`: 4,096 UTF-8 characters.
   - `title`: 1,024 UTF-8 characters.
   - `faviconUrl`: 2,048 UTF-8 characters.
   - `entityId` / `parentId`: 128 ASCII characters.
   - `position`: 64 ASCII characters.
4. **URL Scheme Restrictions:** Only `http://`, `https://`, `ftp://`, `gemini://`, and `feed://` URLs are accepted. `javascript:`, `data:`, and `file://` URIs from remote sync peers are strictly **rejected** to prevent remote code injection / XSS across extension contexts.

---

## 7. Cross-Platform Compatibility & Invariants

| Platform | Local Identifier Mapping | Container Mapping |
| :--- | :--- | :--- |
| **Omni Android** | Uses canonical UUID directly (`OmniBookmark.id`) | Maps directly to `BookmarkCollection` root / folders |
| **Chrome Extension (MV3)** | Maps Chrome integer IDs (`"123"`) to Sync UUIDs via `chrome.storage.local` mapping table | Maps Chrome `"1"` (Bookmarks Bar) $\leftrightarrow$ `"bookmarks_bar"`, `"2"` (Other) $\leftrightarrow$ `"other_bookmarks"` |
| **Firefox Extension** | Maps Firefox Places GUIDs (`"places_guid"`) to Sync UUIDs | Maps `toolbar_____` $\leftrightarrow$ `"bookmarks_bar"`, `unfiled_____` $\leftrightarrow$ `"other_bookmarks"` |

---

*Specification approved for Phase 01. Quality gate passed.*
