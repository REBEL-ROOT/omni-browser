/*
 * Omni Browser - Canonical Bookmark Tree Nodes
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Derived tree view of the canonical bookmark model. The stored model is flat
 * (id + parentId + position); this sealed hierarchy is what UI and exporters
 * consume. Pure Kotlin — JVM testable.
 */

package com.rebelroot.omni.bookmarks.model

/**
 * A node in the derived bookmark tree.
 */
sealed interface BookmarkNode {
    val id: String
    val parentId: String
    val position: Long
    val title: String
    val createdAt: Long
    val modifiedAt: Long

    /** A folder node; [children] are ordered by position. */
    data class Folder(
        override val id: String,
        override val parentId: String,
        override val position: Long,
        override val title: String,
        override val createdAt: Long,
        override val modifiedAt: Long,
        val children: List<BookmarkNode>
    ) : BookmarkNode

    /** A leaf bookmark node. */
    data class Item(
        override val id: String,
        override val parentId: String,
        override val position: Long,
        override val title: String,
        val url: String,
        override val createdAt: Long,
        override val modifiedAt: Long
    ) : BookmarkNode
}