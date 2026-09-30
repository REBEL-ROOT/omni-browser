package com.rebelroot.omni.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the source rewrite applied to Bitwarden's `content/bootstrap-autofill-overlay*.js`
 * bundles, which is what makes the inline autofill suggestion list visible on GeckoView.
 */
class InlineMenuPatchTest {

    private val bundleSnippet = """
        class AutofillInlineMenuIframeService {
            updateIframePosition(position) {
                if (!position || !globalThis.document.hasFocus()) {
                    return;
                }
                this.updateElementStyles(this.iframe, position);
                const inViewport = this.isElementCompletelyWithinViewport(this.iframe.getBoundingClientRect());
                if (!inViewport) {
                    this.forceCloseInlineMenu();
                }
            }
            isElementCompletelyWithinViewport(elementPosition) {
                if (!elementPosition.height || !elementPosition.width) {
                    return true;
                }
                return true;
            }
        }
    """.trimIndent()

    @Test
    fun `drops the document focus gate in updateIframePosition`() {
        val rewritten = rewriteInlineMenuScript(bundleSnippet)

        assertFalse(
            "the hasFocus() gate must be removed so position/height styles always apply",
            rewritten.contains("globalThis.document.hasFocus()")
        )
        assertTrue(rewritten.contains("if (!position) {"))
        // Only the updateIframePosition guard is touched, never a bare hasFocus check elsewhere.
        assertFalse(rewritten.contains("if (!position || ") )
    }

    @Test
    fun `forces the viewport check to pass without touching the call site`() {
        val rewritten = rewriteInlineMenuScript(bundleSnippet)

        assertTrue(
            rewritten.contains("isElementCompletelyWithinViewport(elementPosition) { return true;")
        )
        assertEquals(
            "the call site must remain untouched",
            1,
            Regex("this\\.isElementCompletelyWithinViewport\\(").findAll(rewritten).count()
        )
    }

    @Test
    fun `leaves an unrecognised bundle untouched`() {
        val unrelated = "function doThing(position) { return position; }"
        assertEquals(unrelated, rewriteInlineMenuScript(unrelated))
    }
}