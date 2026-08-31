package com.rebelroot.omni.browser

import android.net.Uri

object ImageGrabberUtils {

    private val NHENTAI_THUMB_PAGE_REGEX = Regex(
        """^https?://t(\d*)\.nhentai\.net/galleries/(\d+)/(\d+)t\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val NHENTAI_COVER_REGEX = Regex(
        """^https?://t(\d*)\.nhentai\.net/galleries/(\d+)/(?:thumb|cover)\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DONMAI_CDN_REGEX = Regex(
        """^(https?://cdn\.donmai\.us)/(?:180x180|360x360|720x720|sample|preview)/(.+)\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val BOORU_THUMB_REGEX = Regex(
        """^(https?://(?:[^/]+\.)?(?:gelbooru\.com|rule34\.xxx|safebooru\.org|realbooru\.com|booru\.[^/]+))/thumbnails/(.+)/thumbnail_([^.]+)\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val BOORU_SAMPLE_REGEX = Regex(
        """^(https?://(?:[^/]+\.)?(?:gelbooru\.com|rule34\.xxx|safebooru\.org|realbooru\.com|booru\.[^/]+))/samples/(.+)/sample_([^.]+)\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val BOORU_PREVIEW_REGEX = Regex(
        """^(https?://(?:[^/]+\.)?(?:gelbooru\.com|rule34\.xxx|safebooru\.org|realbooru\.com|booru\.[^/]+))/preview/(.+)/([^.]+)\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val ZEROCHAN_HOST_REGEX = Regex(
        """^https?://s[12]\.zerochan\.net/""",
        RegexOption.IGNORE_CASE
    )
    private val ZEROCHAN_PREVIEW_REGEX = Regex(
        """\.(?:preview|1024|240|600|720)\.([a-zA-Z0-9]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val PIXIV_MASTER_PATH_REGEX = Regex(
        """/c/\d+x\d+_\d+/img-master/""",
        RegexOption.IGNORE_CASE
    )
    private val PIXIV_MASTER_FILE_REGEX = Regex(
        """_master1200\.([a-zA-Z0-9]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val IMGUR_THUMB_REGEX = Regex(
        """^https?://i\.imgur\.com/([a-zA-Z0-9]{5,8})[stmlh]\.([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )
    private val REDDIT_PREVIEW_REGEX = Regex(
        """^https?://preview\.redd\.it/([^?]+).*""",
        RegexOption.IGNORE_CASE
    )
    private val TWITTER_IMAGE_REGEX = Regex(
        """^(https?://pbs\.twimg\.com/media/[^?]+)\?format=([a-zA-Z0-9]+)&name=\w+""",
        RegexOption.IGNORE_CASE
    )
    private val WORDPRESS_DIMENSION_REGEX = Regex(
        """-\d{2,4}x\d{2,4}\.([a-zA-Z0-9]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val SHOPIFY_SUFFIX_REGEX = Regex(
        """_([0-9]+x[0-9]*|small|medium|large|grande|compact)\.([a-zA-Z0-9]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val GENERIC_THUMB_SUFFIX_REGEX = Regex(
        """[._-](?:thumb|preview|thumbnail|small|mini)\.([a-zA-Z0-9]+)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Transforms a thumbnail/preview image URL into its highest-resolution master counterpart.
     */
    fun transformToHighResImageUrl(url: String): String {
        var u = url.trim()
        if (u.isBlank() || (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true))) {
            return u
        }

        try {
            // 1. nhentai pages: t.nhentai.net/galleries/3748291/1t.jpg -> i.nhentai.net/galleries/3748291/1.jpg
            if (NHENTAI_THUMB_PAGE_REGEX.containsMatchIn(u)) {
                return NHENTAI_THUMB_PAGE_REGEX.replace(u) { matchResult ->
                    val (sub, mediaId, pageNum, ext) = matchResult.destructured
                    "https://i$sub.nhentai.net/galleries/$mediaId/$pageNum.$ext"
                }
            }

            // 1b. nhentai cover: t.nhentai.net/galleries/3748291/thumb.jpg -> i.nhentai.net/galleries/3748291/cover.jpg
            if (NHENTAI_COVER_REGEX.containsMatchIn(u)) {
                return NHENTAI_COVER_REGEX.replace(u) { matchResult ->
                    val (sub, mediaId, ext) = matchResult.destructured
                    "https://i$sub.nhentai.net/galleries/$mediaId/cover.$ext"
                }
            }

            // 2. Danbooru CDN: cdn.donmai.us/180x180/... -> cdn.donmai.us/original/...
            if (DONMAI_CDN_REGEX.containsMatchIn(u)) {
                u = DONMAI_CDN_REGEX.replace(u) { match ->
                    val (host, path, ext) = match.destructured
                    val cleanPath = path.replace("/sample-", "/").replace("sample-", "")
                    "$host/original/$cleanPath.$ext"
                }
            }

            // 3. Gelbooru / Rule34 / Safebooru / Realbooru
            if (BOORU_THUMB_REGEX.containsMatchIn(u)) {
                u = BOORU_THUMB_REGEX.replace(u, "$1/images/$2/$3.$4")
            }
            if (BOORU_SAMPLE_REGEX.containsMatchIn(u)) {
                u = BOORU_SAMPLE_REGEX.replace(u, "$1/images/$2/$3.$4")
            }
            if (BOORU_PREVIEW_REGEX.containsMatchIn(u)) {
                u = BOORU_PREVIEW_REGEX.replace(u, "$1/images/$2/$3.$4")
            }

            // 4. Zerochan: s1.zerochan.net/xxx.240.123.jpg -> static.zerochan.net/xxx.full.123.jpg
            if (ZEROCHAN_HOST_REGEX.containsMatchIn(u)) {
                u = ZEROCHAN_HOST_REGEX.replace(u, "https://static.zerochan.net/")
            }
            if (u.contains("zerochan.net", ignoreCase = true) && ZEROCHAN_PREVIEW_REGEX.containsMatchIn(u)) {
                u = ZEROCHAN_PREVIEW_REGEX.replace(u, ".full.$1")
            }

            // 5. Pixiv: c/540x540_70/img-master/..._master1200.jpg -> img-original/...jpg
            if (u.contains("pximg.net", ignoreCase = true)) {
                u = PIXIV_MASTER_PATH_REGEX.replace(u, "/img-original/")
                u = PIXIV_MASTER_FILE_REGEX.replace(u, ".$1")
            }

            // 6. MangaDex: data-saver -> data
            if (u.contains("mangadex.org", ignoreCase = true) && u.contains("/data-saver/")) {
                u = u.replace("/data-saver/", "/data/")
            }

            // 7. Imgur: i.imgur.com/abcdefghs.jpg -> i.imgur.com/abcdefgh.jpg
            if (IMGUR_THUMB_REGEX.containsMatchIn(u)) {
                u = IMGUR_THUMB_REGEX.replace(u, "https://i.imgur.com/$1.$2")
            }

            // 8. Reddit Preview: preview.redd.it/xxx?params -> i.redd.it/xxx
            if (REDDIT_PREVIEW_REGEX.containsMatchIn(u)) {
                u = REDDIT_PREVIEW_REGEX.replace(u, "https://i.redd.it/$1")
            }

            // 9. Twitter / X: format=jpg&name=small -> name=orig
            if (TWITTER_IMAGE_REGEX.containsMatchIn(u)) {
                u = TWITTER_IMAGE_REGEX.replace(u, "$1?format=$2&name=orig")
            }

            // 10. WordPress scaled dimensions (e.g. photo-300x200.jpg -> photo.jpg)
            if (WORDPRESS_DIMENSION_REGEX.containsMatchIn(u)) {
                u = WORDPRESS_DIMENSION_REGEX.replace(u, ".$1")
            }

            // 11. Generic CDN resizing query parameters (e.g. ?w=300&h=200, ?resize=300,200, ?fit=crop)
            if (u.contains("?") && (u.contains(".jpg?", ignoreCase = true) || u.contains(".jpeg?", ignoreCase = true) ||
                        u.contains(".png?", ignoreCase = true) || u.contains(".webp?", ignoreCase = true) ||
                        u.contains(".avif?", ignoreCase = true))) {
                val queryLower = u.substringAfter("?").lowercase()
                if (queryLower.contains("resize=") || queryLower.contains("fit=") ||
                    queryLower.contains("width=") || queryLower.contains("w=") ||
                    queryLower.contains("quality=") || queryLower.contains("q=")) {
                    u = u.substringBefore("?")
                }
            }

            // 12. Shopify thumbnails (e.g. photo_medium.jpg -> photo.jpg)
            if (SHOPIFY_SUFFIX_REGEX.containsMatchIn(u)) {
                u = SHOPIFY_SUFFIX_REGEX.replace(u, ".$2")
            }

            // 13. Generic thumbnail suffixes (e.g. photo_thumb.jpg -> photo.jpg)
            if (GENERIC_THUMB_SUFFIX_REGEX.containsMatchIn(u)) {
                u = GENERIC_THUMB_SUFFIX_REGEX.replace(u, ".$1")
            }
        } catch (_: Exception) {}

        return u
    }

    /**
     * Processes raw URLs from DOM/scripts, upgrades thumbnails to high resolution,
     * strips unwanted noise/icons, and deduplicates preserving page order.
     */
    fun processAndUpgradeExtractedImages(rawUrls: List<String>): List<String> {
        if (rawUrls.isEmpty()) return emptyList()
        val upgradedUrls = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (raw in rawUrls) {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true))) {
                continue
            }

            val lower = trimmed.lowercase()
            if (lower.contains("favicon") || lower.contains("pixel.gif") || lower.contains("spinner.gif") ||
                lower.contains("loading.gif") || lower.contains("blank.png") || lower.contains("placeholder") ||
                lower.contains("/r.png") || lower.contains("/star.png") || lower.contains("logo") ||
                lower.contains("avatar") || lower.contains("badge") || lower.contains("emoji") ||
                lower.contains("1x1") || lower.startsWith("data:image")) {
                continue
            }

            val upgraded = transformToHighResImageUrl(trimmed)
            if (seen.add(upgraded)) {
                upgradedUrls.add(upgraded)
            }
        }

        return upgradedUrls
    }

    /**
     * Derives an optimal Referer header for the given image URL or host.
     */
    fun resolveReferer(imageUrl: String, currentTabUrl: String = ""): String {
        val tabLower = currentTabUrl.lowercase()
        val imgLower = imageUrl.lowercase()

        if (tabLower.contains("exhentai.org") || imgLower.contains("exhentai.org")) {
            return "https://exhentai.org/"
        }
        if (tabLower.contains("e-hentai.org") || imgLower.contains("e-hentai.org") ||
            imgLower.contains("hath.network") || imgLower.contains("ehgt.org") ||
            imgLower.contains("hentaiverse.net")) {
            return "https://e-hentai.org/"
        }
        if (tabLower.contains("nhentai.net") || imgLower.contains("nhentai.net")) {
            return "https://nhentai.net/"
        }
        if (tabLower.contains("donmai.us") || imgLower.contains("donmai.us") ||
            tabLower.contains("danbooru") || imgLower.contains("danbooru")) {
            return "https://danbooru.donmai.us/"
        }
        if (tabLower.contains("gelbooru.com") || imgLower.contains("gelbooru.com")) {
            return "https://gelbooru.com/"
        }
        if (tabLower.contains("rule34.xxx") || imgLower.contains("rule34.xxx")) {
            return "https://rule34.xxx/"
        }
        if (tabLower.contains("pximg.net") || imgLower.contains("pximg.net") ||
            tabLower.contains("pixiv.net") || imgLower.contains("pixiv.net")) {
            return "https://www.pixiv.net/"
        }

        return try {
            if (currentTabUrl.isNotEmpty()) {
                val uri = Uri.parse(currentTabUrl)
                "${uri.scheme}://${uri.host}/"
            } else {
                val uri = Uri.parse(imageUrl)
                "${uri.scheme}://${uri.host}/"
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Generates fast, focused fallback candidate URLs with alternate extensions (.webp, .jpg, .png)
     * and thumbnail fallbacks when an image request fails.
     */
    fun getCandidateAlternateUrls(imageUrl: String): List<String> {
        val trimmed = imageUrl.trim()
        if (trimmed.isBlank()) return emptyList()
        val candidates = linkedSetOf<String>()

        // 1. nhentai master pages: https://i{sub}.nhentai.net/galleries/{mediaId}/{page}.{ext}
        val nhMasterMatch = Regex("""^https?://i(\d*)\.nhentai\.net/galleries/(\d+)/(\d+)\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (nhMasterMatch != null) {
            val sub = nhMasterMatch.groupValues[1]
            val mediaId = nhMasterMatch.groupValues[2]
            val page = nhMasterMatch.groupValues[3]
            val currentExt = nhMasterMatch.groupValues[4].lowercase()

            val primaryExts = if (currentExt == "webp") listOf("jpg", "png") else listOf("webp", "png")
            for (ext in primaryExts) {
                candidates.add("https://i$sub.nhentai.net/galleries/$mediaId/$page.$ext")
                if (sub.isNotEmpty()) {
                    candidates.add("https://i.nhentai.net/galleries/$mediaId/$page.$ext")
                } else {
                    candidates.add("https://i3.nhentai.net/galleries/$mediaId/$page.$ext")
                }
            }
            // Safe thumbnail fallback
            val hostSub = if (sub.isEmpty()) "3" else sub
            candidates.add("https://t$hostSub.nhentai.net/galleries/$mediaId/${page}t.webp")
            candidates.add("https://t$hostSub.nhentai.net/galleries/$mediaId/${page}t.jpg")
            candidates.add("https://t3.nhentai.net/galleries/$mediaId/${page}t.webp")
            candidates.add("https://t3.nhentai.net/galleries/$mediaId/${page}t.jpg")
            candidates.remove(trimmed)
            return candidates.toList()
        }

        // 2. nhentai thumbnail pages: https://t{sub}.nhentai.net/galleries/{mediaId}/{page}t.{ext}
        val nhThumbMatch = Regex("""^https?://t(\d*)\.nhentai\.net/galleries/(\d+)/(\d+)t\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (nhThumbMatch != null) {
            val sub = nhThumbMatch.groupValues[1]
            val mediaId = nhThumbMatch.groupValues[2]
            val page = nhThumbMatch.groupValues[3]

            val hostSub = if (sub.isEmpty()) "3" else sub
            candidates.add("https://i$hostSub.nhentai.net/galleries/$mediaId/$page.webp")
            candidates.add("https://i$hostSub.nhentai.net/galleries/$mediaId/$page.jpg")
            candidates.add("https://i.nhentai.net/galleries/$mediaId/$page.webp")
            candidates.add("https://i.nhentai.net/galleries/$mediaId/$page.jpg")
            candidates.remove(trimmed)
            return candidates.toList()
        }

        // 3. Generic gallery images with standard image extensions
        val genericMatch = Regex("""^(https?://.+/([^/?#]+))\.(jpg|jpeg|png|webp|avif)((\?.*)?)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (genericMatch != null) {
            val base = genericMatch.groupValues[1]
            val currentExt = genericMatch.groupValues[3].lowercase()
            val query = genericMatch.groupValues[4]
            val allExts = if (currentExt == "jpg" || currentExt == "jpeg") {
                listOf("webp", "png")
            } else if (currentExt == "webp") {
                listOf("jpg", "png")
            } else {
                listOf("webp", "jpg")
            }
            for (ext in allExts) {
                candidates.add("$base.$ext$query")
            }
            candidates.remove(trimmed)
            return candidates.toList()
        }

        // 4. Google S2 Favicon service fallback (e.g. for domains blocked or missing on Google S2)
        val faviconMatch = Regex("""^https?://(?:www\.)?google\.com/s2/favicons\?.*[?&]domain=([^&]+)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (faviconMatch != null) {
            val rawDomain = faviconMatch.groupValues[1].removePrefix("http://").removePrefix("https://").trimEnd('/')
            if (rawDomain.isNotEmpty()) {
                candidates.add("https://icons.duckduckgo.com/ip3/$rawDomain.ico")
                candidates.add("https://$rawDomain/favicon.ico")
                if (!rawDomain.startsWith("www.")) {
                    candidates.add("https://www.$rawDomain/favicon.ico")
                }
                candidates.add("https://$rawDomain/apple-touch-icon.png")
            }
            candidates.remove(trimmed)
            return candidates.toList()
        }

        return emptyList()
    }

    /**
     * Derives a guaranteed thumbnail URL for a given master/gallery image.
     */
    fun getFallbackThumbnailUrl(imageUrl: String): String? {
        val trimmed = imageUrl.trim()
        if (trimmed.isBlank()) return null

        val nhMasterMatch = Regex("""^https?://i(\d*)\.nhentai\.net/galleries/(\d+)/(\d+)\.([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (nhMasterMatch != null) {
            val sub = nhMasterMatch.groupValues[1]
            val mediaId = nhMasterMatch.groupValues[2]
            val page = nhMasterMatch.groupValues[3]
            val hostSub = if (sub.isEmpty()) "3" else sub
            return "https://t$hostSub.nhentai.net/galleries/$mediaId/${page}t.jpg"
        }

        return null
    }
}


