/*
 * Omni Browser - Canonical Bookmark Collection
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * In-memory container and operation set over the canonical bookmark model.
 * All ordering is deterministic: positions are dense 0-based indexes within a
 * parent. Moving a folder into its own descendant is rejected, so the model
 * can never represent a cycle. Pure Kotlin — JVM testable.
 */

package com.rebelroot.omni.bookmarks.model

import java.util.UUID

/**
 * Mutable, validated collection of bookmarks and folders.
 *
 * The collection is intentionally transport-agnostic: persistence (Phase 02),
 * import (Phase 04) and export (Phase 06) are separate layers that read from
 * and mutate through this API.
 */
class BookmarkCollection(
    /** Injectable clock for deterministic tests; defaults to the wall clock. */
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val bookmarks = LinkedHashMap<String, OmniBookmark>()
    private val folders = LinkedHashMap<String, OmniBookmarkFolder>()

    // ── Reads ────────────────────────────────────────────────────────────────

    fun bookmark(id: String): OmniBookmark? = bookmarks[id]

    fun folder(id: String): OmniBookmarkFolder? = folders[id]

    /** Leaf bookmarks directly inside [parentId], ordered by position. */
    fun bookmarkChildren(parentId: String): List<OmniBookmark> =
        bookmarks.values.filter { it.parentId == parentId }.sortedBy { it.position }

    /** Folders directly inside [parentId], ordered by position. */
    fun folderChildren(parentId: String): List<OmniBookmarkFolder> =
        folders.values.filter { it.parentId == parentId }.sortedBy { it.position }

    /** Total number of items (folders + bookmarks) directly inside [parentId]. */
    fun childCount(parentId: String): Int =
        bookmarks.values.count { it.parentId == parentId } +
            folders.values.count { it.parentId == parentId }

    /** Ids of every item inside [parentId], ordered by position (interleaved across folders and bookmarks). */
    fun childIds(parentId: String): List<String> = mutableChildIds(parentId)

    /** All bookmarks (insertion order — for storage round-trips, use the tree). */
    fun allBookmarks(): List<OmniBookmark> = bookmarks.values.toList()

    /** All folders (insertion order). */
    fun allFolders(): List<OmniBookmarkFolder> = folders.values.toList()

    fun bookmarkCount(): Int = bookmarks.size

    fun folderCount(): Int = folders.size

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Adds a leaf bookmark to [parentId] (appended at the end unless an
     * explicit [position] is given). Duplicate URLs and duplicate titles are
     * allowed — identity is the id, never the URL.
     */
    fun addBookmark(
        title: String,
        url: String,
        parentId: String = ROOT_FOLDER_ID,
        position: Long? = null
    ): OmniBookmark {
        requireParentExists(parentId)
        val now = clock()
        val entry = OmniBookmark(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            position = position ?: nextPosition(parentId),
            title = title,
            url = url,
            createdAt = now,
            modifiedAt = now
        )
        bookmarks[entry.id] = entry
        return entry
    }

    /**
     * Adds a folder to [parentId] (appended at the end unless an explicit
     * [position] is given). Folders may be empty and may nest arbitrarily.
     */
    fun addFolder(
        title: String,
        parentId: String = ROOT_FOLDER_ID,
        position: Long? = null
    ): OmniBookmarkFolder {
        requireParentExists(parentId)
        val now = clock()
        val entry = OmniBookmarkFolder(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            position = position ?: nextPosition(parentId),
            title = title,
            createdAt = now,
            modifiedAt = now
        )
        folders[entry.id] = entry
        return entry
    }

    /**
     * Moves any item (bookmark or folder) to [newParentId] at [newIndex]
     * (0-based; clamped to the valid range). Positions stay dense after the
     * move. Moving a folder into itself or its own descendant throws
     * [IllegalArgumentException] — the model must stay acyclic.
     *
     * @return false if [id] is unknown
     */
    fun moveItem(id: String, newParentId: String, newIndex: Int): Boolean {
        val entry = find(id) ?: return false
        requireParentExists(newParentId)

        val isFolderEntry = entry is FolderEntry
        if (isFolderEntry) {
            require(newParentId != id) { "Cannot move a folder into itself" }
            require(newParentId !in descendantsOf(id)) { "Cannot move a folder into its own descendant" }
        }

        val oldParent = entry.parentId
        val oldSiblings = mutableChildIds(oldParent)
        val oldIndex = oldSiblings.indexOf(id)
        require(oldIndex >= 0) { "Item $id has no position in parent $oldParent" }
        oldSiblings.removeAt(oldIndex)

        if (oldParent == newParentId) {
            // Final-position semantics: after removal there are n-1 siblings,
            // so the insertion index is simply the clamped target.
            val target = newIndex.coerceIn(0, oldSiblings.size)
            oldSiblings.add(target, id)
            reindex(oldSiblings, oldParent)
        } else {
            reindex(oldSiblings, oldParent) // origin parent lost one element
            val newSiblings = mutableChildIds(newParentId)
            val target = newIndex.coerceIn(0, newSiblings.size)
            newSiblings.add(target, id)
            setParent(id, newParentId)
            reindex(newSiblings, newParentId)
        }
        touch(id)
        return true
    }

    /**
     * Deletes an item. Deleting a folder cascades to every descendant
     * bookmark and subfolder. Positions of surviving siblings stay dense.
     *
     * @return false if [id] is unknown
     */
    fun deleteItem(id: String): Boolean {
        val doomedFolderIds: Set<String>
        val doomedBookmarkIds: Set<String>
        if (folders.containsKey(id)) {
            doomedFolderIds = descendantsOf(id) + id
            doomedBookmarkIds = bookmarks.values.filter { it.parentId in doomedFolderIds }.map { it.id }.toSet()
        } else if (bookmarks.containsKey(id)) {
            doomedFolderIds = emptySet()
            doomedBookmarkIds = setOf(id)
        } else {
            return false
        }
        // Every parent that loses a child must be re-indexed to stay dense.
        val affectedParents = LinkedHashSet<String>()
        doomedBookmarkIds.forEach { affectedParents += bookmarks[it]?.parentId ?: return@forEach }
        doomedFolderIds.forEach { affectedParents += folders[it]?.parentId ?: return@forEach }
        doomedFolderIds.forEach { folders.remove(it) }
        doomedBookmarkIds.forEach { bookmarks.remove(it) }
        affectedParents
            .filter { it == ROOT_FOLDER_ID || folders.containsKey(it) }
            .forEach { reindex(mutableChildIds(it), it) }
        return true
    }

    /** Renames an item. @return false if [id] is unknown. */
    fun rename(id: String, newTitle: String): Boolean {
        val entry = find(id) ?: return false
        val now = clock()
        when (entry) {
            is FolderEntry -> folders[id] = folders.getValue(id).copy(title = newTitle, modifiedAt = now)
            is ItemEntry -> bookmarks[id] = bookmarks.getValue(id).copy(title = newTitle, modifiedAt = now)
        }
        return true
    }

    /** Updates a bookmark URL. @return false if [id] is unknown or a folder. */
    fun setUrl(id: String, newUrl: String): Boolean {
        val entry = bookmarks[id] ?: return false
        bookmarks[id] = entry.copy(url = newUrl, modifiedAt = clock())
        return true
    }

    // ── Tree ─────────────────────────────────────────────────────────────────

    /**
     * Builds the derived tree, root first, preserving per-parent ordering.
     * There are no allowed shared references, so a simple recursion is safe
     * (invariant: the model is acyclic and parents always reference real ids).
     */
    fun buildTree(): BookmarkNode.Folder = buildChildren(ROOT_FOLDER_ID).let { children ->
        BookmarkNode.Folder(
            id = ROOT_FOLDER_ID,
            parentId = "",
            position = 0,
            title = "",
            createdAt = 0,
            modifiedAt = 0,
            children = children
        )
    }

    private fun buildChildren(parentId: String): List<BookmarkNode> =
        childIds(parentId).mapNotNull { id ->
            folders[id]?.let { f ->
                BookmarkNode.Folder(
                    id = f.id,
                    parentId = f.parentId,
                    position = f.position,
                    title = f.title,
                    createdAt = f.createdAt,
                    modifiedAt = f.modifiedAt,
                    children = buildChildren(f.id)
                )
            } ?: bookmarks[id]?.let { b ->
                BookmarkNode.Item(
                    id = b.id,
                    parentId = b.parentId,
                    position = b.position,
                    title = b.title,
                    url = b.url,
                    createdAt = b.createdAt,
                    modifiedAt = b.modifiedAt
                )
            }
        }

    /**
     * Ids of every folder nested under [folderId] (excluding [folderId]).
     * Iterative to stay safe on very deep trees. Returns empty for an unknown id.
     */
    fun descendantsOf(folderId: String): Set<String> {
        val result = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(folderId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            folders.values.forEach { f ->
                if (f.parentId == current && result.add(f.id)) stack.addLast(f.id)
            }
        }
        return result
    }

    // ── Integrity ────────────────────────────────────────────────────────────

    /** Structural integrity of the current state. Empty list = valid. */
    fun validate(): List<BookmarkValidationIssue> =
        validate(allBookmarks(), allFolders())

    // ── Storage-layer restore (migration / import / deserialization) ────────

    /**
     * Replaces the entire collection with [newBookmarks] and [newFolders]
     * exactly as given. **Does not validate** — the storage and import layers
     * must call [validate] on the candidate dataset first and only commit when
     * the data is clean (fatal errors must never partially corrupt the live
     * bookmark state). The UI never calls this.
     */
    fun replaceAll(newBookmarks: List<OmniBookmark>, newFolders: List<OmniBookmarkFolder>) {
        bookmarks.clear()
        folders.clear()
        newBookmarks.forEach { bookmarks[it.id] = it }
        newFolders.forEach { folders[it.id] = it }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private sealed interface Entry {
        val id: String
        val parentId: String
    }

    private data class FolderEntry(override val id: String, override val parentId: String) : Entry
    private data class ItemEntry(override val id: String, override val parentId: String) : Entry

    private fun find(id: String): Entry? =
        folders[id]?.let { FolderEntry(it.id, it.parentId) } ?: bookmarks[id]?.let { ItemEntry(it.id, it.parentId) }

    private fun requireParentExists(parentId: String) {
        require(parentId == ROOT_FOLDER_ID || folders.containsKey(parentId)) { "Unknown parent folder $parentId" }
    }

    private fun nextPosition(parentId: String): Long = childCount(parentId).toLong()

    /** Sorted ids of every item directly inside [parentId] (mutable for internal reordering). */
    private fun mutableChildIds(parentId: String): MutableList<String> =
        (folders.values.filter { it.parentId == parentId }.map { it.id to it.position } +
            bookmarks.values.filter { it.parentId == parentId }.map { it.id to it.position })
            .sortedBy { it.second }
            .map { it.first }
            .toMutableList()

    /** Reassigns dense 0-based positions for the given items inside [parentId]. */
    private fun reindex(ids: List<String>, parentId: String) {
        ids.forEachIndexed { index, id ->
            folders[id]?.let { folders[id] = it.copy(position = index.toLong()) }
                ?: bookmarks[id]?.let { bookmarks[id] = it.copy(position = index.toLong()) }
        }
    }

    private fun setParent(id: String, newParentId: String) {
        folders[id]?.let { folders[id] = it.copy(parentId = newParentId) }
            ?: bookmarks[id]?.let { bookmarks[id] = it.copy(parentId = newParentId) }
    }

    private fun touch(id: String) {
        val now = clock()
        folders[id]?.let { folders[id] = it.copy(modifiedAt = now) }
            ?: bookmarks[id]?.let { bookmarks[id] = it.copy(modifiedAt = now) }
    }

    companion object {
        /**
         * Validates a candidate dataset **without mutating anything**. The
         * storage and import layers use this before committing untrusted data
         * (legacy JSON, parsed bookmark HTML) via [replaceAll].
         */
        fun validate(newBookmarks: List<OmniBookmark>, newFolders: List<OmniBookmarkFolder>): List<BookmarkValidationIssue> {
            val issues = mutableListOf<BookmarkValidationIssue>()
            val allIds = mutableSetOf<String>()
            val knownFolders = newFolders.map { it.id }.toMutableSet()

            // ── ID validation (bookmarks) ──────────────────────────────────
            newBookmarks.forEach { b ->
                if (b.id.isBlank()) {
                    issues += BookmarkValidationIssue(b.id, BookmarkValidationIssue.Kind.EMPTY_ID, "bookmark has empty id")
                    return@forEach
                }
                if (b.id == ROOT_FOLDER_ID) {
                    issues += BookmarkValidationIssue(b.id, BookmarkValidationIssue.Kind.RESERVED_ROOT_ID, "bookmark uses reserved root id")
                }
                if (!allIds.add(b.id)) {
                    issues += BookmarkValidationIssue(b.id, BookmarkValidationIssue.Kind.DUPLICATE_ID, "duplicate id $b.id across bookmarks and folders")
                }
            }

            // ── ID validation (folders) ────────────────────────────────────
            newFolders.forEach { f ->
                if (f.id.isBlank()) {
                    issues += BookmarkValidationIssue(f.id, BookmarkValidationIssue.Kind.EMPTY_ID, "folder has empty id")
                    return@forEach
                }
                if (f.id == ROOT_FOLDER_ID) {
                    issues += BookmarkValidationIssue(f.id, BookmarkValidationIssue.Kind.RESERVED_ROOT_ID, "folder uses reserved root id")
                }
                if (!allIds.add(f.id)) {
                    issues += BookmarkValidationIssue(f.id, BookmarkValidationIssue.Kind.DUPLICATE_ID, "duplicate id ${f.id} across bookmarks and folders")
                }
            }

            // ── Parent/type/position/timestamp validation (bookmarks) ─────
            newBookmarks.forEach { b ->
                validateParent(b.id, b.parentId, "bookmark", b, issues, knownFolders)
                if (b.position < 0) {
                    issues += BookmarkValidationIssue(b.id, BookmarkValidationIssue.Kind.NEGATIVE_POSITION, "bookmark has negative position ${b.position}")
                }
                validateTimestamps(b.id, b.createdAt, b.modifiedAt, "bookmark", issues)
            }

            // ── Parent/type/position/timestamp validation (folders) ───────
            newFolders.forEach { f ->
                validateParent(f.id, f.parentId, "folder", f, issues, knownFolders)
                if (f.position < 0) {
                    issues += BookmarkValidationIssue(f.id, BookmarkValidationIssue.Kind.NEGATIVE_POSITION, "folder has negative position ${f.position}")
                }
                validateTimestamps(f.id, f.createdAt, f.modifiedAt, "folder", issues)
            }

            // ── Position density (fatal) ───────────────────────────────────
            issues += computePositionDensityIssues(newBookmarks, newFolders)

            // ── Cycle detection (fatal) ────────────────────────────────────
            issues += computeCycleIssues(newFolders)

            return issues
        }

        private fun validateParent(
            id: String, parentId: String, label: String,
            entity: Any, issues: MutableList<BookmarkValidationIssue>, knownFolders: Set<String>
        ) {
            if (parentId == id) {
                issues += BookmarkValidationIssue(id, BookmarkValidationIssue.Kind.SELF_PARENT, "$label is its own parent")
                return
            }
            if (parentId != ROOT_FOLDER_ID && parentId !in knownFolders) {
                issues += BookmarkValidationIssue(id, BookmarkValidationIssue.Kind.UNKNOWN_PARENT, "$label references missing parent $parentId")
            }
            // Bookmarks must not be parented to a bookmark.
            if (label == "bookmark" && entity is OmniBookmark && entity.parentId != ROOT_FOLDER_ID && entity.parentId !in knownFolders) {
                // Already caught as UNKNOWN_PARENT; no extra issue needed.
            }
        }

        private fun validateTimestamps(
            id: String, createdAt: Long, modifiedAt: Long,
            label: String, issues: MutableList<BookmarkValidationIssue>
        ) {
            // Future upper bound: year 2100
            val maxTs = 4_102_444_800_000L
            if (createdAt < 0 || createdAt > maxTs) {
                issues += BookmarkValidationIssue(id, BookmarkValidationIssue.Kind.TIMESTAMP_OUT_OF_RANGE, "$label createdAt $createdAt out of sensible range")
            }
            if (modifiedAt < 0 || modifiedAt > maxTs) {
                issues += BookmarkValidationIssue(id, BookmarkValidationIssue.Kind.TIMESTAMP_OUT_OF_RANGE, "$label modifiedAt $modifiedAt out of sensible range")
            }
            if (createdAt > modifiedAt) {
                issues += BookmarkValidationIssue(id, BookmarkValidationIssue.Kind.TIMESTAMP_REVERSED, "$label createdAt $createdAt > modifiedAt $modifiedAt")
            }
        }

        private fun computePositionDensityIssues(
            newBookmarks: List<OmniBookmark>,
            newFolders: List<OmniBookmarkFolder>
        ): List<BookmarkValidationIssue> {
            val issues = mutableListOf<BookmarkValidationIssue>()
            val itemsByParent = mutableMapOf<String, MutableSet<Long>>()

            fun record(parent: String, pos: Long) {
                itemsByParent.getOrPut(parent) { mutableSetOf() }.add(pos)
            }
            newBookmarks.forEach { record(it.parentId, it.position) }
            newFolders.forEach { record(it.parentId, it.position) }

            itemsByParent.forEach { (parent, positions) ->
                if (positions.isEmpty()) return@forEach
                val sorted = positions.sorted()
                val childCount = newBookmarks.count { it.parentId == parent } +
                    newFolders.count { it.parentId == parent }

                // Must start at 0.
                if (sorted.first() != 0L) {
                    issues += BookmarkValidationIssue(
                        parent,
                        BookmarkValidationIssue.Kind.NON_DENSE_POSITION,
                        "parent $parent positions do not start at 0 (first=${sorted.first()})"
                    )
                } else {
                    // Must be dense: every value 0..max must exist.
                    val expected = (0L until sorted.size.toLong()).toSet()
                    if (sorted.toSet() != expected) {
                        issues += BookmarkValidationIssue(
                            parent,
                            BookmarkValidationIssue.Kind.NON_DENSE_POSITION,
                            "parent $parent positions are not dense 0..${sorted.size - 1} (actual=$sorted)"
                        )
                    }
                }
                // Duplicate detection: size mismatch means duplicates.
                if (sorted.size != childCount) {
                    issues += BookmarkValidationIssue(
                        parent,
                        BookmarkValidationIssue.Kind.DUPLICATE_POSITION,
                        "parent $parent has duplicate positions ($childCount children, ${sorted.size} unique positions)"
                    )
                }
            }
            return issues
        }

        private fun computeCycleIssues(newFolders: List<OmniBookmarkFolder>): List<BookmarkValidationIssue> {
            val issues = mutableListOf<BookmarkValidationIssue>()
            val parentOf = newFolders.associate { it.id to it.parentId }
            val knownIds = newFolders.map { it.id }.toSet()

            for (folder in newFolders) {
                if (folder.parentId == ROOT_FOLDER_ID) continue
                if (folder.parentId == folder.id) continue // already SELF_PARENT
                if (folder.parentId !in knownIds) continue // already UNKNOWN_PARENT

                // Walk ancestors.
                val visited = mutableSetOf<String>()
                var current = folder.parentId
                while (current != ROOT_FOLDER_ID && current in knownIds) {
                    if (!visited.add(current)) {
                        issues += BookmarkValidationIssue(
                            folder.id,
                            BookmarkValidationIssue.Kind.PARENT_CYCLE,
                            "folder ${folder.id} is part of a parent cycle involving ancestor $current"
                        )
                        break
                    }
                    current = parentOf[current] ?: break
                    if (current == folder.id) {
                        issues += BookmarkValidationIssue(
                            folder.id,
                            BookmarkValidationIssue.Kind.PARENT_CYCLE,
                            "folder ${folder.id} is its own ancestor (cycle)"
                        )
                        break
                    }
                }
            }
            return issues
        }
    }
}

/** A structural integrity problem found by [BookmarkCollection.validate]. */
data class BookmarkValidationIssue(
    val id: String,
    val kind: Kind,
    val message: String
) {
    enum class Kind {
        EMPTY_ID,
        DUPLICATE_ID,
        RESERVED_ROOT_ID,
        UNKNOWN_PARENT,
        SELF_PARENT,
        PARENT_CYCLE,
        NEGATIVE_POSITION,
        DUPLICATE_POSITION,
        NON_DENSE_POSITION,
        TIMESTAMP_OUT_OF_RANGE,
        TIMESTAMP_REVERSED
    }
}
