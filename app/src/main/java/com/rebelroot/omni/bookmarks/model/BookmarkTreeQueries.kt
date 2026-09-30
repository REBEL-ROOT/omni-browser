/*
 * Omni Browser - Bookmark Tree Queries
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Read-only helpers over the derived bookmark tree (BookmarkNode.Folder).
 * The bookmark manager UI navigates this tree: it resolves the current folder,
 * builds breadcrumbs, flattens folders for the "move to" picker, and searches
 * bookmarks across every folder. Pure Kotlin — JVM testable.
 */

package com.rebelroot.omni.bookmarks.model

/** A folder paired with its depth below the root (root itself is depth 0). */
data class FolderAtDepth(
    val depth: Int,
    val folder: BookmarkNode.Folder
)

/** A bookmark paired with the folders that contain it (root-first, root excluded). */
data class BookmarkWithPath(
    val item: BookmarkNode.Item,
    val folderPath: List<BookmarkNode.Folder>
)

/**
 * Finds the folder with [id] anywhere in this subtree, including this folder
 * itself. Returns null when no such folder exists (e.g. it was just deleted).
 */
fun BookmarkNode.Folder.findFolder(id: String): BookmarkNode.Folder? {
    if (this.id == id) return this
    children.forEach { child ->
        if (child is BookmarkNode.Folder) {
            child.findFolder(id)?.let { return it }
        }
    }
    return null
}

/**
 * Ancestor chain from this folder down to [id], inclusive on both ends
 * (breadcrumb order). Empty when [id] is not inside this subtree.
 */
fun BookmarkNode.Folder.pathToFolder(id: String): List<BookmarkNode.Folder> {
    if (this.id == id) return listOf(this)
    children.forEach { child ->
        if (child is BookmarkNode.Folder) {
            val sub = child.pathToFolder(id)
            if (sub.isNotEmpty()) return listOf(this) + sub
        }
    }
    return emptyList()
}

/**
 * Depth-first flatten of every folder in this subtree, honoring per-parent
 * order. Folder ids in [exclude] (and their descendants) are omitted — the
 * move picker uses this to hide a folder's own subtree as a destination.
 */
fun BookmarkNode.Folder.flattenFolders(exclude: Set<String> = emptySet()): List<FolderAtDepth> {
    val out = mutableListOf<FolderAtDepth>()
    fun walk(node: BookmarkNode.Folder, depth: Int) {
        if (node.id in exclude) return
        out += FolderAtDepth(depth, node)
        node.children.forEach { child ->
            if (child is BookmarkNode.Folder) walk(child, depth + 1)
        }
    }
    walk(this, 0)
    return out
}

/** Ids of every folder nested under this one (this folder excluded). */
fun BookmarkNode.Folder.descendantFolderIds(): Set<String> {
    val out = mutableSetOf<String>()
    fun walk(node: BookmarkNode.Folder) {
        node.children.forEach { child ->
            if (child is BookmarkNode.Folder) {
                out += child.id
                walk(child)
            }
        }
    }
    walk(this)
    return out
}

/**
 * Every bookmark in this subtree paired with its containing folders. The
 * synthetic root (whose title is empty) is never part of a path.
 */
fun BookmarkNode.Folder.collectBookmarkPaths(): List<BookmarkWithPath> {
    val out = mutableListOf<BookmarkWithPath>()
    fun walk(node: BookmarkNode.Folder, path: List<BookmarkNode.Folder>) {
        node.children.forEach { child ->
            when (child) {
                is BookmarkNode.Folder -> walk(child, path + child)
                is BookmarkNode.Item -> out += BookmarkWithPath(child, path)
            }
        }
    }
    walk(this, emptyList())
    return out
}