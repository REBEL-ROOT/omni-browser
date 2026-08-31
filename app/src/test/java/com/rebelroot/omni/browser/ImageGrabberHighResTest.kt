package com.rebelroot.omni.browser

import org.junit.Assert.*
import org.junit.Test

class ImageGrabberHighResTest {

    @Test
    fun testNhentaiThumbnailPageUrlUpgrade() {
        // nhentai jpg thumbnail
        val thumbJpg = "https://t.nhentai.net/galleries/3748291/1t.jpg"
        val expectedJpg = "https://i.nhentai.net/galleries/3748291/1.jpg"
        assertEquals(expectedJpg, ImageGrabberUtils.transformToHighResImageUrl(thumbJpg))

        // nhentai numbered subdomain with png
        val thumbPng = "https://t3.nhentai.net/galleries/3748291/12t.png"
        val expectedPng = "https://i3.nhentai.net/galleries/3748291/12.png"
        assertEquals(expectedPng, ImageGrabberUtils.transformToHighResImageUrl(thumbPng))

        // nhentai webp
        val thumbWebp = "https://t7.nhentai.net/galleries/3748291/3t.webp"
        val expectedWebp = "https://i7.nhentai.net/galleries/3748291/3.webp"
        assertEquals(expectedWebp, ImageGrabberUtils.transformToHighResImageUrl(thumbWebp))

        // nhentai covers
        val thumbCover = "https://t.nhentai.net/galleries/3748291/thumb.jpg"
        val expectedCover = "https://i.nhentai.net/galleries/3748291/cover.jpg"
        assertEquals(expectedCover, ImageGrabberUtils.transformToHighResImageUrl(thumbCover))

        val thumbCover2 = "https://t2.nhentai.net/galleries/3748291/cover.jpg"
        val expectedCover2 = "https://i2.nhentai.net/galleries/3748291/cover.jpg"
        assertEquals(expectedCover2, ImageGrabberUtils.transformToHighResImageUrl(thumbCover2))
    }

    @Test
    fun testDanbooruUrlUpgrade() {
        val preview = "https://cdn.donmai.us/180x180/ab/cd/abcdef123456.jpg"
        val expected = "https://cdn.donmai.us/original/ab/cd/abcdef123456.jpg"
        assertEquals(expected, ImageGrabberUtils.transformToHighResImageUrl(preview))

        val sample = "https://cdn.donmai.us/sample/ab/cd/sample-abcdef123456.jpg"
        assertEquals(expected, ImageGrabberUtils.transformToHighResImageUrl(sample))
    }

    @Test
    fun testGelbooruAndRule34UrlUpgrade() {
        val gelbooruThumb = "https://gelbooru.com/thumbnails/ab/cd/thumbnail_abcdef.jpg"
        val gelbooruExpected = "https://gelbooru.com/images/ab/cd/abcdef.jpg"
        assertEquals(gelbooruExpected, ImageGrabberUtils.transformToHighResImageUrl(gelbooruThumb))

        val rule34Thumb = "https://rule34.xxx/thumbnails/123/thumbnail_abc.png"
        val rule34Expected = "https://rule34.xxx/images/123/abc.png"
        assertEquals(rule34Expected, ImageGrabberUtils.transformToHighResImageUrl(rule34Thumb))
    }

    @Test
    fun testImgurAndRedditAndTwitterUpgrade() {
        // Imgur suffix removal
        val imgurThumb = "https://i.imgur.com/abcdefghs.jpg"
        val imgurExpected = "https://i.imgur.com/abcdefgh.jpg"
        assertEquals(imgurExpected, ImageGrabberUtils.transformToHighResImageUrl(imgurThumb))

        // Reddit preview -> i.redd.it
        val redditPreview = "https://preview.redd.it/abcdef.png?width=640&crop=smart&auto=webp&s=1234"
        val redditExpected = "https://i.redd.it/abcdef.png"
        assertEquals(redditExpected, ImageGrabberUtils.transformToHighResImageUrl(redditPreview))

        // Twitter orig
        val twitterSmall = "https://pbs.twimg.com/media/abcdef?format=jpg&name=small"
        val twitterExpected = "https://pbs.twimg.com/media/abcdef?format=jpg&name=orig"
        assertEquals(twitterExpected, ImageGrabberUtils.transformToHighResImageUrl(twitterSmall))
    }

    @Test
    fun testWordPressAndShopifyAndGenericCdnCleanups() {
        // WordPress dimensions
        val wpScaled = "https://example.com/wp-content/uploads/2024/01/photo-300x200.jpg"
        val wpExpected = "https://example.com/wp-content/uploads/2024/01/photo.jpg"
        assertEquals(wpExpected, ImageGrabberUtils.transformToHighResImageUrl(wpScaled))

        // Shopify
        val shopify = "https://cdn.shopify.com/s/files/1/photo_medium.jpg"
        val shopifyExpected = "https://cdn.shopify.com/s/files/1/photo.jpg"
        assertEquals(shopifyExpected, ImageGrabberUtils.transformToHighResImageUrl(shopify))

        // Resizing query param stripping
        val cdnQuery = "https://example.com/photo.jpg?resize=300,200"
        assertEquals("https://example.com/photo.jpg", ImageGrabberUtils.transformToHighResImageUrl(cdnQuery))

        val cdnQuery2 = "https://example.com/photo.webp?w=400&fit=crop"
        assertEquals("https://example.com/photo.webp", ImageGrabberUtils.transformToHighResImageUrl(cdnQuery2))
    }

