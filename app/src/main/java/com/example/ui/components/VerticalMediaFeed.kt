package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.MediaItem
import com.example.network.SmartNetworkBoosterEngine
import com.example.ui.viewmodel.StreamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vertical continuous video feed displaying media items (series, episodes, movies)
 * in edge-to-edge cards with touch-and-hold inline preview playback,
 * detailed metadata, options button for info modal, and on-demand scroll fetching.
 */
@Composable
fun VerticalMediaFeedCard(
    item: MediaItem,
    isPlayingPreview: Boolean,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val subTextColor = if (isDark) Color(0xFFA0A0A5) else Color(0xFF6E6E73)
    val cardBackground = if (isDark) Color(0xFF121214) else Color(0xFFFFFFFF)
    val orangeAccent = Color(0xFFFF6B00)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val detectedSeason = remember(item.title) {
        viewModel.extractSeasonFromTitle(item.title) ?: 1
    }
    val tmdbId = remember(item.id, item.imdbId) {
        item.imdbId ?: item.id
    }
    val isTv = remember(item.type) {
        item.type.equals("series", ignoreCase = true) || item.type.equals("tv", ignoreCase = true)
    }
    val isAnime = remember(item.title, item.category, item.type, item.id) {
        com.example.scraper.AnimePosterEngine.isAnime(item.title, item.category, item.type, item.id)
    }

    // Proactive background pre-scrape: As soon as card is composed in feed, warm cache for 0ms instant play
    LaunchedEffect(item.id, detectedSeason) {
        val cached = com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, detectedSeason, 1)
        if (cached == null || cached.streamUrl.isBlank()) {
            viewModel.preScrapeMediaItem(item, season = detectedSeason, episode = 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardBackground)
            .padding(bottom = 16.dp)
            .testTag("feed_card_${item.id}")
    ) {
        // === 16:9 EDGE-TO-EDGE THUMBNAIL / PREVIEW FRAME ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .pointerInput(item.id) {
                    detectTapGestures(
                        onLongPress = {
                            onStartPreview()
                        },
                        onTap = {
                            onPlayClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Static Poster Image
            if (!isPlayingPreview) {
                ShimmerAsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark
                )

                // Bottom Gradient for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f)
                                ),
                                startY = 100f
                            )
                        )
                )

                // Top-Left Badge (Type / Hot Indicator)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, orangeAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.type == "series") "SERIES" else "MOVIE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = orangeAccent
                        )
                    }
                }

                // Top-Right Category Pill
                if (item.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Bottom-Right Rating & Duration Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ ${item.rating.ifEmpty { "4.8" }}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.year.isNotEmpty()) item.year else "HD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                // Center subtle Hold-to-preview tooltip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Hold for preview",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Hold to preview",
                            fontSize = 8.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            } else {
                // === INLINE VIDEO PREVIEW PLAYER ===
                var isMuted by remember { mutableStateOf(false) }
                var previewPlayerReady by remember { mutableStateOf(false) }
                var resolvedPreviewUrl by remember { mutableStateOf("") }
                var resolvedPreviewHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var areSubtitlesEnabled by remember { mutableStateOf(true) }

                // New customization & auto-hide states
                var currentSeason by remember { androidx.compose.runtime.mutableIntStateOf(detectedSeason) }
                var currentEpisode by remember { androidx.compose.runtime.mutableIntStateOf(1) }
                var showServerSelectorDialog by remember { mutableStateOf(false) }
                var showSeasonEpisodeDialog by remember { mutableStateOf(false) }
                var showControls by remember { mutableStateOf(true) }
                var lastInteractionTime by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }

                // Auto-hide controls after 3 seconds of inactivity
                LaunchedEffect(showControls, lastInteractionTime) {
                    if (showControls) {
                        delay(3000)
                        showControls = false
                    }
                }

                // Instant parallel scraping & cache retrieval (0ms cache hit, no spinner overlay)
                LaunchedEffect(item.id, isPlayingPreview, currentSeason, currentEpisode) {
                    if (isPlayingPreview) {
                        val effectiveSeason = currentSeason
                        // 1. Instant check from cache (0ms instant!)
                        val cached = com.example.scraper.UnifiedStreamManager.getCachedStream(tmdbId, effectiveSeason, currentEpisode)
                            ?: com.example.scraper.UnifiedStreamManager.getCachedStream(item.id, effectiveSeason, currentEpisode)
                        
                        if (cached != null && cached.streamUrl.isNotBlank()) {
                            resolvedPreviewUrl = cached.streamUrl
                            resolvedPreviewHeaders = cached.headers
                        } else if (item.streamUrl.isNotBlank()) {
                            resolvedPreviewUrl = item.streamUrl
                        }

                        // 2. If not pre-cached, race fastest servers in parallel immediately
                        if (resolvedPreviewUrl.isBlank()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val streamRes = if (!isAnime) {
                                        com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                                            context = context,
                                            tmdbId = tmdbId,
                                            title = item.title,
                                            isTv = isTv,
                                            season = effectiveSeason,
                                            episode = currentEpisode,
                                            preferredServerKey = "fastest_auto"
                                        )?.result
                                    } else {
                                        com.example.scraper.UnifiedStreamManager.getStream(
                                            context = context,
                                            title = item.title,
                                            tmdbId = tmdbId,
                                            isTv = isTv,
                                            season = effectiveSeason,
                                            episode = currentEpisode,
                                            isAnime = true
                                        )
                                    }
                                    if (streamRes != null && streamRes.streamUrl.isNotBlank()) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            resolvedPreviewUrl = streamRes.streamUrl
                                            resolvedPreviewHeaders = streamRes.headers
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                val exoPlayer = remember(item.id, resolvedPreviewUrl, resolvedPreviewHeaders) {
                    if (resolvedPreviewUrl.isBlank()) {
                        null
                    } else {
                        val httpDataSourceFactory = SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
                            customHeaders = resolvedPreviewHeaders,
                            url = resolvedPreviewUrl
                        )
                        val mediaSourceFactory = SmartNetworkBoosterEngine.createOptimizedMediaSourceFactory(context, httpDataSourceFactory)

                        ExoPlayer.Builder(context)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .build().apply {
                                val mediaItemBuilder = Media3Item.Builder().setUri(resolvedPreviewUrl)
                                val urlLower = resolvedPreviewUrl.lowercase()
                                if (urlLower.contains(".m3u8") || urlLower.contains("m3u8") || urlLower.contains("hls")) {
                                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                                } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
                                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                                } else {
                                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MP4)
                                }
                                setMediaItem(mediaItemBuilder.build())
                                repeatMode = Player.REPEAT_MODE_ALL
                                volume = if (isMuted) 0f else 1f
                                playWhenReady = true
                                prepare()
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_READY) {
                                            previewPlayerReady = true
                                        }
                                    }
                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                        // On error, race alternate server to quickly switch stream without crashing
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val alt = com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                                                    context = context,
                                                    tmdbId = tmdbId,
                                                    title = item.title,
                                                    isTv = isTv,
                                                    season = currentSeason,
                                                    episode = currentEpisode,
                                                    preferredServerKey = "vidrock_direct"
                                                )?.result
                                                if (alt != null && alt.streamUrl.isNotBlank() && alt.streamUrl != resolvedPreviewUrl) {
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        previewPlayerReady = false
                                                        resolvedPreviewUrl = alt.streamUrl
                                                        resolvedPreviewHeaders = alt.headers
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                })
                            }
                    }
                }

                LaunchedEffect(areSubtitlesEnabled, exoPlayer) {
                    try {
                        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !areSubtitlesEnabled)
                            ?.build() ?: return@LaunchedEffect
                    } catch (_: Exception) {}
                }

                var currentPosition by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                var totalDuration by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                var isSeeking by remember { mutableStateOf(false) }
                var sliderValue by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

                LaunchedEffect(isPlayingPreview, exoPlayer) {
                    if (isPlayingPreview && exoPlayer != null) {
                        while (true) {
                            try {
                                currentPosition = exoPlayer.currentPosition
                                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                            } catch (_: Exception) {}
                            delay(250)
                        }
                    }
                }

                val displayPosition = if (isSeeking) sliderValue.toLong() else currentPosition

                fun formatTime(ms: Long): String {
                    val totalSecs = ms / 1000
                    val mins = totalSecs / 60
                    val secs = totalSecs % 60
                    return String.format("%02d:%02d", mins, secs)
                }

                LaunchedEffect(isMuted, exoPlayer) {
                    exoPlayer?.volume = if (isMuted) 0f else 1f
                }

                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, exoPlayer) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                            try {
                                exoPlayer?.playWhenReady = false
                            } catch (_: Exception) {}
                        } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            try {
                                exoPlayer?.playWhenReady = true
                            } catch (_: Exception) {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose {
                        try {
                            exoPlayer?.stop()
                            exoPlayer?.release()
                        } catch (_: Exception) {}
                    }
                }

                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            if (view.player != exoPlayer) {
                                view.player = exoPlayer
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                lastInteractionTime = System.currentTimeMillis()
                                if (!showControls) {
                                    showControls = true
                                } else {
                                    onPlayClick()
                                }
                            }
                    )
                }

                // Smooth high-res backdrop poster until player's first frame is ready (no spinner)
                if (!previewPlayerReady) {
                    ShimmerAsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        isDark = isDark
                    )
                }

                // Top-Right Compact Vertical Control Panel with Auto-Hide
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Close Preview Button (Cross)
                        IconButton(
                            onClick = { 
                                lastInteractionTime = System.currentTimeMillis()
                                onStopPreview() 
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 2. Mute / Unmute Toggle Button (Sound)
                        IconButton(
                            onClick = { 
                                lastInteractionTime = System.currentTimeMillis()
                                isMuted = !isMuted 
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 3. Caption / Subtitle Toggle Button (Closed Caption)
                        IconButton(
                            onClick = { 
                                lastInteractionTime = System.currentTimeMillis()
                                areSubtitlesEnabled = !areSubtitlesEnabled 
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Toggle Subtitles",
                                tint = if (areSubtitlesEnabled) orangeAccent else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 4. Server Selector Button (Dns Icon)
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                showServerSelectorDialog = true
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Select Server",
                                tint = orangeAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 5. Season & Episode Selector Button (PlaylistPlay Icon) - Only show if TV / Series / Anime
                        val isTvOrAnime = item.type.equals("series", true) || item.type.equals("tv", true) || com.example.scraper.AnimePosterEngine.isAnime(item.title, item.category, item.type, item.id)
                        if (isTvOrAnime) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showSeasonEpisodeDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = "Change Season/Episode",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Controls / Seek Progress Bar with Auto-Hide
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(displayPosition),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable { onPlayClick() }
                            ) {
                                Text(
                                    text = "Tap to open full player",
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = formatTime(totalDuration),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        
                        Slider(
                            value = displayPosition.toFloat().coerceIn(0f, (if (totalDuration > 0) totalDuration.toFloat() else 1f)),
                            onValueChange = { newValue ->
                                lastInteractionTime = System.currentTimeMillis()
                                isSeeking = true
                                sliderValue = newValue
                            },
                            onValueChangeFinished = {
                                lastInteractionTime = System.currentTimeMillis()
                                exoPlayer?.seekTo(sliderValue.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(if (totalDuration > 0) totalDuration.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                thumbColor = orangeAccent,
                                activeTrackColor = orangeAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                        )
                    }
                }

                // === Server Selector Dialog (Custom Styled with Sub/Dub Partitions for Anime) ===
                if (showServerSelectorDialog) {
                    AlertDialog(
                        onDismissRequest = { showServerSelectorDialog = false },
                        containerColor = Color(0xFF161618),
                        shape = RoundedCornerShape(16.dp),
                        title = {
                            Text(
                                text = "Select Stream Server",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        text = {
                            val isAnime = com.example.scraper.AnimePosterEngine.isAnime(item.title, item.category, item.type, item.id)
                            if (isAnime) {
                                var loadingServers by remember { mutableStateOf(true) }
                                var serverGroup by remember { mutableStateOf<com.example.scraper.AnikotoServerGroup?>(null) }
                                
                                LaunchedEffect(item.title, currentSeason, currentEpisode) {
                                    try {
                                        serverGroup = com.example.scraper.AnikotoScraper.fetchAvailableServers(
                                            item.title,
                                            season = currentSeason,
                                            episode = currentEpisode
                                        )
                                    } catch (_: Exception) {}
                                    loadingServers = false
                                }
                                
                                if (loadingServers) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = orangeAccent)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        var selectedTab by remember { mutableStateOf("SUB") }
                                        
                                        // Tab Buttons
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF242426), RoundedCornerShape(8.dp))
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = { selectedTab = "SUB" },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedTab == "SUB") orangeAccent else Color.Transparent,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("SUB PARTITION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { selectedTab = "DUB" },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedTab == "DUB") orangeAccent else Color.Transparent,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("DUB PARTITION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        val serversToDisplay = if (selectedTab == "SUB") {
                                            serverGroup?.subServers ?: emptyList()
                                        } else {
                                            serverGroup?.dubServers ?: emptyList()
                                        }
                                        
                                        if (serversToDisplay.isEmpty()) {
                                            Text(
                                                text = "No servers found in this partition",
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp)
                                            )
                                        } else {
                                            androidx.compose.foundation.lazy.LazyColumn(
                                                modifier = Modifier.heightIn(max = 200.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(serversToDisplay.size) { idx ->
                                                    val srv = serversToDisplay[idx]
                                                    var extracting by remember { mutableStateOf(false) }
                                                    Button(
                                                        onClick = {
                                                            extracting = true
                                                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                                                try {
                                                                    val extracted = com.example.scraper.AnikotoScraper.extractStreamFromServer(srv)
                                                                    if (extracted != null && extracted.streamUrl.isNotBlank()) {
                                                                        resolvedPreviewUrl = extracted.streamUrl
                                                                    }
                                                                } catch (_: Exception) {}
                                                                showServerSelectorDialog = false
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242426)),
                                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(srv.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                            if (extracting) {
                                                                CircularProgressIndicator(color = orangeAccent, modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                                            } else {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = orangeAccent, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Non-anime stream servers list (Fastest Auto, Hindi, VidRock, Flixer, Prime, Hexa, etc.)
                                val providers = listOf(
                                    "HM VIP Server" to "filxer",
                                    "HINDI Server" to "delta",
                                    "⚡ Fastest (Auto Parallel)" to "fastest_auto",
                                    "ZOZO Server (Direct)" to "vidrock_direct",
                                    "Prime Server" to "prime",
                                    "Hexa Server" to "hexa",
                                    "Alfa Server" to "alfa",
                                    "Gama Server" to "gama",
                                    "Lamda Server" to "lamda",
                                    "Zeta Server" to "zeta",
                                    "Catflix Server" to "catflix",
                                    "VidLink Server" to "vidlink_direct",
                                    "AutoEmbed Server" to "autoembed_direct"
                                )

                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(providers.size) { idx ->
                                        val (label, key) = providers[idx]
                                        var extracting by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = {
                                                extracting = true
                                                viewModel.selectStreamServerKey(key)
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                                    try {
                                                        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            com.example.scraper.UnifiedStreamManager.raceFastestServerStream(
                                                                context = context,
                                                                tmdbId = tmdbId,
                                                                title = item.title,
                                                                isTv = isTv,
                                                                season = currentSeason,
                                                                episode = currentEpisode,
                                                                preferredServerKey = key
                                                            )?.result
                                                        }
                                                        if (res != null && res.streamUrl.isNotBlank()) {
                                                            previewPlayerReady = false
                                                            resolvedPreviewUrl = res.streamUrl
                                                            resolvedPreviewHeaders = res.headers
                                                        }
                                                    } catch (_: Exception) {}
                                                    showServerSelectorDialog = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242426)),
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                if (extracting) {
                                                    CircularProgressIndicator(color = orangeAccent, modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = orangeAccent, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { showServerSelectorDialog = false }
                            ) {
                                Text("Close", color = orangeAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // === Season & Episode Selector Dialog ===
                if (showSeasonEpisodeDialog) {
                    AlertDialog(
                        onDismissRequest = { showSeasonEpisodeDialog = false },
                        containerColor = Color(0xFF161618),
                        shape = RoundedCornerShape(16.dp),
                        title = {
                            Text(
                                text = "Select Season & Episode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Adjust the season and episode for preview streaming:",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                
                                // Season Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Season", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { if (currentSeason > 1) currentSeason-- },
                                            modifier = Modifier.size(28.dp).background(Color(0xFF242426), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                        Text(
                                            text = currentSeason.toString(),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 24.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = { currentSeason++ },
                                            modifier = Modifier.size(28.dp).background(Color(0xFF242426), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                // Episode Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Episode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { if (currentEpisode > 1) currentEpisode-- },
                                            modifier = Modifier.size(28.dp).background(Color(0xFF242426), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                        Text(
                                            text = currentEpisode.toString(),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 24.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = { currentEpisode++ },
                                            modifier = Modifier.size(28.dp).background(Color(0xFF242426), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showSeasonEpisodeDialog = false
                                    previewPlayerReady = false
                                    resolvedPreviewUrl = "" // Reset to trigger reload
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = orangeAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply & Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showSeasonEpisodeDialog = false }
                            ) {
                                Text("Cancel", color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    )
                }
            }
        }

        // === BELOW THUMBNAIL INFO & ACTIONS ROW ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Category / Channel Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                orangeAccent,
                                Color(0xFFFF8C00)
                            )
                        )
                    )
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.category.lowercase()) {
                        "anime", "anime series" -> Icons.Default.AutoAwesome
                        "movies" -> Icons.Default.Movie
                        "series & tv shows", "series" -> Icons.Default.LiveTv
                        else -> Icons.Default.PlayCircleFilled
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title and Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlayClick() }
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subDetails = buildString {
                    append(item.category)
                    if (item.year.isNotBlank()) append(" • ${item.year}")
                    if (item.rating.isNotBlank()) append(" • ★ ${item.rating}")
                    if (item.type == "series") append(" • Series")
                }

                Text(
                    text = subDetails,
                    fontSize = 11.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2-Dot / 3-Dot Info & Options Button
            IconButton(
                onClick = { onInfoClick() },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("feed_options_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Video Options",
                    tint = subTextColor,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // === SYNOPSIS (OVERVIEW) ===
        val synopsis = item.description
        if (!synopsis.isNullOrBlank()) {
            Text(
                text = synopsis,
                fontSize = 12.sp,
                color = subTextColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clickable { onInfoClick() }
            )
        }

        // === STREAM SERVERS MULTI-LIST (UNDER SYNOPSIS) ===
        val isAnime = remember(item) {
            com.example.scraper.AnimePosterEngine.isAnime(item.title, item.category, item.type, item.id)
        }
        if (!isAnime) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Servers",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Stream Servers",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "⚡ Parallel Auto Race",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val streamServers = listOf(
                Triple("filxer", "HM VIP", Color(0xFF00E5FF)),
                Triple("delta", "HINDI", Color(0xFFFF9800)),
                Triple("fastest_auto", "⚡ Fastest Direct", Color(0xFF00E5FF)),
                Triple("vidrock_direct", "ZOZO (Direct)", Color(0xFFB388FF)),
                Triple("prime", "Prime", Color(0xFF00E676)),
                Triple("hexa", "Hexa", Color(0xFFE040FB)),
                Triple("alfa", "Alfa", Color(0xFF00E5FF)),
                Triple("gama", "Gama", Color(0xFFB388FF)),
                Triple("lamda", "Lamda", subTextColor),
                Triple("zeta", "Zeta", subTextColor),
                Triple("catflix", "Catflix", Color(0xFFFF5722)),
                Triple("vidlink_direct", "VidLink", Color(0xFF00E5FF)),
                Triple("autoembed_direct", "AutoEmbed", Color(0xFFB388FF))
            )

            val selectedStreamServerKey by viewModel.selectedStreamServerKey.collectAsState()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(streamServers) { (key, label, accentColor) ->
                    val isSelected = (selectedStreamServerKey == key) || (selectedStreamServerKey == null && key == "fastest_auto")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.22f) else if (isDark) Color(0xFF1C1C20) else Color(0xFFF2F2F5),
                        border = BorderStroke(1.dp, if (isSelected) accentColor else if (isDark) Color(0xFF2C2C32) else Color(0xFFE2E2E6)),
                        modifier = Modifier.clickable {
                            viewModel.selectStreamServerKey(key)
                            viewModel.preScrapeMediaItem(item, preferredServerKey = key)
                            viewModel.playMediaItem(item)
                            onPlayClick()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accentColor else textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Continuous infinite feed section integrated under the Hot Air section on the Home Page.
 */
@Composable
fun InfiniteVerticalMediaFeed(
    mediaItems: List<MediaItem>,
    viewModel: StreamViewModel,
    onNavigateToPlayer: () -> Unit,
    onOpenInfoModal: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val titleColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    var activePreviewItemId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("infinite_vertical_media_feed")
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subscriptions,
                    contentDescription = null,
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DISCOVER FEED",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = titleColor,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Continuous Stream",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF6B00)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical List of Feed Cards
        mediaItems.forEachIndexed { index, mediaItem ->
            // Trigger loading more as user scrolls towards bottom
            if (index >= mediaItems.size - 2) {
                LaunchedEffect(mediaItems.size) {
                    onLoadMore()
                }
            }

            VerticalMediaFeedCard(
                item = mediaItem,
                isPlayingPreview = activePreviewItemId == mediaItem.id,
                onStartPreview = {
                    activePreviewItemId = mediaItem.id
                },
                onStopPreview = {
                    if (activePreviewItemId == mediaItem.id) {
                        activePreviewItemId = null
                    }
                },
                onPlayClick = {
                    activePreviewItemId = null
                    viewModel.playMediaItem(mediaItem)
                    onNavigateToPlayer()
                },
                onInfoClick = {
                    viewModel.preScrapeMediaItem(mediaItem)
                    onOpenInfoModal(mediaItem)
                },
                viewModel = viewModel
            )
        }
    }
}
