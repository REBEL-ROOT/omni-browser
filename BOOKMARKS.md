# Omni Browser — Bookmark Interoperability System

**Status**: Phase 09 complete (all 10 phases of the Bookmark Interoperability Master Task)  
**Branch**: `feat/bookmark-interoperability`  
**Tests**: 180 passing  
**Last updated**: 2026-08-12

---

## Overview

The bookmark system is a full import/export pipeline that lets Omni Browser users:

1. **Import** bookmarks from Chrome, Firefox, Safari, Edge, Brave, Opera, and any other browser that exports Netscape Bookmark HTML
2. **Preview** the import before committing — see folder structure, counts, warnings, and choose how to handle duplicates
3. **Export** bookmarks to standard Netscape Bookmark HTML for backup or migration to another browser

The system is built on a **canonical bookmark model** (UUID-based, parent-ID + position) that is designed to be the foundation for future Omni Sync.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Android UI Layer                                │
│  BookmarksScreen.kt ──── ImportPreviewScreen.kt                             │
│       │                        │                                            │
│       │    file picker         │    confirm import                          │
│       ▼                        ▼                                            │
│  ┌─────────────────────────────────────────┐                                │
│  │   BookmarkImportViewModelExt.kt         │                                │
│  │   prepareImportPreview()                │                                │
│  │   confirmImport()                       │                                │
│  │   exportBookmarksToFile()               │                                │
│  └─────────────────────────────────────────┘                                │
│       │                        │                    │                       │
│       ▼                        ▼                    ▼                       │
│  ┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │   Parser    │    │ Import Pipeline │    │    Exporter     │             │
│  │  HTML →     │───▶│   merge +       │───▶│   model →       │             │
│  │ collection  │    │   persist       │    │   Netscape HTML │             │
│  └─────────────┘    └─────────────────┘    └─────────────────┘             │
│       │                    │                       │                        │
│       ▼                    ▼                       ▼                        │
│  ┌─────────────────────────────────────────────────────────────┐            │
│  │              BookmarkCollection (in-memory)                  │            │
│  │  OmniBookmark + OmniBookmarkFolder + tree ops + validation   │            │
│  └─────────────────────────────────────────────────────────────┘            │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐            │
│  │         BookmarkStorage v2 (atomic JSON)                   │            │
│  │  browser_bookmarks_v2.json  +  legacy migration            │            │
│  └─────────────────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## File Map

### Model (Pure Kotlin, JVM-testable)

| File | Description |
|------|-------------|
| `model/OmniBookmark.kt` | `OmniBookmark` + `OmniBookmarkFolder` data classes |
| `model/BookmarkNode.kt` | Sealed tree nodes (`Folder` with `children`, `Item`) |
| `model/BookmarkCollection.kt` | In-memory container: add/move/delete/rename/buildTree/validate |

### Parser (Pure Kotlin)

| File | Description |
|------|-------------|
| `parser/NetscapeBookmarkParser.kt` | Line-by-line state machine for DL/DT/H3/A tags |

### Export (Pure Kotlin)

| File | Description |
|------|-------------|
| `export/NetscapeBookmarkExporter.kt` | Tree → deterministic Netscape HTML |

### Import Pipeline (Pure Kotlin)

| File | Description |
|------|-------------|
| `importexport/BookmarkImportPipeline.kt` | `importBookmarks(source, target, policy)` — transactional merge |
| `importexport/ImportPreviewState.kt` | Immutable preview before commit |
| `importexport/BookmarkImportViewModelExt.kt` | Android glue: file picker, preview, confirm, export |

### Storage (Android)

| File | Description |
|------|-------------|
| `storage/BookmarkStorage.kt` | Atomic v2 JSON + automatic legacy migration |

### UI (Android Compose)

| File | Description |
|------|-------------|
| `BookmarksScreen.kt` | Main bookmarks list with search, import/export buttons |
| `ImportPreviewScreen.kt` | Full-screen preview: tree, counts, warnings, duplicate policy |

---

## Canonical Model

```kotlin
data class OmniBookmark(
    val id: String,        // UUID — sync identity
    val parentId: String,  // ROOT_FOLDER_ID or folder UUID
    val position: Long,    // 0-based dense index within parent
    val title: String,
    val url: String,
    val createdAt: Long,
    val modifiedAt: Long
)

data class OmniBookmarkFolder(
    val id: String,
    val parentId: String,
    val position: Long,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long
)
```

