package com.example.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaItem
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.StreamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailSheet(
    item: MediaItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onDismiss: () -> Unit,
    onPlayStream: (season: Int, episode: Int) -> Unit,
    viewModel: StreamViewModel,
    startWithTrailer: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    // Dynamic Theme-Aware Palette for Bottom Plate
    val sheetContainerColor = if (isDark) Color(0xFF16161A) else Color(0xFFFFFFFF)
    val sheetSurfaceCardColor = if (isDark) Color(0xFF24242A) else Color(0xFFF2F4F7)
    val sheetTextColor = if (isDark) Color.White else Color(0xFF111827)
    val sheetSubTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)
    val sheetChipBg = if (isDark) Color(0xFF26262C) else Color(0xFFE5E7EB)
    val sheetChipText = if (isDark) Color(0xFFD1D5DB) else Color(0xFF1F2937)
    val sheetDividerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    val isSeries = item.type == "series" || item.category in listOf(
        "Series & TV Shows", "Anime", "Anime Series", "Anime Movies", "K-Dramas", "Hindi Series"
    )

    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var showDownloaderModal by remember { mutableStateOf(false) }
    var isPlayingTrailer by remember { mutableStateOf(startWithTrailer) }
    var showTrailerModal by remember { mutableStateOf(startWithTrailer) }

    val youtubeTrailerId by viewModel.youtubeTrailerId.collectAsState()
    val castMembers by viewModel.castState.collectAsState()
    val isFetchingCast by viewModel.isFetchingCast.collectAsState()
    val mediaDetails by viewModel.mediaDetailState.collectAsState()

    val subServers by viewModel.availableSubServers.collectAsState()
    val dubServers by viewModel.availableDubServers.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val isFetchingServers by viewModel.isFetchingServers.collectAsState()

    val isAnime = remember(item) {
        com.example.scraper.AnimePosterEngine.isAnime(
            title = item.title,
            category = item.category,
            type = item.type,
            id = item.id
        )
    }

    val anikotoDetails by viewModel.anikotoDetailsState.collectAsState()
    val anikotoSeasons by viewModel.anikotoSeasons.collectAsState()
    val anikotoEpisodes by viewModel.anikotoEpisodes.collectAsState()
    val isAnimeLoading by viewModel.isAnimeLoading.collectAsState()

    LaunchedEffect(item.id, selectedSeason, selectedEpisode) {
        viewModel.fetchMediaDetails(item.imdbId ?: item.id, item.type)
        viewModel.fetchYouTubeTrailerId(item.title, item.year)
        viewModel.fetchAnikotoServers(item, selectedSeason, selectedEpisode)
        // Background stream scraping link generation starts immediately on bottom plate display
        viewModel.preScrapeMediaItem(item, selectedSeason, selectedEpisode)
    }

    LaunchedEffect(item.id, selectedSeason) {
        if (isAnime) {
            viewModel.fetchAnikotoMediaData(item, selectedSeason)
        }
    }

    LaunchedEffect(item.id) {
        isPlayingTrailer = startWithTrailer
        showTrailerModal = startWithTrailer
    }

    DisposableEffect(item.id) {
        onDispose {
            viewModel.clearYouTubeTrailerId()
        }
    }

    val totalSeasons = remember(mediaDetails, anikotoSeasons, isAnime) {
        if (isAnime && anikotoSeasons.isNotEmpty()) {
            anikotoSeasons.size
        } else {
            val num = mediaDetails?.number_of_seasons
            if (num != null && num > 0) num else 10
        }
    }

    val totalEpisodes = remember(mediaDetails, anikotoEpisodes, isAnime) {
        if (isAnime && anikotoEpisodes.isNotEmpty()) {
            anikotoEpisodes.size
        } else {
            val num = mediaDetails?.number_of_episodes
            if (num != null && num > 0) num else 30
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Hero / Banner Image / Trailer Player (Branding-free clean trailer player)
            val imageUrl = remember(item, anikotoDetails, isAnime) {
                if (isAnime && anikotoDetails != null && !anikotoDetails!!.posterUrl.isNullOrBlank()) {
                    anikotoDetails!!.posterUrl
                } else if (item.imageUrl.isNotBlank()) {
                    item.imageUrl
                } else {
                    ""
                }
            }
            if (imageUrl.isNotBlank() || isPlayingTrailer || isAnime) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    if (isPlayingTrailer) {
                        val trailerId = youtubeTrailerId
                        if (!trailerId.isNullOrBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        webChromeClient = object : android.webkit.WebChromeClient() {
                                            override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                                                return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                            }
                                        }
                                        webViewClient = object : android.webkit.WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                val reqUrl = request?.url?.toString() ?: return false
                                                if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                                    return false
                                                }
                                                return true
                                            }
                                        }
                                        settings.apply {
                                            javaScriptEnabled = true
                                            mediaPlaybackRequiresUserGesture = false
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            }
                                        }
                                        val htmlData = com.example.ui.components.getYouTubeEmbedHtml(trailerId)
                                        loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay Buttons (Expand Popup Window & Close)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { showTrailerModal = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Expand Trailer",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { isPlayingTrailer = false },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Trailer",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Loading state
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Loading Official Trailer...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Cancel button
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                IconButton(
                                    onClick = { isPlayingTrailer = false },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Normal Featured Poster Banner with Ambient Glow & Play Overlay
                        if (imageUrl.isNotBlank()) {
                            // 1. Ambient blurred background
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.45f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.85f)),
                                            startY = 0f
                                        )
                                    )
                            )
                            // 2. Centered crisp featured poster image
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                )
                            }
                        } else {
                            // Shimmer / Placeholder while loading
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF1E2230), Color(0xFF131620), Color(0xFF1E2230))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                            }
                        }

                        // Feature Badge Top-Start
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAnime) "FEATURED ANIME" else "FEATURED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NeonCyan
                                    )
                                }
                            }
                        }

                        // Center Play Trailer Button Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    val ytId = youtubeTrailerId
                                    val targetUrl = if (!ytId.isNullOrBlank()) {
                                        "https://www.youtube.com/watch?v=$ytId"
                                    } else {
                                        "https://www.youtube.com/results?search_query=" + android.net.Uri.encode("${item.title} official trailer")
                                    }
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                            setPackage("com.google.android.youtube")
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(fallbackIntent)
                                        } catch (ex: Exception) {
                                            // ignore
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    .border(2.dp, NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Trailer",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Title & Year & Category
            Text(
                text = item.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = sheetTextColor
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.year.isNotBlank()) {
                    Surface(
                        color = NeonPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.year,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.rating.isNotBlank()) {
                    Surface(
                        color = Color(0xFFFFD700).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.rating,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                }
                if (item.category.isNotBlank()) {
                    Text(
                        text = item.category,
                        fontSize = 12.sp,
                        color = sheetSubTextColor
                    )
                }
            }

            // Overview / Description
            val overviewText = remember(mediaDetails, anikotoDetails, isAnime, item.description) {
                if (isAnime && anikotoDetails != null && !anikotoDetails!!.description.isNullOrBlank()) {
                    anikotoDetails!!.description
                } else {
                    mediaDetails?.overview ?: item.description
                }
            }
            if (overviewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = overviewText,
                    fontSize = 13.sp,
                    color = sheetSubTextColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Anikoto Scraped Server Selection (Line 1: SUB, Line 2: DUB)
            if (isFetchingServers || subServers.isNotEmpty() || dubServers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = sheetDividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Servers",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Stream Servers",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = sheetTextColor
                        )
                    }

                    if (isFetchingServers) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                            Text(
                                text = "Scanning servers...",
                                fontSize = 11.sp,
                                color = sheetSubTextColor
                            )
                        }
                    } else if (selectedServer != null) {
                        Surface(
                            color = (if (selectedServer?.type?.lowercase() == "dub") NeonPurple else NeonCyan).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${selectedServer?.type?.uppercase() ?: "SUB"}: ${selectedServer?.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedServer?.type?.lowercase() == "dub") NeonPurple else NeonCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Line 1: SUB Servers
                if (subServers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "SUB",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "SUB",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(subServers) { srv ->
                                val isSelected = selectedServer?.linkId == srv.linkId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectAnikotoServer(srv) },
                                    label = {
                                        Text(
                                            text = srv.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan,
                                        selectedLabelColor = Color.Black,
                                        containerColor = sheetChipBg,
                                        labelColor = sheetChipText
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = sheetDividerColor,
                                        selectedBorderColor = NeonCyan
                                    )
                                )
                            }
                        }
                    }
                }

                // Line 2: DUB Servers (if available)
                if (dubServers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonPurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "DUB",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "DUB",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonPurple
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(dubServers) { srv ->
                                val isSelected = selectedServer?.linkId == srv.linkId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectAnikotoServer(srv) },
                                    label = {
                                        Text(
                                            text = srv.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonPurple,
                                        selectedLabelColor = Color.White,
                                        containerColor = sheetChipBg,
                                        labelColor = sheetChipText
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = sheetDividerColor,
                                        selectedBorderColor = NeonPurple
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Series Season & Episode Picker
            if (isSeries) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = sheetDividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Season Selection Header & Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Season",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = sheetTextColor
                    )
                    if (isAnimeLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                    } else {
                        Text(
                            text = "Season $selectedSeason Selected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..maxOf(1, totalSeasons)).toList()) { s ->
                        val isSelected = selectedSeason == s
                        val matchedSeason = if (isAnime) anikotoSeasons.find { it.number == s } else null
                        val seasonLabel = matchedSeason?.title ?: "Season $s"
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSeason = s
                                selectedEpisode = 1
                            },
                            label = {
                                Text(
                                    text = seasonLabel,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = Color.White,
                                containerColor = sheetChipBg,
                                labelColor = sheetChipText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = sheetDividerColor,
                                selectedBorderColor = NeonPurple
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Episode Selection Header & Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = sheetTextColor
                    )
                    Text(
                        text = "Episode $selectedEpisode Selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val epList = (1..maxOf(1, totalEpisodes)).toList()
                val hasManyEps = epList.size > 25
                val epChunkSize = 25
                val epRanges = remember(epList.size) {
                    if (hasManyEps) epList.chunked(epChunkSize).map { it.first()..it.last() } else emptyList()
                }
                var activeEpRangeIdx by remember(selectedSeason, epList.size) {
                    mutableIntStateOf(0)
                }

                if (hasManyEps && epRanges.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        itemsIndexed(epRanges) { idx, range ->
                            val isSelectedRange = activeEpRangeIdx == idx
                            FilterChip(
                                selected = isSelectedRange,
                                onClick = { activeEpRangeIdx = idx },
                                label = { Text("${range.first}-${range.last}", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = sheetChipBg,
                                    labelColor = sheetSubTextColor
                                )
                            )
                        }
                    }
                }

                val visibleEpisodes = remember(activeEpRangeIdx, epRanges, epList) {
                    if (hasManyEps && epRanges.isNotEmpty() && activeEpRangeIdx in epRanges.indices) {
                        val r = epRanges[activeEpRangeIdx]
                        epList.filter { it in r.first..r.last }
                    } else {
                        epList
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleEpisodes) { ep ->
                        val isSelected = selectedEpisode == ep
                        val matchedEp = if (isAnime) anikotoEpisodes.find { it.number == ep } else null
                        val epLabel = if (matchedEp != null) {
                            "Ep $ep: ${matchedEp.title}"
                        } else {
                            "Ep $ep"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedEpisode = ep },
                            label = {
                                Text(
                                    text = epLabel,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = sheetChipBg,
                                labelColor = sheetChipText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = sheetDividerColor,
                                selectedBorderColor = NeonCyan
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Red Compact Trailer Button
            Button(
                onClick = {
                    val ytId = youtubeTrailerId
                    val targetUrl = if (!ytId.isNullOrBlank()) {
                        "https://www.youtube.com/watch?v=$ytId"
                    } else {
                        "https://www.youtube.com/results?search_query=" + android.net.Uri.encode("${item.title} official trailer")
                    }
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                            setPackage("com.google.android.youtube")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallbackIntent)
                        } catch (ex: Exception) {
                            // ignore
                        }
                    }
                },
                modifier = Modifier
                    .wrapContentWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Trailer",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Trailer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action Buttons Row (Play, Favorite, Download)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onPlayStream(selectedSeason, selectedEpisode)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSeries) "Play S$selectedSeason E$selectedEpisode" else "Watch Stream",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(sheetSurfaceCardColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else sheetTextColor
                    )
                }

                IconButton(
                    onClick = { showDownloaderModal = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(sheetSurfaceCardColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = NeonCyan
                    )
                }
            }

            // Cast section if available
            if (castMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = sheetDividerColor)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cast & Crew",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = sheetTextColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(castMembers) { cast ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp)
                        ) {
                            val profileUrl = cast.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
                            if (!profileUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profileUrl,
                                    contentDescription = cast.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(sheetSurfaceCardColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = sheetSubTextColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cast.name ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = sheetTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

        if (showTrailerModal) {
        com.example.ui.components.YouTubeTrailerModal(
            showModal = showTrailerModal,
            onDismiss = { showTrailerModal = false },
            title = item.title,
            trailerId = youtubeTrailerId,
            isLoading = youtubeTrailerId.isNullOrBlank()
        )
    }

    if (showDownloaderModal) {
        val userProfile by viewModel.userProfile.collectAsState()
        com.example.ui.components.DownloaderModal(
            showModal = showDownloaderModal,
            onDismiss = { showDownloaderModal = false },
            title = item.title,
            imdbId = item.imdbId ?: item.id,
            season = selectedSeason,
            episode = selectedEpisode,
            isSeries = isSeries,
            isAnime = isAnime,
            coroutineScope = scope,
            userProfile = userProfile,
            viewModel = viewModel
        )
    }
}
