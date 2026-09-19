package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.ui.components.VerticalMediaFeedCard
import com.example.ui.components.WavePullToRefreshIndicator
import com.example.ui.viewmodel.StreamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feeds Screen: Dedicated Discover Feed screen showcasing a continuous stream
 * of movies, series, episodes, and latest releases with inline previews,
 * category filtering, swipe pull-down to refresh & shuffle, and direct media playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverFeedsScreen(
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onBackPress: () -> Unit,
    isHeaderVisible: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val discoverFeedItems by viewModel.discoverFeedItems.collectAsState()
    val isRefreshingFromVm by viewModel.isDiscoverFeedRefreshing.collectAsState()

    var activePreviewFeedItemId by remember { mutableStateOf<String?>(null) }
    var selectedItemForDetail by remember { mutableStateOf<MediaItem?>(null) }
    var selectedFilterCategory by remember { mutableStateOf("All") }

    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    // Ensure feeds are loaded and shuffled when entering the screen
    LaunchedEffect(Unit) {
        if (discoverFeedItems.isEmpty()) {
            viewModel.refreshDiscoverFeed(forceReloadFromNetwork = false)
        }
    }

    // Filter items based on active chip
    val displayedItems = remember(discoverFeedItems, selectedFilterCategory) {
        if (selectedFilterCategory == "All") {
            discoverFeedItems
        } else {
            discoverFeedItems.filter { item ->
                when (selectedFilterCategory) {
                    "Latest" -> item.category.contains("Latest", ignoreCase = true) || (item.year.toIntOrNull() ?: 0) >= 2025
                    "Movies" -> item.type == "movie"
                    "Series" -> item.type == "series" || item.type == "tv"
                    "Anime" -> item.category.contains("Anime", ignoreCase = true)
                    "Bangla" -> item.category.contains("Bangla", ignoreCase = true) || item.category.contains("Natok", ignoreCase = true)
                    "K-Drama" -> item.category.contains("K-Drama", ignoreCase = true) || item.category.contains("Korean", ignoreCase = true)
                    "Action" -> item.category.contains("Action", ignoreCase = true)
                    else -> item.category.contains(selectedFilterCategory, ignoreCase = true)
                }
            }
        }
    }

    val filterChips = remember {
        listOf("All", "Latest", "Movies", "Series", "Anime", "Bangla", "K-Drama", "Action")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0D0D11) else Color(0xFFF7F7FA))
            .testTag("discover_feeds_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- CATEGORY FILTER CHIPS ONLY (Top header line removed as requested) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) Color(0xFF141419) else Color.White,
                shadowElevation = 2.dp
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(filterChips) { chip ->
                        val isSelected = selectedFilterCategory == chip
                        Surface(
                            onClick = {
                                selectedFilterCategory = chip
                                coroutineScope.launch {
                                    listState.scrollToItem(0)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFFFF6B00) else (if (isDark) Color(0xFF222228) else Color(0xFFEBEBF0)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chip,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else (if (isDark) Color.LightGray else Color(0xFF3A3A3C))
                                )
                            }
                        }
                    }
                }
            }

            // --- PULL DOWN TO REFRESH & SHUFFLE FEED ---
            PullToRefreshBox(
                isRefreshing = isPullRefreshing || isRefreshingFromVm,
                onRefresh = {
                    coroutineScope.launch {
                        isPullRefreshing = true
                        viewModel.refreshDiscoverFeed(forceReloadFromNetwork = false)
                        delay(600)
                        isPullRefreshing = false
                        listState.animateScrollToItem(0)
                    }
                },
                state = pullRefreshState,
                indicator = {
                    WavePullToRefreshIndicator(
                        state = pullRefreshState,
                        isRefreshing = isPullRefreshing || isRefreshingFromVm,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (displayedItems.isEmpty() && (isPullRefreshing || isRefreshingFromVm)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF6B00),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Curating latest shuffled feeds...",
                                fontSize = 12.sp,
                                color = if (isDark) Color.Gray else Color.DarkGray
                            )
                        }
                    }
                } else if (displayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No feeds found for $selectedFilterCategory",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF1C1C1E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    selectedFilterCategory = "All"
                                    viewModel.refreshDiscoverFeed(forceReloadFromNetwork = true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Refresh Feeds", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        itemsIndexed(
                            displayedItems,
                            key = { _, item -> "feed_screen_" + item.id }
                        ) { index, feedItem ->
                            if (index >= displayedItems.size - 3) {
                                LaunchedEffect(displayedItems.size) {
                                    viewModel.loadMoreMedia()
                                }
                            }

                            VerticalMediaFeedCard(
                                item = feedItem,
                                isPlayingPreview = activePreviewFeedItemId == feedItem.id,
                                onStartPreview = {
                                    activePreviewFeedItemId = feedItem.id
                                },
                                onStopPreview = {
                                    if (activePreviewFeedItemId == feedItem.id) {
                                        activePreviewFeedItemId = null
                                    }
                                },
                                onPlayClick = {
                                    activePreviewFeedItemId = null
                                    viewModel.playMediaItem(feedItem)
                                    onNavigateToPlayer()
                                },
                                onInfoClick = {
                                    viewModel.preScrapeMediaItem(feedItem)
                                    selectedItemForDetail = feedItem
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        // --- MEDIA DETAIL MODAL ---
        selectedItemForDetail?.let { item ->
            val isFavorite by viewModel.isMediaFavoriteStream(item.id).collectAsState(initial = false)
            MediaDetailSheet(
                item = item,
                isFavorite = isFavorite,
                onFavoriteToggle = {
                    viewModel.toggleMediaFavorite(item, isFavorite)
                },
                onDismiss = {
                    selectedItemForDetail = null
                },
                onPlayStream = { season, episode ->
                    selectedItemForDetail = null
                    viewModel.playMediaItem(item, season, episode)
                    onNavigateToPlayer()
                },
                viewModel = viewModel
            )
        }
    }
}