**Key invariants** (enforced by `BookmarkCollection`):
- Positions are **dense** (0, 1, 2, … n-1) within each parent
- **No cycles** — moving a folder into its own descendant is rejected
- **No orphan items** — every `parentId` references a real folder or `ROOT_FOLDER_ID`

---

## Duplicate Policies

When importing, users choose how to handle bookmarks whose URL already exists:

| Policy | Behavior |
|--------|----------|
| **KEEP_BOTH** | Add the imported bookmark even if URL duplicates |
| **SKIP** | Ignore imported bookmarks with duplicate URLs |
| **REPLACE** | Delete existing bookmark, add imported one |
| **MERGE** | Update existing bookmark's title if different |

---

## Validation

`BookmarkCollection.validate()` checks 11 kinds of issues:

1. `EMPTY_ID` — blank id string
2. `DUPLICATE_ID` — same id used twice
3. `RESERVED_ROOT_ID` — entity claims `"root"` id
4. `UNKNOWN_PARENT` — parentId doesn't exist
5. `SELF_PARENT` — entity is its own parent
6. `PARENT_CYCLE` — folder loop in ancestry (fatal)
7. `NEGATIVE_POSITION` — position < 0
8. `DUPLICATE_POSITION` — same position used twice in parent
9. `NON_DENSE_POSITION` — gaps in 0..n sequence (fatal)
10. `TIMESTAMP_OUT_OF_RANGE` — before 1970 or after 2100
11. `TIMESTAMP_REVERSED` — createdAt > modifiedAt

**Fatal issues** (block import): `PARENT_CYCLE`, `NON_DENSE_POSITION`

---

## Security Limits

| Limit | Value | Purpose |
|-------|-------|---------|
| Max file size | 10 MB | Prevent memory exhaustion |
| Max nesting depth | 100 | Prevent stack overflow |
| Max attribute length | 10,000 | Prevent memory exhaustion |
| Dangerous schemes | `javascript:`, `data:`, `vbscript:`, `file:`, `about:` | XSS prevention |

---

## Storage v2 Format

```json
{
  "schema_version": 2,
  "bookmarks": [
    { "id": "...", "parentId": "root", "position": 0, "title": "...", "url": "...", "createdAt": 123, "modifiedAt": 123 }
  ],
  "folders": [
    { "id": "...", "parentId": "root", "position": 1, "title": "...", "createdAt": 123, "modifiedAt": 123 }
  ]
}
```

- Writes use **atomic temp-file + rename** — crash during write never corrupts live data
- Automatic **legacy migration** from flat `browser_bookmarks.json` on first load

---

## Test Summary

| Test File | Tests | Focus |
|-----------|-------|-------|
| `BookmarkCollectionTest.kt` | 32 | Core CRUD, tree building, moves, deletes |
| `BookmarkCollectionHardeningTest.kt` | 44 | Validation, ordering, edge cases |
| `BookmarkStorageTest.kt` | 14 | Save/load, migration, atomicity, corruption |
| `NetscapeBookmarkParserTest.kt` | 27 | Parser correctness, entity decoding, limits |
| `BookmarkImportPipelineTest.kt` | 14 | Duplicate policies, transactional merge |
| `BookmarkImportViewModelExtTest.kt` | 4 | Tree flattening, preview state |
| `NetscapeBookmarkExporterTest.kt` | 11 | Export structure, encoding, round-trip |
| `BookmarkFixtureTest.kt` | 15 | Chrome, Firefox, Safari, Edge fixtures + edge cases |
| `StressTest.kt` | 19 | 1000+ items, deep nesting, boundaries, integrity |
| **Total** | **180** | **All passing** |

---

## Round-Trip Guarantee

```
Any browser's export → parse → export → re-parse
produces structurally identical trees (same titles, same URLs, same nesting)
```

Verified for: Chrome, Firefox, Safari, Edge, and synthetic edge cases.

---

## Future Work (Omni Sync)

The canonical model is intentionally sync-ready:
- **Stable UUIDs** are the sync identity
- **parentId + position** map cleanly to Sync's `parentid` + `index`
- **Timestamps** support conflict resolution
- **Validation** catches sync-induced corruption before it reaches storage

The next phase will add a sync adapter layer that converts `OmniBookmark`/`OmniBookmarkFolder` to/from the Omni Sync wire format.
