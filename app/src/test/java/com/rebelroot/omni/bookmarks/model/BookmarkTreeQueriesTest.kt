/*
 * Omni Browser - Bookmark Tree Query Tests
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Pure JVM tests for the bookmark manager's tree navigation helpers.
 */

package com.rebelroot.omni.bookmarks.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkTreeQueriesTest {

    /** Root with: Tech > Android > Kotlin, News > Verge, and a root-level "Home". */
    private class Fixture {
        val collection = BookmarkCollection()
        val tech = collection.addFolder("Tech")
        val android = collection.addFolder("Android", parentId = tech.id)
        val news = collection.addFolder("News")
        val kotlin = collection.addBookmark("Kotlin", "https://kotlinlang.org", parentId = android.id)
        val verge = collection.addBookmark("Verge", "https://theverge.com", parentId = news.id)
        val home = collection.addBookmark("Home", "https://home.example", parentId = ROOT_FOLDER_ID)
        val root: BookmarkNode.Folder = collection.buildTree()
    }

    @Test
    fun `findFolder resolves self, nested folders and unknown ids`() {
        val f = Fixture()
        assertEquals(ROOT_FOLDER_ID, f.root.findFolder(ROOT_FOLDER_ID)?.id)
        assertEquals("Tech", f.root.findFolder(f.tech.id)?.title)
        assertEquals("Android", f.root.findFolder(f.android.id)?.title)
        assertNull(f.root.findFolder("does-not-exist"))
    }

    @Test
    fun `pathToFolder returns the breadcrumb chain root-first`() {
        val f = Fixture()
        assertEquals(
            listOf(ROOT_FOLDER_ID, f.tech.id, f.android.id),
            f.root.pathToFolder(f.android.id).map { it.id }
        )
        assertEquals(listOf(ROOT_FOLDER_ID), f.root.pathToFolder(ROOT_FOLDER_ID).map { it.id })
        assertTrue(f.root.pathToFolder("does-not-exist").isEmpty())
    }

    @Test
    fun `flattenFolders is depth-first with correct depths`() {
        val f = Fixture()
        val all = f.root.flattenFolders()
        assertEquals(
            listOf(ROOT_FOLDER_ID, f.tech.id, f.android.id, f.news.id),
            all.map { it.folder.id }
        )
        assertEquals(listOf(0, 1, 2, 1), all.map { it.depth })
    }

    @Test
    fun `flattenFolders hides excluded subtrees`() {
        val f = Fixture()
        val remaining = f.root.flattenFolders(exclude = setOf(f.tech.id))
        // Excluding Tech also hides its descendant Android.
        assertEquals(listOf(ROOT_FOLDER_ID, f.news.id), remaining.map { it.folder.id })
    }

    @Test
    fun `descendantFolderIds excludes self and includes nested folders`() {
        val f = Fixture()
        val tech = f.root.findFolder(f.tech.id)!!
        assertEquals(setOf(f.android.id), tech.descendantFolderIds())
        assertEquals(
            setOf(f.tech.id, f.android.id, f.news.id),
            f.root.descendantFolderIds()
        )
    }

    @Test
    fun `collectBookmarkPaths pairs each bookmark with its ancestor folders`() {
        val f = Fixture()
        val byUrl = f.root.collectBookmarkPaths().associateBy { it.item.url }

        assertEquals(
            listOf("Tech", "Android"),
            byUrl.getValue("https://kotlinlang.org").folderPath.map { it.title }
        )
        assertEquals(
            listOf("News"),
            byUrl.getValue("https://theverge.com").folderPath.map { it.title }
        )
        // Root-level bookmark has no folder path.
        assertTrue(byUrl.getValue("https://home.example").folderPath.isEmpty())
    }

    @Test
    fun `empty tree yields root only and no bookmarks`() {
        val root = BookmarkCollection().buildTree()
        assertEquals(listOf(ROOT_FOLDER_ID), root.flattenFolders().map { it.folder.id })
        assertTrue(root.collectBookmarkPaths().isEmpty())
        assertTrue(root.descendantFolderIds().isEmpty())
    }
}