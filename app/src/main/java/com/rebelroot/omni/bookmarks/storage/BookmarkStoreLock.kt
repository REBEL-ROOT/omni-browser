/*
 * Omni Browser - Bookmark Store Lock
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.bookmarks.storage

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide lock serializing writes to the canonical bookmark store.
 *
 * The bookmarks UI, the Firefox-sync manager and the LAN sync server all mutate
 * the same on-disk file. Loading + mutating + saving is not atomic, so without a
 * shared lock two writers can load the same snapshot and the later save silently
 * discards the other's change. Every writer must hold this for the whole
 * load-modify-save cycle.
 */
object BookmarkStoreLock {
    val mutex: Mutex = Mutex()
}