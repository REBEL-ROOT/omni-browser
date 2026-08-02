/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.news.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

import com.rebelroot.omni.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: BrowserViewModel,
    onNavigateHome: () -> Unit
) {
    val categories = listOf("Top Stories", "Technology", "Business", "World", "Sports", "Entertainment", "Science", "Health", "Astrology", "Recipes")
    val listState = rememberLazyListState()

    // Fetch initial news when entering screen if empty
    LaunchedEffect(Unit) {
        if (viewModel.newsArticles.isEmpty() || !categories.contains(viewModel.selectedNewsCategory)) {
            viewModel.fetchNews("Top Stories")
        }
    }

    // Lazy load pagination trigger as user scrolls near bottom
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreNews()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NEWS",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Home Icon
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateHome() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = "Home",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Center: News Center Icon (Selected in primary color)
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = "News Center",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Right: Tabs Icon
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.shouldOpenTabsSheetOnLaunch = true
                                    onNavigateHome()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = viewModel.tabs.count { it.isIncognito == viewModel.isIncognitoMode }.toString(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal scrolling Category Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = viewModel.selectedNewsCategory == category
                    Surface(
                        onClick = { viewModel.fetchNews(category) },
                        shape = RoundedCornerShape(32.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Article List Area
            if (viewModel.isNewsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            } else if (viewModel.newsArticles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No articles available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(viewModel.newsArticles, key = { index, article -> "${article.link}_$index" }) { _, article ->
                        val universalFallbackPhoto = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=800"
                        val fallbackPhoto = remember(article.title, viewModel.selectedNewsCategory) {
                            viewModel.getFallbackCategoryPhoto(article.title, viewModel.selectedNewsCategory)
                        }
                        val initialPhoto = remember(article.imageUrl, fallbackPhoto) {
                            if (article.imageUrl.isNotBlank() && article.imageUrl.startsWith("http")) article.imageUrl else fallbackPhoto
                        }
                        var currentImgUrl by remember(article.imageUrl, initialPhoto) { mutableStateOf(initialPhoto) }
                        var isLoadFailed by remember(article.imageUrl, fallbackPhoto) { mutableStateOf(false) }

                        Card(
                            onClick = {
                                viewModel.loadUrl(article.link)
                                onNavigateHome()
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Article Headline Photo with Multi-Tier Real Photo Fallback
                                if (!isLoadFailed && currentImgUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(currentImgUrl)
                                            .crossfade(true)
                                            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                            .listener(
                                                onError = { _, _ ->
                                                    if (currentImgUrl != fallbackPhoto) {
                                                        currentImgUrl = fallbackPhoto
                                                    } else if (currentImgUrl != universalFallbackPhoto) {
                                                        currentImgUrl = universalFallbackPhoto
                                                    } else {
                                                        isLoadFailed = true
                                                    }
                                                }
                                            )
                                            .build(),
                                        contentDescription = article.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                } else {
                                    // Sleek Editorial News Banner Fallback (No dark/blank placeholders)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                )
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = viewModel.selectedNewsCategory.uppercase(),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Text(
                                                    text = article.source.ifBlank { "Omni News" },
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = article.title,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))


                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    // Article Title
                                    Text(
                                        text = article.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Source Favicon & Source Name & PubDate Row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (article.sourceFaviconUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(article.sourceFaviconUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = article.source,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        Text(
                                            text = article.source,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "•",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = article.pubDate,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    if (viewModel.isMoreNewsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
