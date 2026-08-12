/*
 * Omni Browser - Canonical Bookmark Model
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * The canonical bookmark data model. This is the sync-ready foundation that
 * import/export and future Omni Sync build on. It intentionally mirrors the
 * shape used by mainstream browsers (stable string ID + parent ID + position)
 * so browser interoperability is a representation problem, not a model problem.
 *
 * This file is pure Kotlin — no Android dependencies — so the model is fully
 * unit-testable on the JVM.
 */

package com.rebelroot.omni.bookmarks.model

/**
 * Sentinel id of the implicit root folder. The root is not stored as a folder
 * entry; any bookmark/folder whose [parentId] equals this lives at the top
 * level of the bookmark tree.
 */
const val ROOT_FOLDER_ID: String = "root"

/**
 * A leaf bookmark.
 *
 * @param id stable, unique entity id (UUID string) — the future sync identity
 * @param parentId id of the containing folder, or [ROOT_FOLDER_ID]
 * @param position deterministic ordering index within the parent (0-based, dense)
 * @param title display title (may be empty, may duplicate other titles)
 * @param url the bookmark target (may duplicate other URLs)
 * @param createdAt epoch millis of creation
 * @param modifiedAt epoch millis of last modification
 */
data class OmniBookmark(
    val id: String,
    val parentId: String,
    val position: Long,
    val title: String,
    val url: String,
    val createdAt: Long,
    val modifiedAt: Long
)

/**
 * A bookmark folder (may be nested arbitrarily, may be empty).
 *
 * @param id stable, unique entity id (UUID string)
 * @param parentId id of the containing folder, or [ROOT_FOLDER_ID]
 * @param position deterministic ordering index within the parent (0-based, dense)
 * @param title folder name (may be empty)
 * @param createdAt epoch millis of creation
 * @param modifiedAt epoch millis of last modification
 */
data class OmniBookmarkFolder(
    val id: String,
    val parentId: String,
    val position: Long,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long
)
