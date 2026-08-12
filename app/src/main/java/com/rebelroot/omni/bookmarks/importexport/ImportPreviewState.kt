/*
 * Omni Browser - Bookmark Import Preview State
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Immutable preview of what an import will do, before the user confirms.
 * Used by the import preview UI (Phase 05).
 */

package com.rebelroot.omni.bookmarks.importexport

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.BookmarkNode

/**
 * A preview of an import operation, computed without mutating the live collection.
 *
 * @param sourceCollection the parsed collection from the HTML file (independent copy)
 * @param tree the derived tree view of what will be imported
 * @param totalBookmarks total bookmarks in the import
 * @param totalFolders total folders in the import
 * @param duplicateCount how many URLs already exist in the target (for duplicate-policy hints)
 * @param warnings non-fatal parser warnings
 * @param validationIssues structural issues found in the source data
 */
data class ImportPreviewState(
    val sourceCollection: BookmarkCollection,
    val tree: BookmarkNode.Folder,
    val totalBookmarks: Int,
    val totalFolders: Int,
    val duplicateCount: Int,
    val warnings: List<String>,
    val validationIssues: List<String>
) {
    /** True if the source data has fatal validation issues (cycles, corrupt structure). */
    val hasFatalIssues: Boolean
        get() = validationIssues.isNotEmpty()

    /** True if there are non-fatal warnings the user should see. */
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()
}

/**
 * Result of a confirmed import operation.
 */
data class ImportConfirmationResult(
    val success: Boolean,
    val addedBookmarks: Int,
    val addedFolders: Int,
    val skippedBookmarks: Int,
    val replacedBookmarks: Int,
    val mergedBookmarks: Int,
    val errorMessage: String? = null
)
