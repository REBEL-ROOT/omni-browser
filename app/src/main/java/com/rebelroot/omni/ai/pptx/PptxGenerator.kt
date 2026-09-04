/*
 * Omni Browser - PPTX Generator
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Hand-built OpenXML (.pptx) writer — no external dependencies.
 * PPTX is a ZIP archive containing XML files. We build the minimum
 * required parts for a valid presentation:
 *   - [Content_Types].xml
 *   - _rels/.rels
 *   - ppt/presentation.xml
 *   - ppt/_rels/presentation.xml.rels
 *   - ppt/slides/slide{N}.xml + rels
 *   - ppt/slideLayouts/slideLayout1.xml + rels (blank)
 *   - ppt/theme/theme1.xml (minimal)
 *   - docProps/app.xml + core.xml
 */
package com.rebelroot.omni.ai.pptx

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A single slide's content. */
data class PptxSlide(
    val title: String,
    val bullets: List<String> = emptyList(),
    val body: String = "",
    val imageBytes: ByteArray? = null,
    val imageMime: String = "image/png"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PptxSlide) return false
        return title == other.title && bullets == other.bullets && body == other.body &&
            (imageBytes?.contentEquals(other.imageBytes) ?: (other.imageBytes == null)) &&
            imageMime == other.imageMime
    }
    override fun hashCode(): Int =
        title.hashCode() * 31 + bullets.hashCode() + body.hashCode() + (imageBytes?.contentHashCode() ?: 0)
}

/** Slide dimensions (16:9 widescreen, in EMU — 914400 per inch). */
private const val SLIDE_W_EMU = 12192000   // 13.333"
private const val SLIDE_H_EMU = 6858000    // 7.5"

object PptxGenerator {

    private const val TAG = "PptxGenerator"

    /**
     * Build a .pptx file from the provided slides and write it to the cache
     * directory. Returns the saved file, or null on failure.
     */
    fun writeToCache(context: Context, slides: List<PptxSlide>, title: String, fileName: String): File? {
        if (slides.isEmpty()) {
            Log.w(TAG, "writeToCache: no slides provided")
            return null
        }
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                .ifBlank { "presentation_${System.currentTimeMillis()}" }
            val outFile = File(context.cacheDir, "$safeName.pptx")
            FileOutputStream(outFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    writePresentation(zos, slides, title)
                }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "writeToCache failed", e)
            null
        }
    }

    /**
     * Build a .pptx file and return its bytes.
     */
    fun buildBytes(slides: List<PptxSlide>, title: String): ByteArray? {
        if (slides.isEmpty()) return null
        return try {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                writePresentation(zos, slides, title)
            }
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "buildBytes failed", e)
            null
        }
    }

    private fun writePresentation(zos: ZipOutputStream, slides: List<PptxSlide>, title: String) {
        val slideCount = slides.size

        // 1. [Content_Types].xml
        val contentTypes = buildContentTypes(slideCount)
        writeEntry(zos, "[Content_Types].xml", contentTypes.toByteArray(Charsets.UTF_8))

        // 2. _rels/.rels
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>""".trimIndent()
        writeEntry(zos, "_rels/.rels", rootRels.toByteArray(Charsets.UTF_8))

        // 3. docProps/core.xml
        val now = java.util.Date().toInstant().toString()
        val coreXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                   xmlns:dc="http://purl.org/dc/elements/1.1/"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${xmlEscape(title)}</dc:title>
  <dc:creator>Omni Browser</dc:creator>
  <cp:lastModifiedBy>Omni Browser</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>""".trimIndent()
        writeEntry(zos, "docProps/core.xml", coreXml.toByteArray(Charsets.UTF_8))

        // 4. docProps/app.xml
        val appXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
            xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Omni Browser</Application>
  <AppVersion>1.0.0</AppVersion>
  <Slides>$slideCount</Slides>
</Properties>""".trimIndent()
        writeEntry(zos, "docProps/app.xml", appXml.toByteArray(Charsets.UTF_8))

        // 5. ppt/_rels/presentation.xml.rels
        val presRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
""")
        for (i in 1..slideCount) {
            presRels.append("  <Relationship Id=\"rId${i + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$i.xml\"/>\n")
        }
        presRels.append("</Relationships>")
        writeEntry(zos, "ppt/_rels/presentation.xml.rels", presRels.toString().toByteArray(Charsets.UTF_8))

        // 6. ppt/presentation.xml
        val slideIdList = (1..slideCount).joinToString("") { "<p:sldId id=\"$it\" r:id=\"rId${it + 2}\"/>" }
        val presentationXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
  <p:sldIdLst>$slideIdList</p:sldIdLst>
  <p:sldSz cx="$SLIDE_W_EMU" cy="$SLIDE_H_EMU" type="screen16x9"/>
  <p:notesSz cx="6858000" cy="9144000"/>
  <p:defaultTextStyle>
    <a:lvl1pPr><a:defRPr sz="1800"/><a:buNone/></a:lvl1pPr>
  </p:defaultTextStyle>
