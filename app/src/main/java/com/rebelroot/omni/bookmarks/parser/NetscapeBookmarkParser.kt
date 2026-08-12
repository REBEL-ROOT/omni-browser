/*
 * Omni Browser - Netscape Bookmark HTML Parser
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Parses the standard Netscape Bookmark HTML format produced by Chrome,
 * Firefox, Edge, Safari, Brave and Opera. The parser is non-executable,
 * operates entirely locally, and never visits URLs.
 *
 * This is a hand-written parser rather than an HTML DOM parser because:
 * 1. Bookmark HTML is a very constrained subset (DL/DT/H3/A only)
 * 2. We need to control recursion depth and attribute size limits
 * 3. We must not pull in heavy dependencies
 * 4. The format is simple enough for a state machine
 *
 * Pure Kotlin — no Android dependencies.
 */

package com.rebelroot.omni.bookmarks.parser

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.model.ROOT_FOLDER_ID
import java.net.URLDecoder
import java.util.UUID

/** Maximum nesting depth to prevent stack overflow on malicious input. */
private const val MAX_DEPTH = 100

/** Maximum attribute length to prevent memory exhaustion. */
private const val MAX_ATTR_LENGTH = 10_000

/** Maximum file size to parse (10 MB). */
private const val MAX_FILE_SIZE = 10 * 1024 * 1024

/**
 * Result of parsing a Netscape Bookmark HTML file.
 */
data class BookmarkHtmlParseResult(
    val collection: BookmarkCollection,
    val warnings: List<ParseWarning>,
    val importedBookmarks: Int,
    val importedFolders: Int,
    val skippedEntries: Int
) {
    val totalEntries: Int get() = importedBookmarks + importedFolders + skippedEntries
}

/**
 * A non-fatal warning produced during parsing.
 */
data class ParseWarning(
    val line: Int,
    val message: String
)

/**
 * Parses a Netscape Bookmark HTML string and returns a [BookmarkCollection].
 *
 * @param html the raw HTML string
 * @param clock injectable clock for tests; defaults to System.currentTimeMillis
 * @return parse result containing the collection and any warnings
 * @throws ParseException on fatal errors (excessive depth, file too large, etc.)
 */
fun parseNetscapeBookmarkHtml(
    html: String,
    clock: () -> Long = System::currentTimeMillis
): BookmarkHtmlParseResult {
    if (html.length > MAX_FILE_SIZE) {
        throw ParseException("File exceeds maximum size of $MAX_FILE_SIZE bytes")
    }

    val collection = BookmarkCollection(clock)
    val warnings = mutableListOf<ParseWarning>()
    var skippedEntries = 0

    // Stack of folder IDs representing the current nesting path.
    // Starts with root.
    val folderStack = mutableListOf(ROOT_FOLDER_ID)
    var currentPosition = mutableMapOf<String, Long>()
    var depth = 0

    // Simple line-by-line state machine.
    val lines = html.lines()
    var lineNum = 0

    for (rawLine in lines) {
        lineNum++
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        // Detect <DL> (start folder contents)
        if (line.contains("<dl", ignoreCase = true)) {
            depth++
            if (depth > MAX_DEPTH) {
                throw ParseException("Exceeded maximum nesting depth of $MAX_DEPTH at line $lineNum")
            }
            continue
        }

        // Detect </DL> (end folder contents)
        if (line.contains("</dl", ignoreCase = true)) {
            if (folderStack.size > 1) {
                folderStack.removeAt(folderStack.size - 1)
            }
            depth = (depth - 1).coerceAtLeast(0)
            continue
        }

        // Detect <DT><H3...> (folder)
        if (line.contains("<h3", ignoreCase = true)) {
            val folderTitle = extractTextContent(line, "h3")
            val folder = collection.addFolder(
                title = folderTitle,
                parentId = folderStack.last()
            )
            folderStack.add(folder.id)
            continue
        }

        // Detect <DT><A...> (bookmark)
        if (line.contains("<a", ignoreCase = true) && line.contains("href", ignoreCase = true)) {
            val url = extractHref(line, lineNum, warnings)
            val title = extractTextContent(line, "a")

            if (url != null && isValidUrl(url)) {
                collection.addBookmark(
                    title = title.ifEmpty { url },
                    url = url,
                    parentId = folderStack.last()
                )
            } else {
                skippedEntries++
                warnings.add(ParseWarning(lineNum, "Skipped invalid or malformed URL: ${url ?: "null"}"))
            }
            continue
        }
    }

    return BookmarkHtmlParseResult(
        collection = collection,
        warnings = warnings,
        importedBookmarks = collection.bookmarkCount(),
        importedFolders = collection.folderCount(),
        skippedEntries = skippedEntries
    )
}

/**
 * Extracts the text content between an opening and closing tag.
 * Handles basic HTML entities.
 */
private fun extractTextContent(line: String, tag: String): String {
    val openPattern = "<$tag[^>]*>"
    val closePattern = "</$tag>"

    val openMatch = openPattern.toRegex(RegexOption.IGNORE_CASE).find(line)
    val closeMatch = closePattern.toRegex(RegexOption.IGNORE_CASE).find(line)

    if (openMatch != null && closeMatch != null) {
        val start = openMatch.range.last + 1
        val end = closeMatch.range.first
        if (start < end) {
            return decodeHtmlEntities(line.substring(start, end).trim())
        }
    }
    return ""
}

/**
 * Extracts the href attribute from an <A> tag.
 */
private fun extractHref(line: String, lineNum: Int, warnings: MutableList<ParseWarning>): String? {
    val hrefPattern = "href=[\"']([^\"']*)[\"']".toRegex(RegexOption.IGNORE_CASE)
    val match = hrefPattern.find(line)
    return match?.groupValues?.get(1)?.let { href ->
        if (href.length > MAX_ATTR_LENGTH) {
            warnings.add(ParseWarning(lineNum, "href attribute exceeds maximum length, truncating"))
            href.take(MAX_ATTR_LENGTH)
        } else {
            href
        }
    }
}

/**
 * Decodes common HTML entities.
 */
private fun decodeHtmlEntities(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

/**
 * Basic URL validation. Accepts http, https, and a few other common schemes.
 */
private fun isValidUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    // Reject javascript:, data:, vbscript: and other dangerous schemes
    val dangerousSchemes = listOf("javascript:", "data:", "vbscript:", "file:", "about:")
    if (dangerousSchemes.any { lower.startsWith(it) }) return false
    return true
}

/**
 * Thrown when parsing cannot continue safely.
 */
class ParseException(message: String) : Exception(message)
