package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import kotlinx.coroutines.delay

@Composable
fun AutoScrollingBannerCarousel(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    autoScrollDelay: Long = 3500L,
    edgeToEdge: Boolean = true
) {
    if (items.isEmpty()) return

    val validItems = remember(items) { items.filter { it.imageUrl.isNotEmpty() } }
    if (validItems.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { validItems.size })

    // Ultra smooth auto-scroll loop that respects delay and prevents rapid restarts
    LaunchedEffect(validItems.size) {
        if (validItems.size > 1) {
            while (true) {
                delay(autoScrollDelay)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % validItems.size
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = if (edgeToEdge) PaddingValues(0.dp) else PaddingValues(horizontal = 20.dp),
            pageSpacing = if (edgeToEdge) 0.dp else 12.dp,
            beyondViewportPageCount = 2
        ) { page ->
            val item = validItems[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(if (edgeToEdge) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))
                    .background(com.example.ui.theme.DeepSlate)
                    .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Smooth gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 80f
                            )
                        )
                )
                // Content Information
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = com.example.ui.theme.NeonCyan.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "POPULAR NOW",
                                color = com.example.ui.theme.NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (item.rating.isNotEmpty() && item.rating != "0.0") {
                            Text(
                                text = "⭐ ${item.rating}",
                                color = Color(0xFFFFD700),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.category.isNotEmpty()) {
                        Text(
                            text = item.category,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Indicator Dots at Bottom Right
        if (validItems.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = if (edgeToEdge) 16.dp else 32.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(validItems.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 16.dp else 6.dp,
                        label = "dotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) com.example.ui.theme.NeonCyan else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestedBannerCarousel(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 130.dp,
    autoScrollDelay: Long = 4000L
) {
    if (items.isEmpty()) return

    val validItems = remember(items) { items.filter { it.imageUrl.isNotEmpty() } }
    if (validItems.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { validItems.size })

    LaunchedEffect(validItems.size) {
        if (validItems.size > 1) {
            while (true) {
                delay(autoScrollDelay)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % validItems.size
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 2
        ) { page ->
            val item = validItems[page]
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.example.ui.theme.DeepSlate)
                    .clickable { onItemClick(item) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Suggested For You",
                        color = com.example.ui.theme.NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        color = com.example.ui.theme.TextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.year.ifEmpty { item.category },
                        color = com.example.ui.theme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
