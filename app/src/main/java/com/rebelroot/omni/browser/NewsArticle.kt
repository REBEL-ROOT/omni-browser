package com.rebelroot.omni.browser

data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val pubDate: String,
    val imageUrl: String,
    val sourceFaviconUrl: String = "",
    val category: String = "",
    val summary: String = ""
)