    @Test
    fun testProcessAndUpgradeExtractedImagesDeduplication() {
        val rawUrls = listOf(
            "https://t.nhentai.net/galleries/3748291/1t.jpg",
            "https://i.nhentai.net/galleries/3748291/1.jpg",
            "https://t.nhentai.net/galleries/3748291/2t.jpg",
            "https://example.com/favicon.ico",
            "https://example.com/spinner.gif",
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            "https://t.nhentai.net/galleries/3748291/3t.jpg"
        )

        val result = ImageGrabberUtils.processAndUpgradeExtractedImages(rawUrls)

        // Verifies noise & base64 placeholders were removed,
        // and low-res thumbnails were upgraded and deduplicated
        assertEquals(3, result.size)
        assertEquals("https://i.nhentai.net/galleries/3748291/1.jpg", result[0])
        assertEquals("https://i.nhentai.net/galleries/3748291/2.jpg", result[1])
        assertEquals("https://i.nhentai.net/galleries/3748291/3.jpg", result[2])
    }

    @Test
    fun testResolveReferer() {
        assertEquals("https://nhentai.net/", ImageGrabberUtils.resolveReferer("https://i3.nhentai.net/galleries/3748291/1.jpg"))
        assertEquals("https://danbooru.donmai.us/", ImageGrabberUtils.resolveReferer("https://cdn.donmai.us/original/ab/cd/123.jpg"))
        assertEquals("https://gelbooru.com/", ImageGrabberUtils.resolveReferer("https://gelbooru.com/images/ab/cd/123.jpg"))
        assertEquals("https://www.pixiv.net/", ImageGrabberUtils.resolveReferer("https://i.pximg.net/img-original/img/123.jpg"))
        assertEquals("https://e-hentai.org/", ImageGrabberUtils.resolveReferer("https://123.45.67.89.hath.network:443/h/12345/page.jpg", "https://e-hentai.org/g/4156637/6049dcf087/"))
        assertEquals("https://e-hentai.org/", ImageGrabberUtils.resolveReferer("https://ehgt.org/g/cover.jpg"))
        assertEquals("https://exhentai.org/", ImageGrabberUtils.resolveReferer("https://s.exhentai.org/h/123/page.jpg", "https://exhentai.org/g/123/456/"))
    }

    @Test
    fun testCandidateAlternateUrls() {
        val nhJpg = "https://i2.nhentai.net/galleries/4144908/2.jpg"
        val nhAlts = ImageGrabberUtils.getCandidateAlternateUrls(nhJpg)
        assertTrue(nhAlts.contains("https://i2.nhentai.net/galleries/4144908/2.webp"))
        assertTrue(nhAlts.contains("https://i.nhentai.net/galleries/4144908/2.webp"))
        assertTrue(nhAlts.contains("https://t2.nhentai.net/galleries/4144908/2t.webp"))
        assertTrue(nhAlts.contains("https://t2.nhentai.net/galleries/4144908/2t.jpg"))

        val nhWebp = "https://i.nhentai.net/galleries/3744648/5.webp"
        val nhWebpAlts = ImageGrabberUtils.getCandidateAlternateUrls(nhWebp)
        assertTrue(nhWebpAlts.contains("https://i.nhentai.net/galleries/3744648/5.jpg"))
        assertTrue(nhWebpAlts.contains("https://i.nhentai.net/galleries/3744648/5.png"))

        val nhThumb = "https://t3.nhentai.net/galleries/3744648/1t.jpg"
        val nhThumbAlts = ImageGrabberUtils.getCandidateAlternateUrls(nhThumb)
        assertTrue(nhThumbAlts.contains("https://i3.nhentai.net/galleries/3744648/1.webp"))
        assertTrue(nhThumbAlts.contains("https://i3.nhentai.net/galleries/3744648/1.jpg"))

        val genericJpg = "https://example.com/galleries/chapter1/01.jpg"
        val genericAlts = ImageGrabberUtils.getCandidateAlternateUrls(genericJpg)
        assertTrue(genericAlts.contains("https://example.com/galleries/chapter1/01.webp"))
        assertTrue(genericAlts.contains("https://example.com/galleries/chapter1/01.png"))

        val googleFavicon = "https://www.google.com/s2/favicons?sz=128&domain=1337x.to"
        val faviconAlts = ImageGrabberUtils.getCandidateAlternateUrls(googleFavicon)
        assertTrue(faviconAlts.contains("https://icons.duckduckgo.com/ip3/1337x.to.ico"))
        assertTrue(faviconAlts.contains("https://1337x.to/favicon.ico"))
        assertTrue(faviconAlts.contains("https://www.1337x.to/favicon.ico"))
    }
}
