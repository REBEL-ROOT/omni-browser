package com.rebelroot.omni.browser

import org.mozilla.geckoview.GeckoSession

fun BrowserViewModel.openFindInPage() {
    showFindInPage = true
    findQuery = ""
    findMatchCurrent = 0
    findMatchTotal = 0
    findMatchFound = true
}

fun BrowserViewModel.closeFindInPage() {
    showFindInPage = false
    findQuery = ""
    findMatchCurrent = 0
    findMatchTotal = 0
    // Clear GeckoView highlights on the current session
    try { geckoSession.finder.clear() } catch (_: Exception) {}
}

/** Called by the UI on every keystroke in the search field. */
fun BrowserViewModel.updateFindQuery(query: String) {
    findQuery = query
    if (query.isEmpty()) {
        findMatchCurrent = 0
        findMatchTotal = 0
        findMatchFound = true
        try { geckoSession.finder.clear() } catch (_: Exception) {}
        return
    }
    geckoSession.finder.find(query, GeckoSession.FINDER_FIND_FORWARD)
        .accept({ result ->
            findMatchFound  = result?.found  ?: false
            findMatchCurrent = result?.current ?: 0
            findMatchTotal  = result?.total   ?: 0
        }, null)
}

fun BrowserViewModel.findNext() {
    if (findQuery.isEmpty()) return
    geckoSession.finder.find(findQuery, GeckoSession.FINDER_FIND_FORWARD)
        .accept({ result ->
            findMatchFound  = result?.found  ?: false
            findMatchCurrent = result?.current ?: 0
            findMatchTotal  = result?.total   ?: 0
        }, null)
}

fun BrowserViewModel.findPrev() {
    if (findQuery.isEmpty()) return
    geckoSession.finder.find(findQuery, GeckoSession.FINDER_FIND_BACKWARDS)
        .accept({ result ->
            findMatchFound  = result?.found  ?: false
            findMatchCurrent = result?.current ?: 0
            findMatchTotal  = result?.total   ?: 0
        }, null)
}
