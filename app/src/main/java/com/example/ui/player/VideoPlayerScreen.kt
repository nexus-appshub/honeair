package com.example.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.network.SmartNetworkBoosterEngine
import androidx.media3.ui.PlayerView
import com.example.scraper.ScrapedStreamResult
import com.example.scraper.UnifiedStreamManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    tmdbId: String,
    title: String,
    overview: String = "",
    mediaType: String = "movie",
    season: Int = 1,
    episode: Int = 1,
    streamUrlOverride: String? = null,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var isFullscreen by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var streamResult by remember { mutableStateOf<ScrapedStreamResult?>(null) }
    var activeStreamUrl by remember { mutableStateOf(streamUrlOverride) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 1. Configure optimized HTTP Data Source Factory to prevent M3U8 403 blocks and infinite buffering
    val dataSourceFactory = remember(streamResult, activeStreamUrl) {
        val headers = streamResult?.headers ?: emptyMap()
        SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
            customHeaders = headers,
            url = activeStreamUrl
        )
    }

    // 2. High-performance Load Control to eliminate buffering hangs
    val loadControl = remember {
        val sharedPrefs = context.getSharedPreferences("stream_app_prefs", android.content.Context.MODE_PRIVATE)
        val userBufferIndex = sharedPrefs.getInt("setting_buffer_index", 1)
        SmartNetworkBoosterEngine.createDynamicLoadControl(userBufferIndex, isLiveStream = false)
    }

    val renderersFactory = remember {
        val sharedPrefs = context.getSharedPreferences("stream_app_prefs", android.content.Context.MODE_PRIVATE)
        val userDecoderIndex = sharedPrefs.getInt("setting_decoder_index", 0)
        val isHwAccel = sharedPrefs.getBoolean("setting_hardware_accel", true)
        SmartNetworkBoosterEngine.createRenderersFactory(
            context = context,
            decoderMode = userDecoderIndex,
            isHardwareAccelerated = isHwAccel
        )
    }

    // 3. Create ExoPlayer Instance with custom MediaSourceFactory and HW+ Renderers
    val exoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(SmartNetworkBoosterEngine.createOptimizedMediaSourceFactory(context, dataSourceFactory))
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // 4. Resolve Stream from UnifiedStreamManager (Zero-Server Architecture)
    LaunchedEffect(tmdbId, season, episode, activeStreamUrl) {
        if (activeStreamUrl != null) {
            val url = activeStreamUrl!!
            val headers = streamResult?.headers ?: emptyMap()
            val httpFactory = SmartNetworkBoosterEngine.createBoostedHttpDataSourceFactory(
                customHeaders = headers,
                url = url
            )

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(if (url.contains(".m3u8", ignoreCase = true) || url.contains(".txt", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4)
                .build()

            val mediaSource = SmartNetworkBoosterEngine.createOptimizedMediaSourceFactory(context, httpFactory).createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isBuffering = false
        } else {
            isBuffering = true
            errorMessage = null
            coroutineScope.launch {
                val isAnime = com.example.scraper.AnimePosterEngine.isAnime(
                    title = title,
                    type = mediaType,
                    id = tmdbId
                )
                val isTv = mediaType.equals("series", ignoreCase = true) || mediaType.equals("tv", ignoreCase = true) || (mediaType.equals("anime", ignoreCase = true) && !title.lowercase().contains("movie"))
                val res = UnifiedStreamManager.getStream(
                    context = context,
                    title = title,
                    tmdbId = tmdbId,
                    isTv = isTv,
                    season = season,
                    episode = episode,
                    isAnime = isAnime
                )
                if (res != null && res.streamUrl.isNotEmpty()) {
                    streamResult = res
                    activeStreamUrl = res.streamUrl
                } else {
                    isBuffering = false
                    errorMessage = "Stream source could not be extracted directly."
                }
            }
        }
    }

    // Tracker for Position & Duration
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Force controls to always remain open and never auto-hide
    LaunchedEffect(showControls) {
        if (!showControls) {
            showControls = true
        }
    }

    // Player State and Lifecycle Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: PlaybackException) {
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Handle Back Press
    BackHandler {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isFullscreen = false
        } else {
            onBackPress()
        }
    }

    // Orientation and System Insets Management
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (isFullscreen) {
        // Landscape Full-Screen Mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = true }
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.exo_player_texture_view, null) as PlayerView
                    view.apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            CompactPlayerControls(
                title = title,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                showControls = showControls,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                isFullscreen = true,
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onToggleFullscreen = { isFullscreen = false },
                onBack = { isFullscreen = false }
            )
        }
    } else {
        // Non-Fullscreen Mode with Clean Inset Padding (No overlap with Status / Navigation Bars)
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = Color(0xFF0B0D14)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. Compact 16:9 Player with Custom Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showControls = true }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.exo_player_texture_view, null) as PlayerView
                            view.apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    CompactPlayerControls(
                        title = title,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        showControls = showControls,
                        currentPosition = currentPosition,
                        totalDuration = totalDuration,
                        isFullscreen = false,
                        onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onSeek = { exoPlayer.seekTo(it) },
                        onToggleFullscreen = { isFullscreen = true },
                        onBack = onBackPress
                    )
                }

                // 2. Movie Storyline & Metadata
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FF3B30),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (overview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Storyline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactPlayerControls(
    title: String = "",
    isPlaying: Boolean,
    isBuffering: Boolean,
    showControls: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit
) {
    AnimatedVisibility(
        visible = true, // Always visible, controllers must never hide
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            // Top Bar: Back button and Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White
                    )
                }
            }

            // Center Play/Pause button (Buffering spinner removed, play/pause is always visible)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Bottom Bar: Position, Seekbar, Duration
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlayerDuration(currentPosition),
                        color = Color.White,
                        fontSize = 11.sp
                    )

                    Text(
                        text = formatPlayerDuration(totalDuration),
                        color = Color(0xFFB0B0B0),
                        fontSize = 11.sp
                    )
                }

                val progress = if (totalDuration > 0) {
                    (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = progress,
                    onValueChange = { frac ->
                        if (totalDuration > 0) {
                            onSeek((frac * totalDuration).toLong())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF9800),
                        activeTrackColor = Color(0xFFFF9800),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }
        }
    }
}

private fun formatPlayerDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
