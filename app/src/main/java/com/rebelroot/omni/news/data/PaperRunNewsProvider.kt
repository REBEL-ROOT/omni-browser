package com.rebelroot.omni.news.data

import android.util.Log
import com.rebelroot.omni.browser.NewsArticle
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object PaperRunNewsProvider {
    private const val TAG = "PaperRunNewsProvider"
    private const val BASE_URL = "https://paperrun.news"

    fun fetchArticles(category: String = "Top Stories"): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            val targetUrl = when (category.lowercase()) {
                "tech", "technology" -> "$BASE_URL/c/technology"
                "science" -> "$BASE_URL/c/science"
                "space" -> "$BASE_URL/c/space"
                "environment" -> "$BASE_URL/c/environment"
                "nature" -> "$BASE_URL/c/nature"
                "health" -> "$BASE_URL/c/health"
                "history" -> "$BASE_URL/c/history"
                else -> BASE_URL
            }

            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val matcher = Pattern.compile("<script data-page=\"app\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL).matcher(html)
                if (matcher.find()) {
                    val jsonStr = matcher.group(1)
                    if (!jsonStr.isNullOrEmpty()) {
                        val rootObj = JSONObject(jsonStr)
                        val props = rootObj.optJSONObject("props")
                        val articlesObj = props?.optJSONObject("articles")
                        val dataArray = articlesObj?.optJSONArray("data")

                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i) ?: continue
                                val title = item.optString("title", "").trim()
                                val url = item.optString("url", "").trim()
                                if (title.isBlank() || url.isBlank()) continue

                                val summary = item.optString("summary", "").trim()
                                val imageUrl = item.optString("image_url", "").trim()
                                val publishedAgo = item.optString("published_ago", "").trim()

                                val sourceObj = item.optJSONObject("source")
                                val sourceName = sourceObj?.optString("name", "")?.ifBlank { "PaperRun" } ?: "PaperRun"
                                val sourceHome = sourceObj?.optString("homepage_url", "") ?: ""

                                val categoryObj = item.optJSONObject("category")
                                val categoryName = categoryObj?.optString("name", "")?.ifBlank { category } ?: category

                                val domain = extractDomain(sourceHome, sourceName)
                                val faviconUrl = if (domain.isNotBlank()) "https://www.google.com/s2/favicons?sz=64&domain=$domain" else ""

                                articles.add(
                                    NewsArticle(
                                        title = title,
                                        link = url,
                                        source = sourceName,
                                        pubDate = publishedAgo.ifEmpty { "Recently" },
                                        imageUrl = imageUrl,
                                        sourceFaviconUrl = faviconUrl,
                                        category = categoryName,
                                        summary = summary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching PaperRun news: ${e.message}", e)
        }
        return articles
    }

    private fun extractDomain(homepageUrl: String, sourceName: String): String {
        return try {
            if (homepageUrl.isNotBlank()) {
                val uri = java.net.URI(homepageUrl)
                uri.host?.removePrefix("www.") ?: ""
            } else {
                sourceName.lowercase().replace(" ", "").replace("[^a-z0-9]".toRegex(), "") + ".com"
            }
        } catch (_: Exception) {
            ""
        }
    }
}
