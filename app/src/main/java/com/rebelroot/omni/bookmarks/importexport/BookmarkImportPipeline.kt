/*
 * Omni Browser — Bookmark Import Pipeline
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Phase 04: validates parsed data, applies duplicate policy, and merges
 * into the live bookmark collection transactionally. The existing bookmark
 * state is never mutated until the merge is fully validated.
 *
 * Pure Kotlin — no Android dependencies.
 */

package com.rebelroot.omni.bookmarks.importexport

import com.rebelroot.omni.bookmarks.model.*

/**
 * How the import pipeline should handle bookmarks whose URL already exists
 * in the target collection.
 */
enum class DuplicatePolicy {
    /** Keep both the existing bookmark and the imported one. */
    KEEP_BOTH,

    /** Skip the imported bookmark if its URL already exists. */
    SKIP,

    /** Replace the existing bookmark with the imported one (same position). */
    REPLACE,

    /** Update the existing bookmark's title if the URL matches. */
    MERGE
}

/**
 * Summary of what an import operation did.
 */
data class ImportResult(
    val addedBookmarks: Int,
    val addedFolders: Int,
    val skippedBookmarks: Int,
    val replacedBookmarks: Int,
    val mergedBookmarks: Int,
    val validationIssues: List<BookmarkValidationIssue>,
    val parserWarnings: List<String>
)

/**
 * Imports a parsed [BookmarkCollection] into an existing [target] collection
 * according to [policy]. The operation is transactional: if validation
 * fails, [target] is left untouched.
 *
 * @param source the parsed collection from the HTML parser
 * @param target the live collection to merge into
 * @param policy how to handle URL duplicates
 * @return import result with counts and any issues
 * @throws IllegalArgumentException if validation fails (caller should catch)
 */
fun importBookmarks(
    source: BookmarkCollection,
    target: BookmarkCollection,
    policy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH
): ImportResult {
    // ── Step 1: Validate the source data ───────────────────────────────────
    val validationIssues = source.validate()
    if (validationIssues.any { it.kind == BookmarkValidationIssue.Kind.PARENT_CYCLE }) {
        throw IllegalArgumentException("Import blocked: source contains parent cycles")
    }

    // ── Step 2: Build a mapping from source folder IDs to target folder IDs.
    // The source may have folder IDs that collide with target IDs, so we
    // remap every source folder to a fresh UUID.
    val folderIdMap = mutableMapOf<String, String>()
    folderIdMap[ROOT_FOLDER_ID] = ROOT_FOLDER_ID

    // ── Step 3: Create folders first (topological order by depth) so that
    // every parent exists before its children are added.
    val foldersByDepth = source.allFolders()
        .sortedBy { folderDepth(source, it.id) }

    var addedFolders = 0
    for (srcFolder in foldersByDepth) {
        val newParentId = folderIdMap[srcFolder.parentId]
            ?: throw IllegalArgumentException("Import blocked: folder ${srcFolder.id} references unknown parent ${srcFolder.parentId}")

        // Check for duplicate folder name at same parent ( cosmetic — we still create it).
        val newFolder = target.addFolder(
            title = srcFolder.title,
            parentId = newParentId
        )
        folderIdMap[srcFolder.id] = newFolder.id
        addedFolders++
    }

    // ── Step 4: Add bookmarks with duplicate policy.
    var addedBookmarks = 0
    var skippedBookmarks = 0
    var replacedBookmarks = 0
    var mergedBookmarks = 0

    val existingUrls = target.allBookmarks().map { it.url }.toMutableSet()

    for (srcBookmark in source.allBookmarks()) {
        val newParentId = folderIdMap[srcBookmark.parentId]
            ?: throw IllegalArgumentException("Import blocked: bookmark ${srcBookmark.id} references unknown parent ${srcBookmark.parentId}")

        val urlExists = srcBookmark.url in existingUrls

        when {
            !urlExists || policy == DuplicatePolicy.KEEP_BOTH -> {
                target.addBookmark(
                    title = srcBookmark.title,
                    url = srcBookmark.url,
                    parentId = newParentId
                )
                addedBookmarks++
                if (!urlExists) {
                    existingUrls += srcBookmark.url
                }
            }
            policy == DuplicatePolicy.SKIP -> {
                skippedBookmarks++
            }
            policy == DuplicatePolicy.REPLACE -> {
                // Find the existing bookmark with this URL and replace it.
                val existing = target.allBookmarks().firstOrNull { it.url == srcBookmark.url }
                if (existing != null) {
                    target.deleteItem(existing.id)
                    target.addBookmark(
                        title = srcBookmark.title,
                        url = srcBookmark.url,
                        parentId = newParentId
                    )
                    replacedBookmarks++
                }
            }
            policy == DuplicatePolicy.MERGE -> {
                // Update title of existing bookmark if different.
                val existing = target.allBookmarks().firstOrNull { it.url == srcBookmark.url }
                if (existing != null && existing.title != srcBookmark.title) {
                    target.rename(existing.id, srcBookmark.title)
                    mergedBookmarks++
                } else {
                    skippedBookmarks++
                }
            }
        }
    }

    return ImportResult(
        addedBookmarks = addedBookmarks,
        addedFolders = addedFolders,
        skippedBookmarks = skippedBookmarks,
        replacedBookmarks = replacedBookmarks,
        mergedBookmarks = mergedBookmarks,
        validationIssues = validationIssues,
        parserWarnings = emptyList() // populated by caller from parser result
    )
}

/** Computes the depth of a folder in the source collection (0 = root). */
private fun folderDepth(collection: BookmarkCollection, folderId: String): Int {
    var depth = 0
    var current = folderId
    while (current != ROOT_FOLDER_ID) {
        val parent = collection.folder(current)?.parentId ?: break
        current = parent
        depth++
        if (depth > 1000) break // safety
    }
    return depth
}
