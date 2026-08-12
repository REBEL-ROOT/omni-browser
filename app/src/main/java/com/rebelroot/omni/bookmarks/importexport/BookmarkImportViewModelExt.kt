/*
 * Omni Browser - Bookmark Import ViewModel Extension
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Extension functions on BrowserViewModel that wire the import pipeline
 * (parser + preview + merge) into the Android UI layer. Phase 05.
 */

package com.rebelroot.omni.bookmarks.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.BookmarkNode
import com.rebelroot.omni.bookmarks.parser.parseNetscapeBookmarkHtml
import com.rebelroot.omni.bookmarks.storage.loadBookmarks
import com.rebelroot.omni.bookmarks.storage.saveBookmarks
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Public API ─────────────────────────────────────────────────────────────

/**
 * Reads a Netscape Bookmark HTML file from [uri], parses it, and produces
 * an [ImportPreviewState] without mutating the live bookmark collection.
 *
 * @param context Android context
 * @param uri the content URI of the HTML file
 * @param onResult callback with the preview state or an error message
 */
fun BrowserViewModel.prepareImportPreview(
    context: Context,
    uri: Uri,
    onResult: (Result<ImportPreviewState>) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val html = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("Cannot read file")

            // Parse the HTML into a standalone collection.
            val parseResult = parseNetscapeBookmarkHtml(html)

            // Build tree for preview.
            val tree = parseResult.collection.buildTree()

            // Count duplicates against the live collection.
            val liveCollection = loadBookmarks(context)
            val existingUrls = liveCollection.allBookmarks().map { bmk -> bmk.url }.toSet()
            val duplicateCount = parseResult.collection.allBookmarks().count { bmk -> bmk.url in existingUrls }

            val preview = ImportPreviewState(
                sourceCollection = parseResult.collection,
                tree = tree,
                totalBookmarks = parseResult.importedBookmarks,
                totalFolders = parseResult.importedFolders,
                duplicateCount = duplicateCount,
                warnings = parseResult.warnings.map { w -> "Line ${w.line}: ${w.message}" },
                validationIssues = parseResult.collection.validate().map { issue -> "${issue.kind}: ${issue.message}" }
            )

            withContext(Dispatchers.Main) {
                this@prepareImportPreview.importPreview = preview
                onResult(Result.success(preview))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(Result.failure(e))
            }
        }
    }
}

/**
 * Confirms the import: merges the previewed [sourceCollection] into the live
 * bookmarks using [policy], persists to disk, and clears the preview state.
 *
 * @param context Android context
 * @param policy how to handle duplicate URLs
 * @param onResult callback with the confirmation result
 */
fun BrowserViewModel.confirmImport(
    context: Context,
    policy: DuplicatePolicy,
    onResult: (ImportConfirmationResult) -> Unit
) {
    val preview = importPreview ?: run {
        onResult(ImportConfirmationResult(false, 0, 0, 0, 0, 0, "No import preview available"))
        return
    }

    isImporting = true
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val liveCollection = loadBookmarks(context)
            val result = importBookmarks(
                source = preview.sourceCollection,
                target = liveCollection,
                policy = policy
            )
            saveBookmarks(context, liveCollection)

            withContext(Dispatchers.Main) {
                this@confirmImport.importPreview = null
                this@confirmImport.isImporting = false
                onResult(
                    ImportConfirmationResult(
                        success = true,
                        addedBookmarks = result.addedBookmarks,
                        addedFolders = result.addedFolders,
                        skippedBookmarks = result.skippedBookmarks,
                        replacedBookmarks = result.replacedBookmarks,
                        mergedBookmarks = result.mergedBookmarks
                    )
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                this@confirmImport.isImporting = false
                onResult(
                    ImportConfirmationResult(
                        success = false,
                        addedBookmarks = 0,
                        addedFolders = 0,
                        skippedBookmarks = 0,
                        replacedBookmarks = 0,
                        mergedBookmarks = 0,
                        errorMessage = e.message ?: "Unknown import error"
                    )
                )
            }
        }
    }
}

/**
 * Clears the current import preview state (e.g. when the user cancels).
 */
fun BrowserViewModel.clearImportPreview() {
    importPreview = null
}

/**
 * Recursively flattens a tree node into a list of (depth, node) pairs for UI display.
 */
fun flattenTreeForPreview(node: BookmarkNode, depth: Int = 0): List<Pair<Int, BookmarkNode>> {
    val result = mutableListOf<Pair<Int, BookmarkNode>>()
    result += depth to node
    if (node is BookmarkNode.Folder) {
        node.children.forEach { child ->
            result += flattenTreeForPreview(child, depth + 1)
        }
    }
    return result
}
