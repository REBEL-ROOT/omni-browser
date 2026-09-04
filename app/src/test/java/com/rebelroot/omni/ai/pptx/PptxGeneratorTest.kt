package com.rebelroot.omni.ai.pptx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class PptxGeneratorTest {

    @Test
    fun `buildBytes returns valid PPTX archive for single slide`() {
        val bytes = PptxGenerator.buildBytes(
            slides = listOf(
                PptxSlide(
                    title = "Hello",
                    bullets = listOf("One", "Two", "Three")
                )
            ),
            title = "Test Presentation"
        )
        assertNotNull("buildBytes should return non-null bytes", bytes)
        val entries = readZipEntries(bytes!!)
        // Required parts
        assertTrue("[Content_Types].xml missing", "[Content_Types].xml" in entries)
        assertTrue("_rels/.rels missing", "_rels/.rels" in entries)
        assertTrue("ppt/presentation.xml missing", "ppt/presentation.xml" in entries)
        assertTrue("ppt/slides/slide1.xml missing", "ppt/slides/slide1.xml" in entries)
        assertTrue("docProps/core.xml missing", "docProps/core.xml" in entries)
        assertTrue("docProps/app.xml missing", "docProps/app.xml" in entries)
    }

    @Test
    fun `buildBytes supports multiple slides`() {
        val slides = (1..5).map { i ->
            PptxSlide(title = "Slide $i", bullets = listOf("Bullet ${i}a", "Bullet ${i}b"))
        }
        val bytes = PptxGenerator.buildBytes(slides, "Multi Slide")!!
        val entries = readZipEntries(bytes)
        for (i in 1..5) {
            assertTrue("slide $i missing", "ppt/slides/slide$i.xml" in entries)
        }
        val presRels = String(entries["ppt/_rels/presentation.xml.rels"]!!, Charsets.UTF_8)
        // Should reference 5 slide relationships (Target=slides/slideN.xml)
        assertTrue("presentation rels should reference slide1", presRels.contains("slides/slide1.xml"))
        assertTrue("presentation rels should reference slide5", presRels.contains("slides/slide5.xml"))
    }

    @Test
    fun `content types reference all slide overrides`() {
        val slides = (1..3).map { PptxSlide(title = "S$it", bullets = listOf("x")) }
        val bytes = PptxGenerator.buildBytes(slides, "T")!!
        val entries = readZipEntries(bytes)
        val ct = String(entries["[Content_Types].xml"]!!, Charsets.UTF_8)
        for (i in 1..3) {
            assertTrue("Content type override for slide $i missing", ct.contains("slides/slide$i.xml"))
        }
    }

    @Test
    fun `slide xml contains title and bullet text`() {
        val bytes = PptxGenerator.buildBytes(
            listOf(PptxSlide("MyTitle", bullets = listOf("Hello world", "Second"))),
            "T"
        )!!
        val entries = readZipEntries(bytes)
        val slide = String(entries["ppt/slides/slide1.xml"]!!, Charsets.UTF_8)
        assertTrue("Slide should contain title text", slide.contains("MyTitle"))
        assertTrue("Slide should contain first bullet", slide.contains("Hello world"))
        assertTrue("Slide should contain second bullet", slide.contains("Second"))
    }

    @Test
    fun `buildBytes returns null for empty slides list`() {
        val result = PptxGenerator.buildBytes(emptyList(), "Empty")
        assertEquals(null, result)
    }

    @Test
    fun `slide with image bytes adds image entry and relationship`() {
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(50)
        val bytes = PptxGenerator.buildBytes(
            listOf(
                PptxSlide(
                    title = "With Image",
                    bullets = listOf("Body text"),
                    imageBytes = fakePng,
                    imageMime = "image/png"
                )
            ),
            "T"
        )!!
        val entries = readZipEntries(bytes)
        assertTrue("image entry missing", entries.any { it.key.startsWith("ppt/media/") })
        assertTrue("image relationship missing",
            String(entries["ppt/slides/_rels/slide1.xml.rels"]!!, Charsets.UTF_8).contains("image"))
    }

    @Test
    fun `xml escapes special characters in slide text`() {
        val bytes = PptxGenerator.buildBytes(
            listOf(PptxSlide("A & B <c> \"d\"", bullets = listOf("x<y"))),
            "T"
        )!!
        val entries = readZipEntries(bytes)
        val slide = String(entries["ppt/slides/slide1.xml"]!!, Charsets.UTF_8)
        assertTrue("Ampersand should be escaped", slide.contains("&amp;"))
        assertTrue("Less-than should be escaped", slide.contains("&lt;"))
        assertTrue("Quote should be escaped", slide.contains("&quot;"))
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val data = zis.readBytes()
                map[e.name] = data
                e = zis.nextEntry
            }
        }
        return map
    }
}
