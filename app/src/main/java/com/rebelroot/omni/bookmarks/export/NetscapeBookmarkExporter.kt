/*
 * Omni Browser - Netscape Bookmark HTML Exporter
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Exports the canonical bookmark model to Netscape Bookmark HTML format.
 * This is the inverse of the parser (Phase 03): tree → HTML string.
 *
 * The output is byte-for-byte deterministic (sorted by position, fixed
 * indentation) so it can be consumed by any browser that imports bookmarks.
 *
 * Phase 06.
 *
 * Pure Kotlin — no Android dependencies.
 */

package com.rebelroot.omni.bookmarks.export

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.BookmarkNode

/**
 * Exports the entire bookmark [collection] as a Netscape Bookmark HTML string.
 *
 * The output includes:
 * - Standard DOCTYPE and META charset header
 * - Every folder as `<DT><H3>` with `ADD_DATE` and `LAST_MODIFIED` (seconds)
 * - Every bookmark as `<DT><A HREF>` with `ADD_DATE` and `LAST_MODIFIED`
 * - Proper HTML entity encoding in titles and URLs
 * - Children ordered by position within each parent
 *
 * @param collection the source bookmarks
 * @param title the document title (default "Bookmarks")
 * @return well-formed Netscape Bookmark HTML
 */
fun exportNetscapeBookmarkHtml(
    collection: BookmarkCollection,
    title: String = "Bookmarks"
): String = buildString {
    appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
    appendLine("<!-- This is an automatically generated file.")
    appendLine("     It will be read and overwritten.")
    appendLine("     DO NOT EDIT! -->")
    appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
    appendLine("<TITLE>${encodeHtmlEntities(title)}</TITLE>")
    appendLine("<H1>${encodeHtmlEntities(title)}</H1>")

    val tree = collection.buildTree()
    renderNode(tree, this, indent = 0)
}

// ── Internal rendering ─────────────────────────────────────────────────────

private fun renderNode(
    node: BookmarkNode,
    out: StringBuilder,
    indent: Int
) {
    val prefix = "  ".repeat(indent)

    when (node) {
        is BookmarkNode.Folder -> {
            // Root folder is not emitted as H3; its children go directly into a DL.
            if (node.id != com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID) {
                val addDate = millisToSeconds(node.createdAt)
                val lastModified = millisToSeconds(node.modifiedAt)
                out.appendLine(
                    "${prefix}<DT><H3 ADD_DATE=\"$addDate\" LAST_MODIFIED=\"$lastModified\">" +
                    "${encodeHtmlEntities(node.title)}</H3>"
                )
            }
            out.appendLine("${prefix}<DL><p>")
            node.children.forEach { child ->
                renderNode(child, out, indent + 1)
            }
            out.appendLine("${prefix}</DL><p>")
        }
        is BookmarkNode.Item -> {
            val addDate = millisToSeconds(node.createdAt)
            val lastModified = millisToSeconds(node.modifiedAt)
            out.appendLine(
                "${prefix}<DT><A HREF=\"${encodeHtmlAttribute(node.url)}\" " +
                "ADD_DATE=\"$addDate\" LAST_MODIFIED=\"$lastModified\">" +
                "${encodeHtmlEntities(node.title)}</A>"
            )
        }
    }
}

// ── Encoding helpers ───────────────────────────────────────────────────────

/** Converts milliseconds to seconds (Netscape format). */
private fun millisToSeconds(millis: Long): Long = millis / 1000L

/**
 * Encodes text content for HTML body (titles inside H3 and A tags).
 * Reverses the decoding done by the parser.
 */
private fun encodeHtmlEntities(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

/**
 * Encodes a string for use inside a double-quoted HTML attribute.
 * This is stricter than body encoding because quotes must be escaped.
 */
private fun encodeHtmlAttribute(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