</p:presentation>""".trimIndent()
        writeEntry(zos, "ppt/presentation.xml", presentationXml.toByteArray(Charsets.UTF_8))

        // 7. ppt/theme/theme1.xml (minimal but valid)
        val themeXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Omni Theme">
  <a:themeElements>
    <a:clrScheme name="Omni">
      <a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1>
      <a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="1F2937"/></a:dk2>
      <a:lt2><a:srgbClr val="F3F4F6"/></a:lt2>
      <a:accent1><a:srgbClr val="3B82F6"/></a:accent1>
      <a:accent2><a:srgbClr val="10B981"/></a:accent2>
      <a:accent3><a:srgbClr val="F59E0B"/></a:accent3>
      <a:accent4><a:srgbClr val="EF4444"/></a:accent4>
      <a:accent5><a:srgbClr val="8B5CF6"/></a:accent5>
      <a:accent6><a:srgbClr val="EC4899"/></a:accent6>
      <a:hlink><a:srgbClr val="2563EB"/></a:hlink>
      <a:folHlink><a:srgbClr val="7C3AED"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="Omni">
      <a:majorFont>
        <a:latin typeface="Inter"/>
        <a:ea typeface=""/>
        <a:cs typeface=""/>
      </a:majorFont>
      <a:minorFont>
        <a:latin typeface="Inter"/>
        <a:ea typeface=""/>
        <a:cs typeface=""/>
      </a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="Omni">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="6350" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="12700" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="19050" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>""".trimIndent()
        writeEntry(zos, "ppt/theme/theme1.xml", themeXml.toByteArray(Charsets.UTF_8))

        // 8. ppt/slideMasters/slideMaster1.xml + rels
        val slideMasterXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>""".trimIndent()
        writeEntry(zos, "ppt/slideMasters/slideMaster1.xml", slideMasterXml.toByteArray(Charsets.UTF_8))

        val slideMasterRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>""".trimIndent()
        writeEntry(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels.toByteArray(Charsets.UTF_8))

        // 9. ppt/slideLayouts/slideLayout1.xml + rels (blank layout)
        val slideLayoutXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" type="blank">
  <p:cSld name="Blank">
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
</p:sldLayout>""".trimIndent()
        writeEntry(zos, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml.toByteArray(Charsets.UTF_8))

        val slideLayoutRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>""".trimIndent()
        writeEntry(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels.toByteArray(Charsets.UTF_8))

        // 10. Each slide
        for ((index, slide) in slides.withIndex()) {
            val slideNum = index + 1
            val (slideXml, hasImage) = buildSlideXml(slide, slideNum)
            writeEntry(zos, "ppt/slides/slide$slideNum.xml", slideXml.toByteArray(Charsets.UTF_8))

            val slideRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
""")
            if (hasImage) {
                slideRels.append("  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image$slideNum.${extensionFor(slide.imageMime)}\"/>\n")
            }
            slideRels.append("</Relationships>")
            writeEntry(zos, "ppt/slides/_rels/slide$slideNum.xml.rels", slideRels.toString().toByteArray(Charsets.UTF_8))

            if (hasImage && slide.imageBytes != null) {
                writeEntry(zos, "ppt/media/image$slideNum.${extensionFor(slide.imageMime)}", slide.imageBytes)
            }
        }
    }

    private fun buildSlideXml(slide: PptxSlide, slideNum: Int): Pair<String, Boolean> {
        val hasImage = slide.imageBytes != null
        val hasBullets = slide.bullets.isNotEmpty()
        val hasBody = slide.body.isNotBlank()
        val titleText = slide.title.ifBlank { "Slide $slideNum" }

        val shapes = StringBuilder()
        var shapeId = 1000

        // Title shape
        shapes.append(buildTextShape(
            shapeId++,
            name = "Title $slideNum",
            x = 609600, y = 365760, w = SLIDE_W_EMU - 2 * 609600, h = 1100000,
            text = titleText,
            fontSizePt = 36,
            bold = true,
            color = "1F2937",
            align = "ctr"
        ))

        // Body content area below the title
        val bodyTop = 1600200
        val bodyHeight = SLIDE_H_EMU - bodyTop - 365760

        when {
            hasBullets -> {
                val bulletText = slide.bullets.joinToString("") { b ->
                    "<a:p><a:r><a:rPr lang=\"en-US\" sz=\"2000\"/><a:t>${xmlEscape(b)}</a:t></a:r></a:p>"
                }
                shapes.append("""
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="${shapeId++}" name="Bullets $slideNum"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
                      <p:spPr>
                        <a:xfrm><a:off x="609600" y="$bodyTop"/><a:ext cx="${SLIDE_W_EMU - 2 * 609600}" cy="$bodyHeight"/></a:xfrm>
                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                        <a:noFill/>
                      </p:spPr>
                      <p:txBody>
                        <a:bodyPr wrap="square" rtlCol="0" anchor="t"/>
                        <a:lstStyle/>
                        <a:p>
                          <a:pPr marL="342900" indent="-342900"><a:buFont typeface="Arial" panose="020B0604020202020204" pitchFamily="34" charset="0"/><a:buChar char="•"/></a:pPr>
                          <a:r><a:rPr lang="en-US" sz="2000" b="0"/><a:t>${xmlEscape(slide.bullets.first())}</a:t></a:r>
                        </a:p>
                        ${slide.bullets.drop(1).joinToString("") { b -> "<a:p><a:pPr marL=\"342900\" indent=\"-342900\"><a:buFont typeface=\"Arial\" panose=\"020B0604020202020204\" pitchFamily=\"34\" charset=\"0\"/><a:buChar char=\"•\"/></a:pPr><a:r><a:rPr lang=\"en-US\" sz=\"2000\"/><a:t>${xmlEscape(b)}</a:t></a:r></a:p>" }}
                      </p:txBody>
                    </p:sp>
                """.trimIndent())
            }
            hasBody -> {
                val paragraphs = slide.body.split("\n").joinToString("") { p ->
                    "<a:p><a:r><a:rPr lang=\"en-US\" sz=\"2000\"/><a:t>${xmlEscape(p)}</a:t></a:r></a:p>"
                }
                shapes.append("""
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="${shapeId++}" name="Body $slideNum"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
                      <p:spPr>
                        <a:xfrm><a:off x="609600" y="$bodyTop"/><a:ext cx="${SLIDE_W_EMU - 2 * 609600}" cy="$bodyHeight"/></a:xfrm>
                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                        <a:noFill/>
                      </p:spPr>
                      <p:txBody>
                        <a:bodyPr wrap="square" rtlCol="0" anchor="t"/>
                        <a:lstStyle/>
                        $paragraphs
                      </p:txBody>
                    </p:sp>
                """.trimIndent())
            }
        }

        // Image (if present)
        if (hasImage) {
            val imgTop = if (hasBullets || hasBody) SLIDE_H_EMU / 2 else 1600200
            val imgH = SLIDE_H_EMU - imgTop - 365760
            val imgW = (imgH * 16 / 9).coerceAtMost(SLIDE_W_EMU - 2 * 609600)
            val imgX = (SLIDE_W_EMU - imgW) / 2
            shapes.append("""
                <p:pic>
                  <p:nvPicPr><p:cNvPr id="${shapeId++}" name="Image $slideNum"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                  <p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
                  <p:spPr>
                    <a:xfrm><a:off x="$imgX" y="$imgTop"/><a:ext cx="$imgW" cy="$imgH"/></a:xfrm>
                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                  </p:spPr>
                </p:pic>
            """.trimIndent())
        }

        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:cSld><p:spTree>$shapes</p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>""".trimIndent()

        return xml to hasImage
    }

    private fun buildTextShape(
        id: Int,
        name: String,
        x: Int, y: Int, w: Int, h: Int,
        text: String,
        fontSizePt: Int,
        bold: Boolean = false,
        color: String? = null,
        align: String = "l"
    ): String {
        val alignAttr = if (align == "ctr") " algn=\"ctr\"" else ""
        val colorEl = if (color != null) "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill>" else ""
        val boldEl = if (bold) " b=\"1\"" else ""
        val bodyPrAnchor = if (align == "ctr") "<a:bodyPr wrap=\"square\" rtlCol=\"0\" anchor=\"ctr\"/>" else "<a:bodyPr wrap=\"square\" rtlCol=\"0\" anchor=\"t\"/>"
        val paragraphPr = "<a:pPr$alignAttr/>"
        val textEl = text.split("\n").joinToString("") { t ->
            "<a:p>$paragraphPr<a:r><a:rPr lang=\"en-US\" sz=\"$fontSizePt\"$boldEl/>$colorEl<a:t>${xmlEscape(t)}</a:t></a:r></a:p>"
        }
        return """
            <p:sp>
              <p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
              <p:spPr>
                <a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:noFill/>
              </p:spPr>
              <p:txBody>
                $bodyPrAnchor
                <a:lstStyle/>
                $textEl
              </p:txBody>
            </p:sp>
        """.trimIndent()
    }

    private fun buildContentTypes(slideCount: Int): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="gif" ContentType="image/gif"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
""")
        for (i in 1..slideCount) {
            sb.append("  <Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun extensionFor(mime: String): String = when {
        mime.contains("png") -> "png"
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        else -> "jpg"
    }

    private fun xmlEscape(s: String): String {
        if (s.isEmpty()) return ""
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        // Use STORED for parts that are small/known — not necessary, DEFLATED works
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }
}
